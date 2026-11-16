package org.syalosovetskyi.onemoney.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.data.repository.*
import org.syalosovetskyi.onemoney.ui.accounts.currencyDisplayName
import org.syalosovetskyi.onemoney.ui.components.currency.CurrencyPageContent
import org.syalosovetskyi.onemoney.ui.settings.data.*
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme

// ── Enum внутрішньої навігації ─────────────────────────────────────────────────

private enum class SettingsPage { MAIN, THEME, CURRENCY, ABOUT }

// ── Головний контейнер ─────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onData:         () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    var page by remember { mutableStateOf(SettingsPage.MAIN) }

    val onBack: () -> Unit = {
        if (page == SettingsPage.MAIN) onNavigateBack() else page = SettingsPage.MAIN
    }

    BackHandler(onBack = onBack)

    // Settings is drawn outside any Scaffold: the Surface provides LocalContentColor,
    // otherwise untinted Text/Icon stay Color.Black in dark theme (#3037 R7).
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (page) {
            SettingsPage.MAIN     -> MainSettingsContent(
                settings   = settings,
                vm         = viewModel,
                onTheme    = { page = SettingsPage.THEME },
                onCurrency = { page = SettingsPage.CURRENCY },
                onData     = onData,
                onAbout    = { page = SettingsPage.ABOUT },
                onBack     = onBack
            )
            SettingsPage.THEME    -> ThemePageContent(
                settings = settings,
                vm       = viewModel,
                onBack   = onBack
            )
            SettingsPage.CURRENCY -> CurrencyPageContent(
                selected = settings.defaultCurrency,
                onSelect = { code -> viewModel.setDefaultCurrency(code); page = SettingsPage.MAIN },
                onClose  = { page = SettingsPage.MAIN }
            )
            SettingsPage.ABOUT    -> AboutPageContent(onBack = onBack)
        }
    }
}

// ── Головна сторінка налаштувань ───────────────────────────────────────────────

