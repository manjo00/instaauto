package com.autoinsta.data.repository

import com.autoinsta.data.db.dao.HashtagPresetDao
import com.autoinsta.data.db.entities.HashtagPresetEntity
import kotlinx.coroutines.flow.Flow

/**
 * Access point for hashtag preset data.
 */
class PresetRepository(
    private val presetDao: HashtagPresetDao,
) {

    fun observeAll(): Flow<List<HashtagPresetEntity>> = presetDao.observeAll()

    suspend fun getById(presetId: Long): HashtagPresetEntity? = presetDao.getById(presetId)

    suspend fun insert(preset: HashtagPresetEntity): Long = presetDao.insert(preset)

    suspend fun update(preset: HashtagPresetEntity) = presetDao.update(preset)

    suspend fun delete(presetId: Long) = presetDao.deleteById(presetId)
}
