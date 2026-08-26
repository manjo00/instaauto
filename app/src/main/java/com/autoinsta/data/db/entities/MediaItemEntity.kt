package com.autoinsta.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.autoinsta.domain.MediaFit
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

    /**
     * Pixel dimensions, measured when the file is imported.
     *
     * Needed *before* upload so the compose screen can say whether Instagram will accept
     * the shape, and so the fitting editor can draw an accurate guide. Cloudinary reports
     * dimensions too, but only after uploading — far too late to ask the owner anything.
     *
     * Zero means "not measured" (an older row, or a video).
     */
    val widthPx: Int = 0,
    val heightPx: Int = 0,

    /**
     * How this item should be brought inside Instagram's 4:5–1.91:1 window.
     * Chosen per item in the fitting editor. Added in schema v3.
     */
    val fitMode: MediaFit.Mode = MediaFit.Mode.PAD,

    /**
     * Which part of the image survives a crop, as a fraction from 0 to 1.
     *
     * 0 = top (or left), 0.5 = centre, 1 = bottom (or right). Stored normalised rather
     * than in pixels so it stays meaningful if the same choice is ever applied to a
     * different export of the same artwork.
     */
    val cropOffset: Float = 0.5f,
)
