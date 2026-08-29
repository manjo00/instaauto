package com.autoinsta.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore

/**
 * Holds the Instagram access token, encrypted at rest.
 *
 * ## Why not Room
 * The database file is plain SQLite. Anything with device access — root, an extracted
 * backup, a debugger on a developer build — can read it. This token can publish to the
 * account, so it gets stronger treatment: `EncryptedSharedPreferences` encrypts the value
 * with a key held in the Android Keystore, which is hardware-backed on most devices and
 * cannot be extracted even from a rooted phone.
 *
 * The *account* (username, id, expiry) stays in Room — it isn't secret, and the UI needs
 * to observe it. Only the token itself lives here.
 */
class TokenStore(
    private val context: Context,
) {

    private val prefs: SharedPreferences by lazy { openOrReset() }

    /**
     * Open the encrypted store, and if the stored keyset cannot be read, throw it away and
     * start a new one.
     *
     * ## Why this is not paranoia
     * The token is encrypted with a key in the Android Keystore, and **Keystore keys never
     * travel**. Restore a backup, move to a new phone with Smart Switch, or have the
     * package reinstalled under a new UID, and the ciphertext arrives with no key that can
     * read it. `androidx.security` then throws while *opening* the file — before any call
     * of ours runs — so the failure lands wherever the store is first touched, which for
     * this app is a background coroutine on launch. That is a permanent crash on start.
     *
     * Backing the file up is now prevented outright (`res/xml/backup_rules.xml`), which is
     * the real fix. This is the second line: a wiped Keystore, a changed lock screen, or
     * anything else that invalidates the key still costs only the login. The owner
     * reconnects in Settings, which already knows how to say "not connected"; the queue,
     * the posts and the media are untouched.
     */
    private fun openOrReset(): SharedPreferences = try {
        open()
    } catch (e: Exception) {
        // Deliberately broad. Tink surfaces this as AEADBadTagException, KeyStoreException,
        // IOException or a wrapped RuntimeException depending on how the key was lost, and
        // every one of them means the same thing: these bytes are unreadable. Guessing the
        // exact type wrong here would put the crash back.
        context.deleteSharedPreferences(FILE_NAME)
        deleteMasterKey()
        open()
    }

    private fun open(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * Drop the Keystore entry so the next [open] mints a fresh one. Without this, a key
     * that exists but cannot decrypt the file would fail again on the retry.
     */
    private fun deleteMasterKey() {
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }
    }

    /** The long-lived access token, or null when not connected. */
    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    /** When the stored token was issued — needed for Meta's 24-hour refresh rule. */
    fun getIssuedAt(): Long = prefs.getLong(KEY_ISSUED_AT, 0L)

    /** When the stored token stops working. */
    fun getExpiresAt(): Long = prefs.getLong(KEY_EXPIRES_AT, 0L)

    fun save(accessToken: String, issuedAtMillis: Long, expiresAtMillis: Long) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_ISSUED_AT, issuedAtMillis)
            .putLong(KEY_EXPIRES_AT, expiresAtMillis)
            .apply()
    }

    /** Wipe everything — on disconnect, or when a token is beyond saving. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    fun hasToken(): Boolean = !getAccessToken().isNullOrBlank()

    private companion object {
        const val FILE_NAME = "autoinsta_secure_prefs"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_ISSUED_AT = "issued_at"
        const val KEY_EXPIRES_AT = "expires_at"
    }
}
