package org.syalosovetskyi.onemoney.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.stringResource
import org.syalosovetskyi.onemoney.R
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import org.syalosovetskyi.onemoney.data.db.dao.TransactionWithDetails
import org.syalosovetskyi.onemoney.data.db.entities.AccountType
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.data.repository.HomeScreenTab
import org.syalosovetskyi.onemoney.ui.accounts.AccountFormSheet
import org.syalosovetskyi.onemoney.ui.accounts.AccountsScreen
import org.syalosovetskyi.onemoney.ui.accounts.AccountsViewModel
import org.syalosovetskyi.onemoney.ui.accounts.NewAccountTypeSheet
import org.syalosovetskyi.onemoney.ui.budget.BudgetScreen
import org.syalosovetskyi.onemoney.ui.categories.CategoriesScreen
import org.syalosovetskyi.onemoney.ui.categories.CategoriesViewModel
import org.syalosovetskyi.onemoney.ui.categories.EditCategoriesScreen
import org.syalosovetskyi.onemoney.ui.components.icons.DoubleChevronRight
import org.syalosovetskyi.onemoney.ui.components.icons.ToolbarEditIcon
import org.syalosovetskyi.onemoney.ui.components.icons.ToolbarProfileIcon
import org.syalosovetskyi.onemoney.ui.components.icons.ToolbarSettingsIcon
import org.syalosovetskyi.onemoney.ui.data.DataScreen
import org.syalosovetskyi.onemoney.ui.overview.OverviewScreen
import org.syalosovetskyi.onemoney.ui.settings.SettingsScreen
import org.syalosovetskyi.onemoney.ui.settings.SettingsViewModel
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.transactions.TransactionsListScreen
import org.syalosovetskyi.onemoney.util.DateFormats
import org.syalosovetskyi.onemoney.util.formatMoney
import org.syalosovetskyi.onemoney.ui.accounts.currencySymbol
import org.syalosovetskyi.onemoney.util.parseColorHex
import java.util.*

// ── Bottom tab definition ─────────────────────────────────────────────────────

