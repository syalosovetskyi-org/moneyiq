package org.syalosovetskyi.onemoney.data.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, name ASC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): AccountEntity?

    @Query("SELECT SUM(balance) FROM accounts WHERE includeInTotal = 1")
    fun getTotalBalance(): Flow<Double?>

    @Upsert
    suspend fun insertAccount(account: AccountEntity): Long

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("UPDATE accounts SET balance = balance + :delta WHERE id = :accountId")
    suspend fun updateBalance(accountId: Long, delta: Double)

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Query("UPDATE accounts SET isDefault = 0")
    suspend fun clearAllDefault()

    @Query("UPDATE accounts SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: Long)

    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllAccountsOnce(): List<AccountEntity>

    @Upsert
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()

    @Query("UPDATE accounts SET balance = 0")
    suspend fun resetAllBalances()
}
