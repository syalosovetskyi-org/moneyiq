package org.syalosovetskyi.onemoney.ui.budget

import org.syalosovetskyi.onemoney.util.parseColorHex
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.ui.main.SharedMonthNavPill
import org.syalosovetskyi.onemoney.util.formatMoney
import org.syalosovetskyi.onemoney.ui.main.horizontalSwipe
import org.syalosovetskyi.onemoney.ui.categories.categoryIconFor
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.util.suggestCategoryStyle

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun BudgetScreen(
    padding:           PaddingValues  = PaddingValues(),
    embeddedMode:      Boolean        = false,
    showSettings:      Boolean        = false,
    onSettingsDismiss: () -> Unit     = {},
    viewModel:         BudgetViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    val appColors           = OneMoneyTheme.colors
    val expenseColor        = appColors.budgetExpense
    val incomeColor         = appColors.budgetIncome
    val monthLabel          = "${stringArrayResource(R.array.month_names)[state.selectedMonth.month]} ${state.selectedMonth.year}"
    var confirmDeleteBudget by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showSettingsSheet      by remember { mutableStateOf(false) }
    var currentExpensesMode   by remember { mutableStateOf(false) }
    val settingsVisible        = showSettings || showSettingsSheet
    var showIncomeBudgetSheet  by remember { mutableStateOf(false) }
    var incomeCatToEdit        by remember { mutableStateOf<BudgetCatRow?>(null) }

    val effectiveIncomeBudget = state.incomeSection.totalBudget

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = if (embeddedMode) 0.dp else padding.calculateTopPadding())
            .horizontalSwipe(
                onSwipeLeft  = viewModel::nextMonth,
                onSwipeRight = viewModel::prevMonth
            )
    ) {
        SharedMonthNavPill(
            appMonth       = state.appMonth,
            daysInPeriod   = state.daysInMonth,
            onPrev         = viewModel::prevMonth,
            onNext         = viewModel::nextMonth,
            onSelectPeriod = viewModel::setPeriod
        )

        LazyColumn(
            modifier       = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Spacing.sm)
        ) {
            item {
                BudgetSectionCard(
                    data                = state.expenseSection,
                    title               = stringResource(R.string.common_expenses),
                    amountLabel         = stringResource(R.string.budget_spent),
                    accentColor         = expenseColor,
                    monthLabel          = monthLabel,
                    currentExpensesMode = currentExpensesMode,
                    onUpdateBudget      = viewModel::updateCategoryBudget
                )
            }
            item { Spacer(Modifier.height(Spacing.xs)) }
            item {
                SavingsSectionCard(
                    expenseTotal  = state.expenseSection.totalAmount,
                    incomeTotal   = state.incomeSection.totalAmount,
                    incomeBudget  = effectiveIncomeBudget,
                    expenseBudget = state.expenseSection.totalBudget,
                    daysInMonth   = state.daysInMonth,
                    daysPassed    = state.daysPassed
                )
            }
            item { Spacer(Modifier.height(Spacing.xs)) }
            item {
                BudgetSectionCard(
                    data                = state.incomeSection,
                    title               = stringResource(R.string.common_incomes),
                    amountLabel         = stringResource(R.string.budget_received),
                    accentColor         = incomeColor,
                    monthLabel          = monthLabel,
                    currentExpensesMode = currentExpensesMode,
                    incomeMode          = true,
                    onUpdateBudget      = viewModel::updateCategoryBudget
                )
            }
        }
        IncomeBudgetBar(
            effectiveIncomeBudget   = effectiveIncomeBudget,
            expenseTotal            = state.expenseSection.totalAmount,
            hasIncomeCategories     = state.incomeSection.rows.isNotEmpty(),
            onClick                 = {
                val rows = state.incomeSection.rows
                if (rows.size == 1) incomeCatToEdit = rows.first()
                else showIncomeBudgetSheet = true
            },
            modifier                = Modifier.padding(bottom = padding.calculateBottomPadding())
        )
    }

    if (showIncomeBudgetSheet) {
        IncomeCategoryPickerSheet(
            rows        = state.incomeSection.rows,
            monthLabel  = monthLabel,
            accentColor = incomeColor,
            onPick      = { row ->
                incomeCatToEdit       = row
                showIncomeBudgetSheet = false
            },
            onDismiss   = { showIncomeBudgetSheet = false }
        )
    }

    incomeCatToEdit?.let { row ->
        BudgetInputSheet(
            catRow      = row,
            monthLabel  = monthLabel,
            accentColor = incomeColor,
            amountLabel = stringResource(R.string.budget_received),
            onIconClick = { incomeCatToEdit = null; showIncomeBudgetSheet = true },
            onDismiss   = { incomeCatToEdit = null },
            onConfirm   = { newBudget, currency ->
                viewModel.updateCategoryBudget(row.category, newBudget, currency)
                incomeCatToEdit = null
            }
        )
    }

    if (settingsVisible) {
        BudgetSettingsSheet(
            monthLabel          = monthLabel,
            currentExpensesMode = currentExpensesMode,
            onToggleMode        = { currentExpensesMode = it },
            onDeleteBudget      = { confirmDeleteBudget = true; showSettingsSheet = false; onSettingsDismiss() },
            onDismiss           = { showSettingsSheet = false; onSettingsDismiss() }
        )
    }
    if (confirmDeleteBudget) {
        org.syalosovetskyi.onemoney.ui.components.dialogs.ConfirmationDialog(
            title = stringResource(R.string.budget_delete_confirm_title),
            message = stringResource(R.string.budget_delete_confirm_message),
            onConfirm = { viewModel.clearAllBudgets(); confirmDeleteBudget = false },
            onDismiss = { confirmDeleteBudget = false }
        )
    }

}

