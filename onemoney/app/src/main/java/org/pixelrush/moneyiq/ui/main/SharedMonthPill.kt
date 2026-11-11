package org.syalosovetskyi.onemoney.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.data.repository.AppMonth
import org.syalosovetskyi.onemoney.data.repository.PeriodMode
import org.syalosovetskyi.onemoney.ui.components.icons.DoubleChevronLeft
import org.syalosovetskyi.onemoney.ui.components.icons.DoubleChevronRight
import org.syalosovetskyi.onemoney.ui.theme.MonthRed
import org.syalosovetskyi.onemoney.ui.theme.MonthRedLight
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import java.util.*

private class Ref<T>(var value: T)

// ── Загальна пілюля навігації по місяцю ──────────────────────────────────────

/**
 * Єдина малинова пілюля навігації.
 * При кліку — відкривається [PeriodSelectorSheet].
 */
// ── Helpers ───────────────────────────────────────────────────────────────────

/** Локалізовані назви місяців (називний відмінок), 0-based. */
@Composable
internal fun localizedMonths(): Array<String> = stringArrayResource(R.array.month_names)

/** Мітка пілюлі періоду — форматується у Composable-шарі (локаль/ресурси доступні). */
@Composable
internal fun monthPillLabel(a: AppMonth): String {
    val months = localizedMonths()
    val fromStart = stringResource(R.string.period_from_start)
    val today = Calendar.getInstance()
    return when (a.mode) {
        PeriodMode.MONTH -> "${months[a.month].uppercase()} ${a.year}"
        PeriodMode.TODAY -> {
            val d = today.get(Calendar.DAY_OF_MONTH)
            "$d ${months[today.get(Calendar.MONTH)]}"
        }
        PeriodMode.WEEK -> {
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            val s = "${cal.get(Calendar.DAY_OF_MONTH)} ${months[cal.get(Calendar.MONTH)]}"
            cal.add(Calendar.DAY_OF_MONTH, 6)
            val e = "${cal.get(Calendar.DAY_OF_MONTH)} ${months[cal.get(Calendar.MONTH)]}"
            "$s — $e"
        }
        PeriodMode.YEAR  -> "${a.year}"
        PeriodMode.ALL   -> fromStart
        PeriodMode.DAY   -> {
            val cal = Calendar.getInstance().apply { timeInMillis = a.fromMillis }
            "${cal.get(Calendar.DAY_OF_MONTH)} ${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"
        }
        PeriodMode.RANGE -> {
            val s = Calendar.getInstance().apply { timeInMillis = a.fromMillis }
            val e = Calendar.getInstance().apply { timeInMillis = a.toMillis }
            fun fmt(c: Calendar) = "${c.get(Calendar.DAY_OF_MONTH)} ${months[c.get(Calendar.MONTH)]}"
            "${fmt(s)} — ${fmt(e)}"
        }
    }
}

internal fun pillBadgeFor(a: AppMonth, daysInPeriod: Int): String = when (a.mode) {
    PeriodMode.ALL   -> "∞"
    PeriodMode.TODAY -> "1"
    PeriodMode.WEEK  -> "7"
    else             -> "$daysInPeriod"
}

