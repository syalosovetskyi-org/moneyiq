package org.syalosovetskyi.onemoney.data.repository

import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.syalosovetskyi.onemoney.data.db.dao.AccountDao
import org.syalosovetskyi.onemoney.data.db.dao.TransactionDao
import org.syalosovetskyi.onemoney.data.db.entities.TransactionEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity

class TransactionRepositoryTest {

    private val txDao: TransactionDao = mockk(relaxed = true)
    private val accountDao: AccountDao = mockk(relaxed = true)
    private val ratesRepo: CurrencyRatesRepository = mockk(relaxed = true)
    private lateinit var repo: TransactionRepository

    @Before
    fun setup() {
        every { ratesRepo.rates } returns flowOf(mapOf("UAH" to 1.0, "USD" to 41.0))
        repo = TransactionRepository(txDao, accountDao, ratesRepo)
    }

    // ── addTransaction ───────────────────────────────────────────────────────

    @Test
    fun `addIncome increases account balance`() = runTest {
        val tx = TransactionEntity(type = TransactionType.INCOME, amount = 100.0, accountId = 1L)
        repo.addTransaction(tx)
        coVerify { txDao.insertTransaction(tx) }
        coVerify { accountDao.updateBalance(1L, +100.0) }
    }

    @Test
    fun `addExpense decreases account balance`() = runTest {
        val tx = TransactionEntity(type = TransactionType.EXPENSE, amount = 50.0, accountId = 1L)
        repo.addTransaction(tx)
        coVerify { accountDao.updateBalance(1L, -50.0) }
    }

    @Test
    fun `addTransfer decreases from and increases to account`() = runTest {
        val tx = TransactionEntity(type = TransactionType.TRANSFER, amount = 200.0, accountId = 1L, toAccountId = 2L)
        repo.addTransaction(tx)
        coVerify { accountDao.updateBalance(1L, -200.0) }
        coVerify { accountDao.updateBalance(2L, +200.0) }
    }

    @Test
    fun `addBorrow increases account balance`() = runTest {
        val tx = TransactionEntity(type = TransactionType.BORROW, amount = 300.0, accountId = 1L)
        repo.addTransaction(tx)
        coVerify { accountDao.updateBalance(1L, +300.0) }
    }

    @Test
    fun `addLend decreases account balance`() = runTest {
        val tx = TransactionEntity(type = TransactionType.LEND, amount = 150.0, accountId = 1L)
        repo.addTransaction(tx)
        coVerify { accountDao.updateBalance(1L, -150.0) }
    }

    @Test
    fun `addRepay with toAccountId updates both accounts`() = runTest {
        val tx = TransactionEntity(type = TransactionType.REPAY, amount = 500.0, accountId = 1L, toAccountId = 2L)
        repo.addTransaction(tx)
        coVerify { accountDao.updateBalance(1L, -500.0) }
        coVerify { accountDao.updateBalance(2L, +500.0) }
    }

    @Test
    fun `addRepay without toAccountId updates only from account`() = runTest {
        val tx = TransactionEntity(type = TransactionType.REPAY, amount = 500.0, accountId = 1L, toAccountId = null)
        repo.addTransaction(tx)
        coVerify { accountDao.updateBalance(1L, -500.0) }
        coVerify(exactly = 1) { accountDao.updateBalance(any(), any()) }
    }

    // ── deleteTransaction ────────────────────────────────────────────────────

    @Test
    fun `deleteIncome reverses balance increase`() = runTest {
        val tx = TransactionEntity(type = TransactionType.INCOME, amount = 100.0, accountId = 1L)
        repo.deleteTransaction(tx)
        coVerify { accountDao.updateBalance(1L, -100.0) }
        coVerify { txDao.deleteTransaction(tx) }
    }

    @Test
    fun `deleteExpense reverses balance decrease`() = runTest {
        val tx = TransactionEntity(type = TransactionType.EXPENSE, amount = 50.0, accountId = 1L)
        repo.deleteTransaction(tx)
        coVerify { accountDao.updateBalance(1L, +50.0) }
    }

    @Test
    fun `deleteTransfer reverses both balance changes`() = runTest {
        val tx = TransactionEntity(type = TransactionType.TRANSFER, amount = 200.0, accountId = 1L, toAccountId = 2L)
        repo.deleteTransaction(tx)
        coVerify { accountDao.updateBalance(1L, +200.0) }
        coVerify { accountDao.updateBalance(2L, -200.0) }
    }

    @Test
    fun `deleteBorrow reverses balance increase`() = runTest {
        val tx = TransactionEntity(type = TransactionType.BORROW, amount = 300.0, accountId = 1L)
        repo.deleteTransaction(tx)
        coVerify { accountDao.updateBalance(1L, -300.0) }
    }

