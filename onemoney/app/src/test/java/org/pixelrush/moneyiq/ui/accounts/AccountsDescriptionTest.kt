package org.syalosovetskyi.onemoney.ui.accounts

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyThemeProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class, qualifiers = "uk")
class AccountsDescriptionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun description_is_conditional() = checkDescription(fontScale = 1f)

    @Test fun description_is_conditional_with_large_system_font() = checkDescription(fontScale = 2f)

    private fun checkDescription(fontScale: Float) {
        val description = "VOO · 3,72 шт × \$612,40"
        val account = AccountEntity(id = 1, name = "IBKR VOO", description = description)
        val flow = MutableStateFlow(AccountsUiState(accounts = listOf(account)))
        val vm = mockk<AccountsViewModel>(relaxed = true) { every { state } returns flow }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme { OneMoneyThemeProvider { AccountsScreen(viewModel = vm) } }
            }
        }

        val name = compose.onNodeWithText(account.name, useUnmergedTree = true)
        val subtitle = compose.onNodeWithText(description, useUnmergedTree = true)
        name.assertIsDisplayed()
        subtitle.assertIsDisplayed()
        assertTrue(subtitle.fetchSemanticsNode().boundsInRoot.top >= name.fetchSemanticsNode().boundsInRoot.bottom)

        compose.runOnIdle { flow.value = AccountsUiState(accounts = listOf(account.copy(description = ""))) }
        subtitle.assertDoesNotExist()
        name.assertIsDisplayed()
        val emptyHeight = compose.onNodeWithText(account.name).fetchSemanticsNode().boundsInRoot.height

        compose.runOnIdle { flow.value = AccountsUiState(accounts = listOf(account.copy(description = "   "))) }
        compose.onNodeWithText("   ", useUnmergedTree = true).assertDoesNotExist()
        subtitle.assertDoesNotExist()
        assertEquals(emptyHeight, compose.onNodeWithText(account.name).fetchSemanticsNode().boundsInRoot.height, 0.01f)
    }
}
