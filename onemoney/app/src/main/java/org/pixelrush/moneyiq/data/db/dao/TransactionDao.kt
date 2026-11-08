package org.syalosovetskyi.onemoney.data.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.syalosovetskyi.onemoney.data.db.entities.TransactionEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType

data class TransactionWithDetails(
    val id: Long,
    val type: TransactionType,
    val amount: Double,
    val accountId: Long,
    val accountName: String,
    val accountColor: String,
    val accountCurrency: String,
    val toAccountId: Long?,
    val toAccountName: String?,
    val categoryId: Long?,
    val categoryName: String?,
    val categoryColor: String?,
    val categoryIcon: String?,
    val note: String,
    val date: Long
)

data class CategorySpending(
    val categoryId: Long,
    val categoryName: String,
    val categoryColor: String,
    val categoryIcon: String,
    val total: Double,
    val count: Int = 0
)

@Dao
interface TransactionDao {

    @Query("""
        SELECT t.id, t.type, t.amount, t.accountId,
               a.name AS accountName, a.colorHex AS accountColor,
               a.currency AS accountCurrency,
               t.toAccountId, ta.name AS toAccountName,
               t.categoryId, c.name AS categoryName,
               c.colorHex AS categoryColor, c.icon AS categoryIcon,
               t.note, t.date
        FROM transactions t
        INNER JOIN accounts a ON t.accountId = a.id
        LEFT JOIN accounts ta ON t.toAccountId = ta.id
        LEFT JOIN categories c ON t.categoryId = c.id
        ORDER BY t.date DESC
        LIMIT :limit OFFSET :offset
    """)
    fun getTransactionsPaged(limit: Int = 50, offset: Int = 0): Flow<List<TransactionWithDetails>>

    @Query("""
        SELECT t.id, t.type, t.amount, t.accountId,
               a.name AS accountName, a.colorHex AS accountColor,
               a.currency AS accountCurrency,
               t.toAccountId, ta.name AS toAccountName,
               t.categoryId, c.name AS categoryName,
               c.colorHex AS categoryColor, c.icon AS categoryIcon,
               t.note, t.date
        FROM transactions t
        INNER JOIN accounts a ON t.accountId = a.id
        LEFT JOIN accounts ta ON t.toAccountId = ta.id
        LEFT JOIN categories c ON t.categoryId = c.id
        WHERE t.date BETWEEN :fromDate AND :toDate
        ORDER BY t.date DESC
    """)
    fun getTransactionsByDateRange(fromDate: Long, toDate: Long): Flow<List<TransactionWithDetails>>

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM transactions t
        WHERE t.type = :type AND t.date BETWEEN :fromDate AND :toDate
    """)
    fun getSumByTypeAndPeriod(type: TransactionType, fromDate: Long, toDate: Long): Flow<Double>

    @Query("""
        SELECT c.id AS categoryId, c.name AS categoryName,
               c.colorHex AS categoryColor, c.icon AS categoryIcon,
               COALESCE(SUM(t.amount), 0) AS total,
               COUNT(t.id) AS count
        FROM categories c
        LEFT JOIN transactions t ON t.categoryId = c.id
            AND t.type = :type
            AND t.date BETWEEN :fromDate AND :toDate
        WHERE c.type = :type
        GROUP BY c.id
        ORDER BY total DESC
    """)
    fun getCategorySpending(
        type: TransactionType,
        fromDate: Long,
        toDate: Long
    ): Flow<List<CategorySpending>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    suspend fun getAllTransactions(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Query("""
        SELECT t.id, t.type, t.amount, t.accountId,
               a.name AS accountName, a.colorHex AS accountColor,
               a.currency AS accountCurrency,
               t.toAccountId, ta.name AS toAccountName,
               t.categoryId, c.name AS categoryName,
               c.colorHex AS categoryColor, c.icon AS categoryIcon,
               t.note, t.date
        FROM transactions t
        INNER JOIN accounts a ON t.accountId = a.id
        LEFT JOIN accounts ta ON t.toAccountId = ta.id
        LEFT JOIN categories c ON t.categoryId = c.id
        ORDER BY t.date DESC
    """)
    suspend fun getAllTransactionsWithDetails(): List<TransactionWithDetails>

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("SELECT * FROM transactions WHERE nextRepeatDate IS NOT NULL AND nextRepeatDate <= :today ORDER BY date ASC")
    suspend fun getDueRepeatTransactions(today: Long): List<TransactionEntity>

    @Query("UPDATE transactions SET nextRepeatDate = NULL WHERE id = :id")
    suspend fun clearNextRepeatDate(id: Long)

    @Query("SELECT * FROM transactions WHERE nextRepeatDate IS NOT NULL AND reminderMode != 'NEVER' ORDER BY nextRepeatDate ASC")
    suspend fun getTransactionsWithReminder(): List<TransactionEntity>
}
