package org.syalosovetskyi.onemoney.ui.categories

import androidx.core.graphics.toColorInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.util.formatMoney
import org.syalosovetskyi.onemoney.ui.theme.CategoryScreenTokens
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme
import org.syalosovetskyi.onemoney.util.parseColorHex
import org.syalosovetskyi.onemoney.util.suggestCategoryStyle
import org.syalosovetskyi.onemoney.R

private val FallbackCategoryColor: Color = Color(0xFFFF5722)

// ── Чип категорії ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CategoryChip(
    category:       CategoryEntity,
    spending:       Double,
    onClick:        () -> Unit,
    childCount:     Int     = 0,
    onLongPress:    (() -> Unit)? = null,
    onDoubleClick:  () -> Unit = {},
    showChildBadge: Boolean = false,
    groupColorHex:  String? = null,
    isCompact:      Boolean = false,
    isExpanded:     Boolean = false,
    budgetOverride: Double? = null,
    flatBottom:     Boolean = false,
    overrideWidth:  Dp? = null,
    overrideHeight: Dp? = null,
    overrideCircle: Dp? = null,
) {
    val tokens = OneMoneyTheme.dimens
    val typo = OneMoneyTheme.typography
    val colors = OneMoneyTheme.colors
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val fallback = remember(category.colorHex) { parseColorHex(category.colorHex, FallbackCategoryColor) }
    val style = CategoryScreenTokens.resolve(category.name, category.type, fallback,
        (budgetOverride ?: category.budgetAmount) > 0, dark)
    val iconKey = if (category.icon == "category") suggestCategoryStyle(category.name, category.type).first else category.icon
    val budget = budgetOverride ?: category.budgetAmount
    Column(
        modifier = Modifier.width(overrideWidth ?: if (isCompact) CHIP_WIDTH_COMPACT else CHIP_WIDTH)
            .heightIn(min = overrideHeight ?: 112.dp)
            .testTag("category_cell_${category.id}")
            .clip(RoundedCornerShape(tokens.cardRadius))
            .then(if (isExpanded) Modifier.background(MaterialTheme.colorScheme.surfaceVariant) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress, onDoubleClick = onDoubleClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(overrideCircle ?: tokens.categoryCircleSize), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(style.circleBg), contentAlignment = Alignment.Center) {
                Icon(categoryIconFor(iconKey), null, tint = style.iconTint, modifier = Modifier.size(tokens.categoryIconSize))
            }
            if (showChildBadge && childCount > 0) {
                Text("+$childCount", style = typo.subcategoryAmount,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary).padding(horizontal = 3.dp))
            }
        }
        Text(category.name, style = typo.categoryTitle, color = colors.primaryText,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth())
        Text(formatMoney(spending) + " ₴", style = typo.categoryBottomAmount,
            color = if (spending == 0.0) colors.secondaryText else colors.primaryText,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag("category_amount_${category.id}"))
        if (budget > 0.0) {
            Text(formatBudgetAmount(budget - spending) + " ₴", style = typo.categoryTopAmount,
                color = if (spending > budget) colors.expenseRed else colors.secondaryText,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

// ── Інлайн-панель підкатегорій (в рядку доната) ──────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SideSubcategoryPanel(
    parent:           CategoryEntity,
    children:         List<CategoryEntity>,
    spending:         Map<Long, Double>,
    onClickChild:     (CategoryEntity) -> Unit,
    onLongClickChild: (CategoryEntity) -> Unit = {},
    modifier:         Modifier = Modifier
) {
    val tokens = OneMoneyTheme.dimens
    val typo   = OneMoneyTheme.typography
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f

    val parentColor = remember(parent.colorHex) {
        parseColorHex(parent.colorHex, FallbackCategoryColor)
    }
    val sortedKids = children
        .filter { (spending[it.id] ?: 0.0) > 0.0 || it.budgetAmount > 0.0 }
        .sortedByDescending { spending[it.id] ?: 0.0 }

    Card(
        modifier  = modifier.padding(horizontal = Spacing.xs, vertical = Spacing.sm),
        shape     = RoundedCornerShape(tokens.cardRadius),
        colors    = CardDefaults.cardColors(containerColor = parentColor.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            sortedKids.forEach { child ->
                val childColor = remember(child.colorHex) {
                    parseColorHex(child.colorHex, FallbackCategoryColor)
                }
                val childStyle = CategoryScreenTokens.resolve(child.name, child.type, childColor, child.budgetAmount > 0, dark)
                val childIconKey = if (child.icon == "category")
                    suggestCategoryStyle(child.name, child.type).first else child.icon
                val childSpend = spending[child.id] ?: 0.0
                val hasBudget  = child.budgetAmount > 0.0
                val remainingBudget = child.budgetAmount - childSpend
                val overBudget = hasBudget && remainingBudget < 0.0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick     = { onClickChild(child) },
                            onLongClick = { onLongClickChild(child) }
                        )
                        .padding(horizontal = Spacing.sm, vertical = 5.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(tokens.sidePanelCircleSize)
                            .clip(CircleShape)
                            .background(childStyle.circleBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            categoryIconFor(childIconKey), null,
                            tint     = childStyle.iconTint,
                            modifier = Modifier.size(tokens.sidePanelIconSize)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            child.name,
                            style    = typo.subcategoryTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            formatMoney(childSpend) + " ₴",
                            style      = typo.subcategoryAmount,
                            color      = if (childSpend > 0) OneMoneyTheme.colors.primaryText
                                         else OneMoneyTheme.colors.secondaryText,
                            maxLines   = 1
                        )
                    }
                    if (hasBudget) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (overBudget) childColor
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                formatBudgetAmount(kotlin.math.abs(remainingBudget)) + " ₴",
                                style      = typo.subcategoryAmount.copy(fontSize = 12.sp, lineHeight = 16.sp),
                                fontWeight = FontWeight.Medium,
                                color      = if (overBudget) {
                                    if (childColor.luminance() > .5f) org.syalosovetskyi.onemoney.ui.theme.OnLightColor else Color.White
                                }
                                             else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                maxLines   = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Полоса підкатегорій ───────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ExpandedCategoryStrip(
    parent:           CategoryEntity,
    children:         List<CategoryEntity>,
    spending:         Map<Long, Double>,
    onClickParent:    () -> Unit,
    onClickChild:     (CategoryEntity) -> Unit,
    onLongClickChild: (CategoryEntity) -> Unit = {},
    showParentHeader: Boolean = false,
    inline:           Boolean = false
) {
    val tokens = OneMoneyTheme.dimens
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f

    val parentColor = remember(parent.colorHex) {
        parseColorHex(parent.colorHex, FallbackCategoryColor)
    }
    val sortedKids = children
        .filter { (spending[it.id] ?: 0.0) > 0.0 || it.budgetAmount > 0.0 }
        .sortedByDescending { spending[it.id] ?: 0.0 }

    val stripContent: @Composable ColumnScope.() -> Unit = {
        if (showParentHeader) {
            val parentIconKey = if (parent.icon == "category")
                suggestCategoryStyle(parent.name, parent.type).first else parent.icon
            val parentSpend = spending[parent.id] ?: 0.0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClickParent() }
                    .padding(horizontal = Spacing.md, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(tokens.categoryCircleSize)
                        .clip(CircleShape)
                        .background(parentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        categoryIconFor(parentIconKey), null,
                        tint     = if (parentColor.luminance() > .5f) org.syalosovetskyi.onemoney.ui.theme.OnLightColor else Color.White,
                        modifier = Modifier.size(tokens.categoryIconSize)
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Column {
                    Text(
                        parent.name,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (parentSpend > 0.0) {
                        Text(
                            formatMoney(parentSpend) + " ₴",
                            style      = MaterialTheme.typography.bodySmall,
                            color      = parentColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            HorizontalDivider(
                color     = parentColor.copy(alpha = 0.15f),
                thickness = 1.dp,
                modifier  = Modifier.padding(horizontal = Spacing.sm)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.sm, vertical = Spacing.md)
        ) {
            sortedKids.forEach { child ->
                val childColor = remember(child.colorHex) {
                    parseColorHex(child.colorHex, FallbackCategoryColor)
                }
                val childStyle = CategoryScreenTokens.resolve(child.name, child.type, childColor, child.budgetAmount > 0, dark)
                val childIconKey = if (child.icon == "category")
                    suggestCategoryStyle(child.name, child.type).first else child.icon
                val childSpend = spending[child.id] ?: 0.0

                Column(
                    modifier = Modifier
                        .width(84.dp)
                        .combinedClickable(
                            onClick     = { onClickChild(child) },
                            onLongClick = { onLongClickChild(child) }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(tokens.categoryCircleSize)
                            .clip(CircleShape)
                            .background(childStyle.circleBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            categoryIconFor(childIconKey), null,
                            tint     = childStyle.iconTint,
                            modifier = Modifier.size(tokens.categoryIconCompactSize)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        child.name,
                        style     = OneMoneyTheme.typography.subcategoryTitle,
                        maxLines  = 1,
                        overflow  = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.fillMaxWidth()
                    )
                    Text(
                        formatMoney(childSpend) + " ₴",
                        style      = OneMoneyTheme.typography.subcategoryAmount,
                        color      = if (childSpend > 0) OneMoneyTheme.colors.primaryText else OneMoneyTheme.colors.secondaryText,
                        maxLines   = 1,
                        textAlign  = TextAlign.Center,
                        modifier   = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (inline) {
        HorizontalDivider(color = parentColor.copy(alpha = 0.18f), thickness = 1.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(parentColor.copy(alpha = 0.07f)),
            content = stripContent
        )
    } else {
        Card(
            modifier  = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            shape     = RoundedCornerShape(OneMoneyTheme.dimens.largeRadius),
            colors    = CardDefaults.cardColors(containerColor = parentColor.copy(alpha = 0.08f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content   = stripContent
        )
    }
}

// ── Чип «Додати» ─────────────────────────────────────────────────────────────

@Composable
internal fun AddCategoryChip(onClick: () -> Unit, circleSize: Dp = OneMoneyTheme.dimens.addButtonSize) {
    val tokens = OneMoneyTheme.dimens
    val colors = OneMoneyTheme.colors
    Column(Modifier.testTag("new_category_cell").fillMaxWidth().clickable(onClick = onClick)
        .padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(circleSize).dashedCircleBorder(colors.addButtonStroke,
            strokeWidth = tokens.addButtonStrokeWidth,
            dashWidth = tokens.addButtonDashWidth, dashGap = tokens.addButtonDashGap),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, null, tint = colors.addButtonIcon, modifier = Modifier.size(24.dp))
        }
        Text(stringResource(R.string.categories_new), style = OneMoneyTheme.typography.categoryTitle,
            color = colors.secondaryText, maxLines = 2, textAlign = TextAlign.Center)
    }
}

private fun formatBudgetAmount(amount: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale.getDefault())
    nf.maximumFractionDigits = 0
    nf.minimumFractionDigits = 0
    return nf.format(amount)
}

// ── Donut-чарт ───────────────────────────────────────────────────────────────

@Composable
internal fun DonutChart(
    categories:   List<CategoryEntity>,
    spending:     Map<Long, Double>,
    totalExpense: Double,
    totalIncome:  Double,
    selectedTab:  Int,
    onToggle:     () -> Unit,
    modifier:     Modifier = Modifier
) {
    val colors = OneMoneyTheme.colors
    val typo   = OneMoneyTheme.typography

    val surfaceColor = MaterialTheme.colorScheme.surface
    val emptyColor   = colors.centerRing
    val expenseColor = colors.expensePink
    val incomeColor  = colors.incomeTeal

    val tabType = if (selectedTab == 0) TransactionType.EXPENSE else TransactionType.INCOME
    val activeSpending = categories
        .filter { it.type == tabType && !it.archived }
        .mapNotNull { cat -> (spending[cat.id] ?: 0.0).takeIf { it > 0.0 }?.let { cat to it } }
        .sortedByDescending { it.second }

    val tabTotal = activeSpending.sumOf { it.second }

    val dark = surfaceColor.luminance() < .5f
    val categoryColors = activeSpending.map { (cat, _) ->
        CategoryScreenTokens.resolve(cat.name, cat.type,
            parseColorHex(cat.colorHex, FallbackCategoryColor), false, dark).circleBg
    }
    val label = stringResource(if (selectedTab == 0) R.string.common_expenses else R.string.common_incomes)
    val amount = formatMoney(if (selectedTab == 0) totalExpense else totalIncome) + " ₴"
    Box(modifier = modifier.clickable(onClick = onToggle).semantics(mergeDescendants = true) { contentDescription = "$label: $amount" }, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.sizeIn(maxWidth = 200.dp, maxHeight = 240.dp).fillMaxSize()) {
            val minDim = size.minDimension
            val sw     = minDim * 0.10f
            val inset  = sw / 2f
            val arcDim = minDim - sw
            val arcSz  = Size(arcDim, arcDim)
            val tl     = Offset(
                x = (size.width  - minDim) / 2f + inset,
                y = (size.height - minDim) / 2f + inset
            )

            if (tabTotal == 0.0) {
                drawArc(
                    color      = emptyColor,
                    startAngle = -90f, sweepAngle = 360f,
                    useCenter  = false, topLeft = tl, size = arcSz,
                    style      = Stroke(width = sw)
                )
            } else {
                var startAngle = -90f
                activeSpending.forEachIndexed { idx, (_, amount) ->
                    val sweep = (amount / tabTotal * 360.0).toFloat()
                    drawArc(
                        color      = categoryColors[idx],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter  = false, topLeft = tl, size = arcSz,
                        style      = Stroke(width = sw, cap = StrokeCap.Butt)
                    )
                    startAngle += sweep
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = Spacing.xl)
        ) {
            Text(
                if (selectedTab == 0) stringResource(R.string.common_expenses) else stringResource(R.string.common_incomes),
                style      = typo.centerTitle.copy(letterSpacing = 0.sp),
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(amount, style = typo.centerAmount.copy(letterSpacing = 0.sp),
                color = if (tabTotal == 0.0) colors.secondaryText else if (selectedTab == 0) expenseColor else incomeColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis)

        }
    }
}

// ── Пунктирна кругла рамка ────────────────────────────────────────────────────

internal fun Modifier.dashedCircleBorder(
    color:       Color,
    dashWidth:   Dp = 8.dp,
    dashGap:     Dp = 5.dp,
    strokeWidth: Dp = 1.5.dp
): Modifier = this.drawBehind {
    val sw = strokeWidth.toPx()
    drawCircle(
        color  = color,
        radius = (size.minDimension - sw) / 2f,
        style  = Stroke(
            width      = sw,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(dashWidth.toPx(), dashGap.toPx()), 0f
            )
        )
    )
}
