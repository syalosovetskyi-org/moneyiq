package org.syalosovetskyi.onemoney.data.repository

import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class PreferenceRepositoriesTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun settings_defaults_roundtrip_and_invalid_enums() = runTest {
        val ds = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            temp.root.resolve("settings.preferences_pb")
        }
        val repo = SettingsRepository(ds)
        assertEquals(AppSettings().copy(monoflowUrl = repo.settings.first().monoflowUrl,
            monoflowToken = repo.settings.first().monoflowToken), repo.settings.first())
        repo.update {
            this[SettingsRepository.KEY_THEME_MODE] = "DARK"
            this[SettingsRepository.KEY_HOME_SCREEN] = "ACCOUNTS"
            this[SettingsRepository.KEY_DEFAULT_CURRENCY] = "EUR"
            this[SettingsRepository.KEY_NOTIFICATIONS] = false
        }
        repo.setIncomeBudget(17, 123.5)
        val saved = repo.settings.first()
        assertEquals(ThemeMode.DARK, saved.themeMode)
        assertEquals(HomeScreenTab.ACCOUNTS, saved.homeScreen)
        assertEquals("EUR", saved.defaultCurrency)
        assertFalse(saved.notificationsEnabled)
        assertEquals(17L, saved.incomeBudgetAccountId)
        assertEquals(123.5, saved.expectedMonthlyIncome, 0.0)
        repo.update {
            this[SettingsRepository.KEY_THEME_MODE] = "obsolete"
            this[SettingsRepository.KEY_HOME_SCREEN] = "obsolete"
        }
        assertEquals(ThemeMode.SYSTEM, repo.settings.first().themeMode)
        assertEquals(HomeScreenTab.CATEGORIES, repo.settings.first().homeScreen)
        assertEquals("EUR", repo.settings.first().defaultCurrency)
    }

    @Test fun rates_refresh_cache_freshness_and_network_failure() = runTest {
        val ds = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            temp.root.resolve("rates.preferences_pb")
        }
        var calls = 0
        var fail = false
        val repo = CurrencyRatesRepository(ds) {
            calls++
            if (fail) throw IOException("offline")
            """[{"cc":"USD","rate":41.5},{"cc":"EUR","rate":0},{"cc":"","rate":12}]"""
        }
        assertEquals(mapOf("UAH" to 1.0), repo.rates.first())
        assertEquals(0L, repo.lastUpdated.first())
        repo.refreshIfStale()
        assertEquals(mapOf("UAH" to 1.0, "USD" to 41.5), repo.rates.first())
        val updated = repo.lastUpdated.first()
        assertTrue(updated > 0)
        repo.refreshIfStale()
        assertEquals(1, calls)
        fail = true
        repo.refreshIfStale(-1)
        assertEquals(2, calls)
        assertEquals(updated, repo.lastUpdated.first())
        assertEquals(41.5, repo.rates.first()["USD"]!!, 0.0)
        ds.edit { it[stringPreferencesKey("rates_json")] = "broken" }
        assertEquals(mapOf("UAH" to 1.0), repo.rates.first())
    }
}
