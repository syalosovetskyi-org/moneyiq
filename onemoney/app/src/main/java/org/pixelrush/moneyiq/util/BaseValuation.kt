package org.syalosovetskyi.onemoney.util

import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType

/** All cross-account summaries use UAH, the application's reporting currency. */
data class BaseValuation(val amount: Double, val missingCurrencies: Set<String> = emptySet()) {
    val complete: Boolean get() = missingCurrencies.isEmpty()
}

fun baseRate(currency: String, rates: Map<String, Double>): Double? =
    (if (currency == "UAH") 1.0 else rates[currency])?.takeIf { it.isFinite() && it > 0 }

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
    valueInBase(accounts.filter { it.includeInTotal }.map { it.balance to it.currency }, rates)

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
