package org.syalosovetskyi.onemoney.util

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PlatformUtilitiesTest {
    @Test fun locale_selection_is_persisted_and_applied_to_context() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val previous = Locale.getDefault()
        try {
            LocaleWrapper.setLang(context, "uk")
            assertEquals("uk", LocaleWrapper.getLang(context))
            assertEquals("uk", LocaleWrapper.wrap(context).resources.configuration.locales[0].language)
            LocaleWrapper.setLang(context, LocaleWrapper.DEFAULT)
            assertSame(context, LocaleWrapper.wrap(context))
        } finally { Locale.setDefault(previous) }
    }

    @Test fun color_parser_accepts_valid_hex_and_falls_back_on_invalid_data() {
        assertEquals(Color.Red, parseColorHex("#FF0000", Color.Blue))
        assertEquals(Color.Blue, parseColorHex("invalid", Color.Blue))
        assertEquals(Color.Blue, parseColorHex(null, Color.Blue))
        assertEquals(Color.Blue, parseColorHex("", Color.Blue))
    }

    @Test fun all_date_formats_render_under_ukrainian_locale_and_file_names_are_stable() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("uk-UA"))
            val date = Calendar.getInstance().apply { clear(); set(2026, Calendar.JULY, 3, 14, 30) }.time
            assertEquals("2026-07-03", DateFormats.isoDate(date))
            assertEquals("2026-07-03 14:30", DateFormats.isoDateTime(date))
            assertEquals("20260703_1430", DateFormats.fileStamp(date))
            assertEquals("20260703", DateFormats.fileStampDate(date))
            for (text in listOf(DateFormats.dayMonthShort(date), DateFormats.dayMonthFull(date),
                DateFormats.dayMonthYearTime(date), DateFormats.dayMonthYearUk(date), DateFormats.monthYear(date))) {
                assertTrue(text.isNotBlank())
            }
            assertTrue(DateFormats.dayMonthYearUk(date).endsWith("р."))
        } finally { Locale.setDefault(previous) }
    }
}
