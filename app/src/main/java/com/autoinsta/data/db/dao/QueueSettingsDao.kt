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

    // ── The publish lease ──────────────────────────────────────────────────

    /**
     * Take the lease, if it is free or has expired.
     *
     * **One statement on purpose.** Reading "is anyone publishing?" and then writing "I am
     * now" as two calls leaves a gap in which a second worker reads the same free lease —
     * which is the exact race this is here to close. SQLite applies the `WHERE` and the
     * `SET` atomically, so exactly one caller can win.
     *
     * @return 1 if the lease is now ours, 0 if someone else holds a live one.
     */
    @Query("""
        UPDATE queue_settings
           SET publishingPostId = :postId, publishingSinceMillis = :nowMillis
         WHERE id = :id
           AND (publishingPostId IS NULL
             OR publishingPostId = :postId
             OR publishingSinceMillis IS NULL
             OR publishingSinceMillis < :staleBeforeMillis)
    """)
    suspend fun tryAcquireLease(
        postId: Long,
        nowMillis: Long,
        staleBeforeMillis: Long,
        id: Int = QueueSettingsEntity.SINGLETON_ID,
    ): Int

    /**
     * Give it back — but only if we still hold it.
     *
     * The `WHERE publishingPostId = :postId` matters: a post whose lease expired and was
     * taken over by someone else must not clear the new holder's claim when it eventually
     * finishes.
     */
    @Query("""
        UPDATE queue_settings
           SET publishingPostId = NULL, publishingSinceMillis = NULL
         WHERE id = :id AND publishingPostId = :postId
    """)
    suspend fun releaseLease(
        postId: Long,
        id: Int = QueueSettingsEntity.SINGLETON_ID,
    ): Int
}
