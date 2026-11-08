package org.syalosovetskyi.onemoney.ui.transactions

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.syalosovetskyi.onemoney.data.db.dao.TransactionWithDetails
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.data.repository.AccountRepository
import org.syalosovetskyi.onemoney.data.repository.AppMonth
import org.syalosovetskyi.onemoney.data.repository.CategoryRepository
import org.syalosovetskyi.onemoney.data.repository.SelectedMonthRepository
import org.syalosovetskyi.onemoney.data.repository.TransactionRepository
import org.syalosovetskyi.onemoney.data.repository.TxOverrideRepository
import org.syalosovetskyi.onemoney.util.MainDispatcherRule
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val txRepo: TransactionRepository = mockk(relaxed = true)
    private val accountRepo: AccountRepository = mockk(relaxed = true)
    private val categoryRepo: CategoryRepository = mockk(relaxed = true)
    private val overrideRepo: TxOverrideRepository = mockk(relaxed = true)
    private lateinit var monthRepo: SelectedMonthRepository

    private fun millis(day: Int): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.JUNE, day, 12, 0, 0)
    }.timeInMillis

    private fun tx(id: Long, type: TransactionType, amount: Double) = TransactionWithDetails(
        id = id,
        type = type,
        amount = amount,
        accountId = 1L,
        accountName = "Гаманець",
        accountColor = "#4CAF50",
        accountCurrency = "UAH",
        toAccountId = null,
        toAccountName = null,
        categoryId = 2L,
        categoryName = "Продукти",
        categoryColor = "#4AAFE8",
        categoryIcon = "grocery",
        note = "",
        date = millis(1)
    )

    @Before
    fun setup() {
        monthRepo = SelectedMonthRepository().also {
            it.setPeriod(AppMonth(2026, Calendar.JUNE))
        }
        every { txRepo.getTransactionsByPeriod(any(), any()) } returns flowOf(
            listOf(
                tx(1L, TransactionType.INCOME, 1_000.0),
                tx(2L, TransactionType.BORROW, 200.0),
                tx(3L, TransactionType.EXPENSE, 300.0),
                tx(4L, TransactionType.LEND, 50.0),
                tx(5L, TransactionType.REPAY, 25.0),
                tx(6L, TransactionType.TRANSFER, 400.0)
            )
        )
        every { txRepo.getBaseTransactionsByPeriod(any(), any()) } answers { txRepo.getTransactionsByPeriod(firstArg(), secondArg()) }
        every { txRepo.getPeriodBalances(any(), any()) } returns flowOf(
            org.syalosovetskyi.onemoney.util.PeriodBalances(
                org.syalosovetskyi.onemoney.util.BaseValuation(1175.0),
                org.syalosovetskyi.onemoney.util.BaseValuation(2000.0)))
        every { accountRepo.getAllAccounts() } returns flowOf(
            listOf(AccountEntity(id = 1L, name = "Гаманець"))
        )
        every { categoryRepo.getAll() } returns flowOf(
            listOf(
                CategoryEntity(id = 2L, name = "Продукти", type = TransactionType.EXPENSE, archived = false),
                CategoryEntity(id = 3L, name = "Архів", type = TransactionType.EXPENSE, archived = true),
                CategoryEntity(id = 4L, name = "Зарплата", type = TransactionType.INCOME, archived = false)
            )
        )
    }

    private fun buildVm() = TransactionsListViewModel(txRepo, accountRepo, categoryRepo, monthRepo, overrideRepo)

    @Test
    fun `state computes income expense and opening balance from visible transactions`() = runTest {
        val vm = buildVm()

        vm.state.test {
            val state = awaitItem()
            assertEquals(1_200.0, state.totalIncome, 0.001)
            assertEquals(375.0, state.totalExpense, 0.001)
            assertEquals(2_000.0, state.closingBalance, 0.001)
            assertEquals(1_175.0, state.openingBalance, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state excludes archived categories from picker lists`() = runTest {
        val vm = buildVm()

        vm.state.test {
            val state = awaitItem()
            assertEquals(listOf("Продукти"), state.expenseCategories.map { it.name })
            assertEquals(listOf("Зарплата"), state.incomeCategories.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recordTransaction persists repeat and reminder metadata`() = runTest {
        val vm = buildVm()
        val category = CategoryEntity(id = 7L, name = "Оренда", type = TransactionType.EXPENSE)
        val date = millis(2)

        vm.recordTransaction(
            accountId = 1L,
            category = category,
            amount = 700.0,
            note = "rent",
            date = date,
            repeatMode = "MONTHLY",
            reminderMode = "1_DAY"
        )

        coVerify {
            txRepo.addTransaction(match {
                it.type == TransactionType.EXPENSE &&
                    it.accountId == 1L &&
                    it.categoryId == 7L &&
                    it.repeatMode == "MONTHLY" &&
                    it.reminderMode == "1_DAY" &&
                    it.nextRepeatDate != null &&
                    it.nextRepeatDate!! > date
            })
        }
    }

    @Test
    fun `recordTransfer creates transfer transaction without category`() = runTest {
        val vm = buildVm()

        vm.recordTransfer(fromAccountId = 1L, toAccountId = 2L, amount = 150.0, date = millis(3))

        coVerify {
            txRepo.addTransaction(match {
                it.type == TransactionType.TRANSFER &&
                    it.accountId == 1L &&
                    it.toAccountId == 2L &&
                    it.categoryId == null &&
                    it.amount == 150.0
            })
        }
    }

    @Test
    fun `deleteTransaction loads entity before deleting`() = runTest {
        val vm = buildVm()
        val entity = TransactionEntity(id = 5L, type = TransactionType.EXPENSE, amount = 20.0, accountId = 1L)
        coEvery { txRepo.getById(5L) } returns entity

        vm.deleteTransaction(tx(5L, TransactionType.EXPENSE, 20.0))

        coVerify { txRepo.deleteTransaction(entity) }
    }

    @Test
    fun `deleteTransaction does nothing when entity is missing`() = runTest {
        val vm = buildVm()
        coEvery { txRepo.getById(404L) } returns null

        vm.deleteTransaction(tx(404L, TransactionType.EXPENSE, 20.0))

        coVerify(exactly = 0) { txRepo.deleteTransaction(any()) }
    }

    @Test
    fun `updateTransaction preserves original type and updates editable fields`() = runTest {
        val vm = buildVm()
        val original = TransactionEntity(
            id = 9L,
            type = TransactionType.EXPENSE,
            amount = 20.0,
            accountId = 1L,
            note = "old",
            date = millis(1)
        )
        coEvery { txRepo.getById(9L) } returns original

        vm.updateTransaction(tx(9L, TransactionType.EXPENSE, 20.0), note = "new", amount = 30.0, date = millis(4))

        coVerify {
            txRepo.updateTransaction(
                original,
                match {
                    it.id == 9L &&
                        it.type == TransactionType.EXPENSE &&
                        it.note == "new" &&
                        it.amount == 30.0 &&
                        it.date == millis(4)
                }
            )
        }
    }

    @Test
    fun `duplicateTransaction creates fresh transaction with current timestamp`() = runTest {
        val vm = buildVm()

        vm.duplicateTransaction(tx(12L, TransactionType.INCOME, 80.0))

        coVerify {
            txRepo.addTransaction(match {
                it.id == 0L &&
                    it.type == TransactionType.INCOME &&
                    it.amount == 80.0 &&
                    it.accountId == 1L &&
                    it.categoryId == 2L &&
                    it.date > 0L
            })
        }
    }

    @Test
    fun `assigning a category to a transfer turns it into an expense`() = runTest {
        val vm = buildVm()
        val original = TransactionEntity(
            id = 6L,
            type = TransactionType.TRANSFER,
            amount = 7_500.0,
            accountId = 1L,
            toAccountId = 2L,
            note = "",
            date = millis(1)
        )
        coEvery { txRepo.getById(6L) } returns original
        coEvery { categoryRepo.getById(2L) } returns
            CategoryEntity(id = 2L, name = "Продукти", type = TransactionType.EXPENSE)

        vm.updateTransaction(
            tx(6L, TransactionType.TRANSFER, 7_500.0),
            note = "заказ подкрилка",
            amount = 7_500.0,
            date = millis(1),
            categoryId = 2L
        )

        coVerify {
            txRepo.updateTransaction(
                original,
                match {
                    it.type == TransactionType.EXPENSE &&
                        it.toAccountId == null &&
                        it.categoryId == 2L &&
                        it.note == "заказ подкрилка"
                }
            )
        }
    }

    @Test
    fun `edited transaction is queued for the server`() = runTest {
        val vm = buildVm()
        val original = TransactionEntity(
            id = 6L,
            type = TransactionType.TRANSFER,
            amount = 7_500.0,
            accountId = 1L,
            toAccountId = 2L,
            note = "",
            date = millis(1)
        )
        coEvery { txRepo.getById(6L) } returns original
        coEvery { categoryRepo.getById(2L) } returns
            CategoryEntity(id = 2L, name = "Продукти", type = TransactionType.EXPENSE)

        vm.updateTransaction(
            tx(6L, TransactionType.TRANSFER, 7_500.0),
            note = "заказ подкрилка",
            amount = 7_500.0,
            date = millis(1),
            categoryId = 2L
        )

        coVerify {
            overrideRepo.enqueue(6L, "заказ подкрилка", "Продукти", TransactionType.EXPENSE)
        }
    }

    @Test
    fun `period navigation delegates to shared month repository`() {
        val vm = buildVm()

        vm.prevMonth()
        assertFalse(monthRepo.month.value == AppMonth(2026, Calendar.JUNE))

        vm.setPeriod(AppMonth(2026, Calendar.JULY))
        assertTrue(monthRepo.month.value == AppMonth(2026, Calendar.JULY))
    }
}
