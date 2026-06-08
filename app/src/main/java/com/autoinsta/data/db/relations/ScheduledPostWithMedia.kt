package com.autoinsta.data.db.relations

import androidx.room.Embedded
import androidx.room.Relation
import com.autoinsta.data.db.entities.MediaItemEntity
import com.autoinsta.data.db.entities.ScheduledPostEntity

/**
 * Room multimap — a post bundled with all its media items.
 * Used everywhere we need to display or process a full post (UI queue, PostWorker).
 */
data class ScheduledPostWithMedia(
    @Embedded val post: ScheduledPostEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "postId",
    )
    val mediaItems: List<MediaItemEntity>,
)
