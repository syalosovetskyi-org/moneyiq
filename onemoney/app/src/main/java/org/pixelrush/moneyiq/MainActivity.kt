package org.syalosovetskyi.onemoney

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.*
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.syalosovetskyi.onemoney.data.repository.SettingsRepository
import org.syalosovetskyi.onemoney.data.repository.ThemeMode
import org.syalosovetskyi.onemoney.ui.navigation.onemoneyNavGraph
import org.syalosovetskyi.onemoney.ui.settings.SettingsViewModel
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyThemeProvider
import org.syalosovetskyi.onemoney.ui.theme.onemoneyTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    private val screenRequestId = mutableLongStateOf(0L)
    private val requestedScreen = mutableStateOf<String?>(null)
    private var lastPausedAt = 0L
    private var isUnlocked   = mutableStateOf(true)

    override fun attachBaseContext(newBase: Context) {
        // Apply the user-selected app language before the activity resources are resolved.
        super.attachBaseContext(org.syalosovetskyi.onemoney.util.LocaleWrapper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedScreen.value = intent.getStringExtra("zerno.screen")
        enableEdgeToEdge()
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = org.syalosovetskyi.onemoney.data.repository.AppSettings()
            )
            val systemDark  = isSystemInDarkTheme()
            val darkTheme   = when (settings.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT  -> false
                ThemeMode.DARK   -> true
            }
            val accentColor = Color(settings.accentColorArgb)

            onemoneyTheme(darkTheme = darkTheme, accentColor = accentColor) {
                OneMoneyThemeProvider(darkTheme = darkTheme) {
                    val unlocked by isUnlocked
                    if (!unlocked) {
                        LockScreen(onUnlock = { triggerBiometric() })
                    } else {
                        onemoneyNavGraph(requestedScreen = requestedScreen.value, screenRequestId = screenRequestId.longValue)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        screenRequestId.longValue++
        requestedScreen.value = intent.getStringExtra("zerno.screen")
    }

    override fun onPause() {
        super.onPause()
        lastPausedAt = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val settings = settingsRepository.settings.first()
            if (settings.loginProtectionEnabled) {
                val elapsed = System.currentTimeMillis() - lastPausedAt
                if (elapsed > 30_000 && lastPausedAt > 0) {
                    isUnlocked.value = false
                    triggerBiometric()
                }
            } else {
                isUnlocked.value = true
            }
        }
    }

    private fun triggerBiometric() {
        val bm = BiometricManager.from(this)
        val canAuth = bm.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            isUnlocked.value = true
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                isUnlocked.value = true
            }
            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                if (code == BiometricPrompt.ERROR_USER_CANCELED ||
                    code == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    finish()
                }
            }
            override fun onAuthenticationFailed() {
                Toast.makeText(this@MainActivity, "Автентифікацію не пройдено", Toast.LENGTH_SHORT).show()
            }
        })

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Zerno")
            .setSubtitle("Підтвердіть особу для входу")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()

        prompt.authenticate(info)
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
}
