package org.syalosovetskyi.onemoney.data.repository

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.syalosovetskyi.onemoney.data.db.dao.TxOverrideDao
import org.syalosovetskyi.onemoney.data.db.entities.TxOverrideEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.util.NetworkTimeouts
import org.syalosovetskyi.onemoney.workers.TxOverridePushWorker
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Правки операцій (ім'я, категорія, тип), які треба віддати на mono-flow.
 *
 * Синк тільки зливає дані з сервера (REPLACE за id) і нічого не відправляє назад,
 * тому без цієї черги перейменування операції жило б до наступного пробудження
 * воркера. Черга переживає офлайн: рядок лишається `synced = 0`, доки сервер не
 * підтвердить запис.
 */
@Singleton
class TxOverrideRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TxOverrideDao,
    private val settingsRepo: SettingsRepository
) {
    /**
     * Результат спроби віддати чергу.
     * `skipped` — сервер не налаштований (це НЕ успіх відправки, просто нічого не робимо);
     * `pendingLeft` — скільки правок ще чекають відправки і тому блокують тягнення даних.
     */
    data class PushResult(
        val sent: Int = 0,
        val failed: Int = 0,
        val skipped: Boolean = false,
        val networkError: Boolean = false,
        val pendingLeft: Int = 0
    )

    /**
     * Відмова, яка мине сама: невірний токен, ліміт запитів, збій сервера. Спроба не
     * рахується — інакше протермінований токен за п'ять пробуджень воркера «вбив» би
     * цілком правильну правку.
     */
    private class TransientServerException(message: String) : IOException(message)

    /**
     * Ставить правку в чергу. Операції, заведені в застосунку вручну, серверу невідомі —
     * їх id вміщається в мільярди, тоді як id з mono-flow це 60-бітний хеш. Такі правки
     * нікуди не їдуть, і це видно в лозі, а не мовчазний пропуск.
     */
    suspend fun enqueue(
        txId: Long,
        note: String,
        categoryName: String?,
        type: TransactionType,
        toAccountId: Long? = null
    ) {
        if (!org.syalosovetskyi.onemoney.BuildConfig.MONOFLOW_ENABLED) return // Play has no personal server queue.
        if (txId < SERVER_ID_THRESHOLD) {
            Log.i(TAG, "правка операції $txId не їде на сервер: операція локальна, сервер її не знає")
            return
        }
        dao.upsert(
            TxOverrideEntity(
                txId         = txId,
                note         = note,
                categoryName = categoryName,
                txType       = type.name,
                toAccountId  = toAccountId,
                updatedAt    = System.currentTimeMillis(),
                synced       = false
            )
        )
        TxOverridePushWorker.scheduleOneTime(context)
    }

    suspend fun pendingCount(): Int = dao.pendingCount(MAX_ATTEMPTS)

    /** Віддає всі непідтверджені правки. Мережеву помилку повідомляє, а не ковтає. */
    suspend fun pushPending(): PushResult = withContext(Dispatchers.IO) {
        if (!org.syalosovetskyi.onemoney.BuildConfig.MONOFLOW_ENABLED) return@withContext PushResult(skipped = true)
        val pending = dao.getPending(MAX_ATTEMPTS)
        if (pending.isEmpty()) {
            val dead = dao.deadCount(MAX_ATTEMPTS)
            if (dead > 0) {
                // Правки нікуди не поділися, але сервер їх не бере. Синк далі не блокуємо,
                // інакше одна зіпсована правка зупинила б синхронізацію назавжди.
                Log.e(TAG, "$dead правок сервер відхилив $MAX_ATTEMPTS разів поспіль; наступний синк перезапише ці операції серверною версією")
            }
            return@withContext PushResult()
        }

        val settings = settingsRepo.settings.first()
        val url   = settings.monoflowUrl.trimEnd('/')
        val token = settings.monoflowToken
        if (url.isBlank() || token.isBlank()) {
            Log.w(TAG, "${pending.size} правок чекають: mono-flow не налаштований (немає URL або токена)")
            return@withContext PushResult(skipped = true)
        }

        var sent = 0
        var failed = 0
        var networkError = false
        for (row in pending) {
            try {
                postOverride(url, token, row)
                dao.markSynced(row.txId, row.updatedAt)
                sent++
            } catch (e: IOException) {
                // мережа, таймаут або тимчасова відмова сервера — рядок лишається як є
                org.syalosovetskyi.onemoney.CrashReporter.warning(TAG, "правка операції ${row.txId} не доїхала: ${e.message}", e)
                failed++
                networkError = true
            } catch (e: Exception) {
                // відмова сервера — рахуємо спробу і зберігаємо причину в рядку
                org.syalosovetskyi.onemoney.CrashReporter.error(TAG, "правка операції ${row.txId} відхилена сервером: ${e.message}", e)
                dao.markRejected(row.txId, row.updatedAt, e.message?.take(300) ?: e.javaClass.simpleName)
                failed++
            }
        }
        val left = dao.pendingCount(MAX_ATTEMPTS)
        Log.i(TAG, "правки операцій: віддано $sent, не вдалося $failed, лишилось у черзі $left")
        PushResult(sent = sent, failed = failed, networkError = networkError, pendingLeft = left)
    }

    private fun postOverride(baseUrl: String, token: String, row: TxOverrideEntity) {
        val body = JSONObject().apply {
            put("id", row.txId)
            put("note", row.note)
            if (row.categoryName != null) put("category", row.categoryName)
            put("type", row.txType)
            // рахунок адресуємо ідентифікатором: його id похідний від внутрішнього
            // ключа і переживає перейменування рахунку людиною (на відміну від категорії)
            if (row.toAccountId != null) put("to_account_id", row.toAccountId)
        }.toString()

        val conn = (URL("$baseUrl$OVERRIDE_PATH").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            connectTimeout = NetworkTimeouts.CONNECT_MS
            readTimeout    = NetworkTimeouts.READ_SHORT_MS
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code != 200) {
                val detail = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val message = "HTTP $code from mono-flow: ${detail.take(200)}"
                // 401/403 — токен, 429 — ліміт, 5xx — сервер: усе це тимчасове.
                // Решта 4xx означає, що з правкою щось не так і повтори не допоможуть.
                if (code in TRANSIENT_CODES || code >= 500) throw TransientServerException(message)
                throw IllegalStateException(message)
            }
            conn.inputStream.use { it.readBytes() }   // дочитуємо, щоб з'єднання пішло в пул
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "TxOverrideRepo"
        private const val OVERRIDE_PATH = "/api/tx/override"

        /**
         * Нижня межа id, який міг прийти з mono-flow: там id — 60-бітний хеш
         * (`sha256(...)[:15]`), тож потрапити нижче за 10^12 він практично не може,
         * а автоінкремент Room стільки не набирає.
         */
        private const val SERVER_ID_THRESHOLD = 1_000_000_000_000L

        /** Коди, після яких повторити варто: токен, ліміт запитів. 5xx перевіряється окремо. */
        private val TRANSIENT_CODES = setOf(401, 403, 408, 429)

        /**
         * Скільки разів пробуємо віддати правку, яку сервер відхиляє. Після цього вона
         * перестає блокувати синк, але лишається в таблиці з причиною відмови.
         */
        const val MAX_ATTEMPTS = 5
    }
}
