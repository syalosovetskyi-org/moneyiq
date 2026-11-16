package org.syalosovetskyi.onemoney.ui.shots

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.data.repository.AppSettings
import org.syalosovetskyi.onemoney.ui.accounts.AccountsScreen
import org.syalosovetskyi.onemoney.ui.accounts.AccountsUiState
import org.syalosovetskyi.onemoney.ui.accounts.AccountsViewModel
import org.syalosovetskyi.onemoney.ui.accounts.IconColorPickerScreen
import org.syalosovetskyi.onemoney.ui.budget.BudgetScreen
import org.syalosovetskyi.onemoney.ui.budget.BudgetViewModel
import org.syalosovetskyi.onemoney.ui.categories.CategoriesScreen
import org.syalosovetskyi.onemoney.ui.categories.CategoriesViewModel
import org.syalosovetskyi.onemoney.ui.categories.EditCategoriesScreen
import org.syalosovetskyi.onemoney.ui.components.currency.CurrencyPageContent
import org.syalosovetskyi.onemoney.ui.data.DataScreen
import org.syalosovetskyi.onemoney.ui.data.DataUiState
import org.syalosovetskyi.onemoney.ui.main.CurrencyStatusViewModel
import org.syalosovetskyi.onemoney.ui.main.MainScreen
import org.syalosovetskyi.onemoney.ui.main.MainUiState
import org.syalosovetskyi.onemoney.ui.main.MainViewModel
import org.syalosovetskyi.onemoney.ui.overview.OverviewScreen
import org.syalosovetskyi.onemoney.ui.overview.OverviewViewModel
import org.syalosovetskyi.onemoney.ui.reports.ReportsScreen
import org.syalosovetskyi.onemoney.ui.settings.AboutPageContent
import org.syalosovetskyi.onemoney.ui.settings.SettingsScreen
import org.syalosovetskyi.onemoney.ui.settings.SettingsViewModel
import org.syalosovetskyi.onemoney.ui.settings.ThemePageContent
import org.syalosovetskyi.onemoney.ui.transactions.AddTransactionScreen
import org.syalosovetskyi.onemoney.ui.transactions.TransactionsListScreen
import org.syalosovetskyi.onemoney.ui.transactions.TransactionsListViewModel
import org.syalosovetskyi.onemoney.ui.transactions.TxSearchScreen
import org.syalosovetskyi.onemoney.ui.shots.ShotData as D

