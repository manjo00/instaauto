package com.autoinsta.domain

import com.autoinsta.domain.ScheduleCalculator.Action
import com.autoinsta.domain.model.MissedPostPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The awkward cases a scheduler has to get right — missed by a second, missed by three
 * days, the phone was off for a week — without waiting for any of them to happen.
 */
class ScheduleCalculatorTest {

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val MINUTE = 60L * 1000L
        const val HOUR = 60L * MINUTE
        const val DAY = 24L * HOUR
        val GRACE = ScheduleCalculator.MISSED_GRACE_MILLIS
    }

    private fun actionAt(
        scheduledAt: Long,
        policy: MissedPostPolicy = MissedPostPolicy.POST_IF_RECENT,
    ) = ScheduleCalculator.actionFor(scheduledAt, policy, NOW)

    // ── Not due yet ────────────────────────────────────────────────────────

    @Test
    fun `a future post waits for its time`() {
        assertEquals(Action.WaitUntil(NOW + HOUR), actionAt(NOW + HOUR))
    }

    @Test
    fun `a post one millisecond in the future still waits`() {
        assertEquals(Action.WaitUntil(NOW + 1), actionAt(NOW + 1))
    }

    @Test
    fun `the waiting time is the post's own time, not a rounded one`() {
        val odd = NOW + 37 * MINUTE + 13
        assertEquals(Action.WaitUntil(odd), actionAt(odd))
    }

    // ── Exactly on time ────────────────────────────────────────────────────

    @Test
    fun `a post due exactly now publishes, whatever its policy`() {
        MissedPostPolicy.entries.forEach { policy ->
            assertEquals(
                "policy $policy should publish when exactly due",
                Action.PublishNow,
                actionAt(NOW, policy),
            )
        }
    }

    // ── POST_IF_RECENT: the default, grace-bounded ─────────────────────────

    @Test
    fun `default policy publishes a post one minute late`() {
        assertEquals(Action.PublishNow, actionAt(NOW - MINUTE))
    }

    @Test
    fun `default policy publishes right at the edge of the grace period`() {
        assertEquals(Action.PublishNow, actionAt(NOW - GRACE))
    }

    @Test
    fun `default policy refuses one millisecond past the grace period`() {
        assertEquals(Action.MarkMissed, actionAt(NOW - GRACE - 1))
    }

    @Test
    fun `default policy refuses a post three days stale`() {
        assertEquals(Action.MarkMissed, actionAt(NOW - 3 * DAY))
    }

    // ── POST_ANYWAY: never gives up ────────────────────────────────────────

    @Test
    fun `post-anyway publishes something a week late`() {
        assertEquals(
            Action.PublishNow,
            actionAt(NOW - 7 * DAY, MissedPostPolicy.POST_ANYWAY),
        )
    }

    @Test
    fun `post-anyway ignores the grace period entirely`() {
        assertEquals(
            Action.PublishNow,
            actionAt(NOW - GRACE - 1, MissedPostPolicy.POST_ANYWAY),
        )
    }

    // ── ASK_ME: never fires on its own once late ───────────────────────────

    @Test
    fun `ask-me defers a post that is even slightly late`() {
        assertEquals(Action.AskUser, actionAt(NOW - 1, MissedPostPolicy.ASK_ME))
    }

    @Test
    fun `ask-me defers rather than marking missed, however stale`() {
        assertEquals(
            Action.AskUser,
            actionAt(NOW - 30 * DAY, MissedPostPolicy.ASK_ME),
        )
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    @Test
    fun `isOverdue is inclusive of the scheduled moment`() {
        assertTrue(ScheduleCalculator.isOverdue(NOW, NOW))
        assertTrue(ScheduleCalculator.isOverdue(NOW - 1, NOW))
        assertFalse(ScheduleCalculator.isOverdue(NOW + 1, NOW))
    }

    @Test
    fun `lateness never goes negative for a future post`() {
        assertEquals(0L, ScheduleCalculator.latenessMillis(NOW + HOUR, NOW))
        assertEquals(0L, ScheduleCalculator.latenessMillis(NOW, NOW))
        assertEquals(HOUR, ScheduleCalculator.latenessMillis(NOW - HOUR, NOW))
    }

    // ── Alarm clamping ─────────────────────────────────────────────────────

    @Test
    fun `a comfortably future alarm is left alone`() {
        assertEquals(NOW + HOUR, ScheduleCalculator.alarmTimeFor(NOW + HOUR, NOW))
    }

    @Test
    fun `an alarm in the past is pushed to the minimum lead time`() {
        // AlarmManager treats a past time as "fire immediately", which would stampede
        // when re-arming a backlog at boot.
        assertEquals(
            NOW + ScheduleCalculator.MIN_ALARM_LEAD_MILLIS,
            ScheduleCalculator.alarmTimeFor(NOW - DAY, NOW),
        )
    }

    @Test
    fun `an alarm too close to now is pushed out to the floor`() {
        assertEquals(
            NOW + ScheduleCalculator.MIN_ALARM_LEAD_MILLIS,
            ScheduleCalculator.alarmTimeFor(NOW + 1000L, NOW),
        )
    }

    @Test
    fun `the grace period is one hour`() {
        // Pinned deliberately: this number is a product decision, not an implementation
        // detail, and changing it silently would change what the app does overnight.
        assertEquals(HOUR, ScheduleCalculator.MISSED_GRACE_MILLIS)
    }
}
