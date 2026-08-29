package com.autoinsta.domain

import com.autoinsta.domain.QueuePlanner.QueuedAction
import com.autoinsta.domain.QueuePlanner.Slot
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cases a posting queue has to get right that you cannot afford to wait for:
 * a slot missed by three days, a phone off across a daylight-saving change, a queue
 * reordered mid-week, a pool that sits empty over a slot.
 *
 * Dates are real ones. 2026-09-07 is a Monday, so the week reads:
 * Mon 7th, Wed 9th, Sat 12th, then Mon 14th.
 */
class QueuePlannerTest {

    private companion object {
        /** Riyadh: UTC+3 all year, so the ordinary cases carry no DST noise. */
        val ZONE: ZoneId = ZoneId.of("Asia/Riyadh")

        /** London, purely for the two daylight-saving tests. */
        val LONDON: ZoneId = ZoneId.of("Europe/London")

        val MON_7PM = Slot(DayOfWeek.MONDAY, 19, 0)
        val WED_7PM = Slot(DayOfWeek.WEDNESDAY, 19, 0)
        val SAT_11AM = Slot(DayOfWeek.SATURDAY, 11, 0)
        val SCHEDULE = listOf(MON_7PM, WED_7PM, SAT_11AM)

        const val MINUTE = 60L * 1000L
        const val HOUR = 60L * MINUTE
        const val DAY = 24L * HOUR

        val TWO_HOURS = QueuePlanner.windowMillis(120)
        val ONE_HOUR = QueuePlanner.windowMillis(60)
        val TWO_DAYS = QueuePlanner.windowMillis(2880)
    }

