package org.syalosovetskyi.onemoney.ui.shots

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.syalosovetskyi.onemoney.data.db.entities.AccountType
import org.syalosovetskyi.onemoney.data.repository.AppMonth
import org.syalosovetskyi.onemoney.data.repository.PeriodMode
import org.syalosovetskyi.onemoney.ui.accounts.ACCOUNT_FORM_COLORS
import org.syalosovetskyi.onemoney.ui.accounts.AccountActionSheet
import org.syalosovetskyi.onemoney.ui.accounts.AccountFormSheet
import org.syalosovetskyi.onemoney.ui.accounts.BalanceInputDialog
import org.syalosovetskyi.onemoney.ui.accounts.ColorPickerSheet
import org.syalosovetskyi.onemoney.ui.accounts.DescEditorDialog
import org.syalosovetskyi.onemoney.ui.accounts.NewAccountTypeSheet
import org.syalosovetskyi.onemoney.ui.accounts.TypePickerSheet
import org.syalosovetskyi.onemoney.ui.budget.BudgetCatRow
import org.syalosovetskyi.onemoney.ui.budget.BudgetInputSheet
import org.syalosovetskyi.onemoney.ui.budget.BudgetSettingsSheet
import org.syalosovetskyi.onemoney.ui.budget.IncomeCategoryPickerSheet
import org.syalosovetskyi.onemoney.ui.categories.CategoryActionSheet
import org.syalosovetskyi.onemoney.ui.categories.CategoryFormSheet
import org.syalosovetskyi.onemoney.ui.categories.ColorIconPickerSheet
import org.syalosovetskyi.onemoney.ui.categories.ParentPickerDialog
import org.syalosovetskyi.onemoney.ui.categories.QuickCategoryPickerSheet
import org.syalosovetskyi.onemoney.ui.categories.QuickExpenseSheet
import org.syalosovetskyi.onemoney.ui.components.calculator.AccountPickerSheet
import org.syalosovetskyi.onemoney.ui.components.calculator.AmountCalculatorSheet
import org.syalosovetskyi.onemoney.ui.components.calculator.CalcDateSheet
import org.syalosovetskyi.onemoney.ui.components.calculator.FullDatePickerDialog
import org.syalosovetskyi.onemoney.ui.components.calculator.ReminderDialog
import org.syalosovetskyi.onemoney.ui.components.calculator.RepeatDialog
import org.syalosovetskyi.onemoney.ui.components.currency.CurrencyBottomSheet
import org.syalosovetskyi.onemoney.ui.components.currency.CurrencyPickerSheet
import org.syalosovetskyi.onemoney.ui.components.dialogs.ConfirmationDialog
import org.syalosovetskyi.onemoney.ui.components.dialogs.TextInputDialog
import org.syalosovetskyi.onemoney.ui.data.ResetDataDialog
import org.syalosovetskyi.onemoney.ui.main.DateRangePickerFullScreen
import org.syalosovetskyi.onemoney.ui.main.PeriodSelectorSheet
import org.syalosovetskyi.onemoney.ui.main.SharedMonthNavPill
import org.syalosovetskyi.onemoney.ui.overview.CategoryDetailSheet
import org.syalosovetskyi.onemoney.ui.settings.RadioListDialog
import org.syalosovetskyi.onemoney.ui.settings.TimePickerDialog
import org.syalosovetskyi.onemoney.ui.transactions.CategoryPickerSheet
import org.syalosovetskyi.onemoney.ui.transactions.TransactionDetailSheet
import org.syalosovetskyi.onemoney.ui.transactions.TransferQuickSheet
import java.util.Calendar
import org.syalosovetskyi.onemoney.ui.shots.ShotData as D

