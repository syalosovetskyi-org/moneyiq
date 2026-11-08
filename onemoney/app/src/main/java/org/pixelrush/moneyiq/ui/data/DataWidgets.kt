package org.syalosovetskyi.onemoney.ui.data

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.workers.DriveBackupEntry
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme
import java.text.SimpleDateFormat
import java.util.*
// ── MonoFlow Sync Card ────────────────────────────────────────────────────────

@Composable
internal fun MonoFlowSyncCard(
    state: DataUiState,
    context: android.content.Context,
    viewModel: DataViewModel
) {
    val scope = rememberCoroutineScope()
    val fmt   = remember { java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault()) }

    // Локальні поля введення
    var editUrl   by remember(state.monoflowUrl)   { mutableStateOf(state.monoflowUrl)   }
    var editToken by remember(state.monoflowToken) { mutableStateOf(state.monoflowToken) }
    var showToken by remember { mutableStateOf(false) }

    val isConfigured = state.monoflowUrl.isNotBlank() && state.monoflowToken.isNotBlank()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConfigured)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {

            // Заголовок
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = null,
                    tint = if (isConfigured) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isConfigured) stringResource(R.string.data_monoflow_connected) else stringResource(R.string.data_monoflow_not_configured),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (isConfigured) {
                    IconButton(
                        onClick = { viewModel.clearMonoFlowConfig(context) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, stringResource(R.string.data_disconnect),
                            modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // URL
            OutlinedTextField(
                value = editUrl,
                onValueChange = { editUrl = it },
                label = { Text(stringResource(R.string.data_monoflow_url_label)) },
                placeholder = { Text("https://monoflow.ibotz.fun/api/sync") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSyncing
            )

            Spacer(Modifier.height(8.dp))

            // Токен
            OutlinedTextField(
                value = editToken,
                onValueChange = { editToken = it },
                label = { Text(stringResource(R.string.data_token)) },
                placeholder = { Text("miq_xxxxxxxxxxxxxxxx") },
                singleLine = true,
                visualTransformation = if (showToken)
                    androidx.compose.ui.text.input.VisualTransformation.None
                else
                    androidx.compose.ui.text.input.PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                            if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSyncing
            )

            Spacer(Modifier.height(12.dp))

            // Кнопка Зберегти/Оновити
            Button(
                onClick = {
                    if (editUrl.isNotBlank() && editToken.isNotBlank()) {
                        viewModel.saveMonoFlowConfig(context, editUrl, editToken)
                    }
                },
                enabled = editUrl.isNotBlank() && editToken.isNotBlank() && !state.isSyncing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(OneMoneyTheme.dimens.keyRadius)
            ) {
                if (state.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.data_syncing))
                } else {
                    Icon(Icons.Default.Sync, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isConfigured) stringResource(R.string.data_sync_now) else stringResource(R.string.data_connect_sync))
                }
            }

            // Статус і автосинк — тільки якщо налаштовано
            if (isConfigured) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))

                // Автосинк
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.data_autosync),
                            style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.data_autosync_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = state.monoflowAutoSync,
                        onCheckedChange = { viewModel.setMonoFlowAutoSync(context, it) }
                    )
                }

                // Час останнього синку
                if (state.monoflowLastSyncMs > 0L) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.data_last_sync, fmt.format(java.util.Date(state.monoflowLastSyncMs))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Приватні composable ───────────────────────────────────────────────────────

@Composable
internal fun DataSectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
internal fun DataActionItem(
    icon: ImageVector,
    title: String,
    sub: String? = null,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(enabled = !loading, onClick = onClick),
        leadingContent = {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            else Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface)
        },
        headlineContent  = { Text(title) },
        supportingContent = sub?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } }
    )
}

@Composable
internal fun DriveBackupItem(
    entry: DriveBackupEntry,
    onRestore: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()) }
    val size = when {
        entry.sizeBytes < 1024        -> "${entry.sizeBytes} ${stringResource(R.string.unit_b)}"
        entry.sizeBytes < 1024 * 1024 -> "${entry.sizeBytes / 1024} ${stringResource(R.string.unit_kb)}"
        else -> "${"%.1f".format(entry.sizeBytes / (1024.0 * 1024))} ${stringResource(R.string.unit_mb)}"
    }
    ListItem(
        leadingContent = {
            Icon(Icons.Default.CloudQueue, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp))
        },
        headlineContent  = { Text(entry.name, style = MaterialTheme.typography.bodyMedium) },
        supportingContent = {
            Text("${fmt.format(Date(entry.modifiedMs))}  ·  $size",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            TextButton(onClick = onRestore) { Text(stringResource(R.string.data_restore)) }
        }
    )
}

@Composable
internal fun LocalBackupItem(backup: BackupEntry) {
    val fmt    = remember { SimpleDateFormat("d MMMM yyyy, H:mm", Locale.getDefault()) }
    val txLbl  = pluralStringResource(R.plurals.cat_operations_count, backup.txCount, backup.txCount)
    val accLbl = pluralStringResource(R.plurals.data_plural_accounts, backup.accountCount, backup.accountCount)
    val catLbl = pluralStringResource(R.plurals.data_plural_categories, backup.categoryCount, backup.categoryCount)

    ListItem(
        leadingContent = {
            Icon(Icons.Default.History, null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        },
        headlineContent  = { Text(stringResource(R.string.data_local_backup), fontWeight = FontWeight.Medium) },
        supportingContent = {
            Column {
                Text(fmt.format(Date(backup.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                Text("$txLbl · $accLbl · $catLbl",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            }
        }
    )
}

@Composable
internal fun ResetDataDialog(
    onDeleteAll: () -> Unit,
    onDeleteTransactions: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 28.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Delete, null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.data_reset),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.data_reset_explain),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onDeleteAll,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor   = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) { Text(stringResource(R.string.data_delete_all), fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDeleteTransactions) {
                    Text(stringResource(R.string.data_delete_all_tx))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.data_dismiss)) }
            }
        }
    }
}
