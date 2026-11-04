package org.syalosovetskyi.onemoney.util

import org.syalosovetskyi.onemoney.data.db.dao.TransactionWithDetails
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType

data class CurrencyTotal(val income: Double = 0.0, val expense: Double = 0.0)

/** Never add unlike units. Transfers are excluded from income and expenditure. */
internal fun currencyTotals(transactions: List<TransactionWithDetails>): Map<String, CurrencyTotal> =
    transactions.groupBy { it.accountCurrency }.mapValues { (_, rows) ->
        CurrencyTotal(
            income = rows.filter { it.type == TransactionType.INCOME || it.type == TransactionType.BORROW }.sumOf { it.amount },
            expense = rows.filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.LEND || it.type == TransactionType.REPAY }.sumOf { it.amount }
        )
    }.toSortedMap()

data class AccountCurrencyTotal(val assets: Double, val debts: Double) {
    val net: Double get() = assets - debts
}

internal fun accountCurrencyTotals(accounts: List<org.syalosovetskyi.onemoney.data.db.entities.AccountEntity>): Map<String, AccountCurrencyTotal> =
    accounts.filter { it.includeInTotal }.groupBy { it.currency }.mapValues { (_, rows) ->
        AccountCurrencyTotal(
            assets = rows.filter { it.type != org.syalosovetskyi.onemoney.data.db.entities.AccountType.DEBT && it.balance > 0 }
                .sumOf { it.balance },
            debts = rows.sumOf {
                when {
                    it.type == org.syalosovetskyi.onemoney.data.db.entities.AccountType.DEBT -> it.balance
                    it.balance < 0 -> -it.balance
                    else -> 0.0
                }
            }
        )
    }.toSortedMap()
