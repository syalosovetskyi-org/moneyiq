package org.syalosovetskyi.onemoney

import android.content.Context
import android.util.Log
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid

object CrashReporter {
    fun initialize(context: Context) {
        // Падения уезжают в СВОЙ GlitchTip (glitchtip.ibotz.fun, проект mesh/onemoney).
        // Публичный sentry.io запрещён правилами меша, а здесь он был особенно неуместен:
        // приложение показывает балансы Сержа.
        //
        // DSN в исходнике не живёт — только через BuildConfig (local.properties / CI).
        // Пустой DSN = отправка выключена: тогда Sentry не инициализируется вообще,
        // иначе SDK молча копит события в очередь и шлёт их в никуда.
        val dsn = BuildConfig.GLITCHTIP_DSN
        if (dsn.isBlank()) {
            Log.i("CrashReporter", "GLITCHTIP_DSN не задан — звіти про падіння вимкнено")
        } else {
            SentryAndroid.init(context) { options ->
                options.dsn = dsn
                options.isEnabled = true
                options.environment = if (BuildConfig.DEBUG) "debug" else "production"
                options.release = "onemoney@${BuildConfig.VERSION_NAME}"
                options.sampleRate = 1.0          // 100% ошибок
                options.tracesSampleRate = 1.0    // 100% performance traces
                // Скриншот и дерево вьюх УВОЗЯТ БАЛАНСЫ целиком, а не текст ошибки, и
                // выключены даже для своего приёмника: UI GlitchTip открыт по домену.
                // Не включать без отдельного решения Сержа.
                options.isAttachScreenshot = false
                options.isAttachViewHierarchy = false
                options.isSendDefaultPii = false
                // Хлебные крошки по тапам называют экраны и элементы — для отладки
                // падения этого не нужно, а из имён видно, что человек делал с деньгами.
                options.isEnableUserInteractionTracing = false
                options.isDebug = BuildConfig.DEBUG  // подробные логи только в debug
            }
        }

    }
    fun recordException(failure: Throwable) {
        if (BuildConfig.GLITCHTIP_DSN.isNotBlank()) {
            try { Sentry.captureException(failure) } catch (reportingFailure: Exception) {
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
