package org.syalosovetskyi.onemoney.util

import java.text.NumberFormat
import java.util.Locale

/**
 * Форматування грошових сум. Ціле число — без дробової частини, інакше 2 знаки.
 * Раніше жила в MainScreen.kt і імпортувалася по всьому застосунку — суто
 * утиліта, тому винесена сюди без зміни поведінки.
 */
fun formatMoney(amount: Double, currency: String = ""): String {
    require(amount.isFinite()) { "Amount must be finite" }
    val isWhole = amount == kotlin.math.floor(amount) && !amount.isInfinite()
    val nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("uk-UA"))
    nf.minimumFractionDigits = if (isWhole) 0 else 2
    nf.maximumFractionDigits = if (isWhole) 0 else 2
    val number = nf.format(kotlin.math.abs(amount))
    val signed = if (amount < 0) "−$number" else number
    val symbol = org.syalosovetskyi.onemoney.ui.settings.data.CURRENCIES_ALL
        .find { it.code == currency }?.symbol ?: currency
    return if (symbol.isNotBlank()) "$signed $symbol" else signed
}

/** Скорочення для [formatMoneyCompact]: «тис.», «млн», «млрд» мовою інтерфейсу. */
data class CompactSuffixes(val thousand: String, val million: String, val billion: String)

/**
 * Скорочений запис суми для вузьких місць (R4, serg/tasks#3037): «1,2 млн ₴», «−15,9 тис. $».
 * Застосовується лише тоді, коли повна сума не влазить навіть зменшеним кеглем; сума менша
 * за 10 000 не скорочується — її видно повністю. Один знак після коми, «,0» відкидається.
 */
fun formatMoneyCompact(amount: Double, currency: String, suffixes: CompactSuffixes): String {
    require(amount.isFinite()) { "Amount must be finite" }
    val abs = kotlin.math.abs(amount)
    if (abs < 10_000.0) return formatMoney(amount, currency)
    val tiers = listOf(1e9 to suffixes.billion, 1e6 to suffixes.million, 1e3 to suffixes.thousand)
    // Беремо найбільший порядок, за якого заокруглене значення ≥ 1; і переходимо на порядок вище,
    // якщо округлення дало 1000 (999 950 → «1 млн», а не «1 000 тис.»).
    var index = tiers.indexOfFirst { abs >= it.first }
    var scaled = Math.round(abs / tiers[index].first * 10) / 10.0
    if (scaled >= 1000.0 && index > 0) {
        index -= 1
        scaled = Math.round(abs / tiers[index].first * 10) / 10.0
    }
    val nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("uk-UA"))
    nf.minimumFractionDigits = 0
    nf.maximumFractionDigits = 1
    val number = "${nf.format(scaled)} ${tiers[index].second}"
    val signed = if (amount < 0) "−$number" else number
    val symbol = org.syalosovetskyi.onemoney.ui.settings.data.CURRENCIES_ALL
        .find { it.code == currency }?.symbol ?: currency
    return if (symbol.isNotBlank()) "$signed $symbol" else signed
}
