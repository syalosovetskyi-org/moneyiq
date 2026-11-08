package org.syalosovetskyi.onemoney.ui.components.calculator

import org.syalosovetskyi.onemoney.util.parseColorHex
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import org.syalosovetskyi.onemoney.ui.settings.data.CURRENCIES_ALL
import org.syalosovetskyi.onemoney.util.formatMoney
import org.syalosovetskyi.onemoney.ui.accounts.currencySymbol
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme
import org.syalosovetskyi.onemoney.ui.theme.FallbackAccountColor
import org.syalosovetskyi.onemoney.ui.theme.OnLightColor
import java.text.SimpleDateFormat
import java.util.*


// ── Date label helpers ────────────────────────────────────────────────────────

@Composable
fun txDateLabelPublic(date: Long): String = txDateLabel(date)

@Composable
internal fun txDateLabel(date: Long): String {
    val fmt  = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    val cal  = Calendar.getInstance().apply { timeInMillis = date }
    val now  = Calendar.getInstance()
    val yest = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val short = fmt.format(Date(date))
    return when {
        sameDay(cal, now)  -> "${stringResource(R.string.date_today)}, $short"
        sameDay(cal, yest) -> "${stringResource(R.string.date_yesterday)}, $short"
        else               -> short
    }
}

internal fun sameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
    a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

// ── Repeat options ────────────────────────────────────────────────────────────

private val REPEAT_OPTIONS = listOf(
    "NEVER"          to R.string.rep_never,
    "DAILY"          to R.string.rep_daily,
    "EVERY_2_DAYS"   to R.string.rep_every_2_days,
    "WEEKDAYS"       to R.string.rep_weekdays,
    "WEEKENDS"       to R.string.rep_weekends,
    "WEEKLY"         to R.string.rep_weekly,
    "EVERY_2_WEEKS"  to R.string.rep_every_2_weeks,
    "EVERY_4_WEEKS"  to R.string.rep_every_4_weeks,
    "MONTHLY"        to R.string.rep_monthly,
    "EVERY_2_MONTHS" to R.string.rep_every_2_months,
    "EVERY_3_MONTHS" to R.string.rep_every_3_months,
    "EVERY_6_MONTHS" to R.string.rep_every_6_months,
    "YEARLY"         to R.string.rep_yearly
)

internal fun repeatLabelResFor(mode: String) =
    REPEAT_OPTIONS.firstOrNull { it.first == mode }?.second ?: R.string.rep_never

// ── Reminder options ──────────────────────────────────────────────────────────

private val REMINDER_OPTIONS = listOf(
    "NEVER"    to R.string.rep_never,
    "SAME_DAY" to R.string.rem_same_day,
    "1_DAY"    to R.string.rem_1_day,
    "2_DAYS"   to R.string.rem_2_days,
    "3_DAYS"   to R.string.rem_3_days,
    "4_DAYS"   to R.string.rem_4_days,
    "5_DAYS"   to R.string.rem_5_days,
    "6_DAYS"   to R.string.rem_6_days,
    "7_DAYS"   to R.string.rem_7_days
)

internal fun reminderLabelResFor(mode: String) =
    REMINDER_OPTIONS.firstOrNull { it.first == mode }?.second ?: R.string.rep_never

