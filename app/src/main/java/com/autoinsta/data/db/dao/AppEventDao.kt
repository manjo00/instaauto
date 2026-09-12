package com.autoinsta.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.autoinsta.data.db.entities.AppEventEntity

@Dao
interface AppEventDao {

    @Insert
    suspend fun insert(event: AppEventEntity)

    /** Newest first — how anyone actually reads a log. */
    @Query("SELECT * FROM app_events ORDER BY atMillis DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<AppEventEntity>

    @Query("SELECT * FROM app_events WHERE postId = :postId ORDER BY atMillis ASC, id ASC")
    suspend fun forPost(postId: Long): List<AppEventEntity>

    /**
     * Drop anything older than the retention window.
     *
     * A diagnostic log that grows without bound eventually becomes the problem it was
     * meant to diagnose.
     */
    @Query("DELETE FROM app_events WHERE atMillis < :cutoffMillis")
    suspend fun pruneOlderThan(cutoffMillis: Long): Int

    @Query("SELECT COUNT(*) FROM app_events")
    suspend fun count(): Int
}
