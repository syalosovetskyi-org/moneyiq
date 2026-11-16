package org.syalosovetskyi.onemoney.ui.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R8/R9 (serg/tasks#3037): перевитрата видна явно, дохід понад план — позитивне відхилення. */
class BudgetDeviationTest {
    private fun norm(s: String) = s.replace(' ', ' ').replace(' ', ' ')

    @Test fun `expense over plan is overspent with minus sign`() {
        val d = budgetDeviation(isIncome = false, plan = 8000.0, actual = 9876.54)
        assertEquals(BudgetDeviationKind.OVERSPENT, d.kind)
        assertEquals(1876.54, d.amount, 0.001)
        assertEquals("−1 876,54 ₴", norm(d.signedText("UAH")))
    }

    @Test fun `income over plan is positive not minus`() {
        // Снимок budget_data-end: дохід 1 239 880,29 при плані 0 показувався червоним «−1 239 880,29 ₴».
        val d = budgetDeviation(isIncome = true, plan = 0.0, actual = 1_239_880.29)
        assertEquals(BudgetDeviationKind.ABOVE_PLAN, d.kind)
        val text = norm(d.signedText("UAH"))
        assertTrue(text, text.startsWith("+1 239 880,29"))
        assertTrue(text, !text.contains("−"))
    }

    @Test fun `under plan is remaining for both expense and income`() {
        assertEquals(BudgetDeviation(BudgetDeviationKind.REMAINING, 500.0), budgetDeviation(false, 2000.0, 1500.0))
        assertEquals(BudgetDeviation(BudgetDeviationKind.REMAINING, 500.0), budgetDeviation(true, 2000.0, 1500.0))
        assertEquals(BudgetDeviationKind.ON_PLAN, budgetDeviation(true, 2000.0, 2000.0).kind)
    }
}