    private fun at(
        day: Int,
        hour: Int,
        minute: Int = 0,
        month: Int = 9,
        zone: ZoneId = ZONE,
    ): Long = ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone)
        .toInstant()
        .toEpochMilli()

    private fun planAt(
        nowMillis: Long,
        queue: List<Long>,
        slots: List<Slot> = SCHEDULE,
        window: Long = TWO_HOURS,
        paused: Boolean = false,
        resumedAt: Long = 0L,
        fixed: List<Long> = emptyList(),
        notBefore: Map<Long, Long> = emptyMap(),
    ) = QueuePlanner.plan(
        queuedIdsInOrder = queue,
        slots = slots,
        nowMillis = nowMillis,
        zone = ZONE,
        catchUpWindowMillis = window,
        paused = paused,
        resumedAtMillis = resumedAt,
        fixedPostTimes = fixed,
        notBefore = notBefore,
    )

    // ── The everyday case ──────────────────────────────────────────────────

    @Test
    fun `posts take consecutive slots in queue order`() {
        val plan = planAt(at(7, 8), listOf(1L, 2L, 3L))

        assertEquals(at(7, 19), plan.timeFor(1L))
        assertEquals(at(9, 19), plan.timeFor(2L))
        assertEquals(at(12, 11), plan.timeFor(3L))
        assertTrue("nothing should be left over", plan.unassigned.isEmpty())
    }

    @Test
    fun `the schedule wraps into the following week`() {
        val plan = planAt(at(7, 8), listOf(1L, 2L, 3L, 4L))

        assertEquals("the fourth post rolls to next Monday", at(14, 19), plan.timeFor(4L))
    }

    @Test
    fun `reordering the queue swaps the times`() {
        val before = planAt(at(7, 8), listOf(1L, 2L))
        val after = planAt(at(7, 8), listOf(2L, 1L))

        assertEquals(before.timeFor(1L), after.timeFor(2L))
        assertEquals(before.timeFor(2L), after.timeFor(1L))
    }

    @Test
    fun `a slot already passed today is not offered again`() {
        // 20:00 Monday: Monday's 19:00 is gone, so the head takes Wednesday.
        val plan = planAt(at(7, 20), listOf(1L), window = 0L)

        assertEquals(at(9, 19), plan.timeFor(1L))
    }

    // ── The empty pool: the day is simply skipped ──────────────────────────

    @Test
    fun `an empty pool produces no assignments`() {
        val plan = planAt(at(7, 8), emptyList())

        assertTrue(plan.assignments.isEmpty())
        assertTrue(plan.unassigned.isEmpty())
    }

    @Test
    fun `no slots means nothing is given a time`() {
        val plan = planAt(at(7, 8), listOf(1L, 2L), slots = emptyList())

        assertTrue(plan.assignments.isEmpty())
        assertEquals(listOf(1L, 2L), plan.unassigned)
    }

    // ── Pause ──────────────────────────────────────────────────────────────

    @Test
    fun `paused assigns nothing and leaves the pool intact`() {
        val plan = planAt(at(7, 8), listOf(1L, 2L), paused = true)

        assertTrue("a paused queue must arm no alarms", plan.assignments.isEmpty())
        assertEquals("and must not lose its order", listOf(1L, 2L), plan.unassigned)
    }

    @Test
    fun `a slot that passed while paused is not caught up after resuming`() {
        // Monday 19:30, 2h window — normally wide open. But the queue only resumed
        // at 19:15, after that slot had already gone by.
        val plan = planAt(
            nowMillis = at(7, 19, 30),
            queue = listOf(1L),
            resumedAt = at(7, 19, 15),
        )

        assertEquals("must wait for the next real slot", at(9, 19), plan.timeFor(1L))
        assertTrue(plan.assignments.none { it.isCatchUp })
    }

    // ── The catch-up window ────────────────────────────────────────────────

    @Test
    fun `an open slot goes to the head of the queue`() {
        // 19:30 Monday, 30 minutes after the 19:00 slot, window is 2h.
        val plan = planAt(at(7, 19, 30), listOf(1L, 2L))

        assertEquals(at(7, 19), plan.timeFor(1L))
        assertTrue("it should be marked as a catch-up", plan.isCatchUp(1L))
        assertEquals("everyone else carries on normally", at(9, 19), plan.timeFor(2L))
    }

    @Test
    fun `a slot older than the window is not caught up`() {
        // Wednesday 09:00 with a 1h window. Monday 19:00 was 38 hours ago.
        val plan = planAt(at(9, 9), listOf(1L), window = ONE_HOUR)

        assertEquals(at(9, 19), plan.timeFor(1L))
        assertTrue(plan.assignments.none { it.isCatchUp })
    }

    @Test
    fun `only one post catches up even when several slots were missed`() {
        // Phone off all week: Wednesday 20:00 with a 2-day window means both Monday
        // 19:00 and Wednesday 19:00 are inside it.
        val plan = planAt(at(9, 20), listOf(1L, 2L, 3L), window = TWO_DAYS)

        assertEquals(
            "exactly one catch-up — two posts minutes apart reads as a glitch",
            1,
            plan.assignments.count { it.isCatchUp },
        )
        assertEquals("the most recent missed slot, not the oldest", at(9, 19), plan.timeFor(1L))
        assertEquals(at(12, 11), plan.timeFor(2L))
        assertEquals(at(14, 19), plan.timeFor(3L))
    }

    @Test
    fun `a zero window disables catch-up entirely`() {
        val plan = planAt(at(7, 19, 30), listOf(1L), window = 0L)

        assertEquals(at(9, 19), plan.timeFor(1L))
    }

    @Test
    fun `openCatchUpSlot reports nothing when no slot has passed`() {
        assertNull(
            QueuePlanner.openCatchUpSlot(SCHEDULE, at(7, 8), ZONE, TWO_HOURS),
        )
    }

    // ── "Wait for the next slot instead" ───────────────────────────────────

    @Test
    fun `notBefore pushes the head past an open slot`() {
        // The owner was warned the post would go out now and chose to wait.
        val plan = planAt(
            nowMillis = at(7, 19, 30),
            queue = listOf(1L),
            notBefore = mapOf(1L to at(9, 19)),
        )

        assertEquals(at(9, 19), plan.timeFor(1L))
        assertTrue(plan.assignments.none { it.isCatchUp })
    }

    @Test
    fun `a declined open slot is left unfilled rather than handed to the next post`() {
        val plan = planAt(
            nowMillis = at(7, 19, 30),
            queue = listOf(1L, 2L),
            notBefore = mapOf(1L to at(9, 19)),
        )

        assertTrue("nobody jumps the queue", plan.assignments.none { it.isCatchUp })
        assertEquals(at(9, 19), plan.timeFor(1L))
        assertEquals(at(12, 11), plan.timeFor(2L))
    }

    // ── Staying out of the way of fixed-time posts ─────────────────────────

    @Test
    fun `a slot within half an hour of a fixed post is skipped`() {
        val plan = planAt(
            nowMillis = at(7, 8),
            queue = listOf(1L, 2L),
            fixed = listOf(at(9, 19, 10)),
        )

        assertEquals(at(7, 19), plan.timeFor(1L))
        assertEquals("Wednesday is taken, so this lands on Saturday", at(12, 11), plan.timeFor(2L))
    }

    @Test
    fun `a fixed post further than half an hour away leaves the slot alone`() {
        val plan = planAt(
            nowMillis = at(7, 8),
            queue = listOf(1L, 2L),
            fixed = listOf(at(9, 19, 31)),
        )

        assertEquals(at(9, 19), plan.timeFor(2L))
    }

    // ── Daylight saving ────────────────────────────────────────────────────

    @Test
    fun `a slot at a local time that does not exist shifts forward`() {
        // 2026-03-29, London: the clocks jump 01:00 to 02:00, so 01:30 never happens.
        val slots = listOf(Slot(DayOfWeek.SUNDAY, 1, 30))
        val saturdayNoon = at(day = 28, hour = 12, month = 3, zone = LONDON)

        val next = QueuePlanner.slotTimesFrom(slots, saturdayNoon, LONDON).first()
        val local = Instant.ofEpochMilli(next).atZone(LONDON)

        assertEquals("the hour that vanished becomes the next real one", 2, local.hour)
        assertEquals(30, local.minute)
    }

    @Test
    fun `a slot at a local time that happens twice takes the first`() {
        // 2026-10-25, London: the clocks go back, so 01:30 happens at +01:00 then +00:00.
        val slots = listOf(Slot(DayOfWeek.SUNDAY, 1, 30))
        val saturdayNoon = at(day = 24, hour = 12, month = 10, zone = LONDON)

        val next = QueuePlanner.slotTimesFrom(slots, saturdayNoon, LONDON).first()
        val local = Instant.ofEpochMilli(next).atZone(LONDON)

        assertEquals(1, local.hour)
        assertEquals("the earlier of the two 01:30s", ZoneOffset.ofHours(1), local.offset)
    }

    // ── What the worker asks when an alarm fires ───────────────────────────

    @Test
    fun `a queued post that is not due yet waits`() {
        val now = at(7, 18)
        assertEquals(
            QueuedAction.WaitUntil(at(7, 19)),
            QueuePlanner.actionForQueued(at(7, 19), now, TWO_HOURS),
        )
    }

    @Test
    fun `a queued post due right now publishes`() {
        assertEquals(
            QueuedAction.PublishNow,
            QueuePlanner.actionForQueued(at(7, 19), at(7, 19), TWO_HOURS),
        )
    }

    @Test
    fun `a queued post late but inside the window still publishes`() {
        assertEquals(
            QueuedAction.PublishNow,
            QueuePlanner.actionForQueued(at(7, 19), at(7, 19) + TWO_HOURS, TWO_HOURS),
        )
    }

    @Test
    fun `a queued post past the window rolls forward instead of failing`() {
        assertEquals(
            QueuedAction.RollForward,
            QueuePlanner.actionForQueued(at(7, 19), at(7, 19) + TWO_HOURS + 1, TWO_HOURS),
        )
    }

    // ── Which alarms are actually armed ────────────────────────────────────

    @Test
    fun `only assignments inside the horizon get an alarm`() {
        val now = at(7, 8)
        val plan = planAt(now, listOf(1L, 2L, 3L, 4L))

        val armed = QueuePlanner.alarmsToArm(plan, now).map { it.postId }

        // Mon 7th, Wed 9th and Sat 12th are inside a week of Monday morning;
        // Mon 14th is not.
        assertEquals(listOf(1L, 2L, 3L), armed)
    }

    @Test
    fun `a schedule sparser than the horizon still arms its first alarm`() {
        val now = at(7, 8)
        // One slot a month away — nothing is inside the 7-day horizon.
        val plan = planAt(now, listOf(1L), slots = listOf(Slot(DayOfWeek.MONDAY, 19, 0)))
            .let { QueuePlanner.Plan(listOf(it.assignments.first().copy(atMillis = now + 30 * DAY)), emptyList()) }

        val armed = QueuePlanner.alarmsToArm(plan, now)

        assertEquals("otherwise it would never fire at all", 1, armed.size)
        assertEquals(1L, armed.first().postId)
    }

    @Test
    fun `an empty plan arms nothing`() {
        assertTrue(
            QueuePlanner.alarmsToArm(QueuePlanner.Plan(emptyList(), emptyList()), at(7, 8)).isEmpty(),
        )
    }
}
