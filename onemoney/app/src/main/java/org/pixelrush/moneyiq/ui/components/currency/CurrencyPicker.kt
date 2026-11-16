package org.syalosovetskyi.onemoney.ui.components.currency

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.ui.accounts.currencyDisplayName
import org.syalosovetskyi.onemoney.ui.settings.data.CURRENCIES_CRYPTO
import org.syalosovetskyi.onemoney.ui.settings.data.CURRENCIES_MAIN
import org.syalosovetskyi.onemoney.ui.settings.data.CURRENCIES_OTHER
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme

// ── CurrencyPickerSheet ───────────────────────────────────────────────────────
// Full-screen Dialog with tabs: Main / Other / Crypto currencies.
// Used when opening a currency picker as an overlay (e.g. from a bottom sheet).

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CurrencyPickerSheet(
    selected:  String,
    onSelect:  (String) -> Unit,
    onDismiss: () -> Unit,
    title:     String? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress      = true,
            dismissOnClickOutside   = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {

                Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, stringResource(R.string.acc_close))
                        }
                        Text(
                            title ?: stringResource(R.string.acc_field_currency),
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier   = Modifier.padding(start = Spacing.xs)
                        )
                    }
                }

                var tab by remember { mutableIntStateOf(0) }
                val tabLists   = listOf(CURRENCIES_MAIN, CURRENCIES_OTHER, CURRENCIES_CRYPTO)
                val tabLabels  = listOf(
                    stringResource(R.string.curr_tab_main),
                    stringResource(R.string.curr_tab_other),
                    stringResource(R.string.curr_tab_crypto)
                )
                val tabIcons   = listOf<ImageVector>(
                    Icons.Outlined.MonetizationOn,
                    Icons.Outlined.CurrencyExchange,
                    Icons.Outlined.Memory
                )

                TabRow(
                    selectedTabIndex = tab,
                    containerColor   = MaterialTheme.colorScheme.surface,
                    contentColor     = MaterialTheme.colorScheme.primary
                ) {
                    tabLabels.forEachIndexed { i, label ->
                        Tab(
                            selected = tab == i,
                            onClick  = { tab = i },
                            icon     = { Icon(tabIcons[i], null, modifier = Modifier.size(20.dp)) },
                            text     = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                LazyColumn(Modifier.fillMaxSize()) {
                    items(tabLists[tab]) { cur ->
                        val isSelected = cur.code == selected
                        ListItem(
                            modifier        = Modifier.clickable { onSelect(cur.code) },
                            leadingContent  = {
                                RadioButton(selected = isSelected, onClick = { onSelect(cur.code) })
                            },
                            headlineContent = {
                                Text(
                                    currencyDisplayName(cur.code),
                                    color      = if (isSelected) MaterialTheme.colorScheme.primary
                                                 else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            trailingContent = {
                                Text(
                                    cur.symbol,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        )
                        HorizontalDivider(
                            modifier  = Modifier.padding(start = 56.dp),
                            thickness = 0.5.dp,
                            color     = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}

// ── CurrencyBottomSheet ───────────────────────────────────────────────────────
// ModalBottomSheet variant — use from regular screens (not from inside bottom sheets).
// Avoids Dialog window timing issues that prevent CurrencyPickerSheet from appearing.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CurrencyBottomSheet(
    selected:  String,
    onSelect:  (String) -> Unit,
    onDismiss: () -> Unit,
    title:     String? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier         = Modifier.fillMaxHeight(0.92f)
    ) {
        var tab by remember { mutableIntStateOf(0) }
        val tabLists  = listOf(CURRENCIES_MAIN, CURRENCIES_OTHER, CURRENCIES_CRYPTO)
        val tabIcons  = listOf<ImageVector>(
            Icons.Outlined.MonetizationOn,
            Icons.Outlined.CurrencyExchange,
            Icons.Outlined.Memory
        )
        val tabLabels = listOf(
            stringResource(R.string.curr_tab_main_short),
            stringResource(R.string.curr_tab_other_short),
            stringResource(R.string.curr_tab_crypto_short)
        )

        Text(
            text     = title ?: stringResource(R.string.curr_title),
            style    = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
        )

        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
            tabLabels.forEachIndexed { i, label ->
                Tab(
                    selected = tab == i,
                    onClick  = { tab = i },
                    icon     = { Icon(tabIcons[i], null, modifier = Modifier.size(20.dp)) },
                    text     = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(tabLists[tab]) { cur ->
                val isSelected = cur.code == selected
                ListItem(
                    modifier        = Modifier.clickable { onSelect(cur.code) },
                    leadingContent  = {
                        RadioButton(selected = isSelected, onClick = { onSelect(cur.code) })
                    },
                    headlineContent = {
                        Text(
                            currencyDisplayName(cur.code),
                            color      = if (isSelected) MaterialTheme.colorScheme.primary
                                         else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    trailingContent = {
                        Text(cur.symbol, style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                )
                HorizontalDivider(Modifier.padding(start = 56.dp), 0.5.dp,
                    MaterialTheme.colorScheme.outlineVariant)
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ── CurrencyPageContent ───────────────────────────────────────────────────────
// Bare column (fills parent) — caller wraps in Dialog or uses as a page.
// Used in SettingsScreen as a navigation page, and in other sheets via Dialog.

@Composable
internal fun CurrencyPageContent(
    selected: String,
    onSelect: (String) -> Unit,
    onClose:  () -> Unit,
    title:    String? = null
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.curr_tab_main),
        stringResource(R.string.curr_tab_other),
        stringResource(R.string.curr_tab_crypto)
    )
    val currencies = when (tabIndex) {
        0    -> CURRENCIES_MAIN
        1    -> CURRENCIES_OTHER
        else -> CURRENCIES_CRYPTO
    }

    // Callers show this in a bare Dialog or as a page outside any Scaffold, so the
    // Surface is what sets LocalContentColor (title and «×» were black in dark theme)
    // and gives the list an opaque background (#3037 R7).
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.acc_close))
                }
                Text(title ?: stringResource(R.string.settings_default_currency), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = Spacing.xs))
            }

            TabRow(selectedTabIndex = tabIndex) {
                tabs.forEachIndexed { i, label ->
                    Tab(
                        selected = tabIndex == i,
                        onClick  = { tabIndex = i },
                        text     = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            LazyColumn {
                items(currencies) { currency ->
                    val isSelected = currency.code == selected
                    ListItem(
                        modifier        = Modifier.clickable { onSelect(currency.code) },
                        leadingContent  = {
                            RadioButton(selected = isSelected, onClick = { onSelect(currency.code) })
                        },
                        headlineContent = {
                            Text(
                                currencyDisplayName(currency.code),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color      = if (isSelected) MaterialTheme.colorScheme.primary
                                             else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        trailingContent = {
                            Text(
                                currency.symbol,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    HorizontalDivider(
                        modifier  = Modifier.padding(start = 72.dp),
                        thickness = 0.5.dp,
                        color     = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}
