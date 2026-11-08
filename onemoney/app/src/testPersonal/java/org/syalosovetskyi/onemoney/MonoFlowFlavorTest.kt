package org.syalosovetskyi.onemoney

import org.junit.Assert.*
import org.junit.Test
import org.syalosovetskyi.onemoney.data.repository.AppSettings
import org.syalosovetskyi.onemoney.workers.monoFlowConfigured

class MonoFlowFlavorTest {
    @Test fun `distribution gates mono-flow scheduling`() {
        assertEquals(true, BuildConfig.MONOFLOW_ENABLED)
        assertFalse(monoFlowConfigured(AppSettings()))
        assertFalse(monoFlowConfigured(AppSettings(monoflowUrl = "https://example.test")))
        assertEquals(true, monoFlowConfigured(AppSettings(monoflowUrl = "https://example.test", monoflowToken = "test")))
    }
}
