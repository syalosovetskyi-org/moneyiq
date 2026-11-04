package org.syalosovetskyi.onemoney.ui.transactions

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.syalosovetskyi.onemoney.data.db.entities.TransactionEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.data.repository.AccountRepository
import org.syalosovetskyi.onemoney.data.repository.CategoryRepository
import org.syalosovetskyi.onemoney.data.repository.TransactionRepository
import org.syalosovetskyi.onemoney.util.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val txRepo: TransactionRepository = mockk(relaxed = true)
    private val accountRepo: AccountRepository = mockk()
    private val categoryRepo: CategoryRepository = mockk()

    @Before
    fun setup() {
        every { accountRepo.getAllAccounts() } returns flowOf(emptyList())
        every { categoryRepo.getAll() } returns flowOf(emptyList())
    }

    private fun createVm(handle: SavedStateHandle = SavedStateHandle()): TransactionViewModel =
        TransactionViewModel(txRepo, accountRepo, categoryRepo, handle)

    // ── amount validation ────────────────────────────────────────────────────

    @Test
    fun `save with empty amount sets error`() = runTest {
        val vm = createVm()
        vm.state.test {
            awaitItem() // initial
            vm.save()
            val state = awaitItem()
            assertNotNull(state.error)
            assertTrue(state.error!!.contains("суму", ignoreCase = true))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save with zero amount sets error`() = runTest {
        val vm = createVm()
        vm.setAmount("0")
        vm.state.test {
            awaitItem()
            vm.save()
            val state = awaitItem()
            assertNotNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save with negative amount sets error`() = runTest {
        val vm = createVm()
        vm.setAmount("-10")
        vm.state.test {
            awaitItem()
            vm.save()
            val state = awaitItem()
            assertNotNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save with non-numeric amount sets error`() = runTest {
        val vm = createVm()
        vm.setAmount("abc")
        vm.state.test {
            awaitItem()
            vm.save()
            val state = awaitItem()
            assertNotNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── account validation ───────────────────────────────────────────────────

    @Test
    fun `save with no account selected sets error`() = runTest {
        val vm = createVm()
        vm.setAmount("100")
        vm.setAccount(null)
        vm.state.test {
            awaitItem()
            vm.save()
            val state = awaitItem()
            assertNotNull(state.error)
            assertTrue(state.error!!.contains("рахунок", ignoreCase = true))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save TRANSFER with no toAccount sets error`() = runTest {
        val vm = createVm()
        vm.setAmount("100")
        vm.setAccount(1L)
        vm.setType(TransactionType.TRANSFER)
        vm.setToAccount(null)
        vm.state.test {
            awaitItem()
            vm.save()
            val state = awaitItem()
            assertNotNull(state.error)
            assertTrue(state.error!!.contains("призначенн", ignoreCase = true))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── new transaction ──────────────────────────────────────────────────────

    @Test
    fun `save new transaction calls addTransaction`() = runTest {
        val vm = createVm()
        vm.setAmount("100")
        vm.setAccount(1L)
        vm.save()
        coVerify { txRepo.addTransaction(match { it.amount == 100.0 && it.accountId == 1L }) }
    }

    @Test
    fun `save with comma separator parses correctly`() = runTest {
        val vm = createVm()
        vm.setAmount("1,5")
        vm.setAccount(1L)
        vm.save()
        coVerify { txRepo.addTransaction(match { it.amount == 1.5 }) }
    }

    @Test
    fun `save new transaction does not call updateTransaction`() = runTest {
        val vm = createVm()
        vm.setAmount("100")
        vm.setAccount(1L)
        vm.save()
        coVerify(exactly = 0) { txRepo.updateTransaction(any(), any()) }
    }

    @Test
    fun `save sets saved true on success`() = runTest {
        val vm = createVm()
        vm.setAmount("100")
        vm.setAccount(1L)
        vm.state.test {
            awaitItem()
            vm.save()
            val state = awaitItem()
            assertTrue(state.saved)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── edit mode ────────────────────────────────────────────────────────────

    @Test
    fun `edit mode loads transaction and sets isEditMode true`() = runTest {
        coEvery { txRepo.getById(1L) } returns TransactionEntity(
            id = 1L, type = TransactionType.EXPENSE, amount = 50.0, accountId = 1L
        )
        val vm = TransactionViewModel(txRepo, accountRepo, categoryRepo, SavedStateHandle(mapOf("txId" to 1L)))
        vm.state.test {
            val state = awaitItem()
            assertTrue(state.isEditMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save in edit mode calls updateTransaction`() = runTest {
        coEvery { txRepo.getById(1L) } returns TransactionEntity(
            id = 1L, type = TransactionType.EXPENSE, amount = 50.0, accountId = 1L
        )
        val vm = TransactionViewModel(txRepo, accountRepo, categoryRepo, SavedStateHandle(mapOf("txId" to 1L)))
        vm.setAmount("200")
        vm.setAccount(1L)
        vm.save()
        coVerify { txRepo.updateTransaction(any(), any()) }
        coVerify(exactly = 0) { txRepo.addTransaction(any()) }
    }

    @Test
    fun `delete in edit mode calls deleteTransaction`() = runTest {
        val originalTx = TransactionEntity(id = 1L, type = TransactionType.EXPENSE, amount = 50.0, accountId = 1L)
        coEvery { txRepo.getById(1L) } returns originalTx
        val vm = TransactionViewModel(txRepo, accountRepo, categoryRepo, SavedStateHandle(mapOf("txId" to 1L)))
        vm.delete()
        coVerify { txRepo.deleteTransaction(originalTx) }
    }

    @Test
    fun `delete without edit mode does nothing`() = runTest {
        val vm = createVm()
        vm.delete()
        coVerify(exactly = 0) { txRepo.deleteTransaction(any()) }
    }

    // ── state updates ────────────────────────────────────────────────────────

    @Test
    fun `setType TRANSFER updates type in state`() = runTest {
        val vm = createVm()
        vm.state.test {
            awaitItem()
            vm.setType(TransactionType.TRANSFER)
            val state = awaitItem()
            assertTrue(state.type == TransactionType.TRANSFER)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setType clears selectedCategoryId`() = runTest {
        val vm = createVm()
        vm.setCategory(5L)
        vm.state.test {
            awaitItem()
            vm.setType(TransactionType.INCOME)
            val state = awaitItem()
            assertNull(state.selectedCategoryId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setType before setCategory preserves selected category`() = runTest {
        val vm = createVm()
        vm.state.test {
            awaitItem()
            vm.setType(TransactionType.INCOME)
            awaitItem()
            vm.setCategory(12L)
            val state = awaitItem()
            assertTrue(state.type == TransactionType.INCOME)
            assertTrue(state.selectedCategoryId == 12L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setNote updates note in state`() = runTest {
        val vm = createVm()
        vm.state.test {
            awaitItem()
            vm.setNote("Test note")
            val state = awaitItem()
            assertTrue(state.note == "Test note")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `non finite amounts never reach repository`() = runTest {
        for (value in listOf("NaN", "Infinity", "1e309")) {
            val vm = createVm()
            vm.setAccount(1L)
            vm.setAmount(value)
            vm.save()
            assertNotNull(vm.state.value.error)
        }
        coVerify(exactly = 0) { txRepo.addTransaction(any()) }
    }

    @Test
    fun `editing note preserves recurrence and historical fields`() = runTest {
        val original = TransactionEntity(id = 1L, type = TransactionType.EXPENSE,
            amount = 50.0, accountId = 1L, createdAt = 123L,
            repeatMode = "WEEKLY", reminderMode = "1_DAY", nextRepeatDate = 999L)
        coEvery { txRepo.getById(1L) } returns original
        val vm = createVm(SavedStateHandle(mapOf("txId" to 1L)))
        vm.setNote("Updated")
        vm.save()
        coVerify { txRepo.updateTransaction(original, original.copy(note = "Updated")) }
    }
}
