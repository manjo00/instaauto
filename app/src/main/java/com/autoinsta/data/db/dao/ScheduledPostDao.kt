package com.autoinsta.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.autoinsta.data.db.entities.ScheduledPostEntity
import com.autoinsta.data.db.relations.ScheduledPostWithMedia
import com.autoinsta.domain.model.PostStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledPostDao {

    /** Insert a new post; returns the auto-generated row id. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(post: ScheduledPostEntity): Long

    @Update
    suspend fun update(post: ScheduledPostEntity)

    /** Convenience: update only the status + optional WorkManager request id. */
    @Query("""
        UPDATE scheduled_posts
        SET status = :status, workRequestId = :workRequestId
        WHERE id = :postId
    """)
    suspend fun updateStatus(
        postId: Long,
        status: PostStatus,
        workRequestId: String? = null,
    )

    @Query("DELETE FROM scheduled_posts WHERE id = :postId")
    suspend fun deleteById(postId: Long)

    /** All posts with their media, ordered newest-scheduled first. */
    @Transaction
    @Query("SELECT * FROM scheduled_posts ORDER BY scheduledAt DESC")
    fun observeAll(): Flow<List<ScheduledPostWithMedia>>

    /** Only SCHEDULED posts — the active queue shown in the UI. */
    @Transaction
    @Query("""
        SELECT * FROM scheduled_posts
        WHERE status = 'SCHEDULED'
        ORDER BY scheduledAt ASC
    """)
    fun observeScheduled(): Flow<List<ScheduledPostWithMedia>>

    /** One post by id, with media. Null if not found. */
    @Transaction
    @Query("SELECT * FROM scheduled_posts WHERE id = :postId")
    suspend fun getById(postId: Long): ScheduledPostWithMedia?

    /**
     * All SCHEDULED posts whose time has already passed.
     * Used by BootReceiver to reschedule alarms after device reboot.
     */
    @Transaction
    @Query("""
        SELECT * FROM scheduled_posts
        WHERE status = 'SCHEDULED' AND scheduledAt <= :nowMillis
    """)
    suspend fun getOverduePending(nowMillis: Long): List<ScheduledPostWithMedia>

    /** All SCHEDULED posts — list snapshot for BootReceiver (no Flow needed). */
    @Transaction
    @Query("SELECT * FROM scheduled_posts WHERE status = 'SCHEDULED'")
    suspend fun getAllScheduled(): List<ScheduledPostWithMedia>

    // ── The queue ──────────────────────────────────────────────────────────
    // Ordered by queuePosition, never by scheduledAt: position is the truth, and the
    // time is only what the planner made of it.

    @Transaction
    @Query("""
        SELECT * FROM scheduled_posts
        WHERE status = 'SCHEDULED' AND timingMode = 'QUEUED'
        ORDER BY queuePosition ASC
    """)
    fun observeQueued(): Flow<List<ScheduledPostWithMedia>>

    @Query("""
        SELECT id FROM scheduled_posts
        WHERE status = 'SCHEDULED' AND timingMode = 'QUEUED'
        ORDER BY queuePosition ASC
    """)
    suspend fun getQueuedIdsInOrder(): List<Long>

    /**
     * Slot times something has already been published into.
     *
     * This is what stops a wide catch-up window draining the pool: without it, the slot a
     * post just fired into still looks open on the very next replan, and the post behind
     * it goes out seconds later.
     */
    @Query("""
        SELECT scheduledAt FROM scheduled_posts
        WHERE status = 'POSTED' AND timingMode = 'QUEUED' AND scheduledAt >= :sinceMillis
    """)
    suspend fun getFilledSlotTimes(sinceMillis: Long): List<Long>

    /** Posts the owner pinned to a time, so the planner can stay out of their way. */
    @Query("""
        SELECT scheduledAt FROM scheduled_posts
        WHERE status = 'SCHEDULED' AND timingMode = 'FIXED'
    """)
    suspend fun getFixedScheduledTimes(): List<Long>

    /** Posts with a fixed time — the second section of the queue screen. */
    @Transaction
    @Query("""
        SELECT * FROM scheduled_posts
        WHERE status = 'SCHEDULED' AND timingMode = 'FIXED'
        ORDER BY scheduledAt ASC
    """)
    fun observeFixedScheduled(): Flow<List<ScheduledPostWithMedia>>

    /** Null when the queue is empty — the first post then takes position 0. */
    @Query("""
        SELECT MAX(queuePosition) FROM scheduled_posts
        WHERE status = 'SCHEDULED' AND timingMode = 'QUEUED'
    """)
    suspend fun maxQueuePosition(): Int?

    @Query("UPDATE scheduled_posts SET queuePosition = :position WHERE id = :postId")
    suspend fun updateQueuePosition(postId: Long, position: Int?)

    @Query("UPDATE scheduled_posts SET scheduledAt = :atMillis WHERE id = :postId")
    suspend fun updateScheduledAt(postId: Long, atMillis: Long)

    @Query("SELECT id, notBeforeMillis FROM scheduled_posts WHERE notBeforeMillis IS NOT NULL")
    suspend fun getNotBeforeHolds(): List<NotBeforeHold>

    /**
     * Take a post out of the pool without deleting it — it has published, or failed
     * permanently. Its [ScheduledPostEntity.timingMode] stays QUEUED so history still
     * shows how it was scheduled.
     */
    @Query("UPDATE scheduled_posts SET queuePosition = NULL WHERE id = :postId")
    suspend fun clearQueuePosition(postId: Long)
}

/** Row shape for [ScheduledPostDao.getNotBeforeHolds]. */
data class NotBeforeHold(
    val id: Long,
    val notBeforeMillis: Long,
)
