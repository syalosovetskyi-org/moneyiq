package org.syalosovetskyi.onemoney.alisaphone

import org.junit.Assert.*
import org.junit.Test

class CallerVerifierTest {
    private val pin = "AB".repeat(32)
    private fun verify(config: String = pin, packages: List<String> = listOf("com.alisaphone"), signatures: List<String> = listOf(pin)) =
        CallerVerifier(config, { packages }, { signatures }).error(123)
    @Test fun `allowed package and signature accepted`() { assertNull(verify()) }
    @Test fun `foreign package denied even with pinned signature`() { assertEquals("caller_denied", verify(packages = listOf("com.foreign"))) }
    @Test fun `foreign signature denied`() { assertEquals("caller_denied", verify(signatures = listOf("CD".repeat(32)))) }
    @Test fun `missing configuration rejects every caller explicitly`() { assertEquals("caller_not_configured", verify(config = "")) }
    @Test fun `malformed configuration is not configured`() { assertEquals("caller_not_configured", verify(config = "sha256")) }
    @Test fun `comma separated rotation pins and colon formatting accepted`() { assertNull(verify(config = "CD".repeat(32) + "," + pin.chunked(2).joinToString(":"))) }
    @Test fun `empty or partially untrusted multisigner set denied`() {
        assertEquals("caller_denied", verify(signatures = emptyList()))
        assertEquals("caller_denied", verify(signatures = listOf(pin, "CD".repeat(32))))
    }
    @Test fun `missing UID cannot pass authorization`() {
        assertEquals("caller_denied", CallerVerifier(pin, { listOf("com.alisaphone") }, { listOf(pin) }).error(-1))
    }
}
