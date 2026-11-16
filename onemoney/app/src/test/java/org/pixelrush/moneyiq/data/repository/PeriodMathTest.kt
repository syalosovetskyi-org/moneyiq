package org.syalosovetskyi.onemoney.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Locale

class PeriodMathTest {

    private val kyiv = ZoneId.of("Europe/Kyiv")

    private fun utcMidnight(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun local(y: Int, m: Int, d: Int, h: Int, min: Int, zone: ZoneId = kyiv): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    // ── R2: діапазон із DateRangePicker ─────────────────────────────────────

    @Test
    fun `range 01-11 to 15-11 includes operation on 15-11 at 22 30 in Kyiv`() {
        val (from, to) = PeriodMath.pickerRangeToLocal(utcMidnight(2026, 11, 1), utcMidnight(2026, 11, 15), kyiv)
        val op = local(2026, 11, 15, 22, 30)
        assertTrue("operation 15.11 22:30 must be inside", op in from..to)
        assertEquals(local(2026, 11, 1, 0, 0), from)
        assertEquals(LocalDate.of(2026, 11, 16).atStartOfDay(kyiv).toInstant().toEpochMilli() - 1, to)
    }

    @Test
    fun `range excludes operations just outside local day bounds`() {
        val (from, to) = PeriodMath.pickerRangeToLocal(utcMidnight(2026, 11, 1), utcMidnight(2026, 11, 15), kyiv)
        assertTrue(local(2026, 10, 31, 23, 59) < from)
        assertTrue(local(2026, 11, 16, 0, 0) > to)
        // 01.11 00:30 за Києвом — це ще 31.10 за UTC, але день обрано 01.11.
        assertTrue(local(2026, 11, 1, 0, 30) in from..to)
    }

    @Test
    fun `range without end date covers the single start day`() {
        val (from, to) = PeriodMath.pickerRangeToLocal(utcMidnight(2026, 11, 15), null, kyiv)
        assertEquals(local(2026, 11, 15, 0, 0), from)
        assertTrue(local(2026, 11, 15, 23, 59) in from..to)
        assertTrue(local(2026, 11, 16, 0, 0) > to)
    }

    @Test
    fun `range works west of UTC where UTC midnight is the previous local day`() {
        val ny = ZoneId.of("America/New_York")
        val (from, to) = PeriodMath.pickerRangeToLocal(utcMidnight(2026, 11, 1), utcMidnight(2026, 11, 15), ny)
        assertEquals(local(2026, 11, 1, 0, 0, ny), from)
        assertTrue(local(2026, 11, 15, 23, 30, ny) in from..to)
    }

    @Test
    fun `single day picker gives whole local day`() {
        val (from, to) = PeriodMath.pickerDayToLocal(utcMidnight(2026, 11, 15), kyiv)
        assertEquals(local(2026, 11, 15, 0, 0), from)
        assertEquals(local(2026, 11, 16, 0, 0) - 1, to)
    }

    @Test
    fun `stored local range converts back to the same picker dates`() {
        val startUtc = utcMidnight(2026, 11, 1)
        val endUtc = utcMidnight(2026, 11, 15)
        val (from, to) = PeriodMath.pickerRangeToLocal(startUtc, endUtc, kyiv)
        assertEquals(startUtc, PeriodMath.localMillisToPickerUtc(from, kyiv))
        assertEquals(endUtc, PeriodMath.localMillisToPickerUtc(to, kyiv))
    }

    @Test
    fun `round trip holds across DST change`() {
        val startUtc = utcMidnight(2026, 10, 20)
        val endUtc = utcMidnight(2026, 10, 30)  // перехід на зимовий час 25.10
        val (from, to) = PeriodMath.pickerRangeToLocal(startUtc, endUtc, kyiv)
        assertEquals(startUtc, PeriodMath.localMillisToPickerUtc(from, kyiv))
        assertEquals(endUtc, PeriodMath.localMillisToPickerUtc(to, kyiv))
    }

    // ── R11: перший день місяця ─────────────────────────────────────────────

    @Test
    fun `month from 1st is calendar month`() {
        val (from, to) = PeriodMath.monthPeriodRange(2026, 10, 1, kyiv)
        assertEquals(local(2026, 11, 1, 0, 0), from)
        assertEquals(local(2026, 12, 1, 0, 0) - 1, to)
        assertEquals(30, PeriodMath.monthPeriodDays(2026, 10, 1))
    }

    @Test
    fun `month from 25th runs until 24th of next month`() {
        val (from, to) = PeriodMath.monthPeriodRange(2026, 10, 25, kyiv)
        assertEquals(local(2026, 11, 25, 0, 0), from)
        assertEquals(local(2026, 12, 25, 0, 0) - 1, to)
        assertEquals(30, PeriodMath.monthPeriodDays(2026, 10, 25))
    }

    @Test
    fun `day 31 is clamped to last day of short months`() {
        // січень з 31-го → до 27.02 включно (лютий 2026 має 28 днів → старт 28.02)
        val (from, to) = PeriodMath.monthPeriodRange(2026, 0, 31, kyiv)
        assertEquals(local(2026, 1, 31, 0, 0), from)
        assertEquals(local(2026, 2, 28, 0, 0) - 1, to)
        assertEquals(LocalDate.of(2026, 2, 28), PeriodMath.monthPeriodStart(YearMonth.of(2026, 2), 31))
    }

    @Test
    fun `consecutive month periods have no gaps`() {
        for (day in listOf(1, 15, 29, 30, 31)) {
            for (m in 0 until 12) {
                val (_, to) = PeriodMath.monthPeriodRange(2026, m, day, kyiv)
                val nextYear = if (m == 11) 2027 else 2026
                val (nextFrom, _) = PeriodMath.monthPeriodRange(nextYear, (m + 1) % 12, day, kyiv)
                assertEquals("day=$day month=$m", to + 1, nextFrom)
            }
        }
    }

    @Test
    fun `date before start day belongs to previous month period`() {
        assertEquals(YearMonth.of(2026, 10), PeriodMath.monthPeriodContaining(LocalDate.of(2026, 11, 10), 25))
        assertEquals(YearMonth.of(2026, 11), PeriodMath.monthPeriodContaining(LocalDate.of(2026, 11, 25), 25))
        assertEquals(YearMonth.of(2026, 11), PeriodMath.monthPeriodContaining(LocalDate.of(2026, 11, 16), 1))
        // 28.02 при старті з 31-го — вже лютневий період
        assertEquals(YearMonth.of(2026, 2), PeriodMath.monthPeriodContaining(LocalDate.of(2026, 2, 28), 31))
    }

    // ── R11: перший день тижня ──────────────────────────────────────────────

    @Test
    fun `week starts on configured day`() {
        val wed = LocalDate.of(2026, 11, 18)
        assertEquals(LocalDate.of(2026, 11, 16) to LocalDate.of(2026, 11, 22),
            PeriodMath.weekDates(wed, Calendar.MONDAY))
        assertEquals(LocalDate.of(2026, 11, 15) to LocalDate.of(2026, 11, 21),
            PeriodMath.weekDates(wed, Calendar.SUNDAY))
        assertEquals(LocalDate.of(2026, 11, 14) to LocalDate.of(2026, 11, 20),
            PeriodMath.weekDates(wed, Calendar.SATURDAY))
        // сам перший день тижня — початок свого тижня
        assertEquals(LocalDate.of(2026, 11, 16), PeriodMath.weekDates(LocalDate.of(2026, 11, 16), Calendar.MONDAY).first)
    }

    @Test
    fun `week range spans seven whole local days`() {
        val (from, to) = PeriodMath.weekRange(LocalDate.of(2026, 11, 18), Calendar.MONDAY, kyiv)
        assertEquals(local(2026, 11, 16, 0, 0), from)
        assertEquals(local(2026, 11, 23, 0, 0) - 1, to)
    }

    @Test
    fun `calendar locale keeps language and switches first day of week`() {
        val uk = Locale("uk", "UA")
        val sunday = PeriodMath.calendarLocaleFor(uk, Calendar.SUNDAY)
        assertEquals("uk", sunday.language)
        assertEquals(Calendar.SUNDAY, Calendar.getInstance(sunday).firstDayOfWeek)
        assertEquals(uk, PeriodMath.calendarLocaleFor(uk, Calendar.MONDAY))
        // для вівторка регіону немає — лишається базова локаль
        assertEquals(uk, PeriodMath.calendarLocaleFor(uk, Calendar.TUESDAY))
    }
}
