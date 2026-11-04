package org.syalosovetskyi.onemoney.util

import org.json.JSONArray
import org.json.JSONObject
import org.syalosovetskyi.onemoney.data.db.entities.*

// ── Модель резервної копії ────────────────────────────────────────────────────

data class BackupData(
    val version: Int,
    val exportDate: Long,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>
)

// ── Серіалізатор JSON ─────────────────────────────────────────────────────────

object BackupSerializer {

    const val BACKUP_VERSION = 1
    const val MIME_TYPE      = "application/json"

    /** Validate a complete replacement before touching live tables. */
    fun validate(data: BackupData) {
        require(data.version == BACKUP_VERSION) { "Unsupported backup version: ${data.version}" }
        fun ids(values: List<Long>): Set<Long> {
            require(values.all { it > 0 } && values.distinct().size == values.size) { "Invalid or duplicate IDs" }
            return values.toSet()
        }
        val accounts = ids(data.accounts.map { it.id })
        val categories = ids(data.categories.map { it.id })
        ids(data.transactions.map { it.id })
        require(data.accounts.all { it.balance.isFinite() && it.creditLimit.isFinite() }) { "Invalid account amount" }
        require(data.categories.all { it.budgetAmount.isFinite() && it.budgetAmount >= 0 &&
            (it.parentId == null || it.parentId in categories) }) { "Invalid category" }
        require(data.transactions.all {
            it.amount.isFinite() && it.amount > 0 &&
                (it.toAmount == null || (it.toAmount.isFinite() && it.toAmount > 0)) &&
                it.accountId in accounts && (it.toAccountId == null || it.toAccountId in accounts) &&
                (it.categoryId == null || it.categoryId in categories)
        }) { "Invalid transaction amount or reference" }
    }

    // ── Серіалізація ──────────────────────────────────────────────────────────

    fun serialize(data: BackupData): String {
        val root = JSONObject()
        root.put("version",    data.version)
        root.put("exportDate", data.exportDate)
        root.put("app",        "onemoney")

        // Accounts
        val accsArr = JSONArray()
        data.accounts.forEach { a ->
            accsArr.put(JSONObject().apply {
                put("id",             a.id)
                put("name",           a.name)
                put("type",           a.type.name)
                put("balance",        a.balance)
                put("currency",       a.currency)
                put("colorHex",       a.colorHex)
                put("icon",           a.icon)
                put("includeInTotal", a.includeInTotal)
                put("isDefault",      a.isDefault)
                put("sortOrder",      a.sortOrder)
                put("description",    a.description)
                put("creditLimit",    a.creditLimit)
                put("createdAt",      a.createdAt)
            })
        }
        root.put("accounts", accsArr)

        // Categories
        val catsArr = JSONArray()
        data.categories.forEach { c ->
            catsArr.put(JSONObject().apply {
                put("id",           c.id)
                put("name",         c.name)
                put("type",         c.type.name)
                put("colorHex",     c.colorHex)
                put("icon",         c.icon)
                put("budgetAmount", c.budgetAmount)
                put("budgetPeriod", c.budgetPeriod)
                put("isDefault",    c.isDefault)
                put("sortOrder",    c.sortOrder)
                put("archived",     c.archived)
                put("parentId",     c.parentId ?: JSONObject.NULL)
                put("currencyCode", c.currencyCode)
            })
        }
        root.put("categories", catsArr)

        // Transactions
        val txArr = JSONArray()
        data.transactions.forEach { t ->
            txArr.put(JSONObject().apply {
                put("id",          t.id)
                put("type",        t.type.name)
                put("amount",      t.amount)
                put("accountId",   t.accountId)
                put("toAccountId", t.toAccountId ?: JSONObject.NULL)
                put("toAmount",    t.toAmount ?: JSONObject.NULL)
                put("categoryId",  t.categoryId  ?: JSONObject.NULL)
                put("note",           t.note)
                put("date",           t.date)
                put("createdAt",      t.createdAt)
                put("repeatMode",     t.repeatMode)
                put("reminderMode",   t.reminderMode)
                put("nextRepeatDate", t.nextRepeatDate ?: JSONObject.NULL)
            })
        }
        root.put("transactions", txArr)

        return root.toString(2)
    }

    // ── Десеріалізація ────────────────────────────────────────────────────────

    fun deserialize(json: String): BackupData {
        val root    = JSONObject(json)
        val version = root.getInt("version")
        val date    = root.getLong("exportDate")

        val accounts = mutableListOf<AccountEntity>()
        val accsArr  = root.getJSONArray("accounts")
        for (i in 0 until accsArr.length()) {
            val a = accsArr.getJSONObject(i)
            accounts += AccountEntity(
                id             = a.getLong("id"),
                name           = a.getString("name"),
                type           = AccountType.valueOf(a.getString("type")),
                balance        = a.getDouble("balance"),
                currency       = a.getString("currency"),
                colorHex       = a.getString("colorHex"),
                icon           = a.getString("icon"),
                includeInTotal = a.getBoolean("includeInTotal"),
                isDefault      = a.getBoolean("isDefault"),
                sortOrder      = a.getInt("sortOrder"),
                description    = a.optString("description", ""),
                creditLimit    = a.optDouble("creditLimit", 0.0),
                createdAt      = a.optLong("createdAt", System.currentTimeMillis())
            )
        }

        val categories = mutableListOf<CategoryEntity>()
        val catsArr    = root.getJSONArray("categories")
        for (i in 0 until catsArr.length()) {
            val c = catsArr.getJSONObject(i)
            categories += CategoryEntity(
                id           = c.getLong("id"),
                name         = c.getString("name"),
                type         = TransactionType.valueOf(c.getString("type")),
                colorHex     = c.getString("colorHex"),
                icon         = c.getString("icon"),
                budgetAmount = c.getDouble("budgetAmount"),
                budgetPeriod = c.getString("budgetPeriod"),
                isDefault    = c.getBoolean("isDefault"),
                sortOrder    = c.getInt("sortOrder"),
                archived     = c.optBoolean("archived", false),
                parentId     = if (c.has("parentId") && !c.isNull("parentId")) c.getLong("parentId") else null,
                currencyCode = c.optString("currencyCode", "UAH")
            )
        }

        val transactions = mutableListOf<TransactionEntity>()
        val txArr        = root.getJSONArray("transactions")
        for (i in 0 until txArr.length()) {
            val t = txArr.getJSONObject(i)
            transactions += TransactionEntity(
                id          = t.getLong("id"),
                type        = TransactionType.valueOf(t.getString("type")),
                amount      = t.getDouble("amount"),
                accountId   = t.getLong("accountId"),
                toAccountId = if (!t.has("toAccountId") || t.isNull("toAccountId")) null else t.getLong("toAccountId"),
                toAmount    = if (t.has("toAmount") && !t.isNull("toAmount")) t.getDouble("toAmount") else null,
                categoryId  = if (!t.has("categoryId")  || t.isNull("categoryId"))  null else t.getLong("categoryId"),
                note           = t.optString("note", ""),
                date           = t.getLong("date"),
                createdAt      = t.optLong("createdAt", System.currentTimeMillis()),
                repeatMode     = t.optString("repeatMode", "NEVER"),
                reminderMode   = t.optString("reminderMode", "NEVER"),
                nextRepeatDate = if (t.has("nextRepeatDate") && !t.isNull("nextRepeatDate")) t.getLong("nextRepeatDate") else null
            )
        }

        return BackupData(version, date, accounts, categories, transactions)
    }
}
