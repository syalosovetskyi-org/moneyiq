package org.syalosovetskyi.onemoney.ui.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.syalosovetskyi.onemoney.data.db.AppDatabase
import org.syalosovetskyi.onemoney.data.db.dao.CategoryDao
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.data.repository.BackupStore
import org.syalosovetskyi.onemoney.util.BackupSerializer

/**
 * Експорт читає рахунки, категорії й операції. Якщо між цими читаннями пише синк
 * (нова категорія + операція з нею), а читання не в одній транзакції, у файл потрапляє
 * операція з посиланням на категорію, якої у файлі немає, і BackupSerializer.validate
 * відкидає такий бекап цілком при відновленні (serg/tasks#3045).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class DataExportConsistencyTest {
    private lateinit var db: AppDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun close() { db.close() }

    @Test fun export_stays_restorable_when_sync_writes_between_table_reads() = runTest {
        db.accountDao().insertAccount(AccountEntity(id = 1, name = "Card"))
        db.categoryDao().insertCategory(CategoryEntity(id = 2, name = "Food", type = TransactionType.EXPENSE))
        db.transactionDao().insertTransaction(
            TransactionEntity(id = 3, type = TransactionType.EXPENSE, amount = 10.0, accountId = 1, categoryId = 2))

        // Категорії прочитано, а до читання операцій синк дописав категорію й операцію з нею.
        val racingCategories = mockk<CategoryDao>(relaxed = true)
        coEvery { racingCategories.getAllCategoriesOnce() } coAnswers {
            val snapshot = db.categoryDao().getAllCategoriesOnce()
            db.categoryDao().insertCategory(CategoryEntity(id = 9, name = "Synced", type = TransactionType.EXPENSE))
            db.transactionDao().insertTransaction(
                TransactionEntity(id = 10, type = TransactionType.EXPENSE, amount = 5.0, accountId = 1, categoryId = 9))
            snapshot
        }
        val vm = DataViewModel(db.transactionDao(), db.accountDao(), racingCategories,
            mockk(relaxed = true), mockk(relaxed = true), BackupStore(db))

        // Кидає IllegalArgumentException, якщо такий бекап не відновиться.
        BackupSerializer.validate(BackupSerializer.deserialize(vm.buildExportJson()))
    }
}
