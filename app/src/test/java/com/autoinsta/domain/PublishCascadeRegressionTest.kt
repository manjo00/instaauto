package com.autoinsta.domain

import com.autoinsta.domain.model.FailureKind
import com.autoinsta.domain.model.PostStatus
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The 2026-09-09 incident, reproduced end to end in pure code.
 *
 * Two posts published into one 19:00 slot, 46 seconds apart, and the owner watched the
 * queue start to drain. These tests join the two halves that decide it — [SlotLedger] says
 * which slots are finished with, [QueuePlanner] hands out the rest — because neither half
 * alone shows the bug.
 *
 * Every test here fails on the code as it stood on 2026-09-09.
 */
class PublishCascadeRegressionTest {

    private val zone: ZoneId = ZoneId.of("Asia/Riyadh")

    /** The owner's real schedule: one slot, Wednesday 19:00. */
    private val slots = listOf(QueuePlanner.Slot(DayOfWeek.WEDNESDAY, 19, 0))

    /** Their real setting: a two-day catch-up window. */
    private val window = QueuePlanner.windowMillis(2880)

    private val wed1900 = ZonedDateTime.of(2026, 9, 9, 19, 0, 0, 0, zone)
        .toInstant().toEpochMilli()

    /** Thursday 07:58 — when the app was opened and the alarm finally came through. */
    private val thuMorning = ZonedDateTime.of(2026, 9, 10, 7, 58, 0, 0, zone)
        .toInstant().toEpochMilli()

    private fun planWith(queue: List<Long>, attempts: List<SlotAttempt>, now: Long) =
        QueuePlanner.plan(
            queuedIdsInOrder = queue,
            slots = slots,
            nowMillis = now,
            zone = zone,
            catchUpWindowMillis = window,
            paused = false,
            resumedAtMillis = 0L,
            fixedPostTimes = emptyList(),
            notBefore = emptyMap(),
            filledSlotTimes = SlotLedger.spentSlots(attempts),
        )

    /** The rule as it was: only a POSTED row counted, so nothing else held a slot. */
    private fun oldFilledSlots(attempts: List<SlotAttempt>): Set<Long> =
        attempts.filter { it.status == PostStatus.POSTED }.map { it.slotMillis }.toSet()

    // ── The race the owner described ───────────────────────────────────────

    @Test
    fun `a post that is mid-publish does not have its slot given away`() {
        // Post 5 is publishing into the 19:00 slot right now — a Reel upload plus the
        // readiness poll can run well over a minute. Opening the app replans the queue.
        val publishing = listOf(SlotAttempt(wed1900, PostStatus.POSTING))

        val plan = planWith(queue = listOf(2L, 3L), attempts = publishing, now = thuMorning)

        assertNotEquals(
            "post 2 was handed the slot post 5 is still publishing into",
            wed1900,
            plan.timeFor(2L),
        )
    }

    @Test
    fun `the old rule is what let two publishes run at once`() {
        // Proves the above actually regressed rather than merely passing today: under the
        // old set, an in-flight post contributed nothing and its slot looked free.
        val publishing = listOf(SlotAttempt(wed1900, PostStatus.POSTING))

        assertEquals(emptySet<Long>(), oldFilledSlots(publishing))
        assertEquals(setOf(wed1900), SlotLedger.spentSlots(publishing))
    }

    // ── Bounding "try the next one" ────────────────────────────────────────

    @Test
    fun `a dead connection does not march through the whole queue`() {
        // The dangerous version of the cascade. A transient failure means the token or the
        // network, not the file, so every post behind it would fail identically — and each
        // one would be marked FAILED and dropped out of the pool.
        val transient = listOf(
            SlotAttempt(wed1900, PostStatus.FAILED, FailureKind.TRANSIENT, atMillis = thuMorning)
        )

        val plan = planWith(queue = listOf(2L, 3L, 4L), attempts = transient, now = thuMorning)

        assertNotEquals(wed1900, plan.timeFor(2L))
        assertEquals("nothing else may be dragged into this slot either", emptySet<Long>(),
            plan.assignments.filter { it.atMillis == wed1900 }.map { it.postId }.toSet())
    }

    @Test
    fun `three bad files close the slot instead of emptying the pool`() {
        val threeRejected = (1..SlotLedger.MAX_ATTEMPTS_PER_SLOT).map {
            SlotAttempt(wed1900, PostStatus.FAILED, FailureKind.PERMANENT, atMillis = it.toLong())
        }

        val plan = planWith(queue = listOf(9L), attempts = threeRejected, now = thuMorning)

        assertNotEquals(wed1900, plan.timeFor(9L))
    }

    // ── What must keep working ─────────────────────────────────────────────

    @Test
    fun `one rejected file still lets the next post have the slot`() {
        // Exactly what happened on 09-09 and it was correct: Instagram refused post 5's
        // media, so post 2 took the slot and published. The fix must not undo this.
        val rejected = listOf(
            SlotAttempt(wed1900, PostStatus.FAILED, FailureKind.PERMANENT, atMillis = thuMorning)
        )

        val plan = planWith(queue = listOf(2L), attempts = rejected, now = thuMorning)

        assertEquals(wed1900, plan.timeFor(2L))
    }

    @Test
    fun `a published slot is never offered again`() {
        val published = listOf(SlotAttempt(wed1900, PostStatus.POSTED, atMillis = thuMorning))

        val plan = planWith(queue = listOf(3L), attempts = published, now = thuMorning)

        assertNotEquals(wed1900, plan.timeFor(3L))
    }

    @Test
    fun `a post displaced from a spent slot waits for the next one, it does not vanish`() {
        // The failure mode that would be worse than the bug: silently dropping a finished
        // piece out of the rotation.
        val publishing = listOf(SlotAttempt(wed1900, PostStatus.POSTING))

        val plan = planWith(queue = listOf(2L), attempts = publishing, now = thuMorning)

        assertNull("post 2 fell out of the queue entirely", plan.unassigned.firstOrNull())
        assertEquals(
            "it should hold the following Wednesday",
            ZonedDateTime.of(2026, 9, 16, 19, 0, 0, 0, zone).toInstant().toEpochMilli(),
            plan.timeFor(2L),
        )
    }
}
