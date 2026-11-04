package org.syalosovetskyi.onemoney.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that the current database schema contains all expected columns.
 *
 * Note: Full migration path tests (MigrationTestHelper) require exportSchema = true
 * in AppDatabase. These tests validate the final schema instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class AppDatabaseSchemaTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun getColumnNames(tableName: String): List<String> {
        val cursor = db.openHelper.writableDatabase.query("PRAGMA table_info($tableName)")
        val names = mutableListOf<String>()
        cursor.use {
            while (it.moveToNext()) {
                names.add(it.getString(it.getColumnIndexOrThrow("name")))
            }
        }
        return names
    }

    // ── accounts table (migrations 1→2 added isDefault, 2→3 added description) ──

    @Test
    fun accounts_table_has_id_column() {
        assertTrue("id", getColumnNames("accounts").contains("id"))
    }

    @Test
    fun accounts_table_has_name_column() {
        assertTrue("name", getColumnNames("accounts").contains("name"))
    }

    @Test
    fun accounts_table_has_balance_column() {
        assertTrue("balance", getColumnNames("accounts").contains("balance"))
    }

    @Test
    fun accounts_table_has_isDefault_column_added_by_migration_1_to_2() {
        assertTrue("isDefault", getColumnNames("accounts").contains("isDefault"))
    }

    @Test
    fun accounts_table_has_description_column_added_by_migration_2_to_3() {
        assertTrue("description", getColumnNames("accounts").contains("description"))
    }

    @Test
    fun accounts_table_has_type_column() {
        assertTrue("type", getColumnNames("accounts").contains("type"))
    }

    @Test
    fun accounts_table_has_currency_column() {
        assertTrue("currency", getColumnNames("accounts").contains("currency"))
    }

    @Test
    fun accounts_table_has_colorHex_column() {
        assertTrue("colorHex", getColumnNames("accounts").contains("colorHex"))
    }

    @Test
    fun accounts_table_has_includeInTotal_column() {
        assertTrue("includeInTotal", getColumnNames("accounts").contains("includeInTotal"))
    }

    @Test
    fun accounts_table_has_creditLimit_column_added_by_migration_26_to_27() {
        assertTrue("creditLimit", getColumnNames("accounts").contains("creditLimit"))
    }

    // ── categories table (migrations 3→4 added archived, 4→5 added parentId) ──

    @Test
    fun categories_table_has_id_column() {
        assertTrue("id", getColumnNames("categories").contains("id"))
    }

    @Test
    fun categories_table_has_name_column() {
        assertTrue("name", getColumnNames("categories").contains("name"))
    }

    @Test
    fun categories_table_has_type_column() {
        assertTrue("type", getColumnNames("categories").contains("type"))
    }

    @Test
    fun categories_table_has_archived_column_added_by_migration_3_to_4() {
        assertTrue("archived", getColumnNames("categories").contains("archived"))
    }

    @Test
    fun categories_table_has_parentId_column_added_by_migration_4_to_5() {
        assertTrue("parentId", getColumnNames("categories").contains("parentId"))
    }

    @Test
    fun categories_table_has_budgetAmount_column() {
        assertTrue("budgetAmount", getColumnNames("categories").contains("budgetAmount"))
    }

    @Test
    fun categories_table_has_sortOrder_column() {
        assertTrue("sortOrder", getColumnNames("categories").contains("sortOrder"))
    }

    @Test
    fun categories_table_has_currencyCode_column_added_by_migration_20_to_21() {
        assertTrue("currencyCode", getColumnNames("categories").contains("currencyCode"))
    }

    // ── transactions table ────────────────────────────────────────────────────

    @Test
    fun transactions_table_has_id_column() {
        assertTrue("id", getColumnNames("transactions").contains("id"))
    }

    @Test
    fun transactions_table_has_type_column() {
        assertTrue("type", getColumnNames("transactions").contains("type"))
    }

    @Test
    fun transactions_table_has_amount_column() {
        assertTrue("amount", getColumnNames("transactions").contains("amount"))
    }

    @Test
    fun transactions_table_has_accountId_column() {
        assertTrue("accountId", getColumnNames("transactions").contains("accountId"))
    }

    @Test
    fun transactions_table_has_toAccountId_column() {
        assertTrue("toAccountId", getColumnNames("transactions").contains("toAccountId"))
    }

    @Test
    fun transactions_table_has_categoryId_column() {
        assertTrue("categoryId", getColumnNames("transactions").contains("categoryId"))
    }

    @Test
    fun transactions_table_has_date_column() {
        assertTrue("date", getColumnNames("transactions").contains("date"))
    }

    @Test
    fun transactions_table_has_note_column() {
        assertTrue("note", getColumnNames("transactions").contains("note"))
    }

    @Test
    fun transactions_table_has_repeatMode_column_added_by_migration_27_to_28() {
        assertTrue("repeatMode", getColumnNames("transactions").contains("repeatMode"))
    }

    @Test
    fun transactions_table_has_reminderMode_column_added_by_migration_27_to_28() {
        assertTrue("reminderMode", getColumnNames("transactions").contains("reminderMode"))
    }

    @Test
    fun transactions_table_has_nextRepeatDate_column_added_by_migration_27_to_28() {
        assertTrue("nextRepeatDate", getColumnNames("transactions").contains("nextRepeatDate"))
    }

    // ── all 3 tables exist ────────────────────────────────────────────────────

    @Test
    fun all_required_tables_exist() {
        val cursor = db.openHelper.writableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%'"
        )
        val tables = mutableSetOf<String>()
        cursor.use { while (it.moveToNext()) tables.add(it.getString(0)) }
        assertTrue("accounts table", tables.contains("accounts"))
        assertTrue("categories table", tables.contains("categories"))
        assertTrue("transactions table", tables.contains("transactions"))
    }
}