// ── Загальна пілюля навігації по місяцю ──────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMonthNavPill(
    appMonth:       AppMonth,
    daysInPeriod:   Int,
    onPrev:         () -> Unit,
    onNext:         () -> Unit,
    onSelectPeriod: (AppMonth) -> Unit = {}
) {
    var showSheet by remember { mutableStateOf(false) }

    val pillLabel = monthPillLabel(appMonth)
    val pillBadge = pillBadgeFor(appMonth, daysInPeriod)

    val today = Calendar.getInstance()
    val isCurrentMonth = appMonth.mode == PeriodMode.MONTH &&
        appMonth.month == today.get(Calendar.MONTH) &&
        appMonth.year  == today.get(Calendar.YEAR)
    // Малиновим підсвічується стрілка, що веде В БІК поточного місяця:
    // обраний місяць у минулому (раніше за сьогодні) → права (next);
    // обраний місяць у майбутньому (пізніше за сьогодні) → ліва (prev);
    // поточний місяць → жодна.
    val todayOrd   = today.get(Calendar.YEAR) * 12 + today.get(Calendar.MONTH)
    val selOrd     = appMonth.year * 12 + appMonth.month
    val nextIsHot  = appMonth.mode == PeriodMode.MONTH && selOrd < todayOrd
    val prevIsHot  = appMonth.mode == PeriodMode.MONTH && selOrd > todayOrd
    val pillColor = if (isCurrentMonth) Color(0xFF111111) else MonthRed
    val pillBg    = Color(0xFFD5D6EC)
    val dimens    = OneMoneyTheme.dimens

    // Track swipe direction without extra recompositions (plain Ref, not MutableState)
    val prevRef    = remember { Ref(appMonth) }
    val forwardRef = remember { Ref(true) }
    remember(appMonth) {
        forwardRef.value = when {
            appMonth.year  > prevRef.value.year  -> true
            appMonth.year  < prevRef.value.year  -> false
            appMonth.month > prevRef.value.month -> true
            else                                 -> false
        }
        prevRef.value = appMonth
    }

    if (showSheet) {
        PeriodSelectorSheet(
            appMonth       = appMonth,
            daysInPeriod   = daysInPeriod,
            onDismiss      = { showSheet = false },
            onSelectPeriod = { p ->
                onSelectPeriod(p)
                showSheet = false
            }
        )
    }

    val headerBg = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .background(headerBg)
            .padding(start = 24.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            DoubleChevronLeft, stringResource(R.string.period_prev),
            tint     = if (prevIsHot) MonthRed else Color(0xFF111111),
            modifier = Modifier
                .size(dimens.pillArrowSize)
                .testTag("month_pill_prev")
                .clickable(onClick = onPrev)
                .padding(2.dp)
        )

        Surface(
            shape    = RoundedCornerShape(dimens.pillRadius),
            color    = pillBg,
            modifier = Modifier
                .testTag("month_pill")
                .clickable { showSheet = true }
        ) {
            // Animate only the inner content; the pill capsule shape stays static
            AnimatedContent(
                targetState   = pillLabel to pillBadge,
                transitionSpec = {
                    val fwd = forwardRef.value
                    val enter = slideInHorizontally(tween(220)) { if (fwd)  it else -it } +
                                fadeIn(tween(180))
                    val exit  = slideOutHorizontally(tween(180)) { if (fwd) -it else  it } +
                                fadeOut(tween(120))
                    enter togetherWith exit
                },
                label    = "month_pill",
                modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp)
            ) { (label, badge) ->
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Surface(
                        shape  = RoundedCornerShape(dimens.pillBadgeRadius),
                        color  = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(dimens.thinStroke, pillColor)
                    ) {
                        Text(
                            badge,
                            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style      = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 12.sp, letterSpacing = 0.sp),
                            fontWeight = FontWeight.Light,
                            color      = pillColor
                        )
                    }
                    Text(
                        label,
                        style      = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.sp, letterSpacing = 0.sp),
                        fontWeight = FontWeight.Light,
                        color      = pillColor
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown, null,
                        tint     = pillColor,
                        modifier = Modifier.size(dimens.pillDropArrowSize)
                    )
                }
            }
        }

        Icon(
            DoubleChevronRight, stringResource(R.string.period_next),
            tint     = if (nextIsHot) MonthRed else Color(0xFF111111),
            modifier = Modifier
                .size(dimens.pillArrowSize)
                .testTag("month_pill_next")
                .clickable(onClick = onNext)
                .padding(2.dp)
        )
    }
}

// ── Period Selector Bottom Sheet ──────────────────────────────────────────────

