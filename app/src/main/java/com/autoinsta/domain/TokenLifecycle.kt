package com.autoinsta.domain

/**
 * When an Instagram access token needs attention.
 *
 * Meta's rules, which this encodes:
 * - A long-lived token lasts **60 days**.
 * - It can be refreshed for another 60, but only once it is **at least 24 hours old**.
 * - A token not refreshed within its 60 days **expires permanently** — the user has to
 *   log in again; there is no way to revive it.
 *
 * Pure, with the clock passed in, for the same reason as [PostValidator] and
 * [ScheduleCalculator]: otherwise verifying "does this refresh on day 51?" means waiting
 * 51 days. Here it is an instant unit test.
 */
object TokenLifecycle {

    /** Meta issues long-lived tokens valid for 60 days. */
    const val TOKEN_LIFETIME_MILLIS: Long = 60L * 24 * 60 * 60 * 1000

    /** Meta refuses to refresh a token younger than this. */
    const val MIN_AGE_TO_REFRESH_MILLIS: Long = 24L * 60 * 60 * 1000

    /**
     * Refresh once fewer than 10 days remain, rather than at the last moment.
     * The app may not be opened often, and every launch that passes without refreshing
     * is a chance the token quietly dies.
     */
    const val REFRESH_WHEN_REMAINING_MILLIS: Long = 10L * 24 * 60 * 60 * 1000

    sealed interface State {
        /** Plenty of time left; nothing to do. */
        data object Healthy : State

        /** Inside the refresh window and old enough — refresh now. */
        data object ShouldRefresh : State

        /**
         * Inside the refresh window but younger than 24 hours, so Meta would reject a
         * refresh. Try again later; this is not a problem.
         */
        data object TooYoungToRefresh : State

        /** Past its 60 days. Unrecoverable — the user must connect again. */
        data object Expired : State
    }

    fun stateOf(
        issuedAtMillis: Long,
        expiresAtMillis: Long,
        nowMillis: Long,
    ): State {
        if (nowMillis >= expiresAtMillis) return State.Expired

        val remaining = expiresAtMillis - nowMillis
        if (remaining > REFRESH_WHEN_REMAINING_MILLIS) return State.Healthy

        val age = nowMillis - issuedAtMillis
        return if (age >= MIN_AGE_TO_REFRESH_MILLIS) State.ShouldRefresh else State.TooYoungToRefresh
    }

    /** Convenience for the UI. */
    fun isExpired(expiresAtMillis: Long, nowMillis: Long): Boolean = nowMillis >= expiresAtMillis

    /** Whole days left, floored, never negative. Drives the Settings screen's wording. */
    fun daysRemaining(expiresAtMillis: Long, nowMillis: Long): Int {
        val remaining = expiresAtMillis - nowMillis
        if (remaining <= 0) return 0
        return (remaining / (24L * 60 * 60 * 1000)).toInt()
    }

    /** The expiry stamp to store when a token is issued or refreshed. */
    fun expiryFor(issuedAtMillis: Long, expiresInSeconds: Long?): Long =
        issuedAtMillis + (expiresInSeconds?.times(1000) ?: TOKEN_LIFETIME_MILLIS)
}