    @Test
    fun `deleteLend reverses balance decrease`() = runTest {
        val tx = TransactionEntity(type = TransactionType.LEND, amount = 150.0, accountId = 1L)
        repo.deleteTransaction(tx)
        coVerify { accountDao.updateBalance(1L, +150.0) }
    }

    @Test
    fun `deleteRepay with toAccountId reverses both accounts`() = runTest {
        val tx = TransactionEntity(type = TransactionType.REPAY, amount = 500.0, accountId = 1L, toAccountId = 2L)
        repo.deleteTransaction(tx)
        coVerify { accountDao.updateBalance(1L, +500.0) }
        coVerify { accountDao.updateBalance(2L, -500.0) }
    }

    // ── updateTransaction ────────────────────────────────────────────────────

    @Test
    fun `updateTransaction reverts old income and applies new expense`() = runTest {
        val old = TransactionEntity(id = 1L, type = TransactionType.INCOME, amount = 100.0, accountId = 1L)
        val new = TransactionEntity(id = 1L, type = TransactionType.EXPENSE, amount = 200.0, accountId = 1L)
        repo.updateTransaction(old, new)
        coVerify { accountDao.updateBalance(1L, -100.0) } // revert income
        coVerify { accountDao.updateBalance(1L, -200.0) } // apply expense
        coVerify { txDao.updateTransaction(new) }
    }

    @Test
    fun `updateTransaction with account change uses correct accounts`() = runTest {
        val old = TransactionEntity(id = 1L, type = TransactionType.EXPENSE, amount = 50.0, accountId = 1L)
        val new = TransactionEntity(id = 1L, type = TransactionType.EXPENSE, amount = 50.0, accountId = 2L)
        repo.updateTransaction(old, new)
        coVerify { accountDao.updateBalance(1L, +50.0) } // revert old expense
        coVerify { accountDao.updateBalance(2L, -50.0) } // apply new expense
    }

    @Test
    fun `updateTransaction with amount change applies correct delta`() = runTest {
        val old = TransactionEntity(id = 1L, type = TransactionType.EXPENSE, amount = 100.0, accountId = 1L)
        val new = TransactionEntity(id = 1L, type = TransactionType.EXPENSE, amount = 150.0, accountId = 1L)
        repo.updateTransaction(old, new)
        coVerify { accountDao.updateBalance(1L, +100.0) }
        coVerify { accountDao.updateBalance(1L, -150.0) }
    }

    @Test
    fun `updateTransaction follows revert-then-apply order`() = runTest {
        val old = TransactionEntity(id = 1L, type = TransactionType.INCOME, amount = 50.0, accountId = 1L)
        val new = TransactionEntity(id = 1L, type = TransactionType.INCOME, amount = 80.0, accountId = 1L)
        repo.updateTransaction(old, new)
        coVerifyOrder {
            accountDao.updateBalance(1L, -50.0) // revert old
            txDao.updateTransaction(new)        // persist
            accountDao.updateBalance(1L, +80.0) // apply new
        }
    }

    @Test
    fun `updateTransaction old transfer toAccount changes correctly`() = runTest {
        val old = TransactionEntity(id = 1L, type = TransactionType.TRANSFER, amount = 100.0, accountId = 1L, toAccountId = 2L)
        val new = TransactionEntity(id = 1L, type = TransactionType.TRANSFER, amount = 100.0, accountId = 1L, toAccountId = 3L)
        repo.updateTransaction(old, new)
        coVerify { accountDao.updateBalance(2L, -100.0) } // revert old to
        coVerify { accountDao.updateBalance(3L, +100.0) } // apply new to
    }

    // ── переказ між валютами (serg/tasks#721) ────────────────────────────────

    private fun account(id: Long, currency: String) =
        AccountEntity(id = id, name = "acc$id", currency = currency)

    @Test
    fun `transfer between different currencies converts credited amount`() = runTest {
        coEvery { accountDao.getAccountById(1L) } returns account(1L, "UAH")
        coEvery { accountDao.getAccountById(2L) } returns account(2L, "USD")
        val tx = TransactionEntity(type = TransactionType.TRANSFER, amount = 820.0, accountId = 1L, toAccountId = 2L)

        repo.addTransaction(tx)

        coVerify { accountDao.updateBalance(1L, -820.0) }   // списано у гривнях
        coVerify { accountDao.updateBalance(2L, +20.0) }    // зараховано в доларах (курс 41)
    }

