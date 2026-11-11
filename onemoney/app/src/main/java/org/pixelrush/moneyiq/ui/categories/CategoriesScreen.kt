package org.syalosovetskyi.onemoney.ui.categories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.ui.budget.BudgetCatRow
import org.syalosovetskyi.onemoney.ui.budget.BudgetInputSheet
import org.syalosovetskyi.onemoney.ui.main.SharedMonthNavPill
import org.syalosovetskyi.onemoney.ui.main.monthPillLabel
import org.syalosovetskyi.onemoney.util.formatMoney
import org.syalosovetskyi.onemoney.ui.main.horizontalSwipe
import org.syalosovetskyi.onemoney.util.suggestCategoryStyle
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme

// ── Розміри чипів ─────────────────────────────────────────────────────────────

internal val CHIP_WIDTH           = 84.dp
internal val CHIP_WIDTH_COMPACT   = 82.dp
internal val CATEGORY_VERTICAL_GAP = 20.dp
internal val SUBCATEGORY_PANEL_WIDTH = 150.dp
internal val SUBCATEGORY_PANEL_HEIGHT = 76.dp

// ── Головний екран ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onNavigateBack:   () -> Unit    = {},
    embeddedMode:     Boolean       = false,
    padding:          PaddingValues = PaddingValues(),
    onViewCategoryTx: (CategoryEntity) -> Unit = {},
    onViewBudget:     () -> Unit    = {},
    viewModel:        CategoriesViewModel = hiltViewModel(),
    selectedTypeTab:  Int? = null,
    onSelectTypeTab:  (Int) -> Unit = {}
) {
    val state        by viewModel.state.collectAsState()
    var localTab by remember { mutableIntStateOf(0) }
    val selectedTab = selectedTypeTab ?: localTab
    val selectTab: (Int) -> Unit = { localTab = it; onSelectTypeTab(it) }

    var quickCategory      by remember { mutableStateOf<CategoryEntity?>(null) }
    var actionCategory     by remember { mutableStateOf<CategoryEntity?>(null) }
    var budgetCategory     by remember { mutableStateOf<CategoryEntity?>(null) }
    var editCategory       by remember { mutableStateOf<CategoryEntity?>(null) }
    var addSubcategoryTo   by remember { mutableStateOf<CategoryEntity?>(null) }
    var expandedCategoryId by remember { mutableStateOf<Long?>(null) }
    var showAddSheet       by remember { mutableStateOf(false) }
    var showQuickPicker by remember { mutableStateOf(false) }

    // Collapse double-click expansion when toggling subcategory view
    LaunchedEffect(state.showSubcategories) {
        if (state.showSubcategories) expandedCategoryId = null
    }

    val allCategoriesForTab = (if (selectedTab == 0) state.expenseCategories else state.incomeCategories)
        .filter { !it.archived }
    val spending   = if (selectedTab == 0) state.monthSpending else state.monthIncome
    val childCounts = allCategoriesForTab
        .filter { it.parentId != null && ((spending[it.id] ?: 0.0) > 0.0 || it.budgetAmount > 0.0) }
        .groupBy { it.parentId!! }
        .mapValues { it.value.size }
    val categories = if (!state.showSubcategories) {
        allCategoriesForTab.filter { it.parentId == null }
    } else {
        allCategoriesForTab.filter { it.parentId != null }
    }

    val effectiveSpending: Map<Long, Double> = if (!state.showSubcategories) {
        val result = spending.toMutableMap()
        allCategoriesForTab.filter { it.parentId != null }.forEach { child ->
            child.parentId?.let { pid ->
                result[pid] = (result[pid] ?: 0.0) + (spending[child.id] ?: 0.0)
            }
        }
        result
    } else spending
    val monthLabel = "${stringArrayResource(R.array.month_names)[state.selectedMonth.month]} ${state.selectedMonth.year}"

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

        if (selectedTypeTab == null) CategoryTypeSegment(selectedTab,
            Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm).fillMaxWidth(), selectTab)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CategoriesGridContent(
                categories            = categories,
                allCategoriesForTab   = allCategoriesForTab,
                spending              = effectiveSpending,
                totalExpense          = state.totalExpense,
                totalIncome           = state.totalIncome,
                selectedTab           = selectedTab,
                onToggleTab           = { selectTab(if (selectedTab == 0) 1 else 0) },
                bottomPadding         = padding.calculateBottomPadding(),
                onChipClick           = { cat -> quickCategory = cat },
                onChipLongClick       = { cat -> actionCategory = cat },
                onChipDoubleClick     = { id ->
                    expandedCategoryId = if (expandedCategoryId == id) null else id
                },
                expandedCategoryId    = expandedCategoryId,
                onAdd                 = { showAddSheet = true },
                showSubcategories     = state.showSubcategories,
                onToggleSubcategories = viewModel::toggleSubcategories,
                childCounts           = childCounts,
                sortBySpending        = false
            )
            CategoryQuickAddButton(selectedTab, modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = Spacing.lg, bottom = padding.calculateBottomPadding() + Spacing.lg)) {
                if (allCategoriesForTab.isEmpty()) showAddSheet = true else showQuickPicker = true
            }
        }
    }
    if (showQuickPicker) QuickCategoryPickerSheet(
        categories = allCategoriesForTab,
        selectedCategoryId = -1L,
        onSelect = { quickCategory = it; showQuickPicker = false },
        includeSubcategories = true,
        onDismiss = { showQuickPicker = false }
    )

    // ── Category action sheet (long press) ───────────────────────────────────
    actionCategory?.let { cat ->
        val catSpending = effectiveSpending[cat.id] ?: 0.0
        val catTotal    = if (cat.type == TransactionType.EXPENSE) state.totalExpense else state.totalIncome
        val catTxCount  = allCategoriesForTab
            .filter { it.id == cat.id || it.parentId == cat.id }
            .sumOf { state.monthTxCounts[it.id] ?: 0 }
        CategoryActionSheet(
            category      = cat,
            spending      = catSpending,
            txCount       = catTxCount,
            totalInPeriod = catTotal,
            pillLabel     = monthPillLabel(state.appMonth),
            onEdit        = { actionCategory = null; editCategory = cat },
            onBudget      = { actionCategory = null; budgetCategory = cat },
            onOperations  = { actionCategory = null; onViewCategoryTx(cat) },
            onDismiss     = { actionCategory = null }
        )
    }

    budgetCategory?.let { cat ->
        val catAmount = effectiveSpending[cat.id] ?: 0.0
        BudgetInputSheet(
            catRow      = BudgetCatRow(cat, catAmount),
            monthLabel  = monthLabel,
            accentColor = if (cat.type == TransactionType.INCOME) OneMoneyTheme.colors.budgetIncome else OneMoneyTheme.colors.budgetExpense,
            amountLabel = if (cat.type == TransactionType.INCOME) stringResource(R.string.budget_received) else stringResource(R.string.budget_spent),
            onDismiss   = { budgetCategory = null },
            onConfirm   = { newBudget, _ ->
                viewModel.update(cat.copy(budgetAmount = newBudget))
                budgetCategory = null
            }
        )
    }

    // ── Quick expense / income sheet ─────────────────────────────────────────
    quickCategory?.let { cat ->
        val allCats = (state.expenseCategories + state.incomeCategories).filter { !it.archived }
        QuickExpenseSheet(
            category   = cat,
            categories = allCats,
            accounts   = state.accounts,
            onSave     = { accountId, amount, note, date, repeatMode, reminderMode, categoryId ->
                val saveCategory = allCats.firstOrNull { it.id == categoryId } ?: cat
                viewModel.recordTransaction(accountId, saveCategory, amount, note, date, repeatMode, reminderMode)
                quickCategory = null
            },
            onDismiss  = { quickCategory = null }
        )
    }

    // ── Редагування категорії ────────────────────────────────────────────────
    editCategory?.let { cat ->
        val catChildren = allCategoriesForTab.filter { it.parentId == cat.id }
        CategoryFormSheet(
            existing             = cat,
            children             = catChildren,
            onAddSubcategory     = if (cat.parentId == null) ({ addSubcategoryTo = cat }) else null,
            onDetachSubcategory  = if (cat.parentId == null) ({ child ->
                viewModel.update(child.copy(parentId = null))
            }) else null,
            onDeleteSubcategory  = if (cat.parentId == null) ({ child ->
                viewModel.delete(child)
            }) else null,
            parentOptions        = allCategoriesForTab.filter { it.parentId == null && it.id != cat.id },
            onChangeParent       = if (cat.parentId != null) ({ newParentId ->
                viewModel.update(cat.copy(parentId = newParentId))
            }) else null,
            defaultType          = cat.type,
            onSave               = { name, type, color, icon, budget, period, archived, currency ->
                viewModel.update(cat.copy(
                    name         = name,
                    type         = type,
                    colorHex     = color,
                    icon         = icon,
                    budgetAmount = budget,
                    budgetPeriod = period,
                    archived     = archived,
                    currencyCode = currency
                ))
                editCategory = null
            },
            onDelete  = { viewModel.delete(cat); editCategory = null },
            onDismiss = { editCategory = null }
        )
    }

    // ── Форма нової підкатегорії ─────────────────────────────────────────────
    addSubcategoryTo?.let { parent ->
        CategoryFormSheet(
            existing    = null,
            forParentId = parent.id,
            defaultType = parent.type,
            onSave      = { name, type, color, icon, budget, period, _, currency ->
                viewModel.add(name, type, color, icon, budget, period, currency, parentId = parent.id)
                addSubcategoryTo = null
            },
            onDismiss   = { addSubcategoryTo = null }
        )
    }

    // ── Форма нової категорії ────────────────────────────────────────────────
    if (showAddSheet) {
        val defaultType = if (selectedTab == 0) TransactionType.EXPENSE else TransactionType.INCOME
        CategoryFormSheet(
            existing    = null,
            defaultType = defaultType,
            onSave      = { name, type, color, icon, budget, period, _, currency ->
                viewModel.add(name, type, color, icon, budget, period, currency)
                showAddSheet = false
            },
            onDismiss   = { showAddSheet = false }
        )
    }
}

