@file:OptIn(ExperimentalRoborazziApi::class)

package org.syalosovetskyi.onemoney.ui.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performSemanticsAction
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyThemeProvider
import org.syalosovetskyi.onemoney.ui.theme.onemoneyTheme
import java.io.File
import java.util.Locale

/**
 * Матриця знімків усіх екранів / шторок / діалогів (serg/tasks#3037).
 *
 * Ім'я знімка: `<screen>_<state>_<device>_<font>_<theme>.png`; усередині частин — лише дефіси,
 * щоб ім'я однозначно ділилось по `_`. Знімок після докрутки має стан `<state>-end`.
 */
enum class ShotDevice(val id: String, val qualifiers: String) {
    S23("s23", "+w384dp-h832dp-port-xxhdpi"),
    FOLD_CLOSED("fold-closed", "+w369dp-h905dp-port-xxhdpi"),
    FOLD_BOOK("fold-book", "+w690dp-h829dp-port-xxhdpi"),
    FOLD_LAND("fold-land", "+w829dp-h690dp-land-xxhdpi"),
}

data class ShotConfig(val device: ShotDevice, val fontScale: Float, val dark: Boolean) {
    val fontLabel: String get() = "%.1f".format(Locale.ROOT, fontScale)
    val themeLabel: String get() = if (dark) "dark" else "light"
    val suffix: String get() = "${device.id}_${fontLabel}_$themeLabel"
}

object ShotMatrix {
    private val fonts = listOf(1.0f, 1.3f)
    private val dark = listOf(
        ShotConfig(ShotDevice.S23, 1.0f, dark = true),
        ShotConfig(ShotDevice.FOLD_CLOSED, 1.3f, dark = true),
    )

    /** Повноекранні стани: 4 пристрої × 2 шрифти світла + 2 темні. */
    val screens: List<ShotConfig> =
        ShotDevice.entries.flatMap { d -> fonts.map { ShotConfig(d, it, dark = false) } } + dark

    /** Шторки й діалоги: лише S23 і Fold закритий (обидва шрифти) + ті самі 2 темні. */
    val sheets: List<ShotConfig> =
        listOf(ShotDevice.S23, ShotDevice.FOLD_CLOSED).flatMap { d -> fonts.map { ShotConfig(d, it, dark = false) } } + dark

    /** Порожні стани — лише S23 × 1.0, світла. */
    val empty: List<ShotConfig> = listOf(ShotConfig(ShotDevice.S23, 1.0f, dark = false))
}

/**
 * Що знімаємо: вміст + необов'язкова дія перед знімком (клік по вкладці тощо).
 *
 * [pausedClock] — для вмісту з нескінченною анімацією (сфокусоване поле в AlertDialog: блимання
 * курсора — `delay` на тестовому годиннику). З autoAdvance=true waitForIdle крутить годинник
 * без кінця і падає AppNotIdleException через 60 с. Тут годинник зупинено, час просуваємо вручну
 * на [ShotRunner.SETTLE_MS] (рівно, тож знімок детермінований), а waitForIdle чекає лише
 * рекомпозиції/розмітки — аудит працює як для решти.
 */
class ShotSpec(
    val screen: String,
    val state: String,
    val prepare: (ComposeContentTestRule) -> Unit = {},
    val pausedClock: Boolean = false,
    val content: @Composable () -> Unit,
)

/** Один параметр тесту; toString — ім'я кейсу в звіті JUnit. */
class ShotCase(val spec: ShotSpec, val config: ShotConfig) {
    val stem: String get() = "${spec.screen}_${spec.state}_${config.suffix}"
    val endStem: String get() = "${spec.screen}_${spec.state}-end_${config.suffix}"
    override fun toString(): String = stem

    companion object {
        fun expand(specs: List<ShotSpec>, configs: List<ShotConfig>): List<Array<Any>> =
            specs.flatMap { s -> configs.map { arrayOf<Any>(ShotCase(s, it)) } }
    }
}

/**
 * Зовнішнє правило: пристрій і масштаб шрифту виставляються ДО запуску activity compose-правила.
 * Масштаб шрифту — через конфігурацію, а не LocalDensity: шторки (ModalBottomSheet) і діалоги
 * живуть у власних вікнах, і їхній AndroidComposeView перевизначає LocalDensity своїм.
 */
class ShotEnvRule(private val config: ShotConfig) : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            RuntimeEnvironment.setQualifiers(config.device.qualifiers)
            RuntimeEnvironment.setFontScale(config.fontScale)
            try {
                base.evaluate()
            } finally {
                RuntimeEnvironment.setFontScale(1.0f)
            }
        }
    }
}

