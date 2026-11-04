package org.syalosovetskyi.onemoney.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class OneMoneyColors(
    // ── Category screen ───────────────────────────────────────────────────────
    val primaryText:      Color,
    val secondaryText:    Color,
    val tertiaryText:     Color,
    val centerRing:       Color,
    val expensePink:      Color,
    val incomeTeal:       Color,
    val addButtonStroke:  Color,
    val addButtonIcon:    Color,
    // ── Semantic / transaction ────────────────────────────────────────────────
    val incomeGreen:      Color,
    val expenseRed:       Color,
    val transferBlue:     Color,
    val debtOrange:       Color,
    val budgetExpense:    Color,
    val budgetIncome:     Color,
    // ── Navigation shell ──────────────────────────────────────────────────────
    val bottomNavBg:      Color,
    val bottomNavPill:    Color,
    val secondaryIcon:    Color,
)

@Immutable
data class OneMoneyTypography(
    // ── Category screen ───────────────────────────────────────────────────────
    val categoryTitle:        TextStyle,
    val categoryTopAmount:    TextStyle,
    val categoryBottomAmount: TextStyle,
    val centerTitle:          TextStyle,
    val centerAmount:         TextStyle,
    val subcategoryTitle:     TextStyle,
    val subcategoryAmount:    TextStyle,
    // ── Lists / cards ─────────────────────────────────────────────────────────
    val listItemTitle:        TextStyle,
    val listItemAmount:       TextStyle,
    val listItemSub:          TextStyle,
    // ── Sheets ────────────────────────────────────────────────────────────────
    val sheetTitle:           TextStyle,
    // ── Top bar ───────────────────────────────────────────────────────────────
    val topBarLabel:          TextStyle,
    val topBarBalance:        TextStyle,
)

@Immutable
data class OneMoneyDimens(
    // ── Category screen ───────────────────────────────────────────────────────
    val categoryCircleSize:       Dp,
    val categoryCircleCompactSize:Dp,
    val categoryIconSize:         Dp,
    val categoryIconCompactSize:  Dp,
    val sidePanelCircleSize:      Dp,
    val sidePanelIconSize:        Dp,
    val childBadgeSize:           Dp,
    val childBadgeCompactSize:    Dp,
    val addButtonSize:            Dp,
    val addButtonIconSize:        Dp,
    val addButtonStrokeWidth:     Dp,
    val addButtonDashWidth:       Dp,
    val addButtonDashGap:         Dp,
    val cardRadius:               Dp,
    // ── Top bar ───────────────────────────────────────────────────────────────
    val topBarAvatarSize:         Dp,
    val topBarAvatarIconSize:     Dp,
    val topBarBalanceIconSize:    Dp,
    // ── Lists / cards ─────────────────────────────────────────────────────────
    val listItemCircleSize:       Dp,
    val listItemIconSize:         Dp,
    val listItemSmallCircle:      Dp,
    val listItemSmallIcon:        Dp,
    // ── Bottom nav ────────────────────────────────────────────────────────────
    val bottomNavIconSize:        Dp,
    // ── Month pill ────────────────────────────────────────────────────────────
    val pillArrowSize:            Dp,   // DoubleChevron nav arrows
    val pillDropArrowSize:        Dp,   // KeyboardArrowDown inside pill
    val pillBadgeRadius:          Dp,
    // ── Shared ────────────────────────────────────────────────────────────────
    val standardIconSize:         Dp,
    val sheetHandleWidth:         Dp,
    val sheetHandleHeight:        Dp,
    // ── Radius variants ───────────────────────────────────────────────────────
    val smallRadius:              Dp,   // 8dp
    val keyRadius:                Dp,   // 10dp — клавиатура/мелкие чипы
    val cardRadiusAlt:            Dp,   // 14dp — карточки-строки, поля
    val largeRadius:              Dp,   // 16dp
    val pillRadius:               Dp,   // 50dp — «таблетки»
    // ── Stroke widths ─────────────────────────────────────────────────────────
    val thinStroke:               Dp,
    val mediumStroke:             Dp,
    val thickStroke:              Dp,
    val selectionRingWidth:       Dp,   // 3dp — кольцо выделения иконки/цвета
)

