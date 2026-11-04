package org.syalosovetskyi.onemoney.ui.categories

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
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
internal val CHIP_HEIGHT          = 136.dp
internal val CHIP_CIRCLE_SIZE     = 50.dp
internal val CHIP_WIDTH_COMPACT   = 82.dp
internal val CHIP_HEIGHT_COMPACT  = 112.dp
internal val CHIP_CIRCLE_COMPACT  = 40.dp
internal val CATEGORY_VERTICAL_GAP = 20.dp
internal val DONUT_SECTION_HEIGHT = (CHIP_HEIGHT * 2 + CATEGORY_VERTICAL_GAP)
internal val SUBCATEGORY_PANEL_WIDTH = 150.dp
internal val SUBCATEGORY_PANEL_HEIGHT = 76.dp

// ── Головний екран ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onNavigateBack:   () -> Unit    = {},
    embeddedMode:     Boolean       = false,
    padding:          PaddingValues = PaddingValues(),
    isCompact:        Boolean       = false,
    onViewCategoryTx: (CategoryEntity) -> Unit = {},
    onViewBudget:     () -> Unit    = {},
    viewModel:        CategoriesViewModel = hiltViewModel()
) {
    val state        by viewModel.state.collectAsState()
    var selectedTab  by remember { mutableIntStateOf(0) }

    var quickCategory      by remember { mutableStateOf<CategoryEntity?>(null) }
    var actionCategory     by remember { mutableStateOf<CategoryEntity?>(null) }
    var budgetCategory     by remember { mutableStateOf<CategoryEntity?>(null) }
    var editCategory       by remember { mutableStateOf<CategoryEntity?>(null) }
    var addSubcategoryTo   by remember { mutableStateOf<CategoryEntity?>(null) }
    var expandedCategoryId by remember { mutableStateOf<Long?>(null) }
    var showAddSheet       by remember { mutableStateOf(false) }

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

        CategoriesGridContent(
            categories            = categories,
            allCategoriesForTab   = allCategoriesForTab,
            spending              = effectiveSpending,
            totalExpense          = state.totalExpense,
            totalIncome           = state.totalIncome,
            selectedTab           = selectedTab,
            onToggleTab           = { selectedTab = if (selectedTab == 0) 1 else 0 },
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
            isCompact             = isCompact,
            sortBySpending        = false
        )
    }

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
private fun CategoryGridRow(
    rowCats:           List<CategoryEntity?>,
    spending:          Map<Long, Double>,
    displayBudgets:    Map<Long, Double>,
    childCounts:       Map<Long, Int>,
    parentColors:      Map<Long, String>,
    expandedId:        Long?,
    showChildBadge:    Boolean,
    isCompact:         Boolean,
    inlineStripShown:  Boolean = false,
    suppressLongPress: Boolean = false,
    extraChipModifier: (CategoryEntity?) -> Modifier = { Modifier },
    chipWidth:         Dp = if (isCompact) CHIP_WIDTH_COMPACT else CHIP_WIDTH,
    chipHeight:        Dp? = null,
    circleSize:        Dp? = null,
    onChipClick:       (CategoryEntity) -> Unit,
    onChipLongClick:   (CategoryEntity) -> Unit,
    onChipDoubleClick: (Long?) -> Unit,
    modifier:          Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top
    ) {
        repeat(4) { i ->
            val cat = rowCats.getOrNull(i)
            CategoryGridSlot(
                category          = cat,
                spending          = spending,
                displayBudgets    = displayBudgets,
                childCounts       = childCounts,
                parentColors      = parentColors,
                expandedId        = expandedId,
                showChildBadge    = showChildBadge,
                isCompact         = isCompact,
                inlineStripShown  = inlineStripShown,
                suppressLongPress = suppressLongPress,
                extraModifier     = extraChipModifier(cat),
                chipWidth         = chipWidth,
                chipHeight        = chipHeight,
                circleSize        = circleSize,
                onChipClick       = onChipClick,
                onChipLongClick   = onChipLongClick,
                onChipDoubleClick = onChipDoubleClick
            )
        }
    }
}

