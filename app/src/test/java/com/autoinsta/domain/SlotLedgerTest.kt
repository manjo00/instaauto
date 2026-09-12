package com.autoinsta.domain

import com.autoinsta.domain.model.FailureKind
import com.autoinsta.domain.model.PostStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which slots are finished with.
 *
 * The incident these exist for: on 2026-09-09 two posts published into the same 19:00
 * slot, 46 seconds apart, because the old rule was `status = 'POSTED'` and a **failed**
 * post therefore left its slot looking untouched.
 */
class SlotLedgerTest {

    private val slot = 1_757_444_400_000L // Wed 2026-09-09 19:00
    private val nextSlot = slot + 7 * 24 * 60 * 60 * 1000L

    // ── The incident ───────────────────────────────────────────────────────

    @Test
    fun `a permanent failure lets the next post have the slot`() {
        // Post 5 was rejected by Instagram: "Only photo or video can be accepted". That is
        // about that file, so the next piece deserves the slot. This is the owner's
        // stated intent, and it is what actually happened.
        val spent = SlotLedger.spentSlots(
            listOf(SlotAttempt(slot, PostStatus.FAILED, FailureKind.PERMANENT, atMillis = 1))
        )

        assertFalse("a bad file must not cost the slot", slot in spent)
    }

    @Test
    fun `once something publishes, the slot is closed`() {
        // The half that already worked — and the only reason the 09-09 cascade stopped at
        // two rather than draining the pool.
        val spent = SlotLedger.spentSlots(
            listOf(
                SlotAttempt(slot, PostStatus.FAILED, FailureKind.PERMANENT, atMillis = 1),
                SlotAttempt(slot, PostStatus.POSTED, atMillis = 2),
            )
        )

        assertTrue(slot in spent)
    }

    @Test
    fun `a post that is publishing right now holds its slot`() {
        // The race the owner described: publishing takes 30-90 seconds, and for all of it
        // the post used to match neither queue query — so a replan could hand the same
        // slot to someone else and two publishes would run at once.
        val spent = SlotLedger.spentSlots(listOf(SlotAttempt(slot, PostStatus.POSTING)))

        assertTrue("an in-flight post must hold its slot", slot in spent)
    }

    // ── Not letting "try the next one" mean "empty the queue" ──────────────

    @Test
    fun `a transient failure closes the slot rather than burning the queue`() {
        // No network or a dead token will fail the next post in exactly the same way.
        // Walking the queue here would mark every finished piece FAILED in about a minute.
        val spent = SlotLedger.spentSlots(
            listOf(SlotAttempt(slot, PostStatus.FAILED, FailureKind.TRANSIENT, atMillis = 1))
        )

        assertTrue(slot in spent)
    }

    @Test
    fun `the most recent outcome decides, not the worst one`() {
        val spent = SlotLedger.spentSlots(
            listOf(
                SlotAttempt(slot, PostStatus.FAILED, FailureKind.TRANSIENT, atMillis = 1),
                SlotAttempt(slot, PostStatus.FAILED, FailureKind.PERMANENT, atMillis = 2),
            )
        )

        assertFalse("the network came back; the last file was simply bad", slot in spent)
    }

    @Test
    fun `a run of bad files gives up rather than draining the pool`() {
        val threeBadFiles = (1..SlotLedger.MAX_ATTEMPTS_PER_SLOT).map {
            SlotAttempt(slot, PostStatus.FAILED, FailureKind.PERMANENT, atMillis = it.toLong())
        }

        assertTrue(slot in SlotLedger.spentSlots(threeBadFiles))
    }

    @Test
    fun `under the cap the slot is still open`() {
        val twoBadFiles = (1..SlotLedger.MAX_ATTEMPTS_PER_SLOT - 1).map {
            SlotAttempt(slot, PostStatus.FAILED, FailureKind.PERMANENT, atMillis = it.toLong())
        }

        assertFalse(slot in SlotLedger.spentSlots(twoBadFiles))
    }

    // ── Slots are independent ──────────────────────────────────────────────

    @Test
    fun `one spent slot does not close another`() {
        val spent = SlotLedger.spentSlots(
            listOf(
                SlotAttempt(slot, PostStatus.POSTED, atMillis = 1),
                SlotAttempt(nextSlot, PostStatus.FAILED, FailureKind.PERMANENT, atMillis = 2),
            )
        )

        assertEquals(setOf(slot), spent)
    }

    @Test
    fun `nothing attempted, nothing spent`() {
        assertEquals(emptySet<Long>(), SlotLedger.spentSlots(emptyList()))
    }

    @Test
    fun `a failure of unrecorded kind does not freeze a slot`() {
        // Every history row written before v5 has a null failureKind. Treating unknown as
        // blocking would let old rows silently close slots after the upgrade.
        val spent = SlotLedger.spentSlots(
            listOf(SlotAttempt(slot, PostStatus.FAILED, failureKind = null, atMillis = 1))
        )

        assertFalse(slot in spent)
    }
}
