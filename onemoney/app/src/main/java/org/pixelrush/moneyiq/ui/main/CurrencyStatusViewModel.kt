package org.syalosovetskyi.onemoney.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import org.syalosovetskyi.onemoney.data.db.dao.AccountDao
import org.syalosovetskyi.onemoney.data.db.dao.TransactionDao
import org.syalosovetskyi.onemoney.data.repository.CurrencyRatesRepository
import org.syalosovetskyi.onemoney.util.baseRate
import javax.inject.Inject

@HiltViewModel
class CurrencyStatusViewModel @Inject constructor(accounts: AccountDao, transactions: TransactionDao,
                                                 rates: CurrencyRatesRepository) : ViewModel() {
    val missing = combine(accounts.getAllAccounts(), transactions.observeAllTransactions(), rates.rates) { accts, txs, fx ->
        val used = txs.map { it.accountId }.toSet()
        accts.filter { (it.includeInTotal && it.balance != 0.0) || it.id in used }
            .map { it.currency }.filter { baseRate(it, fx) == null }.toSortedSet().toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
}
