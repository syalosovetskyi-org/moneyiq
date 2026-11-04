package org.syalosovetskyi.onemoney.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.syalosovetskyi.onemoney.data.db.dao.CategoryDao
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class CategoryDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CategoryDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.categoryDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insertCategory_and_getAllCategories() = runTest {
        dao.insertCategory(CategoryEntity(name = "Food", type = TransactionType.EXPENSE))
        val categories = dao.getAllCategories().first()
        assertEquals(1, categories.size)
        assertEquals("Food", categories[0].name)
    }

    @Test
    fun insertCategory_returns_generated_id() = runTest {
        val id = dao.insertCategory(CategoryEntity(name = "Test", type = TransactionType.EXPENSE))
        assertTrue(id > 0)
    }

    @Test
    fun getCategoryById_returns_correct_category() = runTest {
        val id = dao.insertCategory(CategoryEntity(name = "Found", type = TransactionType.INCOME))
        val cat = dao.getCategoryById(id)
        assertNotNull(cat)
        assertEquals("Found", cat!!.name)
        assertEquals(TransactionType.INCOME, cat.type)
    }

    @Test
    fun getCategoryById_returns_null_for_missing_id() = runTest {
        assertNull(dao.getCategoryById(999L))
    }

    @Test
    fun getCategoriesByType_filters_expense_only() = runTest {
        dao.insertCategory(CategoryEntity(name = "Expense Cat", type = TransactionType.EXPENSE))
        dao.insertCategory(CategoryEntity(name = "Income Cat", type = TransactionType.INCOME))

        val expenses = dao.getCategoriesByType(TransactionType.EXPENSE).first()
        assertEquals(1, expenses.size)
        assertEquals("Expense Cat", expenses[0].name)
    }

    @Test
    fun getCategoriesByType_filters_income_only() = runTest {
        dao.insertCategory(CategoryEntity(name = "Expense Cat", type = TransactionType.EXPENSE))
        dao.insertCategory(CategoryEntity(name = "Income Cat", type = TransactionType.INCOME))

        val incomes = dao.getCategoriesByType(TransactionType.INCOME).first()
        assertEquals(1, incomes.size)
        assertEquals("Income Cat", incomes[0].name)
    }

    @Test
    fun insertCategories_bulk_inserts_all() = runTest {
        val categories = listOf(
            CategoryEntity(name = "Cat1", type = TransactionType.EXPENSE),
            CategoryEntity(name = "Cat2", type = TransactionType.EXPENSE),
            CategoryEntity(name = "Cat3", type = TransactionType.INCOME)
        )
        dao.insertCategories(categories)
        assertEquals(3, dao.count())
    }

    @Test
    fun deleteCategory_removes_from_db() = runTest {
        val id = dao.insertCategory(CategoryEntity(name = "ToDelete", type = TransactionType.EXPENSE))
        val cat = dao.getCategoryById(id)!!
        dao.deleteCategory(cat)
        assertNull(dao.getCategoryById(id))
    }

    @Test
    fun updateCategory_changes_name_and_budget() = runTest {
        val id = dao.insertCategory(CategoryEntity(name = "Original", type = TransactionType.EXPENSE))
        val cat = dao.getCategoryById(id)!!
        dao.updateCategory(cat.copy(name = "Updated", budgetAmount = 500.0))

        val updated = dao.getCategoryById(id)
        assertEquals("Updated", updated!!.name)
        assertEquals(500.0, updated.budgetAmount, 0.001)
    }

    @Test
    fun updateCategory_preserves_type() = runTest {
        val id = dao.insertCategory(CategoryEntity(name = "Test", type = TransactionType.INCOME))
        val cat = dao.getCategoryById(id)!!
        dao.updateCategory(cat.copy(name = "Updated"))
        val updated = dao.getCategoryById(id)
        assertEquals(TransactionType.INCOME, updated!!.type)
    }

    @Test
    fun count_returns_correct_number() = runTest {
        assertEquals(0, dao.count())
        dao.insertCategory(CategoryEntity(name = "A", type = TransactionType.EXPENSE))
        dao.insertCategory(CategoryEntity(name = "B", type = TransactionType.INCOME))
        assertEquals(2, dao.count())
    }

    @Test
    fun deleteAllCategories_removes_all() = runTest {
        dao.insertCategory(CategoryEntity(name = "A", type = TransactionType.EXPENSE))
        dao.insertCategory(CategoryEntity(name = "B", type = TransactionType.INCOME))
        dao.deleteAllCategories()
        assertEquals(0, dao.count())
    }

    @Test
    fun category_parentId_is_stored_correctly() = runTest {
        val parentId = dao.insertCategory(CategoryEntity(name = "Parent", type = TransactionType.EXPENSE))
        val childId = dao.insertCategory(
            CategoryEntity(name = "Child", type = TransactionType.EXPENSE, parentId = parentId)
        )
        val child = dao.getCategoryById(childId)
        assertEquals(parentId, child!!.parentId)
    }

    @Test
    fun category_archived_flag_is_stored() = runTest {
        val id = dao.insertCategory(CategoryEntity(name = "Archived", type = TransactionType.EXPENSE, archived = true))
        val cat = dao.getCategoryById(id)
        assertTrue(cat!!.archived)
    }

    @Test
    fun getAllCategories_ordered_by_sortOrder_then_name() = runTest {
        dao.insertCategory(CategoryEntity(name = "Beta", type = TransactionType.EXPENSE, sortOrder = 1))
        dao.insertCategory(CategoryEntity(name = "Alpha", type = TransactionType.EXPENSE, sortOrder = 2))
        dao.insertCategory(CategoryEntity(name = "Gamma", type = TransactionType.EXPENSE, sortOrder = 1))

        val categories = dao.getAllCategories().first()
        assertEquals("Beta", categories[0].name)   // sortOrder=1, alphabetically first
        assertEquals("Gamma", categories[1].name)  // sortOrder=1, alphabetically second
        assertEquals("Alpha", categories[2].name)  // sortOrder=2
    }
}