@Composable
private fun CategoryBottomActionRow(
    leftCategory:      CategoryEntity?,
    rightCategory:     CategoryEntity?,
    spending:          Map<Long, Double>,
    displayBudgets:    Map<Long, Double>,
    childCounts:       Map<Long, Int>,
    parentColors:      Map<Long, String>,
    expandedId:        Long?,
    isCompact:         Boolean,
    onChipClick:       (CategoryEntity) -> Unit,
    onChipLongClick:   (CategoryEntity) -> Unit,
    onChipDoubleClick: (Long?) -> Unit,
    onAdd:             () -> Unit,
    modifier:          Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top
    ) {
        CategoryGridSlot(
            category       = leftCategory,
            spending       = spending,
            displayBudgets = displayBudgets,
            childCounts    = childCounts,
            parentColors   = parentColors,
            expandedId     = expandedId,
            showChildBadge = true,
            isCompact      = isCompact,
            onChipClick    = onChipClick,
            onChipLongClick = onChipLongClick,
            onChipDoubleClick = onChipDoubleClick
        )
        Box(
            Modifier.width(if (isCompact) CHIP_WIDTH_COMPACT else CHIP_WIDTH),
            contentAlignment = Alignment.Center
        ) {
            AddCategoryChip(onClick = onAdd)
        }
        CategoryGridSlot(
            category       = rightCategory,
            spending       = spending,
            displayBudgets = displayBudgets,
            childCounts    = childCounts,
            parentColors   = parentColors,
            expandedId     = expandedId,
            showChildBadge = true,
            isCompact      = isCompact,
            onChipClick    = onChipClick,
            onChipLongClick = onChipLongClick,
            onChipDoubleClick = onChipDoubleClick
        )
    }
}

@Composable
private fun LocalSubcategoryPanel(
    parent:           CategoryEntity,
    children:         List<CategoryEntity>,
    spending:         Map<Long, Double>,
    onChipClick:      (CategoryEntity) -> Unit,
    onChipLongClick:  (CategoryEntity) -> Unit,
    modifier:         Modifier = Modifier
) {
    SideSubcategoryPanel(
        parent           = parent,
        children         = children,
        spending         = spending,
        onClickChild     = onChipClick,
        onLongClickChild = onChipLongClick,
        modifier         = modifier
            .width(SUBCATEGORY_PANEL_WIDTH)
            .heightIn(max = SUBCATEGORY_PANEL_HEIGHT)
    )
}

@Composable
private fun TopSubcategoryPanelRow(
    expandedIndex:    Int,
    parent:           CategoryEntity,
    children:         List<CategoryEntity>,
    spending:         Map<Long, Double>,
    onChipClick:      (CategoryEntity) -> Unit,
    onChipLongClick:  (CategoryEntity) -> Unit,
    modifier:         Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top
    ) {
        repeat(4) { i ->
            Box(
                Modifier.width(if (i == expandedIndex) SUBCATEGORY_PANEL_WIDTH else CHIP_WIDTH),
                contentAlignment = Alignment.TopCenter
            ) {
                if (i == expandedIndex) {
                    LocalSubcategoryPanel(
                        parent          = parent,
                        children        = children,
                        spending        = spending,
                        onChipClick     = onChipClick,
                        onChipLongClick = onChipLongClick
                    )
                }
            }
        }
    }
}

