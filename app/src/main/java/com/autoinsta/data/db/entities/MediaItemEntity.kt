package com.autoinsta.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.autoinsta.domain.model.MediaType

/**
 * One media file belonging to a scheduled post.
 * A SINGLE_IMAGE/REEL post has exactly 1; a CAROUSEL post has 2–10.
 *
 * Foreign key cascades delete so media rows are cleaned up when the post is removed.
 */
@Entity(
    tableName = "media_items",
    foreignKeys = [
        ForeignKey(
            entity = ScheduledPostEntity::class,
            parentColumns = ["id"],
            childColumns = ["postId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("postId")],
)
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** FK → ScheduledPostEntity.id */
    val postId: Long,

    val mediaType: MediaType,

    /**
     * Content URI pointing to the file on-device, e.g. "content://media/...".
     * Persisted as a String; resolved to a Uri when the worker uploads it.
     */
    val localUri: String,

    /**
     * Public HTTPS URL returned by Cloudinary after upload.
     * Null until the worker has uploaded this item.
     */
    val cloudinaryUrl: String? = null,

    /**
     * Zero-based order within the carousel.
     * Always 0 for SINGLE_IMAGE and REEL posts.
     */
    val orderIndex: Int = 0,
)
