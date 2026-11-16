package org.syalosovetskyi.onemoney.ui.categories

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ArrowCircleDown
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCategoriesScreen(
    expenseCategories: List<CategoryEntity>,
    incomeCategories:  List<CategoryEntity>,
    monthSpending:     Map<Long, Double>,
    monthIncome:       Map<Long, Double>,
    totalExpense:      Double,
    totalIncome:       Double,
    onSave:            (name: String, type: TransactionType, color: String, icon: String, budget: Double, period: String, archived: Boolean, currency: String, existing: CategoryEntity?) -> Unit,
    onAddSubcategory:  (name: String, type: TransactionType, color: String, icon: String, budget: Double, period: String, currency: String, parentId: Long) -> Unit,
    onDelete:          (CategoryEntity) -> Unit,
    onReorder:         (List<CategoryEntity>) -> Unit = {},
    onDismiss:         () -> Unit
) {
    var selectedTab       by remember { mutableIntStateOf(0) }
    var showSubcategories by remember { mutableStateOf(false) }
    var editCategory      by remember { mutableStateOf<CategoryEntity?>(null) }
    var showAddSheet      by remember { mutableStateOf(false) }
    var addSubcategoryFor by remember { mutableStateOf<CategoryEntity?>(null) }

    val allCategoriesForTab = (if (selectedTab == 0) expenseCategories else incomeCategories)
        .filter { !it.archived }
    val rawSpending = if (selectedTab == 0) monthSpending else monthIncome

    // Local mutable list for drag reordering (reset when tab changes)
    val localCats = remember(selectedTab) { mutableStateListOf<CategoryEntity>() }
    LaunchedEffect(allCategoriesForTab) {
        localCats.clear()
        localCats.addAll(allCategoriesForTab)
    }

    val effectiveSpending: Map<Long, Double> = run {
        val result = rawSpending.toMutableMap()
        allCategoriesForTab.filter { it.parentId != null }.forEach { child ->
            child.parentId?.let { pid ->
                result[pid] = (result[pid] ?: 0.0) + (rawSpending[child.id] ?: 0.0)
            }
        }
        result
    }

    val categories = if (!showSubcategories)
        localCats.filter { it.parentId == null }
    else
        localCats.filter { it.parentId != null }

    // Surface, not Box+background: the screen is drawn outside any Scaffold, so
    // without it LocalContentColor stays Color.Black and the title/back arrow are
    // invisible in dark theme (#3037 R7).
    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
                Text(
                    if (showSubcategories) stringResource(R.string.cat_edit_subcategories) else stringResource(R.string.cat_edit_categories),
                    style    = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                )
                TextButton(onClick = { showSubcategories = !showSubcategories }) {
                    Text(if (showSubcategories) stringResource(R.string.nav_categories) else stringResource(R.string.cat_subcategories_tab))
                }
            }

            // ── Tab row ───────────────────────────────────────────────────────
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 },
                    icon     = { Icon(Icons.Outlined.ArrowCircleDown, contentDescription = null) },
                    text     = { Text(stringResource(R.string.common_expenses)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 },
                    icon     = { Icon(Icons.Outlined.AddCircleOutline, contentDescription = null) },
                    text     = { Text(stringResource(R.string.common_incomes)) }
                )
            }

            // ── Grid (same layout as categories, badges hidden) ───────────────
            CategoriesGridContent(
                categories            = categories,
                allCategoriesForTab   = allCategoriesForTab,
                spending              = effectiveSpending,
                totalExpense          = totalExpense,
                totalIncome           = totalIncome,
                selectedTab           = selectedTab,
                onToggleTab           = { selectedTab = if (selectedTab == 0) 1 else 0 },
                bottomPadding         = 0.dp,
                onChipClick           = { cat -> editCategory = cat },
                onChipLongClick       = {},  // suppress edit form — drag handles long press
                onAdd                 = { showAddSheet = true },
                showSubcategories     = showSubcategories,
                onToggleSubcategories = { showSubcategories = !showSubcategories },
                childCounts           = emptyMap(),
                sortBySpending        = false,
                onChipDragSwap        = { fromId, toId ->
                    val idxA = localCats.indexOfFirst { it.id == fromId }
                    val idxB = localCats.indexOfFirst { it.id == toId }
                    if (idxA >= 0 && idxB >= 0) {
                        val tmp = localCats[idxA]
                        localCats[idxA] = localCats[idxB]
                        localCats[idxB] = tmp
                        onReorder(localCats.toList())
                    }
                }
            )
        }
    }

    // ── Edit existing category ────────────────────────────────────────────────
    editCategory?.let { cat ->
        val children = allCategoriesForTab.filter { it.parentId == cat.id && !it.archived }
        CategoryFormSheet(
            existing             = cat,
            children             = children,
            defaultType          = cat.type,
            onAddSubcategory     = if (cat.parentId == null) ({
                editCategory = null
                addSubcategoryFor = cat
            }) else null,
            onDetachSubcategory  = if (cat.parentId == null) ({ child ->
                onSave(child.name, child.type, child.colorHex, child.icon,
                       child.budgetAmount, child.budgetPeriod, child.archived,
                       child.currencyCode, child.copy(parentId = null))
            }) else null,
            onDeleteSubcategory  = if (cat.parentId == null) ({ child -> onDelete(child) }) else null,
            parentOptions        = allCategoriesForTab.filter { it.parentId == null && it.id != cat.id },
            onChangeParent       = if (cat.parentId != null) ({ newParentId ->
                onSave(cat.name, cat.type, cat.colorHex, cat.icon,
                       cat.budgetAmount, cat.budgetPeriod, cat.archived,
                       cat.currencyCode, cat.copy(parentId = newParentId))
            }) else null,
            onSave    = { name, type, color, icon, budget, period, archived, currency ->
                onSave(name, type, color, icon, budget, period, archived, currency, cat)
                editCategory = null
            },
            onDelete  = { onDelete(cat); editCategory = null },
            onDismiss = { editCategory = null }
        )
    }

    // ── Add new top-level category ────────────────────────────────────────────
    if (showAddSheet) {
        val defaultType = if (selectedTab == 0) TransactionType.EXPENSE else TransactionType.INCOME
        CategoryFormSheet(
            existing    = null,
            defaultType = defaultType,
            onSave      = { name, type, color, icon, budget, period, _, currency ->
                onSave(name, type, color, icon, budget, period, false, currency, null)
                showAddSheet = false
            },
            onDismiss = { showAddSheet = false }
        )
    }

    // ── Add subcategory under a parent ────────────────────────────────────────
    addSubcategoryFor?.let { parent ->
        CategoryFormSheet(
            existing    = null,
            forParentId = parent.id,
            defaultType = parent.type,
            onSave      = { name, type, color, icon, budget, period, _, currency ->
                onAddSubcategory(name, type, color, icon, budget, period, currency, parent.id)
                addSubcategoryFor = null
            },
            onDismiss = { addSubcategoryFor = null }
        )
    }
}
