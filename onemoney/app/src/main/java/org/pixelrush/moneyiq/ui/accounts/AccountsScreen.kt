package org.syalosovetskyi.onemoney.ui.accounts

import org.syalosovetskyi.onemoney.util.parseColorHex
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import org.syalosovetskyi.onemoney.ui.settings.data.CURRENCIES_ALL
import org.syalosovetskyi.onemoney.data.db.entities.AccountType
import org.syalosovetskyi.onemoney.util.formatMoney
import org.syalosovetskyi.onemoney.ui.main.horizontalSwipe
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.theme.OnLightColor
import org.syalosovetskyi.onemoney.ui.theme.FallbackAccountColor
import org.syalosovetskyi.onemoney.ui.theme.StarGoldColor

// ── Main screen ───────────────────────────────────────────────────────────────

@Composable
fun AccountsScreen(
    padding:        PaddingValues = PaddingValues(),
    onNavigateBack: () -> Unit    = {},
    embeddedMode:   Boolean       = false,
    onRequestAdd:   () -> Unit    = {},
    onAddIncome:    (AccountEntity) -> Unit = {},
    onAddExpense:   (AccountEntity) -> Unit = {},
    onAddTransfer:  (AccountEntity) -> Unit = {},
    onViewTx:       (AccountEntity) -> Unit = {},
    viewModel:      AccountsViewModel = hiltViewModel()
) {
    val state          by viewModel.state.collectAsState()
    var selectedSubTab by remember { mutableIntStateOf(0) }
    var editAccount    by remember { mutableStateOf<AccountEntity?>(null) }
    var actionAccount  by remember { mutableStateOf<AccountEntity?>(null) }
    var adjustAccount  by remember { mutableStateOf<AccountEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = if (embeddedMode) 0.dp else padding.calculateTopPadding())
            .horizontalSwipe(
                onSwipeLeft  = { selectedSubTab = 1 },
                onSwipeRight = { selectedSubTab = 0 }
            )
    ) {
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor   = MaterialTheme.colorScheme.surface,
            contentColor     = MaterialTheme.colorScheme.primary,
            divider          = {
                HorizontalDivider(
                    thickness = 1.dp,
                    color     = MaterialTheme.colorScheme.outlineVariant
                )
            }
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick  = { selectedSubTab = 0 },
                icon     = { Icon(Icons.Outlined.AccountBalanceWallet, null, modifier = Modifier.size(20.dp)) },
                text     = {
                    Text(
                        stringResource(R.string.acc_tab_accounts),
                        fontWeight = if (selectedSubTab == 0) FontWeight.SemiBold else FontWeight.Normal,
                        style      = MaterialTheme.typography.bodyMedium
                    )
                }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick  = { selectedSubTab = 1 },
                icon     = { Icon(Icons.Outlined.BarChart, null, modifier = Modifier.size(20.dp)) },
                text     = {
                    Text(
                        stringResource(R.string.acc_tab_finances),
                        fontWeight = if (selectedSubTab == 1) FontWeight.SemiBold else FontWeight.Normal,
                        style      = MaterialTheme.typography.bodyMedium
                    )
                }
            )
        }

        when (selectedSubTab) {
            0 -> AccountsListTab(
                    state         = state,
                    bottomPadding = padding.calculateBottomPadding(),
                    onAdd         = onRequestAdd,
                    onTap         = { actionAccount = it },
                    onDelete      = { viewModel.delete(it) },
                    onSetDefault  = { viewModel.setDefault(it) }
                 )
            1 -> MyFinancesTab(
                    state         = state,
                    bottomPadding = padding.calculateBottomPadding()
                 )
        }
    }

    // Action sheet
    actionAccount?.let { acc ->
        AccountActionSheet(
            account         = acc,
            onDismiss       = { actionAccount = null },
            onEdit          = { editAccount = acc },
            onAdjustBalance = { adjustAccount = acc },
            onTransactions  = { onViewTx(acc) },
            onIncome        = { onAddIncome(acc) },
            onExpense       = { onAddExpense(acc) },
            onTransfer      = { onAddTransfer(acc) },
            onSetDefault    = { viewModel.setDefault(acc) }
        )
    }

    // Edit form
    editAccount?.let { acc ->
        AccountFormSheet(
            initialType = acc.type,
            existing    = acc,
            onSave      = { name, type, balance, color, currency, description, includeInTotal, icon, creditLimit ->
                viewModel.update(
                    acc.copy(
                        name           = name,
                        type           = type,
                        balance        = balance,
                        colorHex       = color,
                        currency       = currency,
                        description    = description,
                        includeInTotal = includeInTotal,
                        icon           = icon,
                        creditLimit    = creditLimit
                    )
                )
                editAccount = null
            },
            onDismiss = { editAccount = null }
        )
    }

    // Adjust balance
    adjustAccount?.let { acc ->
        org.syalosovetskyi.onemoney.ui.components.calculator.AmountCalculatorSheet(
            initial        = acc.balance,
            currencySymbol = currencySymbol(acc.currency),
            title          = stringResource(R.string.acc_field_balance),
            onResult       = { newBalance ->
                viewModel.update(acc.copy(balance = newBalance))
                adjustAccount = null
            },
            onDismiss = { adjustAccount = null }
        )
    }
}

