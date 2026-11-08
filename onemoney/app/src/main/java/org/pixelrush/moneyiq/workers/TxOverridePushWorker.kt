package org.syalosovetskyi.onemoney.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.syalosovetskyi.onemoney.data.repository.TxOverrideRepository

// ── Hilt Entry Point ──────────────────────────────────────────────────────────

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TxOverridePushEntryPoint {
    fun txOverrideRepository(): TxOverrideRepository
}

// ── Worker ────────────────────────────────────────────────────────────────────

/**
 * Віддає на mono-flow правки операцій (ім'я, категорія, тип), зроблені в застосунку.
 *
 * Без цього синк був би одностороннім і затирав правку за id при наступному тягненні
 * даних з сервера.
 */
class TxOverridePushWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!org.syalosovetskyi.onemoney.BuildConfig.MONOFLOW_ENABLED) return Result.failure(workDataOf("error" to "disabled"))
        if (!monoFlowConfigured(applicationContext)) return Result.failure(workDataOf("error" to "not_configured"))
        val repo = EntryPointAccessors
            .fromApplication(applicationContext, TxOverridePushEntryPoint::class.java)
            .txOverrideRepository()

        val result = repo.pushPending()
        return when {
            // сервер не налаштований — це не успіх відправки, але і ретраїти нема сенсу
            result.skipped              -> Result.failure(workDataOf("error" to "not_configured"))
            result.networkError         -> Result.retry()
            result.failed > 0           -> {
                Log.w(TAG, "правок відхилено сервером: ${result.failed}; вони лишаються в черзі")
                Result.failure()
            }
            else                        -> Result.success()
        }
    }

    companion object {
        private const val TAG = "TxOverridePush"
        const val WORK_NAME = "monoflow_override_push"

        /** Разова відправка — після кожної правки операції. */
        suspend fun scheduleOneTime(context: Context) {
            if (!monoFlowConfigured(context)) return
            val request = OneTimeWorkRequestBuilder<TxOverridePushWorker>()
                .addTag("monoflow")
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}
