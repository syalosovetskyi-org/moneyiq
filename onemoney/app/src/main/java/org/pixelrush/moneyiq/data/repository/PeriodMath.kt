package org.syalosovetskyi.onemoney.data.repository

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Locale

/**
 * Чисті обчислення меж періодів (без Android і без Compose), щоб їх можна було
 * покрити unit-тестами.
 *
 * Дві системи відліку, які не можна змішувати:
 *  - M3 DatePicker / DateRangePicker повертає і приймає **UTC-північ** обраного дня;
 *  - фільтри операцій працюють з **локальними** мілісекундами: від 00:00:00.000
 *    першого дня до 23:59:59.999 останнього в часовому поясі пристрою.
 */
object PeriodMath {

    /** UTC-північ із пікера → календарна дата, яку бачив користувач. */
    fun pickerUtcToDate(utcMidnightMillis: Long): LocalDate =
        Instant.ofEpochMilli(utcMidnightMillis).atZone(ZoneOffset.UTC).toLocalDate()

    /** Календарна дата → UTC-північ для initialSelection пікера. */
    fun dateToPickerUtc(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** Локальна мітка часу (межа збереженого періоду) → UTC-північ того ж календарного дня. */
    fun localMillisToPickerUtc(localMillis: Long, zone: ZoneId): Long =
        dateToPickerUtc(Instant.ofEpochMilli(localMillis).atZone(zone).toLocalDate())

    fun startOfDay(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** 23:59:59.999 дня [date]; через початок наступного дня, щоб DST не з'їдав годину. */
    fun endOfDay(date: LocalDate, zone: ZoneId): Long =
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    /**
     * Діапазон із DateRangePicker (UTC-півночі) → локальні межі `[start 00:00, end 23:59:59.999]`.
     * [endUtc] == null — обрано лише один день. Переставлені межі впорядковуються.
     */
    fun pickerRangeToLocal(startUtc: Long, endUtc: Long?, zone: ZoneId): Pair<Long, Long> {
        val a = pickerUtcToDate(startUtc)
        val b = endUtc?.let(::pickerUtcToDate) ?: a
        val from = minOf(a, b)
        val to = maxOf(a, b)
        return startOfDay(from, zone) to endOfDay(to, zone)
    }

    /** Один день із DatePicker → локальні межі дня. */
    fun pickerDayToLocal(utc: Long, zone: ZoneId): Pair<Long, Long> =
        pickerRangeToLocal(utc, null, zone)

    // ── «Перший день місяця» ────────────────────────────────────────────────

    /**
     * Фактичний день початку періоду в даному місяці: якщо обрано 31, а в місяці
     * 30 (або 28/29) днів, період починається з останнього дня місяця.
     */
    fun effectiveStartDay(ym: YearMonth, firstDayOfMonth: Int): Int =
        firstDayOfMonth.coerceIn(1, 31).coerceAtMost(ym.lengthOfMonth())

    /** Перший день «місяця» [ym] з урахуванням налаштування «Перший день місяця». */
    fun monthPeriodStart(ym: YearMonth, firstDayOfMonth: Int): LocalDate =
        ym.atDay(effectiveStartDay(ym, firstDayOfMonth))

    /**
     * Межі «місяця» [ym] (month0 — 0-based як у [AppMonth]): від N-го числа цього
     * місяця до дня перед N-м числом наступного включно.
     */
    fun monthPeriodRange(year: Int, month0: Int, firstDayOfMonth: Int, zone: ZoneId): Pair<Long, Long> {
        val ym = YearMonth.of(year, month0 + 1)
        val start = monthPeriodStart(ym, firstDayOfMonth)
        val nextStart = monthPeriodStart(ym.plusMonths(1), firstDayOfMonth)
        return startOfDay(start, zone) to startOfDay(nextStart, zone) - 1
    }

    fun monthPeriodDays(year: Int, month0: Int, firstDayOfMonth: Int): Int {
        val ym = YearMonth.of(year, month0 + 1)
        val start = monthPeriodStart(ym, firstDayOfMonth)
        val nextStart = monthPeriodStart(ym.plusMonths(1), firstDayOfMonth)
        return java.time.temporal.ChronoUnit.DAYS.between(start, nextStart).toInt()
    }

    /** До якого «місяця» належить дата [date] при початку місяця з [firstDayOfMonth]-го. */
    fun monthPeriodContaining(date: LocalDate, firstDayOfMonth: Int): YearMonth {
        val ym = YearMonth.from(date)
        return if (date.dayOfMonth >= effectiveStartDay(ym, firstDayOfMonth)) ym else ym.minusMonths(1)
    }

    // ── «Перший день тижня» ─────────────────────────────────────────────────

    /** Налаштування зберігається як константа Calendar (1 = неділя … 7 = субота). */
    fun calendarDayToDayOfWeek(calendarDay: Int): DayOfWeek = when (calendarDay) {
        Calendar.SUNDAY -> DayOfWeek.SUNDAY
        in Calendar.MONDAY..Calendar.SATURDAY -> DayOfWeek.of(calendarDay - 1)
        else -> DayOfWeek.MONDAY
    }

    /** Перший і останній день тижня, що містить [date]. */
    fun weekDates(date: LocalDate, firstDayOfWeekCalendar: Int): Pair<LocalDate, LocalDate> {
        val first = calendarDayToDayOfWeek(firstDayOfWeekCalendar)
        val start = date.with(TemporalAdjusters.previousOrSame(first))
        return start to start.plusDays(6)
    }

    fun weekRange(date: LocalDate, firstDayOfWeekCalendar: Int, zone: ZoneId): Pair<Long, Long> {
        val (start, end) = weekDates(date, firstDayOfWeekCalendar)
        return startOfDay(start, zone) to endOfDay(end, zone)
    }

    /**
     * M3 DatePicker не має параметра «перший день тижня»: він бере його з локалі стану
     * (WeekFields / ICU по **регіону** локалі). Тому підставляємо регіон, де тиждень
     * починається з потрібного дня, зберігаючи мову (назви місяців і днів не змінюються).
     * Для вівторка/середи/четверга такого регіону в CLDR немає — повертаємо [base] як є.
     */
    fun calendarLocaleFor(base: Locale, firstDayOfWeekCalendar: Int): Locale {
        val region = when (firstDayOfWeekCalendar) {
            Calendar.SUNDAY   -> "US"
            Calendar.MONDAY   -> "UA"
            Calendar.FRIDAY   -> "MV"
            Calendar.SATURDAY -> "EG"
            else              -> return base
        }
        val baseFirst = Calendar.getInstance(base).firstDayOfWeek
        if (baseFirst == firstDayOfWeekCalendar) return base
        return Locale(base.language, region)
    }
}
