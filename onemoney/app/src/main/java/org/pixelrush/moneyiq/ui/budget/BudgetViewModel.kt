package org.syalosovetskyi.onemoney.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.data.repository.AccountRepository
import org.syalosovetskyi.onemoney.data.repository.AppMonth
import org.syalosovetskyi.onemoney.data.repository.CategoryRepository
import org.syalosovetskyi.onemoney.data.repository.SelectedMonthRepository
import org.syalosovetskyi.onemoney.data.repository.TransactionRepository
import java.util.Calendar
import javax.inject.Inject

data class BudgetSelMonth(val year: Int, val month: Int)

data class BudgetCatRow(
    val category: CategoryEntity,
    val amount:   Double,
    val budgetForPeriod: Double = category.budgetAmount
)

data class BudgetSectionData(
    val totalBudget: Double,
    val totalAmount: Double,
    val rows:        List<BudgetCatRow>
)

data class BudgetUiState(
    val selectedMonth: BudgetSelMonth = run {
        val cal = Calendar.getInstance()
        BudgetSelMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
    },
    val appMonth:       AppMonth          = AppMonth(Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH)),
    val daysInMonth:    Int               = 31,
    val daysPassed:     Int               = 0,
    val pillBadge:      String            = "31",
    val totalBalance:   Double            = 0.0,
    val expenseSection: BudgetSectionData = BudgetSectionData(0.0, 0.0, emptyList()),
    val incomeSection:  BudgetSectionData = BudgetSectionData(0.0, 0.0, emptyList())
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val categoryRepo: CategoryRepository,
    private val txRepo:       TransactionRepository,
    private val accountRepo:  AccountRepository,
    private val monthRepo:    SelectedMonthRepository
) : ViewModel() {

    val state: StateFlow<BudgetUiState> = monthRepo.month.flatMapLatest { am ->
        val sel        = BudgetSelMonth(am.year, am.month)
        val (from, to) = monthRepo.computeRange(am)
        val totalDays  = monthRepo.daysInPeriod(am)
        val daysPassed = run {
            val now      = Calendar.getInstance()
            val curYear  = now.get(Calendar.YEAR)
            val curMonth = now.get(Calendar.MONTH)
            when {
                am.year < curYear || (am.year == curYear && am.month < curMonth) -> totalDays
                am.year == curYear && am.month == curMonth -> now.get(Calendar.DAY_OF_MONTH)
                else -> 0
            }
        }
        combine(
            categoryRepo.getByType(TransactionType.EXPENSE),
            categoryRepo.getByType(TransactionType.INCOME),
            txRepo.getCategorySpending(TransactionType.EXPENSE, from, to),
            txRepo.getCategorySpending(TransactionType.INCOME, from, to),
            accountRepo.getTotalBalance()
        ) { expCats, incCats, expSpend, incSpend, rawBalance ->
            val expMap  = expSpend.associate { it.categoryId to it.total }
            val incMap  = incSpend.associate { it.categoryId to it.total }
            val expRows = expCats.map { BudgetCatRow(it, expMap[it.id] ?: 0.0, periodBudget(it, totalDays)) }
            val incRows = incCats.map { BudgetCatRow(it, incMap[it.id] ?: 0.0, periodBudget(it, totalDays)) }
            BudgetUiState(
                selectedMonth  = sel,
                appMonth       = am,
                daysInMonth    = totalDays,
                daysPassed     = daysPassed,
                pillBadge      = monthRepo.pillBadge(am),
                totalBalance   = rawBalance ?: 0.0,
                expenseSection = BudgetSectionData(expRows.sumOf { it.budgetForPeriod }, expRows.sumOf { it.amount }, expRows),
                incomeSection  = BudgetSectionData(incRows.sumOf { it.budgetForPeriod }, incRows.sumOf { it.amount }, incRows)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetUiState())

    fun prevMonth()                      = monthRepo.prevMonth()
    fun nextMonth()                      = monthRepo.nextMonth()
    fun goToMonth(year: Int, month: Int) = monthRepo.goToMonth(year, month)
    fun setPeriod(appMonth: AppMonth)    = monthRepo.setPeriod(appMonth)

    fun updateCategoryBudget(category: CategoryEntity, newBudget: Double, currency: String = category.currencyCode) {
        viewModelScope.launch { categoryRepo.update(category.copy(budgetAmount = newBudget, currencyCode = currency)) }
    }

    fun clearAllBudgets() {
        viewModelScope.launch {
            val allCats = categoryRepo.getByType(TransactionType.EXPENSE).first() +
                          categoryRepo.getByType(TransactionType.INCOME).first()
            allCats.forEach { categoryRepo.update(it.copy(budgetAmount = 0.0)) }
        }
    }
}

/** Weekly limits accrue per calendar day; a seven-day selection gets exactly one weekly limit. */
internal fun periodBudget(category: CategoryEntity, days: Int): Double =
    if (category.budgetPeriod == "WEEKLY") category.budgetAmount * days / 7.0 else category.budgetAmount
