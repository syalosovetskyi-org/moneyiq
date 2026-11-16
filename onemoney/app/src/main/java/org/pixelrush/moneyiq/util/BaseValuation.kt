package org.syalosovetskyi.onemoney.util

import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType

/**
 * Валюта звітності: усі крос-валютні підсумки (шапка main, бюджет, категорії) перераховуються
 * в неї за курсами НБУ (`rate` = гривень за одиницю). Знак валюти для таких сум береться
 * з цього коду через [formatBase], а не зашивається в UI як «₴» (R10, serg/tasks#3037).
 */
const val REPORTING_CURRENCY = "UAH"

/** Знак валюти звітності — для місць, де число і знак виводяться окремо (клавіатура, підписи). */
val REPORTING_CURRENCY_SYMBOL: String
    get() = org.syalosovetskyi.onemoney.ui.settings.data.CURRENCIES_ALL
        .find { it.code == REPORTING_CURRENCY }?.symbol ?: REPORTING_CURRENCY

/** Сума у валюті звітності зі знаком валюти, наприклад «1 234,50 ₴». */
fun formatBase(amount: Double): String = formatMoney(amount, REPORTING_CURRENCY)

/** All cross-account summaries use UAH, the application's reporting currency. */
data class BaseValuation(val amount: Double, val missingCurrencies: Set<String> = emptySet()) {
    val complete: Boolean get() = missingCurrencies.isEmpty()
}

fun baseRate(currency: String, rates: Map<String, Double>): Double? =
    (if (currency == REPORTING_CURRENCY) 1.0 else rates[currency])?.takeIf { it.isFinite() && it > 0 }

fun valueInBase(values: List<Pair<Double, String>>, rates: Map<String, Double>): BaseValuation {
    val missing = sortedSetOf<String>()
    var total = 0.0
    values.forEach { (amount, currency) ->
        val rate = baseRate(currency, rates)
        if (rate == null) { if (amount != 0.0) missing += currency }
        else total += amount * rate
    }
    return BaseValuation(total, missing)
}

fun accountValuation(accounts: List<AccountEntity>, rates: Map<String, Double>): BaseValuation =
    // Знак і фільтр includeInTotal — з тієї ж функції, що й «Мої фінанси» (R3).
    valueInBase(accounts.filter { it.includeInTotal }.map { totalContribution(it) to it.currency }, rates)

data class PeriodBalances(val opening: BaseValuation, val closing: BaseValuation)

/** Reconstruct boundaries from current balances, including transfers and transactions after the range. */
fun periodBalances(accounts: List<AccountEntity>, transactions: List<TransactionEntity>,
                   rates: Map<String, Double>, from: Long, to: Long): PeriodBalances {
    fun at(boundary: Long, inclusive: Boolean): BaseValuation {
        val balances = accounts.associate { it.id to it.balance }.toMutableMap()
        transactions.filter { if (inclusive) it.date > boundary else it.date >= boundary }.forEach { tx ->
            val delta = if (tx.type == TransactionType.INCOME || tx.type == TransactionType.BORROW) tx.amount else -tx.amount
            balances[tx.accountId]?.let { balances[tx.accountId] = it - delta }
            if (tx.type == TransactionType.TRANSFER || tx.type == TransactionType.REPAY) {
                tx.toAccountId?.let { id -> balances[id]?.let { balances[id] = it - (tx.toAmount ?: tx.amount) } }
            }
        }
        return accountValuation(accounts.map { it.copy(balance = balances.getValue(it.id)) }, rates)
    }
    return PeriodBalances(at(from, false), at(to, true))
}

internal fun canForecast(incomeBudget: Double, income: Double, expense: Double, daysPassed: Int, days: Int): Boolean =
    (incomeBudget > 0 || income > 0) && expense > 0 && daysPassed > 0 && days > daysPassed
