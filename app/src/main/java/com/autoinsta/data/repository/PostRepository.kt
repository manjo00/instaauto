package com.autoinsta.data.repository

import android.net.Uri
import com.autoinsta.data.db.dao.MediaItemDao
import com.autoinsta.data.db.dao.ScheduledPostDao
import com.autoinsta.data.db.entities.MediaItemEntity
import com.autoinsta.data.db.entities.ScheduledPostEntity
import com.autoinsta.data.db.relations.ScheduledPostWithMedia
import com.autoinsta.data.media.MediaFileStore
import com.autoinsta.domain.model.MediaType
import kotlinx.coroutines.flow.Flow
import com.autoinsta.domain.model.PostStatus

/**
 * One media file on its way into a post.
 *
 * [alreadyImported] tells the repository whether [sourceUri] is a picker address that
 * still needs copying into app storage (false — freshly picked) or a path we imported
 * on an earlier save (true — loaded back from the database while editing). Without
 * this flag an edit would re-copy every file on every save.
 */
data class MediaToSave(
    val sourceUri: String,
    val mediaType: MediaType,
    val alreadyImported: Boolean,
    val existingCloudinaryUrl: String? = null,
)

/**
 * Single access point for scheduled-post data.
 * All callers (ViewModels, PostWorker, BootReceiver) go through here —
 * no one talks to the DAOs directly.
 */
class PostRepository(
    private val postDao: ScheduledPostDao,
    private val mediaDao: MediaItemDao,
    private val mediaFileStore: MediaFileStore,
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
     * Insert a new post and copy its media into app-private storage.
     * Returns the new post's row id.
     *
     * The copy is what makes a scheduled post survive until its publish time —
     * see [MediaFileStore] for why the picker's own URI cannot be trusted to last.
     */
    suspend fun insertPost(
        post: ScheduledPostEntity,
        media: List<MediaToSave>,
    ): Long {
        val postId = postDao.insert(post)
        mediaDao.insertAll(importAll(media, postId))
        return postId
    }

    suspend fun updatePost(post: ScheduledPostEntity) = postDao.update(post)

    /**
     * Update a post AND replace its media set — used when the user edits a scheduled
     * post and may have added, removed, or reordered files.
     *
     * Files that the edit dropped are deleted from disk; if we only removed the rows
     * the bytes would sit in app storage forever with nothing pointing at them.
     */
    suspend fun updatePost(
        post: ScheduledPostEntity,
        media: List<MediaToSave>,
    ) {
        val previousPaths = mediaDao.getForPost(post.id).map { it.localUri }

        postDao.update(post)
        mediaDao.deleteForPost(post.id)
        val imported = importAll(media, post.id)
        mediaDao.insertAll(imported)

        val keptPaths = imported.map { it.localUri }.toSet()
        mediaFileStore.deleteAll(previousPaths.filterNot { it in keptPaths })
    }

    /** Stamp a media item's Cloudinary URL once it has been uploaded. */
    suspend fun updateCloudinaryUrl(mediaItemId: Long, url: String) =
        mediaDao.updateCloudinaryUrl(mediaItemId, url)

    suspend fun updateStatus(
        postId: Long,
        status: PostStatus,
        workRequestId: String? = null,
    ) = postDao.updateStatus(postId, status, workRequestId)

    /**
     * Delete a post, its media rows, and its media files.
     *
     * Room's foreign-key CASCADE removes the rows automatically — but it knows
     * nothing about the filesystem, so the files must be collected before the rows
     * disappear and deleted afterwards.
     */
    suspend fun deletePost(postId: Long) {
        val paths = mediaDao.getForPost(postId).map { it.localUri }
        postDao.deleteById(postId)
        mediaFileStore.deleteAll(paths)
    }

    // ── Internal ───────────────────────────────────────────────────────────

    private suspend fun importAll(
        media: List<MediaToSave>,
        postId: Long,
    ): List<MediaItemEntity> = media.mapIndexed { index, item ->
        val storedPath =
            if (item.alreadyImported) item.sourceUri
            else mediaFileStore.import(Uri.parse(item.sourceUri))

        MediaItemEntity(
            id = 0,
            postId = postId,
            mediaType = item.mediaType,
            localUri = storedPath,
            cloudinaryUrl = item.existingCloudinaryUrl,
            orderIndex = index,
        )
    }
}
