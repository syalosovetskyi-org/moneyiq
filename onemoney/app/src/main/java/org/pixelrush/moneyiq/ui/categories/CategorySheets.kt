package org.syalosovetskyi.onemoney.ui.categories

import org.syalosovetskyi.onemoney.util.parseColorHex
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.core.graphics.toColorInt
import java.text.Collator
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import org.syalosovetskyi.onemoney.R
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.ui.components.currency.CurrencyBottomSheet
import org.syalosovetskyi.onemoney.ui.components.calculator.*
import org.syalosovetskyi.onemoney.util.formatMoney
import org.syalosovetskyi.onemoney.ui.settings.data.CURRENCIES_ALL
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme
import org.syalosovetskyi.onemoney.ui.theme.OnLightColor
import org.syalosovetskyi.onemoney.ui.theme.FallbackCategoryColor

private val FallbackIconColor:     Color = Color(0xFF757575)
private val CategoryDisplayColor:  Color = Color(0xFF37474F)

// ── Category Action Sheet ─────────────────────────────────────────────────────

@Composable
fun CategoryActionSheet(
    category:      CategoryEntity,
    spending:      Double,
    txCount:       Int,
    totalInPeriod: Double,
    pillLabel:     String,
    onEdit:        () -> Unit,
    onBudget:      () -> Unit,
    onOperations:  () -> Unit,
    onDismiss:     () -> Unit
) {
    val catColor = remember(category.colorHex) {
        parseColorHex(category.colorHex, FallbackCategoryColor)
    }
    val isLightBg  = catColor.luminance() > 0.5f
    val onCatColor = if (isLightBg) OnLightColor else Color.White
    val percent  = if (totalInPeriod > 0.0) (spending / totalInPeriod * 100).toInt() else 0
    val progress = if (totalInPeriod > 0.0) (spending / totalInPeriod).coerceIn(0.0, 1.0).toFloat() else 0f
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Повноекранний Dialog = скрим + кастомний шит знизу (без кліпінгу ModalBottomSheet)
    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize()) {
            // ── Скрим ────────────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null,
                        onClick           = onDismiss
                    )
            )

            // ── Панель знизу ─────────────────────────────────────────────
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth()) {
                // Кольорова шапка
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(catColor)
                        .padding(start = 20.dp, top = 20.dp, bottom = 20.dp, end = 20.dp)
                ) {
                    Spacer(Modifier.height(Spacing.lg))
                    Text(
                        category.name,
                        style      = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color      = onCatColor
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            pluralStringResource(R.plurals.cat_operations_count, txCount, txCount),
                            color = onCatColor.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${formatMoney(spending)} ₴",
                            color      = onCatColor,
                            fontWeight = FontWeight.Bold,
                            style      = MaterialTheme.typography.titleLarge
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    val clamped    = progress.coerceIn(0f, 1f)
                    val showInside = clamped >= 0.4f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(OneMoneyTheme.dimens.smallRadius))
                    ) {
                        Spacer(Modifier.fillMaxSize().background(onCatColor.copy(alpha = 0.28f)))
                        if (clamped > 0f) {
                            Spacer(Modifier.fillMaxHeight().fillMaxWidth(clamped).background(onCatColor))
                        }
                        Box(
                            Modifier.fillMaxWidth(clamped.coerceAtLeast(0.15f)).fillMaxHeight(),
                            contentAlignment = if (showInside) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Text(
                                "$percent%",
                                color      = if (showInside) catColor else onCatColor,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 11.sp,
                                lineHeight = 11.sp,
                                modifier   = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            pillLabel,
                            color = onCatColor.copy(0.8f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${formatMoney(totalInPeriod)} ₴",
                            color      = onCatColor.copy(0.8f),
                            fontWeight = FontWeight.Medium,
                            style      = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // ── Кнопки дій (білий фон) ────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(
                            start = 16.dp,
                            top = 20.dp,
                            end = 16.dp,
                            bottom = 32.dp + navigationBottom
                        ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CatActionButton(Icons.Default.Edit,     stringResource(R.string.common_edit), catColor, onEdit,       Modifier.weight(1f))
                    CatActionButton(Icons.Outlined.Speed,   stringResource(R.string.cat_budget),     catColor, onBudget,     Modifier.weight(1f))
                    CatActionButton(Icons.Outlined.Receipt, stringResource(R.string.nav_operations),   catColor, onOperations, Modifier.weight(1f))
                }
                } // Column wrapper

                // ── Іконка категорії (виступає за верхній край картки) ────────────
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 20.dp)
                        .offset(y = (-36).dp)
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(2.dp, catColor.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        categoryIconFor(category.icon), null,
                        tint     = catColor,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CatActionButton(
    icon:     ImageVector,
    label:    String,
    color:    Color,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier            = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier         = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style     = MaterialTheme.typography.labelMedium,
            color     = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

// ── Quick Expense / Income Sheet ──────────────────────────────────────────────
// Лейаут: 2 кольорові панелі (рахунок / категорія) + сума + нотатка +
//         5×4 калькулятор (оператори зліва, ✓ праворуч на 2 рядки) + дата.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickExpenseSheet(
    category:   CategoryEntity,
    categories: List<CategoryEntity> = emptyList(),
    accounts:   List<AccountEntity>,
    onSave:     (accountId: Long, amount: Double, note: String, date: Long, repeatMode: String, reminderMode: String, categoryId: Long) -> Unit,
    onDismiss:  () -> Unit
) {
    var selectedCategoryId by androidx.compose.runtime.saveable.rememberSaveable(category.id) { mutableStateOf(category.id) }
    val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId } ?: category

    val catColor = remember(selectedCategory.colorHex) {
        parseColorHex(selectedCategory.colorHex, FallbackCategoryColor)
    }
    val isCatLight   = catColor.luminance() > 0.5f
    val onCatColor   = if (isCatLight) OnLightColor else Color.White
    val displayColor = if (isCatLight) CategoryDisplayColor else catColor
    val isIncome     = selectedCategory.type == TransactionType.INCOME

    // ── Стан калькулятора ──────────────────────────────────────────────────
    val calc = rememberCalcState()

    // ── Інший стан ────────────────────────────────────────────────────────
    var note            by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var selectedAccountId by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf((accounts.firstOrNull { it.isDefault } ?: accounts.firstOrNull())?.id)
    }
    val selectedAccount = accounts.firstOrNull { it.id == selectedAccountId }
    var selectedDate    by androidx.compose.runtime.saveable.rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedCurrency  by androidx.compose.runtime.saveable.rememberSaveable(selectedAccountId) { mutableStateOf(selectedAccount?.currency ?: "UAH") }
    val currencySymbol = CURRENCIES_ALL.find { it.code == selectedCurrency }?.symbol ?: selectedCurrency

    var showDateSheet      by remember { mutableStateOf(false) }
    var showRepeat         by remember { mutableStateOf(false) }
    var showReminder       by remember { mutableStateOf(false) }
    var showFullDate       by remember { mutableStateOf(false) }
    var showAccSheet       by remember { mutableStateOf(false) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showCatPicker      by remember { mutableStateOf(false) }
    var repeatMode     by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("NEVER") }
    var reminderMode   by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("NEVER") }
    var amountError    by remember { mutableStateOf(false) }

    LaunchedEffect(amountError) {
        if (amountError) { delay(600); amountError = false }
    }

    val amountDisplayColor by animateColorAsState(
        targetValue    = if (amountError) MaterialTheme.colorScheme.error else displayColor,
        animationSpec  = tween(150),
        label          = "amountColor"
    )

    // ── Логіка ────────────────────────────────────────────────────────────
    fun onConfirm() {
        val amt   = calc.result()
        val accId = selectedAccount?.id ?: return
        if (amt > 0.0) {
            onSave(accId, amt, note.trim(), selectedDate, repeatMode, reminderMode, selectedCategory.id)
        } else {
            amountError = true
        }
    }

    // ── Висота аркуша ≈ 2/3 екрана ────────────────────────────────────────
    val screenH  = LocalConfiguration.current.screenHeightDp.dp
    val sheetH   = screenH * 0.67f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = MaterialTheme.colorScheme.surface,
        dragHandle       = {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetH)
        ) {
            // ── 1. Шапка: обидві іконки виступають над панелями
            Box(modifier = Modifier.fillMaxWidth()) {
                val iconD  = 80.dp
                val badgeD = 48.dp
                val stripH = 40.dp

                Column(Modifier.fillMaxWidth()) {
                    // Колірна смуга — продовжує фони панелей вгору без білого зазору
                    Row(Modifier.fillMaxWidth().height(stripH)) {
                        Spacer(Modifier.weight(1f).fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surface))
                        Spacer(Modifier.weight(1f).fillMaxHeight()
                            .background(catColor))
                    }
                    // Панелі
                    Row(Modifier.fillMaxWidth().height(80.dp)) {
                        // Ліва: рахунок
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { if (accounts.size > 1) showAccSheet = true }
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        ) {
                            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                                Text(
                                    if (isIncome) stringResource(R.string.tx_to_account) else stringResource(R.string.tx_from_account),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                                )
                                Text(
                                    selectedAccount?.name ?: "—",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        // Права: категорія
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(catColor)
                                .clickable(enabled = categories.isNotEmpty()) { showCatPicker = true }
                                .padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                                Text(
                                    if (isIncome) stringResource(R.string.cat_from_category) else stringResource(R.string.tx_to_category),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onCatColor.copy(0.7f)
                                )
                                Text(
                                    selectedCategory.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = onCatColor,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Іконка рахунку — над лівою панеллю (badge)
                Box(
                    modifier = Modifier
                        .size(badgeD)
                        .align(Alignment.TopCenter)
                        .offset(x = -(badgeD / 2 + 8.dp))
                        .clip(RoundedCornerShape(OneMoneyTheme.dimens.cardRadius))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.CreditCard, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Іконка категорії — над правою панеллю (коло)
                Box(
                    modifier = Modifier
                        .size(iconD)
                        .align(Alignment.TopEnd)
                        .offset(x = (-12).dp)
                        .clip(CircleShape)
                        .background(catColor)
                        .clickable(enabled = categories.isNotEmpty()) { showCatPicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        categoryIconFor(selectedCategory.icon), null,
                        tint = onCatColor,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // ── 2. Відображення виразу / суми ─────────────────────────────
            val displayText = calc.displayExpr(currencySymbol)

            Column(
                modifier            = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (isIncome) stringResource(R.string.tx_income) else stringResource(R.string.tx_expense),
                    style = MaterialTheme.typography.labelMedium,
                    color = amountDisplayColor
                )
                Text(
                    text       = displayText,
                    fontSize   = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color      = amountDisplayColor,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
            }

            // ── 3. Нотатка ────────────────────────────────────────────────
            OutlinedTextField(
                value         = note,
                onValueChange = { note = it },
                placeholder   = {
                    Text(
                        stringResource(R.string.tx_notes_hint),
                        style     = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier  = Modifier.fillMaxWidth()
                    )
                },
                textStyle = MaterialTheme.typography.bodySmall,
                modifier   = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                singleLine = true,
                shape      = RoundedCornerShape(OneMoneyTheme.dimens.cardRadius)
            )

            Spacer(Modifier.height(6.dp))

            // ── 4. Клавіатура-калькулятор ─────────────────────────────────
            SharedCalcKeypad(
                calc            = calc,
                modifier        = Modifier.weight(1f).fillMaxWidth(),
                currencySymbol  = currencySymbol,
                confirmColor    = catColor,
                onCurrencyClick = { showCurrencyPicker = true },
                onConfirm       = { onConfirm() },
                row2ExtraKey    = {
                    val keyBg = MaterialTheme.colorScheme.surfaceVariant
                    Box(
                        modifier         = Modifier.weight(1f).fillMaxHeight()
                            .clip(RoundedCornerShape(OneMoneyTheme.dimens.keyRadius)).background(keyBg)
                            .clickable { showDateSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.CalendarMonth, null, modifier = Modifier.size(20.dp))
                    }
                }
            )

            // ── 5. Дата внизу ─────────────────────────────────────────────
            Text(
                text      = txDateLabel(selectedDate),
                style     = MaterialTheme.typography.bodySmall,
                color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth().padding(vertical = 5.dp)
            )
        }
    }

    // ── Вибір валюти ─────────────────────────────────────────────────────────
    if (showCurrencyPicker) {
        CurrencyBottomSheet(
            selected  = selectedCurrency,
            title     = stringResource(R.string.tx_currency_title),
            onSelect  = { selectedCurrency = it; showCurrencyPicker = false },
            onDismiss = { showCurrencyPicker = false }
        )
    }

    // ── Вибір категорії ───────────────────────────────────────────────────────
    if (showCatPicker && categories.isNotEmpty()) {
        QuickCategoryPickerSheet(
            categories         = categories,
            selectedCategoryId = selectedCategory.id,
            onSelect           = { selectedCategoryId = it.id; showCatPicker = false },
            onDismiss          = { showCatPicker = false }
        )
    }

    // ── Вибір дати (аркуш) ────────────────────────────────────────────────────
    if (showDateSheet) {
        CalcDateSheet(
            currentDate  = selectedDate,
            repeatMode   = repeatMode,
            reminderMode = reminderMode,
            onDateSelected  = { selectedDate = it; showDateSheet = false },
            onRepeatClick   = { showDateSheet = false; showRepeat = true },
            onReminderClick = { showDateSheet = false; showReminder = true },
            onPickDate      = { showDateSheet = false; showFullDate = true },
            onDismiss       = { showDateSheet = false }
        )
    }

    // ── Повний DatePicker ─────────────────────────────────────────────────────
    if (showFullDate) {
        FullDatePickerDialog(
            initial        = selectedDate,
            onDateSelected = { selectedDate = it; showFullDate = false },
            onDismiss      = { showFullDate = false }
        )
    }

    // ── Діалог повторення ─────────────────────────────────────────────────────
    if (showRepeat) {
        RepeatDialog(
            current   = repeatMode,
            onSelect  = { repeatMode = it; showRepeat = false },
            onDismiss = { showRepeat = false }
        )
    }

    // ── Діалог нагадування ────────────────────────────────────────────────────
    if (showReminder) {
        ReminderDialog(
            current   = reminderMode,
            onSelect  = { reminderMode = it; showReminder = false },
            onDismiss = { showReminder = false }
        )
    }

    // ── Вибір рахунку (аркуш) ────────────────────────────────────────────────
    if (showAccSheet) {
        AccountPickerSheet(
            accounts       = accounts,
            selectedId     = selectedAccount?.id,
            label          = stringResource(R.string.tx_from_account),
            onSelect       = { acc -> selectedAccountId = acc.id; showAccSheet = false },
            onDismiss      = { showAccSheet = false }
        )
    }
}

/** Аркуш вибору категорії для швидкого запису (секції Витрати/Доходи).
 *  Раніше ~73 рядки inline у QuickExpenseSheet з дублюванням exp/inc гілок.
 *  `includeSubcategories` — показати ще й підкатегорії (потрібно при зміні
 *  категорії вже записаної операції: підкатегорія там осмислений вибір). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickCategoryPickerSheet(
    categories:           List<CategoryEntity>,
    selectedCategoryId:   Long,
    includeSubcategories: Boolean = false,
    /** Рахунки для розділу «Переказ». Порожній список — розділу не буде. */
    transferAccounts:     List<AccountEntity> = emptyList(),
    onSelectAccount:      (AccountEntity) -> Unit = {},
    onSelect:             (CategoryEntity) -> Unit,
    onDismiss:            () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    val allExp = remember(categories, includeSubcategories) {
        groupedByParent(categories, TransactionType.EXPENSE, includeSubcategories)
    }
    val allInc = remember(categories, includeSubcategories) {
        groupedByParent(categories, TransactionType.INCOME, includeSubcategories)
    }
    // Пошук шукає і по батьківській категорії: «машина» має знаходити АЗС і Автозапчастини,
    // навіть якщо цих слів немає в їхніх назвах.
    val parentNames = remember(categories) { categories.associate { it.id to it.name } }
    val expCats = remember(allExp, query, parentNames) { filterByQuery(allExp, query, parentNames) }
    val incCats = remember(allInc, query, parentNames) { filterByQuery(allInc, query, parentNames) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Text(
            stringResource(R.string.tx_category),
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        OutlinedTextField(
            value         = query,
            onValueChange = { query = it },
            placeholder   = { Text(stringResource(R.string.tx_search)) },
            leadingIcon   = { Icon(Icons.Default.Search, null) },
            trailingIcon  = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, stringResource(R.string.cat_search_clear))
                    }
                }
            },
            singleLine = true,
            shape      = RoundedCornerShape(OneMoneyTheme.dimens.cardRadius),
            modifier   = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        )
        val expensesLabel = stringResource(R.string.common_expenses)
        val incomesLabel  = stringResource(R.string.common_incomes)
        val transferLabel = stringResource(R.string.tx_transfer)
        val shownAccounts = remember(transferAccounts, query) {
            val q = query.trim()
            if (q.isEmpty()) transferAccounts
            else transferAccounts.filter { it.name.contains(q, ignoreCase = true) }
        }
        if (expCats.isEmpty() && incCats.isEmpty() && shownAccounts.isEmpty()) {
            Text(
                stringResource(R.string.cat_search_empty, query),
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth().padding(24.dp)
            )
        }
        // weight(fill = false) обмежує список тим, що лишилось на екрані: без цього
        // LazyColumn міряється по вмісту, хвіст списку йде за край і прокрутити його
        // неможливо — жест перехоплює сам аркуш. Видно лише на довгих списках
        // (з підкатегоріями), тому у швидкому вводі баг не проявлявся.
        LazyColumn(
            modifier       = Modifier.fillMaxWidth()
                .weight(1f, fill = false)
                .nestedScroll(SheetDragBlocker),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            val searching = query.isNotBlank()
            quickCatSection(expensesLabel, expCats, selectedCategoryId, parentNames, searching, onSelect)
            quickCatSection(incomesLabel,  incCats, selectedCategoryId, parentNames, searching, onSelect)
            // Розділ «Переказ»: гроші не витрачені, а переміщені на власний рахунок
            // (IBKR, готівка, інша картка). Без нього такий переказ лишався витратою.
            transferSection(transferLabel, shownAccounts, onSelectAccount)
        }
    }
}

/**
 * Фільтр пошуку. Порядок (глобальна → її підкатегорії) зберігається, тому знайдене
 * лишається на своєму місці в дереві. Збіг у назві батька залишає всю групу: шукаючи
 * «транспорт», людина хоче побачити і АЗС, і Таксі.
 */
internal fun filterByQuery(
    ordered:     List<CategoryEntity>,
    query:       String,
    parentNames: Map<Long, String>,
): List<CategoryEntity> {
    val q = query.trim()
    if (q.isEmpty()) return ordered
    return ordered.filter { cat ->
        cat.name.contains(q, ignoreCase = true) ||
            cat.parentId?.let { parentNames[it]?.contains(q, ignoreCase = true) } == true
    }
}

/**
 * З'їдає залишок вертикальної прокрутки, щоб він не діставався аркушу.
 *
 * За замовчуванням ModalBottomSheet тягнеться від будь-якого жесту всередині себе:
 * ведеш пальцем по списку — і аркуш поїхав закриватися замість того, щоб гортати.
 * З цим блокувальником тіло аркуша тільки гортає список, а закривається аркуш
 * жестом по шапці (там nested scroll не діє) або тапом по затемненню.
 *
 * `sheetGesturesEnabled` з'явився лише в material3 1.4; у нас 1.3.1.
 */
private val SheetDragBlocker = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = available.copy(x = 0f)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        available.copy(x = 0f)
}

