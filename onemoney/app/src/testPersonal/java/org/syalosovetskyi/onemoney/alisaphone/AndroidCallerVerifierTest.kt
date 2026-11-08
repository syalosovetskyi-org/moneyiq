package org.syalosovetskyi.onemoney.alisaphone

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27], application = android.app.Application::class)
class AndroidCallerVerifierTest {
    @Suppress("DEPRECATION")
    @Test fun `Android verifier hashes actual signing certificate for message UID`() {
        val pm = mockk<PackageManager>()
        val bytes = byteArrayOf(1, 2, 3, 4)
        val info = PackageInfo().apply { signatures = arrayOf(Signature(bytes)) }
        every { pm.getPackagesForUid(123) } returns arrayOf("com.alisaphone")
        every { pm.getPackageInfo("com.alisaphone", PackageManager.GET_SIGNATURES) } returns info
        val pin = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it.toInt() and 255) }
        assertNull(CallerVerifier.from(pm, pin).error(123))
        assertEquals("caller_denied", CallerVerifier.from(pm, "AA".repeat(32)).error(123))
        verify(exactly = 2) { pm.getPackagesForUid(123) }
    }
}
