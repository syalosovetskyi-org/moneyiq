package org.syalosovetskyi.onemoney.util

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.syalosovetskyi.onemoney.data.db.dao.TransactionWithDetails
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.ui.settings.data.formatMoneyWithSettings
import org.syalosovetskyi.onemoney.ui.budget.periodBudget
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity

class CurrencyAndFormatTest {
    private fun tx(currency: String, type: TransactionType, amount: Double) = TransactionWithDetails(
        1, type, amount, 1, "Cash", "#123456", currency, null, null, null, null, null, null, "", 0)

    @Test fun `mixed currencies remain separate and transfers excluded`() {
        val totals = currencyTotals(listOf(tx("UAH", TransactionType.EXPENSE, 100.0),
            tx("USD", TransactionType.EXPENSE, 100.0), tx("USD", TransactionType.INCOME, 30.0),
            tx("USD", TransactionType.TRANSFER, 900.0)))
        assertEquals(CurrencyTotal(0.0, 100.0), totals["UAH"])
        assertEquals(CurrencyTotal(30.0, 100.0), totals["USD"])
    }

    @Test fun `weekly limit scales by calendar days without altering stored value`() {
        val category = CategoryEntity(name = "Food", type = TransactionType.EXPENSE,
            budgetAmount = 70.0, budgetPeriod = "WEEKLY")
        assertEquals(70.0, periodBudget(category, 7), 0.00001)
        assertEquals(310.0, periodBudget(category, 31), 0.00001)
        assertEquals(70.0, category.budgetAmount, 0.00001)
        assertEquals(70.0, periodBudget(category.copy(budgetPeriod = "MONTHLY"), 31), 0.00001)
    }

    @Test fun `rounding carries into integer part`() {
        assertEquals("1 000,00 ₴", formatMoneyWithSettings(999.999, "₴", 2))
        assertEquals("−1 000,00 ₴", formatMoneyWithSettings(-999.999, "₴", 2))
    }

    @Test fun `ukrainian currency format does not depend on device locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val formatted = formatMoney(-1234.5, "UAH")
            assertTrue(formatted.startsWith("−1"))
            assertTrue(formatted.contains(",50"))
            assertTrue(formatted.endsWith(" ₴"))
        } finally { Locale.setDefault(previous) }
    }

    @Test fun `account assets and debts remain in their own currencies`() {
        val totals = accountCurrencyTotals(listOf(
            org.syalosovetskyi.onemoney.data.db.entities.AccountEntity(name = "UAH", balance = 100.0),
            org.syalosovetskyi.onemoney.data.db.entities.AccountEntity(name = "USD", currency = "USD", balance = 100.0),
            org.syalosovetskyi.onemoney.data.db.entities.AccountEntity(name = "Debt", currency = "USD", balance = -30.0)))
        assertEquals(100.0, totals["UAH"]!!.net, 0.0)
        assertEquals(70.0, totals["USD"]!!.net, 0.0)
    }
}
