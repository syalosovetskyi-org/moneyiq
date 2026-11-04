package org.syalosovetskyi.onemoney.workers

import android.content.Context
import android.util.Log
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
import org.syalosovetskyi.onemoney.data.repository.TxOverrideRepository
import org.syalosovetskyi.onemoney.util.BackupSerializer
import org.syalosovetskyi.onemoney.util.NetworkTimeouts
import org.syalosovetskyi.onemoney.util.normalizeImportedCategory
import java.util.concurrent.TimeUnit

// ── Hilt Entry Point ──────────────────────────────────────────────────────────

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MonoFlowSyncEntryPoint {
    fun accountDao(): AccountDao
    fun categoryDao(): CategoryDao
    fun transactionDao(): TransactionDao
    fun settingsRepository(): SettingsRepository
    fun txOverrideRepository(): TxOverrideRepository
}

// ── Worker ────────────────────────────────────────────────────────────────────

class MonoFlowSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val ep = EntryPointAccessors.fromApplication(
                applicationContext, MonoFlowSyncEntryPoint::class.java
            )
            val settings = ep.settingsRepository().settings.first()

            // Якщо не налаштовано — нічого не робимо
            val url   = settings.monoflowUrl.trimEnd('/')
            val token = settings.monoflowToken
            if (url.isBlank() || token.isBlank()) return@withContext Result.success()

            val since = settings.monoflowLastSyncMs

            // СПЕРШУ віддаємо свої правки, і лише потім тягнемо: тягнення перезаписує
            // операції за id, тож невіддана правка була б тут мовчки затерта.
            val pushed = ep.txOverrideRepository().pushPending()
            if (pushed.networkError || pushed.pendingLeft > 0) {
                // Тягнути зараз не можна: REPLACE за id перезапише операцію серверною
                // версією і невіддана правка зникне без сліду. Відкладаємо весь синк.
                Log.w(TAG, "синк відкладено: ${pushed.pendingLeft} правок ще не віддано серверу")
                return@withContext Result.retry()
            }

            // Отримуємо JSON з сервера
            val json = fetchJson(url, token, since)

            // Парсимо
            val data = BackupSerializer.deserialize(json)

            // MERGE: insert/replace по id, не видаляємо існуючі дані
            ep.accountDao().insertAccounts(data.accounts)
            val normalizedCats = data.categories.map { normalizeImportedCategory(it) }
            ep.categoryDao().insertCategories(normalizedCats)
            ep.transactionDao().insertTransactions(data.transactions)
            Log.i(
                TAG,
                "синк: ${data.accounts.size} рахунків, ${data.categories.size} категорій, " +
                    "${data.transactions.size} операцій"
            )

            // Оновлюємо час останньої синхронізації
            ep.settingsRepository().update {
                this[SettingsRepository.KEY_MONOFLOW_LAST_SYNC] = System.currentTimeMillis()
            }

            Result.success()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Retry при мережевих помилках, failure при parse-помилках.
            // Мовчазного провалу тут бути не може: невдалий синк ззовні виглядає
            // точно як «нових даних немає», і причина має лишитися в лозі.
            if (e is java.net.SocketTimeoutException ||
                e is java.net.ConnectException ||
                e is java.io.IOException) {
                Log.w(TAG, "синк не вдався (мережа), спробуємо ще раз: ${e.message}")
                Result.retry()
            } else {
                Log.e(TAG, "синк провалено: ${e.javaClass.simpleName}: ${e.message}", e)
                Result.failure()
            }
        }
    }

    // ── HTTP (без зовнішніх залежностей) ─────────────────────────────────────

    private fun fetchJson(baseUrl: String, token: String, sinceMs: Long): String {
        val conn = java.net.URL("$baseUrl/api/sync?since=$sinceMs")
            .openConnection() as java.net.HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = NetworkTimeouts.CONNECT_MS
        conn.readTimeout    = NetworkTimeouts.READ_LONG_MS
        val code = conn.responseCode
        if (code != 200) {
            throw Exception("HTTP $code from MonoFlow")
        }
        return conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "MonoFlowSync"
        const val WORK_NAME = "monoflow_auto_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonoFlowSyncWorker>(2, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun scheduleOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<MonoFlowSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
