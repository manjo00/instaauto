package com.autoinsta.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.autoinsta.data.db.entities.QueueSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueSettingsDao {

    @Query("SELECT * FROM queue_settings WHERE id = :id")
    fun observe(id: Int = QueueSettingsEntity.SINGLETON_ID): Flow<QueueSettingsEntity?>

    @Query("SELECT * FROM queue_settings WHERE id = :id")
    suspend fun get(id: Int = QueueSettingsEntity.SINGLETON_ID): QueueSettingsEntity?

    /**
     * REPLACE rather than update: the row is seeded by the migration, but a database
     * created fresh at v4 has never run one, so the first write has to be able to
     * create it.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: QueueSettingsEntity)
}