// ── Іконка категорії з авто-підказкою ────────────────────────────────────────

internal fun resolvedCatIcon(iconName: String, catName: String, type: TransactionType) =
    categoryIconFor(
        if (iconName == "category") suggestCategoryStyle(catName, type).first else iconName
    )

// ── Секция бюджета (Расходы / Доходы) ────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetSectionCard(
    data:                BudgetSectionData,
    title:               String,
    amountLabel:         String,
    accentColor:         Color,
    monthLabel:          String,
    currentExpensesMode: Boolean = false,
    incomeMode:          Boolean = false,
    onUpdateBudget:      (CategoryEntity, Double, String) -> Unit
) {
    var expanded    by remember { mutableStateOf(false) }
    var editingRow  by remember { mutableStateOf<BudgetCatRow?>(null) }

    val budgetedRows = when {
        incomeMode          -> emptyList()
        currentExpensesMode -> emptyList()
        else                -> data.rows.filter { it.category.budgetAmount > 0 }
    }
    val chipRows = when {
        incomeMode          -> data.rows.filter { it.amount > 0 || it.category.budgetAmount > 0 }
                                  .sortedByDescending { it.amount }
        currentExpensesMode -> data.rows.filter { it.amount > 0 }.sortedByDescending { it.amount }
        else                -> data.rows.filter { it.category.budgetAmount == 0.0 && it.amount > 0 }
    }
    val visibleChips    = if (expanded) chipRows else chipRows.take(3)
    val hasMoreChips    = chipRows.size > 3
    val hiddenChipTotal = chipRows.drop(3).sumOf { it.amount }
    val remaining       = data.totalBudget - data.totalAmount

    editingRow?.let { row ->
        BudgetInputSheet(
            catRow      = row,
            monthLabel  = monthLabel,
            accentColor = accentColor,
            amountLabel = amountLabel,
            onDismiss   = { editingRow = null },
            onConfirm   = { newBudget, currency ->
                onUpdateBudget(row.category, newBudget, currency)
                editingRow = null
            }
        )
    }

    // Трек прогресу секції: у світлій темі — білий (як в оригіналі),
    // у темній — тёмна поверхня. Захоплюємо тут, бо DrawScope не бачить MaterialTheme.
    val trackColor = MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(accentColor.copy(alpha = 0.06f))
    ) {
        // ── Section header ────────────────────────────────────────────────
        val headerProgress = if (data.totalBudget > 0.0)
            (data.totalAmount / data.totalBudget).coerceIn(0.0, 1.0).toFloat()
        else 0f

        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .drawBehind {
                    drawRect(trackColor)
                    drawRect(
                        color = accentColor.copy(alpha = 0.20f),
                        size  = Size(size.width * headerProgress, size.height)
                    )
                }
        ) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accentColor))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = Spacing.md)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(title,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "${formatMoney(remaining)} ₴",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        color = when {
                            remaining < 0   -> MaterialTheme.colorScheme.error
                            remaining > 0   -> accentColor
                            else            -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        }
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$amountLabel ${formatMoney(data.totalAmount)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (data.totalAmount > 0) accentColor
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                    Text(stringResource(R.string.budget_in_budget, formatMoney(data.totalBudget)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                }
            }
        }

        // ── Budgeted categories — full rows ──────────────────────────────
        budgetedRows.forEach { row ->
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            BudgetCatFullRow(
                row         = row,
                accentColor = accentColor,
                onClick     = { editingRow = row }
            )
        }

        // ── Chip-row for unbudgeted (or all in currentExpensesMode) ──────
        if (chipRows.isNotEmpty()) {
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            val rowBg = Modifier
                .fillMaxWidth()
                .background(trackColor)
            if (!expanded) {
                Row(modifier = rowBg) {
                    visibleChips.forEach { row ->
                        Box(Modifier.weight(1f)) {
                            BudgetCatChip(row = row, accentColor = accentColor, onClick = { editingRow = row })
                        }
                    }
                    if (hasMoreChips) {
                        Box(Modifier.weight(1f)) {
                            MoreLessChip(
                                expanded    = false,
                                hiddenTotal = hiddenChipTotal,
                                onClick     = { expanded = true }
                            )
                        }
                    }
                }
            } else {
                val totalItems = chipRows.size + 1
                Column(modifier = rowBg) {
                    (0 until totalItems).chunked(4).forEach { indices ->
                        Row(Modifier.fillMaxWidth()) {
                            indices.forEach { i ->
                                Box(Modifier.weight(1f)) {
                                    if (i < chipRows.size) {
                                        val r = chipRows[i]
                                        BudgetCatChip(row = r, accentColor = accentColor, onClick = { editingRow = r })
                                    } else {
                                        MoreLessChip(expanded = true, hiddenTotal = 0.0, onClick = { expanded = false })
                                    }
                                }
                            }
                            repeat(4 - indices.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

// ── Full row for a budgeted category ─────────────────────────────────────────

@Composable
private fun BudgetCatFullRow(
    row:         BudgetCatRow,
    accentColor: Color,
    onClick:     () -> Unit
) {
    val color     = remember(row.category.colorHex) {
        parseColorHex(row.category.colorHex, accentColor)
    }
    val remaining = row.budgetForPeriod - row.amount
    val isOver    = remaining < 0
    val progress  = if (row.budgetForPeriod > 0.0)
        (row.amount / row.budgetForPeriod).coerceIn(0.0, 1.0).toFloat()
    else 0f
    val trackColor = MaterialTheme.colorScheme.surface

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (isOver) {
                    drawRect(color = color.copy(alpha = 0.12f))
                } else {
                    drawRect(color = trackColor)
                    drawRect(
                        color = color.copy(alpha = 0.18f),
                        size  = Size(size.width * progress, size.height)
                    )
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon circle + spending below
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier         = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    resolvedCatIcon(row.category.icon, row.category.name, row.category.type), null,
                    tint     = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "${formatMoney(row.amount)} ₴",
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color      = color
            )
        }

        Spacer(Modifier.width(Spacing.md))

        // Category name
        Text(
            row.category.name,
            modifier   = Modifier.weight(1f),
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onSurface,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis
        )

        // Right: remaining/badge + budget (bold number)
        Column(horizontalAlignment = Alignment.End) {
            if (isOver) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accentColor
                ) {
                    Text(
                        "${formatMoney(-remaining)} ₴",
                        modifier   = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White
                    )
                }
            } else {
                Text(
                    "${formatMoney(remaining)} ₴",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = accentColor
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.ovr_in_budget) + " ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
                Text(
                    "${formatMoney(row.budgetForPeriod)} ₴",
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        }
    }
}

// ── Чип отдельной категории ───────────────────────────────────────────────────

@Composable
private fun BudgetCatChip(
    row:         BudgetCatRow,
    accentColor: Color,
    onClick:     () -> Unit
) {
    val color = remember(row.category.colorHex) {
        parseColorHex(row.category.colorHex, accentColor)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs, horizontal = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            row.category.name,
            style     = MaterialTheme.typography.labelSmall,
            maxLines  = 2,
            overflow  = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onSurface,
            modifier  = Modifier.fillMaxWidth().heightIn(min = 32.dp),
            lineHeight = MaterialTheme.typography.labelSmall.lineHeight
        )
        Spacer(Modifier.height(2.dp))
        val hasAmount = row.amount > 0
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(if (hasAmount) color else color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                resolvedCatIcon(row.category.icon, row.category.name, row.category.type), null,
                tint     = if (hasAmount) Color.White else color,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "${formatMoney(row.amount)} ₴",
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color      = color,
            maxLines   = 1,
            textAlign  = TextAlign.Center,
            modifier   = Modifier.fillMaxWidth()
        )
    }
}

// ── Чип «Больше...» / «Свернуть» ─────────────────────────────────────────────

@Composable
private fun MoreLessChip(expanded: Boolean, hiddenTotal: Double = 0.0, onClick: () -> Unit) {
    val surfaceVar = MaterialTheme.colorScheme.surfaceVariant
    val onSurfVar  = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (expanded) stringResource(R.string.budget_collapse) else stringResource(R.string.budget_more),
            style     = MaterialTheme.typography.labelSmall,
            color     = onSurfVar,
            maxLines  = 2,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth().heightIn(min = 32.dp)
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(surfaceVar),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null,
                tint     = onSurfVar,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            if (!expanded && hiddenTotal > 0.0) "${formatMoney(hiddenTotal)} ₴" else " ",
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color      = onSurfVar,
            textAlign  = TextAlign.Center,
            modifier   = Modifier.fillMaxWidth()
        )
    }
}

// ── Секция заощаджень з прогнозом ────────────────────────────────────────────

@Composable
private fun SavingsSectionCard(
    expenseTotal:  Double,
    incomeTotal:   Double,
    incomeBudget:  Double,
    expenseBudget: Double,
    daysInMonth:   Int,
    daysPassed:    Int
) {
    val appColors    = OneMoneyTheme.colors
    val savingsColor = appColors.budgetIncome
    val negColor     = appColors.budgetExpense

    // Реальні заощадження = отримано - витрачено (для підпису «збережено»)
    val realSavings = incomeTotal - expenseTotal

    // Заощадження за бюджетом = бюджет доходів - витрачено (для заголовку)
    val actualSavings = if (incomeBudget > 0) incomeBudget - expenseTotal else realSavings

    // Прогноз витрат на кінець місяця (лінійна екстраполяція по днях)
    val hasForecast = org.syalosovetskyi.onemoney.util.canForecast(incomeBudget, incomeTotal, expenseTotal, daysPassed, daysInMonth)
    val projectedExpenses = if (hasForecast) expenseTotal / daysPassed * daysInMonth else expenseTotal
    val projectedSavings  = if (incomeBudget > 0) incomeBudget - projectedExpenses
                            else realSavings - (projectedExpenses - expenseTotal)

    // Бюджетні заощадження = бюджет доходів - бюджет витрат
    val budgetSavings = incomeBudget - expenseBudget

    // Що показуємо в заголовку: прогноз (якщо є) або бюджетні заощадження
    val headerAmount = if (hasForecast && (incomeBudget > 0 || incomeTotal > 0)) projectedSavings
                       else actualSavings
    val headerColor  = if (headerAmount >= 0) savingsColor else negColor

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(savingsColor.copy(alpha = 0.06f))
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(savingsColor))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = Spacing.md)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            stringResource(R.string.budget_savings),
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onSurface
                        )
                        if (hasForecast) {
                            Text(
                                stringResource(R.string.budget_days_passed, daysPassed, daysInMonth),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${formatMoney(headerAmount)} ₴",
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color      = headerColor
                        )
                        if (hasForecast) {
                            Text(
                                stringResource(R.string.budget_forecast),
                                style = MaterialTheme.typography.labelSmall,
                                color = headerColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.xs))

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (incomeTotal > 0) {
                        Text(
                            stringResource(R.string.budget_saved, formatMoney(realSavings)),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (realSavings >= 0) savingsColor.copy(alpha = 0.8f)
                                    else negColor.copy(alpha = 0.8f)
                        )
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    if (hasForecast && expenseTotal > 0) {
                        Text(
                            stringResource(R.string.budget_spent_to_end, formatMoney(projectedExpenses)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    } else if (budgetSavings != 0.0 && incomeBudget > 0) {
                        Text(
                            stringResource(R.string.budget_in_budget, formatMoney(incomeBudget)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

// ── Нижня панель доходу (prompt → доступно в бюджеті) ────────────────────────

@Composable
private fun IncomeBudgetBar(
    effectiveIncomeBudget: Double,
    expenseTotal:          Double,
    hasIncomeCategories:   Boolean,
    onClick:               () -> Unit,
    modifier:              Modifier = Modifier
) {
    val hasBudget      = effectiveIncomeBudget > 0.0
    val overspend      = expenseTotal - effectiveIncomeBudget
    val overspendColor = OneMoneyTheme.colors.budgetExpense

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (hasIncomeCategories) Modifier.clickable(onClick = onClick) else Modifier),
        color = if (hasBudget && overspend > 0)
                    overspendColor.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
            contentAlignment = Alignment.Center
        ) {
            when {
                hasBudget && overspend > 0 -> {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.budget_overspend) + "  ",
                            style     = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color     = overspendColor
                        )
                        Text(
                            "${formatMoney(overspend)} ₴",
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color      = overspendColor
                        )
                    }
                }
                hasBudget -> {
                    val available = effectiveIncomeBudget - expenseTotal
                    Text(
                        stringResource(R.string.budget_available, formatMoney(available)),
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign  = TextAlign.Center,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                }
                else -> {
                    Text(
                        stringResource(R.string.budget_income_prompt),
                        style     = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
                    )
                }
            }
        }
    }
}
