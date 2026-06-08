package com.autoinsta.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.autoinsta.data.db.entities.PostHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PostHistoryDao {

    /** PostWorker calls this once after a publish attempt (success or failure). */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(history: PostHistoryEntity)

    /** All history entries, newest first — for the History screen. */
    @Query("SELECT * FROM post_history ORDER BY postedAt DESC")
    fun observeAll(): Flow<List<PostHistoryEntity>>

    /** All entries for a specific original post id. */
    @Query("SELECT * FROM post_history WHERE postId = :postId ORDER BY postedAt DESC")
    suspend fun getForPost(postId: Long): List<PostHistoryEntity>

    @Query("DELETE FROM post_history WHERE id = :historyId")
    suspend fun deleteById(historyId: Long)

    @Query("DELETE FROM post_history")
    suspend fun deleteAll()
}
