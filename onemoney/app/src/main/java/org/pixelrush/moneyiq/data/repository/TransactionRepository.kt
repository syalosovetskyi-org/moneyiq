package org.syalosovetskyi.onemoney.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.syalosovetskyi.onemoney.data.db.dao.AccountDao
import org.syalosovetskyi.onemoney.data.db.dao.CategorySpending
import org.syalosovetskyi.onemoney.data.db.dao.TransactionDao
import org.syalosovetskyi.onemoney.data.db.dao.TransactionWithDetails
import org.syalosovetskyi.onemoney.data.db.entities.TransactionEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val ratesRepo: CurrencyRatesRepository
) {
    /**
     * Скільки зарахувати на рахунок-отримувач.
     *
     * Рахунки бувають у різних валютах: 900 ₴ на доларовий рахунок — це не 900 $.
     * Курс береться з кешу НБУ (`rate` = скільки гривень коштує одиниця валюти),
     * тому перерахунок працює й офлайн — але тільки після першого завантаження.
     *
     * Порахований результат ЗБЕРІГАЄТЬСЯ в операції (`toAmount`) і далі береться
     * звідти: курс змінюється, і відкат операції за свіжим курсом лишив би на
     * рахунках розбіжність.
     */
    private suspend fun creditedAmount(tx: TransactionEntity): Double {
        tx.toAmount?.let { return it }
        val toId = tx.toAccountId ?: return tx.amount
        val from = accountDao.getAccountById(tx.accountId)
        val to   = accountDao.getAccountById(toId)
        if (from == null || to == null || from.currency == to.currency) return tx.amount

        val rates    = ratesRepo.rates.first()
        val fromRate = rates[from.currency]
        val toRate   = rates[to.currency]
        if (fromRate == null || toRate == null || toRate == 0.0) {
            // Курс невідомий (кеш порожній або валюти немає в НБУ) — зараховуємо
            // «як є». Це неточно, тому факт видно в лозі, а не тільки в балансі.
            Log.w(
                TAG,
                "курс ${from.currency}→${to.currency} невідомий, переказ ${tx.amount} " +
                    "зараховано без перерахунку (рахунки ${from.id}→${to.id})"
            )
            return tx.amount
        }
        return tx.amount * fromRate / toRate
    }

    private companion object {
        const val TAG = "TransactionRepo"
    }

    fun getRecentTransactions(limit: Int = 50): Flow<List<TransactionWithDetails>> =
        transactionDao.getTransactionsPaged(limit)

    fun getTransactionsByPeriod(from: Long, to: Long): Flow<List<TransactionWithDetails>> =
        transactionDao.getTransactionsByDateRange(from, to)

    fun getIncomeSum(from: Long, to: Long): Flow<Double> =
        transactionDao.getSumByTypeAndPeriod(TransactionType.INCOME, from, to)

    fun getExpenseSum(from: Long, to: Long): Flow<Double> =
        transactionDao.getSumByTypeAndPeriod(TransactionType.EXPENSE, from, to)

    fun getCategorySpending(type: TransactionType, from: Long, to: Long): Flow<List<CategorySpending>> =
        transactionDao.getCategorySpending(type, from, to)

    suspend fun addTransaction(tx: TransactionEntity) {
        // Зачисляемую сумму считаем ДО вставки и кладём в саму операцию:
        // откат потом должен снять ровно столько же, сколько положили.
        val credited = if (tx.toAccountId != null) creditedAmount(tx) else tx.amount
        val stored   = if (tx.toAccountId != null) tx.copy(toAmount = credited) else tx
        transactionDao.insertTransaction(stored)
        when (tx.type) {
            TransactionType.INCOME   -> accountDao.updateBalance(tx.accountId, +tx.amount)
            TransactionType.EXPENSE  -> accountDao.updateBalance(tx.accountId, -tx.amount)
            TransactionType.BORROW   -> accountDao.updateBalance(tx.accountId, +tx.amount)  // деньги пришли
            TransactionType.LEND     -> accountDao.updateBalance(tx.accountId, -tx.amount)  // деньги ушли
            TransactionType.REPAY    -> {
                // Если toAccountId задан — гасится долг другого счёта, иначе уменьшается основной
                accountDao.updateBalance(tx.accountId, -tx.amount)
                tx.toAccountId?.let { accountDao.updateBalance(it, +credited) }
            }
            TransactionType.TRANSFER -> {
                accountDao.updateBalance(tx.accountId, -tx.amount)
                tx.toAccountId?.let { accountDao.updateBalance(it, +credited) }
            }
        }
    }

    suspend fun deleteTransaction(tx: TransactionEntity) {
        // Снимаем ровно то, что зачислили: у старых операций поля нет — там суммы равны
        val credited = tx.toAmount ?: tx.amount
        when (tx.type) {
            TransactionType.INCOME   -> accountDao.updateBalance(tx.accountId, -tx.amount)
            TransactionType.EXPENSE  -> accountDao.updateBalance(tx.accountId, +tx.amount)
            TransactionType.BORROW   -> accountDao.updateBalance(tx.accountId, -tx.amount)
            TransactionType.LEND     -> accountDao.updateBalance(tx.accountId, +tx.amount)
            TransactionType.REPAY    -> {
                accountDao.updateBalance(tx.accountId, +tx.amount)
                tx.toAccountId?.let { accountDao.updateBalance(it, -credited) }
            }
            TransactionType.TRANSFER -> {
                accountDao.updateBalance(tx.accountId, +tx.amount)
                tx.toAccountId?.let { accountDao.updateBalance(it, -credited) }
            }
        }
        transactionDao.deleteTransaction(tx)
    }

    suspend fun getById(id: Long): TransactionEntity? = transactionDao.getTransactionById(id)

    /** Откатывает балансовые изменения старой транзакции, применяет новые. */
    suspend fun updateTransaction(oldTx: TransactionEntity, newTx: TransactionEntity) {
        val oldCredited = oldTx.toAmount ?: oldTx.amount
        // Сумма пересчитывается заново, если поменялись сумма или любой из счетов;
        // иначе остаётся прежней — курс с тех пор мог уехать, а баланс сойтись должен.
        val keepsCredit = newTx.accountId == oldTx.accountId &&
            newTx.toAccountId == oldTx.toAccountId && newTx.amount == oldTx.amount
        val newCredited = when {
            newTx.toAccountId == null -> newTx.amount
            keepsCredit && oldTx.toAmount != null -> oldCredited
            else -> creditedAmount(newTx.copy(toAmount = null))
        }
        // 1. Откатить старые изменения баланса
        when (oldTx.type) {
            TransactionType.INCOME   -> accountDao.updateBalance(oldTx.accountId, -oldTx.amount)
            TransactionType.EXPENSE  -> accountDao.updateBalance(oldTx.accountId, +oldTx.amount)
            TransactionType.BORROW   -> accountDao.updateBalance(oldTx.accountId, -oldTx.amount)
            TransactionType.LEND     -> accountDao.updateBalance(oldTx.accountId, +oldTx.amount)
            TransactionType.REPAY    -> {
                accountDao.updateBalance(oldTx.accountId, +oldTx.amount)
                oldTx.toAccountId?.let { accountDao.updateBalance(it, -oldCredited) }
            }
            TransactionType.TRANSFER -> {
                accountDao.updateBalance(oldTx.accountId, +oldTx.amount)
                oldTx.toAccountId?.let { accountDao.updateBalance(it, -oldCredited) }
            }
        }
        // 2. Записать новую версию
        transactionDao.updateTransaction(
            newTx.copy(toAmount = if (newTx.toAccountId != null) newCredited else null)
        )
        // 3. Применить новые изменения баланса
        when (newTx.type) {
            TransactionType.INCOME   -> accountDao.updateBalance(newTx.accountId, +newTx.amount)
            TransactionType.EXPENSE  -> accountDao.updateBalance(newTx.accountId, -newTx.amount)
            TransactionType.BORROW   -> accountDao.updateBalance(newTx.accountId, +newTx.amount)
            TransactionType.LEND     -> accountDao.updateBalance(newTx.accountId, -newTx.amount)
            TransactionType.REPAY    -> {
                accountDao.updateBalance(newTx.accountId, -newTx.amount)
                newTx.toAccountId?.let { accountDao.updateBalance(it, +newCredited) }
            }
            TransactionType.TRANSFER -> {
                accountDao.updateBalance(newTx.accountId, -newTx.amount)
                newTx.toAccountId?.let { accountDao.updateBalance(it, +newCredited) }
            }
        }
    }
}
