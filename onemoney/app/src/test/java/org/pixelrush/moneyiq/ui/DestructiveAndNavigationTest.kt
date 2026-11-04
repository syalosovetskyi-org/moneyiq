package org.syalosovetskyi.onemoney.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.ui.budget.*
import org.syalosovetskyi.onemoney.ui.main.AppDrawerContent
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyThemeProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class, qualifiers = "uk")
class DestructiveAndNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun drawer_every_visible_destination_invokes_callback() {
        var settings = 0
        var data = 0
        compose.setContent { MaterialTheme { OneMoneyThemeProvider {
            AppDrawerContent(onClose = {}, onDataClick = { data++ }, onSettingsClick = { settings++ })
        } } }
        compose.onNodeWithText("Zerno").assertExists()
        compose.onNodeWithText("Особистий бюджет").assertExists()
        compose.onNodeWithText(context.getString(R.string.settings_title)).performClick()
        compose.onNodeWithText(context.getString(R.string.settings_data)).performClick()
        compose.runOnIdle { assertEquals(1, settings); assertEquals(1, data) }
    }

    @Test fun budget_delete_requires_explicit_confirmation() {
        val vm = mockk<BudgetViewModel>(relaxed = true) {
            every { state } returns MutableStateFlow(BudgetUiState())
        }
        compose.setContent { MaterialTheme { OneMoneyThemeProvider {
            BudgetScreen(showSettings = true, viewModel = vm)
        } } }
        compose.onNodeWithText("Видалити бюджет").performClick()
        verify(exactly = 0) { vm.clearAllBudgets() }
        compose.onNodeWithText(context.getString(R.string.common_delete)).performClick()
        verify(exactly = 1) { vm.clearAllBudgets() }
    }
}