/**
 * Повноекранні стани (вкладки, MainScreen, операція, пошук, налаштування, дані) на повній матриці
 * пристроїв + порожні стани на S23 × 1.0 (serg/tasks#3037).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class, qualifiers = "uk")
class AllScreensShotsTest(case: ShotCase) : ShotTestBase(case) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> =
            ShotCase.expand(dataSpecs(), ShotMatrix.screens) + ShotCase.expand(emptySpecs(), ShotMatrix.empty)

        private fun dataSpecs(): List<ShotSpec> = listOf(
            // ── Вкладки (embeddedMode, як усередині MainScreen) ──
            ShotSpec("overview", "data", inScaffold = true) {
                val vm = remember { ShotVm.overview(D.overviewState) }
                OverviewScreen(embeddedMode = true, viewModel = vm)
            },
            ShotSpec("transactions", "data", inScaffold = true) {
                val vm = remember { ShotVm.txList(D.txListState) }
                TransactionsListScreen(embeddedMode = true, viewModel = vm)
            },
            ShotSpec("categories", "expense", inScaffold = true) {
                val vm = remember { ShotVm.categories(D.categoriesState) }
                CategoriesScreen(embeddedMode = true, viewModel = vm, selectedTypeTab = 0)
            },
            ShotSpec("categories", "income", inScaffold = true) {
                val vm = remember { ShotVm.categories(D.categoriesState) }
                CategoriesScreen(embeddedMode = true, viewModel = vm, selectedTypeTab = 1)
            },
            ShotSpec("accounts", "data", inScaffold = true) {
                val vm = remember { ShotVm.accounts(D.accountsState) }
                AccountsScreen(embeddedMode = true, viewModel = vm)
            },
            ShotSpec("accounts", "finances", inScaffold = true, prepare = { it.onNodeWithText(str(R.string.acc_tab_finances)).performClick() }) {
                val vm = remember { ShotVm.accounts(D.accountsState) }
                AccountsScreen(embeddedMode = true, viewModel = vm)
            },
            ShotSpec("budget", "data", inScaffold = true) {
                val vm = remember { ShotVm.budget(D.budgetState) }
                BudgetScreen(embeddedMode = true, viewModel = vm)
            },
            ShotSpec("reports", "data", inScaffold = true) {
                val vm = remember { ShotVm.reports(D.reportsState) }
                ReportsScreen(viewModel = vm)
            },
            ShotSpec("main", "data") { MainShot(data = true) },
            // ── Операція ──
            ShotSpec("add-tx", "expense") {
                val vm = remember { ShotVm.addTx(D.addTxState(TransactionType.EXPENSE)) }
                AddTransactionScreen(onNavigateBack = {}, viewModel = vm)
            },
            ShotSpec("add-tx", "income") {
                val vm = remember { ShotVm.addTx(D.addTxState(TransactionType.INCOME)) }
                AddTransactionScreen(onNavigateBack = {}, viewModel = vm)
            },
            ShotSpec("add-tx", "transfer") {
                val vm = remember { ShotVm.addTx(D.addTxState(TransactionType.TRANSFER)) }
                AddTransactionScreen(onNavigateBack = {}, viewModel = vm)
            },
            ShotSpec("tx-search", "filters") {
                TxSearchScreen(query = "комунальні", filterTypes = setOf("EXPENSE", "TRANSFER"),
                    filterAccountIds = setOf(D.accCash.id, D.accMono.id),
                    filterCategoryIds = setOf(D.cat(D.LONG_CATEGORY).id), accounts = D.accounts,
                    expenseCategories = D.expenseCategories, incomeCategories = D.incomeCategories,
                    onQueryChange = {}, onTypesChange = {}, onAccountsChange = {}, onCategoriesChange = {},
                    onReset = {}, onDone = {})
            },
            // ── Категорії / рахунки ──
            ShotSpec("edit-categories", "data") {
                EditCategoriesScreen(expenseCategories = D.expenseCategories, incomeCategories = D.incomeCategories,
                    monthSpending = D.spending, monthIncome = D.income, totalExpense = D.totalExpense,
                    totalIncome = D.totalIncome, onSave = { _, _, _, _, _, _, _, _, _ -> },
                    onAddSubcategory = { _, _, _, _, _, _, _, _ -> }, onDelete = {}, onReorder = {}, onDismiss = {})
            },
            ShotSpec("icon-color-picker", "data") {
                IconColorPickerScreen(initialIconKey = D.accDeposit.icon, initialColorHex = D.accDeposit.colorHex,
                    onResult = { _, _ -> }, onDismiss = {})
            },
            // ── Налаштування ──
            ShotSpec("settings", "main") {
                val vm = remember { ShotVm.settings(D.settings) }
                SettingsScreen(onNavigateBack = {}, viewModel = vm)
            },
            ShotSpec("settings", "theme") {
                val vm = remember { ShotVm.settings(D.settings) }
                ThemePageContent(settings = D.settings, vm = vm, onBack = {})
            },
            ShotSpec("settings", "currency") {
                CurrencyPageContent(selected = "UAH", onSelect = {}, onClose = {})
            },
            ShotSpec("settings", "about") { AboutPageContent(onBack = {}) },
            // ── Дані ──
            ShotSpec("data", "data") {
                val vm = remember { ShotVm.data(D.dataState) }
                DataScreen(onNavigateBack = {}, viewModel = vm)
            },
        )

        private fun emptySpecs(): List<ShotSpec> = listOf(
            ShotSpec("overview", "empty", inScaffold = true) {
                val vm = remember { ShotVm.overview(D.overviewEmpty) }
                OverviewScreen(embeddedMode = true, viewModel = vm)
            },
            ShotSpec("transactions", "empty", inScaffold = true) {
                val vm = remember { ShotVm.txList(D.txListEmpty) }
                TransactionsListScreen(embeddedMode = true, viewModel = vm)
            },
            ShotSpec("categories", "empty", inScaffold = true) {
                val vm = remember { ShotVm.categories(D.categoriesEmpty) }
                CategoriesScreen(embeddedMode = true, viewModel = vm, selectedTypeTab = 0)
            },
            ShotSpec("accounts", "empty", inScaffold = true) {
                val vm = remember { ShotVm.accounts(D.accountsEmpty) }
                AccountsScreen(embeddedMode = true, viewModel = vm)
            },
            ShotSpec("budget", "empty", inScaffold = true) {
                val vm = remember { ShotVm.budget(D.budgetEmpty) }
                BudgetScreen(embeddedMode = true, viewModel = vm)
            },
            ShotSpec("reports", "empty", inScaffold = true) {
                val vm = remember { ShotVm.reports(D.reportsEmpty) }
                ReportsScreen(viewModel = vm)
            },
            ShotSpec("main", "empty") { MainShot(data = false) },
            ShotSpec("add-tx", "empty") {
                val vm = remember { ShotVm.addTx(D.addTxEmpty) }
                AddTransactionScreen(onNavigateBack = {}, viewModel = vm)
            },
            ShotSpec("tx-search", "empty") {
                TxSearchScreen(query = "", filterTypes = emptySet(), filterAccountIds = emptySet(),
                    filterCategoryIds = emptySet(), accounts = emptyList(), expenseCategories = emptyList(),
                    incomeCategories = emptyList(), onQueryChange = {}, onTypesChange = {}, onAccountsChange = {},
                    onCategoriesChange = {}, onReset = {}, onDone = {})
            },
            ShotSpec("edit-categories", "empty") {
                EditCategoriesScreen(expenseCategories = emptyList(), incomeCategories = emptyList(),
                    monthSpending = emptyMap(), monthIncome = emptyMap(), totalExpense = 0.0, totalIncome = 0.0,
                    onSave = { _, _, _, _, _, _, _, _, _ -> }, onAddSubcategory = { _, _, _, _, _, _, _, _ -> },
                    onDelete = {}, onReorder = {}, onDismiss = {})
            },
            ShotSpec("settings", "empty") {
                val vm = remember { ShotVm.settings(AppSettings()) }
                SettingsScreen(onNavigateBack = {}, viewModel = vm)
            },
            ShotSpec("data", "empty") {
                val vm = remember { ShotVm.data(DataUiState()) }
                DataScreen(onNavigateBack = {}, viewModel = vm)
            },
        )
    }
}

/**
 * MainScreen цілком (нижня навігація, верхня панель, вкладка «Категорії» за замовчуванням).
 * Вкладки всередині беруть VM через hiltViewModel() — їх віддає [MockViewModelOwner].
 */
