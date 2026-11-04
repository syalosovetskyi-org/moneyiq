package org.syalosovetskyi.onemoney.ui.accounts

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.data.db.entities.AccountType
import org.syalosovetskyi.onemoney.ui.components.calculator.AmountCalculatorSheet
import org.syalosovetskyi.onemoney.ui.components.currency.CurrencyPickerSheet
import org.syalosovetskyi.onemoney.ui.components.form.FormNavRow
import org.syalosovetskyi.onemoney.ui.components.form.FormSectionHeader
import org.syalosovetskyi.onemoney.ui.components.form.FormValueRow
import org.syalosovetskyi.onemoney.ui.settings.data.CURRENCIES_ALL
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme
import org.syalosovetskyi.onemoney.ui.theme.OnLightColor

private val AccountTypeNormalColor:  Color = Color(0xFFB07040)
private val AccountTypeDebtColor:    Color = Color(0xFF2E7D60)
private val AccountTypeSavingsColor: Color = Color(0xFF3D3F8F)

/**
 * Localized display name for a currency [code]. Fiat (ISO 4217) names come from
 * ICU via [java.util.Currency], resolved against the current app locale (kept in
 * sync by LocaleWrapper.setDefault). Crypto and unknown codes aren't ISO — ICU
 * throws — so we fall back to the hand-written [CurrencyDef.name].
 */
fun currencyDisplayName(code: String): String {
    val fallback = CURRENCIES_ALL.find { it.code == code }?.name ?: code
    return try {
        val icu = java.util.Currency.getInstance(code).getDisplayName()
        if (icu.equals(code, ignoreCase = true)) fallback
        else icu.replaceFirstChar { it.uppercase() }
    } catch (failure: IllegalArgumentException) {
        android.util.Log.w("AccountSheets", "Operation failed", failure)

        fallback
    }
}
fun currencySymbol(code: String): String      = CURRENCIES_ALL.find { it.code == code }?.symbol ?: code

// ── Account type helpers (Ukrainian) ─────────────────────────────────────────

@Composable
fun accountTypeNameUA(type: AccountType): String = when (type) {
    AccountType.CASH       -> stringResource(R.string.acc_type_cash)
    AccountType.CARD       -> stringResource(R.string.acc_type_card)
    AccountType.SAVING     -> stringResource(R.string.acc_type_saving)
    AccountType.INVESTMENT -> stringResource(R.string.acc_type_investment)
    AccountType.DEBT       -> stringResource(R.string.acc_type_debt_item)
    AccountType.OTHER      -> stringResource(R.string.acc_type_other)
}

val ACCOUNT_FORM_COLORS = listOf(
    "#D81B60", "#4361EE", "#3A86FF", "#8338EC",
    "#FB5607", "#FFBE0B", "#06D6A0", "#118AB2",
    "#4CAF50", "#009688", "#607D8B", "#A07040"
)

// ── NewAccountTypeSheet ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAccountTypeSheet(
    onSelect:  (AccountType) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor  = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.acc_new_title),
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = Spacing.sm)
            )

            AccountTypeOption(
                color    = AccountTypeNormalColor,
                icon     = Icons.Outlined.Wallet,
                title    = stringResource(R.string.acc_type_regular),
                subtitle = stringResource(R.string.acc_type_regular_sub),
                onClick  = { onSelect(AccountType.CASH) }
            )
            AccountTypeOption(
                color    = AccountTypeDebtColor,
                icon     = Icons.Outlined.MoneyOff,
                title    = stringResource(R.string.acc_type_debt),
                subtitle = stringResource(R.string.acc_type_debt_sub),
                onClick  = { onSelect(AccountType.DEBT) }
            )
            AccountTypeOption(
                color    = AccountTypeSavingsColor,
                icon     = Icons.Outlined.Savings,
                title    = stringResource(R.string.acc_type_savings),
                subtitle = stringResource(R.string.acc_type_savings_sub),
                onClick  = { onSelect(AccountType.SAVING) }
            )
        }
    }
}

