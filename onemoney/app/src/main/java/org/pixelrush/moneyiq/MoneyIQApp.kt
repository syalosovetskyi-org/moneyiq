package org.syalosovetskyi.onemoney

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.syalosovetskyi.onemoney.BuildConfig
import org.syalosovetskyi.onemoney.data.db.dao.AccountDao
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import org.syalosovetskyi.onemoney.data.db.entities.AccountType
import org.syalosovetskyi.onemoney.data.repository.CategoryRepository
import org.syalosovetskyi.onemoney.data.repository.CurrencyRatesRepository
import org.syalosovetskyi.onemoney.workers.RepeatTransactionWorker
import javax.inject.Inject

@HiltAndroidApp
class onemoneyApp : Application() {

    @Inject lateinit var categoryRepository: CategoryRepository
    @Inject lateinit var accountDao: AccountDao
    @Inject lateinit var currencyRatesRepository: CurrencyRatesRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        CrashReporter.initialize(this)

        appScope.launch { seedInitialData() }
        appScope.launch { currencyRatesRepository.refreshIfStale() }
        try {
            RepeatTransactionWorker.scheduleOnce(this)
        } catch (failure: Exception) {
            CrashReporter.warning(TAG, "Could not schedule recurring transactions", failure)
        }
    }

    private suspend fun seedInitialData() {
        // Категории по умолчанию (seedDefaults внутри проверяет count > 0)
        categoryRepository.seedDefaults()
        // Починяем ключи иконок для существующих категорий
        categoryRepository.repairIconKeys()
        categoryRepository.repairDefaultColors()

        // Счёт по умолчанию — только при первом запуске
        if (accountDao.count() == 0) {
            accountDao.insertAccount(
                AccountEntity(
                    name = "Кошелёк",
                    type = AccountType.CASH,
                    balance = 0.0,
                    currency = "RUB",
                    colorHex = "#4CAF50",
                    icon = "wallet",
                    includeInTotal = true,
                    sortOrder = 0
                )
            )
        }
    }

    private companion object {
        const val TAG = "MoneyIQApp"
    }
}