/** Тема Zerno як у MainActivity: onemoneyTheme + токени OneMoney, без кастомного акценту. */
@Composable
fun ShotTheme(dark: Boolean, content: @Composable () -> Unit) {
    onemoneyTheme(darkTheme = dark, accentColor = null) {
        OneMoneyThemeProvider(darkTheme = dark) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
        }
    }
}

/** Знахідки аудиту — append у build/outputs/roborazzi/findings.tsv (робоча тека тесту — app/). */
object Findings {
    const val DIR = "build/outputs/roborazzi"
    private val file = File(DIR, "findings.tsv")
    private const val HEADER = "снимок\tтип\tтекст_или_tag\tbounds\n"

    @Synchronized
    fun add(shot: String, type: String, text: String, bounds: String) {
        file.parentFile?.mkdirs()
        val head = if (!file.exists()) HEADER else ""
        file.appendText(head + listOf(shot, type, text, bounds).joinToString("\t") { clean(it) } + "\n", Charsets.UTF_8)
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').take(300)
}

/**
 * Прогін одного кейсу: рендер → знімок → аудит → докрутка до кінця → знімок → аудит.
 * Помилку рендера пишемо як `render-error`, помилку аудиту — як `audit-error`, і в обох
 * випадках перекидаємо далі: тест падає, решта кейсів іде своїм ходом.
 */
class ShotRunner(private val compose: ComposeContentTestRule, private val case: ShotCase) {

    fun run() {
        val spec = case.spec
        try {
            // Фікстуру (in-memory Room) вантажимо до composition, а не всередині неї.
            ShotData.categories
            ShotData.transactions
            if (spec.pausedClock) compose.mainClock.autoAdvance = false
            compose.setContent { ShotTheme(case.config.dark) { spec.content() } }
            settle()
            spec.prepare(compose)
            settle()
            capture(case.stem)
        } catch (t: Throwable) {
            Findings.add("${case.stem}.png", "render-error", describe(t), "")
            throw t
        }
        audit(case.stem)

        val scrolled = try {
            scrollAllToEnd()
        } catch (t: Throwable) {
            Findings.add("${case.endStem}.png", "render-error", "докрутка: ${describe(t)}", "")
            throw t
        }
        if (scrolled) {
            try {
                capture(case.endStem)
            } catch (t: Throwable) {
                Findings.add("${case.endStem}.png", "render-error", describe(t), "")
                throw t
            }
            audit(case.endStem)
        }
    }

    /**
     * Звичайний кейс — waitForIdle (годинник крутиться сам до кінця анімацій). Кейс з
     * [ShotSpec.pausedClock] — фіксований крок часу, потім waitForIdle, який при
     * autoAdvance=false годинник не чіпає і чекає лише рекомпозицію/розмітку.
     */
    private fun settle() {
        if (case.spec.pausedClock) compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
    }

    private fun capture(stem: String) {
        compose.waitForIdle()
        // captureScreenRoboImage знімає ВСІ вікна (activity + діалоги/шторки), на відміну від
        // onRoot().captureRoboImage(), який бачить лише один корінь і падає, коли їх два.
        // Без -Proborazzi.test.record=true Roborazzi PNG не пише — це його штатна поведінка.
        captureScreenRoboImage("${Findings.DIR}/$stem.png")
    }

    private fun audit(stem: String) {
        try {
            LayoutAudit(compose, "$stem.png", ShotData.latinAllowList).run()
        } catch (t: Throwable) {
            Findings.add("$stem.png", "audit-error", describe(t), "")
            throw t
        }
    }

    /** Докручує кожен вертикальний скрол (більший — першим) до кінця. true — хоч щось зрушило. */
    private fun scrollAllToEnd(): Boolean {
        val matcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
            SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)
        val all = compose.onAllNodes(matcher, useUnmergedTree = true)
        val count = all.fetchSemanticsNodes(atLeastOneRootRequired = false).size
        var moved = false
        for (i in 0 until count) {
            val node = all[i]
            repeat(MAX_SCROLL_STEPS) {
                val range = node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                    ?: return@repeat
                if (range.maxValue() <= 0f || range.value() >= range.maxValue() - 0.5f) return@repeat
                node.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1_000_000f) }
                settle()
                moved = true
            }
        }
        return moved
    }

    private fun describe(t: Throwable) = "${t::class.java.simpleName}: ${t.message ?: "(без повідомлення)"}"

    companion object {
        private const val MAX_SCROLL_STEPS = 10
        /**
         * Крок часу для [ShotSpec.pausedClock] на кожен settle(): вистачає на появу діалогу й
         * анімацію мітки поля (~150 мс). Сумарний час до знімка фіксований (2 кроки = 800 мс), тож
         * фаза блимання курсора на знімку однакова від прогону до прогону.
         */
        const val SETTLE_MS = 400L
    }
}
