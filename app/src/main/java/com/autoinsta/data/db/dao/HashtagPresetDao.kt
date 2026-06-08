package com.autoinsta.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.autoinsta.data.db.entities.HashtagPresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HashtagPresetDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(preset: HashtagPresetEntity): Long

    @Update
    suspend fun update(preset: HashtagPresetEntity)

    @Query("DELETE FROM hashtag_presets WHERE id = :presetId")
    suspend fun deleteById(presetId: Long)

    /** All presets, alphabetical by name — for the picker list. */
    @Query("SELECT * FROM hashtag_presets ORDER BY name ASC")
    fun observeAll(): Flow<List<HashtagPresetEntity>>

    @Query("SELECT * FROM hashtag_presets WHERE id = :presetId")
    suspend fun getById(presetId: Long): HashtagPresetEntity?
}