private data class BottomTab(
    val label: String,        // stable identity key (do NOT localize — used for lookups)
    val titleRes: Int,        // localized display label
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

// Порядок вкладок — як в оригіналі 1Money
private val TABS = listOf(
    BottomTab("Рахунки",   R.string.nav_accounts,   Icons.Filled.AccountBalanceWallet,      Icons.Outlined.AccountBalanceWallet),
    BottomTab("Категорії", R.string.nav_categories, Icons.Filled.DonutLarge,                Icons.Outlined.DonutLarge),
    BottomTab("Операції",  R.string.nav_operations, Icons.AutoMirrored.Filled.ReceiptLong,  Icons.AutoMirrored.Outlined.ReceiptLong),
    BottomTab("Бюджет",    R.string.nav_budget,     Icons.Filled.Speed,                     Icons.Outlined.Speed),
    BottomTab("Огляд",     R.string.nav_overview,   Icons.AutoMirrored.Filled.TrendingUp,   Icons.AutoMirrored.Filled.TrendingUp),
)

// ── Main container ────────────────────────────────────────────────────────────

@Composable
fun MainScreen(
    onAddTransaction:    () -> Unit,
    mainViewModel:       MainViewModel      = hiltViewModel(),
    accountsViewModel:   AccountsViewModel  = hiltViewModel(),
    categoriesViewModel: CategoriesViewModel = hiltViewModel(),
    settingsViewModel:   SettingsViewModel  = hiltViewModel()
) {
    val settings       by settingsViewModel.settings.collectAsState()
    val budgetVisible   = settings.budgetVisible
    val initialPage     = settings.homeScreen.index

    val activeTabs = if (budgetVisible) TABS else TABS.filterIndexed { i, _ -> i != 3 }
    // initialPage used only on first composition — subsequent setting changes don't force scroll
    val safeInitial = if (!budgetVisible && initialPage == HomeScreenTab.BUDGET.index)
        HomeScreenTab.CATEGORIES.index
    else initialPage.coerceIn(0, activeTabs.lastIndex)
    val pagerState  = rememberPagerState(initialPage = safeInitial,
                                        pageCount    = { activeTabs.size })
    val scope       = rememberCoroutineScope()
    val currentPage = pagerState.currentPage
    val mainState  by mainViewModel.state.collectAsState()
    val totalBalance = mainState.totalBalance
    val drawerState  = rememberDrawerState(DrawerValue.Closed)

    // ── Новий рахунок ────────────────────────────────────────────────────────
    var showAccTypeSheet by remember { mutableStateOf(false) }
    var pendingAccType   by remember { mutableStateOf<AccountType?>(null) }
    val triggerNewAccount: () -> Unit = { showAccTypeSheet = true }

    // ── Редагування категорій ────────────────────────────────────────────────
    var showEditCategories by remember { mutableStateOf(false) }
    val triggerEditCategories: () -> Unit = { showEditCategories = true }
    val categoriesState by categoriesViewModel.state.collectAsState()

    // ── Екран Дані / Налаштування / Пошук / Фільтр за категорією ─────────────
    var showDataScreen         by remember { mutableStateOf(false) }
    var showSettingsScreen     by remember { mutableStateOf(false) }
    var openTxSearch           by remember { mutableStateOf(false) }
    var showBudgetSettings     by remember { mutableStateOf(false) }
    var filterByCategoryId     by remember { mutableStateOf<Long?>(null) }
    var filterByAccountId      by remember { mutableStateOf<Long?>(null) }
    var categoriesCompact      by remember { mutableStateOf(false) }

    if (showDataScreen) {
        DataScreen(onNavigateBack = { showDataScreen = false })
        return
    }

    if (showSettingsScreen) {
        SettingsScreen(
            onNavigateBack = { showSettingsScreen = false },
            onData         = { showSettingsScreen = false; showDataScreen = true }
        )
        return
    }

    // Жест «назад» — повертаємося на головну вкладку (налаштовується), звідти — виходимо з додатку
    val homeTabIndex   = activeTabs.indexOfFirst { it.titleRes == settings.homeScreen.labelRes }
        .takeIf { it >= 0 } ?: 0
    val txTabIndex     = activeTabs.indexOfFirst { it.titleRes == R.string.nav_operations }.takeIf { it >= 0 } ?: 2
    val budgetTabIndex = activeTabs.indexOfFirst { it.titleRes == R.string.nav_budget }.takeIf { it >= 0 } ?: -1
    val goBack: () -> Unit = {
        if (currentPage != homeTabIndex) scope.launch { pagerState.animateScrollToPage(homeTabIndex) }
    }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = showEditCategories) { showEditCategories = false }
    BackHandler(enabled = currentPage != homeTabIndex) { goBack() }

    ModalNavigationDrawer(
        drawerState     = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent   = {
            AppDrawerContent(
                onClose         = { scope.launch { drawerState.close() } },
                onDataClick     = { scope.launch { drawerState.close() }; showDataScreen = true },
                onSettingsClick = { scope.launch { drawerState.close() }; showSettingsScreen = true }
            )
        }
    ) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            val navColors = OneMoneyTheme.colors
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp
            ) {
                activeTabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = currentPage == index,
                        onClick  = { scope.launch { pagerState.animateScrollToPage(index) } },
                        icon = {
                            Icon(
                                if (currentPage == index) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = stringResource(tab.titleRes)
                            )
                        },
                        label  = {
                            Text(
                                stringResource(tab.titleRes), maxLines = 1,
                                fontSize   = 10.sp,
                                fontWeight = if (currentPage == index) FontWeight.Medium else FontWeight.Light,
                                letterSpacing = 0.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = navColors.primaryText,
                            selectedTextColor   = navColors.primaryText,
                            indicatorColor      = navColors.bottomNavPill,
                            unselectedIconColor = navColors.primaryText,
                            unselectedTextColor = navColors.primaryText,
                        )
                    )
                }
            }
        }
    ) { padding ->
        val bottomPadding = PaddingValues(bottom = padding.calculateBottomPadding())

        // Logical tab index → content page (accounting for hidden Budget tab)
        val pageToContent: (Int) -> Int = { page ->
            if (!budgetVisible && page >= 3) page + 1 else page
        }

        val contentPage = if (!budgetVisible && currentPage >= 3) currentPage + 1 else currentPage

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .edgeSwipe(
                    onLeftEdge  = { scope.launch { drawerState.open() } },
                    onRightEdge = goBack
                )
        ) {
            SharedTopBar(
                totalBalance        = totalBalance,
                currentPage         = contentPage,
                onAvatarClick       = { scope.launch { drawerState.open() } },
                onPlusClick         = triggerNewAccount,
                onEditCategories    = triggerEditCategories,
                onSettings          = { showSettingsScreen = true },
                onSearchTx          = { openTxSearch = true },
                onBudgetSettings    = { showBudgetSettings = true }
            )

            HorizontalPager(
                state             = pagerState,
                modifier          = Modifier.weight(1f),
                userScrollEnabled = false
            ) { page ->
                when (pageToContent(page)) {
                    0 -> AccountsScreen(
                             padding       = bottomPadding,
                             embeddedMode  = true,
                             onRequestAdd  = triggerNewAccount,
                             onViewTx      = { acc ->
                                 filterByAccountId = acc.id
                                 scope.launch { pagerState.animateScrollToPage(txTabIndex) }
                             },
                             onAddIncome   = { onAddTransaction() },
                             onAddExpense  = { onAddTransaction() },
                             onAddTransfer = { onAddTransaction() }
                         )
                    1 -> CategoriesScreen(
                             padding          = bottomPadding,
                             embeddedMode     = true,
                             isCompact        = categoriesCompact,
                             onViewCategoryTx = { cat ->
                                 filterByCategoryId = cat.id
                                 scope.launch { pagerState.animateScrollToPage(txTabIndex) }
                             },
                             onViewBudget     = {
                                 if (budgetTabIndex >= 0)
                                     scope.launch { pagerState.animateScrollToPage(budgetTabIndex) }
                             }
                         )
                    2 -> TransactionsListScreen(
                             padding                       = bottomPadding,
                             embeddedMode                  = true,
                             openSearch                    = openTxSearch,
                             onSearchDismissed             = { openTxSearch = false },
                             initialCategoryFilter         = filterByCategoryId,
                             onInitialFilterApplied        = { filterByCategoryId = null },
                             initialAccountFilter          = filterByAccountId,
                             onInitialAccountFilterApplied = { filterByAccountId = null }
                         )
                    3 -> BudgetScreen(
                             padding           = bottomPadding,
                             embeddedMode      = true,
                             showSettings      = showBudgetSettings,
                             onSettingsDismiss = { showBudgetSettings = false }
                         )
                    4 -> OverviewScreen(
                             padding          = bottomPadding,
                             onAddTransaction = onAddTransaction,
                             embeddedMode     = true,
                             onViewCategoryTx = { catId ->
                                 filterByCategoryId = catId
                                 scope.launch { pagerState.animateScrollToPage(txTabIndex) }
                             }
                         )
                    else -> Unit
                }
            }
        }
    }

    // ── Вибір типу рахунку ───────────────────────────────────────────────────
    if (showAccTypeSheet) {
        NewAccountTypeSheet(
            onSelect  = { type ->
                showAccTypeSheet = false
                pendingAccType   = type
            },
            onDismiss = { showAccTypeSheet = false }
        )
    }

    // ── Форма створення рахунку ──────────────────────────────────────────────
    pendingAccType?.let { type ->
        AccountFormSheet(
            initialType = type,
            existing    = null,
            onSave      = { name, accType, balance, color, currency, description, includeInTotal, icon, creditLimit ->
                accountsViewModel.add(name, accType, balance, color, currency, description, includeInTotal, icon, creditLimit)
                pendingAccType = null
            },
            onDismiss   = { pendingAccType = null }
        )
    }

    // ── Редагування категорій (з SharedTopBar — олівець на вкладці 1) ────────
    if (showEditCategories) {
        EditCategoriesScreen(
            expenseCategories = categoriesState.expenseCategories,
            incomeCategories  = categoriesState.incomeCategories,
            monthSpending     = categoriesState.monthSpending,
            monthIncome       = categoriesState.monthIncome,
            totalExpense      = categoriesState.totalExpense,
            totalIncome       = categoriesState.totalIncome,
            onSave = { name, type, color, icon, budget, period, archived, currency, existing ->
                if (existing != null) {
                    categoriesViewModel.update(
                        existing.copy(
                            name         = name,
                            type         = type,
                            colorHex     = color,
                            icon         = icon,
                            budgetAmount = budget,
                            budgetPeriod = period,
                            archived     = archived,
                            currencyCode = currency
                        )
                    )
                } else {
                    categoriesViewModel.add(name, type, color, icon, budget, period, currency)
                }
            },
            onAddSubcategory = { name, type, color, icon, budget, period, currency, parentId ->
                categoriesViewModel.add(name, type, color, icon, budget, period, currency, parentId)
            },
            onDelete  = { categoriesViewModel.delete(it) },
            onReorder = { categoriesViewModel.reorderCategories(it) },
            onDismiss = { showEditCategories = false }
        )
    }
    } // end ModalNavigationDrawer
}

