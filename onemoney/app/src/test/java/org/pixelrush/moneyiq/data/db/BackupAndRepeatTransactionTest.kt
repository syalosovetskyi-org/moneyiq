package org.syalosovetskyi.onemoney.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.syalosovetskyi.onemoney.data.db.entities.*
import org.syalosovetskyi.onemoney.data.repository.BackupStore
import org.syalosovetskyi.onemoney.util.BackupData
import org.syalosovetskyi.onemoney.workers.createRepeatOccurrence

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class BackupAndRepeatTransactionTest {
    private lateinit var db: AppDatabase
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun close() { db.close() }

    @Test fun `invalid backup leaves live history untouched`() = runTest {
        val account = AccountEntity(id = 1, name = "Live", balance = 100.0)
        db.accountDao().insertAccount(account)
        val bad = TransactionEntity(id = 1, type = TransactionType.EXPENSE, amount = 5.0, accountId = 999)
        try {
            BackupStore(db).replace(BackupData(1, 0, listOf(account.copy(name = "Imported")), emptyList(), listOf(bad)))
            fail("Invalid reference accepted")
        } catch (expected: IllegalArgumentException) {
            // Expected validation failure is the assertion, not a suppressed runtime error.
        }
        assertEquals(account, db.accountDao().getAccountById(1))
    }

    @Test fun `SQL failure after delete rolls back complete replacement`() = runTest {
        val account = AccountEntity(id = 1, name = "Live", balance = 100.0)
        db.accountDao().insertAccount(account)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_import BEFORE INSERT ON accounts BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        try {
            BackupStore(db).replace(BackupData(1, 0, listOf(account.copy(name = "Imported")), emptyList(), emptyList()))
            fail("Injected failure ignored")
        } catch (expected: android.database.sqlite.SQLiteException) {
            // Deliberate fault injection verifies transaction rollback.
        }
        assertEquals(account, db.accountDao().getAccountById(1))
    }

    @Test fun `snapshot round trip replaces all tables`() = runTest {
        val account = AccountEntity(id = 1, name = "Restored", balance = 75.0)
        val tx = TransactionEntity(id = 9, type = TransactionType.EXPENSE, amount = 25.0, accountId = 1)
        val data = BackupData(1, 0, listOf(account), emptyList(), listOf(tx))
        val store = BackupStore(db)
        store.replace(data)
        val snapshot = store.snapshot()
        assertEquals(data.accounts, snapshot.accounts)
        assertEquals(data.transactions, snapshot.transactions)
    }

    @Test fun `stale repeat claim never inserts twice`() = runTest {
        db.accountDao().insertAccount(AccountEntity(id = 1, name = "Cash", balance = 100.0))
        val tx = TransactionEntity(id = 7, type = TransactionType.EXPENSE, amount = 10.0,
            accountId = 1, repeatMode = "WEEKLY", nextRepeatDate = 1000L)
        db.transactionDao().insertTransaction(tx)
        assertTrue(createRepeatOccurrence(db, 7, 1000))
        assertFalse(createRepeatOccurrence(db, 7, 1000))
        assertEquals(2, db.transactionDao().count())
        assertEquals(90.0, db.accountDao().getAccountById(1)!!.balance, 0.00001)
        assertNull(db.transactionDao().getTransactionById(7)!!.nextRepeatDate)
    }

    @Test fun `repeat failure rolls back insert balance and due marker`() = runTest {
        db.accountDao().insertAccount(AccountEntity(id = 1, name = "Cash", balance = 100.0))
        val tx = TransactionEntity(id = 7, type = TransactionType.EXPENSE, amount = 10.0,
            accountId = 1, repeatMode = "WEEKLY", nextRepeatDate = 1000L)
        db.transactionDao().insertTransaction(tx)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_balance BEFORE UPDATE ON accounts BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        try {
            createRepeatOccurrence(db, 7, 1000)
            fail("Injected failure ignored")
        } catch (expected: android.database.sqlite.SQLiteException) {
            // Deliberate fault injection verifies transaction rollback.
        }
        assertEquals(1, db.transactionDao().count())
        assertEquals(100.0, db.accountDao().getAccountById(1)!!.balance, 0.00001)
        assertEquals(1000L, db.transactionDao().getTransactionById(7)!!.nextRepeatDate)
    }
}
