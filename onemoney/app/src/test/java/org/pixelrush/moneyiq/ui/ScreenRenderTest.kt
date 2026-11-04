package org.syalosovetskyi.onemoney.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.syalosovetskyi.onemoney.data.db.entities.*
import org.syalosovetskyi.onemoney.data.repository.AppSettings
import org.syalosovetskyi.onemoney.ui.accounts.*
import org.syalosovetskyi.onemoney.ui.budget.*
import org.syalosovetskyi.onemoney.ui.categories.*
import org.syalosovetskyi.onemoney.ui.data.*
import org.syalosovetskyi.onemoney.ui.overview.*
import org.syalosovetskyi.onemoney.ui.reports.*
import org.syalosovetskyi.onemoney.ui.settings.*
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyThemeProvider
import org.syalosovetskyi.onemoney.ui.transactions.*

/** Render regressions use explicit ViewModels so the test Activity needs no Hilt application. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class, qualifiers = "uk")
class ScreenRenderTest {
    @get:Rule val compose = createComposeRule()
    private fun render(content: @Composable () -> Unit) {
        compose.setContent { MaterialTheme { OneMoneyThemeProvider { content() } } }
        compose.onRoot().assertExists()
    }

    @Test fun accounts_empty_then_data() {
        val flow = MutableStateFlow(AccountsUiState())
        val vm = mockk<AccountsViewModel>(relaxed = true) { every { state } returns flow }
        render { AccountsScreen(viewModel = vm) }
        compose.runOnIdle { flow.value = AccountsUiState(accounts = listOf(AccountEntity(id = 1, name = "Test cash"))) }
        compose.onNodeWithText("Test cash").assertExists()
    }

    @Test fun categories_empty_then_data() {
        val flow = MutableStateFlow(CategoriesUiState())
        val vm = mockk<CategoriesViewModel>(relaxed = true) { every { state } returns flow }
        render { CategoriesScreen(viewModel = vm) }
        compose.runOnIdle { flow.value = CategoriesUiState(expenseCategories = listOf(
            CategoryEntity(id = 1, name = "Test food", type = TransactionType.EXPENSE))) }
        compose.onNodeWithText("Test food").assertExists()
    }

    @Test fun budget_empty_then_data() {
        val flow = MutableStateFlow(BudgetUiState())
        val vm = mockk<BudgetViewModel>(relaxed = true) { every { state } returns flow }
        render { BudgetScreen(viewModel = vm) }
        compose.runOnIdle { flow.value = BudgetUiState(expenseSection = BudgetSectionData(100.0, 20.0,
            listOf(BudgetCatRow(CategoryEntity(id = 1, name = "Test budget", type = TransactionType.EXPENSE,
                budgetAmount = 100.0), 20.0)))) }
        compose.onNodeWithText("Test budget").assertExists()
    }

    @Test fun overview_empty_then_data() {
        val flow = MutableStateFlow(OverviewUiState())
        val vm = mockk<OverviewViewModel>(relaxed = true) { every { state } returns flow }
        render { OverviewScreen(viewModel = vm) }
        compose.runOnIdle { flow.value = OverviewUiState(monthExpense = 20.0,
            categories = listOf(OverviewCatRow(1, "Test overview", "#123456", "grocery", 20.0, 1f, 100.0))) }
        // The category follows the chart/stats and is outside the initial viewport.
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Test overview"))
        compose.onNodeWithText("Test overview").assertIsDisplayed()
    }

    @Test fun reports_empty_then_period_data() {
        val flow = MutableStateFlow(ReportsUiState())
        val vm = mockk<ReportsViewModel>(relaxed = true) { every { state } returns flow }
        render { ReportsScreen(viewModel = vm) }
        compose.runOnIdle { flow.value = ReportsUiState(periodLabel = "Test period", income = 100.0, expense = 20.0) }
        compose.onNodeWithText("Test period").assertExists()
    }

    @Test fun transactions_empty_then_data() {
        val flow = MutableStateFlow(TxListUiState())
        val vm = mockk<TransactionsListViewModel>(relaxed = true) { every { state } returns flow }
        render { TransactionsListScreen(viewModel = vm) }
        compose.runOnIdle { flow.value = TxListUiState(accounts = listOf(AccountEntity(id = 1, name = "Cash"))) }
        compose.onRoot().assertExists()
    }

    @Test fun add_transaction_empty_then_note_data() {
        val flow = MutableStateFlow(AddTransactionUiState())
        val vm = mockk<TransactionViewModel>(relaxed = true) { every { state } returns flow }
        render { AddTransactionScreen(onNavigateBack = {}, viewModel = vm) }
        compose.runOnIdle { flow.value = AddTransactionUiState(note = "Test note") }
        compose.onNodeWithText("Test note").assertExists()
    }

    @Test fun settings_renders_default_and_user_settings() {
        val flow = MutableStateFlow(AppSettings())
        val vm = mockk<SettingsViewModel>(relaxed = true) { every { settings } returns flow }
        render { SettingsScreen(onNavigateBack = {}, viewModel = vm) }
        compose.runOnIdle { flow.value = AppSettings(defaultCurrency = "EUR") }
        compose.onRoot().assertExists()
    }

    @Test fun data_empty_then_backup_data() {
        val flow = MutableStateFlow(DataUiState())
        val vm = mockk<DataViewModel>(relaxed = true) { every { state } returns flow }
        render { DataScreen(onNavigateBack = {}, viewModel = vm) }
        compose.runOnIdle { flow.value = DataUiState(localBackups = listOf(BackupEntry(1000, 5, 2, 3))) }
        compose.onRoot().assertExists()
    }
}
