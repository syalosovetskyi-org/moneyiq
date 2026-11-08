package org.syalosovetskyi.onemoney.alisaphone

import android.os.Bundle
import java.time.*

internal class ApiError(val code: String, val details: String = code) : IllegalArgumentException(details)

internal data class ApiRequest(val cmd: String, val params: Bundle) {
    fun text(key: String): String? {
        @Suppress("DEPRECATION") val value = params.get(key) ?: return null
        if (value !is String || value.isBlank() || value.length > 4096) throw ApiError("invalid_params", "$key must be a non-empty string (max 4096)")
        return value.trim()
    }
    fun amount(): Double {
        @Suppress("DEPRECATION") val value = params.get("amount")
        if (value !is Double || !value.isFinite() || value <= 0) throw ApiError("invalid_params", "amount must be a finite positive Double")
        return value
    }
    fun limit(): Int {
        @Suppress("DEPRECATION") val value = params.get("limit") ?: return 10
        if (value !is Int || value !in 1..50) throw ApiError("invalid_params", "limit must be an Int in 1..50")
        return value
    }
    fun date(): Long = text("date")?.let { value ->
        try {
            if (value.length == 10) LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            else OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (e: DateTimeException) { throw ApiError("invalid_params", "date must be ISO date or timestamp with offset") }
    } ?: System.currentTimeMillis()
    fun month(): YearMonth = text("month")?.let {
        if (!it.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])"))) throw ApiError("invalid_params", "month must be YYYY-MM")
        try { YearMonth.parse(it) } catch (e: DateTimeException) { throw ApiError("invalid_params", "month must be YYYY-MM") }
    } ?: YearMonth.now()

    companion object {
        fun parse(bundle: Bundle): ApiRequest {
            val request = ApiRequest("", bundle)
            val cmd = request.text("cmd") ?: throw ApiError("invalid_params", "cmd is required")
            if (cmd !in setOf("add_transaction", "balance", "recent", "month_summary", "open", "undo_last")) throw ApiError("unknown_command", cmd)
            val allowed = when (cmd) {
                "add_transaction" -> setOf("type", "amount", "currency", "category", "account", "note", "date")
                "balance" -> setOf("account")
                "recent" -> setOf("limit")
                "month_summary" -> setOf("month")
                "open" -> setOf("screen")
                else -> emptySet()
            } + "cmd"
            val unknown = bundle.keySet() - allowed
            if (unknown.isNotEmpty()) throw ApiError("invalid_params", "Unknown parameters: " + unknown.joinToString())
            return request.copy(cmd = cmd)
        }
    }
}
