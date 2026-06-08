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
}
