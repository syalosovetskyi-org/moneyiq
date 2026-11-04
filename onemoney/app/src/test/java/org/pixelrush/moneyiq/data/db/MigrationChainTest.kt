package org.syalosovetskyi.onemoney.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/** Historical schema fixtures are reconstructed from v1 and the recorded migrations.
 * This verifies every SQL step and Room's final schema validation, not historical JSON snapshots. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class MigrationChainTest(private val fromVersion: Int) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "upgrade {0} to 34")
        fun versions(): List<Array<Any>> = (1..33).map { arrayOf<Any>(it) }
    }

    @Test fun upgrade_preserves_history_and_validates_current_schema() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "migration-$fromVersion.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(fromVersion) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("""CREATE TABLE accounts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL, type TEXT NOT NULL, balance REAL NOT NULL,
                            currency TEXT NOT NULL, colorHex TEXT NOT NULL, icon TEXT NOT NULL,
                            includeInTotal INTEGER NOT NULL, sortOrder INTEGER NOT NULL, createdAt INTEGER NOT NULL)""")
                        db.execSQL("""CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL, type TEXT NOT NULL, colorHex TEXT NOT NULL, icon TEXT NOT NULL,
                            budgetAmount REAL NOT NULL, budgetPeriod TEXT NOT NULL, isDefault INTEGER NOT NULL,
                            sortOrder INTEGER NOT NULL)""")
                        db.execSQL("""CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            type TEXT NOT NULL, amount REAL NOT NULL, accountId INTEGER NOT NULL,
                            toAccountId INTEGER, categoryId INTEGER, note TEXT NOT NULL, date INTEGER NOT NULL,
                            createdAt INTEGER NOT NULL,
                            FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                            FOREIGN KEY(toAccountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                            FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE SET NULL)""")
                        for (column in listOf("accountId", "toAccountId", "categoryId", "date")) {
                            db.execSQL("CREATE INDEX index_transactions_$column ON transactions ($column)")
                        }
                        db.execSQL("INSERT INTO accounts VALUES (1, 'Cash', 'CASH', 75.0, 'UAH', '#123456', 'wallet', 1, 0, 123)")
                        db.execSQL("INSERT INTO categories VALUES (1, 'Продукти', 'EXPENSE', '#123456', 'category', 70.0, 'WEEKLY', 0, 0)")
                        db.execSQL("INSERT INTO transactions VALUES (1, 'EXPENSE', 25.0, 1, NULL, 1, 'history', 123, 123)")
                        ALL_MIGRATIONS.filter { it.endVersion <= fromVersion }.forEach { it.migrate(db) }
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        error("Fixture upgrades must be performed by Room")
                    }
                }).build()
        )
        helper.writableDatabase
        helper.close()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        try {
            assertEquals(34, db.openHelper.writableDatabase.version)
            assertEquals("history", db.transactionDao().getTransactionById(1)!!.note)
            assertEquals(75.0, db.accountDao().getAccountById(1)!!.balance, 0.00001)
            assertEquals("grocery", db.categoryDao().getCategoryById(1)!!.icon)
            assertNull(db.transactionDao().getTransactionById(1)!!.toAmount)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
