package org.syalosovetskyi.onemoney.ui.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.workers.DriveBackupEntry
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme
import org.syalosovetskyi.onemoney.ui.theme.NegativeAmountColor
import java.text.SimpleDateFormat
import java.util.*


// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    onNavigateBack: () -> Unit,
    viewModel: DataViewModel = hiltViewModel()
) {
    BackHandler(onBack = onNavigateBack)

    val context  = LocalContext.current
    val state    by viewModel.state.collectAsState()
    val scope    = rememberCoroutineScope()

    // Діалоги
    var showResetDialog   by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf<Uri?>(null) }
    var showRestoreFromDrive by remember { mutableStateOf<DriveBackupEntry?>(null) }
    // JSON рядок для запису після відкриття CreateDocument
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    var pendingCsvIntent  by remember { mutableStateOf<Intent?>(null) }

    LaunchedEffect(Unit) { viewModel.loadState(context) }

    // Повідомлення
    state.message?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }

    // ── Лаунчери ──────────────────────────────────────────────────────────────

    // Вибір папки Google Drive
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.configureDriveFolder(context, it) }
    }

    // Збереження JSON-бекапу
    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { dst ->
            pendingExportJson?.let { json ->
                viewModel.writeExportToUri(context, dst, json)
                pendingExportJson = null
            }
        }
    }

    // Відкриття JSON-файлу для імпорту
    val importJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { showImportConfirm = it }
    }

    // Реакція на підготовлений CSV
    LaunchedEffect(pendingCsvIntent) {
        pendingCsvIntent?.let { intent ->
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.data_share_csv)))
            pendingCsvIntent = null
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_data)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {

            // ── Скинути дані ──────────────────────────────────────────────────
            item {
                ListItem(
                    modifier = Modifier.clickable { showResetDialog = true },
                    leadingContent = {
                        Icon(Icons.Default.RestartAlt, null,
                            tint = MaterialTheme.colorScheme.error)
                    },
                    headlineContent = {
                        Text(stringResource(R.string.data_reset), color = MaterialTheme.colorScheme.error)
                    }
                )
                HorizontalDivider()
            }

            // ── Імпорт / Експорт ──────────────────────────────────────────────
            item { DataSectionHeader(stringResource(R.string.data_section_import_export)) }

            item {
                DataActionItem(
                    icon  = Icons.Default.FileUpload,
                    title = stringResource(R.string.data_export_json),
                    sub   = stringResource(R.string.data_export_json_sub),
                    loading = state.isExporting,
                    onClick = {
                        if (!state.isExporting) {
                            scope.launch {
                                val json = viewModel.buildExportJson()
                                pendingExportJson = json
                                val fmt  = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
                                exportJsonLauncher.launch("onemoney_${fmt.format(Date())}.json")
                            }
                        }
                    }
                )
            }
            item {
                DataActionItem(
                    icon  = Icons.Default.FileDownload,
                    title = stringResource(R.string.data_import_json),
                    sub   = stringResource(R.string.data_import_json_sub),
                    loading = state.isImporting,
                    onClick = {
                        if (!state.isImporting) {
                            importJsonLauncher.launch(arrayOf("application/json", "*/*"))
                        }
                    }
                )
            }
            item {
                DataActionItem(
                    icon    = Icons.Default.TableChart,
                    title   = stringResource(R.string.data_export_csv),
                    sub     = stringResource(R.string.data_export_csv_sub),
                    onClick = {
                        scope.launch {
                            val intent = viewModel.buildCsvShareIntentSuspend(context)
                            if (intent != null) pendingCsvIntent = intent
                            else Toast.makeText(context, context.getString(R.string.data_no_operations_export), Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                HorizontalDivider()
            }

            // ── Google Drive автобекап ─────────────────────────────────────────
            item { DataSectionHeader(stringResource(R.string.data_section_drive)) }

            if (state.driveFolderUri.isBlank()) {
                // Папка не вибрана
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.data_drive_promo_title),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.data_drive_promo_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { folderLauncher.launch(null) },
                                shape = RoundedCornerShape(OneMoneyTheme.dimens.cardRadius)
                            ) {
                                Icon(Icons.Default.FolderOpen, null,
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.data_drive_pick_folder))
                            }
                        }
                    }
                }
            } else {
                // Папка вибрана — показуємо статус
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudDone, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    state.driveFolderName.ifBlank { "Google Drive" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.clearDriveFolder(context) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, stringResource(R.string.data_disconnect),
                                        modifier = Modifier.size(18.dp))
                                }
                            }
                            if (state.driveLastBackupMs > 0L) {
                                Spacer(Modifier.height(4.dp))
                                val fmt = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()) }
                                Text(
                                    stringResource(R.string.data_last_backup, fmt.format(Date(state.driveLastBackupMs))),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                item {
                    ListItem(
                        leadingContent = {
                            Icon(Icons.Default.Autorenew, null,
                                tint = MaterialTheme.colorScheme.onSurface)
                        },
                        headlineContent = { Text(stringResource(R.string.data_drive_daily)) },
                        supportingContent = { Text(stringResource(R.string.data_drive_daily_sub)) },
                        trailingContent = {
                            Switch(
                                checked = state.driveBackupEnabled,
                                onCheckedChange = { viewModel.setDriveAutoBackup(context, it) }
                            )
                        }
                    )
                }
                item {
                    ListItem(
                        modifier = Modifier.clickable(enabled = !state.isBacking) {
                            viewModel.backupToDriveNow(context)
                        },
                        leadingContent = {
                            if (state.isBacking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Backup, null,
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        headlineContent = {
                            Text(stringResource(R.string.data_backup_now),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium)
                        }
                    )
                }
                item {
                    ListItem(
                        modifier = Modifier.clickable { folderLauncher.launch(null) },
                        leadingContent = {
                            Icon(Icons.Default.FolderOpen, null,
                                tint = MaterialTheme.colorScheme.onSurface)
                        },
                        headlineContent = { Text(stringResource(R.string.data_change_folder)) }
                    )
                    HorizontalDivider()
                }

                // Список Drive-бекапів
                if (state.driveBackups.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.data_drive_backups_count, state.driveBackups.size),
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(state.driveBackups) { entry ->
                        DriveBackupItem(
                            entry = entry,
                            onRestore = { showRestoreFromDrive = entry }
                        )
                    }
                    item { HorizontalDivider() }
                }
            }

            // ── MonoFlow синхронізація ─────────────────────────────────────────
            if (org.syalosovetskyi.onemoney.BuildConfig.MONOFLOW_ENABLED) {
            item { DataSectionHeader(stringResource(R.string.data_section_monoflow)) }
            item {
                MonoFlowSyncCard(
                    state       = state,
                    context     = context,
                    viewModel   = viewModel
                )
                HorizontalDivider()
            }

            }

            // ── Локальний бекап ────────────────────────────────────────────────
            item { DataSectionHeader(stringResource(R.string.data_local_backup)) }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFEBEE))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Outlined.Warning, null,
                        tint = NegativeAmountColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.data_local_warning),
                        color = NegativeAmountColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            item {
                ListItem(
                    modifier = Modifier.clickable { viewModel.createLocalBackup(context) },
                    leadingContent = {
                        Icon(Icons.Default.AddCircleOutline, null,
                            tint = MaterialTheme.colorScheme.primary)
                    },
                    headlineContent = {
                        Text(stringResource(R.string.data_create_backup),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium)
                    }
                )
            }
            items(state.localBackups) { backup ->
                LocalBackupItem(backup = backup)
            }
        }
    }

    // ── Діалоги ───────────────────────────────────────────────────────────────

    if (showResetDialog) {
        ResetDataDialog(
            onDeleteAll = {
                showResetDialog = false
                viewModel.deleteAllData()
            },
            onDeleteTransactions = {
                showResetDialog = false
                viewModel.deleteAllTransactions(context)
            },
            onDismiss = { showResetDialog = false }
        )
    }

    showImportConfirm?.let { uri ->
        AlertDialog(
            onDismissRequest = { showImportConfirm = null },
            icon = { Icon(Icons.Default.Warning, null,
                tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.data_replace_title)) },
            text = {
                Text(stringResource(R.string.data_replace_msg))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.importFromUri(context, uri)
                        showImportConfirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.data_replace)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    showRestoreFromDrive?.let { entry ->
        AlertDialog(
            onDismissRequest = { showRestoreFromDrive = null },
            icon = { Icon(Icons.Default.CloudDownload, null,
                tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.data_restore_drive_title)) },
            text = {
                Text(stringResource(R.string.data_restore_drive_msg, entry.name))
            },
            confirmButton = {
                val uri = state.driveFolderUri
                TextButton(onClick = {
                    if (uri.isNotBlank()) {
                        viewModel.restoreFromDrive(context, Uri.parse(uri), entry)
                    }
                    showRestoreFromDrive = null
                }) { Text(stringResource(R.string.data_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreFromDrive = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    // Прогрес-індикатор під час імпорту
    if (state.isImporting) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Card(shape = RoundedCornerShape(OneMoneyTheme.dimens.largeRadius)) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.data_importing))
                }
            }
        }
    }
}
