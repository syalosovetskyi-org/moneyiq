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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.syalosovetskyi.onemoney.data.db.dao.AccountDao
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import org.syalosovetskyi.onemoney.data.db.entities.AccountType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class AccountDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AccountDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.accountDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insertAccount_and_getAllAccounts() = runTest {
        dao.insertAccount(AccountEntity(name = "Cash", balance = 100.0))
        val accounts = dao.getAllAccounts().first()
        assertEquals(1, accounts.size)
        assertEquals("Cash", accounts[0].name)
        assertEquals(100.0, accounts[0].balance, 0.001)
    }

    @Test
    fun insertAccount_returns_generated_id() = runTest {
        val id = dao.insertAccount(AccountEntity(name = "Test", balance = 0.0))
        assertTrue(id > 0)
    }

    @Test
    fun getAccountById_returns_correct_account() = runTest {
        val id = dao.insertAccount(AccountEntity(name = "Found", balance = 50.0))
        val account = dao.getAccountById(id)
        assertNotNull(account)
        assertEquals("Found", account!!.name)
    }

    @Test
    fun getAccountById_returns_null_for_missing_id() = runTest {
        assertNull(dao.getAccountById(999L))
    }

    @Test
    fun getTotalBalance_sums_only_includeInTotal_accounts() = runTest {
        dao.insertAccount(AccountEntity(name = "Included", balance = 500.0, includeInTotal = true))
        dao.insertAccount(AccountEntity(name = "Excluded", balance = 200.0, includeInTotal = false))
        val total = dao.getTotalBalance().first()
        assertEquals(500.0, total ?: 0.0, 0.001)
    }

    @Test
    fun getTotalBalance_returns_null_when_no_accounts() = runTest {
        val total = dao.getTotalBalance().first()
        assertNull(total)
    }

    @Test
    fun updateBalance_increments_correctly() = runTest {
        val id = dao.insertAccount(AccountEntity(name = "Test", balance = 100.0))
        dao.updateBalance(id, 50.0)
        val account = dao.getAccountById(id)
        assertEquals(150.0, account!!.balance, 0.001)
    }

    @Test
    fun updateBalance_decrements_correctly() = runTest {
        val id = dao.insertAccount(AccountEntity(name = "Test", balance = 100.0))
        dao.updateBalance(id, -30.0)
        val account = dao.getAccountById(id)
        assertEquals(70.0, account!!.balance, 0.001)
    }

    @Test
    fun setDefault_clears_previous_default() = runTest {
        val id1 = dao.insertAccount(AccountEntity(name = "First", balance = 0.0, isDefault = true))
        val id2 = dao.insertAccount(AccountEntity(name = "Second", balance = 0.0, isDefault = false))

        dao.clearAllDefault()
        dao.setDefault(id2)

        val first = dao.getAccountById(id1)
        val second = dao.getAccountById(id2)
        assertFalse(first!!.isDefault)
        assertTrue(second!!.isDefault)
    }

    @Test
    fun clearAllDefault_sets_all_isDefault_to_false() = runTest {
        dao.insertAccount(AccountEntity(name = "A", balance = 0.0, isDefault = true))
        dao.insertAccount(AccountEntity(name = "B", balance = 0.0, isDefault = true))
        dao.clearAllDefault()
        val accounts = dao.getAllAccountsOnce()
        assertTrue(accounts.none { it.isDefault })
    }

    @Test
    fun resetAllBalances_sets_all_to_zero() = runTest {
        dao.insertAccount(AccountEntity(name = "A", balance = 500.0, includeInTotal = true))
        dao.insertAccount(AccountEntity(name = "B", balance = 300.0, includeInTotal = true))
        dao.resetAllBalances()
        val total = dao.getTotalBalance().first()
        assertEquals(0.0, total ?: 0.0, 0.001)
    }

    @Test
    fun deleteAccount_removes_from_db() = runTest {
        val id = dao.insertAccount(AccountEntity(name = "ToDelete", balance = 0.0))
        val account = dao.getAccountById(id)!!
        dao.deleteAccount(account)
        assertNull(dao.getAccountById(id))
    }

    @Test
    fun updateAccount_changes_fields() = runTest {
        val id = dao.insertAccount(AccountEntity(name = "Original", balance = 100.0))
        val account = dao.getAccountById(id)!!
        dao.updateAccount(account.copy(name = "Updated", balance = 999.0, type = AccountType.CARD))
        val updated = dao.getAccountById(id)
        assertEquals("Updated", updated!!.name)
        assertEquals(999.0, updated.balance, 0.001)
        assertEquals(AccountType.CARD, updated.type)
    }

    @Test
    fun count_returns_correct_number() = runTest {
        assertEquals(0, dao.count())
        dao.insertAccount(AccountEntity(name = "A", balance = 0.0))
        dao.insertAccount(AccountEntity(name = "B", balance = 0.0))
        assertEquals(2, dao.count())
    }

    @Test
    fun insertAccounts_bulk_inserts_all() = runTest {
        val accounts = listOf(
            AccountEntity(name = "Acc1", balance = 0.0),
            AccountEntity(name = "Acc2", balance = 0.0),
            AccountEntity(name = "Acc3", balance = 0.0)
        )
        dao.insertAccounts(accounts)
        assertEquals(3, dao.count())
    }

    @Test
    fun deleteAllAccounts_removes_all() = runTest {
        dao.insertAccount(AccountEntity(name = "A", balance = 0.0))
        dao.insertAccount(AccountEntity(name = "B", balance = 0.0))
        dao.deleteAllAccounts()
        assertEquals(0, dao.count())
    }

    @Test
    fun getAllAccounts_ordered_by_sortOrder_then_name() = runTest {
        dao.insertAccount(AccountEntity(name = "Bravo", balance = 0.0, sortOrder = 1))
        dao.insertAccount(AccountEntity(name = "Alpha", balance = 0.0, sortOrder = 2))
        dao.insertAccount(AccountEntity(name = "Charlie", balance = 0.0, sortOrder = 1))
        val accounts = dao.getAllAccounts().first()
        assertEquals("Bravo", accounts[0].name)   // sortOrder=1, alphabetically first
        assertEquals("Charlie", accounts[1].name) // sortOrder=1, alphabetically second
        assertEquals("Alpha", accounts[2].name)   // sortOrder=2
    }
}