@androidx.compose.runtime.Composable
private fun MainShot(data: Boolean) {
    val models = remember {
        listOf<Pair<Class<out androidx.lifecycle.ViewModel>, androidx.lifecycle.ViewModel>>(
            MainViewModel::class.java to ShotVm.main(if (data) D.mainState else MainUiState(isLoading = false)),
            AccountsViewModel::class.java to ShotVm.accounts(if (data) D.accountsState else AccountsUiState()),
            CategoriesViewModel::class.java to ShotVm.categories(if (data) D.categoriesState else D.categoriesEmpty),
            SettingsViewModel::class.java to ShotVm.settings(if (data) D.settings else AppSettings()),
            CurrencyStatusViewModel::class.java to ShotVm.currencyStatus(),
            TransactionsListViewModel::class.java to ShotVm.txList(if (data) D.txListState else D.txListEmpty),
            BudgetViewModel::class.java to ShotVm.budget(if (data) D.budgetState else D.budgetEmpty),
            OverviewViewModel::class.java to ShotVm.overview(if (data) D.overviewState else D.overviewEmpty),
        )
    }
    val owner = remember { MockViewModelOwner(*models.toTypedArray()) }
    val vm = models.toMap()
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        MainScreen(
            onAddTransaction    = {},
            mainViewModel       = vm.getValue(MainViewModel::class.java) as MainViewModel,
            accountsViewModel   = vm.getValue(AccountsViewModel::class.java) as AccountsViewModel,
            categoriesViewModel = vm.getValue(CategoriesViewModel::class.java) as CategoriesViewModel,
            settingsViewModel   = vm.getValue(SettingsViewModel::class.java) as SettingsViewModel,
            currencyStatus      = vm.getValue(CurrencyStatusViewModel::class.java) as CurrencyStatusViewModel,
        )
    }
}

