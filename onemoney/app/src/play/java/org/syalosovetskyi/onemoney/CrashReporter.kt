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
            try { FirebaseCrashlytics.getInstance().recordException(failure) } catch (reportingFailure: Exception) {
                Log.w("CrashReporter", "Could not record exception", reportingFailure)
            }
        }
    }
    fun warning(tag: String, message: String, failure: Throwable) {
        Log.w(tag, message, failure)
        recordException(failure)
    }
    fun error(tag: String, message: String, failure: Throwable) {
        Log.e(tag, message, failure)
        recordException(failure)
    }
}
