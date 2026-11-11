package org.syalosovetskyi.onemoney.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
data class CategoryVisualStyle(val circleBg: Color, val iconTint: Color)

object CategoryScreenTokens {
    // Approved categories.html / tokens.json. Stored category colors remain unchanged.
    private val light = mapOf(
        "Продукти" to Color(0xFF26A345),
        "Ресторація" to Color(0xFFE9644F),
        "Дозвілля" to Color(0xFFB670DE),
        "Транспорт" to Color(0xFF608DE5),
        "Здоров'я" to Color(0xFFE3638D),
        "Сім'я" to Color(0xFFD364C5),
        "Подарунки" to Color(0xFFAA8A0D),
        "Покупки" to Color(0xFF2299C1),
        "Робота" to Color(0xFF9180E1),
        "Кафе" to Color(0xFFC17F46),
        "Спорт" to Color(0xFF729A20),
        "Доставка" to Color(0xFF259E8A),
    )
    private val dark = mapOf(
        "Продукти" to Color(0xFF2AB34C),
        "Ресторація" to Color(0xFFEC7A69),
        "Дозвілля" to Color(0xFFBF83E2),
        "Транспорт" to Color(0xFF749BE8),
        "Здоров'я" to Color(0xFFE7799D),
        "Сім'я" to Color(0xFFD97ACD),
        "Подарунки" to Color(0xFFBB980E),
        "Покупки" to Color(0xFF25A8D3),
        "Робота" to Color(0xFF9F90E5),
        "Кафе" to Color(0xFFC98F5D),
        "Спорт" to Color(0xFF7DAA23),
        "Доставка" to Color(0xFF29AE98),
    )

    val byName: Map<String, CategoryVisualStyle> = light.mapValues { style(it.value, false) }

    private fun style(circle: Color, darkTheme: Boolean): CategoryVisualStyle {
        val glyph = if (darkTheme) Color(0xFF14161A) else Color(0xFF16181C)
        // Compare using luminance, including for generated/imported category colors.
        val tint = if ((circle.luminance() + .05f) / (glyph.luminance() + .05f) >= 4.5f)
            glyph else Color.Black
        return CategoryVisualStyle(circle, tint)
    }

    @Suppress("UNUSED_PARAMETER")
    fun resolve(name: String, type: TransactionType, fallback: Color, hasBudget: Boolean,
                darkTheme: Boolean = false): CategoryVisualStyle =
        style((if (darkTheme) dark else light)[name] ?: categoryColor(fallback, darkTheme), darkTheme)

    /** gen.py cat_color: preserve HSL hue/saturation, binary-search target luminance. */
    fun categoryColor(source: Color, darkTheme: Boolean): Color {
        val r = source.red; val g = source.green; val b = source.blue
        val hi = maxOf(r, g, b); val lo = minOf(r, g, b); val delta = hi - lo
        val l = (hi + lo) / 2f
        val s = if (delta == 0f) 0f else delta / (1f - abs(2f * l - 1f))
        val h = if (delta == 0f) 0f else when (hi) {
            r -> ((g - b) / delta + 6f) % 6f
            g -> (b - r) / delta + 2f
            else -> (r - g) / delta + 4f
        } * 60f
        fun at(lightness: Float): Color {
            val c = (1f - abs(2f * lightness - 1f)) * s
            val x = c * (1f - abs((h / 60f) % 2f - 1f))
            val m = lightness - c / 2f
            val rgb = when {
                h < 60 -> floatArrayOf(c,x,0f)
                h < 120 -> floatArrayOf(x,c,0f)
                h < 180 -> floatArrayOf(0f,c,x)
                h < 240 -> floatArrayOf(0f,x,c)
                h < 300 -> floatArrayOf(x,0f,c)
                else -> floatArrayOf(c,0f,x)
            }
            fun channel(i: Int) = ((rgb[i] + m) * 255).roundToInt().coerceIn(0,255)
            return Color(channel(0), channel(1), channel(2))
        }
        var low = 0f; var high = 1f
        repeat(40) {
            val mid = (low + high) / 2f
            if (at(mid).luminance() < (if (darkTheme) .33f else .27f)) low = mid else high = mid
        }
        return at((low + high) / 2f)
    }
}
