
package org.syalosovetskyi.onemoney.ui.transactions

import org.syalosovetskyi.onemoney.util.parseColorHex
import org.syalosovetskyi.onemoney.util.DateFormats
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.syalosovetskyi.onemoney.data.db.dao.TransactionWithDetails
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.ui.categories.QuickExpenseSheet
import org.syalosovetskyi.onemoney.ui.main.SectionHeader
import org.syalosovetskyi.onemoney.ui.main.SharedMonthNavPill
import org.syalosovetskyi.onemoney.ui.main.TransactionListItem
import org.syalosovetskyi.onemoney.util.formatMoney
import org.syalosovetskyi.onemoney.ui.main.horizontalSwipe
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.theme.AccentBlueGrey
import org.syalosovetskyi.onemoney.ui.theme.AccentIndigo
import org.syalosovetskyi.onemoney.ui.theme.AccentIndigoLight
import androidx.compose.ui.res.painterResource
import java.text.SimpleDateFormat
import java.util.*

private val FilterDefaultColor = AccentBlueGrey     // нейтральний фільтр (було 607D8B)
private val FilterQueryColor   = AccentIndigoLight  // фільтр за запитом (було 5C6BC0)
private val FilterAccountColor = AccentIndigo        // фільтр за рахунком (було 3949AB)


// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsListScreen(
    padding:                        PaddingValues = PaddingValues(),
    embeddedMode:                   Boolean        = false,
    openSearch:                     Boolean        = false,
    onSearchDismissed:              () -> Unit     = {},
    initialCategoryFilter:          Long?          = null,
    onInitialFilterApplied:         () -> Unit     = {},
    initialAccountFilter:           Long?          = null,
    onInitialAccountFilterApplied:  () -> Unit     = {},
    viewModel:                      TransactionsListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // ── Фільтри ────────────────────────────────────────────────────────────
    var filterQuery       by remember { mutableStateOf("") }
    var filterTypes       by remember { mutableStateOf(setOf<String>()) }   // порожнє = всі
    var filterAccountIds  by remember { mutableStateOf(setOf<Long>()) }
    var filterCategoryIds by remember { mutableStateOf(setOf<Long>()) }
    var showFilterSheet   by remember { mutableStateOf(false) }

    LaunchedEffect(openSearch) {
        if (openSearch) {
            showFilterSheet = true
            onSearchDismissed()
        }
    }

    LaunchedEffect(initialCategoryFilter) {
        initialCategoryFilter?.let { catId ->
            filterCategoryIds = setOf(catId)
            onInitialFilterApplied()
        }
    }

    LaunchedEffect(initialAccountFilter) {
        initialAccountFilter?.let { accId ->
            filterAccountIds = setOf(accId)
            onInitialAccountFilterApplied()
        }
    }

    // ── Швидке додавання ───────────────────────────────────────────────────
    var showCategoryPicker    by remember { mutableStateOf(false) }
    var quickCategory         by remember { mutableStateOf<CategoryEntity?>(null) }
    var transferFromAccount   by remember { mutableStateOf<AccountEntity?>(null) }

    // ── Деталі транзакції ──────────────────────────────────────────────────
    var selectedDetailTx by remember { mutableStateOf<TransactionWithDetails?>(null) }

    // ── Клієнтська фільтрація ─────────────────────────────────────────────
    val filteredTransactions = remember(state.transactions, filterQuery, filterTypes, filterAccountIds, filterCategoryIds, state.expenseCategories, state.incomeCategories) {
        val allCats = state.expenseCategories + state.incomeCategories
        // Expand filterCategoryIds to include children of selected categories
        val expandedCatIds = if (filterCategoryIds.isEmpty()) emptySet<Long>() else {
            filterCategoryIds + allCats.filter { it.parentId in filterCategoryIds }.map { it.id }.toSet()
        }
        state.transactions.filter { tx ->
            (filterQuery.isBlank() ||
                tx.note.contains(filterQuery, ignoreCase = true) ||
                tx.categoryName?.contains(filterQuery, ignoreCase = true) == true ||
                tx.accountName.contains(filterQuery, ignoreCase = true)) &&
            (filterTypes.isEmpty() || tx.type.name in filterTypes) &&
            (filterAccountIds.isEmpty() || tx.accountId in filterAccountIds) &&
            (expandedCatIds.isEmpty() || tx.categoryId in expandedCatIds)
        }
    }

    val isFiltered = filterQuery.isNotBlank() || filterTypes.isNotEmpty() ||
                     filterAccountIds.isNotEmpty() || filterCategoryIds.isNotEmpty()

    val totalsByCurrency = org.syalosovetskyi.onemoney.util.currencyTotals(filteredTransactions)

    // ── Витрати за категоріями (для пікера) ───────────────────────────────
    val categorySpending = remember(state.baseTransactions) {
        state.baseTransactions.groupBy { it.categoryId }
            .mapValues { (_, txList) -> txList.sumOf { it.amount } }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .horizontalSwipe(
            onSwipeLeft  = viewModel::nextMonth,
            onSwipeRight = viewModel::prevMonth
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = if (embeddedMode) 0.dp else padding.calculateTopPadding())
        ) {
            // Пілюля-навігатор місяця
            SharedMonthNavPill(
                appMonth       = state.appMonth,
                daysInPeriod   = state.daysInMonth,
                onPrev         = viewModel::prevMonth,
                onNext         = viewModel::nextMonth,
                onSelectPeriod = viewModel::setPeriod
            )

            // Активні фільтр-чіпси
            if (isFiltered) {
                ActiveFilterChipsRow(
                    filterQuery       = filterQuery,
                    filterTypes       = filterTypes,
                    filterAccountIds  = filterAccountIds,
                    filterCategoryIds = filterCategoryIds,
                    accounts          = state.accounts,
                    expenseCategories = state.expenseCategories,
                    incomeCategories  = state.incomeCategories,
                    onRemoveQuery     = { filterQuery = "" },
                    onRemoveType      = { t -> filterTypes = filterTypes - t },
                    onRemoveAccount   = { id -> filterAccountIds = filterAccountIds - id },
                    onRemoveCategory  = { id -> filterCategoryIds = filterCategoryIds - id }
                )
            }

            // Баланс-картки
            BalanceCardsRow(
                openingBalance = state.openingBalance,
                closingBalance = state.closingBalance
            )

            // Контент
            if (filteredTransactions.isEmpty()) {
                EmptyMonthState(sel = state.selectedMonth, isFiltered = isFiltered)
            } else {
                totalsByCurrency.forEach { (currency, totals) ->
                    Text(currency, modifier = Modifier.padding(horizontal = Spacing.lg),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TxMonthSummaryRow(income = totals.income, expense = totals.expense)
                }
                TransactionsList(
                    transactions  = filteredTransactions,
                    bottomPadding = padding.calculateBottomPadding(),
                    onEdit        = { txId -> selectedDetailTx = filteredTransactions.firstOrNull { it.id == txId } }
                )
            }
        }

        // ── FAB «+» ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = padding.calculateBottomPadding() + 16.dp, end = 16.dp)
                .size(56.dp)
                .clip(RoundedCornerShape(OneMoneyTheme.dimens.largeRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { showCategoryPicker = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add, stringResource(R.string.tx_new),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
    }

    // ── Аркуш фільтрів ────────────────────────────────────────────────────
    if (showFilterSheet) {
        TxSearchScreen(
            query              = filterQuery,
            filterTypes        = filterTypes,
            filterAccountIds   = filterAccountIds,
            filterCategoryIds  = filterCategoryIds,
            accounts           = state.accounts,
            expenseCategories  = state.expenseCategories,
            incomeCategories   = state.incomeCategories,
            onQueryChange      = { filterQuery = it },
            onTypesChange      = { filterTypes = it },
            onAccountsChange   = { filterAccountIds = it },
            onCategoriesChange = { filterCategoryIds = it },
            onReset = {
                filterQuery       = ""
                filterTypes       = emptySet()
                filterAccountIds  = emptySet()
                filterCategoryIds = emptySet()
                showFilterSheet   = false
            },
            onDone = { showFilterSheet = false }
        )
    }

    // ── Пікер категорій для швидкого додавання ────────────────────────────
    if (showCategoryPicker) {
        CategoryPickerSheet(
            expenseCategories = state.expenseCategories,
            incomeCategories  = state.incomeCategories,
            accounts          = state.accounts,
            categorySpending  = categorySpending,
            onSelect          = { cat ->
                showCategoryPicker = false
                quickCategory = cat
            },
            onTransfer        = { acc ->
                showCategoryPicker = false
                transferFromAccount = acc
            },
            onDismiss = { showCategoryPicker = false }
        )
    }

    // ── QuickExpenseSheet ─────────────────────────────────────────────────
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

    // ── TransferQuickSheet ────────────────────────────────────────────────
    transferFromAccount?.let { fromAcc ->
        TransferQuickSheet(
            fromAccount = fromAcc,
            allAccounts = state.accounts,
            onSave      = { toAccountId, amount, date ->
                viewModel.recordTransfer(fromAcc.id, toAccountId, amount, date)
                transferFromAccount = null
            },
            onDismiss = { transferFromAccount = null }
        )
    }

    // ── Деталі / редагування транзакції ───────────────────────────────────
    selectedDetailTx?.let { tx ->
        TransactionDetailSheet(
            tx          = tx,
            categories  = state.expenseCategories + state.incomeCategories,
            accounts    = state.accounts,
            onDismiss   = { selectedDetailTx = null },
            onDelete    = { viewModel.deleteTransaction(tx); selectedDetailTx = null },
            onDuplicate = { viewModel.duplicateTransaction(tx); selectedDetailTx = null },
            onSave      = { note, amount, date, categoryId, moveToAccountId ->
                viewModel.updateTransaction(tx, note, amount, date, categoryId, moveToAccountId)
                selectedDetailTx = null
            }
        )
    }
}

// ── Рядок активних фільтрів ───────────────────────────────────────────────────

@Composable
private fun ActiveFilterChipsRow(
    filterQuery:       String,
    filterTypes:       Set<String>,
    filterAccountIds:  Set<Long>,
    filterCategoryIds: Set<Long>,
    accounts:          List<AccountEntity>,
    expenseCategories: List<CategoryEntity>,
    incomeCategories:  List<CategoryEntity>,
    onRemoveQuery:     () -> Unit,
    onRemoveType:      (String) -> Unit,
    onRemoveAccount:   (Long) -> Unit,
    onRemoveCategory:  (Long) -> Unit
) {
    val allCats = expenseCategories + incomeCategories

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // Пошуковий запит
        val txColors = OneMoneyTheme.colors
        if (filterQuery.isNotBlank()) {
            FilterActiveChip(
                label     = "«$filterQuery»",
                color     = FilterQueryColor,
                onRemove  = onRemoveQuery
            )
        }

        // Типи операцій
        filterTypes.forEach { typeName ->
            val (label, color) = when (typeName) {
                "INCOME"   -> stringResource(R.string.tx_income)   to txColors.budgetIncome
                "EXPENSE"  -> stringResource(R.string.tx_expense)  to txColors.budgetExpense
                "TRANSFER" -> stringResource(R.string.tx_transfer) to FilterDefaultColor
                "BORROW"   -> stringResource(R.string.tx_borrow)   to txColors.debtOrange
                else       -> typeName  to FilterDefaultColor
            }
            FilterActiveChip(
                label    = label,
                color    = color,
                onRemove = { onRemoveType(typeName) }
            )
        }

        // Рахунки
        filterAccountIds.forEach { accId ->
            val name = accounts.firstOrNull { it.id == accId }?.name ?: stringResource(R.string.tx_account_fallback)
            FilterActiveChip(
                label    = name,
                color    = FilterAccountColor,
                onRemove = { onRemoveAccount(accId) }
            )
        }

        // Категорії
        filterCategoryIds.forEach { catId ->
            val cat   = allCats.firstOrNull { it.id == catId }
            val name  = cat?.name ?: stringResource(R.string.tx_category)
            val color = cat?.colorHex?.let { hex ->
                parseColorHex(hex, FilterDefaultColor)
            } ?: FilterDefaultColor
            FilterActiveChip(label = name, color = color, onRemove = { onRemoveCategory(catId) })
        }
    }
}

@Composable
private fun FilterActiveChip(label: String, color: Color, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50.dp),
        color = color
    ) {
        Row(
            modifier          = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Color.White)
            Box(
                modifier         = Modifier
                    .size(16.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(10.dp))
            }
        }
    }
}

