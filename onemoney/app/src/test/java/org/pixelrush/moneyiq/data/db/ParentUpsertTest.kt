package org.syalosovetskyi.onemoney.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ParentUpsertTest {
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `bulk and single account upserts preserve both transfer links and history`() = runTest {
        val accounts = listOf(AccountEntity(id = 1L, name = "Source"),
            AccountEntity(id = 2L, name = "Destination"))
        db.accountDao().insertAccounts(accounts)
        val tx = TransactionEntity(id = 7L, type = TransactionType.TRANSFER,
            amount = 820.0, accountId = 1L, toAccountId = 2L, toAmount = 20.0)
        db.transactionDao().insertTransaction(tx)

        db.accountDao().insertAccounts(accounts.map { it.copy(name = "Updated", balance = 100.0) })
        assertEquals(tx, db.transactionDao().getTransactionById(7L))
        for (account in accounts) {
            db.accountDao().insertAccount(account.copy(name = "Single"))
            assertEquals(tx, db.transactionDao().getTransactionById(7L))
            assertEquals("Single", db.accountDao().getAccountById(account.id)!!.name)
        }
    }

    @Test
    fun `bulk and single category upserts preserve transaction category`() = runTest {
        db.accountDao().insertAccount(AccountEntity(id = 1L, name = "Cash"))
        val category = CategoryEntity(id = 1L, name = "Food", type = TransactionType.EXPENSE)
        db.categoryDao().insertCategory(category)
        db.transactionDao().insertTransaction(TransactionEntity(id = 7L,
            type = TransactionType.EXPENSE, amount = 10.0, accountId = 1L, categoryId = 1L))

        db.categoryDao().insertCategories(listOf(category.copy(name = "Updated")))
        assertEquals(1L, db.transactionDao().getTransactionById(7L)!!.categoryId)
        db.categoryDao().insertCategory(category.copy(name = "Single"))
        assertEquals(1L, db.transactionDao().getTransactionById(7L)!!.categoryId)
        assertNotNull(db.categoryDao().getCategoryById(1L))
        assertEquals("Single", db.categoryDao().getCategoryById(1L)!!.name)
    }
}