private data class PeriodOption(
    val id:       String,
    val label:    String,
    val subLabel: String,
    val badge:    String?,          // null → icon
    val icon:     ImageVector?,
    val color:    Color = MonthRed
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSelectorSheet(
    appMonth:      AppMonth,
    daysInPeriod:  Int,
    onDismiss:     () -> Unit,
    onSelectPeriod: (AppMonth) -> Unit
) {
    val today     = Calendar.getInstance()
    val todayYear = today.get(Calendar.YEAR)
    val todayMon  = today.get(Calendar.MONTH)
    val todayDay  = today.get(Calendar.DAY_OF_MONTH)
    val yearDays  = if (todayYear % 4 == 0 && (todayYear % 100 != 0 || todayYear % 400 == 0)) 366 else 365

    val months = localizedMonths()
    val weekStart = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek) }
    val weekEnd   = (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 6) }
    fun monthFull(c: Calendar) = months[c.get(Calendar.MONTH)]
    val wkLabel = "${weekStart.get(Calendar.DAY_OF_MONTH)} ${monthFull(weekStart)}" +
                  " — ${weekEnd.get(Calendar.DAY_OF_MONTH)} ${monthFull(weekEnd)}"

    val options = listOf(
        PeriodOption("all",   stringResource(R.string.period_all),      stringResource(R.string.period_all_sub),
                     "∞",   null, Color(0xFF607D8B)),
        PeriodOption("day",   stringResource(R.string.period_pick_day), stringResource(R.string.period_pick_day_sub),
                     null,  Icons.Default.CalendarMonth, Color(0xFF9E9E9E)),
        PeriodOption("week",  stringResource(R.string.period_week),     wkLabel,
                     "7",   null, Color(0xFF26A69A)),
        PeriodOption("today", stringResource(R.string.period_today),    "$todayDay ${months[todayMon]}",
                     "1",   null, Color(0xFF42A5F5)),
        PeriodOption("year",  stringResource(R.string.period_year),     "$todayYear",
                     "$yearDays", null, Color(0xFFEF6C00)),
        PeriodOption("month", stringResource(R.string.period_month),    "${months[appMonth.month]} ${appMonth.year}",
                     "$daysInPeriod", null, MonthRed),
    )

    val selectedId = when (appMonth.mode) {
        PeriodMode.MONTH -> "month"
        PeriodMode.TODAY -> "today"
        PeriodMode.WEEK  -> "week"
        PeriodMode.YEAR  -> "year"
        PeriodMode.ALL   -> "all"
        PeriodMode.DAY   -> "day"
        PeriodMode.RANGE -> "range"
    }

    var showDayPicker   by remember { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }

    if (showDayPicker) {
        val dpState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDayPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { ms ->
                        val cal  = Calendar.getInstance().apply { timeInMillis = ms }
                        val from = cal.apply {
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        val to = from + 86_399_999L
                        onSelectPeriod(AppMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                                                PeriodMode.DAY, from, to))
                        showDayPicker = false
                        onDismiss()
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDayPicker = false }) { Text(stringResource(R.string.common_cancel)) } }
        ) { DatePicker(state = dpState) }
        return
    }

    if (showRangePicker) {
        DateRangePickerFullScreen(
            onDismiss = { showRangePicker = false },
            onConfirm = { startMs, endMs ->
                val cal = Calendar.getInstance().apply { timeInMillis = startMs }
                onSelectPeriod(AppMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                                        PeriodMode.RANGE, startMs, endMs))
                showRangePicker = false
                onDismiss()
            }
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)))
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
            Text(
                stringResource(R.string.period_title),
                modifier   = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center
            )

            Surface(
                onClick  = { showRangePicker = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                shape    = RoundedCornerShape(OneMoneyTheme.dimens.largeRadius),
                color    = if (selectedId == "range") MonthRed.copy(alpha = 0.10f)
                           else MaterialTheme.colorScheme.surfaceVariant,
                border   = if (selectedId == "range")
                               androidx.compose.foundation.BorderStroke(2.dp, MonthRed) else null
            ) {
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(MonthRed.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("···", color = MonthRed, fontWeight = FontWeight.Bold,
                             style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.period_pick_range), style = MaterialTheme.typography.bodyMedium,
                             fontWeight = FontWeight.SemiBold)
                        Text(
                            "${months[appMonth.month]} ${appMonth.year}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null,
                         tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                         modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            options.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    pair.forEach { opt ->
                        PeriodOptionCard(
                            option     = opt,
                            isSelected = opt.id == selectedId,
                            modifier   = Modifier.weight(1f),
                            onClick    = {
                                when (opt.id) {
                                    "day"   -> showDayPicker = true
                                    "month" -> onSelectPeriod(AppMonth(appMonth.year, appMonth.month, PeriodMode.MONTH))
                                    "today" -> onSelectPeriod(AppMonth(todayYear, todayMon, PeriodMode.TODAY))
                                    "week"  -> onSelectPeriod(AppMonth(todayYear, todayMon, PeriodMode.WEEK))
                                    "year"  -> onSelectPeriod(AppMonth(todayYear, 0, PeriodMode.YEAR))
                                    "all"   -> onSelectPeriod(AppMonth(todayYear, todayMon, PeriodMode.ALL))
                                }
                            }
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ── Картка варіанту ───────────────────────────────────────────────────────────

@Composable
private fun PeriodOptionCard(
    option:     PeriodOption,
    isSelected: Boolean,
    modifier:   Modifier = Modifier,
    onClick:    () -> Unit
) {
    val bg = if (isSelected) option.color.copy(alpha = 0.10f)
             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)

    val cardDimens = OneMoneyTheme.dimens
    Card(
        modifier = modifier
            .height(90.dp)
            .clickable(onClick = onClick),
        shape  = RoundedCornerShape(cardDimens.largeRadius),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = if (isSelected)
            androidx.compose.foundation.BorderStroke(cardDimens.thickStroke, option.color)
        else null
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.md, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Верх: бейдж або іконка
            if (option.badge != null) {
                // Закруглений прямокутник (як в оригіналі)
                Surface(
                    shape = RoundedCornerShape(cardDimens.smallRadius),
                    color = if (isSelected) option.color else option.color.copy(alpha = 0.15f)
                ) {
                    Text(
                        option.badge,
                        modifier   = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color      = if (isSelected) Color.White else option.color
                    )
                }
            } else if (option.icon != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(cardDimens.smallRadius))
                        .background(option.color.copy(alpha = if (isSelected) 1f else 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        option.icon, null,
                        tint     = if (isSelected) Color.White else option.color,
                        modifier = Modifier.size(cardDimens.listItemSmallIcon)
                    )
                }
            }

            // Низ: назва + підпис
            Column {
                Text(
                    option.label,
                    style      = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (isSelected) option.color else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    option.subLabel,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1
                )
            }
        }
    }
}

// ── Повноекранний вибір діапазону дат ────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerFullScreen(
    onDismiss: () -> Unit,
    onConfirm: (startMs: Long, endMs: Long) -> Unit
) {
    val rangeState = rememberDateRangePickerState()
    val canSave    = rangeState.selectedStartDateMillis != null

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar   = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.acc_close))
                        }
                    },
                    title  = {},
                    actions = {
                        TextButton(
                            onClick  = {
                                val s = rangeState.selectedStartDateMillis ?: return@TextButton
                                val e = rangeState.selectedEndDateMillis ?: s
                                onConfirm(s, e)
                            },
                            enabled = canSave
                        ) {
                            Text(
                                stringResource(R.string.common_save),
                                fontWeight = FontWeight.SemiBold,
                                color      = if (canSave) MonthRed
                                             else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
            DateRangePicker(
                state    = rangeState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                title    = {
                    Text(
                        stringResource(R.string.period_pick_range),
                        modifier   = Modifier.padding(start = 64.dp, top = 16.dp),
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                headline = null,
                showModeToggle = false
            )
        }
    }
}
