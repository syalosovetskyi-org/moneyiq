package org.syalosovetskyi.onemoney.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Semantic colors ──────────────────────────────────────────────────────────
val IncomeGreen      = Color(0xFF1B6B3A)
val ExpenseRed       = Color(0xFFB3261E)
val TransferBlue     = Color(0xFF1F5FA6)
val DebtOrange       = Color(0xFF8A3FA0)
val BudgetExpenseColor = Color(0xFFB3261E)
val BudgetIncomeColor  = Color(0xFF1B6B3A)

// ── Fallback / utility colors (централизованы; раньше дублировались локальными
//    private val по экранам, местами рассинхронизированные) ─────────────────────
val NegativeAmountColor   = Color(0xFFD32F2F)  // сумма/баланс в минусе
val StarGoldColor         = Color(0xFFFFD700)  // «зроблено основним» / избранное
val OnLightColor          = Color(0xFF1C1B1F)  // тёмный контент на светлом/цветном фоне (был DarkOnLightColor ×6)
val FallbackAccountColor  = Color(0xFF4361EE)  // дефолт-цвет счёта (унифицирован: было 4361EE в 3 местах и 3949AB в CalcDateSheet)
val FallbackCategoryColor = Color(0xFFFF5722)  // дефолт-цвет категории
val FallbackIconColor     = Color(0xFF757575)  // дефолт-тинт иконки

// ── UI-акценты панелей/фильтров (раньше хардкод ARGB по экранам) ──────────────
val AccentIndigo      = Color(0xFF3949AB)  // панель счёта-источника/назначения, фильтр по счёту
val AccentIndigoLight = Color(0xFF5C6BC0)  // фильтр по запросу
val AccentTeal        = Color(0xFF009688)  // панель переказу
val AccentBlueGrey    = Color(0xFF607D8B)  // нейтральный фильтр/fallback категории

// ── Category screen colour tokens ─────────────────────────────────────────────
val MonthRed                  = Color(0xFFD7261E)
val MonthRedLight             = Color(0xFFF8D9D5)
val ExpensePink               = Color(0xFFB3261E)
val IncomeTeal                = Color(0xFF1B6B3A)
val CenterRingColor           = Color(0xFFD3D0D6)
val BottomNavBg               = Color(0xFFFFFFFF)
val BottomNavActivePill       = Color(0xFFE7E7F2)
val SecondaryIconColor        = Color(0xFF373941)
val CategoryTopAmountColor    = Color(0xFFC1C1C1)
val CategoryBottomAmountColor = Color(0xFFACACAC)

// ── Zerno colour palette (Material 3) ───────────────────────────────────────
// Light
private val md_light_primary             = Color(0xFF7C581C)
private val md_light_onPrimary           = Color(0xFFFFFFFF)
private val md_light_primaryContainer    = Color(0xFFFFDDB1)
private val md_light_onPrimaryContainer  = Color(0xFF291800)
private val md_light_secondary           = Color(0xFF6F5B40)
private val md_light_onSecondary         = Color(0xFFFFFFFF)
private val md_light_secondaryContainer  = Color(0xFFDEE1F9)
private val md_light_onSecondaryContainer= Color(0xFF161B2C)
private val md_light_tertiary            = Color(0xFF855239)
private val md_light_onTertiary          = Color(0xFFFFFFFF)
private val md_light_tertiaryContainer   = Color(0xFFFFD6F6)
private val md_light_onTertiaryContainer = Color(0xFF2D1029)
private val md_light_error              = Color(0xFFBA1A1A)
private val md_light_onError            = Color(0xFFFFFFFF)
private val md_light_errorContainer     = Color(0xFFFFDAD6)
private val md_light_onErrorContainer   = Color(0xFF410002)
private val md_light_background         = Color(0xFFFFF8F4)
private val md_light_onBackground       = Color(0xFF201B15)
private val md_light_surface            = Color(0xFFFFF8F4)
private val md_light_onSurface          = Color(0xFF201B15)
private val md_light_surfaceVariant     = Color(0xFFF0EFF6)
private val md_light_onSurfaceVariant   = Color(0xFF4F4538)
private val md_light_outline            = Color(0xFF817566)
private val md_light_outlineVariant     = Color(0xFFD8D7E3)

// Dark
private val md_dark_primary             = Color(0xFFEFBE79)
private val md_dark_onPrimary           = Color(0xFF442B00)
private val md_dark_primaryContainer    = Color(0xFF614004)
private val md_dark_onPrimaryContainer  = Color(0xFFFFDDB1)
private val md_dark_secondary           = Color(0xFFDDC3A1)
private val md_dark_onSecondary         = Color(0xFF2C2F42)
private val md_dark_secondaryContainer  = Color(0xFF414459)
private val md_dark_onSecondaryContainer= Color(0xFFDEE1F9)
private val md_dark_tertiary            = Color(0xFFFBB798)
private val md_dark_onTertiary          = Color(0xFF44243F)
private val md_dark_tertiaryContainer   = Color(0xFF5C3B57)
private val md_dark_onTertiaryContainer = Color(0xFFFFD6F6)
private val md_dark_error              = Color(0xFFFFB4AB)
private val md_dark_onError            = Color(0xFF690005)
private val md_dark_errorContainer     = Color(0xFF93000A)
private val md_dark_onErrorContainer   = Color(0xFFFFDAD6)
private val md_dark_background         = Color(0xFF17130D)
private val md_dark_onBackground       = Color(0xFFEBE1D6)
private val md_dark_surface            = Color(0xFF17130D)
private val md_dark_onSurface          = Color(0xFFEBE1D6)
private val md_dark_surfaceVariant     = Color(0xFF45464F)
private val md_dark_onSurfaceVariant   = Color(0xFFD3C4B3)
private val md_dark_outline            = Color(0xFF9C8F7F)
private val md_dark_outlineVariant     = Color(0xFF45464F)

