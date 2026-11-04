package org.syalosovetskyi.onemoney.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.syalosovetskyi.onemoney.ui.accounts.AccountFormSheet
import org.syalosovetskyi.onemoney.ui.categories.CategoryFormSheet
import org.syalosovetskyi.onemoney.ui.components.calculator.*
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyThemeProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class FormRestorationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun calculator_pending_expression_survives_saved_state_restore() {
        val restoration = StateRestorationTester(compose)
        lateinit var calc: CalcStateHolder
        restoration.setContent {
            calc = rememberCalcState()
            Text(calc.displayExprNoSymbol())
        }
        compose.runOnIdle {
            calc.onKey("2"); calc.onKey("+"); calc.onKey("3")
            assertEquals(5.0, calc.result(), 0.0)
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertEquals("+", calc.pendingOp)
            assertEquals(5.0, calc.result(), 0.0)
        }
    }

    @Test fun account_name_survives_saved_state_restore() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { OneMoneyThemeProvider {
            AccountFormSheet(onSave = { _, _, _, _, _, _, _, _, _ -> }, onDismiss = {})
        } } }
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("Unsaved cash")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Unsaved cash").assertExists()
    }

    @Test fun category_name_survives_saved_state_restore() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { OneMoneyThemeProvider {
            CategoryFormSheet(existing = null, onSave = { _, _, _, _, _, _, _, _ -> }, onDismiss = {})
        } } }
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("Unsaved category")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Unsaved category").assertExists()
    }
}
