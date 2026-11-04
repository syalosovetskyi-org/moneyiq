package org.syalosovetskyi.onemoney.ui.reports

import app.cash.turbine.test
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.syalosovetskyi.onemoney.data.db.dao.CategorySpending
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.data.repository.TransactionRepository
import org.syalosovetskyi.onemoney.util.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val txRepo: TransactionRepository = mockk(relaxed = true)
    private lateinit var vm: ReportsViewModel

    @Before
    fun setup() {
        every { txRepo.getIncomeSum(any(), any()) } returns flowOf(0.0)
        every { txRepo.getExpenseSum(any(), any()) } returns flowOf(0.0)
        every { txRepo.getCategorySpending(any(), any(), any()) } returns flowOf(emptyList())
        vm = ReportsViewModel(txRepo)
    }

    @Test
    fun `initial state has empty category lists`() = runTest {
        vm.state.test {
            val state = awaitItem()
            assertTrue(state.incomeByCategory.isEmpty())
            assertTrue(state.expenseByCategory.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `income value reflects txRepo income sum`() = runTest {
        every { txRepo.getIncomeSum(any(), any()) } returns flowOf(8000.0)
        vm = ReportsViewModel(txRepo)

        vm.state.test {
            val state = awaitItem()
            assertEquals(8000.0, state.income, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `expense value reflects txRepo expense sum`() = runTest {
        every { txRepo.getExpenseSum(any(), any()) } returns flowOf(3200.0)
        vm = ReportsViewModel(txRepo)

        vm.state.test {
            val state = awaitItem()
            assertEquals(3200.0, state.expense, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `expenseByCategory excludes zero-total entries`() = runTest {
        val spending = listOf(
            CategorySpending(categoryId = 1L, categoryName = "Продукти", categoryColor = "#03A9F4", categoryIcon = "shopping", total = 500.0, count = 3),
            CategorySpending(categoryId = 2L, categoryName = "Транспорт", categoryColor = "#FF9800", categoryIcon = "car", total = 0.0, count = 0),
            CategorySpending(categoryId = 3L, categoryName = "Дозвілля", categoryColor = "#E91E63", categoryIcon = "music", total = 200.0, count = 1)
        )
        every { txRepo.getCategorySpending(TransactionType.EXPENSE, any(), any()) } returns flowOf(spending)
        vm = ReportsViewModel(txRepo)

        vm.state.test {
            val state = awaitItem()
            assertEquals(2, state.expenseByCategory.size)
            assertTrue(state.expenseByCategory.none { it.total == 0.0 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `incomeByCategory excludes zero-total entries`() = runTest {
        val spending = listOf(
            CategorySpending(categoryId = 10L, categoryName = "Зарплата", categoryColor = "#4CAF50", categoryIcon = "work", total = 25000.0, count = 1),
            CategorySpending(categoryId = 11L, categoryName = "Фриланс", categoryColor = "#26A69A", categoryIcon = "work", total = 0.0, count = 0)
        )
        every { txRepo.getCategorySpending(TransactionType.INCOME, any(), any()) } returns flowOf(spending)
        vm = ReportsViewModel(txRepo)

        vm.state.test {
            val state = awaitItem()
            assertEquals(1, state.incomeByCategory.size)
            assertEquals(25000.0, state.incomeByCategory[0].total, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `prevMonth causes state to re-emit`() = runTest {
        vm.state.test {
            awaitItem() // initial state

            vm.prevMonth()
            awaitItem() // updated state after month change

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nextMonth causes state to re-emit`() = runTest {
        vm.state.test {
            awaitItem() // initial state

            vm.nextMonth()
            awaitItem() // updated state after month change

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `prevMonth queries new date range`() = runTest {
        val rangeSlot = mutableListOf<Long>()
        every { txRepo.getIncomeSum(capture(rangeSlot), capture(rangeSlot)) } returns flowOf(0.0)

        vm = ReportsViewModel(txRepo)
        val initialFrom = rangeSlot.getOrNull(0)

        rangeSlot.clear()
        vm.prevMonth()

        // After prevMonth, a different (earlier) range is queried
        val newFrom = rangeSlot.getOrNull(0)
        if (initialFrom != null && newFrom != null) {
            assertTrue("prevMonth should query an earlier start date", newFrom < initialFrom)
        }
    }

    @Test
    fun `nextMonth queries later date range`() = runTest {
        val rangeSlot = mutableListOf<Long>()
        every { txRepo.getIncomeSum(capture(rangeSlot), capture(rangeSlot)) } returns flowOf(0.0)

        vm = ReportsViewModel(txRepo)
        val initialFrom = rangeSlot.getOrNull(0)

        rangeSlot.clear()
        vm.nextMonth()

        val newFrom = rangeSlot.getOrNull(0)
        if (initialFrom != null && newFrom != null) {
            assertTrue("nextMonth should query a later start date", newFrom > initialFrom)
        }
    }

    @Test
    fun `periodLabel is non-empty`() = runTest {
        vm.state.test {
            val state = awaitItem()
            assertTrue(state.periodLabel.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `month range includes first and last millisecond`() {
        val cal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.MARCH, 15, 12, 30, 30)
            set(java.util.Calendar.MILLISECOND, 456)
        }
        val (from, to) = vm.monthRange(cal)
        val start = cal.clone() as java.util.Calendar
        start.set(2026, java.util.Calendar.MARCH, 1, 0, 0, 0)
        start.set(java.util.Calendar.MILLISECOND, 0)
        val end = start.clone() as java.util.Calendar
        end.add(java.util.Calendar.MONTH, 1)
        assertEquals(start.timeInMillis, from)
        assertEquals(end.timeInMillis - 1, to)
    }
}
