package org.syalosovetskyi.onemoney.workers

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.workDataOf
import org.syalosovetskyi.onemoney.BuildConfig
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dagger.hilt.internal.GeneratedComponentManager
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.syalosovetskyi.onemoney.data.db.AppDatabase
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import org.syalosovetskyi.onemoney.data.repository.*

class WorkerTestApplication : Application(), GeneratedComponentManager<Any> {
    lateinit var component: Any
    override fun generatedComponent(): Any = component
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = WorkerTestApplication::class)
class WorkerBehaviorTest {
    private lateinit var app: WorkerTestApplication
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var overrides: TxOverrideRepository

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        settings = mockk(relaxed = true)
        overrides = mockk(relaxed = true)
        every { settings.settings } returns flowOf(AppSettings())
        app.component = object : dagger.hilt.internal.GeneratedComponent, DriveBackupEntryPoint, RepeatWorkerEntryPoint,
            MonoFlowSyncEntryPoint, TxOverridePushEntryPoint {
            override fun database() = db
            override fun accountDao() = db.accountDao()
            override fun categoryDao() = db.categoryDao()
            override fun transactionDao() = db.transactionDao()
            override fun settingsRepository() = settings
            override fun txOverrideRepository() = overrides
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().build())
    }
    @After fun close() { db.close() }

    @Test fun drive_without_folder_is_noop_success() = runTest {
        val result = TestListenableWorkerBuilder<DriveBackupWorker>(app).build().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { settings.update(any()) }
    }

    @Test fun drive_settings_failure_is_reported_as_failure() = runTest {
        every { settings.settings } throws IllegalStateException("Injected")
        assertEquals(ListenableWorker.Result.failure(),
            TestListenableWorkerBuilder<DriveBackupWorker>(app).build().doWork())
    }

    @Test fun sync_without_configuration_never_pushes_or_writes() = runTest {
        db.accountDao().insertAccount(AccountEntity(id = 1, name = "Preserve"))
        assertEquals(ListenableWorker.Result.failure(workDataOf("error" to if (BuildConfig.MONOFLOW_ENABLED) "not_configured" else "disabled")),
            TestListenableWorkerBuilder<MonoFlowSyncWorker>(app).build().doWork())
        assertEquals("Preserve", db.accountDao().getAccountById(1)!!.name)
        coVerify(exactly = 0) { overrides.pushPending() }
    }

    @Test fun pending_edits_prevent_sync_network_and_retry() = runTest {
        every { settings.settings } returns flowOf(AppSettings(monoflowUrl = "http://127.0.0.1:1", monoflowToken = "test"))
        coEvery { overrides.pushPending() } returns TxOverrideRepository.PushResult(pendingLeft = 1)
        assertEquals(if (BuildConfig.MONOFLOW_ENABLED) ListenableWorker.Result.retry() else ListenableWorker.Result.failure(workDataOf("error" to "disabled")),
            TestListenableWorkerBuilder<MonoFlowSyncWorker>(app).build().doWork())
    }

    @Test fun override_push_distinguishes_success_network_and_rejection() = runTest {
        every { settings.settings } returns flowOf(AppSettings(monoflowUrl = "https://example.test", monoflowToken = "test"))
        for ((pushed, expected) in listOf(
            TxOverrideRepository.PushResult(skipped = true) to ListenableWorker.Result.failure(workDataOf("error" to "not_configured")),
            TxOverrideRepository.PushResult(sent = 2) to ListenableWorker.Result.success(),
            TxOverrideRepository.PushResult(networkError = true) to ListenableWorker.Result.retry(),
            TxOverrideRepository.PushResult(failed = 1) to ListenableWorker.Result.failure())) {
            coEvery { overrides.pushPending() } returns pushed
            assertEquals(if (BuildConfig.MONOFLOW_ENABLED) expected else ListenableWorker.Result.failure(workDataOf("error" to "disabled")), TestListenableWorkerBuilder<TxOverridePushWorker>(app).build().doWork())
        }
    }

    @Test fun repeat_empty_database_completes_without_creating_transactions() = runTest {
        assertEquals(ListenableWorker.Result.success(),
            TestListenableWorkerBuilder<RepeatTransactionWorker>(app).build().doWork())
        assertEquals(0, db.transactionDao().count())
    }

    @Test fun notification_worker_completes_and_posts_zerno_title() = runTest {
        val nm = app.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        org.robolectric.Shadows.shadowOf(nm).setNotificationsEnabled(true)
        assertEquals(ListenableWorker.Result.success(),
            TestListenableWorkerBuilder<NotificationWorker>(app).build().doWork())
        val manager = app.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val notifications = org.robolectric.Shadows.shadowOf(manager).allNotifications
        assertEquals(1, notifications.size)
        assertEquals("Zerno", notifications.first().extras.getString(android.app.Notification.EXTRA_TITLE))
    }
    @Test fun notifications_disabled_posts_nothing_but_schedules_next_reminder() = runTest {
        val manager = app.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        org.robolectric.Shadows.shadowOf(manager).setNotificationsEnabled(false)
        assertEquals(ListenableWorker.Result.success(),
            TestListenableWorkerBuilder<NotificationWorker>(app).build().doWork())
        assertTrue(org.robolectric.Shadows.shadowOf(manager).allNotifications.isEmpty())
        val scheduled = androidx.work.WorkManager.getInstance(app)
            .getWorkInfosForUniqueWork(NotificationWorker.WORK_NAME).get()
        assertEquals(1, scheduled.size)
        assertEquals(androidx.work.WorkInfo.State.ENQUEUED, scheduled.single().state)
    }

    @Test fun unconfigured_or_disabled_integration_schedules_neither_worker() = runTest {
        MonoFlowSyncWorker.schedule(app)
        MonoFlowSyncWorker.scheduleOneTime(app)
        TxOverridePushWorker.scheduleOneTime(app)
        val work = androidx.work.WorkManager.getInstance(app).getWorkInfosByTag("monoflow").get()
        assertTrue(work.isEmpty())
    }
    @Test fun configured_integration_is_scheduled_only_in_personal() = runTest {
        every { settings.settings } returns flowOf(AppSettings(monoflowUrl = "https://example.test", monoflowToken = "test"))
        MonoFlowSyncWorker.schedule(app)
        TxOverridePushWorker.scheduleOneTime(app)
        val manager = androidx.work.WorkManager.getInstance(app)
        assertEquals(if (BuildConfig.MONOFLOW_ENABLED) 1 else 0, manager.getWorkInfosForUniqueWork(MonoFlowSyncWorker.WORK_NAME).get().size)
        assertEquals(if (BuildConfig.MONOFLOW_ENABLED) 1 else 0, manager.getWorkInfosForUniqueWork(TxOverridePushWorker.WORK_NAME).get().size)
    }

}
