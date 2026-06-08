package com.autoinsta.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.autoinsta.data.db.entities.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    /**
     * REPLACE so a second login overwrites the existing row
     * (v1 single-account: id is always 1).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(account: AccountEntity)

    /** Observe the connected account — emits null when no account is connected. */
    @Query("SELECT * FROM account WHERE id = 1")
    fun observe(): Flow<AccountEntity?>

    /** One-shot read — useful in workers where Flow isn't needed. */
    @Query("SELECT * FROM account WHERE id = 1")
    suspend fun get(): AccountEntity?

    @Query("DELETE FROM account WHERE id = 1")
    suspend fun clear()
}
