package org.syalosovetskyi.onemoney.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.syalosovetskyi.onemoney.util.accountValuation
import org.syalosovetskyi.onemoney.data.db.dao.AccountDao
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val dao: AccountDao,
    private val ratesRepo: CurrencyRatesRepository
) {

    fun getAllAccounts(): Flow<List<AccountEntity>> = dao.getAllAccounts()

    /** Partial totals always travel with an explicit missing-rate status. */
    fun getValuation() = combine(dao.getAllAccounts(), ratesRepo.rates) { accounts, rates ->
        accountValuation(accounts, rates)
    }

    fun getTotalBalance(): Flow<Double?> = getValuation().map { it.amount }

    suspend fun getById(id: Long): AccountEntity? = dao.getAccountById(id)

    suspend fun save(account: AccountEntity): Long = dao.insertAccount(account)

    suspend fun update(account: AccountEntity) = dao.updateAccount(account)

    suspend fun delete(account: AccountEntity) = dao.deleteAccount(account)

    suspend fun setDefault(accountId: Long) {
        dao.clearAllDefault()
        dao.setDefault(accountId)
    }
}