/**
 * Шторки й діалоги — кожен викликаний напряму, без навігації (serg/tasks#3037).
 * Матриця урізана до S23 і Fold закритого (обидва шрифти) + 2 темні: книжка/горизонт — лише для екранів.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class, qualifiers = "uk")
class AllSheetsShotsTest(case: ShotCase) : ShotTestBase(case) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = ShotCase.expand(specs(), ShotMatrix.sheets)

        private fun specs(): List<ShotSpec> = operations() + categories() + accounts() + budgetAndPeriod() + common()

        private fun operations() = listOf(
            ShotSpec("amount-calc", "data") {
                AmountCalculatorSheet(initial = 1_234_567.89, currencySymbol = "₴", onResult = {}, onDismiss = {})
            },
            ShotSpec("calc-date", "data") {
                CalcDateSheet(currentDate = D.at(15, 10), repeatMode = "MONTHLY", reminderMode = "SAME_DAY",
                    onDateSelected = {}, onRepeatClick = {}, onReminderClick = {}, onPickDate = {}, onDismiss = {})
            },
            ShotSpec("full-date-picker", "data") {
                FullDatePickerDialog(initial = D.at(15), onDateSelected = {}, onDismiss = {})
            },
            ShotSpec("time-picker", "data") {
                TimePickerDialog(hour = 21, minute = 30, onConfirm = { _, _ -> }, onDismiss = {})
            },
            ShotSpec("category-picker", "income") { CategoryPicker(initialTab = 0) },
            ShotSpec("category-picker", "expense") { CategoryPicker(initialTab = 1) },
            ShotSpec("category-picker", "transfer") { CategoryPicker(initialTab = 2) },
            ShotSpec("quick-category-picker", "data") {
                QuickCategoryPickerSheet(categories = D.expenseCategories, selectedCategoryId = D.cat("Продукти").id,
                    includeSubcategories = true, transferAccounts = D.accounts, onSelectAccount = {},
                    onSelect = {}, onDismiss = {})
            },
            ShotSpec("income-category-picker", "data") {
                IncomeCategoryPickerSheet(rows = D.incomeRoots.map { BudgetCatRow(it, D.income[it.id] ?: 0.0, 0.0) },
                    monthLabel = D.MONTH_LABEL, accentColor = INCOME_ACCENT, onPick = {}, onDismiss = {})
            },
            ShotSpec("account-picker", "data") {
                AccountPickerSheet(accounts = D.accounts, selectedId = D.accCash.id, onSelect = {}, onDismiss = {})
            },
            ShotSpec("repeat-dialog", "data") { RepeatDialog(current = "MONTHLY", onSelect = {}, onDismiss = {}) },
            ShotSpec("reminder-dialog", "data") { ReminderDialog(current = "SAME_DAY", onSelect = {}, onDismiss = {}) },
            ShotSpec("desc-editor", "data", pausedClock = true) {
                DescEditorDialog(initial = D.accMono.description, onSave = {}, onDismiss = {})
            },
            ShotSpec("tx-detail", "expense") { TxDetail(index = 1) },
            ShotSpec("tx-detail", "transfer") { TxDetail(index = 3) },
            ShotSpec("transfer-quick", "data") {
                TransferQuickSheet(fromAccount = D.accPrivat, allAccounts = D.accounts, onSave = { _, _, _ -> },
                    onDismiss = {})
            },
            ShotSpec("quick-expense", "data") {
                QuickExpenseSheet(category = D.cat(D.LONG_CATEGORY), categories = D.expenseCategories,
                    accounts = D.accounts, onSave = { _, _, _, _, _, _, _ -> }, onDismiss = {})
            },
        )

        private fun categories() = listOf(
            ShotSpec("category-form", "new") {
                CategoryFormSheet(existing = null, onSave = { _, _, _, _, _, _, _, _ -> }, onDismiss = {})
            },
            ShotSpec("category-form", "edit") {
                val parent = D.cat("Продукти")
                CategoryFormSheet(existing = parent, children = D.expenseCategories.filter { it.parentId == parent.id },
                    onAddSubcategory = {}, onDetachSubcategory = {}, onDeleteSubcategory = {},
                    onSave = { _, _, _, _, _, _, _, _ -> }, onDelete = {}, onDismiss = {})
            },
            ShotSpec("category-form", "subcategory") {
                val parent = D.cat("Продукти")
                val child = D.expenseCategories.first { it.parentId == parent.id }
                CategoryFormSheet(existing = child, parentOptions = D.expenseRoots, onChangeParent = {},
                    onSave = { _, _, _, _, _, _, _, _ -> }, onDelete = {}, onDismiss = {})
            },
            ShotSpec("parent-picker", "data") {
                ParentPickerDialog(options = D.expenseRoots, currentParent = D.cat("Продукти").id,
                    onSelect = {}, onDismiss = {})
            },
            ShotSpec("color-icon-picker", "data") {
                ColorIconPickerSheet(currentColor = "#6D4C41", currentIcon = "family", onSave = { _, _ -> }, onDismiss = {})
            },
            ShotSpec("category-action", "data") {
                CategoryActionSheet(category = D.cat(D.LONG_CATEGORY), spending = 3_456.78, txCount = 3,
                    totalInPeriod = D.totalExpense, pillLabel = D.MONTH_LABEL.uppercase(), onEdit = {}, onBudget = {},
                    onOperations = {}, onDismiss = {})
            },
            ShotSpec("category-detail", "data") {
                val row = D.overviewRows.first { it.name == D.LONG_CATEGORY }
                CategoryDetailSheet(cat = row, catColor = Color(0xFF6D4C41), monthLabel = D.MONTH_LABEL,
                    onAddExpense = {}, onOperations = {})
            },
        )

        private fun accounts() = listOf(
            ShotSpec("account-form", "new") {
                AccountFormSheet(initialType = AccountType.CARD, existing = null,
                    onSave = { _, _, _, _, _, _, _, _, _ -> }, onDismiss = {})
            },
            ShotSpec("account-form", "edit") {
                AccountFormSheet(initialType = D.accMono.type, existing = D.accMono,
                    onSave = { _, _, _, _, _, _, _, _, _ -> }, onDismiss = {})
            },
            ShotSpec("account-form", "credit") {
                AccountFormSheet(initialType = D.accCredit.type, existing = D.accCredit,
                    onSave = { _, _, _, _, _, _, _, _, _ -> }, onDismiss = {})
            },
            ShotSpec("new-account-type", "data") { NewAccountTypeSheet(onSelect = {}, onDismiss = {}) },
            ShotSpec("type-picker", "data") { TypePickerSheet(selected = AccountType.CARD, onSelect = {}, onDismiss = {}) },
            ShotSpec("color-picker", "data") {
                ColorPickerSheet(colors = ACCOUNT_FORM_COLORS, selected = ACCOUNT_FORM_COLORS.first(), onSelect = {},
                    onDismiss = {})
            },
            ShotSpec("balance-input", "data", pausedClock = true) {
                BalanceInputDialog(initial = "1234567.89", symbol = "₴", onSave = {}, onDismiss = {})
            },
            ShotSpec("currency-picker", "data") { CurrencyPickerSheet(selected = "UAH", onSelect = {}, onDismiss = {}) },
            ShotSpec("currency-bottom-sheet", "data") {
                CurrencyBottomSheet(selected = "USD", onSelect = {}, onDismiss = {})
            },
            ShotSpec("account-action", "data") {
                AccountActionSheet(account = D.accMono, onDismiss = {}, onEdit = {}, onAdjustBalance = {},
                    onTransactions = {}, onIncome = {}, onExpense = {}, onTransfer = {}, onSetDefault = {})
            },
        )

        private fun budgetAndPeriod() = listOf(
            ShotSpec("budget-input", "overspent") {
                val c = D.cat("Продукти")
                BudgetInputSheet(catRow = BudgetCatRow(c, D.spending[c.id] ?: 0.0, c.budgetAmount),
                    monthLabel = D.MONTH_LABEL, accentColor = EXPENSE_ACCENT, onDismiss = {}, onConfirm = { _, _ -> })
            },
            ShotSpec("budget-settings", "data") {
                BudgetSettingsSheet(monthLabel = D.MONTH_LABEL, currentExpensesMode = false, onToggleMode = {},
                    onDeleteBudget = {}, onDismiss = {})
            },
            ShotSpec("period-selector", "data") {
                PeriodSelectorSheet(appMonth = D.appMonth, daysInPeriod = D.DAYS, onDismiss = {}, onSelectPeriod = {})
            },
            ShotSpec("date-range-picker", "data") { DateRangePickerFullScreen(onDismiss = {}, onConfirm = { _, _ -> }) },
            ShotSpec("month-pill", "current") { MonthPill(currentMonth()) },
            ShotSpec("month-pill", "past") { MonthPill(AppMonth(2026, 4)) },
            ShotSpec("month-pill", "range") {
                MonthPill(AppMonth(D.YEAR, D.MONTH, PeriodMode.RANGE, D.at(1), D.at(30, 23, 59)))
            },
        )

        private fun common() = listOf(
            ShotSpec("radio-list", "data") {
                RadioListDialog(title = "Перший день тижня", icon = Icons.Filled.DateRange,
                    options = listOf("Понеділок", "Неділя", "Субота"), selected = 0, onSelect = {}, onDismiss = {})
            },
            ShotSpec("text-input", "data", pausedClock = true) {
                TextInputDialog(title = "Назва резервної копії", label = "Назва",
                    initialValue = "Резервна копія перед оновленням застосунку", onConfirm = {}, onDismiss = {})
            },
            ShotSpec("reset-data", "data") { ResetDataDialog(onDeleteAll = {}, onDeleteTransactions = {}, onDismiss = {}) },
            ShotSpec("confirmation", "data") {
                ConfirmationDialog(title = "Видалити категорію?",
                    message = "Категорію «${D.LONG_CATEGORY}» і 12 операцій буде видалено назавжди.",
                    icon = Icons.Filled.Delete, onConfirm = {}, onDismiss = {})
            },
        )

        private val EXPENSE_ACCENT = Color(0xFFD7261E)
        private val INCOME_ACCENT = Color(0xFF2E7D32)

        private fun currentMonth(): AppMonth {
            val c = Calendar.getInstance()
            return AppMonth(c.get(Calendar.YEAR), c.get(Calendar.MONTH))
        }
    }
}

@androidx.compose.runtime.Composable
private fun CategoryPicker(initialTab: Int) {
    CategoryPickerSheet(expenseCategories = D.expenseCategories, incomeCategories = D.incomeCategories,
        accounts = D.accounts, categorySpending = D.spending.mapKeys { (k, _) -> k as Long? } + D.income.mapKeys { (k, _) -> k as Long? },
        initialTab = initialTab, onSelect = {}, onTransfer = {}, onDismiss = {})
}

@androidx.compose.runtime.Composable
private fun TxDetail(index: Int) {
    TransactionDetailSheet(tx = D.transactions[index], categories = D.categories, accounts = D.accounts,
        onDismiss = {}, onDelete = {}, onDuplicate = {}, onSave = { _, _, _, _, _ -> })
}

@androidx.compose.runtime.Composable
private fun MonthPill(month: AppMonth) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        SharedMonthNavPill(appMonth = month, daysInPeriod = D.DAYS, onPrev = {}, onNext = {}, onSelectPeriod = {})
    }
}
