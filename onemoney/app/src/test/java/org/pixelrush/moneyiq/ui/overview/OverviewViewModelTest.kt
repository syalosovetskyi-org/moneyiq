package org.syalosovetskyi.onemoney.ui.overview

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.syalosovetskyi.onemoney.data.db.dao.CategorySpending
import org.syalosovetskyi.onemoney.data.db.dao.TransactionWithDetails
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.data.repository.AccountRepository
import org.syalosovetskyi.onemoney.data.repository.AppMonth
import org.syalosovetskyi.onemoney.data.repository.CategoryRepository
import org.syalosovetskyi.onemoney.data.repository.SelectedMonthRepository
import org.syalosovetskyi.onemoney.data.repository.TransactionRepository
import org.syalosovetskyi.onemoney.util.MainDispatcherRule
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val txRepo: TransactionRepository = mockk(relaxed = true)
    private val accountRepo: AccountRepository = mockk(relaxed = true)
    private val categoryRepo: CategoryRepository = mockk(relaxed = true)
    private lateinit var monthRepo: SelectedMonthRepository

    private fun millis(day: Int): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.JUNE, day, 12, 0, 0)
    }.timeInMillis

    private fun tx(
        id: Long,
        type: TransactionType,
        amount: Double,
        day: Int,
        categoryId: Long?,
        categoryName: String?,
        categoryColor: String?,
        categoryIcon: String?
    ) = TransactionWithDetails(
        id = id,
        type = type,
        amount = amount,
        accountId = 1L,
        accountName = "Гаманець",
        accountColor = "#4CAF50",
        accountCurrency = "UAH",
        toAccountId = null,
        toAccountName = null,
        categoryId = categoryId,
        categoryName = categoryName,
        categoryColor = categoryColor,
        categoryIcon = categoryIcon,
        note = "",
        date = millis(day)
    )

    @Before
    fun setup() {
        monthRepo = SelectedMonthRepository().also {
            it.setPeriod(AppMonth(2026, Calendar.JUNE))
        }
        every { accountRepo.getTotalBalance() } returns flowOf(999.0)
        every { categoryRepo.getByType(TransactionType.EXPENSE) } returns flowOf(
            listOf(
                CategoryEntity(id = 1L, name = "Продукти", type = TransactionType.EXPENSE, colorHex = "#4AAFE8", icon = "grocery", budgetAmount = 500.0),
                CategoryEntity(id = 2L, name = "Таксі", type = TransactionType.EXPENSE, colorHex = "#FDD835", icon = "taxi")
            )
        )
        every { categoryRepo.getByType(TransactionType.INCOME) } returns flowOf(
            listOf(CategoryEntity(id = 10L, name = "Зарплата", type = TransactionType.INCOME, colorHex = "#4CAF50", icon = "work", budgetAmount = 20_000.0))
        )
        every { txRepo.getCategorySpending(TransactionType.EXPENSE, any(), any()) } returns flowOf(emptyList())
        every { txRepo.getCategorySpending(TransactionType.INCOME, any(), any()) } returns flowOf(emptyList())
    }

    private fun buildVm() = OverviewViewModel(txRepo, accountRepo, categoryRepo, monthRepo)

    @Test
    fun `transactions remain in state when category rows are empty`() = runTest {
        every { txRepo.getBaseTransactionsByPeriod(any(), any()) } returns flowOf(
            listOf(tx(1L, TransactionType.EXPENSE, 120.0, 3, null, null, null, null))
        )

        val vm = buildVm()

        vm.state.test {
            val state = awaitItem()
            assertTrue(state.categories.isEmpty())
            assertEquals(1, state.transactions.size)
            assertEquals(120.0, state.monthExpense, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `expense day bars contain category color segments sorted largest first`() = runTest {
        every { txRepo.getBaseTransactionsByPeriod(any(), any()) } returns flowOf(
            listOf(
                tx(1L, TransactionType.EXPENSE, 40.0, 5, 1L, "Продукти", "#4AAFE8", "grocery"),
                tx(2L, TransactionType.EXPENSE, 90.0, 5, 2L, "Таксі", "#FDD835", "taxi"),
                tx(3L, TransactionType.EXPENSE, 10.0, 5, 1L, "Продукти", "#4AAFE8", "grocery")
            )
        )

        val vm = buildVm()

        vm.state.test {
            val bar = awaitItem().dayBars.first { it.day == 5 }
            assertEquals(140.0, bar.amount, 0.001)
            assertEquals(listOf("#FDD835", "#4AAFE8"), bar.segments.map { it.colorHex })
            assertEquals(listOf(90.0, 50.0), bar.segments.map { it.amount })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `income mode filters transactions and category rows to income`() = runTest {
        every { txRepo.getBaseTransactionsByPeriod(any(), any()) } returns flowOf(
            listOf(
                tx(1L, TransactionType.EXPENSE, 100.0, 2, 1L, "Продукти", "#4AAFE8", "grocery"),
                tx(2L, TransactionType.INCOME, 1_000.0, 2, 10L, "Зарплата", "#4CAF50", "work")
            )
        )
        every { txRepo.getCategorySpending(TransactionType.INCOME, any(), any()) } returns flowOf(
            listOf(CategorySpending(10L, "Зарплата", "#4CAF50", "work", 1_000.0, 1))
        )

        val vm = buildVm()
        vm.setMode(OverviewMode.INCOME)

        vm.state.test {
            val state = awaitItem()
            assertEquals(OverviewMode.INCOME, state.mode)
            assertEquals(1, state.transactions.size)
            assertEquals(TransactionType.INCOME, state.transactions[0].type)
            assertEquals(listOf("Зарплата"), state.categories.map { it.name })
            assertEquals(20_000.0, state.totalIncomeBudget, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
