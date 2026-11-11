package org.syalosovetskyi.onemoney.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test
import org.syalosovetskyi.onemoney.ui.theme.*

class ZernoColorTest {
    @Test fun `money tokens meet text contrast in both themes`() {
        for ((tokens, surface) in listOf(OneMoneyLightTokens.colors to Color(0xFFFFFFFF),
            OneMoneyDarkTokens.colors to Color(0xFF1C1F24))) {
            for (color in listOf(tokens.incomeGreen, tokens.expenseRed, tokens.transferBlue, tokens.debtOrange)) {
                val a = color.luminance()
                val b = surface.luminance()
                val contrast = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
                assertTrue("Contrast is $contrast", contrast >= 4.5f)
            }
        }
    }
}
