package org.syalosovetskyi.onemoney.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.syalosovetskyi.onemoney.ui.theme.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ThemeSelectionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun dark_theme_and_custom_accent_recompose_material_and_money_tokens() {
        var dark by mutableStateOf(false)
        var accent by mutableStateOf<Color?>(null)
        var background = Color.Unspecified
        var primary = Color.Unspecified
        var money = Color.Unspecified
        compose.setContent { onemoneyTheme(darkTheme = dark, accentColor = accent) {
            OneMoneyThemeProvider(darkTheme = dark) {
                background = MaterialTheme.colorScheme.background
                primary = MaterialTheme.colorScheme.primary
                money = OneMoneyTheme.colors.incomeGreen
                Text("Theme")
            }
        } }
        var lightBackground = Color.Unspecified
        var defaultPrimary = Color.Unspecified
        compose.runOnIdle {
            lightBackground = background; defaultPrimary = primary
            assertEquals(OneMoneyLightTokens.colors.incomeGreen, money)
            dark = true
        }
        compose.runOnIdle {
            assertNotEquals(lightBackground, background)
            assertEquals(OneMoneyDarkTokens.colors.incomeGreen, money)
            accent = Color.Blue
        }
        compose.runOnIdle { assertNotEquals(defaultPrimary, primary) }
    }
}
