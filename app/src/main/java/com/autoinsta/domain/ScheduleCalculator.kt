package com.autoinsta.domain

import com.autoinsta.domain.model.MissedPostPolicy

/**
 * Every "when should this happen" decision in the app, as pure functions.
 *
 * Scheduling is the worst thing to debug on a device: checking whether a post fires
 * correctly means waiting for real time to pass, and the interesting cases (missed by
 * 30 seconds, missed by three days, the phone was off for a week) are the ones you can
 * least afford to wait for. Keeping the decisions here — with `nowMillis` passed in
 * rather than read from the system clock — turns all of them into instant unit tests.
 *
 * Same pattern as [PostValidator]: no Android imports, no clock of its own.
 */
object ScheduleCalculator {

    /**
     * How late a [MissedPostPolicy.POST_IF_RECENT] post may be and still go out.
     * One hour: late enough to absorb an overnight flat battery or a slow reboot,
     * short enough that nothing time-of-day-specific lands embarrassingly wrong.
     */
    const val MISSED_GRACE_MILLIS: Long = 60L * 60L * 1000L

    /** What should happen to a post right now. */
    sealed interface Action {
        /** Not due yet — arm an alarm for [atMillis]. */
        data class WaitUntil(val atMillis: Long) : Action

        /** Due (or acceptably late) — publish now. */
        data object PublishNow : Action

        /** Too late to publish, and the post said not to. */
        data object MarkMissed : Action

        /** Too late to decide automatically; the post said to ask. */
        data object AskUser : Action
    }

    /**
     * The single decision point, used by both the boot receiver (catching up on
     * everything after a reboot) and the worker (confirming it should still publish).
     */
    fun actionFor(
        scheduledAtMillis: Long,
        policy: MissedPostPolicy,
        nowMillis: Long,
    ): Action {
        if (scheduledAtMillis > nowMillis) {
            return Action.WaitUntil(scheduledAtMillis)
        }

        val latenessMillis = nowMillis - scheduledAtMillis
        return when (policy) {
            MissedPostPolicy.POST_ANYWAY -> Action.PublishNow
            MissedPostPolicy.POST_IF_RECENT ->
                if (latenessMillis <= MISSED_GRACE_MILLIS) Action.PublishNow else Action.MarkMissed
            MissedPostPolicy.ASK_ME ->
                // Only "ask" once it is actually late; a post firing exactly on time
                // needs no decision from anyone.
                if (latenessMillis == 0L) Action.PublishNow else Action.AskUser
        }
    }

    /** True when [scheduledAtMillis] has passed. */
    fun isOverdue(scheduledAtMillis: Long, nowMillis: Long): Boolean =
        scheduledAtMillis <= nowMillis

    /** How late this post is, in millis. Zero when it is not late. */
    fun latenessMillis(scheduledAtMillis: Long, nowMillis: Long): Long =
        (nowMillis - scheduledAtMillis).coerceAtLeast(0L)

    /**
     * The moment an alarm should actually be set for.
     *
     * `AlarmManager` refuses anything closer than a few seconds out and treats a time
     * in the past as "fire immediately", which is a stampede risk when re-arming a
     * backlog at boot. Clamping to a small floor keeps that orderly.
     */
    fun alarmTimeFor(scheduledAtMillis: Long, nowMillis: Long): Long =
        maxOf(scheduledAtMillis, nowMillis + MIN_ALARM_LEAD_MILLIS)

    /** Matches the platform's own `min_futurity` (measured at 5s) with headroom. */
    const val MIN_ALARM_LEAD_MILLIS: Long = 10_000L
}
