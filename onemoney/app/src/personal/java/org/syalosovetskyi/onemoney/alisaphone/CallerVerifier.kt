package org.syalosovetskyi.onemoney.alisaphone

import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

internal class CallerVerifier(private val pins: String,
    private val packages: (Int) -> List<String>, private val certificates: (String) -> List<String>) {
    fun error(uid: Int): String? {
        val trusted = pins.split(',').map { normalize(it) }.filter { it.matches(Regex("[0-9A-F]{64}")) }.toSet()
        if (trusted.isEmpty()) return "caller_not_configured"
        if (uid < 0) return "caller_denied"
        val accepted = packages(uid).filter { it in ALLOWED_PACKAGES }.any { name ->
            val actual = certificates(name).map(::normalize)
            actual.isNotEmpty() && actual.all { it in trusted }
        }
        return if (accepted) null else "caller_denied"
    }
    companion object {
        private val ALLOWED_PACKAGES = setOf("com.alisaphone")
        private fun normalize(value: String) = value.trim().replace(":", "").uppercase(java.util.Locale.ROOT)
        fun from(pm: PackageManager, pins: String) = CallerVerifier(pins,
            { pm.getPackagesForUid(it)?.toList().orEmpty() },
            { name ->
                val signatures = if (Build.VERSION.SDK_INT >= 28) {
                    pm.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners.orEmpty()
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(name, PackageManager.GET_SIGNATURES).signatures.orEmpty()
                }
                signatures.map { sig -> MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02X".format(it.toInt() and 255) } }
            })
    }
}
