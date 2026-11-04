package org.syalosovetskyi.onemoney.ui.transactions

import org.syalosovetskyi.onemoney.util.parseColorHex
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
import org.syalosovetskyi.onemoney.data.db.dao.TransactionWithDetails
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.ui.categories.QuickCategoryPickerSheet
import org.syalosovetskyi.onemoney.ui.categories.categoryIconFor
import org.syalosovetskyi.onemoney.ui.components.calculator.CalcDateSheet
import org.syalosovetskyi.onemoney.ui.components.dialogs.ConfirmationDialog
import org.syalosovetskyi.onemoney.ui.components.calculator.FullDatePickerDialog
import org.syalosovetskyi.onemoney.ui.components.calculator.SharedCalcKeypad
import org.syalosovetskyi.onemoney.ui.components.calculator.rememberCalcState
import org.syalosovetskyi.onemoney.ui.components.calculator.txDateLabelPublic
import org.syalosovetskyi.onemoney.util.formatMoney
import org.syalosovetskyi.onemoney.ui.accounts.currencySymbol
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme
import org.syalosovetskyi.onemoney.ui.theme.AccentIndigo
import org.syalosovetskyi.onemoney.ui.theme.AccentTeal
import org.syalosovetskyi.onemoney.ui.theme.FallbackAccountColor
import org.syalosovetskyi.onemoney.ui.theme.FallbackIconColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionDetailSheet(
    tx:          TransactionWithDetails,
    categories:  List<CategoryEntity> = emptyList(),
    /** Рахунки, на які операцію можна перемістити (переказ замість витрати). */
    accounts:    List<AccountEntity> = emptyList(),
    onDismiss:   () -> Unit,
    onDelete:    () -> Unit,
    onDuplicate: () -> Unit,
    onSave:      (note: String, amount: Double, date: Long, categoryId: Long?, moveToAccountId: Long?) -> Unit
) {
    val calc = rememberCalcState(tx.amount)

    var note         by androidx.compose.runtime.saveable.rememberSaveable(tx.id) { mutableStateOf(tx.note) }
    var selectedDate by androidx.compose.runtime.saveable.rememberSaveable(tx.id) { mutableStateOf(tx.date) }
    var categoryId   by androidx.compose.runtime.saveable.rememberSaveable(tx.id) { mutableStateOf(tx.categoryId) }
    var isDirty      by androidx.compose.runtime.saveable.rememberSaveable(tx.id) { mutableStateOf(false) }
    var showCalc     by remember { mutableStateOf(false) }
    var showDeleteDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showDateSheet    by remember { mutableStateOf(false) }
    var showFullDate     by remember { mutableStateOf(false) }
    var showCatPicker    by remember { mutableStateOf(false) }
    // Переказ + категорія = операція стане витратою/доходом → питаємо підтвердження
    var pendingTransferCat by remember { mutableStateOf<CategoryEntity?>(null) }
    // Витрата + рахунок = переміщення своїх грошей; теж питаємо підтвердження
    var pendingMoveAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var moveToAccountId    by androidx.compose.runtime.saveable.rememberSaveable(tx.id) { mutableStateOf<Long?>(null) }


    val accountColor = remember(tx.accountColor) {
        parseColorHex(tx.accountColor, FallbackAccountColor)
    }
    // Обрана категорія: спершу локальний вибір, інакше та, що прийшла з транзакцією
    val selectedCat = remember(categoryId, categories) {
        categories.firstOrNull { it.id == categoryId }
    }
    val catColor = remember(tx.categoryColor, selectedCat) {
        val hex = selectedCat?.colorHex ?: tx.categoryColor
        hex?.let {
            try { Color(it.toColorInt()) } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                android.util.Log.w("TransactionDetailSheet", "Operation failed", failure)
                null
            }
        }
    }
    // Рахунок, на який операцію щойно позначили перемістити (ще не збережено)
    val movedToAccount = remember(moveToAccountId, accounts) {
        moveToAccountId?.let { id -> accounts.firstOrNull { it.id == id } }
    }
    // Категорія на переказі означає, що операцію перекласифіковано у витрату/дохід
    val isTransfer  = movedToAccount != null ||
        (tx.type == TransactionType.TRANSFER && categoryId == null)
    val effectiveType = when {
        movedToAccount != null              -> TransactionType.TRANSFER
        tx.type != TransactionType.TRANSFER -> tx.type
        selectedCat != null                 -> selectedCat.type
        categoryId != null                  -> TransactionType.EXPENSE
        else                                -> TransactionType.TRANSFER
    }
    val leftColor   = if (isTransfer) AccentTeal else accountColor
    val rightColor  = when {
        isTransfer       -> AccentIndigo
        catColor != null -> catColor
        else             -> FallbackIconColor
    }
    val accentColor = when (effectiveType) {     // колір суми — уніфіковано до семантики
        TransactionType.TRANSFER -> OneMoneyTheme.colors.transferBlue
        TransactionType.INCOME   -> OneMoneyTheme.colors.incomeGreen
        else                     -> OneMoneyTheme.colors.expenseRed
    }
    val keyBg   = MaterialTheme.colorScheme.surfaceVariant
    val screenH = LocalConfiguration.current.screenHeightDp.dp

    ModalBottomSheet(
        onDismissRequest = {
            if (isDirty) onSave(note, tx.amount, selectedDate, categoryId, moveToAccountId)
            else onDismiss()
        },
        sheetState     = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().height(screenH * 0.75f)) {

            Row(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(leftColor)) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                            .size(32.dp).clip(RoundedCornerShape(OneMoneyTheme.dimens.smallRadius))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Outlined.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    Column(modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 8.dp)) {
                        Text(stringResource(R.string.tx_from_account), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        Text(tx.accountName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().background(rightColor)
                        .clickable(enabled = categories.isNotEmpty()) { showCatPicker = true }
                ) {
                    Box(
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                            .size(32.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            when {
                                isTransfer                  -> Icons.Outlined.CreditCard
                                selectedCat != null         -> categoryIconFor(selectedCat.icon)
                                tx.categoryIcon != null     -> categoryIconFor(tx.categoryIcon)
                                else                        -> Icons.Outlined.Category
                            },
                            null, tint = Color.White, modifier = Modifier.size(18.dp)
                        )
                    }
                    if (categories.isNotEmpty()) {
                        Icon(
                            Icons.Outlined.Edit, null,
                            tint     = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(16.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            if (isTransfer) stringResource(R.string.tx_to_account) else stringResource(R.string.tx_category),
                            style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            when {
                                movedToAccount != null  -> movedToAccount.name
                                isTransfer              -> tx.toAccountName ?: "—"
                                selectedCat != null     -> selectedCat.name
                                tx.categoryName != null -> tx.categoryName
                                else                    -> stringResource(R.string.tx_no_category)
                            },
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().clickable { showCalc = !showCalc }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    when (effectiveType) {
                        TransactionType.TRANSFER -> stringResource(R.string.tx_transfer)
                        TransactionType.INCOME   -> stringResource(R.string.tx_income)
                        else                     -> stringResource(R.string.tx_expense)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor
                )
                Text(
                    if (showCalc) calc.displayExpr(currencySymbol(tx.accountCurrency)) else "${formatMoney(tx.amount)} ${currencySymbol(tx.accountCurrency)}",
                    fontSize   = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color      = accentColor,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
            }

            if (!showCalc) {
                OutlinedTextField(
                    value         = note,
                    onValueChange = { note = it; isDirty = true },
                    placeholder   = { Text(stringResource(R.string.tx_notes_hint)) },
                    modifier      = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    singleLine    = true,
                    shape         = RoundedCornerShape(OneMoneyTheme.dimens.cardRadius)
                )
                Spacer(Modifier.height(6.dp))

                Text(
                    txDateLabelPublic(selectedDate),
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                )

                Spacer(Modifier.weight(1f))

                HorizontalDivider()

                Row(
                    modifier              = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(
                        onClick = { showDeleteDialog = true },
                        colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.common_delete))
                    }
                    TextButton(onClick = { showDateSheet = true }) {
                        Icon(Icons.Outlined.CalendarMonth, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.tx_date))
                    }
                    TextButton(onClick = onDuplicate) {
                        Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.tx_duplicate))
                    }
                }
                Spacer(Modifier.height(8.dp))
            } else {
                SharedCalcKeypad(
                    calc         = calc,
                    modifier     = Modifier.weight(1f).fillMaxWidth(),
                    confirmColor = accentColor,
                    onConfirm    = {
                        val amt = calc.result()
                        if (amt > 0) onSave(note, amt, selectedDate, categoryId, moveToAccountId)
                    },
                    row2ExtraKey = {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .clip(RoundedCornerShape(OneMoneyTheme.dimens.keyRadius)).background(keyBg)
                                .clickable { showDateSheet = true },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Outlined.CalendarMonth, null, modifier = Modifier.size(20.dp)) }
                    }
                )
                Text(
                    txDateLabelPublic(selectedDate),
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                )
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmationDialog(
            title     = stringResource(R.string.tx_delete_title),
            message   = stringResource(R.string.tx_delete_message),
            icon      = Icons.Outlined.DeleteForever,
            onConfirm = { showDeleteDialog = false; onDelete() },
            onDismiss = { showDeleteDialog = false }
        )
    }

    if (showDateSheet) {
        CalcDateSheet(
            currentDate     = selectedDate,
            repeatMode      = "NEVER",
            reminderMode    = "NEVER",
            onDateSelected  = { selectedDate = it; isDirty = true; showDateSheet = false },
            onRepeatClick   = { showDateSheet = false },
            onReminderClick = { showDateSheet = false },
            onPickDate      = { showDateSheet = false; showFullDate = true },
            onDismiss       = { showDateSheet = false }
        )
    }
    if (showFullDate) {
        FullDatePickerDialog(
            initial        = selectedDate,
            onDateSelected = { selectedDate = it; isDirty = true; showFullDate = false },
            onDismiss      = { showFullDate = false }
        )
    }

    // ── Вибір категорії операції ──────────────────────────────────────────────
    if (showCatPicker && categories.isNotEmpty()) {
        QuickCategoryPickerSheet(
            categories           = categories,
            selectedCategoryId   = categoryId ?: -1L,
            includeSubcategories = true,
            // Рахунок-джерело у списку не показуємо: переказ сам на себе безглуздий
            transferAccounts     = accounts.filter { it.id != tx.accountId },
            onSelectAccount      = { acc ->
                showCatPicker = false
                pendingMoveAccount = acc
            },
            onSelect             = { cat ->
                showCatPicker = false
                if (tx.type == TransactionType.TRANSFER) {
                    // Переказ стане витратою/доходом і перерахує баланси обох рахунків
                    pendingTransferCat = cat
                } else {
                    categoryId = cat.id
                    isDirty    = true
                }
            },
            onDismiss            = { showCatPicker = false }
        )
    }

    // Витрата стає переміщенням на власний рахунок: гроші не витрачені, а переїхали.
    // Баланси обох рахунків перераховуються, тому питаємо підтвердження.
    pendingMoveAccount?.let { acc ->
        ConfirmationDialog(
            title       = stringResource(R.string.tx_convert_move_title),
            message     = stringResource(R.string.tx_convert_move_message, acc.name),
            icon        = Icons.Outlined.SwapHoriz,
            confirmText = stringResource(R.string.tx_convert_move_confirm),
            destructive = false,
            onConfirm   = {
                moveToAccountId = acc.id
                categoryId      = null
                isDirty         = true
                pendingMoveAccount = null
            },
            onDismiss   = { pendingMoveAccount = null }
        )
    }

    pendingTransferCat?.let { cat ->
        ConfirmationDialog(
            title     = stringResource(R.string.tx_convert_transfer_title),
            message   = stringResource(R.string.tx_convert_transfer_message, cat.name),
            icon        = Icons.Outlined.SwapHoriz,
            confirmText = stringResource(R.string.tx_convert_transfer_confirm),
            destructive = false,
            onConfirm = {
                categoryId = cat.id
                isDirty    = true
                pendingTransferCat = null
            },
            onDismiss = { pendingTransferCat = null }
        )
    }
}