@Composable
private fun AccountTypeOption(
    color:    Color,
    icon:     ImageVector,
    title:    String,
    subtitle: String,
    onClick:  () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Box(
                modifier            = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(OneMoneyTheme.dimens.cardRadiusAlt))
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        },
        headlineContent   = {
            Text(title, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    )
}

// ── AccountFormSheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountFormSheet(
    initialType: AccountType = AccountType.CASH,
    existing:    org.syalosovetskyi.onemoney.data.db.entities.AccountEntity? = null,
    onSave:      (name: String, type: AccountType, balance: Double, color: String, currency: String, description: String, includeInTotal: Boolean, icon: String, creditLimit: Double) -> Unit,
    onDismiss:   () -> Unit
) {
    var name           by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var type           by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(existing?.type ?: initialType) }
    var balanceStr     by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(
            existing?.balance?.let { if (it == 0.0) "" else it.toBigDecimal().stripTrailingZeros().toPlainString() } ?: ""
        )
    }
    var creditLimit    by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(existing?.creditLimit ?: 0.0) }
    var colorHex       by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(existing?.colorHex ?: "#D81B60") }
    var iconKey        by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(existing?.icon ?: "account_balance_wallet") }
    var currency       by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(existing?.currency ?: "UAH") }
    var description    by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(existing?.description ?: "") }
    var includeInTotal by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(existing?.includeInTotal ?: true) }

    var showIconColorPicker  by remember { mutableStateOf(false) }
    var showTypePicker       by remember { mutableStateOf(false) }
    var showCurrencyPicker   by remember { mutableStateOf(false) }
    var showDescEditor       by remember { mutableStateOf(false) }
    var showBalanceInput     by remember { mutableStateOf(false) }
    var showCreditLimitInput by remember { mutableStateOf(false) }

    val appColors   = OneMoneyTheme.colors
    val accentColor = remember(colorHex) {
        parseColorHex(colorHex, appColors.budgetExpense)
    }
    val iconTint      = if (accentColor.luminance() > 0.5f) OnLightColor else Color.White
    val currencyLabel = "${currencyDisplayName(currency)} – ${currencySymbol(currency)}"
    val sym           = currencySymbol(currency)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {

                // ── Top bar ─────────────────────────────────────────────────
                Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, stringResource(R.string.acc_close))
                        }
                        Text(
                            if (existing != null) stringResource(R.string.acc_edit_title) else stringResource(R.string.acc_new_title),
                            modifier   = Modifier
                                .weight(1f)
                                .padding(horizontal = Spacing.sm),
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Button(
                            onClick = {
                                val b = balanceStr.replace(",", ".").toDoubleOrNull() ?: 0.0
                                if (name.isNotBlank()) {
                                    onSave(name, type, b, colorHex, currency, description, includeInTotal, iconKey, creditLimit)
                                }
                            },
                            shape           = RoundedCornerShape(50),
                            contentPadding  = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                            enabled         = name.isNotBlank()
                        ) {
                            Text(stringResource(R.string.common_done), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                LazyColumn(Modifier.fillMaxSize()) {

                    // ── Name + icon ──────────────────────────────────────────
                    item {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
                            Row(
                                modifier          = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value         = name,
                                    onValueChange = { name = it },
                                    modifier      = Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp),
                                    textStyle     = MaterialTheme.typography.titleLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    decorationBox = { inner ->
                                        Box {
                                            if (name.isEmpty()) {
                                                Text(
                                                    stringResource(R.string.acc_name_hint),
                                                    style = MaterialTheme.typography.titleLarge,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                )
                                            }
                                            inner()
                                        }
                                    }
                                )
                                // Colored icon box (opens icon+color picker)
                                Box(
                                    modifier         = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(OneMoneyTheme.dimens.cardRadiusAlt))
                                        .background(accentColor)
                                        .clickable { showIconColorPicker = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        accountIconFromKey(iconKey), null,
                                        tint     = iconTint,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }

                    // ── Section: Рахунок ─────────────────────────────────────
                    item { FormSectionHeader(stringResource(R.string.acc_section_account)) }

                    item {
                        FormNavRow(
                            icon    = accountTypeIcon(type),
                            label   = stringResource(R.string.acc_field_type),
                            value   = accountTypeNameUA(type),
                            onClick = { showTypePicker = true }
                        )
                    }
                    item {
                        FormNavRow(
                            icon    = Icons.Outlined.AttachMoney,
                            label   = stringResource(R.string.acc_field_currency),
                            value   = currencyLabel,
                            onClick = { showCurrencyPicker = true }
                        )
                    }
                    item {
                        FormNavRow(
                            icon    = Icons.AutoMirrored.Outlined.Notes,
                            label   = stringResource(R.string.acc_field_description),
                            value   = description,
                            onClick = { showDescEditor = true }
                        )
                    }

                    // ── Section: Баланс ──────────────────────────────────────
                    item { FormSectionHeader(stringResource(R.string.acc_section_balance)) }

                    item {
                        FormValueRow(
                            label   = stringResource(R.string.acc_field_balance),
                            value   = if (balanceStr.isEmpty()) "0 $sym" else "$balanceStr $sym",
                            onClick = { showBalanceInput = true }
                        )
                    }
                    item {
                        FormValueRow(
                            label   = stringResource(R.string.acc_field_credit_limit),
                            value   = if (creditLimit == 0.0) "0 $sym" else "${creditLimit.toBigDecimal().stripTrailingZeros().toPlainString()} $sym",
                            onClick = { showCreditLimitInput = true }
                        )
                    }
                    item {
                        ListItem(
                            headlineContent   = { Text(stringResource(R.string.acc_field_include_total)) },
                            trailingContent   = {
                                Switch(
                                    checked         = includeInTotal,
                                    onCheckedChange = { includeInTotal = it }
                                )
                            }
                        )
                    }

                    item { Spacer(Modifier.height(48.dp)) }
                }
            }
        }
    }

    // ── Nested sheets ────────────────────────────────────────────────────────
    if (showIconColorPicker) {
        IconColorPickerScreen(
            initialIconKey  = iconKey,
            initialColorHex = colorHex,
            onResult = { newIcon, newColor ->
                iconKey  = newIcon
                colorHex = newColor
                showIconColorPicker = false
            },
            onDismiss = { showIconColorPicker = false }
        )
    }

    if (showTypePicker) {
        TypePickerSheet(
            selected  = type,
            onSelect  = { type = it; showTypePicker = false },
            onDismiss = { showTypePicker = false }
        )
    }

    if (showCurrencyPicker) {
        CurrencyPickerSheet(
            selected  = currency,
            onSelect  = { currency = it; showCurrencyPicker = false },
            onDismiss = { showCurrencyPicker = false }
        )
    }

    if (showDescEditor) {
        DescEditorDialog(
            initial   = description,
            onSave    = { description = it; showDescEditor = false },
            onDismiss = { showDescEditor = false }
        )
    }

    if (showBalanceInput) {
        AmountCalculatorSheet(
            initial        = balanceStr.replace(",", ".").toDoubleOrNull() ?: 0.0,
            currencySymbol = sym,
            title          = stringResource(R.string.acc_field_balance),
            onResult       = { v ->
                balanceStr = if (v == 0.0) "" else v.toBigDecimal().stripTrailingZeros().toPlainString()
                showBalanceInput = false
            },
            onDismiss      = { showBalanceInput = false }
        )
    }

    if (showCreditLimitInput) {
        AmountCalculatorSheet(
            initial        = creditLimit,
            currencySymbol = sym,
            title          = stringResource(R.string.acc_field_credit_limit),
            onResult       = { v -> creditLimit = v; showCreditLimitInput = false },
            onDismiss      = { showCreditLimitInput = false }
        )
    }
}
