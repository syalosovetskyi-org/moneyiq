package org.syalosovetskyi.onemoney.alisaphone

import android.content.Context
import android.content.Intent
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.syalosovetskyi.onemoney.MainActivity
import org.syalosovetskyi.onemoney.data.db.AppDatabase
import org.syalosovetskyi.onemoney.data.db.entities.*
import org.syalosovetskyi.onemoney.data.repository.*
import org.syalosovetskyi.onemoney.util.*
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Serialized API commands; DB writes use the same repository as quick entry inside one Room transaction. */
@Singleton
internal class AlisaphoneCommands @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val db: AppDatabase, private val transactions: TransactionRepository,
    private val rates: CurrencyRatesRepository
) {
    private val mutex = Mutex()
    private val ownership = context.getSharedPreferences("alisaphone_owned_transaction", Context.MODE_PRIVATE)

    suspend fun execute(request: ApiRequest): JSONObject = mutex.withLock {
        when (request.cmd) {
            "add_transaction" -> add(request)
            "undo_last" -> undo()
            "balance" -> balance(request)
            "recent" -> JSONObject().put("transactions", JSONArray(transactions.getRecentTransactions(request.limit()).first().map { row ->
                JSONObject().put("id", row.id).put("type", row.type.name.lowercase()).put("amount", row.amount)
                    .put("currency", row.accountCurrency).put("account", row.accountName).put("category", row.categoryName ?: JSONObject.NULL)
                    .put("note", row.note).put("date", java.time.Instant.ofEpochMilli(row.date).toString())
            }))
            "month_summary" -> summary(request)
            "open" -> {
                val screen = request.text("screen")
                if (screen !in setOf("accounts", "categories", "operations", "budget", "overview")) throw ApiError("invalid_params", "unknown screen")
                context.startActivity(Intent(context, MainActivity::class.java).putExtra("zerno.screen", screen)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                JSONObject().put("screen", screen)
            }
            else -> throw ApiError("unknown_command")
        }
    }

    private suspend fun accounts() = db.accountDao().getAllAccounts().first()
    private fun account(rows: List<AccountEntity>, name: String?): AccountEntity {
        if (name == null) return rows.firstOrNull { it.isDefault } ?: rows.firstOrNull() ?: throw ApiError("account_not_found")
        val matches = rows.filter { it.name.equals(name, ignoreCase = true) }
        if (matches.size > 1) throw ApiError("account_ambiguous")
        return matches.singleOrNull() ?: throw ApiError("account_not_found", "Available: " + rows.joinToString { it.name })
    }

    private suspend fun add(r: ApiRequest): JSONObject {
        val type = when (r.text("type")) {
            "expense" -> TransactionType.EXPENSE
            "income" -> TransactionType.INCOME
            else -> throw ApiError("invalid_params", "type must be expense or income")
        }
        val amount = r.amount()
        val date = r.date()
        val note = r.text("note").orEmpty()
        val name = r.text("account")
        val categoryName = r.text("category")
        val currencyParam = r.text("currency")?.uppercase(java.util.Locale.ROOT)
        val fx = rates.rates.first()
        return db.withTransaction {
            val acct = account(accounts(), name)
            val currency = currencyParam ?: acct.currency
            val converted = if (currency == acct.currency) amount else {
                val source = baseRate(currency, fx) ?: throw ApiError("rate_unavailable", currency)
                val target = baseRate(acct.currency, fx) ?: throw ApiError("rate_unavailable", acct.currency)
                amount * source / target
            }
            if (!converted.isFinite() || converted <= 0) throw ApiError("invalid_params", "amount out of range")
            val categories = db.categoryDao().getAllCategoriesOnce().filter { !it.archived && it.type == type }
            val category = categoryName?.let { query ->
                val exact = categories.filter { normalizedCategoryName(query) == normalizedCategoryName(it.name) }
                val matches = exact.ifEmpty { categories.filter { categoryMatches(query, it.name) } }
                if (matches.size > 1) throw ApiError("category_ambiguous", matches.joinToString { it.name })
                matches.singleOrNull() ?: throw ApiError("category_not_found", "Nearest: " + categories.sortedBy { distance(query.lowercase(), it.name.lowercase()) }.take(5).joinToString { it.name })
            }
            // Negative random IDs cannot be confused with Room-generated or imported positive IDs.
            val id = (UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE).coerceAtLeast(1) * -1
            val tx = TransactionEntity(id = id, type = type, amount = converted, accountId = acct.id,
                categoryId = category?.id, note = note, date = date)
            if (transactions.getById(id) != null) throw ApiError("storage_error", "ID collision; retry")
            transactions.addTransaction(tx)
            if (!ownership.edit().putLong("id", id).putString("fingerprint", tx.toString()).commit()) throw ApiError("storage_error", "Cannot persist undo ownership")
            JSONObject().put("id", id).put("amount", converted).put("currency", acct.currency)
                .put("category", category?.name ?: JSONObject.NULL).put("account", acct.name)
                .put("balance_after", db.accountDao().getAccountById(acct.id)!!.balance)
        }
    }

    private suspend fun undo(): JSONObject = db.withTransaction {
        val id = ownership.getLong("id", 0)
        if (id >= 0) throw ApiError("nothing_to_undo")
        val tx = transactions.getById(id) ?: throw ApiError("nothing_to_undo", "API transaction already deleted")
        if (tx.toString() != ownership.getString("fingerprint", null)) throw ApiError("undo_conflict", "API transaction was edited; delete it in UI")
        transactions.deleteTransaction(tx)
        // Keep the tombstone: a repeated undo sees the absent row and cannot delete another transaction.
        JSONObject().put("id", id).put("balance_after", db.accountDao().getAccountById(tx.accountId)!!.balance)
    }

    private suspend fun balance(r: ApiRequest): JSONObject = db.withTransaction {
        val all = accounts()
        val name = r.text("account")
        val selected = if (name == null) all else listOf(account(all, name))
        val value = accountValuation(if (name == null) selected else selected.map { it.copy(includeInTotal = true) }, rates.rates.first())
        JSONObject().put("currency", "UAH").put("total", value.amount).put("complete", value.complete)
            .put("missing_rates", JSONArray(value.missingCurrencies.toList()))
            .put("accounts", JSONArray(selected.map { JSONObject().put("id", it.id).put("account", it.name)
                .put("amount", it.balance).put("currency", it.currency).put("include_in_total", it.includeInTotal) }))
    }

    private suspend fun summary(r: ApiRequest): JSONObject {
        val month = r.month()
        val zone = ZoneId.systemDefault()
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val rows = transactions.getTransactionsByPeriod(from, to).first()
        val fx = rates.rates.first()
        val financial = rows.filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.INCOME }
        val missing = valueInBase(financial.map { it.amount to it.accountCurrency }, fx).missingCurrencies
        val groups = financial.groupBy { it.type to it.categoryId }.map { (key, group) ->
            val value = valueInBase(group.map { it.amount to it.accountCurrency }, fx)
            JSONObject().put("type", key.first.name.lowercase()).put("category", group.first().categoryName ?: JSONObject.NULL)
                .put("amount", value.amount).put("complete", value.complete)
        }
        return JSONObject().put("month", month.toString()).put("currency", "UAH").put("complete", missing.isEmpty())
            .put("missing_rates", JSONArray(missing.toList())).put("categories", JSONArray(groups))
    }
}