// ── Вкладка "Рахунки" ─────────────────────────────────────────────────────────

@Composable
private fun AccountsListTab(
    state:         AccountsUiState,
    bottomPadding: Dp,
    onAdd:         () -> Unit,
    onTap:         (AccountEntity) -> Unit,
    onDelete:      (AccountEntity) -> Unit,
    onSetDefault:  (AccountEntity) -> Unit
) {
    LazyColumn(
        contentPadding      = PaddingValues(
            start  = Spacing.lg, end = Spacing.lg,
            top    = Spacing.sm, bottom = bottomPadding + Spacing.lg
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.acc_tab_accounts),
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary
                )
                Text(
                    formatMoney(state.totalBalance),
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = if (state.totalBalance < 0) MaterialTheme.colorScheme.error
                                 else MaterialTheme.colorScheme.primary
                )
            }
        }

        items(state.accounts) { account ->
            AccountListItem(
                account      = account,
                onTap        = { onTap(account) },
                onDelete     = { onDelete(account) },
                onSetDefault = { onSetDefault(account) }
            )
            Spacer(Modifier.height(4.dp))
        }

        item {
            Spacer(Modifier.height(4.dp))
            AddAccountItem(onClick = onAdd)
        }
    }
}

// ── Елемент рахунку ───────────────────────────────────────────────────────────

