package org.syalosovetskyi.onemoney.ui.transactions

import org.syalosovetskyi.onemoney.util.parseColorHex
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.outlined.CalendarMonth
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
import org.syalosovetskyi.onemoney.ui.accounts.currencySymbol
import org.syalosovetskyi.onemoney.ui.components.calculator.AccountPickerSheet
import org.syalosovetskyi.onemoney.ui.components.calculator.CalcDateSheet
import org.syalosovetskyi.onemoney.ui.components.calculator.FullDatePickerDialog
import org.syalosovetskyi.onemoney.ui.components.calculator.SharedCalcKeypad
import org.syalosovetskyi.onemoney.ui.components.calculator.rememberCalcState
import org.syalosovetskyi.onemoney.ui.components.calculator.txDateLabelPublic
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme
import org.syalosovetskyi.onemoney.ui.theme.FallbackAccountColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransferQuickSheet(
    fromAccount: AccountEntity,
    allAccounts: List<AccountEntity>,
    onSave:      (toAccountId: Long, amount: Double, date: Long) -> Unit,
    onDismiss:   () -> Unit
) {
    var selectedFrom by remember { mutableStateOf(fromAccount) }
    var selectedTo   by remember { mutableStateOf(allAccounts.firstOrNull { it.id != fromAccount.id }) }
    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var note         by remember { mutableStateOf("") }
    var showDateSheet    by remember { mutableStateOf(false) }
    var showFullDate     by remember { mutableStateOf(false) }
    var showFromAccSheet by remember { mutableStateOf(false) }
    var showToAccSheet   by remember { mutableStateOf(false) }

    val calc = rememberCalcState()

    val fromSymbol = remember(selectedFrom.currency) { currencySymbol(selectedFrom.currency) }

    val fromColor = remember(selectedFrom.colorHex) {
        parseColorHex(selectedFrom.colorHex, FallbackAccountColor)
    }
    val toColor = remember(selectedTo?.colorHex) {
        parseColorHex(selectedTo?.colorHex, FallbackAccountColor)
    }
    val transferColor = OneMoneyTheme.colors.transferBlue   // уніфіковано до семантики (було 5C6BC0)
    val keyBg = MaterialTheme.colorScheme.surfaceVariant
    val screenH = LocalConfiguration.current.screenHeightDp.dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().height(screenH * 0.72f)) {

            Row(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .background(fromColor).clickable { showFromAccSheet = true }
                ) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                            .size(32.dp).clip(RoundedCornerShape(OneMoneyTheme.dimens.smallRadius))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    Column(modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 8.dp)) {
                        Text(stringResource(R.string.tx_from_account), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        Text(selectedFrom.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .background(toColor).clickable { showToAccSheet = true }
                ) {
                    Box(
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                            .size(32.dp).clip(RoundedCornerShape(OneMoneyTheme.dimens.smallRadius))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.CreditCard, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    Column(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 8.dp), horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.tx_to_account), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        Text(selectedTo?.name ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.tx_transfer), style = MaterialTheme.typography.labelMedium, color = transferColor)
                Text(calc.displayExpr(fromSymbol), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = transferColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            OutlinedTextField(
                value = note, onValueChange = { note = it },
                placeholder = { Text(stringResource(R.string.tx_notes_hint)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                singleLine = true, shape = RoundedCornerShape(OneMoneyTheme.dimens.cardRadius)
            )
            Spacer(Modifier.height(6.dp))

            SharedCalcKeypad(
                calc           = calc,
                modifier       = Modifier.weight(1f).fillMaxWidth(),
                currencySymbol = fromSymbol,
                confirmColor   = transferColor,
                onConfirm    = {
                    val amt   = calc.result()
                    val toAcc = selectedTo ?: return@SharedCalcKeypad
                    if (amt > 0) onSave(toAcc.id, amt, selectedDate)
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
            )
        }
    }

    if (showDateSheet) {
        CalcDateSheet(
            currentDate = selectedDate, repeatMode = "NEVER", reminderMode = "NEVER",
            onDateSelected  = { selectedDate = it; showDateSheet = false },
            onRepeatClick   = { showDateSheet = false },
            onReminderClick = { showDateSheet = false },
            onPickDate      = { showDateSheet = false; showFullDate = true },
            onDismiss       = { showDateSheet = false }
        )
    }
    if (showFullDate) {
        FullDatePickerDialog(
            initial        = selectedDate,
            onDateSelected = { selectedDate = it; showFullDate = false },
            onDismiss      = { showFullDate = false }
        )
    }
    if (showFromAccSheet) {
        AccountPickerSheet(
            accounts   = allAccounts.filter { it.id != selectedTo?.id },
            selectedId = selectedFrom.id,
            label      = stringResource(R.string.tx_from_account),
            onSelect   = { acc -> selectedFrom = acc; showFromAccSheet = false },
            onDismiss  = { showFromAccSheet = false }
        )
    }
    if (showToAccSheet) {
        AccountPickerSheet(
            accounts   = allAccounts.filter { it.id != selectedFrom.id },
            selectedId = selectedTo?.id,
            label      = stringResource(R.string.tx_to_account),
            onSelect   = { acc -> selectedTo = acc; showToAccSheet = false },
            onDismiss  = { showToAccSheet = false }
        )
    }
}
