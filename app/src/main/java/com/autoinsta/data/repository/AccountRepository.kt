package com.autoinsta.data.repository

import com.autoinsta.data.db.dao.AccountDao
import com.autoinsta.data.db.entities.AccountEntity
import kotlinx.coroutines.flow.Flow

/**
 * Access point for the connected Instagram account.
 */
class AccountRepository(
    private val accountDao: AccountDao,
) {

    /** Observe the account — emits null when no account is connected. */
    fun observe(): Flow<AccountEntity?> = accountDao.observe()

    /** One-shot read — for workers that don't need a live stream. */
    suspend fun get(): AccountEntity? = accountDao.get()

    /** Called after successful OAuth + Graph API account discovery. */
    suspend fun save(account: AccountEntity) = accountDao.save(account)

    /** Called on logout. */
    suspend fun clear() = accountDao.clear()
}
