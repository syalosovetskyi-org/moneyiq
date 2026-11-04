package org.syalosovetskyi.onemoney.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class PeriodMode { MONTH, TODAY, WEEK, YEAR, ALL, DAY, RANGE }

data class AppMonth(
    val year:       Int,
    val month:      Int,          // 0-based; актуально для MONTH / YEAR
    val mode:       PeriodMode = PeriodMode.MONTH,
    val fromMillis: Long       = 0L,
    val toMillis:   Long       = 0L
)

@Singleton
class SelectedMonthRepository @Inject constructor() {

    private val _month = MutableStateFlow(currentMonth())
    val month: StateFlow<AppMonth> = _month.asStateFlow()

    // ── Навігація по місяцях ─────────────────────────────────────────────────

    fun prevMonth() {
        val cur = _month.value
        _month.value = when (cur.mode) {
            PeriodMode.MONTH -> if (cur.month == 0) AppMonth(cur.year - 1, 11) else AppMonth(cur.year, cur.month - 1)
            PeriodMode.YEAR  -> AppMonth(cur.year - 1, cur.month, PeriodMode.YEAR)
            else             -> if (cur.month == 0) AppMonth(cur.year - 1, 11) else AppMonth(cur.year, cur.month - 1)
        }
    }

    fun nextMonth() {
        val cur = _month.value
        _month.value = when (cur.mode) {
            PeriodMode.MONTH -> if (cur.month == 11) AppMonth(cur.year + 1, 0) else AppMonth(cur.year, cur.month + 1)
            PeriodMode.YEAR  -> AppMonth(cur.year + 1, cur.month, PeriodMode.YEAR)
            else             -> if (cur.month == 11) AppMonth(cur.year + 1, 0) else AppMonth(cur.year, cur.month + 1)
        }
    }

    fun goToMonth(year: Int, month: Int) {
        _month.value = AppMonth(year, month, PeriodMode.MONTH)
    }

    /** Встановити довільний режим. */
    fun setPeriod(appMonth: AppMonth) {
        _month.value = appMonth
    }

    // ── Допоміжні обчислення ─────────────────────────────────────────────────

    fun computeRange(a: AppMonth): Pair<Long, Long> = when (a.mode) {
        PeriodMode.MONTH -> {
            val cal = Calendar.getInstance()
            cal.set(a.year, a.month, 1, 0, 0, 0); cal.set(Calendar.MILLISECOND, 0)
            val from = cal.timeInMillis
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
            from to cal.timeInMillis
        }
        PeriodMode.TODAY -> {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val from = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
            from to cal.timeInMillis
        }
        PeriodMode.WEEK -> {
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val from = cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 6)
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
            from to cal.timeInMillis
        }
        PeriodMode.YEAR -> {
            val cal = Calendar.getInstance()
            cal.set(a.year, 0, 1, 0, 0, 0); cal.set(Calendar.MILLISECOND, 0)
            val from = cal.timeInMillis
            cal.set(a.year, 11, 31, 23, 59, 59); cal.set(Calendar.MILLISECOND, 999)
            from to cal.timeInMillis
        }
        PeriodMode.ALL   -> 0L to Long.MAX_VALUE / 2
        PeriodMode.DAY, PeriodMode.RANGE -> a.fromMillis to a.toMillis
    }

    fun daysInPeriod(a: AppMonth): Int = when (a.mode) {
        PeriodMode.MONTH -> Calendar.getInstance()
            .also { it.set(a.year, a.month, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
        PeriodMode.TODAY  -> 1
        PeriodMode.WEEK   -> 7
        PeriodMode.YEAR   -> if (a.year % 4 == 0 && (a.year % 100 != 0 || a.year % 400 == 0)) 366 else 365
        PeriodMode.ALL    -> 0
        PeriodMode.DAY    -> 1
        PeriodMode.RANGE  -> {
            val zone = ZoneId.systemDefault()
            val from = Instant.ofEpochMilli(a.fromMillis).atZone(zone).toLocalDate()
            val to = Instant.ofEpochMilli(a.toMillis).atZone(zone).toLocalDate()
            (ChronoUnit.DAYS.between(from, to) + 1).coerceAtLeast(1).toInt()
        }
    }

    fun pillBadge(a: AppMonth): String = when (a.mode) {
        PeriodMode.MONTH -> "${daysInPeriod(a)}"
        PeriodMode.TODAY -> "1"
        PeriodMode.WEEK  -> "7"
        PeriodMode.YEAR  -> "${daysInPeriod(a)}"
        PeriodMode.ALL   -> "∞"
        PeriodMode.DAY   -> "1"
        PeriodMode.RANGE -> "${daysInPeriod(a)}"
    }

    fun daysInMonth(year: Int, month: Int): Int =
        Calendar.getInstance().also { it.set(year, month, 1) }
            .getActualMaximum(Calendar.DAY_OF_MONTH)

    private fun currentMonth(): AppMonth = Calendar.getInstance().run {
        AppMonth(get(Calendar.YEAR), get(Calendar.MONTH))
    }
}