@Composable
private fun CategoryGridSlot(
    category:          CategoryEntity?,
    spending:          Map<Long, Double>,
    displayBudgets:    Map<Long, Double>,
    childCounts:       Map<Long, Int>,
    parentColors:      Map<Long, String>,
    expandedId:        Long?,
    showChildBadge:    Boolean,
    isCompact:         Boolean,
    inlineStripShown:  Boolean = false,
    suppressLongPress: Boolean = false,
    extraModifier:     Modifier = Modifier,
    chipWidth:         Dp = if (isCompact) CHIP_WIDTH_COMPACT else CHIP_WIDTH,
    chipHeight:        Dp? = null,
    circleSize:        Dp? = null,
    onChipClick:       (CategoryEntity) -> Unit,
    onChipLongClick:   (CategoryEntity) -> Unit,
    onChipDoubleClick: (Long?) -> Unit
) {
    Box(
        Modifier.width(chipWidth).then(extraModifier),
        contentAlignment = Alignment.Center
    ) {
        if (category != null) {
            CategoryChip(
                category       = category,
                spending       = spending[category.id] ?: 0.0,
                onClick        = { onChipClick(category) },
                childCount     = childCounts[category.id] ?: 0,
                onLongPress    = if (suppressLongPress) null else ({ onChipLongClick(category) }),
                onDoubleClick  = {
                    if ((childCounts[category.id] ?: 0) > 0) onChipDoubleClick(category.id)
                    else onChipClick(category)
                },
                showChildBadge = showChildBadge,
                groupColorHex  = parentColors[category.id],
                isCompact      = isCompact,
                isExpanded     = category.id == expandedId,
                budgetOverride = displayBudgets[category.id],
                flatBottom     = category.id == expandedId && inlineStripShown,
                overrideWidth  = chipWidth,
                overrideHeight = chipHeight,
                overrideCircle = circleSize,
            )
        }
    }
}

