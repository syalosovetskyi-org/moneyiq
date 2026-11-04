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