private val LightColors = lightColorScheme(
    primary             = md_light_primary,
    onPrimary           = md_light_onPrimary,
    primaryContainer    = md_light_primaryContainer,
    onPrimaryContainer  = md_light_onPrimaryContainer,
    secondary           = md_light_secondary,
    onSecondary         = md_light_onSecondary,
    secondaryContainer  = md_light_secondaryContainer,
    onSecondaryContainer= md_light_onSecondaryContainer,
    tertiary            = md_light_tertiary,
    onTertiary          = md_light_onTertiary,
    tertiaryContainer   = md_light_tertiaryContainer,
    onTertiaryContainer = md_light_onTertiaryContainer,
    error               = md_light_error,
    onError             = md_light_onError,
    errorContainer      = md_light_errorContainer,
    onErrorContainer    = md_light_onErrorContainer,
    background          = md_light_background,
    onBackground        = md_light_onBackground,
    surface             = md_light_surface,
    onSurface           = md_light_onSurface,
    surfaceVariant      = md_light_surfaceVariant,
    onSurfaceVariant    = md_light_onSurfaceVariant,
    outline             = md_light_outline,
    outlineVariant      = md_light_outlineVariant,
)

private val DarkColors = darkColorScheme(
    primary             = md_dark_primary,
    onPrimary           = md_dark_onPrimary,
    primaryContainer    = md_dark_primaryContainer,
    onPrimaryContainer  = md_dark_onPrimaryContainer,
    secondary           = md_dark_secondary,
    onSecondary         = md_dark_onSecondary,
    secondaryContainer  = md_dark_secondaryContainer,
    onSecondaryContainer= md_dark_onSecondaryContainer,
    tertiary            = md_dark_tertiary,
    onTertiary          = md_dark_onTertiary,
    tertiaryContainer   = md_dark_tertiaryContainer,
    onTertiaryContainer = md_dark_onTertiaryContainer,
    error               = md_dark_error,
    onError             = md_dark_onError,
    errorContainer      = md_dark_errorContainer,
    onErrorContainer    = md_dark_onErrorContainer,
    background          = md_dark_background,
    onBackground        = md_dark_onBackground,
    surface             = md_dark_surface,
    onSurface           = md_dark_onSurface,
    surfaceVariant      = md_dark_surfaceVariant,
    onSurfaceVariant    = md_dark_onSurfaceVariant,
    outline             = md_dark_outline,
    outlineVariant      = md_dark_outlineVariant,
    // ── M3 surface-container elevation ladder (dark tonal palette, seed #B07A1A) ──
    surfaceDim             = Color(0xFF121318),
    surfaceBright          = Color(0xFF38393F),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceContainerLow    = Color(0xFF1B1C22),
    surfaceContainer       = Color(0xFF1F2026),
    surfaceContainerHigh   = Color(0xFF292A31),
    surfaceContainerHighest= Color(0xFF34353C),
    inverseSurface         = Color(0xFFE3E1EA),
    inverseOnSurface       = Color(0xFF2F3038),
    inversePrimary         = Color(0xFF7C581C),
    scrim                  = Color(0xFF000000),
)

private fun Color.lighten(factor: Float) = Color(
    red   = (red   + (1f - red)   * factor).coerceIn(0f, 1f),
    green = (green + (1f - green) * factor).coerceIn(0f, 1f),
    blue  = (blue  + (1f - blue)  * factor).coerceIn(0f, 1f),
    alpha = alpha
)

private fun Color.darken(factor: Float) = Color(
    red   = (red   * (1f - factor)).coerceIn(0f, 1f),
    green = (green * (1f - factor)).coerceIn(0f, 1f),
    blue  = (blue  * (1f - factor)).coerceIn(0f, 1f),
    alpha = alpha
)

private fun onColorFor(bg: Color): Color =
    if (bg.luminance() > 0.4f) Color.Black else Color.White

private fun applyAccent(base: ColorScheme, accent: Color, dark: Boolean): ColorScheme {
    // On dark backgrounds the raw accent (often a mid/dark hue) has poor contrast
    // and is used as text colour for subtitles/labels — lighten it into a legible
    // tonal primary, matching the hand-tuned dark primary (#B6C4FF) design intent.
    val primary     = if (dark) accent.lighten(0.55f) else accent
    val container   = if (dark) accent.darken(0.35f) else accent.lighten(0.72f)
    val onContainer = onColorFor(container)
    return base.copy(
        primary            = primary,
        onPrimary          = onColorFor(primary),
        primaryContainer   = container,
        onPrimaryContainer = onContainer,
        inversePrimary     = if (dark) accent.lighten(0.4f) else accent.darken(0.3f)
    )
}

@Composable
fun onemoneyTheme(
    darkTheme:    Boolean = isSystemInDarkTheme(),
    accentColor:  Color?  = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val base = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else      -> LightColors
    }

    val colorScheme = if (accentColor != null && accentColor != Color(0xFFB07A1A)) applyAccent(base, accentColor, darkTheme) else base

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Transparent system bars so app content draws edge-to-edge; icon/handle
            // brightness follows the APP theme (not the system one). Fixes the light
            // navigation-bar strip that appeared at the bottom when the app is in dark
            // mode on a light-mode system.
            window.statusBarColor     = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            // The XML window background is a fixed white shell (no values-night); with
            // transparent system bars that white leaks through behind the status/nav
            // bars in dark mode. Paint the window with the themed background instead.
            window.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(colorScheme.background.toArgb())
            )
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars     = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
}
