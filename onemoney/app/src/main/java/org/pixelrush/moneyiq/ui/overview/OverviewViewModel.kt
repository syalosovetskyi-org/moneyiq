package org.syalosovetskyi.onemoney.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.syalosovetskyi.onemoney.data.db.dao.CategorySpending
import org.syalosovetskyi.onemoney.data.db.dao.TransactionWithDetails
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.data.repository.AccountRepository
import org.syalosovetskyi.onemoney.data.repository.AppMonth
import org.syalosovetskyi.onemoney.data.repository.CategoryRepository
import org.syalosovetskyi.onemoney.data.repository.SelectedMonthRepository
import org.syalosovetskyi.onemoney.data.repository.TransactionRepository
import java.util.Calendar
import javax.inject.Inject

// ── Data classes ──────────────────────────────────────────────────────────────

enum class OverviewMode { EXPENSE, INCOME }

data class OverviewSelMonth(val year: Int, val month: Int)

data class CategorySegment(val colorHex: String, val amount: Double)

data class DayBar(
    val day:      Int,
    val amount:   Double,
    val isFuture: Boolean,
    val segments: List<CategorySegment> = emptyList()
)

data class OverviewCatRow(
    val categoryId:   Long,
    val name:         String,
    val colorHex:     String,
    val icon:         String,
    val amount:       Double,
    val percent:      Float,
    val budgetAmount: Double
)

private data class OvrRaw(
    val txList:   List<TransactionWithDetails>,
    val expSpend: List<CategorySpending>,
    val incSpend: List<CategorySpending>,
    val expCats:  List<CategoryEntity>,
    val incCats:  List<CategoryEntity>
)

