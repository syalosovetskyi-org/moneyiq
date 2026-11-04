package org.syalosovetskyi.onemoney

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.*
import org.junit.After
import org.junit.Test

class CrashReporterTest {
    @After fun cleanup() = unmockkAll()
    @Test fun collection_and_nonfatal_reporting_follow_build_type() {
        mockkStatic(FirebaseCrashlytics::class)
        val sdk = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns sdk
        CrashReporter.initialize(mockk<Context>())
        verify { sdk.setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG) }
        val failure = IllegalStateException("test failure")
        CrashReporter.warning("test", "warning", failure)
        CrashReporter.error("test", "error", failure)
        verify(exactly = if (BuildConfig.DEBUG) 0 else 2) { sdk.recordException(failure) }
    }
}
