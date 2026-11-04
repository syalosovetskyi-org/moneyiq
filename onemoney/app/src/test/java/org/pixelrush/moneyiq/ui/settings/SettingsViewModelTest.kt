package org.syalosovetskyi.onemoney.ui.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import app.cash.turbine.test
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.syalosovetskyi.onemoney.data.repository.*
import org.syalosovetskyi.onemoney.util.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule(StandardTestDispatcher())
    private val repo = mockk<SettingsRepository>()
    private val preferences = mutablePreferencesOf()
    private fun vm(): SettingsViewModel {
        every { repo.settings } returns flowOf(AppSettings(defaultCurrency = "EUR"))
        coEvery { repo.update(any()) } coAnswers {
            firstArg<androidx.datastore.preferences.core.MutablePreferences.() -> Unit>().invoke(preferences)
        }
        return SettingsViewModel(repo, mockk(relaxed = true))
    }

    @Test fun `loads stored settings`() = runTest {
        val vm = vm()
        vm.settings.test {
            awaitItem()
            runCurrent()
            assertEquals("EUR", awaitItem().defaultCurrency)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `all form settings persist to their own keys`() = runTest {
        val vm = vm()
        vm.setThemeMode(ThemeMode.DARK)
        vm.setAccentColor(123)
        vm.setHomeScreen(HomeScreenTab.ACCOUNTS)
        vm.setBudgetVisible(false)
        vm.setLoginProtection(true)
        vm.setDefaultCurrency("USD")
        vm.setCurrencyFormat(3)
        vm.setFirstDayOfWeek(2)
        vm.setFirstDayOfMonth(5)
        vm.setLanguage("uk")
        runCurrent()
        assertEquals("DARK", preferences[SettingsRepository.KEY_THEME_MODE])
        assertEquals(123, preferences[SettingsRepository.KEY_ACCENT_COLOR])
        assertEquals("ACCOUNTS", preferences[SettingsRepository.KEY_HOME_SCREEN])
        assertEquals(false, preferences[SettingsRepository.KEY_BUDGET_VISIBLE])
        assertEquals(true, preferences[SettingsRepository.KEY_LOGIN_PROTECTION])
        assertEquals("USD", preferences[SettingsRepository.KEY_DEFAULT_CURRENCY])
        assertEquals(3, preferences[SettingsRepository.KEY_CURRENCY_FORMAT])
        assertEquals(2, preferences[SettingsRepository.KEY_FIRST_DAY_OF_WEEK])
        assertEquals(5, preferences[SettingsRepository.KEY_FIRST_DAY_OF_MONTH])
        assertEquals("uk", preferences[SettingsRepository.KEY_LANGUAGE])
    }
}