data class OverviewUiState(
    val selectedMonth: OverviewSelMonth = run {
        val c = Calendar.getInstance()
        OverviewSelMonth(c.get(Calendar.YEAR), c.get(Calendar.MONTH))
    },
    val appMonth:           AppMonth             = AppMonth(Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH)),
    val daysInMonth:        Int                  = 31,
    val mode:               OverviewMode         = OverviewMode.EXPENSE,
    val totalBalance:       Double               = 0.0,
    val monthExpense:       Double               = 0.0,
    val monthIncome:        Double               = 0.0,
    val txCount:            Int                  = 0,
    val dailyAvg:           Double               = 0.0,
    val todayAmount:        Double               = 0.0,
    val weekAmount:         Double               = 0.0,
    val dayBars:            List<DayBar>         = emptyList(),
    val categories:         List<OverviewCatRow>          = emptyList(),
    val transactions:       List<TransactionWithDetails>  = emptyList(),
    val totalExpenseBudget: Double                        = 0.0,
    val totalIncomeBudget:  Double                        = 0.0
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val txRepo:       TransactionRepository,
    private val accountRepo:  AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val monthRepo:    SelectedMonthRepository
) : ViewModel() {

    private val _mode = MutableStateFlow(OverviewMode.EXPENSE)

    val state: StateFlow<OverviewUiState> =
        combine(monthRepo.month, _mode) { am, mode -> am to mode }
        .flatMapLatest { (am, mode) ->
            val sel        = OverviewSelMonth(am.year, am.month)
            val (from, to) = monthRepo.computeRange(am)

            val innerFlow = combine(
                txRepo.getBaseTransactionsByPeriod(from, to),
                txRepo.getCategorySpending(TransactionType.EXPENSE, from, to),
                txRepo.getCategorySpending(TransactionType.INCOME,  from, to),
                categoryRepo.getByType(TransactionType.EXPENSE),
                categoryRepo.getByType(TransactionType.INCOME)
            ) { txList, expSpend, incSpend, expCats, incCats ->
                OvrRaw(txList, expSpend, incSpend, expCats, incCats)
            }

            combine(innerFlow, accountRepo.getTotalBalance()) { data, bal ->
                buildState(am, sel, mode, data, bal ?: 0.0)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OverviewUiState())

    fun prevMonth()                      = monthRepo.prevMonth()
    fun nextMonth()                      = monthRepo.nextMonth()
    fun goToMonth(year: Int, month: Int) = monthRepo.goToMonth(year, month)
    fun setPeriod(appMonth: AppMonth)    = monthRepo.setPeriod(appMonth)
    fun setMode(m: OverviewMode)         { _mode.value = m }

    private fun buildState(
        am:      AppMonth,
        sel:     OverviewSelMonth,
        mode:    OverviewMode,
        data:    OvrRaw,
        balance: Double
    ): OverviewUiState {
        val catSpend = if (mode == OverviewMode.EXPENSE) data.expSpend else data.incSpend
        val cats     = if (mode == OverviewMode.EXPENSE) data.expCats  else data.incCats
        val txList   = data.txList
        val montCal  = Calendar.getInstance().also { it.set(sel.year, sel.month, 1) }
        val dim = montCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val txType = if (mode == OverviewMode.EXPENSE) TransactionType.EXPENSE else TransactionType.INCOME
        val monoTx = txList.filter { it.type == txType }

        val monthExpense = txList.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val monthIncome  = txList.filter { it.type == TransactionType.INCOME  }.sumOf { it.amount }
        val monthTotal   = monoTx.sumOf { it.amount }

        val weekRanges = listOf(1 to 10, 11 to 17, 18 to 24, 25 to dim)

        val today = Calendar.getInstance()
        val isCurrentMonth = sel.year == today.get(Calendar.YEAR) &&
                             sel.month == today.get(Calendar.MONTH)
        val todayDay = if (isCurrentMonth) today.get(Calendar.DAY_OF_MONTH) else dim + 1

        fun rangeMs(startDay: Int, endDay: Int): LongRange {
            val c = Calendar.getInstance()
            c.set(sel.year, sel.month, startDay, 0, 0, 0)
            c.set(Calendar.MILLISECOND, 0)
            val fr = c.timeInMillis
            c.set(Calendar.DAY_OF_MONTH, endDay.coerceAtMost(dim))
            c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59)
            c.set(Calendar.SECOND, 59);      c.set(Calendar.MILLISECOND, 999)
            return fr..c.timeInMillis
        }

        val isFutureMonth = sel.year > today.get(Calendar.YEAR) ||
            (sel.year == today.get(Calendar.YEAR) && sel.month > today.get(Calendar.MONTH))

        val dayBars = (1..dim).map { day ->
            val rng   = rangeMs(day, day)
            val dayTx = monoTx.filter { it.date in rng }
            val dayTotal = dayTx.sumOf { it.amount }
            val segMap = mutableMapOf<String, Double>()
            dayTx.forEach { tx ->
                val color = tx.categoryColor ?: "#9E9E9E"
                segMap[color] = (segMap[color] ?: 0.0) + tx.amount
            }
            val segments = segMap.entries
                .sortedByDescending { it.value }
                .map { CategorySegment(it.key, it.value) }
            DayBar(
                day      = day,
                amount   = dayTotal,
                isFuture = isFutureMonth || day > todayDay,
                segments = segments
            )
        }

        val todayAmount = if (isCurrentMonth) {
            val rng = rangeMs(todayDay, todayDay)
            monoTx.filter { it.date in rng }.sumOf { it.amount }
        } else 0.0

        val currentWeekRange = weekRanges.find { (s, e) -> todayDay in s..e }
        val weekAmount = currentWeekRange?.let { (s, e) ->
            val rng = rangeMs(s, e.coerceAtMost(dim))
            monoTx.filter { it.date in rng }.sumOf { it.amount }
        } ?: 0.0

        val daysPassed = if (isCurrentMonth) todayDay.coerceAtLeast(1) else dim
        val dailyAvg   = if (daysPassed > 0) monthTotal / daysPassed else 0.0

        val activeSpend = catSpend.filter { it.total > 0 }
        val maxAmt = activeSpend.maxOfOrNull { it.total } ?: 0.0
        val catMap = cats.associate { it.id to it }
        val catRows = activeSpend.map { cs ->
            OverviewCatRow(
                categoryId   = cs.categoryId,
                name         = cs.categoryName,
                colorHex     = cs.categoryColor,
                icon         = cs.categoryIcon,
                amount       = cs.total,
                percent      = if (maxAmt > 0) (cs.total / maxAmt).toFloat() else 0f,
                budgetAmount = catMap[cs.categoryId]?.budgetAmount ?: 0.0
            )
        }

        return OverviewUiState(
            selectedMonth      = sel,
            appMonth           = am,
            daysInMonth        = monthRepo.daysInPeriod(am),
            mode               = mode,
            totalBalance       = balance,
            monthExpense       = monthExpense,
            monthIncome        = monthIncome,
            txCount            = monoTx.size,
            dailyAvg           = dailyAvg,
            todayAmount        = todayAmount,
            weekAmount         = weekAmount,
            dayBars            = dayBars,
            categories         = catRows,
            transactions       = monoTx,
            totalExpenseBudget = data.expCats.sumOf { it.budgetAmount },
            totalIncomeBudget  = data.incCats.sumOf { it.budgetAmount }
        )
    }
}