@Composable
private fun AccountListItem(
    account:      AccountEntity,
    onTap:        () -> Unit,
    onDelete:     () -> Unit,
    onSetDefault: () -> Unit
) {
    val accentColor = remember(account.colorHex) {
        parseColorHex(account.colorHex, FallbackAccountColor)
    }
    var showMenu          by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val dimens = OneMoneyTheme.dimens
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.largeRadius))
            .clickable { onTap() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccountIconBox(account = account, accentColor = accentColor)

        Spacer(Modifier.width(Spacing.lg))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                account.name,
                style     = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
                color     = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            val balColor = when {
                account.balance < 0    -> MaterialTheme.colorScheme.error
                account.balance == 0.0 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                else                   -> MaterialTheme.colorScheme.onSurface
            }
            Text(
                "${formatMoney(account.balance)} ${currencySymbol(account.currency)}",
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color      = balColor
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Default.MoreVert, null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (!account.isDefault) {
                    DropdownMenuItem(
                        text        = { Text(stringResource(R.string.acc_make_default)) },
                        leadingIcon = { Icon(Icons.Default.Star, null, tint = StarGoldColor) },
                        onClick     = { onSetDefault(); showMenu = false }
                    )
                }
                DropdownMenuItem(
                    text        = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = { showMenu = false; showDeleteConfirm = true }
                )
            }
        }
    }

    // ── Підтвердження видалення (операції рахунку видаляються каскадно) ────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title   = { Text(stringResource(R.string.acc_delete_title, account.name)) },
            text    = { Text(stringResource(R.string.acc_delete_message)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

// ── Квадратна іконка рахунку з зірочкою ──────────────────────────────────────

@Composable
private fun AccountIconBox(account: AccountEntity, accentColor: Color) {
    val currencySymbol = remember(account.currency) {
        CURRENCIES_ALL.find { it.code == account.currency }?.symbol?.take(2) ?: account.currency.take(2)
    }
    val isLightBg  = accentColor.luminance() > 0.5f
    val iconTint   = if (isLightBg) OnLightColor else Color.White
    val badgeColor = if (isLightBg) OnLightColor else accentColor

    Box(modifier = Modifier.size(64.dp)) {
        Box(
            modifier         = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(OneMoneyTheme.dimens.cardRadiusAlt))
                .background(accentColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = accountIconFromKey(account.icon),
                contentDescription = null,
                tint        = iconTint,
                modifier    = Modifier.size(28.dp)
            )
        }
        Box(
            modifier         = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 2.dp, y = 2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = currencySymbol,
                color      = badgeColor,
                fontSize   = 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines   = 1
            )
        }
        if (account.isDefault) {
            Box(
                modifier         = Modifier
                    .size(20.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-2).dp, y = 2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Star, stringResource(R.string.acc_default),
                    tint     = StarGoldColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ── Кнопка "Додати рахунок" ───────────────────────────────────────────────────

@Composable
private fun AddAccountItem(onClick: () -> Unit) {
    val dashColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneMoneyTheme.dimens.largeRadius))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(64.dp)) {
            Box(
                modifier         = Modifier
                    .size(60.dp)
                    .dashedBorder(
                        color        = dashColor,
                        cornerRadius = 14.dp,
                        dashWidth    = 8.dp,
                        dashGap      = 5.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add, null,
                    tint     = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(Modifier.width(Spacing.lg))
        Text(
            stringResource(R.string.acc_add),
            style      = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

// ── Вкладка "Мої фінанси" ─────────────────────────────────────────────────────

@Composable
private fun MyFinancesTab(
    state:         AccountsUiState,
    bottomPadding: Dp
) {
    val totals = org.syalosovetskyi.onemoney.util.accountCurrencyTotals(state.accounts)
    val totalAssets = totals.entries.joinToString("\n") { (currency, total) -> formatMoney(total.assets, currency) }
    val totalDebts = totals.entries.joinToString("\n") { (currency, total) -> formatMoney(total.debts, currency) }
    val netText = totals.entries.joinToString("\n") { (currency, total) -> formatMoney(total.net, currency) }
    val hasDebt = totals.values.any { it.net < 0 }

    LazyColumn(
        contentPadding = PaddingValues(
            start  = 16.dp, end = 16.dp,
            top    = 16.dp, bottom = bottomPadding + 16.dp
        )
    ) {
        item {
            Text(
                stringResource(R.string.acc_tab_finances),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary,
                modifier   = Modifier.padding(bottom = Spacing.md)
            )

            // ── Таблиця активи / борги ──────────────────────────────────────
            Card(
                shape  = RoundedCornerShape(OneMoneyTheme.dimens.cardRadius),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    // Header row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Box(Modifier.weight(0.18f).padding(vertical = 10.dp))
                        FinanceHeaderCell(stringResource(R.string.acc_assets), Modifier.weight(1f))
                        VerticalDividerLine()
                        FinanceHeaderCell(stringResource(R.string.acc_debts), Modifier.weight(1f))
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Data row
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier         = Modifier
                                .weight(0.18f)
                                .padding(vertical = Spacing.lg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "=",
                                style      = MaterialTheme.typography.titleMedium,
                                color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        FinanceValueCell(totalAssets, Modifier.weight(1f))
                        VerticalDividerLine()
                        FinanceValueCell(totalDebts, Modifier.weight(1f), isDebt = true)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Net row
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            netText,
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color      = if (hasDebt) MaterialTheme.colorScheme.error
                                         else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FinanceHeaderCell(text: String, modifier: Modifier) {
    Box(
        modifier         = modifier.padding(vertical = 10.dp, horizontal = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun FinanceValueCell(amount: String, modifier: Modifier, isDebt: Boolean = false) {
    Box(
        modifier         = modifier.padding(vertical = Spacing.lg, horizontal = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            amount,
            style      = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color      = when {
                isDebt        -> MaterialTheme.colorScheme.error
                else          -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun VerticalDividerLine() {
    Box(
        Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

// ── Helper functions ──────────────────────────────────────────────────────────

internal fun accountTypeIcon(type: AccountType): ImageVector = when (type) {
    AccountType.CASH       -> Icons.Outlined.Wallet
    AccountType.CARD       -> Icons.Outlined.CreditCard
    AccountType.SAVING     -> Icons.Outlined.Savings
    AccountType.INVESTMENT -> Icons.AutoMirrored.Outlined.TrendingUp
    AccountType.DEBT       -> Icons.Outlined.MoneyOff
    AccountType.OTHER      -> Icons.Outlined.AccountBalance
}

// Modifier — пунктирна рамка
private fun Modifier.dashedBorder(
    color:        Color,
    cornerRadius: Dp,
    dashWidth:    Dp,
    dashGap:      Dp,
    strokeWidth:  Dp = 1.5.dp
): Modifier = this.drawBehind {
    val cr = cornerRadius.toPx()
    val sw = strokeWidth.toPx()
    val dw = dashWidth.toPx()
    val dg = dashGap.toPx()
    drawRoundRect(
        color        = color,
        size         = Size(size.width, size.height),
        cornerRadius = CornerRadius(cr, cr),
        style        = Stroke(width = sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dw, dg), 0f))
    )
}
