package org.syalosovetskyi.onemoney.ui.transactions

import org.syalosovetskyi.onemoney.util.parseColorHex
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.ui.accounts.accountTypeIcon
import org.syalosovetskyi.onemoney.ui.categories.categoryIconFor
import org.syalosovetskyi.onemoney.util.formatMoney
import org.syalosovetskyi.onemoney.ui.accounts.currencySymbol
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme
import org.syalosovetskyi.onemoney.ui.theme.NegativeAmountColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryPickerSheet(
    expenseCategories: List<CategoryEntity>,
    incomeCategories:  List<CategoryEntity>,
    accounts:          List<AccountEntity>,
    categorySpending:  Map<Long?, Double>,
    initialTab:        Int = 1,
    currentType:       TransactionType? = null,
    onSelect:          (CategoryEntity) -> Unit,
    onTransfer:        (AccountEntity) -> Unit,
    onDismiss:         () -> Unit
) {
    val screenH    = LocalConfiguration.current.screenHeightDp.dp
    // selectedTab is always created (Compose rule: no remember inside conditionals)
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    data class TabDef(val type: TransactionType?, val label: String,
                      val icon: androidx.compose.ui.graphics.vector.ImageVector)
    val tabDefs = listOf(
        TabDef(TransactionType.INCOME,  stringResource(R.string.tx_income),   Icons.Default.ArrowUpward),
        TabDef(TransactionType.EXPENSE, stringResource(R.string.tx_expense), Icons.Default.ArrowDownward),
        TabDef(null,                    stringResource(R.string.tx_transfer), Icons.Default.SwapHoriz)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = MaterialTheme.colorScheme.surface
    ) {
        if (currentType != null) {
            // ── Упрощённый режим: один тип, без вкладок ──────────────────────
            val typeIcon  = when (currentType) {
                TransactionType.INCOME   -> Icons.Default.ArrowUpward
                TransactionType.TRANSFER -> Icons.Default.SwapHoriz
                else                     -> Icons.Default.ArrowDownward
            }
            val typeLabel = when (currentType) {
                TransactionType.INCOME   -> stringResource(R.string.tx_income)
                TransactionType.TRANSFER -> stringResource(R.string.tx_transfer)
                else                     -> stringResource(R.string.tx_expense)
            }
            Column(modifier = Modifier.fillMaxWidth().height(screenH * 0.55f)) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(typeIcon, null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(typeLabel,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider()

                if (currentType == TransactionType.TRANSFER) {
                    val totals = org.syalosovetskyi.onemoney.util.accountCurrencyTotals(accounts)
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.tx_accounts), style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Text(totals.entries.joinToString(" · ") { (currency, total) -> "${formatMoney(total.net)} $currency" }, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (totals.values.any { it.net < 0 }) NegativeAmountColor else MaterialTheme.colorScheme.onSurface)
                        }
                        accounts.forEach { acc -> AccountPickerRow(account = acc, onClick = { onTransfer(acc) }) }
                    }
                } else {
                    val cats = if (currentType == TransactionType.INCOME) incomeCategories else expenseCategories
                    CategoryGrid(cats, categorySpending, onSelect)
                }
            }
        } else {
            // ── Режим з вкладками (для фільтра транзакцій) ───────────────────

            Column(modifier = Modifier.fillMaxWidth().height(screenH * 0.67f)) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor   = MaterialTheme.colorScheme.surface,
                    contentColor     = MaterialTheme.colorScheme.primary
                ) {
                    tabDefs.forEachIndexed { i, tab ->
                        val active = selectedTab == i
                        val tint = if (active) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        Tab(
                            selected = active,
                            onClick  = { selectedTab = i },
                            text     = {
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(26.dp).clip(CircleShape)
                                            .border(1.5.dp, tint, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) { Icon(tab.icon, null, tint = tint, modifier = Modifier.size(14.dp)) }
                                    Text(tab.label,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                        color      = tint)
                                }
                            }
                        )
                    }
                }

                val tabType   = tabDefs[selectedTab].type
                val tabCats   = when (tabType) {
                    TransactionType.INCOME  -> incomeCategories
                    TransactionType.EXPENSE -> expenseCategories
                    else                   -> emptyList()
                }

                if (tabType == null) {
                    val totals = org.syalosovetskyi.onemoney.util.accountCurrencyTotals(accounts)
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.tx_accounts), style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Text(totals.entries.joinToString(" · ") { (currency, total) -> "${formatMoney(total.net)} $currency" }, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (totals.values.any { it.net < 0 }) NegativeAmountColor else MaterialTheme.colorScheme.onSurface)
                        }
                        accounts.forEach { acc -> AccountPickerRow(acc) { onTransfer(acc) } }
                    }
                } else {
                    CategoryGrid(tabCats, categorySpending, onSelect)
                }
            }
        }
    }
}

@Composable
private fun CategoryGrid(
    categories:      List<CategoryEntity>,
    categorySpending: Map<Long?, Double>,
    onSelect:        (CategoryEntity) -> Unit
) {
    LazyVerticalGrid(
        columns               = GridCells.Fixed(4),
        modifier              = Modifier.fillMaxSize(),
        contentPadding        = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement   = Arrangement.spacedBy(12.dp)
    ) {
        items(categories) { cat ->
            CategoryPickerCell(cat = cat, amount = categorySpending[cat.id] ?: 0.0, onClick = { onSelect(cat) })
        }
        item {
            Column(modifier = Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(30.dp))
                Box(
                    modifier = Modifier.size(54.dp).clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, null,
                        tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
internal fun CategoryPickerCell(
    cat:    CategoryEntity,
    amount: Double,
    onClick: () -> Unit
) {
    val catColor = remember(cat.colorHex) {
        parseColorHex(cat.colorHex, Color(0xFFFF5722))
    }
    val hasSpending = amount > 0
    Column(
        modifier            = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            cat.name,
            style      = MaterialTheme.typography.labelSmall,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
            textAlign  = TextAlign.Center,
            color      = MaterialTheme.colorScheme.onSurface,
            modifier   = Modifier.fillMaxWidth().heightIn(min = 30.dp),
            lineHeight = 14.sp
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(if (hasSpending) catColor else catColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                categoryIconFor(cat.icon), null,
                tint     = if (hasSpending) Color.White else catColor,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${formatMoney(amount)} ₴",
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = if (hasSpending) FontWeight.Bold else FontWeight.Normal,
            color      = if (hasSpending) catColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            textAlign  = TextAlign.Center,
            modifier   = Modifier.fillMaxWidth(),
            maxLines   = 1
        )
    }
}

@Composable
internal fun AccountPickerRow(account: AccountEntity, onClick: () -> Unit) {
    val accColor = remember(account.colorHex) {
        parseColorHex(account.colorHex, Color(0xFF3949AB))
    }
    val balColor = when {
        account.balance < 0 -> NegativeAmountColor
        account.balance > 0 -> MaterialTheme.colorScheme.onSurface
        else                -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Box(modifier = Modifier.size(52.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(OneMoneyTheme.dimens.cardRadius))
                        .background(accColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        accountTypeIcon(account.type),
                        null,
                        tint     = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                if (account.isDefault) {
                    Icon(
                        Icons.Default.Star,
                        null,
                        tint     = Color(0xFFFFB300),
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.BottomStart)
                    )
                }
            }
        },
        headlineContent = {
            Text(account.name, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            Text(
                "${formatMoney(account.balance)} ${currencySymbol(account.currency)}",
                color = balColor,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (account.balance != 0.0) FontWeight.Bold else FontWeight.Normal
            )
        }
    )
}
