package com.autoinsta.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/**
 * Turns "these days and times" plus "these posts, in this order" into actual publish times.
 *
 * ## Why this exists
 * Deciding *when* at the moment a piece is finished is the worst time to decide it. The
 * queue inverts that: the owner sets a rhythm once, then only ever decides *what comes
 * next*. This object is the whole of that translation.
 *
 * ## The rule that must not blur
 * A queued post's position is the truth. Its `scheduledAt` is **derived** here and read
 * only by the alarm machinery and by the UI for display. Two fields that both look like
 * "when" will drift into disagreement unless which one is authoritative stays stated.
 *
 * ## Why it is pure
 * The interesting cases are the ones you can least afford to wait for: a slot missed by
 * three days, a phone off across a daylight-saving change, a queue reordered mid-week.
 * With `nowMillis` **and** the time zone passed in, all of them are instant unit tests.
 * Same pattern as [PostValidator] and [ScheduleCalculator].
 */
object QueuePlanner {

    /** One recurring opening in the week -- "Monday 19:00", in the owner's local time. */
    data class Slot(val dayOfWeek: DayOfWeek, val hour: Int, val minute: Int)

    /** A post and the moment the planner gave it. */
    data class Assignment(
        val postId: Long,
        val atMillis: Long,
        /** True when this fills a slot that has already passed but is still open. */
        val isCatchUp: Boolean,
    )

    /**
     * [unassigned] is not an error -- it is the queue being deliberately idle, because
     * it is paused or because no slots are defined yet.
     */
    data class Plan(
        val assignments: List<Assignment>,
        val unassigned: List<Long>,
    ) {
        fun timeFor(postId: Long): Long? =
            assignments.firstOrNull { it.postId == postId }?.atMillis

        fun isCatchUp(postId: Long): Boolean =
            assignments.firstOrNull { it.postId == postId }?.isCatchUp == true
    }

    /** What a queued post that has come due should do right now. */
    sealed interface QueuedAction {
        /** Not due yet. */
        data class WaitUntil(val atMillis: Long) : QueuedAction

        /** Due, or late but still inside the catch-up window. */
        data object PublishNow : QueuedAction

        /**
         * Too late for this slot. The post is **not** a failure -- it keeps its place
         * and the planner gives it the next one.
         */
        data object RollForward : QueuedAction
    }

    /** The choices offered for the catch-up window, in minutes: 1h, 2h, 1 day, 2 days. */
    val CATCH_UP_WINDOW_CHOICES_MINUTES: List<Int> = listOf(60, 120, 1_440, 2_880)

    const val DEFAULT_CATCH_UP_WINDOW_MINUTES: Int = 120

    /**
     * How close a slot may come to a fixed-time post before the planner skips it.
     * Two posts landing half an hour apart reads as a glitch and wastes the reach on
     * the second.
     */
    const val SLOT_COLLISION_WINDOW_MILLIS: Long = 30L * 60L * 1000L

    /**
     * How far ahead an alarm is actually armed. Everything beyond this has a displayed
     * date and no alarm yet -- a replan brings it into range later. Arming three months
     * of wake-ups the owner will reorder twice before they fire is pure waste.
     */
    const val ALARM_HORIZON_MILLIS: Long = 7L * 24L * 60L * 60L * 1000L

    /**
     * How far the slot search will walk before giving up.
     *
     * The sequence would otherwise be infinite, and it is filtered by values the owner
     * controls -- a hold date, a collision -- so "no match" has to be a finite answer
     * rather than a hang.
     */
    const val MAX_SLOT_SEARCH_DAYS: Long = 366L

    fun windowMillis(minutes: Int): Long = minutes * 60L * 1000L

    /**
     * Every slot time strictly after [afterMillis], ascending, for up to a year.
     *
     * Daylight saving is delegated to `java.time`: a local time that does not exist that
     * day (spring forward) shifts forward by the gap, and one that happens twice (fall
     * back) takes the first occurrence. Both are what a person means by "7pm".
     */
    fun slotTimesFrom(
        slots: List<Slot>,
        afterMillis: Long,
        zone: ZoneId,
    ): Sequence<Long> {
        if (slots.isEmpty()) return emptySequence()
        val byDay: Map<DayOfWeek, List<Slot>> = slots
            .groupBy { it.dayOfWeek }
            .mapValues { (_, daySlots) ->
                daySlots.sortedWith(compareBy({ it.hour }, { it.minute }))
            }

        val startDate = Instant.ofEpochMilli(afterMillis).atZone(zone).toLocalDate()

        return sequence {
            var date = startDate
            var daysWalked = 0L
            while (daysWalked <= MAX_SLOT_SEARCH_DAYS) {
                for (slot in byDay[date.dayOfWeek].orEmpty()) {
                    val at = epochMillisOf(date, slot, zone)
                    if (at > afterMillis) yield(at)
                }
                date = date.plusDays(1)
                daysWalked++
            }
        }
    }

