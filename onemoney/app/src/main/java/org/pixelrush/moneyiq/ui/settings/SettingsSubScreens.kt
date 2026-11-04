package org.syalosovetskyi.onemoney.ui.settings

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.syalosovetskyi.onemoney.BuildConfig
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.data.repository.*
import org.syalosovetskyi.onemoney.ui.settings.data.*
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme

// ── Сторінка Теми ─────────────────────────────────────────────────────────────

@Composable
internal fun ThemePageContent(
    settings: AppSettings,
    vm:       SettingsViewModel,
    onBack:   () -> Unit
) {
    val themeLabel = when (settings.themeMode) {
        ThemeMode.LIGHT  -> stringResource(R.string.settings_theme_light)
        ThemeMode.DARK   -> stringResource(R.string.settings_theme_dark)
        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    }
    var showThemeModeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        SettingsTopBar(title = stringResource(R.string.settings_theme), onBack = onBack)

        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)) {
            // Єдина строка вибору теми: Системна (за системою) / Світла / Темна.
            item {
                SettingsCard {
                    SettingsRow(
                        icon      = Icons.Default.Brightness6,
                        title     = stringResource(R.string.settings_theme),
                        subtitle  = themeLabel,
                        subtitleColor = MaterialTheme.colorScheme.primary,
                        showDivider = false,
                        onClick   = { showThemeModeDialog = true }
                    )
                }
            }

            item {
                Text(
                    stringResource(R.string.cat_color),
                    modifier  = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 12.dp),
                    style     = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color     = MaterialTheme.colorScheme.primary
                )
            }
            item {
                ColorPalette(
                    selected = Color(settings.accentColorArgb),
                    onSelect = { color -> vm.setAccentColor(color.toArgb()) }
                )
            }
        }
    }

    if (showThemeModeDialog) {
        RadioListDialog(
            title    = stringResource(R.string.settings_theme),
            icon     = Icons.Default.Brightness6,
            options  = listOf(
                stringResource(R.string.settings_theme_system),
                stringResource(R.string.settings_theme_light),
                stringResource(R.string.settings_theme_dark)
            ),
            selected = when (settings.themeMode) { ThemeMode.SYSTEM -> 0; ThemeMode.LIGHT -> 1; ThemeMode.DARK -> 2 },
            onSelect = { idx ->
                vm.setThemeMode(when (idx) { 1 -> ThemeMode.LIGHT; 2 -> ThemeMode.DARK; else -> ThemeMode.SYSTEM })
                showThemeModeDialog = false
            },
            onDismiss = { showThemeModeDialog = false }
        )
    }
}

// ── Сітка кольорів ────────────────────────────────────────────────────────────

@Composable
internal fun ColorPalette(selected: Color, onSelect: (Color) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        ACCENT_COLORS.chunked(5).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                row.forEach { color ->
                    val isSelected = color.toArgb() == selected.toArgb()
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .clickable { onSelect(color) }
                    )
                }
            }
        }
    }
}

// ── Спільні компоненти ────────────────────────────────────────────────────────

@Composable
internal fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            title,
            style    = MaterialTheme.typography.titleLarge,
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
internal fun SettingsRow(
    icon:          ImageVector,
    title:         String,
    subtitle:      String?       = null,
    subtitleColor: Color         = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    showDivider:   Boolean       = true,
    onClick:       () -> Unit
) {
    ListItem(
        colors         = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier       = Modifier.clickable(onClick = onClick),
        leadingContent = { SettingsIcon(icon) },
        headlineContent = { Text(title, fontWeight = FontWeight.Normal) },
        supportingContent = subtitle?.let {
            { Text(it, style = MaterialTheme.typography.bodySmall, color = subtitleColor) }
        },
        trailingContent = {
            Icon(
                Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    )
    if (showDivider) ItemDivider()
}

@Composable
internal fun SettingsToggleRow(
    icon:               ImageVector,
    title:              String,
    subtitle:           String?   = null,
    subtitleClickable:  Boolean   = false,
    showDivider:        Boolean   = true,
    checked:            Boolean,
    onToggle:           (Boolean) -> Unit,
    onSubtitleClick:    () -> Unit = {}
) {
    ListItem(
        colors         = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier       = Modifier.clickable { onToggle(!checked) },
        leadingContent = { SettingsIcon(icon) },
        headlineContent = { Text(title, fontWeight = FontWeight.Normal) },
        supportingContent = subtitle?.let {
            {
                Text(
                    it,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.primary,
                    modifier = if (subtitleClickable) Modifier.clickable(onClick = onSubtitleClick) else Modifier
                )
            }
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onToggle)
        }
    )
    if (showDivider) ItemDivider()
}

@Composable
internal fun SettingsIcon(icon: ImageVector) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Група налаштувань як картка на dark surface (замість білих смуг-роздільників). */
@Composable
internal fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(content = content)
    }
}

@Composable
internal fun ItemDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(start = 72.dp),
        thickness = 0.5.dp,
        color     = MaterialTheme.colorScheme.outlineVariant
    )
}

// ── Діалог з radio-buttons ────────────────────────────────────────────────────

@Composable
internal fun RadioListDialog(
    title:    String,
    icon:     ImageVector,
    options:  List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(icon, null) },
        title = { Text(title) },
        text  = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(options.size) { idx ->
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(idx) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = idx == selected,
                            onClick  = { onSelect(idx) }
                        )
                        Text(
                            options[idx],
                            modifier   = Modifier.padding(start = 8.dp),
                            fontWeight = if (idx == selected) FontWeight.Bold else FontWeight.Normal,
                            color      = if (idx == selected) MaterialTheme.colorScheme.primary
                                         else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}

// ── Сторінка «Про додаток» ────────────────────────────────────────────────────

@Composable
internal fun AboutPageContent(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        SettingsTopBar(title = stringResource(R.string.settings_about), onBack = onBack)

        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            Image(
                painter            = painterResource(R.drawable.ic_launcher_full),
                contentDescription = null,
                modifier           = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(22.dp))
            )

            Spacer(Modifier.height(20.dp))

            Text(
                "Zerno",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(6.dp))

            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Spacer(Modifier.height(24.dp))

            Text(
                stringResource(R.string.settings_about_desc),
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Text(
            "© 2025 syalosovetskyi",
            modifier  = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            style     = MaterialTheme.typography.bodySmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ── Діалог вибору часу сповіщення ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickerDialog(
    hour:      Int,
    minute:    Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text(stringResource(R.string.settings_notif_time)) },
        text    = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
