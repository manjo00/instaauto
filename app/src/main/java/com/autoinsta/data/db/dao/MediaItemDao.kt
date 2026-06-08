package com.autoinsta.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.autoinsta.data.db.entities.MediaItemEntity

@Dao
interface MediaItemDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<MediaItemEntity>)

    @Update
    suspend fun update(item: MediaItemEntity)

    /** Stamp the Cloudinary URL once upload succeeds. */
    @Query("UPDATE media_items SET cloudinaryUrl = :url WHERE id = :itemId")
    suspend fun updateCloudinaryUrl(itemId: Long, url: String)

    @Query("SELECT * FROM media_items WHERE postId = :postId ORDER BY orderIndex ASC")
    suspend fun getForPost(postId: Long): List<MediaItemEntity>

    /** Cascade delete handles this via FK, but useful for targeted cleanup. */
    @Query("DELETE FROM media_items WHERE postId = :postId")
    suspend fun deleteForPost(postId: Long)
}
