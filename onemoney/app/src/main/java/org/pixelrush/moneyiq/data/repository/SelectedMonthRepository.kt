package org.syalosovetskyi.onemoney.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.time.Instant
import java.time.LocalDate
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
    val toMillis:   Long       = 0L,
    /** Налаштування «Перший день місяця» (1…31), з яким рахується MONTH. */
    val firstDayOfMonth: Int   = 1,
    /** Налаштування «Перший день тижня» — константа Calendar (1 = неділя, 2 = понеділок…). */
    val firstDayOfWeek:  Int   = Calendar.MONDAY
)

@Singleton
class SelectedMonthRepository @Inject constructor() {

    // Налаштування періоду з SettingsRepository; подаються ззовні через [applyPeriodSettings]
    // (див. onemoneyApp), щоб конструктор лишався без залежностей для тестів.
    @Volatile private var firstDayOfMonth: Int = 1
    @Volatile private var firstDayOfWeek:  Int = Calendar.MONDAY

    private val _month = MutableStateFlow(currentMonth())
    val month: StateFlow<AppMonth> = _month.asStateFlow()

    /**
     * Застосувати «Перший день тижня / місяця». Налаштування вшиваються в [AppMonth],
     * тому StateFlow емітить нове значення і всі ViewModel перераховують діапазони.
     * Якщо був обраний поточний місяць — лишаємося на періоді, що містить сьогодні.
     */
    fun applyPeriodSettings(firstDayOfWeek: Int, firstDayOfMonth: Int) {
        val week = firstDayOfWeek.takeIf { it in Calendar.SUNDAY..Calendar.SATURDAY } ?: Calendar.MONDAY
        val day  = firstDayOfMonth.coerceIn(1, 31)
        this.firstDayOfWeek  = week
        this.firstDayOfMonth = day
        val cur = _month.value
        val wasCurrent = cur.mode == PeriodMode.MONTH && cur == currentMonth(cur.firstDayOfMonth, cur.firstDayOfWeek)
        _month.value = if (wasCurrent) currentMonth() else cur.copy(firstDayOfMonth = day, firstDayOfWeek = week)
    }

    private fun stamp(a: AppMonth) = a.copy(firstDayOfMonth = firstDayOfMonth, firstDayOfWeek = firstDayOfWeek)

    // ── Навігація по місяцях ─────────────────────────────────────────────────

    fun prevMonth() {
        val cur = _month.value
        _month.value = stamp(when (cur.mode) {
            PeriodMode.YEAR  -> AppMonth(cur.year - 1, cur.month, PeriodMode.YEAR)
            else             -> if (cur.month == 0) AppMonth(cur.year - 1, 11) else AppMonth(cur.year, cur.month - 1)
        })
    }

    fun nextMonth() {
        val cur = _month.value
        _month.value = stamp(when (cur.mode) {
            PeriodMode.YEAR  -> AppMonth(cur.year + 1, cur.month, PeriodMode.YEAR)
            else             -> if (cur.month == 11) AppMonth(cur.year + 1, 0) else AppMonth(cur.year, cur.month + 1)
        })
    }

    fun goToMonth(year: Int, month: Int) {
        _month.value = stamp(AppMonth(year, month, PeriodMode.MONTH))
    }

    /** Встановити довільний режим. Налаштування періоду підставляються з репозиторію. */
    fun setPeriod(appMonth: AppMonth) {
        _month.value = stamp(appMonth)
    }

    // ── Допоміжні обчислення ─────────────────────────────────────────────────

    fun computeRange(a: AppMonth): Pair<Long, Long> = when (a.mode) {
        PeriodMode.MONTH -> PeriodMath.monthPeriodRange(a.year, a.month, a.firstDayOfMonth, ZoneId.systemDefault())
        PeriodMode.TODAY -> {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val from = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
            from to cal.timeInMillis
        }
        PeriodMode.WEEK -> PeriodMath.weekRange(LocalDate.now(), a.firstDayOfWeek, ZoneId.systemDefault())
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
        PeriodMode.MONTH -> PeriodMath.monthPeriodDays(a.year, a.month, a.firstDayOfMonth)
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

    /** «Місяць», що містить сьогоднішній день, з урахуванням першого дня місяця. */
    private fun currentMonth(
        dayOfMonth: Int = firstDayOfMonth,
        dayOfWeek:  Int = firstDayOfWeek
    ): AppMonth {
        val ym = PeriodMath.monthPeriodContaining(LocalDate.now(), dayOfMonth)
        return AppMonth(ym.year, ym.monthValue - 1, firstDayOfMonth = dayOfMonth, firstDayOfWeek = dayOfWeek)
    }
}
