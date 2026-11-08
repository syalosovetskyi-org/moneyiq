package org.syalosovetskyi.onemoney.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.syalosovetskyi.onemoney.util.ApiEndpoints
import org.syalosovetskyi.onemoney.util.NetworkTimeouts
import javax.inject.Inject
import javax.inject.Singleton

private val Context.ratesDataStore by preferencesDataStore(name = "currency_rates")

/**
 * Курси валют НБУ для приведення балансів різних валют до однієї (гривні).
 *
 * Джерело — відкритий API НБУ (без ключа): повертає курс `rate` = скільки гривень
 * коштує 1 одиниця валюти `cc`. Останній вдалий знімок кешується в DataStore, тож
 * після першого завантаження конвертація працює й офлайн. Гривня завжди = 1.0.
 *
 * Валюти, яких немає у відповіді НБУ (напр. крипто), у мапі відсутні — виклик
 * показує частковий підсумок із явним переліком відсутніх курсів.
 */
@Singleton
class CurrencyRatesRepository internal constructor(
    private val ds: androidx.datastore.core.DataStore<Preferences>,
    private val fetch: (() -> String)? = null
) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.ratesDataStore)

    companion object {
        private const val BASE = "UAH"
        private const val STALE_AFTER_MS = 12L * 60 * 60 * 1000  // 12 годин

        private val KEY_RATES_JSON    = stringPreferencesKey("rates_json")
        private val KEY_RATES_UPDATED  = longPreferencesKey("rates_updated")
    }

    /** Мапа код-валюти → курс до гривні. Завжди містить UAH = 1.0. */
    val rates: Flow<Map<String, Double>> = ds.data.map { p ->
        buildMap {
            put(BASE, 1.0)
            p[KEY_RATES_JSON]?.takeIf { it.isNotBlank() }?.let { json ->
                runCatching {
                    val o = JSONObject(json)
                    o.keys().forEach { k -> put(k, o.getDouble(k)) }
                }.onFailure { org.syalosovetskyi.onemoney.CrashReporter.warning("CurrencyRatesRepository", "Invalid cached rates", it) }
            }
        }
    }

    /** Час останнього вдалого оновлення (мс), 0 — ще жодного разу. */
    val lastUpdated: Flow<Long> = ds.data.map { it[KEY_RATES_UPDATED] ?: 0L }

    /** Тягне свіжі курси з НБУ й кешує їх. Кидає виняток при мережевій помилці. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        val parsed = parseRates(fetch?.invoke() ?: fetchRatesJson())
        ds.edit { p ->
            p[KEY_RATES_JSON]    = parsed
            p[KEY_RATES_UPDATED] = System.currentTimeMillis()
        }
    }

    /** Оновлює курси, якщо кеш старіший за [maxAgeMs]. Мережеві помилки логуються; кеш зберігається. */
    suspend fun refreshIfStale(maxAgeMs: Long = STALE_AFTER_MS) {
        val updated = lastUpdated.first()
        if (System.currentTimeMillis() - updated > maxAgeMs) {
            runCatching { refresh() }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                org.syalosovetskyi.onemoney.CrashReporter.warning("CurrencyRatesRepository", "Rate refresh failed; retaining cache", it)
            }
        }
    }

    // ── HTTP (без зовнішніх залежностей, як у MonoFlowSyncWorker) ────────────────

    private fun fetchRatesJson(): String {
        val conn = java.net.URL(ApiEndpoints.NBU_RATES)
            .openConnection() as java.net.HttpURLConnection
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = NetworkTimeouts.CONNECT_MS
        conn.readTimeout    = NetworkTimeouts.READ_SHORT_MS
        val code = conn.responseCode
        if (code != 200) throw java.io.IOException("HTTP $code from NBU")
        return conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
    }

    /** Перетворює масив НБУ на компактний JSON-об'єкт {cc: rate}. */
    private fun parseRates(raw: String): String {
        val arr = JSONArray(raw)
        val out = JSONObject()
        for (i in 0 until arr.length()) {
            val o    = arr.getJSONObject(i)
            val cc   = o.optString("cc")
            val rate = o.optDouble("rate", 0.0)
            if (cc.isNotBlank() && rate > 0.0) out.put(cc, rate)
        }
        return out.toString()
    }
}
