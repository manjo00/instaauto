package com.autoinsta.data.repository

import com.autoinsta.data.db.dao.MediaItemDao
import com.autoinsta.data.db.dao.ScheduledPostDao
import com.autoinsta.data.db.entities.MediaItemEntity
import com.autoinsta.data.db.entities.ScheduledPostEntity
import com.autoinsta.data.db.relations.ScheduledPostWithMedia
import com.autoinsta.domain.model.PostStatus
import kotlinx.coroutines.flow.Flow

/**
 * Single access point for scheduled-post data.
 * All callers (ViewModels, PostWorker, BootReceiver) go through here —
 * no one talks to the DAOs directly.
 */
class PostRepository(
    private val postDao: ScheduledPostDao,
    private val mediaDao: MediaItemDao,
) {

    // ── Observe ────────────────────────────────────────────────────────────

    /** Live list of all posts (queue + history). UI subscribes to this. */
    fun observeAll(): Flow<List<ScheduledPostWithMedia>> = postDao.observeAll()

    /** Live list of SCHEDULED posts only — the active queue. */
    fun observeScheduled(): Flow<List<ScheduledPostWithMedia>> = postDao.observeScheduled()

    // ── Read ───────────────────────────────────────────────────────────────

    suspend fun getById(postId: Long): ScheduledPostWithMedia? = postDao.getById(postId)

    /** Posts already past their scheduled time — used by BootReceiver. */
    suspend fun getOverduePending(nowMillis: Long): List<ScheduledPostWithMedia> =
        postDao.getOverduePending(nowMillis)

    /** All SCHEDULED posts — used by BootReceiver to re-enqueue alarms. */
    suspend fun getAllScheduled(): List<ScheduledPostWithMedia> = postDao.getAllScheduled()

    // ── Write ──────────────────────────────────────────────────────────────

    /**
     * Insert a new post + its media in a single operation.
     * Returns the new post's row id.
     */
    suspend fun insertPost(
        post: ScheduledPostEntity,
        mediaItems: List<MediaItemEntity>,
    ): Long {
        val postId = postDao.insert(post)
        val itemsWithPostId = mediaItems.map { it.copy(postId = postId) }
        mediaDao.insertAll(itemsWithPostId)
        return postId
    }

    suspend fun updatePost(post: ScheduledPostEntity) = postDao.update(post)

    suspend fun updateStatus(
        postId: Long,
        status: PostStatus,
        workRequestId: String? = null,
    ) = postDao.updateStatus(postId, status, workRequestId)

    /** Stamp a media item's Cloudinary URL once it has been uploaded. */
    suspend fun updateCloudinaryUrl(mediaItemId: Long, url: String) =
        mediaDao.updateCloudinaryUrl(mediaItemId, url)

    /**
     * Delete a post and all its media (FK cascade removes media automatically).
     */
    suspend fun deletePost(postId: Long) = postDao.deleteById(postId)
}
