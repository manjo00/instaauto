package com.autoinsta.domain.model

/**
 * What to do with a post whose scheduled time passed while the phone was off,
 * out of battery, or otherwise unable to fire.
 *
 * Chosen per post, because the right answer genuinely differs: a "good morning" piece
 * arriving at 4pm is worse than not arriving, while an evergreen art piece is fine
 * whenever it lands.
 */
enum class MissedPostPolicy {
    /** Publish as soon as the device is able, no matter how late. */
    POST_ANYWAY,

    /**
     * Publish only if still within the grace period
     * ([com.autoinsta.domain.ScheduleCalculator.MISSED_GRACE_MILLIS]);
     * otherwise mark FAILED so nothing stale goes out unnoticed. The default.
     */
    POST_IF_RECENT,

    /** Never publish on its own — wait in the queue for the user to decide. */
    ASK_ME,
}
