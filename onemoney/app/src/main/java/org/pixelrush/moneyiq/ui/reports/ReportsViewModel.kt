package org.syalosovetskyi.onemoney.ui.reports

import androidx.lifecycle.ViewModel
import org.syalosovetskyi.onemoney.util.DateFormats
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.syalosovetskyi.onemoney.data.db.dao.CategorySpending
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.data.repository.TransactionRepository
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class ReportsUiState(
    val periodLabel: String = "",
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val incomeByCategory: List<CategorySpending> = emptyList(),
    val expenseByCategory: List<CategorySpending> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val txRepo: TransactionRepository
) : ViewModel() {

    private val _calendar = MutableStateFlow(Calendar.getInstance())
    val state: StateFlow<ReportsUiState> = _calendar.flatMapLatest { cal ->
        val (from, to) = monthRange(cal)
        val label = DateFormats.monthYear(cal.time).replaceFirstChar { it.uppercaseChar() }

        combine(
            txRepo.getIncomeSum(from, to),
            txRepo.getExpenseSum(from, to),
            txRepo.getCategorySpending(TransactionType.INCOME, from, to),
            txRepo.getCategorySpending(TransactionType.EXPENSE, from, to)
        ) { income, expense, incCat, expCat ->
            ReportsUiState(
                periodLabel = label,
                income = income,
                expense = expense,
                incomeByCategory = incCat.filter { it.total > 0 },
                expenseByCategory = expCat.filter { it.total > 0 }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportsUiState())

    fun prevMonth() {
        _calendar.update { cal ->
            val c = cal.clone() as Calendar
            c.add(Calendar.MONTH, -1)
            c
        }
    }

    fun nextMonth() {
        _calendar.update { cal ->
            val c = cal.clone() as Calendar
            c.add(Calendar.MONTH, 1)
            c
        }
    }

    internal fun monthRange(cal: Calendar): Pair<Long, Long> {
        val c = cal.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        val start = c.timeInMillis
        c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
        c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59); c.set(Calendar.SECOND, 59)
        c.set(Calendar.MILLISECOND, 999)
        return start to c.timeInMillis
    }
}
