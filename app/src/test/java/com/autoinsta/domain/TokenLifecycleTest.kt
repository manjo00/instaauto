package com.autoinsta.domain

import com.autoinsta.domain.TokenLifecycle.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Token expiry is the kind of bug you find 60 days later, in production, with a post that
 * silently didn't go out. These cover the boundaries without waiting for any of them.
 */
class TokenLifecycleTest {

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val DAY = 24L * 60 * 60 * 1000
        const val HOUR = 60L * 60 * 1000
    }

    /** A token issued `ageDays` ago with the standard 60-day life. */
    private fun stateFor(ageDays: Long): State {
        val issued = NOW - ageDays * DAY
        return TokenLifecycle.stateOf(issued, issued + TokenLifecycle.TOKEN_LIFETIME_MILLIS, NOW)
    }

    // ── Healthy ────────────────────────────────────────────────────────────

    @Test
    fun `a brand new token is healthy`() {
        assertEquals(State.Healthy, stateFor(ageDays = 0))
    }

    @Test
    fun `a token at 30 days is still healthy`() {
        assertEquals(State.Healthy, stateFor(ageDays = 30))
    }

    @Test
    fun `a token with just over 10 days left is still healthy`() {
        // 60 - 49 = 11 days remaining
        assertEquals(State.Healthy, stateFor(ageDays = 49))
    }

    // ── The refresh window ─────────────────────────────────────────────────

    @Test
    fun `a token with exactly 10 days left should refresh`() {
        assertEquals(State.ShouldRefresh, stateFor(ageDays = 50))
    }

    @Test
    fun `a token with 3 days left should refresh`() {
        assertEquals(State.ShouldRefresh, stateFor(ageDays = 57))
    }

    @Test
    fun `a token one hour from expiry should refresh`() {
        val issued = NOW - (TokenLifecycle.TOKEN_LIFETIME_MILLIS - HOUR)
        assertEquals(
            State.ShouldRefresh,
            TokenLifecycle.stateOf(issued, issued + TokenLifecycle.TOKEN_LIFETIME_MILLIS, NOW),
        )
    }

    // ── Meta's 24-hour rule ────────────────────────────────────────────────

    @Test
    fun `a short-lived token inside the window but under 24h old cannot refresh yet`() {
        // Contrived but real: a token issued 1 hour ago that expires in 2 days. Meta
        // would reject a refresh, so the app must wait rather than spam a failing call.
        val issued = NOW - HOUR
        assertEquals(
            State.TooYoungToRefresh,
            TokenLifecycle.stateOf(issued, NOW + 2 * DAY, NOW),
        )
    }

    @Test
    fun `exactly 24 hours old is old enough to refresh`() {
        val issued = NOW - TokenLifecycle.MIN_AGE_TO_REFRESH_MILLIS
        assertEquals(
            State.ShouldRefresh,
            TokenLifecycle.stateOf(issued, NOW + 2 * DAY, NOW),
        )
    }

    @Test
    fun `one millisecond under 24 hours is still too young`() {
        val issued = NOW - TokenLifecycle.MIN_AGE_TO_REFRESH_MILLIS + 1
        assertEquals(
            State.TooYoungToRefresh,
            TokenLifecycle.stateOf(issued, NOW + 2 * DAY, NOW),
        )
    }

    // ── Expired ────────────────────────────────────────────────────────────

    @Test
    fun `a token past 60 days is expired`() {
        assertEquals(State.Expired, stateFor(ageDays = 61))
    }

    @Test
    fun `expiry is inclusive - the exact moment counts as expired`() {
        assertEquals(State.Expired, TokenLifecycle.stateOf(NOW - 60 * DAY, NOW, NOW))
    }

    @Test
    fun `an ancient token is expired, not merely in need of refresh`() {
        // The distinction matters: expired means reconnect, refresh means retry silently.
        assertEquals(State.Expired, stateFor(ageDays = 400))
    }

    // ── Helpers used by the UI ─────────────────────────────────────────────

    @Test
    fun `isExpired agrees with stateOf`() {
        assertFalse(TokenLifecycle.isExpired(NOW + DAY, NOW))
        assertTrue(TokenLifecycle.isExpired(NOW, NOW))
        assertTrue(TokenLifecycle.isExpired(NOW - 1, NOW))
    }

    @Test
    fun `daysRemaining floors and never goes negative`() {
        assertEquals(10, TokenLifecycle.daysRemaining(NOW + 10 * DAY, NOW))
        assertEquals(0, TokenLifecycle.daysRemaining(NOW + HOUR, NOW))
        assertEquals(0, TokenLifecycle.daysRemaining(NOW - 5 * DAY, NOW))
        // 9.9 days should read as 9, not round up to 10
        assertEquals(9, TokenLifecycle.daysRemaining(NOW + 10 * DAY - HOUR, NOW))
    }

    // ── Expiry stamping ────────────────────────────────────────────────────

    @Test
    fun `expiry uses the value Meta returned when present`() {
        val fiveDays = 5L * 24 * 60 * 60
        assertEquals(NOW + 5 * DAY, TokenLifecycle.expiryFor(NOW, fiveDays))
    }

    @Test
    fun `expiry falls back to 60 days when Meta omits expires_in`() {
        assertEquals(
            NOW + TokenLifecycle.TOKEN_LIFETIME_MILLIS,
            TokenLifecycle.expiryFor(NOW, null),
        )
    }
}