// ── App drawer ────────────────────────────────────────────────────────────────

@Composable
internal fun AppDrawerContent(
    onClose:         () -> Unit,
    onDataClick:     () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
        // Header
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_full),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(52.dp).clip(CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Zerno",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.CloudOff, null,
                        modifier = Modifier.size(14.dp),
                        tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Особистий бюджет",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(
                    DoubleChevronRight, null,
                    tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))

        // Menu items
        DrawerMenuItem(Icons.Outlined.Settings,   stringResource(R.string.settings_title), onClick = onSettingsClick)
        DrawerMenuItem(Icons.Outlined.Storage,    stringResource(R.string.settings_data),  onClick = onDataClick)
    }
}

@Composable
private fun DrawerMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    ListItem(
        modifier          = Modifier.clickable(onClick = onClick),
        leadingContent    = {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        },
        headlineContent   = {
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    )
}

// ── Shared top bar ────────────────────────────────────────────────────────────

@Composable
fun SharedTopBar(
    totalBalance:       Double,
    currentPage:        Int,
    onAvatarClick:      () -> Unit = {},
    onPlusClick:        () -> Unit,
    onEditCategories:   () -> Unit = {},
    onSettings:         () -> Unit = {},
    onSearchTx:         () -> Unit = {},
    onBudgetSettings:   () -> Unit = {}
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val topBarDimens  = OneMoneyTheme.dimens
        val topBarColors  = OneMoneyTheme.colors
        val topBarTypo    = OneMoneyTheme.typography
        IconButton(
            onClick = onAvatarClick,
            modifier = Modifier.size(topBarDimens.topBarAvatarSize)
        ) {
            Icon(
                ToolbarProfileIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(Modifier.width(Spacing.md))

        // Центр: «Всі рахунки» + баланс
        Column(
            modifier            = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.acc_all_title),
                style      = topBarTypo.topBarLabel.copy(letterSpacing = 0.sp),
                color      = topBarColors.primaryText
            )
            Text(
                formatMoney(totalBalance) + " ₴",
                style      = topBarTypo.topBarBalance.copy(letterSpacing = 0.sp),
                color      = topBarColors.primaryText,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(Spacing.md))

        // Права кнопка
        val (icon, description, action) = when (currentPage) {
            0    -> Triple(Icons.Default.Add,       stringResource(R.string.acc_new_title),          onPlusClick)
            1    -> Triple(ToolbarEditIcon,         stringResource(R.string.cat_edit_categories),    onEditCategories)
            2    -> Triple(Icons.Default.Search,    stringResource(R.string.toolbar_search_tx),      onSearchTx)
            3    -> Triple(Icons.Outlined.Speed,    stringResource(R.string.toolbar_budget_settings), onBudgetSettings)
            else -> Triple(ToolbarSettingsIcon,     stringResource(R.string.settings_title),         onSettings)
        }

        IconButton(
            onClick  = action,
            modifier = Modifier.size(topBarDimens.topBarAvatarSize).clip(CircleShape)
        ) {
            Icon(
                icon,
                contentDescription = description,
                tint               = MaterialTheme.colorScheme.onSurface,
                modifier           = Modifier.size(if (currentPage == 2) topBarDimens.topBarAvatarIconSize else 28.dp)
            )
        }
    }
}

