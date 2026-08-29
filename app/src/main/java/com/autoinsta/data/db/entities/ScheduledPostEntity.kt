package com.autoinsta.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.autoinsta.domain.model.MissedPostPolicy
import com.autoinsta.domain.model.PostStatus
import com.autoinsta.domain.model.PostType
import com.autoinsta.domain.model.TimingMode

/**
 * One scheduled (or historical) Instagram post.
 * Media files are stored separately in [MediaItemEntity] (one-to-many).
 */
@Entity(tableName = "scheduled_posts")
data class ScheduledPostEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val postType: PostType,

    val status: PostStatus,

    /** Caption text — the written description shown under the post. */
    val caption: String,

    /**
     * Hashtags as a single space-separated string, e.g. "#digitalart #illustration".
     * Stored raw so the user's exact formatting is preserved.
     */
    val hashtags: String,

    /**
     * Optional FK to [HashtagPresetEntity.id].
     * Null when the user typed hashtags manually or used no preset.
     */
    val presetId: Long?,

    /**
     * When to publish — epoch milliseconds (UTC).
     *
     * For a [TimingMode.FIXED] post this is the owner's own choice and nothing moves it.
     * For a [TimingMode.QUEUED] post it is **derived** by [com.autoinsta.domain.QueuePlanner]
     * from [queuePosition] and the posting schedule, and is rewritten on every replan.
     * Read it to arm an alarm or to show a date; never treat it as the queue's order.
     */
    val scheduledAt: Long,

    /**
     * Whether this post owns its time or takes the next free slot. Added in schema v4;
     * everything that existed before the queue is [TimingMode.FIXED], which is exactly
     * how it already behaved.
     */
    val timingMode: TimingMode = TimingMode.FIXED,

    /**
     * Place in the pool, 0-based. **This is the truth for a queued post's order** —
     * [scheduledAt] is only its consequence.
     *
     * Null for a fixed post, and for a queued post that has left the pool by publishing
     * or failing.
     */
    val queuePosition: Int? = null,

    /**
     * The earliest slot this post will accept, epoch millis.
     *
     * Set when the owner is warned that adding a post would fill a slot that just passed
     * and answers "wait for the next slot instead". Null the rest of the time.
     */
    val notBeforeMillis: Long? = null,

    /**
     * What to do if this post's time passes while the device is off or unable to fire.
     * Chosen per post — see [MissedPostPolicy]. Added in schema v2.
     */
    val missedPolicy: MissedPostPolicy = MissedPostPolicy.POST_IF_RECENT,

    /** Row creation time — epoch milliseconds. */
    val createdAt: Long,

    /**
     * WorkManager request UUID (as String) so we can cancel the job if the post
     * is deleted or rescheduled. Null until the worker is enqueued.
     */
    val workRequestId: String? = null,
)