private fun normalizedCategoryName(s: String) = s.trim().lowercase(java.util.Locale.ROOT)
    .replace('’', '\'').replace('ʼ', '\'').replace('`', '\'')

internal fun categoryMatches(query: String, name: String): Boolean {
    val aliases = listOf(
        setOf("продукти", "продукты", "groceries", "food"),
        setOf("ресторація", "ресторация", "dining", "eating out"), setOf("дозвілля", "досуг", "leisure"),
        setOf("транспорт", "transport"), setOf("здоров'я", "здоровье", "health"),
        setOf("подарунки", "подарки", "gifts"), setOf("сім'я", "семья", "family"),
        setOf("покупки", "shopping"), setOf("робота", "работа", "work"),
        setOf("зарплата", "salary"), setOf("фриланс", "фріланс", "freelance"), setOf("інше", "другое", "other"),
        setOf("food delivery", "доставка їжі", "доставка еды"),
        setOf("ресторани", "рестораны", "restaurants"), setOf("кафе", "cafe", "coffee"),
        setOf("кіно", "кино", "cinema", "movies"), setOf("gaming", "ігри", "игры"),
        setOf("хобі", "хобби", "hobbies"), setOf("спорт", "sport", "sports"),
        setOf("таксі", "такси", "taxi"), setOf("паркінг", "парковка", "parking"),
        setOf("пальне", "топливо", "fuel"), setOf("аптека", "pharmacy"),
        setOf("лікар", "врач", "doctor"), setOf("комуналка", "коммуналка", "utilities"),
        setOf("оренда", "аренда", "rent"), setOf("зв'язок", "связь", "communication"),
        setOf("інтернет", "интернет", "internet"), setOf("одяг", "одежда", "clothes"),
        setOf("електроніка", "электроника", "electronics"), setOf("краса", "красота", "beauty")
    )
    val a = normalizedCategoryName(query); val b = normalizedCategoryName(name)
    return a == b || aliases.any { a in it && b in it }
}

private fun distance(a: String, b: String): Int {
    var previous = IntArray(b.length + 1) { it }
    a.forEachIndexed { i, c ->
        val next = IntArray(b.length + 1); next[0] = i + 1
        b.forEachIndexed { j, d -> next[j + 1] = minOf(next[j] + 1, previous[j + 1] + 1, previous[j] + if (c == d) 0 else 1) }
        previous = next
    }
    return previous[b.length]
}
