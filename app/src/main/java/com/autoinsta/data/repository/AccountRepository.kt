package com.autoinsta.data.repository

import com.autoinsta.BuildConfig
import com.autoinsta.data.db.dao.AccountDao
import com.autoinsta.data.db.entities.AccountEntity
import com.autoinsta.data.prefs.TokenStore
import com.autoinsta.data.remote.InstagramAuthApi
import com.autoinsta.domain.TokenLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** What happened when we tried to connect. */
sealed interface ConnectResult {
    data class Success(val username: String) : ConnectResult
    data class Failure(val message: String) : ConnectResult
}

/**
 * Everything to do with the connected Instagram account: connecting, staying connected,
 * and disconnecting.
 *
 * The token lives in [TokenStore] (encrypted); the account details live in Room. This
 * class is the only thing that knows both halves, so callers can't accidentally end up
 * with one without the other.
 */
class AccountRepository(
    private val accountDao: AccountDao,
    private val tokenStore: TokenStore,
    private val authApi: InstagramAuthApi,
) {

    /** Observe the account — emits null when no account is connected. */
    fun observe(): Flow<AccountEntity?> = accountDao.observe()

    /** One-shot read — for workers that don't need a live stream. */
    suspend fun get(): AccountEntity? = accountDao.get()

    /** The token to sign API calls with, or null when not connected. */
    fun accessToken(): String? = tokenStore.getAccessToken()

    fun isConnected(): Boolean = tokenStore.hasToken()

    // ── Connecting ─────────────────────────────────────────────────────────

    /**
     * Turn the authorization code from the login redirect into a stored, connected account.
     *
     * Three network calls in sequence: code → 1-hour token → 60-day token → profile.
     * Any failure leaves nothing behind, so a half-connected state is impossible.
     */
    suspend fun connectWithCode(rawCode: String): ConnectResult = withContext(Dispatchers.IO) {
        // Instagram appends "#_" to the redirect URL. Left on, the exchange fails with
        // an error that says nothing about the real cause.
        val code = rawCode.removeSuffix("#_").trim()
        if (code.isEmpty()) return@withContext ConnectResult.Failure("No login code was returned.")

        try {
            val shortLived = authApi.exchangeCodeForShortLivedToken(
                clientId = BuildConfig.META_APP_ID,
                clientSecret = BuildConfig.META_APP_SECRET,
                redirectUri = BuildConfig.OAUTH_REDIRECT_URI,
                code = code,
            )
            val shortToken = shortLived.accessToken
                ?: return@withContext ConnectResult.Failure("Instagram didn't return a token.")

            val longLived = authApi.exchangeForLongLivedToken(
                clientSecret = BuildConfig.META_APP_SECRET,
                accessToken = shortToken,
            )
            val longToken = longLived.accessToken
                ?: return@withContext ConnectResult.Failure("Couldn't upgrade to a long-lived token.")

            val profile = authApi.getProfile(accessToken = longToken)
            val igUserId = profile.resolvedId
                ?: return@withContext ConnectResult.Failure("Couldn't read the account id.")
            val username = profile.username ?: "(unknown)"

            val now = System.currentTimeMillis()
            val expiresAt = TokenLifecycle.expiryFor(now, longLived.expiresIn)

            tokenStore.save(longToken, issuedAtMillis = now, expiresAtMillis = expiresAt)
            accountDao.save(
                AccountEntity(
                    igUserId = igUserId,
                    username = username,
                    profilePictureUrl = profile.profilePictureUrl,
                    connectedAt = now,
                    tokenExpiresAt = expiresAt,
                )
            )
            ConnectResult.Success(username)
        } catch (e: Exception) {
            ConnectResult.Failure(readableError(e))
        }
    }

    // ── Staying connected ──────────────────────────────────────────────────

    /**
     * Refresh the token if it needs it. Safe to call on every launch — it does nothing
     * when the token is healthy or too young for Meta to accept a refresh.
     *
     * Returns true only when a refresh actually happened.
     */
    suspend fun refreshIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        val token = tokenStore.getAccessToken() ?: return@withContext false

        val state = TokenLifecycle.stateOf(
            issuedAtMillis = tokenStore.getIssuedAt(),
            expiresAtMillis = tokenStore.getExpiresAt(),
            nowMillis = System.currentTimeMillis(),
        )
        if (state !is TokenLifecycle.State.ShouldRefresh) return@withContext false

        try {
            val refreshed = authApi.refreshLongLivedToken(accessToken = token)
            val newToken = refreshed.accessToken ?: return@withContext false

            val now = System.currentTimeMillis()
            val expiresAt = TokenLifecycle.expiryFor(now, refreshed.expiresIn)
            tokenStore.save(newToken, issuedAtMillis = now, expiresAtMillis = expiresAt)
            accountDao.get()?.let { accountDao.save(it.copy(tokenExpiresAt = expiresAt)) }
            true
        } catch (e: Exception) {
            // A failed refresh is not fatal — the existing token is still valid until it
            // isn't, and the Settings screen surfaces how long is left.
            false
        }
    }

    /** How the token is doing right now, for the Settings screen. */
    fun tokenState(nowMillis: Long = System.currentTimeMillis()): TokenLifecycle.State =
        TokenLifecycle.stateOf(
            issuedAtMillis = tokenStore.getIssuedAt(),
            expiresAtMillis = tokenStore.getExpiresAt(),
            nowMillis = nowMillis,
        )

    fun daysRemaining(nowMillis: Long = System.currentTimeMillis()): Int =
        TokenLifecycle.daysRemaining(tokenStore.getExpiresAt(), nowMillis)

    // ── Disconnecting ──────────────────────────────────────────────────────

    /** Forget the account entirely: token and row both. */
    suspend fun disconnect() {
        tokenStore.clear()
        accountDao.clear()
    }

    /** Kept for callers that already have an entity (tests, migrations). */
    suspend fun save(account: AccountEntity) = accountDao.save(account)

    /**
     * Turn an exception into something worth showing a person. Meta's HTTP errors carry
     * the real reason in the response body, which retrofit's default message throws away.
     */
    private fun readableError(e: Exception): String = when (e) {
        is retrofit2.HttpException -> {
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            val detail = body?.let { extractMetaMessage(it) }
            detail ?: "Instagram rejected the request (HTTP ${e.code()})."
        }
        is java.net.UnknownHostException -> "No internet connection."
        is java.net.SocketTimeoutException -> "Instagram took too long to respond."
        else -> e.message ?: "Something went wrong connecting to Instagram."
    }

    /** Pull the message out of either of Meta's two error shapes, without a full parse. */
    private fun extractMetaMessage(body: String): String? =
        Regex(""""(?:error_message|message)"\s*:\s*"([^"]+)"""")
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
}
