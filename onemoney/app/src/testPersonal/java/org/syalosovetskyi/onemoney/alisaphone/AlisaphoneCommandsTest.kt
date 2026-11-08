package org.syalosovetskyi.onemoney.alisaphone

import android.content.Context
import android.os.Bundle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.syalosovetskyi.onemoney.data.db.AppDatabase
import org.syalosovetskyi.onemoney.data.db.entities.*
import org.syalosovetskyi.onemoney.data.repository.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class AlisaphoneCommandsTest {
    private lateinit var db: AppDatabase
    private lateinit var api: AlisaphoneCommands
    private lateinit var repo: TransactionRepository
    private lateinit var context: Context
    private val rates = mockk<CurrencyRatesRepository>()
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("alisaphone_owned_transaction", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        every { rates.rates } returns flowOf(mapOf("UAH" to 1.0, "USD" to 40.0))
        repo = TransactionRepository(db.transactionDao(), db.accountDao(), rates)
        api = AlisaphoneCommands(context, db, repo, rates)
    }
    @After fun close() { db.close() }
    private suspend fun seed() {
        db.accountDao().insertAccount(AccountEntity(id = 1, name = "Wallet", isDefault = true))
        db.categoryDao().insertCategory(CategoryEntity(id = 1, name = "Продукти", type = TransactionType.EXPENSE))
    }
    private suspend fun execute(cmd: String, block: Bundle.() -> Unit = {}) = api.execute(ApiRequest.parse(Bundle().apply { putString("cmd", cmd); block() }))
    private suspend fun add() = execute("add_transaction") {
        putString("type", "expense"); putDouble("amount", 200.0); putString("category", "ПРОДУКТЫ")
    }
    private suspend fun rejects(code: String, block: suspend () -> Unit) {
        try { block(); fail("Expected $code") } catch (error: ApiError) { assertEquals(code, error.code) }
    }
    @Test fun `add and undo use repository balance updates and leave manual operation`() = runTest {
        seed()
        repo.addTransaction(TransactionEntity(id = 7, type = TransactionType.INCOME, amount = 50.0, accountId = 1))
        val result = add()
        assertTrue(result.getLong("id") < 0)
        assertEquals(-150.0, result.getDouble("balance_after"), 0.001)
        assertEquals("Продукти", result.getString("category"))
        assertEquals("UAH", result.getString("currency"))
        val undone = execute("undo_last")
        assertEquals(result.getLong("id"), undone.getLong("id"))
        assertEquals(50.0, db.accountDao().getAccountById(1)!!.balance, 0.001)
        assertNotNull(repo.getById(7))
        assertEquals(1, db.transactionDao().count())
        rejects("nothing_to_undo") { execute("undo_last") }
    }
    @Test fun `undo ownership survives service recreation and only undoes newest API transaction`() = runTest {
        seed()
        val first = add().getLong("id")
        val second = add().getLong("id")
        api = AlisaphoneCommands(context, db, repo, rates)
        execute("undo_last")
        assertNotNull(repo.getById(first))
        assertNull(repo.getById(second))
        assertEquals(-200.0, db.accountDao().getAccountById(1)!!.balance, 0.001)
    }
    @Test fun `manual-only history cannot be undone`() = runTest {
        seed()
        repo.addTransaction(TransactionEntity(id = 5, type = TransactionType.EXPENSE, amount = 10.0, accountId = 1))
        rejects("nothing_to_undo") { execute("undo_last") }
        assertNotNull(repo.getById(5))
    }
    @Test fun `edited API operation refuses undo`() = runTest {
        seed()
        val id = add().getLong("id")
        val old = repo.getById(id)!!
        repo.updateTransaction(old, old.copy(note = "edited in UI"))
        rejects("undo_conflict") { execute("undo_last") }
        assertNotNull(repo.getById(id))
    }
    @Test fun `currency parameter converts into account units and rejects unavailable rate`() = runTest {
        seed()
        val result = execute("add_transaction") { putString("type", "income"); putDouble("amount", 2.0); putString("currency", "USD") }
        assertEquals(80.0, result.getDouble("amount"), 0.001)
        assertEquals(80.0, db.accountDao().getAccountById(1)!!.balance, 0.001)
        rejects("rate_unavailable") { execute("add_transaction") { putString("type", "expense"); putDouble("amount", 2.0); putString("currency", "XYZ") } }
        assertEquals(1, db.transactionDao().count())
    }
    @Test fun `unknown category returns nearest names without mutation`() = runTest {
        seed()
        try {
            execute("add_transaction") { putString("type", "expense"); putDouble("amount", 20.0); putString("category", "Продук") }
            fail("Expected category_not_found")
        } catch (error: ApiError) {
            assertEquals("category_not_found", error.code)
            assertTrue(error.details.contains("Продукти"))
        }
        assertEquals(0, db.transactionDao().count())
    }
    @Test fun `database failure rolls back insertion and account balance`() = runTest {
        seed()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_balance BEFORE UPDATE ON accounts BEGIN SELECT RAISE(ABORT, 'injected'); END")
        try { add(); fail("Expected database failure") }
        catch (expected: android.database.sqlite.SQLiteException) { /* Fault injection proves atomic rollback. */ }
        assertEquals(0, db.transactionDao().count())
        assertEquals(0.0, db.accountDao().getAccountById(1)!!.balance, 0.001)
    }
    @Test fun `balance recent and monthly summary reflect saved operation`() = runTest {
        seed()
        execute("add_transaction") { putString("type", "expense"); putDouble("amount", 200.0); putString("category", "groceries"); putString("date", "2026-11-08T12:00:00Z") }
        assertEquals(-200.0, execute("balance").getDouble("total"), 0.001)
        assertEquals(1, execute("recent").getJSONArray("transactions").length())
        val summary = execute("month_summary") { putString("month", "2026-11") }
        assertEquals(200.0, summary.getJSONArray("categories").getJSONObject(0).getDouble("amount"), 0.001)
    }
    @Test fun `missing rates return explicit partial API balance`() = runTest {
        seed()
        db.accountDao().insertAccount(AccountEntity(id = 2, name = "RUB", balance = -200.0, currency = "RUB"))
        val result = execute("balance")
        assertFalse(result.getBoolean("complete"))
        assertEquals("RUB", result.getJSONArray("missing_rates").getString(0))
    }
    @Test fun `API transactions survive validated backup round trip`() = runTest {
        seed()
        val id = add().getLong("id")
        val store = BackupStore(db)
        val snapshot = store.snapshot()
        val restored = org.syalosovetskyi.onemoney.util.BackupSerializer.deserialize(
            org.syalosovetskyi.onemoney.util.BackupSerializer.serialize(snapshot))
        store.replace(restored)
        assertNotNull(repo.getById(id))
        execute("undo_last")
        assertEquals(0.0, db.accountDao().getAccountById(1)!!.balance, 0.001)
    }
    @Test fun `unknown account invalid type and ambiguous category do not write`() = runTest {
        seed()
        rejects("account_not_found") { execute("add_transaction") { putString("type", "expense"); putDouble("amount", 20.0); putString("account", "Missing") } }
        rejects("invalid_params") { execute("add_transaction") { putString("type", "transfer"); putDouble("amount", 20.0) } }
        db.categoryDao().insertCategory(CategoryEntity(id = 2, name = "Продукти", type = TransactionType.EXPENSE))
        rejects("category_ambiguous") { add() }
        assertEquals(0, db.transactionDao().count())
    }

}