// ── Сітка: donut-чарт + чипи ─────────────────────────────────────────────────

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
    isCompact:             Boolean                  = false,
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

    // All categories always shown: spending==0 chips render pale (tinted circle, colored icon)
    val display = if (sortBySpending) sorted else categories

    // ── Drag-to-swap (стан-машина винесена в CategoryChipDrag.kt) ─────────────
    val dragState = rememberChipDragState(onChipDragSwap)
    val effectiveChipModifier: (CategoryEntity?) -> Modifier = { cat ->
        chipExtraModifier(cat).then(dragState?.chipModifier(cat) ?: Modifier)
    }

    // Layout: top 4 | [left2 | donut | right2] | + | ext rows of 4
    val topRow   = display.take(4)
    val midLeft  = display.drop(4).take(2)
    val midRight = display.drop(6).take(2)
    val extCats  = display.drop(8)

    val expandedCat = if (expandedCategoryId != null && !showSubcategories)
        display.find { it.id == expandedCategoryId } else null
    val expandedChildren = expandedCat?.let { cat ->
        val parentName = cat.name.trim().lowercase()
        allCategoriesForTab.filter { c ->
            c.parentId == cat.id && !c.archived &&
            c.name.trim().lowercase() != parentName
        }
    } ?: emptyList()

    val hasExpandedStrip = expandedCat != null && expandedChildren.isNotEmpty()
    val topStripShown  = hasExpandedStrip && topRow.any  { it.id == expandedCat?.id }
    val midStripShown  = hasExpandedStrip && (midLeft + midRight).any { it.id == expandedCat?.id }

    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { pos -> dragState?.let { it.containerRootPos = pos.positionInRoot() } }
    ) {
        // rowPad = CategoryGridRow's padding(horizontal=4dp); chipGap = spacedBy(6dp) × 3 gaps
        val rowPad  = 4.dp
        val chipGap = 6.dp
        val chipW   = if (isCompact) CHIP_WIDTH_COMPACT
                      else ((maxWidth - rowPad * 2 - chipGap * 3) / 4).coerceAtLeast(68.dp)
        val circleSize  = when {
            maxWidth < 360.dp -> 46.dp
            maxWidth < 420.dp -> 50.dp
            else              -> 54.dp
        }
        // Тексти в чипі — у sp, тож ростуть разом із системним шрифтом, а висота
        // раніше залежала лише від екрана: при збільшеному шрифті нижню суму зрізало.
        // Прибрати фіксовану висоту не можна — усередині чипа розпірки weight(1f),
        // і без верхньої межі чип розтягується на всю секцію, з'їдаючи місце сусіда.
        val fontScale   = LocalDensity.current.fontScale
        val chipBase    = when {
            maxHeight < 700.dp -> 136.dp
            maxHeight < 800.dp -> 142.dp
            else               -> 148.dp
        }
        val chipHeight  = chipBase + 45.dp * (fontScale - 1f).coerceIn(0f, 1f)
    LazyColumn(
        modifier              = Modifier.fillMaxSize(),
        contentPadding        = PaddingValues(top = Spacing.sm, bottom = bottomPadding + Spacing.lg),
        verticalArrangement   = Arrangement.spacedBy(CATEGORY_VERTICAL_GAP)
    ) {
        // ── Порожній стан ────────────────────────────────────────────────
        if (categories.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(top = 56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Category, null,
                            modifier = Modifier.size(56.dp),
                            tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            stringResource(R.string.cat_empty),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.cat_empty_hint),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

            // ── Top row: 4 chips ───────────────────────────────────────────
            if (topRow.isNotEmpty()) {
                item(key = "top_row") {
                    Column {
                        CategoryGridRow(
                            rowCats           = topRow,
                            spending          = spending,
                            displayBudgets    = displayBudgets,
                            childCounts       = childCounts,
                            parentColors      = parentColors,
                            expandedId        = expandedCategoryId,
                            showChildBadge    = !showSubcategories,
                            isCompact         = isCompact,
                            inlineStripShown  = topStripShown,
                            suppressLongPress = onChipDragSwap != null,
                            extraChipModifier = effectiveChipModifier,
                            chipWidth         = chipW,
                            chipHeight        = chipHeight,
                            circleSize        = circleSize,
                            onChipClick       = onChipClick,
                            onChipLongClick   = onChipLongClick,
                            onChipDoubleClick = onChipDoubleClick,
                            // висота фіксована, але враховує системний шрифт (chipHeight):
                            // без верхньої межі розпірки всередині чипа розтягують його
                            // на всю секцію і з'їдають місце сусіда
                            modifier          = Modifier.height(chipHeight)
                        )
                        if (topStripShown) {
                            ExpandedCategoryStrip(
                                parent           = expandedCat,
                                children         = expandedChildren,
                                spending         = spending,
                                onClickParent    = { onChipClick(expandedCat) },
                                onClickChild     = { onChipClick(it) },
                                onLongClickChild = { onChipLongClick(it) },
                                inline           = true
                            )
                        }
                    }
                }
            }

            // ── Mid: left column | donut | right column ────────────────────
            item(key = "mid_section") {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier            = Modifier.width(chipW),
                            verticalArrangement = Arrangement.spacedBy(CATEGORY_VERTICAL_GAP)
                        ) {
                            midLeft.forEach { cat ->
                                Box(Modifier.height(chipHeight)) {
                                    CategoryGridSlot(
                                        category          = cat,
                                        spending          = spending,
                                        displayBudgets    = displayBudgets,
                                        childCounts       = childCounts,
                                        parentColors      = parentColors,
                                        expandedId        = expandedCategoryId,
                                        showChildBadge    = !showSubcategories,
                                        isCompact         = isCompact,
                                        inlineStripShown  = midStripShown,
                                        suppressLongPress = onChipDragSwap != null,
                                        extraModifier     = effectiveChipModifier(cat),
                                        chipWidth         = chipW,
                                        chipHeight        = chipHeight,
                                        circleSize        = circleSize,
                                        onChipClick       = onChipClick,
                                        onChipLongClick   = onChipLongClick,
                                        onChipDoubleClick = onChipDoubleClick
                                    )
                                }
                            }
                        }
                        val donutCats    = if (hasExpandedStrip) expandedChildren else categories
                        val donutExpense = if (hasExpandedStrip && selectedTab == 0)
                            expandedChildren.sumOf { spending[it.id] ?: 0.0 } else totalExpense
                        val donutIncome  = if (hasExpandedStrip && selectedTab == 1)
                            expandedChildren.sumOf { spending[it.id] ?: 0.0 } else totalIncome
                        DonutChart(
                            categories   = donutCats,
                            spending     = spending,
                            totalExpense = donutExpense,
                            totalIncome  = donutIncome,
                            selectedTab  = selectedTab,
                            onToggle     = onToggleTab,
                            modifier     = Modifier.weight(1f).height(chipHeight * 2 + CATEGORY_VERTICAL_GAP).padding(4.dp)
                        )
                        Column(
                            modifier            = Modifier.width(chipW),
                            verticalArrangement = Arrangement.spacedBy(CATEGORY_VERTICAL_GAP)
                        ) {
                            midRight.forEach { cat ->
                                Box(Modifier.height(chipHeight)) {
                                    CategoryGridSlot(
                                        category          = cat,
                                        spending          = spending,
                                        displayBudgets    = displayBudgets,
                                        childCounts       = childCounts,
                                        parentColors      = parentColors,
                                        expandedId        = expandedCategoryId,
                                        showChildBadge    = !showSubcategories,
                                        isCompact         = isCompact,
                                        inlineStripShown  = midStripShown,
                                        suppressLongPress = onChipDragSwap != null,
                                        extraModifier     = effectiveChipModifier(cat),
                                        chipWidth         = chipW,
                                        chipHeight        = chipHeight,
                                        circleSize        = circleSize,
                                        onChipClick       = onChipClick,
                                        onChipLongClick   = onChipLongClick,
                                        onChipDoubleClick = onChipDoubleClick
                                    )
                                }
                            }
                        }
                    }
                    if (midStripShown) {
                        ExpandedCategoryStrip(
                            parent           = expandedCat,
                            children         = expandedChildren,
                            spending         = spending,
                            onClickParent    = { onChipClick(expandedCat) },
                            onClickChild     = { onChipClick(it) },
                            onLongClickChild = { onChipLongClick(it) },
                            inline           = true
                        )
                    }
                }
            }

            // ── Ext cats: remaining categories in rows of 4 ────────────────
            extCats.chunked(4).forEach { rowCats ->
                val rowStripShown = hasExpandedStrip && rowCats.any { it.id == expandedCat?.id }
                item(key = rowCats.firstOrNull()?.id) {
                    Column {
                        CategoryGridRow(
                            rowCats           = rowCats,
                            spending          = spending,
                            displayBudgets    = displayBudgets,
                            childCounts       = childCounts,
                            parentColors      = parentColors,
                            expandedId        = expandedCategoryId,
                            showChildBadge    = !showSubcategories,
                            isCompact         = isCompact,
                            inlineStripShown  = rowStripShown,
                            suppressLongPress = onChipDragSwap != null,
                            extraChipModifier = effectiveChipModifier,
                            chipWidth         = chipW,
                            chipHeight        = chipHeight,
                            circleSize        = circleSize,
                            onChipClick       = onChipClick,
                            onChipLongClick   = onChipLongClick,
                            onChipDoubleClick = onChipDoubleClick,
                            // висота фіксована, але враховує системний шрифт (chipHeight):
                            // без верхньої межі розпірки всередині чипа розтягують його
                            // на всю секцію і з'їдають місце сусіда
                            modifier          = Modifier.height(chipHeight)
                        )
                        if (rowStripShown) {
                            ExpandedCategoryStrip(
                                parent           = expandedCat,
                                children         = expandedChildren,
                                spending         = spending,
                                onClickParent    = { onChipClick(expandedCat) },
                                onClickChild     = { onChipClick(it) },
                                onLongClickChild = { onChipLongClick(it) },
                                inline           = true
                            )
                        }
                    }
                }
            }

            // ── Add button row (завжди останній) ──────────────────────────
            item(key = "add_row") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                ) {
                    Box(
                        modifier = Modifier.size(chipW, chipHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        AddCategoryChip(onClick = onAdd)
                    }
                    repeat(3) {
                        Box(modifier = Modifier.size(chipW, chipHeight))
                    }
                }
            }

    }

    // ── Drag ghost: follows finger ─────────────────────────────────────────
    val dragId = dragState?.draggingId
    if (dragId != null && dragState != null) {
        val draggingCat = display.firstOrNull { it.id == dragId }
        val center      = dragState.chipCenters[dragId]
        if (draggingCat != null && center != null) {
            val chipPxW = with(density) { chipW.roundToPx() }
            val chipPxH = with(density) { chipHeight.roundToPx() }
            val localX  = (center.x - dragState.containerRootPos.x + dragState.dragOffset.x).toInt() - chipPxW / 2
            val localY  = (center.y - dragState.containerRootPos.y + dragState.dragOffset.y).toInt() - chipPxH / 2
            Box(
                modifier = Modifier
                    .offset { IntOffset(localX, localY) }
                    .size(chipW, chipHeight)
                    .shadow(8.dp, RoundedCornerShape(OneMoneyTheme.dimens.cardRadius))
            ) {
                CategoryChip(
                    category       = draggingCat,
                    spending       = spending[draggingCat.id] ?: 0.0,
                    onClick        = {},
                    isCompact      = isCompact,
                    budgetOverride = displayBudgets[draggingCat.id]
                )
            }
        }
    }
    }
}
