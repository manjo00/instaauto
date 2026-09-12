package com.autoinsta.domain

import com.autoinsta.domain.model.FailureKind
import com.autoinsta.domain.model.PostStatus

/**
 * One post's use of one slot.
 *
 * [failureKind] is null unless [status] is FAILED.
 */
data class SlotAttempt(
    val slotMillis: Long,
    val status: PostStatus,
    val failureKind: FailureKind? = null,
    /** Orders attempts within a slot; the latest one decides. */
    val atMillis: Long = 0L,
)

/**
 * Which posting slots are finished with, and may not be offered to another post.
 *
 * ## Why this exists
 * On 2026-09-09 two posts published into the same 19:00 slot, 46 seconds apart. The query
 * behind the old answer was `status = 'POSTED'`, so a post that **failed** left its slot
 * looking untouched — the next post inherited it, found the time already past, and fired
 * immediately. A second failure would have taken the one after that, and so on through the
 * pool.
 *
 * The rule is deliberately not SQL. It is the whole of the fix, it has four interacting
 * cases, and it has to be testable without a device.
 */
object SlotLedger {

    /**
     * How many posts may attempt one slot before it is abandoned.
     *
     * Three, not unlimited: the owner asked for the next post to be tried when one fails,
     * and a cap is what stops "try the next" from meaning "empty the queue" on a day when
     * several files are bad.
     */
    const val MAX_ATTEMPTS_PER_SLOT = 3

    /**
     * Slots no further post may take.
     *
     * A slot is spent when:
     * - something **published** into it — it did its job;
     * - something is **publishing** into it right now — in flight, and the reason a
     *   concurrent replan could previously hand the same slot out twice;
     * - the most recent attempt failed **transiently** — no network or a dead token will
     *   fail the next post in exactly the same way, so trying it only destroys it;
     * - it has already been attempted [MAX_ATTEMPTS_PER_SLOT] times.
     *
     * A slot whose only failures were **permanent** stays open: that media was the problem,
     * and the next piece deserves the slot. That is the owner's stated intent.
     */
    fun spentSlots(
        attempts: List<SlotAttempt>,
        maxAttemptsPerSlot: Int = MAX_ATTEMPTS_PER_SLOT,
    ): Set<Long> = attempts
        .groupBy { it.slotMillis }
        .filterValues { forSlot -> isSpent(forSlot, maxAttemptsPerSlot) }
        .keys

    private fun isSpent(forSlot: List<SlotAttempt>, maxAttemptsPerSlot: Int): Boolean {
        if (forSlot.any { it.status == PostStatus.POSTED }) return true
        if (forSlot.any { it.status == PostStatus.POSTING }) return true
        if (forSlot.size >= maxAttemptsPerSlot) return true

        // Ties broken by list order, so a caller that cannot supply timestamps still gets
        // "the last one wins" rather than an arbitrary pick.
        val latest = forSlot.withIndex()
            .maxWithOrNull(compareBy({ it.value.atMillis }, { it.index }))
            ?.value
        return latest?.failureKind == FailureKind.TRANSIENT
    }
}
