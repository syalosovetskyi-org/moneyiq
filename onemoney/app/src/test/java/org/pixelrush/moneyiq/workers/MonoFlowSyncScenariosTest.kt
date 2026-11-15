package org.syalosovetskyi.onemoney.workers

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.syalosovetskyi.onemoney.data.db.AppDatabase
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.workers.MonoFlowScenarioHarness as H

/** Сценарії «стан БД × синк mono-flow»: без пар «Продукти / Продукти 0 ₴» (serg/tasks#2673). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MonoFlowSyncScenariosTest {

    private lateinit var db: AppDatabase

    @Before fun setUp() { db = H.newDb(ApplicationProvider.getApplicationContext()) }
    @After fun tearDown() { db.close() }

    @Test fun fresh_install_has_no_duplicate_roots() = runTest {
        H.fresh(db)
        assertInvariants(extraExpenseRoots = emptySet())
    }

    @Test fun upgrade_resync_has_no_duplicate_roots() = runTest {
        H.upgrade(db)
        assertInvariants(extraExpenseRoots = emptySet())
    }

    @Test fun fresh_with_user_category_moves_transaction_to_server_groceries_and_keeps_user_root() = runTest {
        val txId = H.freshWithUserCategory(db)

        assertInvariants(extraExpenseRoots = setOf(H.USER_ROOT))
        val roots = H.visibleRoots(db.categoryDao().getAllCategoriesOnce(), TransactionType.EXPENSE)
        assertTrue("власна категорія «${H.USER_ROOT}» має лишитись", roots.any { it.name == H.USER_ROOT })
        val tx = db.transactionDao().getAllTransactions().single { it.id == txId }
        assertEquals("операція має опинитись у серверній «Продукти»", H.SERVER_GROCERIES_ID, tx.categoryId)
    }

    @Test fun fixture_is_loaded_and_not_empty() {
        val data = H.loadFixture()
        assertTrue("у фікстурі мають бути категорії", data.categories.isNotEmpty())
        assertNotNull(data.categories.firstOrNull { it.id == H.SERVER_GROCERIES_ID })
    }

    private suspend fun assertInvariants(extraExpenseRoots: Set<String>) {
        val all = db.categoryDao().getAllCategoriesOnce()
        val roots = all.filter { it.parentId == null && !it.archived }

        val dups = roots.groupBy(H::nameKey).filterValues { it.size > 1 }
        assertTrue("дублі видимих коренів: ${dups.keys}", dups.isEmpty())

        val expenseNames = H.visibleRoots(all, TransactionType.EXPENSE).map { it.name }.toSet()
        val unexpected = expenseNames - H.EXPENSE_ROOTS - extraExpenseRoots
        assertTrue("зайві EXPENSE-корені: $unexpected", unexpected.isEmpty())
        val missing = H.EXPENSE_ROOTS - expenseNames
        assertTrue("зникли серверні EXPENSE-корені: $missing", missing.isEmpty())

        val ids = all.map { it.id }.toSet()
        val dangling = db.transactionDao().getAllTransactions()
            .filter { it.categoryId != null && it.categoryId !in ids }
        assertTrue("операції на видалених категоріях: $dangling", dangling.isEmpty())
    }
}
