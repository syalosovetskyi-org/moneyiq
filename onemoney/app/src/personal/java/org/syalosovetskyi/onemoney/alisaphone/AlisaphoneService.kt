package org.syalosovetskyi.onemoney.alisaphone

import android.app.Service
import android.content.Intent
import android.os.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import org.syalosovetskyi.onemoney.BuildConfig
import org.syalosovetskyi.onemoney.CrashReporter
import javax.inject.Inject

@AndroidEntryPoint
class AlisaphoneService : Service() {
    @Inject internal lateinit var commands: AlisaphoneCommands
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messenger by lazy { Messenger(Handler(Looper.getMainLooper()) { message ->
        // Capture identity from the actual Message, never from a caller-supplied Bundle.
        val uid = message.sendingUid
        val reply = message.replyTo
        if (reply == null) {
            android.util.Log.w("AlisaphoneAPI", "Missing replyTo from uid=$uid")
        } else {
            val data = Bundle(message.data)
            scope.launch {
                val response = Bundle()
                try {
                    val denied = CallerVerifier.from(packageManager, BuildConfig.ALISAPHONE_CERT_SHA256).error(uid)
                    if (denied != null) {
                        android.util.Log.w("AlisaphoneAPI", "$denied uid=$uid")
                        throw ApiError(denied)
                    }
                    val result = commands.execute(ApiRequest.parse(data))
                    response.putBoolean("ok", true)
                    response.putString("result", result.toString())
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: ApiError) {
                    // Expected command failures are returned explicitly to the client.
                    response.putBoolean("ok", false)
                    response.putString("error", error.code)
                    response.putString("result", org.json.JSONObject().put("message", error.details).toString())
                } catch (error: Exception) {
                    CrashReporter.warning("AlisaphoneAPI", "Command failed uid=$uid", error)
                    response.putBoolean("ok", false)
                    response.putString("error", "internal_error")
                    response.putString("result", "{}")
                }
                try { reply.send(Message.obtain().apply { this.data = response }) }
                catch (error: RemoteException) { CrashReporter.warning("AlisaphoneAPI", "Reply failed uid=$uid", error) }
            }
        }
        true
    }) }
    override fun onBind(intent: Intent): IBinder = messenger.binder
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
