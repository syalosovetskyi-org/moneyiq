package org.syalosovetskyi.onemoney.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import org.syalosovetskyi.onemoney.R
import java.util.Calendar
import java.util.concurrent.TimeUnit

class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val nm = NotificationManagerCompat.from(applicationContext)
        if (nm.areNotificationsEnabled()) {
            createChannel(applicationContext)
            val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Zerno")
                .setContentText("Час додати сьогоднішні витрати 💰")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIF_ID, notif)
        }
        // Reschedule for next day
        val hour   = inputData.getInt(KEY_HOUR, 20)
        val minute = inputData.getInt(KEY_MINUTE, 0)
        scheduleNext(applicationContext, hour, minute)
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "onemoney_daily"
        const val WORK_NAME  = "daily_reminder"
        const val NOTIF_ID   = 1001
        const val KEY_HOUR   = "hour"
        const val KEY_MINUTE = "minute"

        fun createChannel(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Щоденне нагадування",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Нагадати про внесення витрат" }
            nm.createNotificationChannel(channel)
        }

        fun schedule(context: Context, hour: Int, minute: Int) {
            createChannel(context)
            val delay = delayUntil(hour, minute)
            val data = workDataOf(KEY_HOUR to hour, KEY_MINUTE to minute)
            val request = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        private fun scheduleNext(context: Context, hour: Int, minute: Int) {
            val data = workDataOf(KEY_HOUR to hour, KEY_MINUTE to minute)
            val request = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInitialDelay(delayUntil(hour, minute), TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        private fun delayUntil(hour: Int, minute: Int): Long {
            val now    = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DATE, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }
    }
}