    /**
     * The slot that has passed but is still open, if there is one.
     *
     * Open means: it happened, it happened within [catchUpWindowMillis], and it did not
     * happen while the queue was paused ([resumedAtMillis] guards that -- honouring a
     * slot the owner deliberately paused through would betray the toggle).
     *
     * The most recent one wins. If a weekend of slots went by, only the last is offered,
     * because catching every one of them up means several posts landing minutes apart.
     */
    fun openCatchUpSlot(
        slots: List<Slot>,
        nowMillis: Long,
        zone: ZoneId,
        catchUpWindowMillis: Long,
        resumedAtMillis: Long = 0L,
    ): Long? {
        if (slots.isEmpty() || catchUpWindowMillis <= 0L) return null
        val earliest = maxOf(nowMillis - catchUpWindowMillis, resumedAtMillis)
        if (earliest > nowMillis) return null
        return slotTimesFrom(slots, earliest - 1, zone)
            .takeWhile { it <= nowMillis }
            .lastOrNull()
    }

    /**
     * Hand every queued post a time.
     *
     * [slots] must already be filtered to the enabled ones. [fixedPostTimes] are the
     * times of posts the owner pinned by hand, so the planner can stay out of their way.
     * [notBefore] holds the "wait for the next slot instead" answer, per post.
     *
     * Rules, in order:
     * 1. Paused, or no slots, or nothing queued, means everything unassigned. No alarms.
     * 2. At most **one** catch-up, and only for the post at the head of the queue.
     * 3. Everyone else takes the next future slots, in queue order.
     * 4. A slot within [SLOT_COLLISION_WINDOW_MILLIS] of a fixed post is skipped.
     * 5. A post's [notBefore] pushes it past any slot earlier than that.
     */
    fun plan(
        queuedIdsInOrder: List<Long>,
        slots: List<Slot>,
        nowMillis: Long,
        zone: ZoneId,
        catchUpWindowMillis: Long,
        paused: Boolean = false,
        resumedAtMillis: Long = 0L,
        fixedPostTimes: List<Long> = emptyList(),
        notBefore: Map<Long, Long> = emptyMap(),
    ): Plan {
        if (paused || slots.isEmpty() || queuedIdsInOrder.isEmpty()) {
            return Plan(assignments = emptyList(), unassigned = queuedIdsInOrder)
        }

        val assignments = mutableListOf<Assignment>()
        var remaining = queuedIdsInOrder

        // Rule 2 -- only the head of the queue can fill an open slot. Letting the second
        // post take it when the first declined would reorder the queue behind the
        // owner's back, which is worse than simply leaving the slot unfilled.
        val open = openCatchUpSlot(slots, nowMillis, zone, catchUpWindowMillis, resumedAtMillis)
        val head = remaining.first()
        if (open != null &&
            !collidesWithFixed(open, fixedPostTimes) &&
            (notBefore[head] ?: Long.MIN_VALUE) <= open
        ) {
            assignments += Assignment(head, open, isCatchUp = true)
            remaining = remaining.drop(1)
        }

        // Rule 3 -- the rest take future slots in order.
        val future = slotTimesFrom(slots, nowMillis, zone)
            .filterNot { collidesWithFixed(it, fixedPostTimes) }
            .iterator()

        val unassigned = mutableListOf<Long>()
        for (postId in remaining) {
            val floor = notBefore[postId] ?: Long.MIN_VALUE
            var at: Long? = null
            while (future.hasNext()) {
                val candidate = future.next()
                if (candidate >= floor) {
                    at = candidate
                    break
                }
            }
            if (at != null) {
                assignments += Assignment(postId, at, isCatchUp = false)
            } else {
                // Walked past the search horizon: the queue is longer than a year of slots.
                unassigned += postId
            }
        }

        return Plan(assignments = assignments, unassigned = unassigned)
    }

    /**
     * What to do with a queued post whose alarm has just fired.
     *
     * The important difference from [ScheduleCalculator.actionFor]: a queued post is
     * never failed for being late. It rolls forward and keeps its place in the queue.
     */
    fun actionForQueued(
        scheduledAtMillis: Long,
        nowMillis: Long,
        catchUpWindowMillis: Long,
    ): QueuedAction = when {
        scheduledAtMillis > nowMillis -> QueuedAction.WaitUntil(scheduledAtMillis)
        nowMillis - scheduledAtMillis <= catchUpWindowMillis -> QueuedAction.PublishNow
        else -> QueuedAction.RollForward
    }

    /**
     * Which assignments deserve a real alarm now.
     *
     * Always the first one -- otherwise a schedule sparser than the horizon would never
     * fire at all -- plus anything inside [ALARM_HORIZON_MILLIS].
     */
    fun alarmsToArm(plan: Plan, nowMillis: Long): List<Assignment> {
        val sorted = plan.assignments.sortedBy { it.atMillis }
        if (sorted.isEmpty()) return emptyList()
        val within = sorted.filter { it.atMillis - nowMillis <= ALARM_HORIZON_MILLIS }
        return within.ifEmpty { listOf(sorted.first()) }
    }

    private fun collidesWithFixed(slotMillis: Long, fixedPostTimes: List<Long>): Boolean =
        fixedPostTimes.any { abs(it - slotMillis) < SLOT_COLLISION_WINDOW_MILLIS }

    private fun epochMillisOf(date: LocalDate, slot: Slot, zone: ZoneId): Long =
        ZonedDateTime.of(date, LocalTime.of(slot.hour, slot.minute), zone)
            .toInstant()
            .toEpochMilli()
}
