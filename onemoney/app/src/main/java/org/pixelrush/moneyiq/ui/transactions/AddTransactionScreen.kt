package org.syalosovetskyi.onemoney.ui.transactions

import androidx.core.graphics.toColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.ui.components.currency.CurrencyBottomSheet
import org.syalosovetskyi.onemoney.ui.categories.categoryIconFor
import org.syalosovetskyi.onemoney.ui.components.calculator.SharedCalcKeypad
import org.syalosovetskyi.onemoney.ui.components.calculator.rememberCalcState
import org.syalosovetskyi.onemoney.ui.settings.data.CURRENCIES_ALL
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme
import org.syalosovetskyi.onemoney.ui.theme.AccentIndigo
import org.syalosovetskyi.onemoney.ui.theme.AccentTeal
import org.syalosovetskyi.onemoney.ui.theme.FallbackIconColor
import java.text.SimpleDateFormat
import java.util.*

private val PanelFromColor    = AccentIndigo   // рахунок-джерело (витрата/дохід)
private val TransferFromColor = AccentTeal     // рахунок-джерело (переказ)
private val TransferToColor   = AccentIndigo   // рахунок-призначення

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    onNavigateBack: () -> Unit,
    viewModel: TransactionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val calc  = rememberCalcState()

    // Ініціалізуємо калькулятор у режимі редагування
    var editInitialized by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.isEditMode, state.amount) {
        if (state.isEditMode && !editInitialized && state.amount.isNotBlank()) {
            val amt = state.amount.replace(",", ".").toDoubleOrNull() ?: return@LaunchedEffect
            calc.currentStr = when {
                amt == amt.toLong().toDouble() -> amt.toLong().toString()
                else -> amt.toBigDecimal().stripTrailingZeros().toPlainString().replace(".", ",")
            }
            editInitialized = true
        }
    }

    LaunchedEffect(state.saved) { if (state.saved) onNavigateBack() }

    var showDeleteDialog    by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showDatePicker      by remember { mutableStateOf(false) }
    var showFromAccPicker   by remember { mutableStateOf(false) }
    var showCatPicker       by remember { mutableStateOf(false) }
    var showCurrencyPicker  by remember { mutableStateOf(false) }

    // Єдиний BackHandler — перехоплює back доки відкритий будь-який sheet/dialog.
    // Розміщення ПЕРЕД Scaffold гарантує, що він залишається активним навіть під час
    // анімації закриття ModalBottomSheet (уникаємо "витікання" back у NavController).
    BackHandler(enabled = showCatPicker || showCurrencyPicker || showFromAccPicker || showDatePicker || showDeleteDialog) {
        when {
            showCatPicker      -> showCatPicker = false
            showCurrencyPicker -> showCurrencyPicker = false
            showFromAccPicker  -> showFromAccPicker = false
            showDatePicker     -> showDatePicker = false
            showDeleteDialog   -> showDeleteDialog = false
        }
    }

    val isTransfer = state.type == TransactionType.TRANSFER

    val fromAccount  = state.accounts.firstOrNull { it.id == state.selectedAccountId }
    val toAccount    = state.accounts.firstOrNull { it.id == state.selectedToAccountId }

    var selectedCurrency by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(fromAccount?.currency ?: "UAH") }
    LaunchedEffect(fromAccount?.currency) {
        selectedCurrency = fromAccount?.currency ?: "UAH"
    }
    val currencySymbol = remember(selectedCurrency) {
        CURRENCIES_ALL.find { it.code == selectedCurrency }?.symbol ?: selectedCurrency
    }

    val catType      = if (state.type == TransactionType.INCOME) TransactionType.INCOME else TransactionType.EXPENSE
    val fromCategory = state.categories.filter { it.type == catType && !it.archived }
                           .firstOrNull { it.id == state.selectedCategoryId }

    val fromCatColor = remember(fromCategory?.colorHex) {
        fromCategory?.colorHex?.let {
            try { Color(it.toColorInt()) } catch (failure: Exception) {
            android.util.Log.w("AddTransactionScreen", "Operation failed", failure)
            null
        }
        }
    }

    val leftPanelColor  = if (isTransfer) TransferFromColor else PanelFromColor
    val rightPanelColor = when {
        isTransfer -> TransferToColor
        else       -> fromCatColor ?: FallbackIconColor
    }
    val accentColor = when (state.type) {
        TransactionType.TRANSFER -> TransferToColor
        TransactionType.INCOME   -> OneMoneyTheme.colors.incomeGreen
        else                     -> OneMoneyTheme.colors.expenseRed
    }

    fun onSave() {
        val v = calc.result()
        viewModel.setAmount(if (v <= 0.0) "" else v.toBigDecimal().stripTrailingZeros().toPlainString())
        viewModel.save()
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // ── 1. Навігація + перемикач типів ───────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
                listOf(
                    TransactionType.EXPENSE  to stringResource(R.string.tx_expense),
                    TransactionType.INCOME   to stringResource(R.string.tx_income),
                    TransactionType.TRANSFER to stringResource(R.string.tx_transfer)
                ).forEach { (type, label) ->
                    val selected = state.type == type
                    TextButton(
                        onClick          = { viewModel.setType(type) },
                        contentPadding   = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            label,
                            style      = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color      = if (selected) accentColor
                                         else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (state.isEditMode) {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // ── 2. Двопанельний заголовок ─────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().height(80.dp)) {

                // Ліва панель: рахунок-джерело
                Box(
                    modifier = Modifier
                        .weight(1f).fillMaxHeight()
                        .background(leftPanelColor)
                        .clickable { showFromAccPicker = true }
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)
                            .size(34.dp).clip(RoundedCornerShape(OneMoneyTheme.dimens.smallRadius))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Column(modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 8.dp)) {
                        Text(stringResource(R.string.tx_from_account), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        Text(
                            fromAccount?.name ?: "—",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(expanded = showFromAccPicker, onDismissRequest = { showFromAccPicker = false }) {
                        state.accounts.forEach { acc ->
                            DropdownMenuItem(
                                text    = { Text(acc.name) },
                                onClick = { viewModel.setAccount(acc.id); showFromAccPicker = false }
                            )
                        }
                    }
                }

                // Права панель: рахунок-призначення (переказ) або категорія
                Box(
                    modifier = Modifier
                        .weight(1f).fillMaxHeight()
                        .background(rightPanelColor)
                        .clickable { showCatPicker = true }
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart).padding(top = 8.dp, start = 8.dp)
                            .size(34.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            when {
                                isTransfer            -> Icons.Outlined.CreditCard
                                fromCategory != null  -> categoryIconFor(fromCategory.icon)
                                else                  -> Icons.Outlined.Category
                            },
                            null, tint = Color.White, modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            if (isTransfer) stringResource(R.string.tx_to_account) else stringResource(R.string.tx_to_category),
                            style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            when {
                                isTransfer           -> toAccount?.name ?: stringResource(R.string.tx_choose)
                                fromCategory != null -> fromCategory.name
                                else                 -> stringResource(R.string.tx_no_category)
                            },
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // ── 3. Сума ───────────────────────────────────────────────────────
            Column(
                modifier            = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    when (state.type) {
                        TransactionType.TRANSFER -> stringResource(R.string.tx_transfer)
                        TransactionType.INCOME   -> stringResource(R.string.tx_income)
                        else                     -> stringResource(R.string.tx_expense)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text       = calc.displayExprNoSymbol(),
                        fontSize   = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color      = accentColor,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Text(
                        text       = " $currencySymbol",
                        fontSize   = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color      = accentColor,
                        maxLines   = 1,
                        modifier   = Modifier.clickable { showCurrencyPicker = true }
                    )
                }
            }

            // ── 4. Нотатки ────────────────────────────────────────────────────
            OutlinedTextField(
                value         = state.note,
                onValueChange = viewModel::setNote,
                placeholder   = { Text(stringResource(R.string.tx_notes_hint)) },
                modifier      = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                singleLine    = true,
                shape         = RoundedCornerShape(OneMoneyTheme.dimens.cardRadius)
            )

            Spacer(Modifier.height(6.dp))

            // ── 5. Калькулятор ────────────────────────────────────────────────
            val keyBg = MaterialTheme.colorScheme.surfaceVariant
            SharedCalcKeypad(
                calc            = calc,
                modifier        = Modifier.weight(1f).fillMaxWidth(),
                currencySymbol  = currencySymbol,
                confirmColor    = accentColor,
                onConfirm       = { onSave() },
                onCurrencyClick = { showCurrencyPicker = true },
                row2ExtraKey    = {
                    Box(
                        modifier = Modifier
                            .weight(1f).fillMaxHeight()
                            .clip(RoundedCornerShape(OneMoneyTheme.dimens.keyRadius))
                            .background(keyBg)
                            .clickable { showDatePicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.CalendarMonth, null, modifier = Modifier.size(20.dp))
                    }
                }
            )

            // ── 6. Дата ───────────────────────────────────────────────────────
            Text(
                text      = formatTxDate(state.date),
                style     = MaterialTheme.typography.bodySmall,
                color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth().padding(vertical = 5.dp)
            )
        }
    }

    // Вибір категорії / рахунку-призначення
    if (showCatPicker) {
        CategoryPickerSheet(
            expenseCategories = state.categories.filter { it.type == TransactionType.EXPENSE && !it.archived },
            incomeCategories  = state.categories.filter { it.type == TransactionType.INCOME && !it.archived },
            accounts          = state.accounts,
            categorySpending  = emptyMap(),
            currentType       = state.type,
            onSelect   = { cat ->
                viewModel.setType(cat.type)
                viewModel.setCategory(cat.id)
                showCatPicker = false
            },
            onTransfer = { acc ->
                viewModel.setToAccount(acc.id)
                viewModel.setType(TransactionType.TRANSFER)
                showCatPicker = false
            },
            onDismiss  = { showCatPicker = false }
        )
    }

    // Вибір валюти
    if (showCurrencyPicker) {
        CurrencyBottomSheet(
            selected  = selectedCurrency,
            title     = stringResource(R.string.tx_currency_title),
            onSelect  = { selectedCurrency = it; showCurrencyPicker = false },
            onDismiss = { showCurrencyPicker = false }
        )
    }

    // Вибір дати
    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = state.date)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { viewModel.setDate(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.common_cancel)) } }
        ) {
            DatePicker(state = dateState)
        }
    }

    // Видалення
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon  = { Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.tx_delete_title)) },
            text  = { Text(stringResource(R.string.tx_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; viewModel.delete() },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

@Composable
private fun formatTxDate(date: Long): String {
    val fmt  = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    val cal  = Calendar.getInstance().apply { timeInMillis = date }
    val now  = Calendar.getInstance()
    val yest = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val short = fmt.format(Date(date))
    return when {
        sameDayTx(cal, now)  -> stringResource(R.string.tx_today, short)
        sameDayTx(cal, yest) -> stringResource(R.string.tx_yesterday, short)
        else                 -> short
    }
}

private fun sameDayTx(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
    a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
