package com.autoinsta.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.autoinsta.domain.model.MissedPostPolicy
import com.autoinsta.domain.model.PostStatus
import com.autoinsta.domain.model.PostType

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

    /** When to publish — stored as epoch milliseconds (UTC). */
    val scheduledAt: Long,

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