// ── Reusable UI blocks ────────────────────────────────────────────────────────



@Composable
fun TransactionListItem(tx: TransactionWithDetails, onClick: () -> Unit) {
    val appColors = OneMoneyTheme.colors
    val appDimens = OneMoneyTheme.dimens
    val (amountColor, amountPrefix, typeIcon) = when (tx.type) {
        TransactionType.INCOME   -> Triple(appColors.incomeGreen,  "+", Icons.Default.ArrowDownward)
        TransactionType.EXPENSE  -> Triple(appColors.expenseRed,   "−", Icons.Default.ArrowUpward)
        TransactionType.TRANSFER -> Triple(appColors.transferBlue, "⇄", Icons.Default.SwapHoriz)
        TransactionType.BORROW   -> Triple(appColors.debtOrange,   "+", Icons.Default.MoveToInbox)
        TransactionType.LEND     -> Triple(appColors.debtOrange,   "−", Icons.Default.Outbox)
        TransactionType.REPAY    -> Triple(appColors.debtOrange,            "−", Icons.AutoMirrored.Filled.AssignmentReturn)
    }
    val fallbackColor = MaterialTheme.colorScheme.secondaryContainer
    val catColor = tx.categoryColor?.let {
        parseColorHex(it, fallbackColor)
    } ?: fallbackColor

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape)
                    .background(catColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(typeIcon, null, tint = amountColor, modifier = Modifier.size(20.dp))
            }
        },
        headlineContent = {
            Text(
                text = tx.categoryName ?: tx.type.defaultLabel(),
                fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            val dateStr = DateFormats.dayMonthShort(Date(tx.date))
            val sub = buildString {
                append(dateStr)
                if (tx.note.isNotBlank()) append(" · ${tx.note}")
                append(" · ${tx.accountName}")
                tx.toAccountName?.let { append(" → $it") }
            }
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Text(
                "$amountPrefix${formatMoney(tx.amount, currencySymbol(tx.accountCurrency))}",
                color = amountColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
    )
    HorizontalDivider(
        modifier  = Modifier.padding(start = 72.dp),
        thickness = 0.5.dp,
        color     = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun TransactionType.defaultLabel(): String = when (this) {
    TransactionType.TRANSFER -> stringResource(R.string.tx_transfer)
    TransactionType.BORROW   -> stringResource(R.string.tx_type_borrow)
    TransactionType.LEND     -> stringResource(R.string.tx_type_lend)
    TransactionType.REPAY    -> stringResource(R.string.tx_type_repay)
    TransactionType.INCOME   -> stringResource(R.string.tx_income)
    TransactionType.EXPENSE  -> stringResource(R.string.tx_expense)
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title, modifier = modifier,
        style      = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color      = MaterialTheme.colorScheme.onSurface
    )
}

// ── Swipe gesture helpers ─────────────────────────────────────────────────────

private const val SWIPE_THRESHOLD = 130f  // підвищено: випадкові свайпи не перемикають місяць
private const val EDGE_DP         = 80

/** Центральний свайп — ігнорує крайкові зони (їх обробляє edgeSwipe).
 *  Спрацьовує лише коли горизонтальний рух домінує над вертикальним (1.7:1). */
fun Modifier.horizontalSwipe(
    onSwipeLeft:  () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier = pointerInput(onSwipeLeft, onSwipeRight) {
    val edgePx = EDGE_DP.dp.roundToPx().toFloat()
    awaitEachGesture {
        val down   = awaitFirstDown(requireUnconsumed = false)
        val startX = down.position.x
        val startY = down.position.y
        if (startX < edgePx || startX > size.width - edgePx) return@awaitEachGesture
        var endX = startX
        var endY = startY
        while (true) {
            val event  = awaitPointerEvent()
            val change = event.changes.lastOrNull() ?: break
            endX = change.position.x
            endY = change.position.y
            if (!change.pressed) break
        }
        val deltaX = endX - startX
        val deltaY = endY - startY
        if (kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) * 1.7f) {
            when {
                deltaX < -SWIPE_THRESHOLD -> onSwipeLeft()
                deltaX >  SWIPE_THRESHOLD -> onSwipeRight()
            }
        }
    }
}

/** Крайковий свайп: ліва крайка→вправо = відкрити drawer; права крайка→вліво = назад. */
fun Modifier.edgeSwipe(
    onLeftEdge:  () -> Unit = {},
    onRightEdge: () -> Unit = {},
): Modifier = pointerInput(onLeftEdge, onRightEdge) {
    val edgePx = EDGE_DP.dp.roundToPx().toFloat()
    awaitEachGesture {
        val down   = awaitFirstDown(requireUnconsumed = false)
        val startX = down.position.x
        // Центральна зона — ігноруємо; її обробляє horizontalSwipe всередині екрану
        if (startX in edgePx..(size.width - edgePx)) return@awaitEachGesture
        var endX = startX
        while (true) {
            val event  = awaitPointerEvent()
            val change = event.changes.lastOrNull() ?: break
            endX = change.position.x
            if (!change.pressed) break
        }
        val delta = endX - startX
        if (kotlin.math.abs(delta) < SWIPE_THRESHOLD) return@awaitEachGesture
        when {
            startX < edgePx && delta > 0               -> onLeftEdge()
            startX > size.width - edgePx && delta < 0  -> onRightEdge()
        }
    }
}