/**
 * Категорії одного типу в порядку показу: кожна глобальна (коренева) категорія, одразу
 * за нею — її підкатегорії за абеткою. Витрати на машину мають стояти поруч, а не бути
 * розкидані по списку.
 *
 * Порядок самих глобальних — `sortOrder`: він налаштований людиною на екрані категорій,
 * і переставляти його за абеткою тут означало б сперечатися з тим порядком.
 * Абетка — за правилами мови (`Collator`), а не за кодами символів: інакше «Їжа» поїде
 * в кінець списку.
 */
internal fun groupedByParent(
    categories:           List<CategoryEntity>,
    type:                 TransactionType,
    includeSubcategories: Boolean,
): List<CategoryEntity> {
    val byName = Collator.getInstance(java.util.Locale.forLanguageTag("uk")).apply {
        strength = Collator.SECONDARY
    }
    val visible = categories.filter { !it.archived && it.type == type }
    val roots = visible.filter { it.parentId == null }.sortedWith(
        compareBy<CategoryEntity> { it.sortOrder }.thenComparator { a, b -> byName.compare(a.name, b.name) }
    )
    if (!includeSubcategories) return roots

    val children = visible.filter { it.parentId != null }.groupBy { it.parentId }

    val ordered = mutableListOf<CategoryEntity>()
    roots.forEach { root ->
        ordered += root
        ordered += children[root.id].orEmpty().sortedWith { a, b -> byName.compare(a.name, b.name) }
    }
    // Підкатегорії, чий батько не показується (архівний або іншого типу), інакше зникли б
    // із пікера зовсім — вони йдуть у кінці, теж за абеткою.
    val shown = ordered.mapTo(mutableSetOf()) { it.id }
    ordered += visible.filter { it.id !in shown }.sortedWith { a, b -> byName.compare(a.name, b.name) }
    return ordered
}

