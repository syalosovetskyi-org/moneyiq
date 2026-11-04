package org.syalosovetskyi.onemoney.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.syalosovetskyi.onemoney.data.db.dao.AccountDao
import org.syalosovetskyi.onemoney.data.db.dao.CategoryDao
import org.syalosovetskyi.onemoney.data.db.dao.TransactionDao
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class TransactionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var txDao: TransactionDao
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao

    private var accountId: Long = 0L
    private var account2Id: Long = 0L
    private var categoryId: Long = 0L

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        txDao = db.transactionDao()
        accountDao = db.accountDao()
        categoryDao = db.categoryDao()

        runBlocking {
            accountId = accountDao.insertAccount(AccountEntity(name = "Main", balance = 0.0))
            account2Id = accountDao.insertAccount(AccountEntity(name = "Second", balance = 0.0))
            categoryId = categoryDao.insertCategory(CategoryEntity(name = "Food", type = TransactionType.EXPENSE))
        }
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insertTransaction_and_getById() = runTest {
        val id = txDao.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 100.0, accountId = accountId)
        )
        val tx = txDao.getTransactionById(id)
        assertNotNull(tx)
        assertEquals(100.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
    }

    @Test
    fun getTransactionById_returns_null_for_missing() = runTest {
        assertNull(txDao.getTransactionById(999L))
    }

    @Test
    fun getTransactionsByDateRange_filters_to_range() = runTest {
        txDao.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 100.0, accountId = accountId, date = 1000L)
        )
        txDao.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 200.0, accountId = accountId, date = 5000L)
        )
        val results = txDao.getTransactionsByDateRange(500L, 2000L).first()
        assertEquals(1, results.size)
        assertEquals(100.0, results[0].amount, 0.001)
    }

    @Test
    fun getTransactionsByDateRange_includes_boundary_dates() = runTest {
        txDao.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 50.0, accountId = accountId, date = 1000L)
        )
        txDao.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 75.0, accountId = accountId, date = 2000L)
        )
        val results = txDao.getTransactionsByDateRange(1000L, 2000L).first()
        assertEquals(2, results.size)
    }

    @Test
    fun getSumByTypeAndPeriod_income_sum() = runTest {
        txDao.insertTransaction(
            TransactionEntity(type = TransactionType.INCOME, amount = 300.0, accountId = accountId, date = 1000L)
        )
        txDao.insertTransaction(
            TransactionEntity(type = TransactionType.INCOME, amount = 200.0, accountId = accountId, date = 2000L)
        )
        txDao.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 100.0, accountId = accountId, date = 1500L)
        )
        val sum = txDao.getSumByTypeAndPeriod(TransactionType.INCOME, 0L, 5000L).first()
        assertEquals(500.0, sum, 0.001)
    }

    @Test
    fun getSumByTypeAndPeriod_expense_sum() = runTest {
        txDao.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 50.0, accountId = accountId, categoryId = categoryId, date = 1000L)
        )
        txDao.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 75.0, accountId = accountId, categoryId = categoryId, date = 2000L)
        )
        val sum = txDao.getSumByTypeAndPeriod(TransactionType.EXPENSE, 0L, 5000L).first()
        assertEquals(125.0, sum, 0.001)
    }

    @Test
    fun getSumByTypeAndPeriod_returns_zero_when_no_transactions() = runTest {
        val sum = txDao.getSumByTypeAndPeriod(TransactionType.INCOME, 0L, Long.MAX_VALUE).first()
        assertEquals(0.0, sum, 0.001)
    }

    @Test
    fun getCategorySpending_groups_by_category() = runTest {
        val cat2Id = categoryDao.insertCategory(
            CategoryEntity(name = "Transport", type = TransactionType.EXPENSE)
        )
        txDao.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 100.0, accountId = accountId, categoryId = categoryId, date = 1000L)
        )
        txDao.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 50.0, accountId = accountId, categoryId = categoryId, date = 2000L)
        )
        txDao.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 200.0, accountId = accountId, categoryId = cat2Id, date = 1000L)
        )
        val spending = txDao.getCategorySpending(TransactionType.EXPENSE, 0L, 5000L).first()
        val food = spending.find { it.categoryId == categoryId }
        val transport = spending.find { it.categoryId == cat2Id }
        assertNotNull(food)
        assertNotNull(transport)
        assertEquals(150.0, food!!.total, 0.001)
        assertEquals(200.0, transport!!.total, 0.001)
    }

    @Test
    fun getCategorySpending_count_matches_transaction_count() = runTest {
        txDao.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 50.0, accountId = accountId, categoryId = categoryId, date = 1000L)
        )
        txDao.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 75.0, accountId = accountId, categoryId = categoryId, date = 2000L)
        )
        val spending = txDao.getCategorySpending(TransactionType.EXPENSE, 0L, 5000L).first()
        val food = spending.find { it.categoryId == categoryId }
        assertEquals(2, food!!.count)
    }

    @Test
    fun deleteTransaction_removes_from_db() = runTest {
        val id = txDao.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 100.0, accountId = accountId)
        )
        val tx = txDao.getTransactionById(id)!!
        txDao.deleteTransaction(tx)
        assertNull(txDao.getTransactionById(id))
    }

    @Test
    fun updateTransaction_changes_amount_and_note() = runTest {
        val id = txDao.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 100.0, accountId = accountId, note = "Old")
        )
        val tx = txDao.getTransactionById(id)!!
        txDao.updateTransaction(tx.copy(amount = 250.0, note = "Updated"))
        val updated = txDao.getTransactionById(id)
        assertEquals(250.0, updated!!.amount, 0.001)
        assertEquals("Updated", updated.note)
    }

    @Test
    fun updateTransaction_changes_type() = runTest {
        val id = txDao.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 100.0, accountId = accountId)
        )
        val tx = txDao.getTransactionById(id)!!
        txDao.updateTransaction(tx.copy(type = TransactionType.INCOME))
        val updated = txDao.getTransactionById(id)
        assertEquals(TransactionType.INCOME, updated!!.type)
    }

    @Test
    fun count_returns_correct_number() = runTest {
        assertEquals(0, txDao.count())
        txDao.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 100.0, accountId = accountId)
        )
        assertEquals(1, txDao.count())
    }

    @Test
    fun deleteAllTransactions_removes_all() = runTest {
        txDao.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 50.0, accountId = accountId)
        )
        txDao.insertTransaction(
            TransactionEntity(type = TransactionType.INCOME, amount = 100.0, accountId = accountId)
        )
        txDao.deleteAllTransactions()
        assertEquals(0, txDao.count())
    }

    @Test
    fun getTransactionsPaged_respects_limit() = runTest {
        repeat(5) {
            txDao.insertTransaction(
                TransactionEntity(type = TransactionType.EXPENSE, amount = it.toDouble() + 1, accountId = accountId)
            )
        }
        val paged = txDao.getTransactionsPaged(limit = 3).first()
        assertEquals(3, paged.size)
    }

    @Test
    fun getTransactionsPaged_ordered_by_date_desc() = runTest {
        txDao.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 10.0, accountId = accountId, date = 1000L)
        )
        txDao.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 20.0, accountId = accountId, date = 3000L)
        )
        txDao.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 30.0, accountId = accountId, date = 2000L)
        )
        val results = txDao.getTransactionsPaged(limit = 10).first()
        assertEquals(20.0, results[0].amount, 0.001) // date=3000 — most recent
        assertEquals(30.0, results[1].amount, 0.001) // date=2000
        assertEquals(10.0, results[2].amount, 0.001) // date=1000
    }

    @Test
    fun transfer_transaction_joins_to_account_name() = runTest {
        val id = txDao.insertTransaction(
            TransactionEntity(
                type = TransactionType.TRANSFER, amount = 100.0,
                accountId = accountId, toAccountId = account2Id
            )
        )
        val results = txDao.getTransactionsPaged(limit = 10).first()
        val tx = results.find { it.id == id }
        assertNotNull(tx)
        assertEquals("Second", tx!!.toAccountName)
    }

    @Test
    fun getAllTransactionsWithDetails_returns_all() = runTest {
        txDao.insertTransaction(
            TransactionEntity(type = TransactionType.INCOME, amount = 100.0, accountId = accountId)
        )
        txDao.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 50.0, accountId = accountId)
        )
        val all = txDao.getAllTransactionsWithDetails()
        assertEquals(2, all.size)
    }
}
