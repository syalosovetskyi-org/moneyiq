package org.syalosovetskyi.onemoney.ui.components.text

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.util.CompactSuffixes

/** До якого масштабу кеглю зменшуємо повну суму, перш ніж перейти на скорочений запис. */
internal const val AMOUNT_MIN_SCALE = 0.7f

/** Нижче цього масштабу не йдемо навіть для скороченого запису: далі текст стає нечитаним. */
internal const val AMOUNT_HARD_MIN_SCALE = 0.5f

/** Що показати і яким масштабом кеглю. */
internal data class AmountFit(val text: String, val scale: Float)

/**
 * Сума в один рядок, яка НІКОЛИ не обрізається (R4, serg/tasks#3037).
 *
 * Порядок: повна сума у стилі → повна сума зменшеним кеглем (до [minScale]) →
 * [compactText] («1,2 млн ₴»), якщо його передано → останній захід: кегль до
 * [AMOUNT_HARD_MIN_SCALE]. Многоточчя й обрізання не використовуються взагалі; той самий
 * прийом, що й у CategoryCellLabel: ширину дає BoxWithConstraints, текст міряє TextMeasurer,
 * масштаб рахується один раз на (текст, стиль, ширина) — без циклу перекомпоновок.
 *
 * [style] має містити і fontWeight — від нього залежить ширина.
 */
@Composable
fun FitAmountText(
    text:             String,
    style:            TextStyle,
    color:            Color,
    modifier:         Modifier = Modifier,
    compactText:      String? = null,
    textAlign:        TextAlign = TextAlign.Center,
    contentAlignment: Alignment = Alignment.Center,
    minScale:         Float = AMOUNT_MIN_SCALE,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier, contentAlignment = contentAlignment) {
        val maxWidthPx = constraints.maxWidth
        val fit = remember(text, compactText, style, maxWidthPx, minScale, measurer) {
            fitAmount(measurer, text, compactText, style, maxWidthPx, minScale)
        }
        Text(
            fit.text,
            style     = style.scaledBy(fit.scale),
            color     = color,
            maxLines  = 1,
            softWrap  = false,
            // Visible, а не Ellipsis/Clip: навіть у найгіршому разі число не обрізається.
            overflow  = TextOverflow.Visible,
            textAlign = textAlign,
        )
    }
}

/** Скорочення «тис./млн/млрд» мовою інтерфейсу — для [org.syalosovetskyi.onemoney.util.formatMoneyCompact]. */
@Composable
fun compactSuffixes(): CompactSuffixes = CompactSuffixes(
    thousand = stringResource(R.string.amount_suffix_thousand),
    million  = stringResource(R.string.amount_suffix_million),
    billion  = stringResource(R.string.amount_suffix_billion),
)

internal fun fitAmount(
    measurer: TextMeasurer, text: String, compactText: String?, style: TextStyle,
    maxWidthPx: Int, minScale: Float,
): AmountFit {
    // Ширина невідома (немає обмеження) — зменшувати нема відносно чого.
    if (maxWidthPx <= 0 || maxWidthPx == Constraints.Infinity) return AmountFit(text, 1f)
    fun widthOf(s: String) = measurer.measure(s, style, softWrap = false, maxLines = 1).size.width
    val fullScale = scaleToFit(widthOf(text), maxWidthPx)
    if (fullScale >= minScale) return AmountFit(text, fullScale)
    if (compactText != null && compactText != text) {
        val compactScale = scaleToFit(widthOf(compactText), maxWidthPx)
        return AmountFit(compactText, compactScale.coerceAtLeast(AMOUNT_HARD_MIN_SCALE))
    }
    return AmountFit(text, fullScale.coerceAtLeast(AMOUNT_HARD_MIN_SCALE))
}

/** 1f — якщо вміщається; інакше пропорційний масштаб із 2% запасу на округлення до пікселя. */
internal fun scaleToFit(widthPx: Int, maxWidthPx: Int): Float =
    if (widthPx <= maxWidthPx || widthPx <= 0) 1f else (maxWidthPx.toFloat() / widthPx * 0.98f).coerceAtMost(1f)

private fun TextStyle.scaledBy(scale: Float): TextStyle = if (scale >= 1f) this else copy(
    fontSize      = if (fontSize.isSp) fontSize * scale else fontSize,
    lineHeight    = if (lineHeight.isSp) lineHeight * scale else lineHeight,
    letterSpacing = if (letterSpacing.isSp) letterSpacing * scale else letterSpacing,
)