@Composable
internal fun CategoriesGridContent(
    categories:            List<CategoryEntity>,
    allCategoriesForTab:   List<CategoryEntity>     = emptyList(),
    spending:              Map<Long, Double>,
    totalExpense:          Double,
    totalIncome:           Double,
    selectedTab:           Int,
    onToggleTab:           () -> Unit,
    bottomPadding:         Dp,
    onChipClick:           (CategoryEntity) -> Unit,
    onChipLongClick:       (CategoryEntity) -> Unit = {},
    onChipDoubleClick:     (Long?) -> Unit          = {},
    expandedCategoryId:    Long?                    = null,
    onAdd:                 () -> Unit,
    showSubcategories:     Boolean                  = false,
    onToggleSubcategories: () -> Unit               = {},
    childCounts:           Map<Long, Int>           = emptyMap(),
    sortBySpending:        Boolean                          = true,
    chipExtraModifier:     (CategoryEntity?) -> Modifier    = { Modifier },
    onChipDragSwap:        ((Long, Long) -> Unit)?          = null
) {
    val sorted: List<CategoryEntity> = if (sortBySpending)
        categories.sortedByDescending { spending[it.id] ?: 0.0 }
    else
        categories  // DAO returns sortOrder ASC — preserve that order

    val parentColors: Map<Long, String> = if (showSubcategories) {
        val parentMap = allCategoriesForTab.filter { it.parentId == null }.associateBy { it.id }
        categories.mapNotNull { child ->
            parentMap[child.parentId]?.let { child.id to it.colorHex }
        }.toMap()
    } else emptyMap()

    val displayBudgets: Map<Long, Double> = if (!showSubcategories) {
        val childBudgets = allCategoriesForTab
            .filter { it.parentId != null && !it.archived && it.budgetAmount > 0.0 }
            .groupBy { it.parentId!! }
            .mapValues { (_, children) -> children.sumOf { it.budgetAmount } }
        categories.associate { cat ->
            cat.id to (cat.budgetAmount + (childBudgets[cat.id] ?: 0.0))
        }.filterValues { it > 0.0 }
    } else emptyMap()

    // Keep all categories visible and colored, including zero-activity periods.
    val display = if (sortBySpending) sorted else categories

    // ── Drag-to-swap (стан-машина винесена в CategoryChipDrag.kt) ─────────────
    val dragState = rememberChipDragState(onChipDragSwap)
    val effectiveChipModifier: (CategoryEntity?) -> Modifier = { cat ->
        chipExtraModifier(cat).then(dragState?.chipModifier(cat) ?: Modifier)
    }

    val expandedCat = display.firstOrNull { it.id == expandedCategoryId && !showSubcategories }
    val expandedChildren = expandedCat?.let { parent ->
        allCategoriesForTab.filter { !it.archived && it.parentId == parent.id &&
            it.name.trim().lowercase() != parent.name.trim().lowercase() }
    }.orEmpty()
    val hasExpandedStrip = expandedCat != null && expandedChildren.isNotEmpty()
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { pos ->
        dragState?.let { it.containerRootPos = pos.positionInRoot() }
    }) {
        val columns = categoryColumnCount(maxWidth)
        val chipW = (maxWidth - 8.dp - 6.dp * (columns - 1)) / columns
        val chipHeight = if (columns == 6) 132.dp else 112.dp
        val circleSize = if (columns == 6) 64.dp else 56.dp
        val cells = categoryGridCells(columns, display.size + 1)
        val expandedRow = display.indexOfFirst { it.id == expandedCategoryId }
            .takeIf { it >= 0 }?.let { cells[it].row }
        // A strip in the orbital band follows both ring rows, keeping the ring intact.
        val stripAfter = expandedRow?.let { if (it in 1..2) 2 else it }
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("category_grid_$columns"),
            contentPadding = PaddingValues(top = Spacing.sm, bottom = bottomPadding + 80.dp)
        ) {
            if (categories.isEmpty()) item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.cat_empty))
                    Text(stringResource(R.string.cat_empty_hint), color = OneMoneyTheme.colors.secondaryText)
                }
            }
            if ((if (selectedTab == 0) totalExpense else totalIncome) == 0.0) item {
                Text(stringResource(R.string.categories_empty_period),
                    color = OneMoneyTheme.colors.secondaryText,
                    modifier = Modifier.padding(Spacing.lg))
            }
            item(key = "category_cells") {
                Layout(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    content = {
                        display.forEach { cat ->
                            CategoryGridSlot(
                                category = cat, spending = spending, displayBudgets = displayBudgets,
                                childCounts = childCounts, parentColors = parentColors,
                                expandedId = expandedCategoryId, showChildBadge = !showSubcategories,
                                isCompact = false, inlineStripShown = hasExpandedStrip,
                                suppressLongPress = onChipDragSwap != null,
                                extraModifier = effectiveChipModifier(cat), chipWidth = chipW,
                                chipHeight = chipHeight, circleSize = circleSize,
                                onChipClick = onChipClick, onChipLongClick = onChipLongClick,
                                onChipDoubleClick = onChipDoubleClick
                            )
                        }
                        Box(Modifier.width(chipW).heightIn(min = chipHeight), contentAlignment = Alignment.TopCenter) {
                            AddCategoryChip(onClick = onAdd, circleSize = circleSize)
                        }
                        val donutCats = if (hasExpandedStrip) expandedChildren else categories
                        DonutChart(
                            categories = donutCats, spending = spending,
                            totalExpense = if (hasExpandedStrip && selectedTab == 0)
                                expandedChildren.sumOf { spending[it.id] ?: 0.0 } else totalExpense,
                            totalIncome = if (hasExpandedStrip && selectedTab == 1)
                                expandedChildren.sumOf { spending[it.id] ?: 0.0 } else totalIncome,
                            selectedTab = selectedTab, onToggle = onToggleTab
                        )
                        if (hasExpandedStrip) Column { ExpandedCategoryStrip(
                            parent = expandedCat!!, children = expandedChildren, spending = spending,
                            onClickParent = { onChipClick(expandedCat!!) }, onClickChild = onChipClick,
                            onLongClickChild = onChipLongClick, inline = true
                        ) }
                    }
                ) { measurables, constraints ->
                    val gap = 6.dp.roundToPx()
                    val rowGap = CATEGORY_VERTICAL_GAP.roundToPx()
                    val width = (constraints.maxWidth - gap * (columns - 1)) / columns
                    val count = display.size + 1
                    val measured = measurables.take(count).map {
                        it.measure(Constraints(minWidth = width, maxWidth = width))
                    }
                    val rowCount = maxOf(3, cells.maxOf { it.row } + 1)
                    val heights = IntArray(rowCount) { chipHeight.roundToPx() }
                    measured.forEachIndexed { i, placeable ->
                        heights[cells[i].row] = maxOf(heights[cells[i].row], placeable.height)
                    }
                    val strip = if (hasExpandedStrip) measurables[count + 1]
                        .measure(Constraints(minWidth = constraints.maxWidth, maxWidth = constraints.maxWidth)) else null
                    val ys = IntArray(rowCount)
                    for (row in 1 until rowCount) ys[row] = ys[row - 1] + heights[row - 1] + rowGap +
                        (if (stripAfter == row - 1) strip?.height ?: 0 else 0)
                    val ringWidth = width * 2 + gap
                    val ringHeight = heights[1] + heights[2] + rowGap
                    val ring = measurables[count].measure(Constraints.fixed(ringWidth, ringHeight))
                    val totalHeight = ys.last() + heights.last() +
                        (if (stripAfter == rowCount - 1) strip?.height ?: 0 else 0)
                    layout(constraints.maxWidth, totalHeight) {
                        measured.forEachIndexed { i, placeable ->
                            placeable.placeRelative(cells[i].column * (width + gap), ys[cells[i].row])
                        }
                        ring.placeRelative((columns / 2 - 1) * (width + gap), ys[1])
                        if (strip != null && stripAfter != null)
                            strip.placeRelative(0, ys[stripAfter] + heights[stripAfter])
                    }
                }
            }
        }
        val dragId = dragState?.draggingId
        val draggingCat = display.firstOrNull { it.id == dragId }
        val center = dragId?.let { dragState?.chipCenters?.get(it) }
        if (draggingCat != null && center != null && dragState != null) {
            val density = LocalDensity.current
            Box(Modifier.offset {
                IntOffset(
                    (center.x - dragState.containerRootPos.x + dragState.dragOffset.x).toInt() - with(density) { chipW.roundToPx() } / 2,
                    (center.y - dragState.containerRootPos.y + dragState.dragOffset.y).toInt() - with(density) { chipHeight.roundToPx() } / 2
                )
            }.width(chipW).shadow(8.dp, RoundedCornerShape(OneMoneyTheme.dimens.cardRadius))) {
                CategoryChip(category = draggingCat, spending = spending[draggingCat.id] ?: 0.0,
                    onClick = {}, overrideWidth = chipW, overrideHeight = chipHeight, overrideCircle = circleSize)
            }
        }
    }
}

internal fun categoryColumnCount(width: Dp): Int = if (width < 600.dp) 4 else 6

internal data class CategoryGridCell(val column: Int, val row: Int)

/** Row-major order, reserving the central 2 x 2 cells for the ring. Includes Add. */
internal fun categoryGridCells(columns: Int, count: Int): List<CategoryGridCell> {
    require(columns == 4 || columns == 6)
    val result = mutableListOf<CategoryGridCell>()
    val center = columns / 2 - 1
    var row = 0
    while (result.size < count) {
        for (column in 0 until columns) {
            if (row in 1..2 && column in center..center + 1) continue
            if (result.size < count) result += CategoryGridCell(column, row)
        }
        row++
    }
    return result
}
