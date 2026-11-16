package org.syalosovetskyi.onemoney.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import org.syalosovetskyi.onemoney.data.db.entities.AccountType

/**
 * R3/R4/R10 (serg/tasks#3037): одна конвенція знака DEBT-рахунків для «Мої фінанси»
 * ([accountCurrencyTotals]) і шапки main ([accountValuation]); скорочений запис сум.
 */
class DebtSignAndAmountFormatTest {
    private val noRates = emptyMap<String, Double>()

    /** NumberFormat uk-UA групує розряди NBSP або NNBSP залежно від JDK — для порівняння зводимо до пробілу. */
    private fun norm(s: String) = s.replace(' ', ' ').replace(' ', ' ')

    @Test fun `debt minus 1000 and cash 5000 give 4000 in my finances and in main header`() {
        val accounts = listOf(
            AccountEntity(name = "Кредитка", type = AccountType.DEBT, balance = -1000.0),
            AccountEntity(name = "Готівка", type = AccountType.CASH, balance = 5000.0))
        val finances = accountCurrencyTotals(accounts).getValue("UAH")
        assertEquals(5000.0, finances.assets, 0.0)
        assertEquals(1000.0, finances.debts, 0.0)
        assertEquals(4000.0, finances.net, 0.0)
        assertEquals(4000.0, accountValuation(accounts, noRates).amount, 0.0)
    }

    @Test fun `review fixture total subtracts debt instead of adding it`() {
        // Снимок accounts_finances: активи 1 282 777,94, борг −25 000 → разом 1 257 777,94 (було 1 307 777,94).
        val accounts = listOf(
            AccountEntity(name = "Картка", type = AccountType.CARD, balance = 1_234_567.89),
            AccountEntity(name = "Готівка", type = AccountType.CASH, balance = 48_210.05),
            AccountEntity(name = "Кредитка", type = AccountType.DEBT, balance = -25_000.0))
        val net = accountCurrencyTotals(accounts).getValue("UAH").net
        assertEquals(1_257_777.94, net, 0.001)
        assertEquals(net, accountValuation(accounts, noRates).amount, 0.001)
    }

    @Test fun `positive debt balance follows the same signed convention in both totals`() {
        // Баланс зберігається зі знаком (TransactionRepository.updateBalance), окремої «додатної»
        // конвенції для DEBT немає: DEBT +1000 — переплата / нам винні, тобто актив, однаково всюди.
        val accounts = listOf(
            AccountEntity(name = "Нам винні", type = AccountType.DEBT, balance = 1000.0),
            AccountEntity(name = "Готівка", type = AccountType.CASH, balance = 5000.0))
        val finances = accountCurrencyTotals(accounts).getValue("UAH")
        assertEquals(0.0, finances.debts, 0.0)
        assertEquals(6000.0, finances.net, 0.0)
        assertEquals(finances.net, accountValuation(accounts, noRates).amount, 0.0)
    }

    @Test fun `negative non-debt balance is a debt and excluded accounts are ignored in both totals`() {
        val accounts = listOf(
            AccountEntity(name = "Овердрафт", type = AccountType.CARD, balance = -300.0),
            AccountEntity(name = "Готівка", type = AccountType.CASH, balance = 1000.0),
            AccountEntity(name = "Поза підсумком", type = AccountType.DEBT, balance = -5000.0, includeInTotal = false))
        val finances = accountCurrencyTotals(accounts).getValue("UAH")
        assertEquals(300.0, finances.debts, 0.0)
        assertEquals(700.0, finances.net, 0.0)
        assertEquals(700.0, accountValuation(accounts, noRates).amount, 0.0)
    }

    @Test fun `base amounts take the symbol from the reporting currency`() {
        assertEquals(formatMoney(1234.5, REPORTING_CURRENCY), formatBase(1234.5))
        assertTrue(formatBase(10.0).endsWith(" $REPORTING_CURRENCY_SYMBOL"))
    }

    private val suffixes = CompactSuffixes("тис.", "млн", "млрд")

    @Test fun `compact format shortens only large amounts`() {
        assertEquals(norm(formatMoney(9_999.0, "UAH")), norm(formatMoneyCompact(9_999.0, "UAH", suffixes)))
        assertEquals("1,2 млн ₴", norm(formatMoneyCompact(1_234_567.89, "UAH", suffixes)))
        assertEquals("−15,9 тис. $", norm(formatMoneyCompact(-15_899.0, "USD", suffixes)))
        assertEquals("1,8 млн ₴", norm(formatMoneyCompact(1_802_345.67, "UAH", suffixes)))
        assertEquals("2 млрд ₴", norm(formatMoneyCompact(2_000_000_000.0, "UAH", suffixes)))
    }

    @Test fun `compact rounding moves to the next tier instead of 1000 thousand`() {
        assertEquals("1 млн ₴", norm(formatMoneyCompact(999_960.0, "UAH", suffixes)))
        assertFalse(norm(formatMoneyCompact(999_960.0, "UAH", suffixes)).contains("тис."))
    }
}
