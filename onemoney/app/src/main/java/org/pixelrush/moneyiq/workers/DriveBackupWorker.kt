package org.syalosovetskyi.onemoney.workers

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.syalosovetskyi.onemoney.data.db.dao.AccountDao
import org.syalosovetskyi.onemoney.data.db.dao.CategoryDao
import org.syalosovetskyi.onemoney.data.db.dao.TransactionDao
import org.syalosovetskyi.onemoney.data.repository.SettingsRepository
import org.syalosovetskyi.onemoney.util.BackupData
import org.syalosovetskyi.onemoney.util.BackupSerializer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ── Hilt Entry Point для доступу до сховища з Worker ─────────────────────────

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DriveBackupEntryPoint {
    fun accountDao(): AccountDao
    fun categoryDao(): CategoryDao
    fun transactionDao(): TransactionDao
    fun settingsRepository(): SettingsRepository
}

// ── Worker ────────────────────────────────────────────────────────────────────

class DriveBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val ep = EntryPointAccessors.fromApplication(
                applicationContext, DriveBackupEntryPoint::class.java
            )
            val settings = ep.settingsRepository().settings.first()

            // Якщо папка не вибрана — нічого не робимо
            val folderUriStr = settings.driveBackupFolderUri
            if (folderUriStr.isBlank()) return@withContext Result.success()

            val folderUri = Uri.parse(folderUriStr)

            // Збираємо дані
            val accounts     = ep.accountDao().getAllAccountsOnce()
            val categories   = ep.categoryDao().getAllCategoriesOnce()
            val transactions = ep.transactionDao().getAllTransactions()

            val backupData = BackupData(
                version      = BackupSerializer.BACKUP_VERSION,
                exportDate   = System.currentTimeMillis(),
                accounts     = accounts,
                categories   = categories,
                transactions = transactions
            )
            val json = BackupSerializer.serialize(backupData)

            val dateFmt = SimpleDateFormat("yyyy-MM-dd_HHmmss_SSS", Locale.ROOT)
            val fileName = "zerno_${dateFmt.format(Date())}.json"
            writeBackupDocument(applicationContext, folderUri, fileName, json)

            // Зберігаємо час останнього бекапу
            ep.settingsRepository().update {
                this[SettingsRepository.KEY_DRIVE_BACKUP_LAST_DATE] = System.currentTimeMillis()
            }

            // Видаляємо старі бекапи (залишаємо 30 останніх)
            pruneOldBackups(applicationContext, folderUri, keepCount = 30)

            Result.success()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.w("DriveBackupWorker", "Operation failed", e)

            Result.failure()
        }
    }

    // ── Допоміжні функції ─────────────────────────────────────────────────────

    companion object {
        const val WORK_NAME = "drive_daily_backup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(calcInitialDelay(), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /** Планує запуск о 2:00 ночі наступного дня */
        private fun calcInitialDelay(): Long {
            val now    = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 2)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DATE, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }

        // ── SAF-утиліти ───────────────────────────────────────────────────────

        fun createDocumentInFolder(context: Context, treeUri: Uri, fileName: String): Uri? {
            return try {
                val docId    = DocumentsContract.getTreeDocumentId(treeUri)
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                DocumentsContract.createDocument(
                    context.contentResolver, parentUri, "application/json", fileName
                )
            } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.w("DriveBackupWorker", "Operation failed", e)
            null
        }
        }

        fun listBackupFiles(context: Context, treeUri: Uri): List<DriveBackupEntry> {
            val result = mutableListOf<DriveBackupEntry>()
            return try {
                val docId       = DocumentsContract.getTreeDocumentId(treeUri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                val cursor = context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null, null, null
                )
                cursor?.use {
                    val idIdx   = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val modIdx  = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    val sizeIdx = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                    val mimeIdx = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while (it.moveToNext()) {
                        val mime = it.getString(mimeIdx) ?: ""
                        val name = it.getString(nameIdx) ?: ""
                        if (!name.endsWith(".tmp") && (mime == "application/json" || name.endsWith(".json"))) {
                            result += DriveBackupEntry(
                                documentId  = it.getString(idIdx),
                                name        = name,
                                modifiedMs  = it.getLong(modIdx),
                                sizeBytes   = it.getLong(sizeIdx)
                            )
                        }
                    }
                }
                result.sortedByDescending { it.modifiedMs }
            } catch (e: Exception) {
            android.util.Log.w("DriveBackupWorker", "Operation failed", e)

                result
            }
        }

        fun readBackupFile(context: Context, treeUri: Uri, documentId: String): String? {
            return try {
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                context.contentResolver.openInputStream(docUri)?.use {
                    it.bufferedReader(Charsets.UTF_8).readText()
                }
            } catch (e: Exception) {
            android.util.Log.w("DriveBackupWorker", "Operation failed", e)
            null
        }
        }

        /** Publish only after the stream is closed. Existing backups are never deleted first.
         * SAF atomic rename depends on the DocumentsProvider; unsupported rename fails visibly. */
        fun writeBackupDocument(context: Context, treeUri: Uri, fileName: String, json: String) {
            val staging = createDocumentInFolder(context, treeUri, "$fileName.${UUID.randomUUID()}.tmp")
                ?: throw java.io.IOException("Cannot create backup staging document")
            try {
                val output = context.contentResolver.openOutputStream(staging)
                    ?: throw java.io.IOException("Cannot open backup staging document")
                output.use { it.write(json.toByteArray(Charsets.UTF_8)); it.flush() }
                DocumentsContract.renameDocument(context.contentResolver, staging, fileName)
                    ?: throw java.io.IOException("DocumentsProvider does not support backup rename")
            } catch (failure: Exception) {
                android.util.Log.w("DriveBackupWorker", "Backup publication failed", failure)
                try {
                    if (!DocumentsContract.deleteDocument(context.contentResolver, staging)) {
                        android.util.Log.w("DriveBackupWorker", "Could not remove staging document")
                    }
                } catch (cleanup: Exception) {
                    android.util.Log.w("DriveBackupWorker", "Staging cleanup failed", cleanup)
                }
                throw failure
            }
        }

        fun deleteFile(context: Context, treeUri: Uri, documentId: String) {
            try {
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                DocumentsContract.deleteDocument(context.contentResolver, docUri)
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                android.util.Log.w("DriveBackupWorker", "Operation failed", failure)
}
        }

        private fun pruneOldBackups(context: Context, treeUri: Uri, keepCount: Int) {
            val all = listBackupFiles(context, treeUri) // sorted newest first
            if (all.size <= keepCount) return
            all.drop(keepCount).forEach { entry ->
                deleteFile(context, treeUri, entry.documentId)
            }
        }

        fun getFolderDisplayName(context: Context, treeUri: Uri): String? {
            return try {
                val docId  = DocumentsContract.getTreeDocumentId(treeUri)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                context.contentResolver.query(
                    docUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("DriveBackupWorker", "Operation failed", e)
                null
            }
        }
    }
}

// ── Модель елемента Drive-бекапу ──────────────────────────────────────────────

data class DriveBackupEntry(
    val documentId: String,
    val name: String,
    val modifiedMs: Long,
    val sizeBytes: Long
)
