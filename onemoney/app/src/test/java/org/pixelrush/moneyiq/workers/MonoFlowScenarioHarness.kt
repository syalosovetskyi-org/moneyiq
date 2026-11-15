package org.syalosovetskyi.onemoney.workers

import android.content.Context
import androidx.room.Room
import org.syalosovetskyi.onemoney.data.db.AppDatabase
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.data.repository.CategoryRepository
import org.syalosovetskyi.onemoney.util.BackupData
import org.syalosovetskyi.onemoney.util.BackupSerializer

/**
 * Сценарії «стан БД × синк mono-flow» на in-memory Room (serg/tasks#2673).
 * Спільне для [MonoFlowSyncScenariosTest] і знімків матриці пристроїв.
 */
object MonoFlowScenarioHarness {

    const val FIXTURE = "fixtures/monoflow_categories.json"

    /** Серверний id кореня «Продукти» у фікстурі. */
    const val SERVER_GROCERIES_ID = 1130190300660846386L

    /** Дозволені видимі EXPENSE-корені після синку (власні категорії користувача — окремо). */
    val EXPENSE_ROOTS = setOf(
        "Дозвілля", "Здоров'я", "Подарунки", "Покупки", "Продукти",
        "Ресторація", "Робота", "Сім'я", "Транспорт",
    )

    fun loadFixture(): BackupData {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(FIXTURE)) {
            "немає ресурсу $FIXTURE у test/resources"
        }
        return BackupSerializer.deserialize(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    fun newDb(context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    suspend fun apply(db: AppDatabase, data: BackupData = loadFixture()) =
        applyMonoFlowSync(db.accountDao(), db.categoryDao(), db.transactionDao(), data)

    suspend fun seed(db: AppDatabase) = CategoryRepository(db.categoryDao()).seedDefaults()

    /** fresh: пуста БД → seedDefaults → синк. */
    suspend fun fresh(db: AppDatabase) { seed(db); apply(db) }

    /** upgrade: категорії фікстури вже в БД → повторний синк. */
    suspend fun upgrade(db: AppDatabase) { apply(db); apply(db) }

    /** Власний корінь «Моя» + операція в стандартній «Продукти» → синк. Повертає id операції. */
    suspend fun freshWithUserCategory(db: AppDatabase): Long {
        seed(db)
        val accountId = db.accountDao().insertAccount(AccountEntity(name = "Готівка"))
        db.categoryDao().insertCategory(CategoryEntity(name = USER_ROOT, type = TransactionType.EXPENSE))
        val localGroceries = db.categoryDao().getAllCategoriesOnce()
            .first { it.isDefault && it.parentId == null && it.name == "Продукти" }
        val txId = db.transactionDao().insertTransaction(TransactionEntity(
            type = TransactionType.EXPENSE, amount = 100.0, accountId = accountId,
            categoryId = localGroceries.id))
        apply(db)
        return txId
    }

    const val USER_ROOT = "Моя"

    fun visibleRoots(all: List<CategoryEntity>, type: TransactionType): List<CategoryEntity> =
        all.filter { it.parentId == null && !it.archived && it.type == type }

    fun nameKey(c: CategoryEntity) = c.name.trim().lowercase() to c.type
}