// Dark-mode semantic tokens. Neutrals follow the same M3 dark tonal palette as the
// dark colorScheme (surface #121318 family); brand/money colors are kept unchanged
// (they read fine on dark). See dark-theme design spec.
object OneMoneyDarkTokens {
    val colors = OneMoneyColors(
        primaryText     = Color(0xFFE3E1EA),  // onSurface
        secondaryText   = Color(0xFFA8A7B0),
        tertiaryText    = Color(0xFF8B8A93),   // контраст на темному тлі: було ~4.0:1, стало ~4.8:1
        centerRing      = Color(0xFF34353C),  // surfaceContainerHighest (donut empty ring)
        expensePink     = Color(0xFFFFB4A9),
        incomeTeal      = Color(0xFF6FD08C),
        addButtonStroke = Color(0xFF45464F),
        addButtonIcon   = Color(0xFF8F909A),
        incomeGreen     = Color(0xFF6FD08C),
        expenseRed      = Color(0xFFFFB4A9),
        transferBlue    = Color(0xFFA8C8FF),
        debtOrange      = Color(0xFFE9B3F5),
        budgetExpense   = Color(0xFFFFB4A9),
        budgetIncome    = Color(0xFF6FD08C),
        bottomNavBg     = Color(0xFF1F2026),  // surfaceContainer
        bottomNavPill   = Color(0xFF354378),  // primaryContainer
        secondaryIcon   = Color(0xFFC6C5D0),
    )
    val typography = OneMoneyLightTokens.typography
    val dimens     = OneMoneyLightTokens.dimens
}

object OneMoneyLightTokens {
    val colors = OneMoneyColors(
        primaryText     = Color(0xFF111111),
        secondaryText   = Color(0xFF8E8E8E),
        tertiaryText    = Color(0xFFC9C9C9),
        centerRing      = Color(0xFFE6E6EB),
        expensePink     = ExpensePink,
        incomeTeal      = IncomeTeal,
        addButtonStroke = Color(0xFFD8D8D8),
        addButtonIcon   = Color(0xFFB6B6B6),
        incomeGreen     = IncomeGreen,
        expenseRed      = ExpenseRed,
        transferBlue    = TransferBlue,
        debtOrange      = DebtOrange,
        budgetExpense   = BudgetExpenseColor,
        budgetIncome    = BudgetIncomeColor,
        bottomNavBg     = BottomNavBg,
        bottomNavPill   = BottomNavActivePill,
        secondaryIcon   = SecondaryIconColor,
    )

    val typography = OneMoneyTypography(
        categoryTitle        = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light,    fontSize = 11.sp, lineHeight = 13.sp),
        categoryTopAmount    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,   fontSize = 10.sp, lineHeight = 12.sp),
        categoryBottomAmount = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,   fontSize = 10.sp, lineHeight = 12.sp),
        centerTitle          = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light,    fontSize = 15.sp, lineHeight = 18.sp),
        centerAmount         = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light,    fontSize = 15.sp, lineHeight = 18.sp),
        subcategoryTitle     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,   fontSize = 10.sp, lineHeight = 12.sp),
        subcategoryAmount    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,   fontSize =  9.sp, lineHeight = 11.sp),
        listItemTitle        = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 19.sp),
        listItemAmount       = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,   fontSize = 13.sp, lineHeight = 17.sp),
        listItemSub          = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 16.sp),
        sheetTitle           = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
        topBarLabel          = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light,    fontSize = 13.sp, lineHeight = 17.sp),
        topBarBalance        = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light,    fontSize = 18.sp, lineHeight = 22.sp),
    )

    val dimens = OneMoneyDimens(
        categoryCircleSize        = 54.dp,
        categoryCircleCompactSize = 40.dp,
        categoryIconSize          = 24.dp,
        categoryIconCompactSize   = 22.dp,
        sidePanelCircleSize       = 32.dp,
        sidePanelIconSize         = 16.dp,
        childBadgeSize            = 18.dp,
        childBadgeCompactSize     = 16.dp,
        addButtonSize             = 40.dp,
        addButtonIconSize         = 18.dp,
        addButtonStrokeWidth      = 1.dp,
        addButtonDashWidth        = 8.dp,
        addButtonDashGap          = 6.dp,
        cardRadius                = 12.dp,
        topBarAvatarSize          = 44.dp,
        topBarAvatarIconSize      = 22.dp,
        topBarBalanceIconSize     = 16.dp,
        listItemCircleSize        = 44.dp,
        listItemIconSize          = 24.dp,
        listItemSmallCircle       = 36.dp,
        listItemSmallIcon         = 18.dp,
        bottomNavIconSize         = 24.dp,
        pillArrowSize             = 36.dp,
        pillDropArrowSize         = 22.dp,
        pillBadgeRadius           =  6.dp,
        standardIconSize          = 24.dp,
        sheetHandleWidth          = 32.dp,
        sheetHandleHeight         =  4.dp,
        smallRadius               =  8.dp,
        keyRadius                 = 10.dp,
        cardRadiusAlt             = 14.dp,
        largeRadius               = 16.dp,
        pillRadius                = 50.dp,
        thinStroke                =  1.dp,
        mediumStroke              =  1.5.dp,
        thickStroke               =  2.dp,
        selectionRingWidth        =  3.dp,
    )
}