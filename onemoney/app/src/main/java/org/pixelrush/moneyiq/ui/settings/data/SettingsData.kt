package org.syalosovetskyi.onemoney.ui.settings.data

import androidx.compose.ui.graphics.Color

val ACCENT_COLORS: List<Color> = listOf(
    Color(0xFFB07A1A), Color(0xFF9C27B0), Color(0xFF673AB7), Color(0xFF3F51B5),
    Color(0xFF2196F3), Color(0xFF03A9F4), Color(0xFF00BCD4),
    Color(0xFF009688), Color(0xFF4CAF50), Color(0xFF8BC34A),
    Color(0xFFCDDC39), Color(0xFFFFC107), Color(0xFFFF9800),
    Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF795548)
)

val LANGUAGES: List<Pair<String, String>> = listOf(
    "default" to "За замовчуванням",
    "uk"      to "Українська",
    "ru"      to "Русский",
    "en"      to "English",
    "de"      to "Deutsch",
    "fr"      to "Français",
    "pl"      to "Polski"
)

val DAYS_OF_WEEK: List<Pair<Int, String>> = listOf(
    1 to "Неділя",
    2 to "Понеділок",
    3 to "Вівторок",
    4 to "Середа",
    5 to "Четвер",
    6 to "П'ятниця",
    7 to "Субота"
)

val CURRENCY_FORMAT_EXAMPLES: List<String> = listOf(
    "−1.234.567,90 UAH",
    "−1 234 567.90 UAH",
    "−1 234 567,90 UAH",
    "−UAH 1,234,567.90",
    "−UAH 12,34,567.90",
    "−UAH 1.234.567,90",
    "−UAH 1 234 567.90",
    "−UAH 1 234 567,90",
    "UAH −1,234,567.90",
    "UAH −12,34,567.90",
    "UAH −1.234.567,90",
    "UAH −1 234 567.90",
    "UAH −1 234 567,90"
)

fun formatMoneyWithSettings(amount: Double, symbol: String, formatIndex: Int): String {
    val neg     = amount < 0
    val abs     = kotlin.math.abs(amount)
    require(amount.isFinite()) { "Amount must be finite" }
    val rounded = java.math.BigDecimal.valueOf(abs).setScale(2, java.math.RoundingMode.HALF_UP)
        .toPlainString().split('.')
    val integerDigits = rounded[0]
    val decStr = rounded[1]

    data class Fmt(val grp: String, val dec: Char, val indian: Boolean, val pos: Int)

    val (grp, dec, indian, pos) = when (formatIndex) {
        0  -> Fmt(".", ',', false, 0)
        1  -> Fmt(" ", '.', false, 0)
        2  -> Fmt(" ", ',', false, 0)
        3  -> Fmt(",", '.', false, 1)
        4  -> Fmt(",", '.', true,  1)
        5  -> Fmt(".", ',', false, 1)
        6  -> Fmt(" ", '.', false, 1)
        7  -> Fmt(" ", ',', false, 1)
        8  -> Fmt(",", '.', false, 2)
        9  -> Fmt(",", '.', true,  2)
        10 -> Fmt(".", ',', false, 2)
        11 -> Fmt(" ", '.', false, 2)
        12 -> Fmt(" ", ',', false, 2)
        else -> Fmt(" ", ',', false, 0)
    }

    val numStr = buildString {
        val s = integerDigits
        if (indian && s.length > 3) {
            val last3 = s.takeLast(3)
            val rest  = s.dropLast(3)
            rest.forEachIndexed { i, c ->
                if (i > 0 && (rest.length - i) % 2 == 0) append(grp)
                append(c)
            }
            append(grp); append(last3)
        } else {
            s.forEachIndexed { i, c ->
                if (i > 0 && (s.length - i) % 3 == 0) append(grp)
                append(c)
            }
        }
        append(dec); append(decStr)
    }

    val minus = "−"
    return when (pos) {
        0 -> if (neg) "$minus$numStr $symbol" else "$numStr $symbol"
        1 -> if (neg) "$minus$symbol $numStr"  else "$symbol $numStr"
        2 -> if (neg) "$symbol $minus$numStr"  else "$symbol $numStr"
        else -> "$numStr $symbol"
    }
}