@Composable
private fun MainSettingsContent(
    settings:   AppSettings,
    vm:         SettingsViewModel,
    onTheme:    () -> Unit,
    onCurrency: () -> Unit,
    onData:     () -> Unit,
    onAbout:    () -> Unit,
    onBack:     () -> Unit
) {
    var showHomeDialog       by remember { mutableStateOf(false) }
    var showFormatDialog     by remember { mutableStateOf(false) }
    var showWeekDialog       by remember { mutableStateOf(false) }
    var showMonthDialog      by remember { mutableStateOf(false) }
    var showNotifTimeDialog  by remember { mutableStateOf(false) }
    var showLangDialog       by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current

    val currencyInfo = CURRENCIES_ALL.find { it.code == settings.defaultCurrency }
    val currencyLabel = currencyInfo?.let { "${currencyDisplayName(it.code)} — ${it.symbol}" } ?: settings.defaultCurrency
    val formatLabel = CURRENCY_FORMAT_EXAMPLES.getOrNull(settings.currencyFormatIndex) ?: ""
    val weekDays    = stringArrayResource(R.array.week_days)
    val weekLabel   = weekDays.getOrElse(settings.firstDayOfWeek - 1) { weekDays[1] }
    val themeLabel  = when (settings.themeMode) {
        ThemeMode.LIGHT  -> stringResource(R.string.settings_theme_light)
        ThemeMode.DARK   -> stringResource(R.string.settings_theme_dark)
        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    }
    val defaultLangLabel = stringResource(R.string.settings_lang_default)
    val langLabel = LANGUAGES.find { it.first == settings.language }
        ?.let { if (it.first == "default") defaultLangLabel else it.second } ?: defaultLangLabel

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        SettingsTopBar(title = stringResource(R.string.settings_title), onBack = onBack)

        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)) {
            // ── Картка 1: Мова / Тема / Дані ─────────────────────────────────
            item {
                SettingsCard {
                    SettingsRow(
                        icon        = Icons.Default.Language,
                        title       = stringResource(R.string.settings_language),
                        subtitle    = langLabel,
                        subtitleColor = MaterialTheme.colorScheme.primary,
                        onClick     = { showLangDialog = true }
                    )
                    SettingsRow(
                        icon        = Icons.Default.Palette,
                        title       = stringResource(R.string.settings_theme),
                        subtitle    = themeLabel,
                        subtitleColor = MaterialTheme.colorScheme.primary,
                        onClick     = onTheme
                    )
                    SettingsRow(
                        icon    = Icons.Default.Storage,
                        title   = stringResource(R.string.settings_data),
                        showDivider = false,
                        onClick = onData
                    )
                }
            }

            // ── Картка 2: Екран / Перемикачі ─────────────────────────────────
            item {
                SettingsCard {
                    SettingsRow(
                        icon      = Icons.Default.Home,
                        title     = stringResource(R.string.settings_home_screen),
                        subtitle  = stringResource(settings.homeScreen.labelRes),
                        subtitleColor = MaterialTheme.colorScheme.primary,
                        onClick   = { showHomeDialog = true }
                    )
                    SettingsToggleRow(
                        icon    = Icons.Default.PieChart,
                        title   = stringResource(R.string.nav_budget),
                        checked = settings.budgetVisible,
                        onToggle = { vm.setBudgetVisible(it) }
                    )
                    SettingsToggleRow(
                        icon      = Icons.Default.Fingerprint,
                        title     = stringResource(R.string.settings_login_protection),
                        subtitle  = if (settings.loginProtectionEnabled) stringResource(R.string.settings_after_30s) else null,
                        checked   = settings.loginProtectionEnabled,
                        onToggle  = { vm.setLoginProtection(it) }
                    )
                    SettingsToggleRow(
                        icon      = Icons.Default.Notifications,
                        title     = stringResource(R.string.settings_notifications),
                        subtitle  = if (settings.notificationsEnabled)
                            "%02d:%02d".format(settings.notificationHour, settings.notificationMinute)
                            else null,
                        subtitleClickable = settings.notificationsEnabled,
                        showDivider = false,
                        checked   = settings.notificationsEnabled,
                        onToggle  = { vm.setNotifications(it, settings.notificationHour, settings.notificationMinute) },
                        onSubtitleClick = { showNotifTimeDialog = true }
                    )
                }
            }

            // ── Картка 3: Валюта / Формат / Дні ──────────────────────────────
            item {
                SettingsCard {
                    SettingsRow(
                        icon      = Icons.Default.AttachMoney,
                        title     = stringResource(R.string.settings_default_currency),
                        subtitle  = currencyLabel,
                        subtitleColor = MaterialTheme.colorScheme.primary,
                        onClick   = onCurrency
                    )
                    SettingsRow(
                        icon      = Icons.Default.FormatListNumbered,
                        title     = stringResource(R.string.settings_currency_format),
                        subtitle  = formatLabel,
                        subtitleColor = MaterialTheme.colorScheme.primary,
                        onClick   = { showFormatDialog = true }
                    )
                    SettingsRow(
                        icon      = Icons.Default.CalendarToday,
                        title     = stringResource(R.string.settings_first_day_week),
                        subtitle  = weekLabel,
                        subtitleColor = MaterialTheme.colorScheme.primary,
                        onClick   = { showWeekDialog = true }
                    )
                    SettingsRow(
                        icon      = Icons.Default.Event,
                        title     = stringResource(R.string.settings_first_day_month),
                        subtitle  = settings.firstDayOfMonth.toString(),
                        subtitleColor = MaterialTheme.colorScheme.primary,
                        showDivider = false,
                        onClick   = { showMonthDialog = true }
                    )
                }
            }

            // ── Картка 4: Про додаток ────────────────────────────────────────
            item {
                SettingsCard {
                    SettingsRow(
                        icon     = Icons.Default.Info,
                        title    = stringResource(R.string.settings_about),
                        showDivider = false,
                        onClick  = onAbout
                    )
                }
            }
        }
    }

    // ── Діалоги ───────────────────────────────────────────────────────────────

    if (showLangDialog) {
        RadioListDialog(
            title   = stringResource(R.string.settings_language),
            icon    = Icons.Default.Language,
            options = LANGUAGES.map { if (it.first == "default") defaultLangLabel else it.second },
            selected = LANGUAGES.indexOfFirst { it.first == settings.language }.coerceAtLeast(0),
            onSelect = { idx ->
                val tag = LANGUAGES[idx].first
                vm.setLanguage(tag)
                org.syalosovetskyi.onemoney.util.LocaleWrapper.setLang(context, tag)
                showLangDialog = false
                (context as? android.app.Activity)?.recreate()
            },
            onDismiss = { showLangDialog = false }
        )
    }

    if (showHomeDialog) {
        RadioListDialog(
            title    = stringResource(R.string.settings_home_screen),
            icon     = Icons.Default.Home,
            options  = HomeScreenTab.entries.map { stringResource(it.labelRes) },
            selected = settings.homeScreen.index,
            onSelect = { idx -> vm.setHomeScreen(HomeScreenTab.fromIndex(idx)); showHomeDialog = false },
            onDismiss = { showHomeDialog = false }
        )
    }

    if (showFormatDialog) {
        RadioListDialog(
            title    = stringResource(R.string.settings_currency_format),
            icon     = Icons.Default.FormatListNumbered,
            options  = CURRENCY_FORMAT_EXAMPLES.map { it.replace("UAH", currencyInfo?.symbol ?: "₴") },
            selected = settings.currencyFormatIndex,
            onSelect = { idx -> vm.setCurrencyFormat(idx); showFormatDialog = false },
            onDismiss = { showFormatDialog = false }
        )
    }

    if (showWeekDialog) {
        RadioListDialog(
            title    = stringResource(R.string.settings_first_day_week),
            icon     = Icons.Default.CalendarToday,
            options  = weekDays.toList(),
            selected = (settings.firstDayOfWeek - 1).coerceIn(0, 6),
            onSelect = { idx -> vm.setFirstDayOfWeek(idx + 1); showWeekDialog = false },
            onDismiss = { showWeekDialog = false }
        )
    }

    if (showMonthDialog) {
        RadioListDialog(
            title    = stringResource(R.string.settings_first_day_month),
            icon     = Icons.Default.Event,
            options  = (1..31).map { it.toString() },
            selected = (settings.firstDayOfMonth - 1).coerceIn(0, 30),
            onSelect = { idx -> vm.setFirstDayOfMonth(idx + 1); showMonthDialog = false },
            onDismiss = { showMonthDialog = false }
        )
    }

    if (showNotifTimeDialog) {
        TimePickerDialog(
            hour      = settings.notificationHour,
            minute    = settings.notificationMinute,
            onConfirm = { h, m -> vm.setNotifications(true, h, m); showNotifTimeDialog = false },
            onDismiss = { showNotifTimeDialog = false }
        )
    }
}
