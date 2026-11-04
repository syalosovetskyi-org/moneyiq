package org.syalosovetskyi.onemoney

import android.content.Context
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

object CrashReporter {
    fun initialize(context: Context) {
        try {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        } catch (failure: Exception) {
            Log.w("CrashReporter", "Could not initialize crash reporting", failure)
        }
    }
    fun recordException(failure: Throwable) {
        if (!BuildConfig.DEBUG) {
            try { FirebaseCrashlytics.getInstance().recordException(scrub(failure)) } catch (reportingFailure: Exception) {
                Log.w("CrashReporter", "Could not record exception", reportingFailure)
            }
        }
    }
    /**
     * Zerno is a finance app: exception messages may carry amounts, transaction
     * descriptions, CSV rows or server response bodies. Only the exception class
     * and stack trace leave the device; messages stay in the local log.
     */
    internal fun scrub(failure: Throwable, depth: Int = 0): Throwable {
        val scrubbed = ScrubbedException(failure.javaClass.name)
        scrubbed.stackTrace = failure.stackTrace
        val cause = failure.cause
        if (cause != null && cause !== failure && depth < 8) scrubbed.initCause(scrub(cause, depth + 1))
        return scrubbed
    }

    internal class ScrubbedException(originalClass: String) : Exception(originalClass)

    fun warning(tag: String, message: String, failure: Throwable) {
        Log.w(tag, message, failure)
        recordException(failure)
    }
    fun error(tag: String, message: String, failure: Throwable) {
        Log.e(tag, message, failure)
        recordException(failure)
    }
}
