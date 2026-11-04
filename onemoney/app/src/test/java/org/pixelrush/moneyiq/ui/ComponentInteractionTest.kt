package org.syalosovetskyi.onemoney.ui

import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.data.db.entities.*
import org.syalosovetskyi.onemoney.ui.accounts.*
import org.syalosovetskyi.onemoney.ui.components.dialogs.*
import org.syalosovetskyi.onemoney.ui.components.calculator.*
import org.syalosovetskyi.onemoney.ui.components.currency.CurrencyPickerSheet
import org.syalosovetskyi.onemoney.ui.data.ResetDataDialog
import org.syalosovetskyi.onemoney.ui.overview.*
import org.syalosovetskyi.onemoney.ui.transactions.CategoryPickerSheet
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyThemeProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class, qualifiers = "uk")
class ComponentInteractionTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)
    private fun render(content: @Composable () -> Unit) {
        compose.setContent { MaterialTheme { OneMoneyThemeProvider { content() } } }
    }

    @Test fun confirmation_requires_explicit_confirm_and_cancel_never_confirms() {
        var confirmed = 0; var dismissed = 0
        render { ConfirmationDialog("Title", "Message", confirmText = "Confirm", dismissText = "Cancel",
            onConfirm = { confirmed++ }, onDismiss = { dismissed++ }) }
        compose.runOnIdle { assertEquals(0, confirmed) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, confirmed); assertEquals(1, dismissed) }
        compose.onNodeWithText("Confirm").performClick()
        compose.runOnIdle { assertEquals(1, confirmed) }
    }

    @Test fun text_input_rejects_blank_and_submits_edited_value() {
        var saved: String? = null
        render { TextInputDialog("Title", "Name", onConfirm = { saved = it }, onDismiss = {}) }
        compose.onNodeWithText("OK").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("   ")
        compose.onNodeWithText("OK").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("Edited")
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { assertEquals("Edited", saved) }
    }

    @Test fun text_input_cancel_keeps_unsaved_value_out_of_callback() {
        var saved: String? = null; var dismissed = false
        render { TextInputDialog("Title", "Name", initialValue = "Original", dismissText = "Cancel",
            onConfirm = { saved = it }, onDismiss = { dismissed = true }) }
        compose.onNode(hasSetTextAction()).performTextReplacement("Unsaved")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(dismissed); assertNull(saved) }
    }

    @Test fun account_description_sends_edited_text_only_on_save() {
        var saved: String? = null
        render { DescEditorDialog("Old", onSave = { saved = it }, onDismiss = {}) }
        compose.onNode(hasSetTextAction()).performTextReplacement("New description")
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithText(label(R.string.common_done)).performClick()
        compose.runOnIdle { assertEquals("New description", saved) }
    }

    @Test fun balance_input_filters_letters_and_preserves_decimal_separator() {
        var saved: String? = null
        render { BalanceInputDialog("", "₴", onSave = { saved = it }, onDismiss = {}) }
        compose.onNode(hasSetTextAction()).performTextReplacement("abc12,50")
        compose.onNodeWithText(label(R.string.common_done)).performClick()
        compose.runOnIdle { assertEquals("12,50", saved) }
    }

    @Test fun currency_picker_returns_currency_code() {
        var selected: String? = null
        render { CurrencyPickerSheet("UAH", onSelect = { selected = it }, onDismiss = {}) }
        compose.onNodeWithText(currencyDisplayName("USD")).performClick()
        compose.runOnIdle { assertEquals("USD", selected) }
    }

    @Test fun category_picker_calls_selection_with_original_entity() {
        val category = CategoryEntity(id = 42, name = "Food choice", type = TransactionType.EXPENSE)
        var selected: CategoryEntity? = null
        render { CategoryPickerSheet(listOf(category), emptyList(), emptyList(), emptyMap(),
            onSelect = { selected = it }, onTransfer = {}, onDismiss = {}) }
        compose.onNodeWithText("Food choice").performClick()
        compose.runOnIdle { assertEquals(category, selected) }
    }

    @Test fun category_picker_transfer_calls_account_callback() {
        val account = AccountEntity(id = 12, name = "Transfer target")
        var selected: AccountEntity? = null
        render { CategoryPickerSheet(emptyList(), emptyList(), listOf(account), emptyMap(), initialTab = 2,
            onSelect = {}, onTransfer = { selected = it }, onDismiss = {}) }
        compose.onNodeWithText("Transfer target").performClick()
        compose.runOnIdle { assertEquals(account, selected) }
    }

    @Test fun reset_dialog_cancel_does_not_delete_anything() {
        var all = 0; var transactions = 0; var dismissed = false
        render { ResetDataDialog({ all++ }, { transactions++ }, { dismissed = true }) }
        compose.onNodeWithText(label(R.string.data_dismiss)).performClick()
        compose.runOnIdle { assertTrue(dismissed); assertEquals(0, all); assertEquals(0, transactions) }
    }

    @Test fun reset_dialog_distinguishes_transactions_from_all_data() {
        var all = 0; var transactions = 0
        render { ResetDataDialog({ all++ }, { transactions++ }, {}) }
        compose.onNodeWithText(label(R.string.data_delete_all_tx)).performClick()
        compose.runOnIdle { assertEquals(0, all); assertEquals(1, transactions) }
        compose.onNodeWithText(label(R.string.data_delete_all)).performClick()
        compose.runOnIdle { assertEquals(1, all); assertEquals(1, transactions) }
    }

    @Test fun repeat_dialog_does_not_submit_until_confirmation() {
        var selected: String? = null
        render { RepeatDialog("NEVER", { selected = it }, {}) }
        compose.onNodeWithText(label(R.string.rep_daily)).performClick()
        compose.runOnIdle { assertNull(selected) }
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { assertEquals("DAILY", selected) }
    }

    @Test fun reminder_dialog_submits_selected_offset() {
        var selected: String? = null
        render { ReminderDialog("NEVER", { selected = it }, {}) }
        compose.onNodeWithText(label(R.string.rem_1_day)).performClick()
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { assertEquals("1_DAY", selected) }
    }
    @Test fun icon_picker_preserves_initial_selection_until_done() {
        var result: Pair<String, String>? = null
        render { IconColorPickerScreen("wallet", "#123456", onResult = { icon, color -> result = icon to color }, onDismiss = {}) }
        compose.runOnIdle { assertNull(result) }
        compose.onNodeWithText(label(R.string.common_done)).performClick()
        compose.runOnIdle { assertEquals("wallet" to "#123456", result) }
    }

    @Test fun icon_picker_close_does_not_save() {
        var saved = false; var dismissed = false
        render { IconColorPickerScreen("wallet", "#123456", onResult = { _, _ -> saved = true }, onDismiss = { dismissed = true }) }
        compose.onNodeWithContentDescription(label(R.string.acc_close)).performClick()
        compose.runOnIdle { assertTrue(dismissed); assertFalse(saved) }
    }

    @Test fun overview_detail_routes_both_actions() {
        var expense = 0; var operations = 0
        render { CategoryDetailSheet(OverviewCatRow(1, "Detail", "#123456", "grocery", 20.0, 0.5f, 40.0),
            Color.Blue, "Test month", { expense++ }, { operations++ }) }
        compose.onNodeWithText("Detail").assertIsDisplayed()
        compose.onNodeWithText("50%").assertExists()
        compose.onNodeWithText("Test month").assertExists()
        compose.onNodeWithText(label(R.string.tx_expense)).performClick()
        compose.onNodeWithText(label(R.string.nav_operations)).performClick()
        compose.runOnIdle { assertEquals(1, expense); assertEquals(1, operations) }
    }

    @Test fun keypad_taps_update_calculation() {
        lateinit var calc: CalcStateHolder
        render {
            calc = rememberCalcState()
            SharedCalcKeypad(calc, onConfirm = {})
        }
        compose.onNodeWithTag("calc_key_2").performClick()
        compose.onNodeWithTag("calc_key_+").performClick()
        compose.onNodeWithTag("calc_key_3").performClick()
        compose.runOnIdle { assertEquals(5.0, calc.result(), 0.0) }
    }

    @Test fun month_pill_routes_previous_and_next() {
        var previous = 0; var next = 0
        render { org.syalosovetskyi.onemoney.ui.main.SharedMonthNavPill(
            org.syalosovetskyi.onemoney.data.repository.AppMonth(2024, 1), 29, { previous++ }, { next++ }) }
        compose.onNodeWithTag("month_pill_prev").performClick()
        compose.onNodeWithTag("month_pill_next").performClick()
        compose.runOnIdle { assertEquals(1, previous); assertEquals(1, next) }
    }

    @Test fun category_chip_dispatches_click_long_press_and_double_click() {
        var clicks = 0; var longPresses = 0; var doubleClicks = 0
        render { org.syalosovetskyi.onemoney.ui.categories.CategoryChip(
            CategoryEntity(id = 1, name = "Gesture food", type = TransactionType.EXPENSE), 10.0,
            { clicks++ }, onLongPress = { longPresses++ }, onDoubleClick = { doubleClicks++ }) }
        // combinedClickable with onDoubleClick delays onClick until the double-tap
        // timeout expires, so the clock must move past it before the next gesture.
        compose.onNodeWithText("Gesture food").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Gesture food").performTouchInput { longClick() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Gesture food").performTouchInput { doubleClick() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertEquals(1, clicks); assertEquals(1, longPresses); assertEquals(1, doubleClicks) }
    }

    @Test fun settings_radio_list_returns_selected_index() {
        var selected: Int? = null
        render { org.syalosovetskyi.onemoney.ui.settings.RadioListDialog("Options",
            androidx.compose.material.icons.Icons.Default.Settings, listOf("First", "Second"), 0,
            { selected = it }, {}) }
        compose.onNodeWithText("Second").performClick()
        compose.runOnIdle { assertEquals(1, selected) }
    }

    @Test fun budget_settings_routes_mode_and_delete_callbacks() {
        var mode: Boolean? = null; var deletes = 0
        render { org.syalosovetskyi.onemoney.ui.budget.BudgetSettingsSheet("Test month", false,
            { mode = it }, { deletes++ }, {}) }
        compose.onNodeWithText(label(R.string.budget_current_spending)).performClick()
        compose.runOnIdle { assertEquals(true, mode); assertEquals(0, deletes) }
        compose.onNodeWithText(label(R.string.budget_delete)).performClick()
        compose.runOnIdle { assertEquals(1, deletes) }
    }

    @Test fun quick_transfer_rejects_zero_then_saves_positive_amount_to_other_account() {
        val from = AccountEntity(id = 1, name = "Source")
        val to = AccountEntity(id = 2, name = "Target")
        var result: Pair<Long, Double>? = null
        render { org.syalosovetskyi.onemoney.ui.transactions.TransferQuickSheet(from, listOf(from, to),
            { id, amount, _ -> result = id to amount }, {}) }
        compose.onNodeWithContentDescription(label(R.string.calc_save_tx)).performClick()
        compose.runOnIdle { assertNull(result) }
        compose.onNodeWithTag("calc_key_2").performClick()
        compose.onNodeWithTag("calc_key_5").performClick()
        compose.onNodeWithContentDescription(label(R.string.calc_save_tx)).performClick()
        compose.runOnIdle { assertEquals(2L to 25.0, result) }
    }

    @Test fun quick_transfer_with_one_account_cannot_save() {
        val from = AccountEntity(id = 1, name = "Source")
        var saved = false
        render { org.syalosovetskyi.onemoney.ui.transactions.TransferQuickSheet(from, listOf(from),
            { _, _, _ -> saved = true }, {}) }
        compose.onNodeWithTag("calc_key_5").performClick()
        compose.onNodeWithContentDescription(label(R.string.calc_save_tx)).performClick()
        compose.runOnIdle { assertFalse(saved) }
    }

    @Test fun transaction_detail_delete_requires_confirmation() {
        val tx = org.syalosovetskyi.onemoney.data.db.dao.TransactionWithDetails(
            1, TransactionType.EXPENSE, 25.0, 1, "Cash", "#123456", "UAH",
            null, null, null, null, null, null, "Original", 1700000000000)
        var deletes = 0; var duplicates = 0
        render { org.syalosovetskyi.onemoney.ui.transactions.TransactionDetailSheet(tx,
            onDismiss = {}, onDelete = { deletes++ }, onDuplicate = { duplicates++ },
            onSave = { _, _, _, _, _ -> }) }
        compose.onNodeWithText(label(R.string.tx_duplicate)).performClick()
        compose.runOnIdle { assertEquals(1, duplicates); assertEquals(0, deletes) }
        compose.onNodeWithText(label(R.string.common_delete)).performClick()
        compose.runOnIdle { assertEquals(0, deletes) }
        compose.onNodeWithText(label(R.string.common_cancel)).performClick()
        compose.runOnIdle { assertEquals(0, deletes) }
        compose.onNodeWithText(label(R.string.common_delete)).performClick()
        compose.onAllNodesWithText(label(R.string.common_delete)).onLast().performClick()
        compose.runOnIdle { assertEquals(1, deletes) }
    }

    @Test fun search_routes_query_type_reset_and_done() {
        var query = ""; var types = emptySet<String>(); var resets = 0; var done = 0
        render { org.syalosovetskyi.onemoney.ui.transactions.TxSearchScreen("", emptySet(), emptySet(), emptySet(),
            emptyList(), emptyList(), emptyList(), { query = it }, { types = it }, {}, {}, { resets++ }, { done++ }) }
        compose.onNode(hasSetTextAction()).performTextReplacement("Coffee")
        compose.onNodeWithText(label(R.string.tx_expense)).performClick()
        compose.onNodeWithContentDescription(label(R.string.tx_reset_close)).performClick()
        compose.onNodeWithText(label(R.string.common_done)).performClick()
        compose.runOnIdle {
            assertEquals("Coffee", query); assertEquals(setOf("EXPENSE"), types)
            assertEquals(1, resets); assertEquals(1, done)
        }
    }

    @Test fun edit_categories_switches_expense_income_and_subcategory_views() {
        val root = CategoryEntity(id = 1, name = "Root expense", type = TransactionType.EXPENSE)
        val child = CategoryEntity(id = 2, name = "Child expense", type = TransactionType.EXPENSE, parentId = 1)
        val income = CategoryEntity(id = 3, name = "Root income", type = TransactionType.INCOME)
        var dismissed = false
        render { org.syalosovetskyi.onemoney.ui.categories.EditCategoriesScreen(
            listOf(root, child), listOf(income), emptyMap(), emptyMap(), 0.0, 0.0,
            onSave = { _, _, _, _, _, _, _, _, _ -> }, onAddSubcategory = { _, _, _, _, _, _, _, _ -> },
            onDelete = {}, onDismiss = { dismissed = true }) }
        compose.onNodeWithText("Root expense").assertExists()
        compose.onNodeWithText("Child expense").assertDoesNotExist()
        compose.onNodeWithText(label(R.string.cat_subcategories_tab)).performClick()
        compose.onNodeWithText("Child expense").assertExists()
        compose.onNodeWithText(label(R.string.nav_categories)).performClick()
        compose.onNodeWithText(label(R.string.common_incomes)).performClick()
        compose.onNodeWithText("Root income").assertExists()
        compose.onNodeWithText("Root expense").assertDoesNotExist()
        compose.onNodeWithContentDescription(label(R.string.common_back)).performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun category_form_rejects_blank_then_submits_name() {
        var saved: String? = null
        render { org.syalosovetskyi.onemoney.ui.categories.CategoryFormSheet(existing = null,
            onSave = { name, _, _, _, _, _, _, _ -> saved = name }, onDismiss = {}) }
        compose.onNodeWithText(label(R.string.common_save)).assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("New category")
        compose.onNodeWithText(label(R.string.common_save)).performClick()
        compose.runOnIdle { assertEquals("New category", saved) }
    }

    @Test fun account_form_submits_edited_name() {
        var saved: String? = null
        render { AccountFormSheet(onSave = { name, _, _, _, _, _, _, _, _ -> saved = name }, onDismiss = {}) }
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("New cash")
        compose.onNodeWithText(label(R.string.common_done)).performClick()
        compose.runOnIdle { assertEquals("New cash", saved) }
    }

}
