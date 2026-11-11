package org.syalosovetskyi.onemoney.ui.categories

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.ui.theme.*

class CategoryRedesignTest {
    @Test fun columnsFollowWindowWidthIncludingBoundary() {
        assertEquals(4, categoryColumnCount(320.dp))
        assertEquals(4, categoryColumnCount(599.dp))
        assertEquals(6, categoryColumnCount(600.dp))
        assertEquals(6, categoryColumnCount(673.dp))
    }

    @Test fun cellsFollowReferenceAndReserveCenterForRing() {
        for (columns in listOf(4, 6)) {
            for (categoryCount in listOf(0, 1, 3, 4, 8, 12, 17, 40)) {
                val cells = categoryGridCells(columns, categoryCount + 1)
                assertEquals(categoryCount + 1, cells.distinct().size)
                assertTrue(cells.all { it.column in 0 until columns })
                assertTrue(cells.none { it.row in 1..2 && it.column in (columns / 2 - 1)..(columns / 2) })
                assertTrue(cells.zipWithNext().all { (a, b) -> a.row < b.row || a.column < b.column })
            }
        }
        // Twelve categories plus Add: matches the supplied S23/Fold screenshots.
        assertEquals(CategoryGridCell(0, 4), categoryGridCells(4, 13).last())
        assertEquals(CategoryGridCell(4, 2), categoryGridCells(6, 13).last())
        // Three categories: Add shares their first row, never a separate tail row.
        assertEquals(CategoryGridCell(3, 0), categoryGridCells(4, 4).last())
    }

    @Test fun knownAndGeneratedCirclesMeetBothContrastRequirements() {
        for (dark in listOf(false, true)) {
            val surface = if (dark) Color(0xFF1C1F24) else Color.White
            val background = if (dark) Color(0xFF14161A) else Color(0xFFF6F7F8)
            val known = CategoryScreenTokens.byName.keys.map {
                CategoryScreenTokens.resolve(it, TransactionType.EXPENSE, Color.Red, false, dark)
            }
            val generated = (0 until 360 step 5).flatMap { hue ->
                listOf(0f, .2f, .7f, 1f).map { saturation ->
                    CategoryScreenTokens.resolve("Imported", TransactionType.EXPENSE,
                        Color.hsv(hue.toFloat(), saturation, .8f), false, dark)
                }
            }
            for (style in known + generated) {
                assertTrue("glyph contrast $style", contrast(style.circleBg, style.iconTint) >= 4.5f)
                assertTrue("surface contrast $style", contrast(style.circleBg, surface) >= 3f)
                assertTrue("background contrast $style", contrast(style.circleBg, background) >= 3f)
                assertEquals(1f, style.circleBg.alpha, 0f)
                assertEquals(if (dark) .33f else .27f, style.circleBg.luminance(), .004f)
            }
        }
        assertEquals(Color(0xFF26A345), CategoryScreenTokens.byName.getValue("Продукти").circleBg)
    }

    @Test fun sharedTypographyHasNoSizeBelowTwelveSp() {
        for (typo in listOf(OneMoneyLightTokens.typography, OneMoneyDarkTokens.typography)) {
            val styles = listOf(typo.categoryTitle, typo.categoryTopAmount, typo.categoryBottomAmount,
                typo.centerTitle, typo.centerAmount, typo.subcategoryTitle, typo.subcategoryAmount,
                typo.listItemTitle, typo.listItemAmount, typo.listItemSub, typo.sheetTitle,
                typo.topBarLabel, typo.topBarBalance)
            assertTrue(styles.all { it.fontSize.value >= 12f })
        }
    }

    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
}
