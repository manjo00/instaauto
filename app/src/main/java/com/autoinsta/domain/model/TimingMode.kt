package com.autoinsta.domain.model

/**
 * How a post's publish time is decided.
 *
 * The two are genuinely different ideas, not two settings of one idea: a [FIXED] post
 * owns its time, while a [QUEUED] post owns a *position* and is handed a time by
 * [com.autoinsta.domain.QueuePlanner]. Keeping them apart is what lets the queue be
 * reordered without touching anything the owner pinned to a date on purpose.
 */
enum class TimingMode {
    /** The owner picked an exact date and time. Nothing moves it. */
    FIXED,

    /**
     * The post sits in the pool and takes the next free slot in the posting schedule.
     * Its `scheduledAt` is derived — see [com.autoinsta.domain.QueuePlanner].
     */
    QUEUED,
}
