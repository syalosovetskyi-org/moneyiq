package org.syalosovetskyi.onemoney.ui.shots

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.syalosovetskyi.onemoney.BuildConfig

/**
 * Спільна основа параметризованих тестів знімків. Підклас задає @RunWith/@Config/@GraphicsMode
 * і статичний @Parameters; кожен кейс — окремий тест, падіння одного не зупиняє решту.
 */
abstract class ShotTestBase(private val case: ShotCase) {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ShotEnvRule(case.config)).around(composeRule)

    @Test
    fun shot() {
        // Обидва flavor'и пишуть в одну теку build/outputs/roborazzi; другий прогін лише перезаписав би
        // PNG і задвоїв findings.tsv. Пропуск видно у звіті JUnit як skipped, а не як «пройдено».
        Assume.assumeTrue("матриця знімків пишеться лише у flavor personal, тут ${BuildConfig.FLAVOR}",
            BuildConfig.FLAVOR == "personal")
        ShotRunner(composeRule, case).run()
    }
}

fun str(@StringRes id: Int): String = ApplicationProvider.getApplicationContext<Application>().getString(id)
