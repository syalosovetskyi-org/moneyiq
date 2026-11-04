package org.syalosovetskyi.onemoney.components.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.syalosovetskyi.onemoney.ui.components.calculator.CalcStateHolder

class CalcStateTest {

    private lateinit var calc: CalcStateHolder

    @Before
    fun setup() {
        calc = CalcStateHolder()
    }

    // ── initial state ────────────────────────────────────────────────────────

    @Test
    fun `initial state shows 0`() {
        assertEquals("0", calc.currentStr)
    }

    @Test
    fun `initial state has no pending operation`() {
        assertNull(calc.pendingOp)
        assertEquals(0.0, calc.pendingVal, 0.001)
    }

    @Test
    fun `constructor with positive integer value shows integer string`() {
        val c = CalcStateHolder(42.0)
        assertEquals("42", c.currentStr)
    }

    @Test
    fun `constructor with decimal value shows comma separated string`() {
        val c = CalcStateHolder(3.5)
        assertEquals("3,5", c.currentStr)
    }

    @Test
    fun `constructor with zero shows 0`() {
        val c = CalcStateHolder(0.0)
        assertEquals("0", c.currentStr)
    }

    // ── digit input ──────────────────────────────────────────────────────────

    @Test
    fun `pressing digit replaces initial 0`() {
        calc.onKey("5")
        assertEquals("5", calc.currentStr)
    }

    @Test
    fun `pressing multiple digits builds number`() {
        calc.onKey("1")
        calc.onKey("2")
        calc.onKey("3")
        assertEquals("123", calc.currentStr)
    }

    @Test
    fun `result without operation returns current value`() {
        calc.onKey("7")
        assertEquals(7.0, calc.result(), 0.001)
    }

    // ── arithmetic ───────────────────────────────────────────────────────────

    @Test
    fun `addition 5 plus 3 equals 8`() {
        calc.onKey("5")
        calc.onKey("+")
        calc.onKey("3")
        assertEquals(8.0, calc.result(), 0.001)
    }

    @Test
    fun `subtraction 10 minus 4 equals 6`() {
        calc.onKey("1")
        calc.onKey("0")
        calc.onKey("−")
        calc.onKey("4")
        assertEquals(6.0, calc.result(), 0.001)
    }

    @Test
    fun `multiplication 3 times 4 equals 12`() {
        calc.onKey("3")
        calc.onKey("×")
        calc.onKey("4")
        assertEquals(12.0, calc.result(), 0.001)
    }

    @Test
    fun `division 9 divided by 3 equals 3`() {
        calc.onKey("9")
        calc.onKey("÷")
        calc.onKey("3")
        assertEquals(3.0, calc.result(), 0.001)
    }

    @Test
    fun `division by zero returns pendingVal instead of crashing`() {
        calc.onKey("9")
        calc.onKey("÷")
        calc.onKey("0")
        assertEquals(9.0, calc.result(), 0.001)
    }

    // ── equals key ───────────────────────────────────────────────────────────

    @Test
    fun `equals key computes result and clears operation`() {
        calc.onKey("5")
        calc.onKey("+")
        calc.onKey("3")
        calc.onKey("=")
        assertEquals("8", calc.currentStr)
        assertNull(calc.pendingOp)
        assertEquals(0.0, calc.pendingVal, 0.001)
    }

    @Test
    fun `equals key without pending op does nothing`() {
        calc.onKey("5")
        calc.onKey("=")
        assertEquals("5", calc.currentStr)
        assertNull(calc.pendingOp)
    }

    @Test
    fun `result with decimal formats without trailing zeros`() {
        calc.onKey("1")
        calc.onKey("÷")
        calc.onKey("4")
        calc.onKey("=")
        assertEquals("0,25", calc.currentStr)
    }

    // ── backspace ────────────────────────────────────────────────────────────

    @Test
    fun `backspace removes last character`() {
        calc.onKey("1")
        calc.onKey("2")
        calc.onKey("3")
        calc.onKey("⌫")
        assertEquals("12", calc.currentStr)
    }

    @Test
    fun `backspace on single digit returns 0`() {
        calc.onKey("5")
        calc.onKey("⌫")
        assertEquals("0", calc.currentStr)
    }

    @Test
    fun `backspace on 0 stays 0`() {
        calc.onKey("⌫")
        assertEquals("0", calc.currentStr)
    }

    // ── decimal comma ────────────────────────────────────────────────────────

    @Test
    fun `comma key adds decimal separator`() {
        calc.onKey("5")
        calc.onKey(",")
        assertEquals("5,", calc.currentStr)
    }

    @Test
    fun `comma key on 0 gives 0 comma`() {
        calc.onKey(",")
        assertEquals("0,", calc.currentStr)
    }

    @Test
    fun `comma key is not added twice`() {
        calc.onKey("5")
        calc.onKey(",")
        calc.onKey(",")
        assertEquals("5,", calc.currentStr)
    }

    @Test
    fun `decimal limited to 2 places after comma`() {
        calc.onKey("5")
        calc.onKey(",")
        calc.onKey("1")
        calc.onKey("2")
        calc.onKey("3") // should be ignored — exceeds 2 decimal places
        assertEquals("5,12", calc.currentStr)
    }

    // ── operator key ─────────────────────────────────────────────────────────

    @Test
    fun `operator key stores current value as pendingVal`() {
        calc.onKey("5")
        calc.onKey("+")
        assertEquals(5.0, calc.pendingVal, 0.001)
        assertEquals("+", calc.pendingOp)
    }

    @Test
    fun `operator key resets currentStr to 0`() {
        calc.onKey("5")
        calc.onKey("+")
        assertEquals("0", calc.currentStr)
    }

    @Test
    fun `chained operator evaluates previous operation`() {
        // 5 + 3 × ... → pendingVal becomes 8
        calc.onKey("5")
        calc.onKey("+")
        calc.onKey("3")
        calc.onKey("×")
        assertEquals(8.0, calc.pendingVal, 0.001)
        assertEquals("×", calc.pendingOp)
    }

    @Test
    fun `non finite initial value is safe`() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val calc = CalcStateHolder(value)
            org.junit.Assert.assertTrue(calc.result().isFinite())
            calc.onKey("=")
        }
    }

    @Test
    fun `overflow from repeated multiplication cannot crash or save infinity`() {
        val calc = CalcStateHolder(999999999999.0)
        repeat(40) {
            calc.onKey("×")
            "999999999999".forEach { calc.onKey(it.toString()) }
            calc.onKey("=")
        }
        org.junit.Assert.assertTrue(calc.result().isFinite())
    }
}
