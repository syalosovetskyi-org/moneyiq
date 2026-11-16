package org.syalosovetskyi.onemoney.util

import org.syalosovetskyi.onemoney.data.db.dao.TransactionWithDetails
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
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

/**
 * Підсумки рахунків по валютах для «Мої фінанси» та підсумку «Рахунки» в пікерах.
 * [debts] — додатна величина боргу (модуль від'ємних балансів), тож [net] = [assets] − [debts]
 * дорівнює сумі балансів зі знаком, тобто тому самому, що й шапка main ([accountValuation]).
 */
data class AccountCurrencyTotal(val assets: Double, val debts: Double) {
    val net: Double get() = assets - debts
}

/**
 * Єдина конвенція знака для всіх підсумків (R3, serg/tasks#3037).
 *
 * Баланс рахунку зберігається ЗІ ЗНАКОМ: операції змінюють його через `updateBalance(±amount)`
 * (TransactionRepository), тож витрата з кредитки чи DEBT-рахунку робить баланс від'ємним —
 * «ми винні». Тип рахунку на знак не впливає: DEBT −1000 — борг, DEBT +1000 — переплата /
 * нам винні, і в підсумок він іде як актив. Окремої «додатної» конвенції для боргу в коді немає.
 *
 * Будь-який підсумок по рахунках (шапка main, «Мої фінанси», пікери, віджет) бере внесок
 * рахунку тільки через цю функцію і тільки з рахунків, де [AccountEntity.includeInTotal].
 */
internal fun totalContribution(account: AccountEntity): Double =
    if (account.includeInTotal) account.balance else 0.0

internal fun accountCurrencyTotals(accounts: List<AccountEntity>): Map<String, AccountCurrencyTotal> =
    accounts.filter { it.includeInTotal }.groupBy { it.currency }.mapValues { (_, rows) ->
        val contributions = rows.map(::totalContribution)
        AccountCurrencyTotal(
            assets = contributions.filter { it > 0 }.sum(),
            debts  = -contributions.filter { it < 0 }.sum()
        )
    }.toSortedMap()
