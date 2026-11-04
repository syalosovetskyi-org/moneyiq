package org.syalosovetskyi.onemoney.ui.categories

import org.syalosovetskyi.onemoney.util.parseColorHex
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import org.syalosovetskyi.onemoney.R
import androidx.activity.compose.BackHandler
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.ui.components.calculator.*
import org.syalosovetskyi.onemoney.ui.components.dialogs.ConfirmationDialog
import org.syalosovetskyi.onemoney.ui.components.currency.CurrencyPageContent
import org.syalosovetskyi.onemoney.ui.accounts.currencyDisplayName
import org.syalosovetskyi.onemoney.ui.accounts.currencySymbol
import org.syalosovetskyi.onemoney.ui.settings.data.CURRENCIES_CRYPTO
import org.syalosovetskyi.onemoney.ui.settings.data.CURRENCIES_MAIN
import org.syalosovetskyi.onemoney.ui.settings.data.CURRENCIES_OTHER
import org.syalosovetskyi.onemoney.util.suggestCategoryStyle
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme
import org.syalosovetskyi.onemoney.ui.theme.FallbackCategoryColor

// ── Category Form Sheet (додавання / редагування категорії) ───────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFormSheet(
    existing:              CategoryEntity?,
    forParentId:           Long?                      = null,
    children:              List<CategoryEntity>       = emptyList(),
    onAddSubcategory:      (() -> Unit)?              = null,
    onDetachSubcategory:   ((CategoryEntity) -> Unit)? = null,
    onDeleteSubcategory:   ((CategoryEntity) -> Unit)? = null,
    parentOptions:         List<CategoryEntity>       = emptyList(),
    onChangeParent:        ((Long?) -> Unit)?         = null,
    defaultType:           TransactionType            = TransactionType.EXPENSE,
    onSave:                (name: String, type: TransactionType, color: String, icon: String, budget: Double, period: String, archived: Boolean, currencyCode: String) -> Unit,
    onDelete:              (() -> Unit)?              = null,
    onDismiss:             () -> Unit
) {
    var name     by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(existing?.name     ?: "") }
    var type     by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(existing?.type     ?: defaultType) }
    var colorHex by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(existing?.colorHex ?: CATEGORY_FORM_COLORS.first()) }
    var iconKey  by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(existing?.icon     ?: "category") }
    var budget   by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(
            existing?.budgetAmount?.takeIf { it > 0.0 }
                ?.let { it.toBigDecimal().stripTrailingZeros().toPlainString() } ?: ""
        )
    }
    var period        by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(existing?.budgetPeriod ?: "MONTHLY") }
    var archived      by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(existing?.archived ?: false) }
    var currencyCode  by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(existing?.currencyCode ?: "UAH") }

    var showIconPicker     by remember { mutableStateOf(false) }
    var showDeleteConfirm  by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showBudgetCalc     by remember { mutableStateOf(false) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showParentPicker   by remember { mutableStateOf(false) }
    var deleteChildTarget  by remember { mutableStateOf<CategoryEntity?>(null) }
    val nameFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isNameFocused by remember { mutableStateOf(false) }

    BackHandler(enabled = isNameFocused) {
        focusManager.clearFocus()
        keyboardController?.hide()
    }


    // Авто-підказка стилю при введенні назви нової категорії
    LaunchedEffect(name) {
        if (existing == null && name.length >= 3 && iconKey == "category") {
            val (sugIcon, sugColor) = suggestCategoryStyle(name, type)
            iconKey  = sugIcon
            colorHex = sugColor
        }
    }

    val catColor by remember { derivedStateOf {
        parseColorHex(colorHex, FallbackCategoryColor)
    } }

    fun doSave() {
        if (name.isNotBlank()) {
            val b = budget.replace(",", ".").toDoubleOrNull() ?: 0.0
            onSave(name, type, colorHex, iconKey, b, period, archived, currencyCode)
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(when {
                            existing == null && forParentId != null -> stringResource(R.string.cat_new_sub)
                            existing == null                         -> stringResource(R.string.cat_new)
                            existing.parentId != null                -> stringResource(R.string.cat_subcategory)
                            else                                     -> stringResource(R.string.tx_category)
                        })
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isNameFocused) {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            } else {
                                onDismiss()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                        }
                    },
                    actions = {
                        TextButton(
                            onClick  = ::doSave,
                            enabled  = name.isNotBlank()
                        ) {
                            Text(stringResource(R.string.common_save), fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // ── Шапка: назва + іконка ────────────────────────────────────
                item {
                    CategoryFormHeader(
                        name               = name,
                        onNameChange       = { name = it },
                        catColor           = catColor,
                        iconKey            = iconKey,
                        nameFocusRequester = nameFocusRequester,
                        autoFocus = existing == null,
                        onNameFocusChange  = { isNameFocused = it },
                        onImeDone          = { focusManager.clearFocus(); keyboardController?.hide() },
                        onIconClick        = { showIconPicker = true }
                    )
                }

                // ── Налаштування ─────────────────────────────────────────────
                item {
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        stringResource(R.string.cat_settings),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                }

                // Тип (витрати / доходи)
                item {
                    ListItem(
                        leadingContent  = {
                            Icon(Icons.Outlined.Category, null,
                                tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                modifier = Modifier.size(22.dp))
                        },
                        headlineContent = { Text(stringResource(R.string.acc_field_type)) },
                        trailingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(TransactionType.EXPENSE to stringResource(R.string.common_expenses), TransactionType.INCOME to stringResource(R.string.common_incomes))
                                    .forEach { (t, label) ->
                                        FilterChip(
                                            selected = type == t,
                                            onClick  = { type = t },
                                            label    = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }

                // Валюта
                item {
                    val allCurrencies = CURRENCIES_MAIN + CURRENCIES_OTHER + CURRENCIES_CRYPTO
                    val currencyDef = allCurrencies.firstOrNull { it.code == currencyCode }
                    val currencyLabel = currencyDef?.let { "${currencyDisplayName(it.code)} – ${it.symbol}" } ?: currencyCode
                    ListItem(
                        leadingContent  = {
                            Icon(Icons.Outlined.AttachMoney, null,
                                tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                modifier = Modifier.size(22.dp))
                        },
                        headlineContent  = { Text(stringResource(R.string.cat_currency)) },
                        supportingContent = {
                            Text(
                                currencyLabel,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        },
                        modifier = Modifier.clickable { showCurrencyPicker = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }

                // Бюджет
                item {
                    val budgetNum = budget.replace(",", ".").toDoubleOrNull() ?: 0.0
                    ListItem(
                        leadingContent  = {
                            Icon(Icons.Outlined.Speed, null,
                                tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                modifier = Modifier.size(22.dp))
                        },
                        headlineContent  = { Text(stringResource(R.string.cat_budget)) },
                        supportingContent = {
                            Text(
                                if (budgetNum > 0.0) "$budget ₴ / ${if (period == "MONTHLY") stringResource(R.string.cat_month) else stringResource(R.string.cat_week)}"
                                else stringResource(R.string.cat_no_limit),
                                color = if (budgetNum > 0.0) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                        },
                        modifier = Modifier.clickable { showBudgetCalc = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }

                // ── Підкатегорії (тільки для кореневих категорій) ────────────
                if (existing != null && existing.parentId == null) {
                    item {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            stringResource(R.string.cat_subcategories),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                    }

                    items(children) { child ->
                        val childColor = remember(child.colorHex) {
                            parseColorHex(child.colorHex, Color.Gray)
                        }
                        ListItem(
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(childColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        categoryIconFor(child.icon), null,
                                        tint     = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            headlineContent = { Text(child.name) },
                            trailingContent = if (onDetachSubcategory != null || onDeleteSubcategory != null) ({
                                var menuOpen by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = null,
                                            tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        if (onDetachSubcategory != null) {
                                            DropdownMenuItem(
                                                text        = { Text(stringResource(R.string.cat_detach)) },
                                                leadingIcon = { Icon(Icons.Outlined.LinkOff, null) },
                                                onClick     = { menuOpen = false; onDetachSubcategory(child) }
                                            )
                                        }
                                        if (onDeleteSubcategory != null) {
                                            DropdownMenuItem(
                                                text        = { Text(stringResource(R.string.cat_delete_sub),
                                                                    color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icon(Icons.Default.Delete, null,
                                                                    tint = MaterialTheme.colorScheme.error) },
                                                onClick     = { menuOpen = false; deleteChildTarget = child }
                                            )
                                        }
                                    }
                                }
                            }) else null
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    }

                    if (onAddSubcategory != null) {
                        item {
                            ListItem(
                                leadingContent  = {
                                    Icon(Icons.Default.Add, null,
                                        tint     = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp))
                                },
                                headlineContent = {
                                    Text(stringResource(R.string.cat_add_sub),
                                        color = MaterialTheme.colorScheme.primary)
                                },
                                modifier = Modifier.clickable { onAddSubcategory() }
                            )
                            HorizontalDivider()
                        }
                    }
                }

                // ── Батьківська категорія (тільки для підкатегорій) ──────────
                if (existing != null && existing.parentId != null && onChangeParent != null) {
                    item {
                        val parentName = parentOptions.firstOrNull { it.id == existing.parentId }?.name
                            ?: stringResource(R.string.cat_parent_none)
                        ListItem(
                            leadingContent   = {
                                Icon(Icons.Outlined.Category, null,
                                    tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                    modifier = Modifier.size(22.dp))
                            },
                            headlineContent  = { Text(stringResource(R.string.cat_parent)) },
                            supportingContent = {
                                Text(parentName, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                            },
                            modifier = Modifier.clickable { showParentPicker = true }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    }
                }

                // ── Архів ────────────────────────────────────────────────────
                item {
                    ListItem(
                        leadingContent  = {
                            Icon(Icons.Outlined.Archive, null,
                                tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                modifier = Modifier.size(22.dp))
                        },
                        headlineContent  = { Text(stringResource(R.string.cat_archived)) },
                        trailingContent  = {
                            Switch(checked = archived, onCheckedChange = { archived = it })
                        },
                        modifier = Modifier.clickable { archived = !archived }
                    )
                    HorizontalDivider()
                }

                // ── Видалити ─────────────────────────────────────────────────
                if (onDelete != null) {
                    item {
                        ListItem(
                            leadingContent  = {
                                Icon(Icons.Default.Delete, null,
                                    tint     = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp))
                            },
                            headlineContent = {
                                Text(stringResource(R.string.cat_delete),
                                    color = MaterialTheme.colorScheme.error)
                            },
                            modifier = Modifier.clickable { showDeleteConfirm = true }
                        )
                    }
                }
            }
        }
    }

    CategoryFormDialogs(
        showIconPicker     = showIconPicker,
        colorHex           = colorHex,
        iconKey            = iconKey,
        onIconSave         = { newColor, newIcon -> colorHex = newColor; iconKey = newIcon; showIconPicker = false },
        onIconDismiss      = { showIconPicker = false },
        showDeleteConfirm  = showDeleteConfirm,
        onDeleteConfirm    = { showDeleteConfirm = false; onDelete?.invoke() },
        onDeleteDismiss    = { showDeleteConfirm = false },
        showBudgetCalc     = showBudgetCalc,
        budget             = budget,
        onBudgetResult     = { v -> budget = if (v <= 0.0) "" else v.toBigDecimal().stripTrailingZeros().toPlainString(); showBudgetCalc = false },
        onBudgetDismiss    = { showBudgetCalc = false },
        showCurrencyPicker = showCurrencyPicker,
        currencyCode       = currencyCode,
        onCurrencySelect   = { code -> currencyCode = code; showCurrencyPicker = false },
        onCurrencyDismiss  = { showCurrencyPicker = false },
    )

    // Вибір нового батька для підкатегорії
    if (showParentPicker && existing != null) {
        ParentPickerDialog(
            options       = parentOptions,
            currentParent = existing.parentId,
            onSelect      = { newParentId ->
                showParentPicker = false
                onChangeParent?.invoke(newParentId)
                onDismiss()
            },
            onDismiss     = { showParentPicker = false }
        )
    }

    // Підтвердження видалення підкатегорії
    deleteChildTarget?.let { child ->
        ConfirmationDialog(
            title       = stringResource(R.string.cat_delete_sub_title),
            message     = stringResource(R.string.cat_delete_message),
            icon        = Icons.Default.Delete,
            confirmText = stringResource(R.string.cat_delete_sub),
            onConfirm   = { deleteChildTarget = null; onDeleteSubcategory?.invoke(child) },
            onDismiss   = { deleteChildTarget = null }
        )
    }
}

// ── Пікер батьківської категорії (для зміни батька підкатегорії) ──────────────

@Composable
private fun ParentPickerDialog(
    options:       List<CategoryEntity>,
    currentParent: Long?,
    onSelect:      (Long?) -> Unit,
    onDismiss:     () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text(stringResource(R.string.cat_select_parent)) },
        text             = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                // Варіант «без батьківської» (зробити верхнім рівнем)
                item {
                    ParentPickerRow(
                        label      = stringResource(R.string.cat_parent_none),
                        color      = null,
                        icon       = null,
                        selected   = currentParent == null,
                        onClick    = { onSelect(null) }
                    )
                }
                items(options) { opt ->
                    ParentPickerRow(
                        label    = opt.name,
                        color    = parseColorHex(opt.colorHex, Color.Gray),
                        icon     = categoryIconFor(opt.icon),
                        selected = opt.id == currentParent,
                        onClick  = { onSelect(opt.id) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun ParentPickerRow(
    label:    String,
    color:    Color?,
    icon:     ImageVector?,
    selected: Boolean,
    onClick:  () -> Unit
) {
    ListItem(
        colors          = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier        = Modifier.clickable(onClick = onClick),
        leadingContent  = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color ?: MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Outlined.LinkOff, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp))
                }
            }
        },
        headlineContent = { Text(label) },
        trailingContent = if (selected) ({
            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
        }) else null
    )
}

/** Шапка форми категорії: поле назви + велика кольорова іконка (клік → пікер). */
@Composable
private fun CategoryFormHeader(
    name:               String,
    onNameChange:       (String) -> Unit,
    catColor:           Color,
    iconKey:            String,
    nameFocusRequester: FocusRequester,
    autoFocus: Boolean,
    onNameFocusChange:  (Boolean) -> Unit,
    onImeDone:          () -> Unit,
    onIconClick:        () -> Unit,
) {
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(placed, autoFocus) {
        if (placed && autoFocus) {
            try { nameFocusRequester.requestFocus() } catch (failure: IllegalStateException) {
                org.syalosovetskyi.onemoney.CrashReporter.warning("CategoryForm", "Name field detached before focus", failure)
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp)
    ) {
        Column(modifier = Modifier.padding(end = 76.dp)) {
            Text(
                stringResource(R.string.acc_name_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
            Spacer(Modifier.height(6.dp))
            BasicTextField(
                value          = name,
                onValueChange  = onNameChange,
                textStyle      = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush    = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine     = true,
                keyboardOptions   = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions   = KeyboardActions(onDone = { onImeDone() }),
                modifier       = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocusRequester)
                    .onGloballyPositioned { placed = it.isAttached }
                    .onFocusChanged { onNameFocusChange(it.isFocused) },
                decorationBox  = { inner ->
                    Box {
                        if (name.isBlank()) {
                            Text(
                                stringResource(R.string.cat_name_hint),
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            )
                        }
                        inner()
                    }
                }
            )
        }
        // Велика кольорова іконка праворуч
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(64.dp)
                .clip(RoundedCornerShape(OneMoneyTheme.dimens.largeRadius))
                .background(catColor)
                .clickable { onIconClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                categoryIconFor(iconKey), null,
                tint     = Color.White,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

/** Хвостові діалоги форми категорії (пікер кольору/іконки, підтвердження
 *  видалення, бюджет-калькулятор, вибір валюти). Винесено з CategoryFormSheet
 *  для читабельності. */
@Composable
private fun CategoryFormDialogs(
    showIconPicker:     Boolean,
    colorHex:           String,
    iconKey:            String,
    onIconSave:         (String, String) -> Unit,
    onIconDismiss:      () -> Unit,
    showDeleteConfirm:  Boolean,
    onDeleteConfirm:    () -> Unit,
    onDeleteDismiss:    () -> Unit,
    showBudgetCalc:     Boolean,
    budget:             String,
    onBudgetResult:     (Double) -> Unit,
    onBudgetDismiss:    () -> Unit,
    showCurrencyPicker: Boolean,
    currencyCode:       String,
    onCurrencySelect:   (String) -> Unit,
    onCurrencyDismiss:  () -> Unit,
) {
    if (showIconPicker) {
        ColorIconPickerSheet(
            currentColor = colorHex,
            currentIcon  = iconKey,
            onSave       = onIconSave,
            onDismiss    = onIconDismiss
        )
    }

    if (showDeleteConfirm) {
        ConfirmationDialog(
            title     = stringResource(R.string.cat_delete_title),
            message   = stringResource(R.string.cat_delete_message),
            onConfirm = onDeleteConfirm,
            onDismiss = onDeleteDismiss
        )
    }

    if (showBudgetCalc) {
        AmountCalculatorSheet(
            initial        = budget.replace(",", ".").toDoubleOrNull() ?: 0.0,
            currencySymbol = currencySymbol(currencyCode),
            title          = stringResource(R.string.cat_budget),
            onResult       = onBudgetResult,
            onDismiss      = onBudgetDismiss
        )
    }

    if (showCurrencyPicker) {
        Dialog(
            onDismissRequest = onCurrencyDismiss,
            properties       = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            CurrencyPageContent(
                title    = stringResource(R.string.cat_currency),
                selected = currencyCode,
                onSelect = onCurrencySelect,
                onClose  = onCurrencyDismiss
            )
        }
    }
}

// ── Пікер кольору та іконки ───────────────────────────────────────────────────

@Composable
internal fun ColorIconPickerSheet(
    currentColor: String,
    currentIcon:  String,
    onSave:       (color: String, icon: String) -> Unit,
    onDismiss:    () -> Unit
) {
    var selectedColor by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(currentColor) }
    var selectedIcon  by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(currentIcon) }
    val color by remember { derivedStateOf {
        parseColorHex(selectedColor, FallbackCategoryColor)
    } }
    var selectedTab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = Spacing.xs, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.acc_close), modifier = Modifier.size(34.dp))
                    }
                    Text(
                        stringResource(R.string.cat_icon_title),
                        modifier = Modifier.weight(1f).padding(start = Spacing.sm),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { onSave(selectedColor, selectedIcon) },
                        shape = RoundedCornerShape(28.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        Text(stringResource(R.string.common_done), fontWeight = FontWeight.Bold)
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(CircleShape)
                            .background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            categoryIconFor(selectedIcon),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(58.dp)
                        )
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Outlined.StarBorder, null, modifier = Modifier.size(30.dp)) },
                        text = { Text(stringResource(R.string.cat_icon), fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Outlined.Palette, null, modifier = Modifier.size(30.dp)) },
                        text = { Text(stringResource(R.string.cat_color), fontWeight = FontWeight.SemiBold) }
                    )
                }

                if (selectedTab == 0) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        items(CATEGORY_ICONS_LIST) { (key, icon) ->
                            val isSel = key == selectedIcon
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(if (isSel) color else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                    .clickable { selectedIcon = key },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(CATEGORY_FORM_COLORS) { hex ->
                            val swatch = remember(hex) {
                                parseColorHex(hex, Color.Gray)
                            }
                            val isSel = hex == selectedColor
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .then(
                                        if (isSel) Modifier.border(
                                            width = 4.dp,
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = CircleShape
                                        ) else Modifier
                                    )
                                    .clickable { selectedColor = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSel) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(7.dp)
                                            .border(OneMoneyTheme.dimens.selectionRingWidth, Color.White, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Edit Categories Screen (повноекранний діалог) ─────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCategoriesScreen(
    expenseCategories:  List<CategoryEntity>,
    incomeCategories:   List<CategoryEntity>,
    monthSpending:      Map<Long, Double>    = emptyMap(),
    monthIncome:        Map<Long, Double>    = emptyMap(),
    totalExpense:       Double               = 0.0,
    totalIncome:        Double               = 0.0,
    onSave:             (name: String, type: TransactionType, color: String, icon: String, budget: Double, period: String, archived: Boolean, currencyCode: String, existing: CategoryEntity?) -> Unit,
    onAddSubcategory:   ((name: String, type: TransactionType, color: String, icon: String, budget: Double, period: String, currencyCode: String, parentId: Long) -> Unit)? = null,
    onDelete:           (CategoryEntity) -> Unit,
    onDismiss:          () -> Unit
) {
    var selectedTab       by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    var editCategory      by remember { mutableStateOf<CategoryEntity?>(null) }
    var addSubcategoryTo  by remember { mutableStateOf<CategoryEntity?>(null) }
    var showAddSheet      by remember { mutableStateOf(false) }
    var showSubcategories by remember { mutableStateOf(false) }

    val allCats  = if (selectedTab == 0) expenseCategories.filter { !it.archived }
                   else incomeCategories.filter { !it.archived }
    val categories = if (showSubcategories) allCats.filter { it.parentId != null }
                     else allCats.filter { it.parentId == null }
    val spending    = if (selectedTab == 0) monthSpending else monthIncome
    val defaultType = if (selectedTab == 0) TransactionType.EXPENSE else TransactionType.INCOME

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(if (showSubcategories) stringResource(R.string.cat_edit_subcategories) else stringResource(R.string.cat_edit_categories)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { showSubcategories = !showSubcategories }
                        ) {
                            Text(
                                if (showSubcategories) stringResource(R.string.nav_categories) else stringResource(R.string.cat_subcategories_tab),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (showSubcategories)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor   = MaterialTheme.colorScheme.surface,
                    contentColor     = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick  = { selectedTab = 0 },
                        icon     = { Icon(Icons.Outlined.ArrowCircleDown, null, modifier = Modifier.size(20.dp)) },
                        text     = { Text(stringResource(R.string.common_expenses), fontWeight = if (selectedTab == 0) FontWeight.Normal else FontWeight.Normal) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick  = { selectedTab = 1 },
                        icon     = { Icon(Icons.Outlined.ArrowCircleUp, null, modifier = Modifier.size(20.dp)) },
                        text     = { Text(stringResource(R.string.common_incomes), fontWeight = if (selectedTab == 1) FontWeight.Normal else FontWeight.Normal) }
                    )
                }

                val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                CategoriesGridContent(
                    categories          = categories,
                    allCategoriesForTab = allCats,
                    spending            = spending,
                    totalExpense        = totalExpense,
                    totalIncome         = totalIncome,
                    selectedTab         = selectedTab,
                    onToggleTab         = { selectedTab = if (selectedTab == 0) 1 else 0 },
                    bottomPadding       = navBarPadding + 16.dp,
                    onChipClick         = { cat -> editCategory = cat },
                    onAdd               = { showAddSheet = true },
                    showSubcategories   = showSubcategories,
                    sortBySpending      = false
                )
            }
        }
    }

    editCategory?.let { cat ->
        val catChildren = (if (cat.type == TransactionType.EXPENSE) expenseCategories else incomeCategories)
            .filter { it.parentId == cat.id }
        CategoryFormSheet(
            existing             = cat,
            children             = catChildren,
            onAddSubcategory     = if (cat.parentId == null && onAddSubcategory != null)
                                       ({ addSubcategoryTo = cat }) else null,
            onDetachSubcategory  = if (cat.parentId == null) ({ child ->
                onSave(child.name, child.type, child.colorHex, child.icon,
                       child.budgetAmount, child.budgetPeriod, child.archived,
                       child.currencyCode, child.copy(parentId = null))
            }) else null,
            defaultType          = cat.type,
            onSave               = { name, type, color, icon, budget, period, arch, currency ->
                onSave(name, type, color, icon, budget, period, arch, currency, cat)
                editCategory = null
            },
            onDelete    = { onDelete(cat); editCategory = null },
            onDismiss   = { editCategory = null }
        )
    }

    addSubcategoryTo?.let { parent ->
        CategoryFormSheet(
            existing    = null,
            forParentId = parent.id,
            defaultType = parent.type,
            onSave      = { name, type, color, icon, budget, period, _, currency ->
                onAddSubcategory?.invoke(name, type, color, icon, budget, period, currency, parent.id)
                addSubcategoryTo = null
            },
            onDismiss   = { addSubcategoryTo = null }
        )
    }

    if (showAddSheet) {
        CategoryFormSheet(
            existing    = null,
            defaultType = defaultType,
            onSave      = { name, type, color, icon, budget, period, _, currency ->
                onSave(name, type, color, icon, budget, period, false, currency, null)
                showAddSheet = false
            },
            onDismiss   = { showAddSheet = false }
        )
    }
}
