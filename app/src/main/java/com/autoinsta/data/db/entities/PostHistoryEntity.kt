package com.autoinsta.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.autoinsta.domain.model.PostStatus
import com.autoinsta.domain.model.PostType

/**
 * Immutable record written by PostWorker after a publishing attempt.
 * Lives in the history list regardless of success or failure.
 *
 * Note: not a FK to ScheduledPostEntity because the user may delete the scheduled
 * post while the history record should remain visible.
 */
@Entity(tableName = "post_history")
data class PostHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** The original ScheduledPostEntity.id (denormalised — may refer to a deleted post). */
    val postId: Long,

    val postType: PostType,

    /** Snapshot of the caption at publish time. */
    val caption: String,

    /** Snapshot of the hashtags at publish time. */
    val hashtags: String,

    /** When the post was originally scheduled — epoch milliseconds. */
    val scheduledAt: Long,

    /** When PostWorker actually attempted publication — epoch milliseconds. */
    val postedAt: Long,

    /** POSTED on success, FAILED on error. */
    val status: PostStatus,

    /**
     * The Instagram media ID returned by the Graph API on success.
     * Null on failure.
     */
    val instagramMediaId: String? = null,

    /**
     * Human-readable error description on failure.
     * Null on success.
     */
    val errorMessage: String? = null,
)
