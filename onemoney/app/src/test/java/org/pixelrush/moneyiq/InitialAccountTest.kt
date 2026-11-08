package org.syalosovetskyi.onemoney

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class InitialAccountTest {
    @Test fun `bug 1 fresh seed uses localized wallet and UAH`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf("uk" to "Гаманець", "en" to "Wallet", "ru" to "Кошелёк").forEach { (language, expected) ->
            val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale(language)) })
            val seed = initialAccount(localized)
            assertEquals(expected, seed.name)
            assertEquals("UAH", seed.currency)
            assertEquals(0.0, seed.balance, 0.0)
            assertTrue(seed.isDefault)
        }
    }
}
