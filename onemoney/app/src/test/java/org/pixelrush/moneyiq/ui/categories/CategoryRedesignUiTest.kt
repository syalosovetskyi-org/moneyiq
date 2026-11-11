package org.syalosovetskyi.onemoney.ui.categories

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.syalosovetskyi.onemoney.data.db.entities.*
import org.syalosovetskyi.onemoney.ui.theme.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "uk")
class CategoryRedesignUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun addIsFourthCellAlongsideThreeCategories() {
        var added = false
        compose.setContent {
            MaterialTheme { OneMoneyThemeProvider {
                Box(Modifier.requiredWidth(412.dp).height(600.dp)) {
                    CategoriesGridContent(categories = (1L..3L).map { category(it) },
                        spending = emptyMap(), totalExpense = 0.0, totalIncome = 0.0,
                        selectedTab = 0, onToggleTab = {}, bottomPadding = 0.dp,
                        onChipClick = {}, onAdd = { added = true }, sortBySpending = false)
                }
            } }
        }
        compose.onNodeWithTag("category_grid_4").assertExists()
        val last = compose.onNodeWithText("Категорія 3").fetchSemanticsNode().boundsInRoot
        val add = compose.onNodeWithTag("new_category_cell").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(add.left > last.left)
        assertTrue(add.top < last.bottom)
        compose.onNodeWithText("Нова категорія").performClick()
        compose.runOnIdle { assertTrue(added) }
    }

    @Test fun foldUsesSixColumnsAndLargeFontDoesNotClipAmounts() {
        compose.setContent {
            MaterialTheme { OneMoneyThemeProvider {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    Box(Modifier.requiredWidth(673.dp).height(900.dp)) {
                        CategoriesGridContent(categories = (1L..6L).map { category(it) },
                            spending = mapOf(1L to 1234.0), totalExpense = 1234.0, totalIncome = 0.0,
                            selectedTab = 0, onToggleTab = {}, bottomPadding = 0.dp,
                            onChipClick = {}, onAdd = {}, sortBySpending = false)
                    }
                }
            } }
        }
        compose.onNodeWithTag("category_grid_6").assertExists()
        val first = compose.onNodeWithText("Категорія 1").fetchSemanticsNode().boundsInRoot
        val sixth = compose.onNodeWithText("Категорія 6").fetchSemanticsNode().boundsInRoot
        assertTrue(sixth.left > first.left)
        assertEquals(first.top, sixth.top, 1f)
        compose.onNodeWithText("Категорія 6").assertIsDisplayed()
        val cell = compose.onNodeWithTag("category_cell_6").fetchSemanticsNode().boundsInRoot
        val amount = compose.onNodeWithTag("category_amount_6", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Amount fits grown cell", amount.bottom <= cell.bottom)
        assertTrue("Cell grows beyond 132dp at 2x font scale", cell.height > 132f)
        compose.onNodeWithContentDescription("Витрати: ${org.syalosovetskyi.onemoney.util.formatMoney(1234.0)} ₴", substring = false).assertExists()
    }

    @Test fun fabAndSegmentSwitchBetweenExpenseAndIncome() {
        var tab by androidx.compose.runtime.mutableStateOf(0)
        var clicks = 0
        compose.setContent {
            MaterialTheme { OneMoneyThemeProvider {
                Column {
                    CategoryTypeSegment(tab) { tab = it }
                    CategoryQuickAddButton(tab) { clicks++ }
                }
            } }
        }
        compose.onNodeWithText("Додати витрату").assertIsDisplayed().performClick()
        compose.onNodeWithText("Доходи").performClick()
        compose.onNodeWithText("Додати дохід").assertIsDisplayed().performClick()
        compose.onNodeWithTag("quick_add_transaction").assertHeightIsEqualTo(56.dp)
        compose.runOnIdle { assertEquals(2, clicks); assertEquals(1, tab) }
    }

    private fun category(id: Long) = CategoryEntity(id = id, name = "Категорія $id",
        type = TransactionType.EXPENSE, colorHex = "#FF5722", icon = "category")
}
