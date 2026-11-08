package org.syalosovetskyi.onemoney

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import org.syalosovetskyi.onemoney.data.repository.SettingsRepository
import org.syalosovetskyi.onemoney.workers.MonoFlowSyncWorker
import org.syalosovetskyi.onemoney.workers.TxOverridePushWorker
import org.syalosovetskyi.onemoney.workers.monoFlowConfigured
import org.syalosovetskyi.onemoney.data.db.dao.AccountDao
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import org.syalosovetskyi.onemoney.data.db.entities.AccountType
import org.syalosovetskyi.onemoney.data.repository.CategoryRepository
import org.syalosovetskyi.onemoney.data.repository.CurrencyRatesRepository
import org.syalosovetskyi.onemoney.workers.RepeatTransactionWorker
import javax.inject.Inject

@HiltAndroidApp
class onemoneyApp : Application() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var categoryRepository: CategoryRepository
    @Inject lateinit var accountDao: AccountDao
    @Inject lateinit var currencyRatesRepository: CurrencyRatesRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        CrashReporter.initialize(this)

        appScope.launch { seedInitialData() }
        appScope.launch {
            settingsRepository.settings.map { Triple(it.monoflowUrl, it.monoflowToken, it.monoflowAutoSync) }
                .distinctUntilChanged().collect { (url, token, auto) ->
                    try {
                        val configured = monoFlowConfigured(org.syalosovetskyi.onemoney.data.repository.AppSettings(monoflowUrl = url, monoflowToken = token))
                        if (!configured) MonoFlowSyncWorker.cancel(this@onemoneyApp)
                        else {
                            if (auto) MonoFlowSyncWorker.schedule(this@onemoneyApp)
                            else androidx.work.WorkManager.getInstance(this@onemoneyApp).cancelUniqueWork(MonoFlowSyncWorker.WORK_NAME)
                            TxOverridePushWorker.scheduleOneTime(this@onemoneyApp)
                        }
                    } catch (failure: Exception) {
                        if (failure is kotlinx.coroutines.CancellationException) throw failure
                        CrashReporter.warning(TAG, "Could not reconcile MonoFlow scheduling", failure)
                    }
                }
        }
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
                initialAccount(this)
            )
        }
    }

    private companion object {
        const val TAG = "MoneyIQApp"
    }
}

/** Called only after the empty-account-table check; never rewrites existing user data. */
internal fun initialAccount(context: android.content.Context) = AccountEntity(
    name = context.getString(R.string.default_account_name), type = AccountType.CASH,
    balance = 0.0, currency = "UAH", isDefault = true, colorHex = "#4CAF50",
    icon = "wallet", includeInTotal = true, sortOrder = 0
)
