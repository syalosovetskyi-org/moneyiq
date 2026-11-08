package org.syalosovetskyi.onemoney.alisaphone

import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ApiRequestTest {
    private fun request(block: Bundle.() -> Unit = {}) = ApiRequest.parse(Bundle().apply { putString("cmd", "add_transaction"); block() })
    private fun rejects(code: String = "invalid_params", block: () -> Unit) {
        try { block(); fail("Expected $code") } catch (error: ApiError) { assertEquals(code, error.code) }
    }
    @Test fun `requires positive finite double amount`() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { n -> rejects { request { putDouble("amount", n) }.amount() } }
        rejects { request { putString("amount", "200") }.amount() }
        rejects { request().amount() }
        assertEquals(200.0, request { putDouble("amount", 200.0) }.amount(), 0.0)
    }
    @Test fun `recent limit defaults to ten and enforces range and type`() {
        assertEquals(10, request().limit())
        assertEquals(50, request { putString("cmd", "recent"); putInt("limit", 50) }.limit())
        listOf(0, 51, -1).forEach { n -> rejects { request { putString("cmd", "recent"); putInt("limit", n) }.limit() } }
        rejects { request { putString("cmd", "recent"); putDouble("limit", 10.0) }.limit() }
    }
    @Test fun `unknown command and missing command reject`() {
        rejects("unknown_command") { request { putString("cmd", "delete_everything") } }
        rejects { ApiRequest.parse(Bundle()) }
    }
    @Test fun `ISO dates and months validate strictly`() {
        assertEquals(0L, request { putString("date", "1970-01-01T00:00:00Z") }.date())
        assertEquals("2026-11", request { putString("cmd", "month_summary"); putString("month", "2026-11") }.month().toString())
        rejects { request { putString("date", "2026-02-30") }.date() }
        rejects { request { putString("cmd", "month_summary"); putString("month", "2026-13") }.month() }
    }
    @Test fun `category lookup is Unicode case insensitive across seed aliases`() {
        assertTrue(categoryMatches("ПРОДУКТЫ", "Продукти"))
        assertTrue(categoryMatches("GROCERIES", "Продукти"))
        assertTrue(categoryMatches("health", "Здоров’я"))
        assertFalse(categoryMatches("Transport", "Продукти"))
        assertTrue(categoryMatches("МОЯ КАТЕГОРИЯ", "Моя категория"))
    }
    @Test fun `unknown parameters are rejected instead of ignored`() {
        rejects { request { putString("cmd", "balance"); putString("acount", "Wallet") } }
    }

}
