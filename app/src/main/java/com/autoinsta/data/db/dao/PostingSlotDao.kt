package com.autoinsta.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.autoinsta.data.db.entities.PostingSlotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PostingSlotDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(slot: PostingSlotEntity): Long

    @Update
    suspend fun update(slot: PostingSlotEntity)

    @Delete
    suspend fun delete(slot: PostingSlotEntity)

    @Query("DELETE FROM posting_slots WHERE id = :slotId")
    suspend fun deleteById(slotId: Long)

    /** Every slot, on or off, in the order a week reads. */
    @Query("SELECT * FROM posting_slots ORDER BY dayOfWeek ASC, hourOfDay ASC, minute ASC")
    fun observeAll(): Flow<List<PostingSlotEntity>>

    @Query("SELECT * FROM posting_slots ORDER BY dayOfWeek ASC, hourOfDay ASC, minute ASC")
    suspend fun getAll(): List<PostingSlotEntity>

    /** Only the slots that actually count — what the planner is given. */
    @Query("SELECT * FROM posting_slots WHERE enabled = 1 ORDER BY dayOfWeek ASC, hourOfDay ASC, minute ASC")
    suspend fun getEnabled(): List<PostingSlotEntity>
}
