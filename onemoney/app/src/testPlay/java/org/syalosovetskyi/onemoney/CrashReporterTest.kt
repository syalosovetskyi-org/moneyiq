package org.syalosovetskyi.onemoney

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
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
        verify(exactly = if (BuildConfig.DEBUG) 0 else 2) { sdk.recordException(any()) }
        verify(exactly = 0) { sdk.recordException(failure) }
    }

    @Test fun scrub_drops_messages_but_keeps_class_and_stack() {
        val root = java.io.IOException("HTTP 400: {\"amount\":1234.56,\"description\":\"Аптека\"}")
        val failure = IllegalStateException("row 7: 1234.56;Аптека", root)
        val scrubbed = CrashReporter.scrub(failure)
        assertEquals(IllegalStateException::class.java.name, scrubbed.message)
        assertArrayEquals(failure.stackTrace, scrubbed.stackTrace)
        assertEquals(java.io.IOException::class.java.name, scrubbed.cause?.message)
        var t: Throwable? = scrubbed
        while (t != null) { assertFalse(t.message.orEmpty().contains("1234")); assertFalse(t.message.orEmpty().contains("Аптека")); t = t.cause }
    }
}
