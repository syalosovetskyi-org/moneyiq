package org.syalosovetskyi.onemoney.ui.shots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.syalosovetskyi.onemoney.data.repository.AppSettings
import org.syalosovetskyi.onemoney.ui.accounts.AccountsUiState
import org.syalosovetskyi.onemoney.ui.accounts.AccountsViewModel
import org.syalosovetskyi.onemoney.ui.budget.BudgetUiState
import org.syalosovetskyi.onemoney.ui.budget.BudgetViewModel
import org.syalosovetskyi.onemoney.ui.categories.CategoriesUiState
import org.syalosovetskyi.onemoney.ui.categories.CategoriesViewModel
import org.syalosovetskyi.onemoney.ui.data.DataUiState
import org.syalosovetskyi.onemoney.ui.data.DataViewModel
import org.syalosovetskyi.onemoney.ui.main.CurrencyStatusViewModel
import org.syalosovetskyi.onemoney.ui.main.MainUiState
import org.syalosovetskyi.onemoney.ui.main.MainViewModel
import org.syalosovetskyi.onemoney.ui.overview.OverviewUiState
import org.syalosovetskyi.onemoney.ui.overview.OverviewViewModel
import org.syalosovetskyi.onemoney.ui.reports.ReportsUiState
import org.syalosovetskyi.onemoney.ui.reports.ReportsViewModel
import org.syalosovetskyi.onemoney.ui.settings.SettingsViewModel
import org.syalosovetskyi.onemoney.ui.transactions.AddTransactionUiState
import org.syalosovetskyi.onemoney.ui.transactions.TransactionViewModel
import org.syalosovetskyi.onemoney.ui.transactions.TransactionsListViewModel
import org.syalosovetskyi.onemoney.ui.transactions.TxListUiState

/** mockk-ViewModel з фіксованим UiState — як у ScreenRenderTest, без Hilt. */
object ShotVm {
    fun accounts(s: AccountsUiState) = mockk<AccountsViewModel>(relaxed = true) { every { state } returns MutableStateFlow(s) }
    fun categories(s: CategoriesUiState) = mockk<CategoriesViewModel>(relaxed = true) { every { state } returns MutableStateFlow(s) }
    fun txList(s: TxListUiState) = mockk<TransactionsListViewModel>(relaxed = true) { every { state } returns MutableStateFlow(s) }
    fun budget(s: BudgetUiState) = mockk<BudgetViewModel>(relaxed = true) { every { state } returns MutableStateFlow(s) }
    fun overview(s: OverviewUiState) = mockk<OverviewViewModel>(relaxed = true) { every { state } returns MutableStateFlow(s) }
    fun reports(s: ReportsUiState) = mockk<ReportsViewModel>(relaxed = true) { every { state } returns MutableStateFlow(s) }
    fun addTx(s: AddTransactionUiState) = mockk<TransactionViewModel>(relaxed = true) { every { state } returns MutableStateFlow(s) }
    fun data(s: DataUiState) = mockk<DataViewModel>(relaxed = true) { every { state } returns MutableStateFlow(s) }
    fun settings(s: AppSettings) = mockk<SettingsViewModel>(relaxed = true) { every { settings } returns MutableStateFlow(s) }
    fun main(s: MainUiState) = mockk<MainViewModel>(relaxed = true) { every { state } returns MutableStateFlow(s) }
    fun currencyStatus(missing: Set<String> = emptySet()) =
        mockk<CurrencyStatusViewModel>(relaxed = true) { every { this@mockk.missing } returns MutableStateFlow(missing) }
}

/**
 * ViewModelStoreOwner для MainScreen: вкладки всередині MainScreen беруть VM через hiltViewModel().
 * Власник НЕ реалізує HasDefaultViewModelProviderFactory, тож hilt-navigation-compose не створює
 * HiltViewModelFactory (яка вимагає @AndroidEntryPoint-activity), а viewModel() знаходить у сховищі
 * вже покладений mock під ключем за замовчуванням.
 */
class MockViewModelOwner(vararg models: Pair<Class<out ViewModel>, ViewModel>) : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()

    init {
        val byClass = models.toMap()
        val provider = ViewModelProvider(viewModelStore, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                checkNotNull(byClass[modelClass]) { "немає mock для ${modelClass.name}" } as T
        })
        @Suppress("UNCHECKED_CAST")
        val keys = byClass.keys.map { it as Class<ViewModel> }
        for (cls in keys) provider[cls]
    }
}
