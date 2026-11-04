package org.syalosovetskyi.onemoney.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.data.db.dao.AccountDao
import org.syalosovetskyi.onemoney.data.db.dao.TransactionDao
import org.syalosovetskyi.onemoney.data.db.entities.TransactionEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.util.calculateNextRepeatDate
import org.syalosovetskyi.onemoney.util.reminderOffsetDays
import org.syalosovetskyi.onemoney.util.startOfDay
import java.util.Calendar
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepeatWorkerEntryPoint {
    fun transactionDao(): TransactionDao
    fun accountDao(): AccountDao
}

class RepeatTransactionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val ep      = EntryPointAccessors.fromApplication(applicationContext, RepeatWorkerEntryPoint::class.java)
            val txDao   = ep.transactionDao()
            val accDao  = ep.accountDao()
            val today   = startOfDay(System.currentTimeMillis())

            // ── 1. Автосоздание просроченных повторяющихся транзакций ─────────
            var dueTxs = txDao.getDueRepeatTransactions(today)
            while (dueTxs.isNotEmpty()) {
                for (tx in dueTxs) {
                    val nextDate     = tx.nextRepeatDate ?: continue
                    val newNextDate  = calculateNextRepeatDate(nextDate, tx.repeatMode)
                    val newTx = tx.copy(
                        id             = 0,
                        date           = nextDate,
                        createdAt      = System.currentTimeMillis(),
                        nextRepeatDate = if (tx.repeatMode != "NEVER" && newNextDate != Long.MAX_VALUE)
                            newNextDate else null
                    )
                    txDao.insertTransaction(newTx)
                    updateRepeatBalance(accDao, newTx)
                    txDao.clearNextRepeatDate(tx.id)
                }
                dueTxs = txDao.getDueRepeatTransactions(today)
            }

            // ── 2. Напоминания о предстоящих транзакциях ─────────────────────
            val upcomingTxs = txDao.getTransactionsWithReminder()
            for (tx in upcomingTxs) {
                val nextDate = tx.nextRepeatDate ?: continue
                val offsetDays = reminderOffsetDays(tx.reminderMode)
                if (offsetDays < 0) continue
                val reminderDate = startOfDay(nextDate) - offsetDays * 86_400_000L
                if (startOfDay(reminderDate) == today) {
                    sendReminderNotification(applicationContext, tx.note, tx.amount, nextDate, tx.id.toInt())
                }
            }

            scheduleNext(applicationContext)
            Result.success()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w("RepeatTransactionWorker", "Failed to process recurring transactions", e)
            Result.retry()
        }
    }

    private fun sendReminderNotification(
        context:  Context,
        note:     String,
        amount:   Double,
        date:     Long,
        notifId:  Int
    ) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        createReminderChannel(context)
        val fmt   = java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
        val title = if (note.isNotBlank()) note else "Повторна транзакція"
        val text  = "${String.format("%.2f", amount)} ₴ · ${fmt.format(java.util.Date(date))}"
        val notif = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(notifId + 2000, notif)
    }

    companion object {
        const val WORK_NAME          = "repeat_transactions"
        const val REMINDER_CHANNEL_ID = "onemoney_repeat_reminder"

        fun scheduleOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<RepeatTransactionWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        internal fun scheduleNext(context: Context) {
            val delay   = delayUntilMidnight()
            val request = OneTimeWorkRequestBuilder<RepeatTransactionWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        private fun createReminderChannel(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(REMINDER_CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(
                    REMINDER_CHANNEL_ID,
                    "Нагадування про платежі",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Нагадування про заплановані повторні транзакції" }
            )
        }

        private fun delayUntilMidnight(): Long {
            val target = Calendar.getInstance().apply {
                add(Calendar.DATE, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return maxOf(target.timeInMillis - System.currentTimeMillis(), 60_000L)
        }
    }
}

internal suspend fun updateRepeatBalance(accDao: AccountDao, tx: TransactionEntity) {
    when (tx.type) {
        TransactionType.INCOME   -> accDao.updateBalance(tx.accountId, +tx.amount)
        TransactionType.EXPENSE  -> accDao.updateBalance(tx.accountId, -tx.amount)
        TransactionType.BORROW   -> accDao.updateBalance(tx.accountId, +tx.amount)
        TransactionType.LEND     -> accDao.updateBalance(tx.accountId, -tx.amount)
        TransactionType.TRANSFER -> {
            accDao.updateBalance(tx.accountId, -tx.amount)
            tx.toAccountId?.let { accDao.updateBalance(it, +(tx.toAmount ?: tx.amount)) }
        }
        TransactionType.REPAY    -> {
            accDao.updateBalance(tx.accountId, -tx.amount)
            tx.toAccountId?.let { accDao.updateBalance(it, +(tx.toAmount ?: tx.amount)) }
        }
    }
}
