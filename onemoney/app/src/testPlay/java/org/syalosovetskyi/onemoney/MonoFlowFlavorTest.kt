package org.syalosovetskyi.onemoney

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config


import org.syalosovetskyi.onemoney.data.repository.AppSettings
import org.syalosovetskyi.onemoney.workers.monoFlowConfigured

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class MonoFlowFlavorTest {
    @Test fun `distribution gates mono-flow scheduling`() {
        assertEquals(false, BuildConfig.MONOFLOW_ENABLED)
        assertFalse(monoFlowConfigured(AppSettings()))
        assertFalse(monoFlowConfigured(AppSettings(monoflowUrl = "https://example.test")))
        assertEquals(false, monoFlowConfigured(AppSettings(monoflowUrl = "https://example.test", monoflowToken = "test")))
    }
    @Test fun `Play BuildConfig contains no private credentials or API pins`() {
        assertFalse(BuildConfig::class.java.fields.any { it.name.startsWith("DEBUG_MONOFLOW_") || it.name == "ALISAPHONE_CERT_SHA256" })
        assertEquals("", MonoFlowDefaults.url)
        assertEquals("", MonoFlowDefaults.token)
    }
    @Test fun `Play does not export Alisaphone service or enqueue server overrides`() = kotlinx.coroutines.test.runTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        @Suppress("DEPRECATION")
        val services = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SERVICES).services.orEmpty()
        assertFalse(services.any { it.name.contains("Alisaphone") })
        val dao = io.mockk.mockk<org.syalosovetskyi.onemoney.data.db.dao.TxOverrideDao>(relaxed = true)
        val settings = io.mockk.mockk<org.syalosovetskyi.onemoney.data.repository.SettingsRepository>(relaxed = true)
        val repo = org.syalosovetskyi.onemoney.data.repository.TxOverrideRepository(context, dao, settings)
        repo.enqueue(2_000_000_000_000L, "test", null, org.syalosovetskyi.onemoney.data.db.entities.TransactionType.EXPENSE)
        io.mockk.coVerify(exactly = 0) { dao.upsert(any()) }
        assertTrue(repo.pushPending().skipped)
    }

}