/** Секція «Переказ»: рахунки, на які можна перемістити операцію. */
private fun LazyListScope.transferSection(
    title:    String,
    accounts: List<AccountEntity>,
    onSelect: (AccountEntity) -> Unit,
) {
    if (accounts.isEmpty()) return
    item {
        Text(title, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    }
    items(accounts) { acc ->
        val color = parseColorHex(acc.colorHex, FallbackIconColor)
        ListItem(
            modifier        = Modifier.clickable { onSelect(acc) },
            leadingContent  = {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.SwapHoriz, null,
                        tint = if (color.luminance() > 0.5f) OnLightColor else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            headlineContent = {
                Text(acc.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
        )
    }
}

/** Секція пікера категорій (заголовок + рядки). Прибирає дубль exp/inc гілок. */
private fun LazyListScope.quickCatSection(
    title:              String,
    cats:               List<CategoryEntity>,
    selectedCategoryId: Long,
    parentNames:        Map<Long, String>,
    searching:          Boolean,
    onSelect:           (CategoryEntity) -> Unit,
) {
    if (cats.isEmpty()) return
    item {
        Text(title, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    }
    items(cats) { cat ->
        val color = parseColorHex(cat.colorHex, FallbackIconColor)
        // Підкатегорія зсунута вправо — так видно, до якої глобальної вона належить.
        // Під час пошуку дерева на екрані немає: батько міг не потрапити у знайдене,
        // і відступ стверджував би належність до рядка, якого не видно. Тому замість
        // відступу пишемо назву батька під рядком.
        val parentName = cat.parentId?.let { parentNames[it] }
        val isChild = cat.parentId != null && !searching
        ListItem(
            modifier        = Modifier.clickable { onSelect(cat) },
            leadingContent  = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isChild) Spacer(Modifier.width(24.dp))
                    Box(
                        modifier = Modifier.size(if (isChild) 30.dp else 36.dp).clip(CircleShape).background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            categoryIconFor(cat.icon), null, tint = Color.White,
                            modifier = Modifier.size(if (isChild) 17.dp else 20.dp)
                        )
                    }
                }
            },
            headlineContent = {
                Text(
                    cat.name,
                    style = if (isChild) MaterialTheme.typography.bodyMedium
                            else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isChild) FontWeight.Normal else FontWeight.Medium
                )
            },
            supportingContent = if (searching && parentName != null) {
                {
                    Text(
                        parentName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else null,
            trailingContent = {
                if (cat.id == selectedCategoryId)
                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
        )
    }
}