    @Test
    fun `transfer within one currency is not converted`() = runTest {
        coEvery { accountDao.getAccountById(1L) } returns account(1L, "USD")
        coEvery { accountDao.getAccountById(2L) } returns account(2L, "USD")
        val tx = TransactionEntity(type = TransactionType.TRANSFER, amount = 21.45, accountId = 1L, toAccountId = 2L)

        repo.addTransaction(tx)

        coVerify { accountDao.updateBalance(1L, -21.45) }
        coVerify { accountDao.updateBalance(2L, +21.45) }
    }

    @Test
    fun `converted amount is stored with the transaction`() = runTest {
        coEvery { accountDao.getAccountById(1L) } returns account(1L, "UAH")
        coEvery { accountDao.getAccountById(2L) } returns account(2L, "USD")
        val tx = TransactionEntity(type = TransactionType.TRANSFER, amount = 410.0, accountId = 1L, toAccountId = 2L)

        repo.addTransaction(tx)

        coVerify { txDao.insertTransaction(tx.copy(toAmount = 10.0)) }
    }

    @Test
    fun `delete reverses the stored credited amount, not the source amount`() = runTest {
        // Курс з часу створення поїхав, але знімати треба рівно те, що поклали
        every { ratesRepo.rates } returns flowOf(mapOf("UAH" to 1.0, "USD" to 50.0))
        coEvery { accountDao.getAccountById(1L) } returns account(1L, "UAH")
        coEvery { accountDao.getAccountById(2L) } returns account(2L, "USD")
        val tx = TransactionEntity(
            id = 7L, type = TransactionType.TRANSFER, amount = 820.0,
            accountId = 1L, toAccountId = 2L, toAmount = 20.0
        )

        repo.deleteTransaction(tx)

        coVerify { accountDao.updateBalance(1L, +820.0) }
        coVerify { accountDao.updateBalance(2L, -20.0) }
    }

    @Test
    fun `unknown rate credits as is and does not silently distort`() = runTest {
        every { ratesRepo.rates } returns flowOf(mapOf("UAH" to 1.0))   // USD у кеші немає
        coEvery { accountDao.getAccountById(1L) } returns account(1L, "UAH")
        coEvery { accountDao.getAccountById(2L) } returns account(2L, "USD")
        val tx = TransactionEntity(type = TransactionType.TRANSFER, amount = 820.0, accountId = 1L, toAccountId = 2L)

        repo.addTransaction(tx)

        coVerify { accountDao.updateBalance(2L, +820.0) }
    }

    @Test
    fun `moving an expense to a foreign-currency account converts too`() = runTest {
        coEvery { accountDao.getAccountById(1L) } returns account(1L, "UAH")
        coEvery { accountDao.getAccountById(2L) } returns account(2L, "USD")
        val old = TransactionEntity(id = 3L, type = TransactionType.EXPENSE, amount = 820.0, accountId = 1L)
        val new = old.copy(type = TransactionType.TRANSFER, toAccountId = 2L)

        repo.updateTransaction(old, new)

        coVerify { accountDao.updateBalance(1L, +820.0) }  // відкат витрати
        coVerify { accountDao.updateBalance(1L, -820.0) }  // списання переказу
        coVerify { accountDao.updateBalance(2L, +20.0) }   // зарахування в доларах
        coVerify { txDao.updateTransaction(new.copy(toAmount = 20.0)) }
    }

    @Test
    fun `changing transfer source currency recalculates credit`() = runTest {
        coEvery { accountDao.getAccountById(3L) } returns account(3L, "USD")
        coEvery { accountDao.getAccountById(2L) } returns account(2L, "USD")
        val old = TransactionEntity(id = 7L, type = TransactionType.TRANSFER,
            amount = 820.0, accountId = 1L, toAccountId = 2L, toAmount = 20.0)
        val updated = old.copy(accountId = 3L)
        repo.updateTransaction(old, updated)
        coVerify { txDao.updateTransaction(updated.copy(toAmount = 820.0)) }
        coVerify { accountDao.updateBalance(2L, -20.0) }
        coVerify { accountDao.updateBalance(2L, 820.0) }
    }

    @Test
    fun `editing transfer note preserves historical credit after rate change`() = runTest {
        every { ratesRepo.rates } returns flowOf(mapOf("UAH" to 1.0, "USD" to 50.0))
        val old = TransactionEntity(id = 7L, type = TransactionType.TRANSFER,
            amount = 820.0, accountId = 1L, toAccountId = 2L, toAmount = 20.0)
        val updated = old.copy(note = "edited")
        repo.updateTransaction(old, updated)
        coVerify { txDao.updateTransaction(updated) }
        coVerify { accountDao.updateBalance(2L, -20.0) }
        coVerify { accountDao.updateBalance(2L, 20.0) }
    }
}
