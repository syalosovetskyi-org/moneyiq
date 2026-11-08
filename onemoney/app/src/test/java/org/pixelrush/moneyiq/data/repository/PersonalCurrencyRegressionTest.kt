package org.syalosovetskyi.onemoney.data.repository

import io.mockk.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.syalosovetskyi.onemoney.data.db.dao.*
import org.syalosovetskyi.onemoney.data.db.entities.*
import org.syalosovetskyi.onemoney.util.*

class PersonalCurrencyRegressionTest {
    private val accounts = mockk<AccountDao>()
    private val txs = mockk<TransactionDao>()
    private val rates = mockk<CurrencyRatesRepository>()
    private fun row(amount: Double = 200.0, currency: String = "RUB") = TransactionWithDetails(
        1, TransactionType.EXPENSE, amount, 1, "Wallet", "#000000", currency, null, null,
        1, "Продукти", "#000000", "grocery", "", 100)
    private fun setup(rate: Double? = 0.5) {
        every { accounts.getAllAccounts() } returns flowOf(listOf(AccountEntity(id = 1, name = "Wallet", balance = -200.0, currency = "RUB")))
        every { txs.getTransactionsByDateRange(any(), any()) } returns flowOf(listOf(row()))
        every { txs.observeAllTransactions() } returns flowOf(listOf(TransactionEntity(id = 1, type = TransactionType.EXPENSE, amount = 200.0, accountId = 1, date = 100)))
        every { rates.rates } returns flowOf(if (rate == null) mapOf("UAH" to 1.0) else mapOf("UAH" to 1.0, "RUB" to rate))
    }
    @Test fun `bug 2 all accounts converts foreign negative balance`() = runTest {
        setup()
        assertEquals(-100.0, AccountRepository(accounts, rates).getTotalBalance().first()!!, 0.001)
    }
    @Test fun `bug 2 unavailable currency is explicit partial total`() = runTest {
        setup(null)
        val value = AccountRepository(accounts, rates).getValuation().first()
        assertFalse(value.complete)
        assertEquals(setOf("RUB"), value.missingCurrencies)
    }
    @Test fun `bug 3 category and expense ring use converted amount`() = runTest {
        setup()
        val repo = TransactionRepository(txs, accounts, rates)
        assertEquals(100.0, repo.getCategorySpending(TransactionType.EXPENSE, 0, 200).first().single().total, 0.001)
        assertEquals(100.0, repo.getExpenseSum(0, 200).first(), 0.001)
        assertEquals(200.0, repo.getTransactionsByPeriod(0, 200).first().single().amount, 0.001)
    }
    @Test fun `bug 4 opening is zero and closing negative with exchange rate`() = runTest {
        setup()
        val balances = TransactionRepository(txs, accounts, rates).getPeriodBalances(0, 200).first()
        assertEquals(0.0, balances.opening.amount, 0.001)
        assertEquals(-100.0, balances.closing.amount, 0.001)
    }
    @Test fun `bug 5 overview balance and expense use same units`() = runTest {
        setup()
        assertEquals(-100.0, AccountRepository(accounts, rates).getTotalBalance().first()!!, 0.001)
        val projection = TransactionRepository(txs, accounts, rates).getBaseTransactionsByPeriod(0, 200).first().single()
        assertEquals("UAH", projection.accountCurrency)
        assertEquals(100.0, projection.amount, 0.001)
    }
    @Test fun `bug 6 forecast absent with no income and no budget`() {
        assertFalse(canForecast(0.0, 0.0, 200.0, 8, 30))
        assertTrue(canForecast(1000.0, 0.0, 200.0, 8, 30))
        assertFalse(canForecast(1000.0, 0.0, 200.0, 0, 30))
    }
    @Test fun `historical boundaries remove transactions after selected period`() {
        val a = AccountEntity(id = 1, name = "A", balance = -250.0)
        val txs = listOf(TransactionEntity(type = TransactionType.EXPENSE, amount = 200.0, accountId = 1, date = 100),
            TransactionEntity(type = TransactionType.EXPENSE, amount = 50.0, accountId = 1, date = 300))
        val result = periodBalances(listOf(a), txs, emptyMap(), 0, 200)
        assertEquals(0.0, result.opening.amount, 0.001)
        assertEquals(-200.0, result.closing.amount, 0.001)
    }
    @Test fun `same-currency transfer to excluded account changes included period total`() {
        val a = listOf(AccountEntity(id = 1, name = "A", balance = 75.0), AccountEntity(id = 2, name = "B", balance = 25.0, includeInTotal = false))
        val result = periodBalances(a, listOf(TransactionEntity(type = TransactionType.TRANSFER, amount = 25.0, accountId = 1, toAccountId = 2, date = 100)), emptyMap(), 0, 200)
        assertEquals(100.0, result.opening.amount, 0.001)
        assertEquals(75.0, result.closing.amount, 0.001)
    }
    @Test fun `missing rate never relabels a raw amount as UAH`() = runTest {
        setup(null)
        assertTrue(TransactionRepository(txs, accounts, rates).getBaseTransactionsByPeriod(0, 200).first().isEmpty())
    }
}
