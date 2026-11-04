package org.syalosovetskyi.onemoney

import io.mockk.*
import io.sentry.Sentry
import org.junit.After
import org.junit.Test

class CrashReporterTest {
    @After fun cleanup() = unmockkAll()
    @Test fun nonfatal_reporting_respects_optional_configuration() {
        mockkStatic(Sentry::class)
        every { Sentry.captureException(any<Throwable>()) } returns io.sentry.protocol.SentryId.EMPTY_ID
        val failure = IllegalStateException("test failure")
        CrashReporter.warning("test", "warning", failure)
        CrashReporter.error("test", "error", failure)
        verify(exactly = if (BuildConfig.GLITCHTIP_DSN.isBlank()) 0 else 2) { Sentry.captureException(failure) }
    }
}