// ── Date sheet ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalcDateSheet(
    currentDate:     Long,
    repeatMode:      String,
    reminderMode:    String,
    onDateSelected:  (Long) -> Unit,
    onRepeatClick:   () -> Unit,
    onReminderClick: () -> Unit,
    onPickDate:      () -> Unit,
    onDismiss:       () -> Unit
) {
    val todayMs     = System.currentTimeMillis()
    val yesterdayMs = todayMs - 86_400_000L
    val isToday     = sameDay(
        Calendar.getInstance().apply { timeInMillis = currentDate },
        Calendar.getInstance()
    )
    val isYesterday = sameDay(
        Calendar.getInstance().apply { timeInMillis = currentDate },
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    )
    val dFmt = SimpleDateFormat("d MMMM", Locale.getDefault())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.date_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Surface(
                onClick   = onPickDate,
                shape     = RoundedCornerShape(OneMoneyTheme.dimens.cardRadiusAlt),
                color     = MaterialTheme.colorScheme.surfaceVariant,
                modifier  = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier              = Modifier.padding(Spacing.lg),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Outlined.CalendarMonth, null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(Spacing.md))
                    Text(stringResource(R.string.date_pick_day), style = MaterialTheme.typography.bodyLarge)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    onClick  = { onDateSelected(yesterdayMs) },
                    shape    = RoundedCornerShape(OneMoneyTheme.dimens.cardRadiusAlt),
                    color    = if (isYesterday) MaterialTheme.colorScheme.primaryContainer
                               else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier            = Modifier.padding(Spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.DarkMode, null, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(Spacing.xs))
                        Text(stringResource(R.string.date_yesterday), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(dFmt.format(Date(yesterdayMs)), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
                Surface(
                    onClick  = { onDateSelected(todayMs) },
                    shape    = RoundedCornerShape(OneMoneyTheme.dimens.cardRadiusAlt),
                    color    = if (isToday) MaterialTheme.colorScheme.primaryContainer
                               else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier            = Modifier.padding(Spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.WbSunny, null, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(Spacing.xs))
                        Text(stringResource(R.string.date_today), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(dFmt.format(Date(todayMs)), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    onClick  = onRepeatClick,
                    shape    = RoundedCornerShape(OneMoneyTheme.dimens.cardRadiusAlt),
                    color    = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier            = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Repeat, null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(Spacing.xs))
                        Text(stringResource(R.string.date_repeat), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(stringResource(repeatLabelResFor(repeatMode)), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
                Surface(
                    onClick  = onReminderClick,
                    shape    = RoundedCornerShape(OneMoneyTheme.dimens.cardRadiusAlt),
                    color    = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier            = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.Notifications, null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(Spacing.xs))
                        Text(stringResource(R.string.date_reminder), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(stringResource(reminderLabelResFor(reminderMode)), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

// ── Full DatePicker dialog ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullDatePickerDialog(
    initial:       Long,
    onDateSelected: (Long) -> Unit,
    onDismiss:     () -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton    = {
            TextButton(onClick = {
                state.selectedDateMillis?.let(onDateSelected)
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton    = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    ) {
        DatePicker(state = state)
    }
}

// ── Repeat dialog ─────────────────────────────────────────────────────────────

@Composable
internal fun RepeatDialog(
    current:  String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var sel by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Repeat, null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.date_repeat), style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            LazyColumn {
                items(REPEAT_OPTIONS) { (key, labelRes) ->
                    Row(
                        modifier          = Modifier.fillMaxWidth().clickable { sel = key }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = sel == key, onClick = { sel = key })
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton   = { TextButton(onClick = { onSelect(sel) }) { Text("OK") } },
        dismissButton   = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

// ── Reminder dialog ───────────────────────────────────────────────────────────

@Composable
internal fun ReminderDialog(
    current:  String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var sel by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Notifications, null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.date_reminder), style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            LazyColumn {
                items(REMINDER_OPTIONS) { (key, labelRes) ->
                    Row(
                        modifier          = Modifier.fillMaxWidth().clickable { sel = key }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = sel == key, onClick = { sel = key })
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton   = { TextButton(onClick = { onSelect(sel) }) { Text("OK") } },
        dismissButton   = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

// ── Account picker sheet ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPickerSheet(
    accounts:   List<AccountEntity>,
    selectedId: Long?,
    label:      String? = null,
    onSelect:   (AccountEntity) -> Unit,
    onDismiss:  () -> Unit
) {
    val selected = accounts.firstOrNull { it.id == selectedId } ?: accounts.firstOrNull()
    val selColor = selected?.let {
        parseColorHex(it.colorHex, FallbackAccountColor)
    } ?: FallbackAccountColor
    val contentColor = if (selColor.luminance() > 0.5f) OnLightColor else Color.White

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(selColor)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.md)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.CreditCard, null, tint = contentColor, modifier = Modifier.size(20.dp))
                }
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = Spacing.lg, bottom = 10.dp)
                ) {
                    Text(label ?: stringResource(R.string.acc_from), style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.7f))
                    Text(selected?.name ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = contentColor)
                    Text(
                        stringResource(R.string.acc_balance, "${formatMoney(selected?.balance ?: 0.0)} ${currencySymbol(selected?.currency ?: "UAH")}"),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.nav_accounts), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    org.syalosovetskyi.onemoney.util.accountCurrencyTotals(accounts).entries.joinToString(" · ") { (currency, total) -> "${formatMoney(total.net)} $currency" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            HorizontalDivider()

            accounts.forEach { acc ->
                val accColor = parseColorHex(acc.colorHex, FallbackAccountColor)
                val itemContentColor = if (accColor.luminance() > 0.5f) OnLightColor else Color.White
                val isSelected = acc.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
                        .clickable { onSelect(acc) }
                        .padding(horizontal = Spacing.lg, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(modifier = Modifier.size(46.dp)) {
                        Box(
                            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(OneMoneyTheme.dimens.keyRadius)).background(accColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.CreditCard, null, tint = itemContentColor, modifier = Modifier.size(22.dp))
                        }
                        val sym = remember(acc.currency) {
                            CURRENCIES_ALL.find { it.code == acc.currency }?.symbol?.take(2) ?: acc.currency.take(2)
                        }
                        Box(
                            modifier         = Modifier
                                .align(Alignment.BottomEnd)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = sym, color = accColor, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(acc.name, fontWeight = FontWeight.Medium)
                        Text(
                            "${formatMoney(acc.balance)} ${currencySymbol(acc.currency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                    if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}
