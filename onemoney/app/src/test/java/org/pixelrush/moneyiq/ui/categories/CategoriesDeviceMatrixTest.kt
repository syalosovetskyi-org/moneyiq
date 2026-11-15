package org.syalosovetskyi.onemoney.ui.categories

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyThemeProvider
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.workers.MonoFlowScenarioHarness as H

/**
 * Матриця «пристрій x масштаб шрифту» для головного екрана «Категорії» (serg/tasks#2673).
 * Корені беремо зі сценарію fresh (seedDefaults + синк фікстури). Знімки пишуться лише
 * при -Proborazzi.test.record=true; порівняння з еталоном поки не вмикаємо.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class, qualifiers = "uk")
class CategoriesDeviceMatrixTest {
    @get:Rule val compose = createComposeRule()

    // S23
    @Config(qualifiers = "+w384dp-h832dp-port-xxhdpi")
    @Test fun s23_font100() = shoot("s23", 1.0f)
    @Config(qualifiers = "+w384dp-h832dp-port-xxhdpi")
    @Test fun s23_font130() = shoot("s23", 1.3f)

    // Fold закритий (зовнішній екран)
    @Config(qualifiers = "+w369dp-h905dp-port-xxhdpi")
    @Test fun foldClosed_font100() = shoot("fold_closed", 1.0f)
    @Config(qualifiers = "+w369dp-h905dp-port-xxhdpi")
    @Test fun foldClosed_font130() = shoot("fold_closed", 1.3f)

    // Fold відкритий «книжкою» (портрет)
    @Config(qualifiers = "+w690dp-h829dp-port-xxhdpi")
    @Test fun foldBook_font100() = shoot("fold_book", 1.0f)
    @Config(qualifiers = "+w690dp-h829dp-port-xxhdpi")
    @Test fun foldBook_font130() = shoot("fold_book", 1.3f)

    // Fold відкритий горизонтально
    @Config(qualifiers = "+w829dp-h690dp-land-xxhdpi")
    @Test fun foldLandscape_font100() = shoot("fold_land", 1.0f)
    @Config(qualifiers = "+w829dp-h690dp-land-xxhdpi")
    @Test fun foldLandscape_font130() = shoot("fold_land", 1.3f)

    private fun freshExpenseRoots(): List<CategoryEntity> {
        val db = H.newDb(ApplicationProvider.getApplicationContext())
        try {
            return runBlocking {
                H.fresh(db)
                H.visibleRoots(db.categoryDao().getAllCategoriesOnce(), TransactionType.EXPENSE)
            }
        } finally {
            db.close()
        }
    }

    private fun shoot(device: String, fontScale: Float) {
        val roots = freshExpenseRoots()
        assertEquals("корені сценарію fresh мають збігатись з еталонним набором",
            H.EXPENSE_ROOTS, roots.map { it.name }.toSet())

        compose.setContent {
            MaterialTheme { OneMoneyThemeProvider {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        // Верхня панель MainScreen: баланс + сегмент за шириною підпису
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("12 345 ₴", modifier = Modifier.weight(1f, fill = false))
                            Box(Modifier.padding(start = 8.dp).testTag(TAG_TOP)) {
                                CategoryTypeSegment(selectedTab = 0, equalWidth = false, onSelect = {})
                            }
                        }
                        // Автономний екран категорій: сегмент на всю ширину
                        Box(Modifier.testTag(TAG_FULL)) {
                            CategoryTypeSegment(0, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                .fillMaxWidth(), onSelect = {})
                        }
                        // Сітка + FAB поверх неї — той самий вузол, що й у CategoriesScreen
                        // (embedded, нижній інсет 0): FAB міряється, відступ сітки — від нього.
                        CategoriesGridWithQuickAdd(selectedTab = 0, fabBottomPadding = Spacing.lg,
                            onQuickAdd = {}, modifier = Modifier.weight(1f).fillMaxWidth()) { fabOverlap ->
                            CategoriesGridContent(categories = roots, spending = emptyMap(),
                                totalExpense = 0.0, totalIncome = 0.0, selectedTab = 0,
                                onToggleTab = {}, bottomPadding = 0.dp, onChipClick = {},
                                onAdd = {}, sortBySpending = false, fabOverlap = fabOverlap)
                        }
                    }
                }
            } }
        }

        // Кожен корінь видно рівно один раз
        for (root in roots) compose.onAllNodesWithText(root.name).assertCountEquals(1)

        // Підписи сегмента — в один рядок, в обох місцях
        for (tag in listOf(TAG_TOP, TAG_FULL)) for (label in listOf("Витрати", "Доходи")) {
            val lines = textLayout(tag, label).lineCount
            assertEquals("«$label» ($tag, $device, шрифт $fontScale) має бути в один рядок", 1, lines)
        }

        // FAB
        compose.onNodeWithTag("quick_add_transaction").assertExists()
        compose.onNodeWithText("Додати витрату", useUnmergedTree = true).assertExists()

        val shot = "categories_${device}_${"%.1f".format(java.util.Locale.ROOT, fontScale)}"
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$shot.png")

        // Підписи клітинок: перенос лише між словами, однослівні — в один рядок без еліпсиса
        val newLabel = ApplicationProvider.getApplicationContext<Application>().getString(R.string.categories_new)
        val labels = roots.map { "category_cell_${it.id}" to it.name } + (TAG_NEW_CELL to newLabel)
        for ((cellTag, label) in labels) assertWordsWhole(textLayout(cellTag, label), "$label ($device, шрифт $fontScale)")

        // FAB не перекриває клітинки, коли сітку докручено до кінця
        assertFabClearOfCells(device, fontScale, expectedCells = roots.size + 1)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${shot}_end.png")
    }

    private fun assertWordsWhole(layout: TextLayoutResult, what: String) {
        val text = layout.layoutInput.text.text
        val words = Regex("\\S+").findAll(text).toList()
        for (word in words) {
            val first = layout.getLineForOffset(word.range.first)
            val last = layout.getLineForOffset(word.range.last)
            assertEquals("слово «${word.value}» у «$what» розірване між рядками $first і $last", first, last)
        }
        if (words.size == 1) {
            assertEquals("однослівна «$what» має бути в один рядок", 1, layout.lineCount)
            assertFalse("«$what» обрізана еліпсисом", layout.isLineEllipsized(0))
        }
    }

    private fun assertFabClearOfCells(device: String, fontScale: Float, expectedCells: Int) {
        val grid = compose.onNode(tagStartsWith("category_grid_"))
        grid.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy -> scrollBy(0f, 1_000_000f) }
        compose.waitForIdle()
        val range = checkNotNull(grid.fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)) {
            "у сітки немає VerticalScrollAxisRange"
        }
        assertEquals("сітку ($device, шрифт $fontScale) не докручено до кінця", range.maxValue(), range.value(), 0.5f)

        val fab = compose.onNodeWithTag("quick_add_transaction").fetchSemanticsNode().boundsInRoot
        val cells = compose.onAllNodes(tagStartsWith("category_cell_") or hasTestTag(TAG_NEW_CELL)).fetchSemanticsNodes()
        assertEquals("клітинки категорій + «Нова категорія»", expectedCells, cells.size)
        for (cell in cells) {
            val tag = cell.config.getOrNull(SemanticsProperties.TestTag)
            assertFalse("FAB $fab перекриває клітинку $tag ${cell.boundsInRoot} ($device, шрифт $fontScale)",
                cell.boundsInRoot.overlaps(fab))
        }
        // Останній ряд — щонайменше Spacing.md над верхом FAB (запас 1 px на округлення)
        val cellsBottom = compose.onNodeWithTag(TAG_CATEGORY_CELLS).fetchSemanticsNode().boundsInRoot.bottom
        val gap = with(compose.density) { Spacing.md.toPx() }
        assertTrue("між останнім рядом ($cellsBottom px) і FAB (${fab.top} px) має бути ≥ Spacing.md ($gap px)" +
            " ($device, шрифт $fontScale)", fab.top - cellsBottom >= gap - 1f)
    }

    private fun tagStartsWith(prefix: String) = SemanticsMatcher("testTag починається з $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    private fun textLayout(containerTag: String, label: String): TextLayoutResult {
        val node = compose.onNode(
            hasText(label) and hasAnyAncestor(hasTestTag(containerTag)), useUnmergedTree = true
        ).fetchSemanticsNode()
        val action = checkNotNull(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action) {
            "у «$label» ($containerTag) немає GetTextLayoutResult"
        }
        val results = mutableListOf<TextLayoutResult>()
        compose.runOnUiThread { assertTrue("GetTextLayoutResult не віддав розмітку", action(results)) }
        return results.first()
    }

    private companion object {
        const val TAG_TOP = "segment_top"
        const val TAG_FULL = "segment_full"
        const val TAG_NEW_CELL = "new_category_cell"
    }
}
