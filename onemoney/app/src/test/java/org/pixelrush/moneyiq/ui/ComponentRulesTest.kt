package org.syalosovetskyi.onemoney.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.data.repository.AppMonth
import org.syalosovetskyi.onemoney.data.repository.PeriodMode
import org.syalosovetskyi.onemoney.ui.components.calculator.*
import org.syalosovetskyi.onemoney.ui.main.pillBadgeFor
import org.syalosovetskyi.onemoney.ui.theme.CategoryScreenTokens
import java.util.Calendar

class ComponentRulesTest {
    @Test fun category_style_keeps_user_color_for_unknown_categories() {
        for (type in TransactionType.entries) {
            val budget = CategoryScreenTokens.resolve("Custom", type, Color.Blue, true)
            val plain = CategoryScreenTokens.resolve("Custom", type, Color.Blue, false)
            assertEquals(Color.Blue, budget.iconTint)
            assertEquals(Color.Blue, plain.iconTint)
            assertEquals(0.22f, budget.circleBg.alpha, 0.005f)
            assertEquals(0.10f, plain.circleBg.alpha, 0.005f)
        }
        val known = CategoryScreenTokens.byName.getValue("Продукти")
        assertEquals(known, CategoryScreenTokens.resolve("Продукти", TransactionType.EXPENSE, Color.Red, false))
    }

    @Test fun calendar_same_day_compares_year_and_day_ignoring_time() {
        val a = Calendar.getInstance().apply { set(2024, 0, 1, 0, 0) }
        val b = (a.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 23) }
        assertTrue(sameDay(a, b))
        b.add(Calendar.YEAR, 1)
        assertFalse(sameDay(a, b))
        b.timeInMillis = a.timeInMillis
        b.add(Calendar.DAY_OF_YEAR, 1)
        assertFalse(sameDay(a, b))
    }

    @Test fun unsupported_repeat_and_reminder_modes_fall_back_to_never() {
        assertEquals(R.string.rep_never, repeatLabelResFor("obsolete"))
        assertEquals(R.string.rep_never, reminderLabelResFor("obsolete"))
        assertEquals(R.string.rep_daily, repeatLabelResFor("DAILY"))
        assertEquals(R.string.rem_7_days, reminderLabelResFor("7_DAYS"))
    }

    @Test fun period_badges_use_semantic_counts() {
        for ((mode, expected) in listOf(PeriodMode.ALL to "∞", PeriodMode.TODAY to "1",
            PeriodMode.WEEK to "7", PeriodMode.MONTH to "29", PeriodMode.YEAR to "29",
            PeriodMode.DAY to "29", PeriodMode.RANGE to "29")) {
            assertEquals(expected, pillBadgeFor(AppMonth(year = 2024, month = 1, mode = mode), 29))
        }
    }
}
