package com.autoinsta.data.repository

import com.autoinsta.data.db.dao.PostHistoryDao
import com.autoinsta.data.db.entities.PostHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Access point for post-history records written by PostWorker.
 */
class HistoryRepository(
    private val historyDao: PostHistoryDao,
) {

    /** Live list — the History screen subscribes to this. */
    fun observeAll(): Flow<List<PostHistoryEntity>> = historyDao.observeAll()

    /** All history entries for one original post id. */
    suspend fun getForPost(postId: Long): List<PostHistoryEntity> =
        historyDao.getForPost(postId)

    /** PostWorker calls this after every publish attempt. */
    suspend fun record(history: PostHistoryEntity) = historyDao.insert(history)

    suspend fun delete(historyId: Long) = historyDao.deleteById(historyId)

    suspend fun clearAll() = historyDao.deleteAll()
}