// ── Карточки балансу ─────────────────────────────────────────────────────────

@Composable
private fun BalanceCardsRow(openingBalance: Double, closingBalance: Double) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BalanceCard(stringResource(R.string.tx_opening_balance), openingBalance, Modifier.weight(1f))
        BalanceCard(stringResource(R.string.tx_closing_balance), closingBalance, Modifier.weight(1f))
    }
}

@Composable
private fun BalanceCard(label: String, amount: Double, modifier: Modifier = Modifier) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(OneMoneyTheme.dimens.cardRadius),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f))
            Spacer(Modifier.height(2.dp))
            Text(
                "${formatMoney(amount)} ₴",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface,
                maxLines   = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Рядок підсумків ───────────────────────────────────────────────────────────

@Composable
private fun TxMonthSummaryRow(income: Double, expense: Double) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SummaryChip("+${formatMoney(income)}", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Modifier.weight(1f))
        SummaryChip("−${formatMoney(expense)}", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryChip(label: String, containerColor: Color, contentColor: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = containerColor) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text(label, color = contentColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ── Порожній стан ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyMonthState(sel: TxSelectedMonth, isFiltered: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 40.dp)) {
            Image(
                painter = painterResource(R.drawable.empty_transactions),
                contentDescription = null,
                modifier = Modifier.size(width = 220.dp, height = 165.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text      = if (isFiltered) stringResource(R.string.tx_empty_filtered)
                            else stringResource(R.string.tx_empty_month, "${stringArrayResource(R.array.month_names)[sel.month]} ${sel.year}"),
                style     = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
            )
        }
    }
}

// ── Список транзакцій ─────────────────────────────────────────────────────────

@Composable
private fun TransactionsList(
    transactions:  List<TransactionWithDetails>,
    bottomPadding: Dp,
    onEdit:        (Long) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(bottom = bottomPadding + 88.dp)) {
        val grouped = transactions.groupBy { tx ->
            DateFormats.dayMonthFull(Date(tx.date))
        }
        grouped.forEach { (dateLabel, txList) ->
            item {
                SectionHeader(dateLabel, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp))
            }
            items(txList) { tx ->
                TransactionListItem(tx = tx, onClick = { onEdit(tx.id) })
            }
        }
    }
}
