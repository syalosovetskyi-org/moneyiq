package org.syalosovetskyi.onemoney.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme
import org.syalosovetskyi.onemoney.ui.theme.Spacing

/**
 * Сітка категорій з FAB «Додати витрату/дохід» поверх неї (BottomEnd), як на головному екрані.
 * Висоту, яку FAB займає від нижнього краю контейнера (сам FAB + [fabBottomPadding]), міряє
 * onSizeChanged і віддає в [grid] — сітка робить з неї нижній відступ (serg/tasks#2673).
 * Стан висоти тут, а не в сітці: CategoriesGridContent лишається stateless, тест збирає той самий вузол.
 */
@Composable
internal fun CategoriesGridWithQuickAdd(
    selectedTab:      Int,
    fabBottomPadding: Dp,
    onQuickAdd:       () -> Unit,
    modifier:         Modifier = Modifier,
    fabEndPadding:    Dp = Spacing.lg,
    grid:             @Composable (fabOverlap: Dp?) -> Unit,
) {
    val density = LocalDensity.current
    var fabOverlap by remember { mutableStateOf<Dp?>(null) }
    Box(modifier) {
        grid(fabOverlap)
        CategoryQuickAddButton(selectedTab, Modifier.align(Alignment.BottomEnd)
            // onSizeChanged ДО padding: розмір включає нижній відступ FAB, тобто відстань від низу Box до верху FAB
            .onSizeChanged { size -> fabOverlap = with(density) { size.height.toDp() } }
            .padding(end = fabEndPadding, bottom = fabBottomPadding), onClick = onQuickAdd)
    }
}

@Composable
internal fun CategoryQuickAddButton(selectedTab: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick, modifier = modifier.height(56.dp).testTag("quick_add_transaction"),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        icon = { Icon(Icons.Default.Add, null) },
        text = { Text(stringResource(if (selectedTab == 0) R.string.categories_add_expense else R.string.categories_add_income)) }
    )
}

@Composable
internal fun CategoryTypeSegment(
    selectedTab: Int,
    modifier: Modifier = Modifier,
    /** false — сегменты по ширине подписи (верхняя панель): фиксированная ширина ломала
     *  «Витрати» по буквам на Fold и при крупном шрифте (serg/tasks#2673) */
    equalWidth: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    Row(modifier.heightIn(min = 44.dp)
        .clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
        .selectableGroup()) {
        listOf(R.string.common_expenses, R.string.common_incomes).forEachIndexed { index, label ->
            Box((if (equalWidth) Modifier.weight(1f) else Modifier.widthIn(min = 88.dp))
                .heightIn(min = 44.dp).clip(RoundedCornerShape(50))
                .background(if (selectedTab != index) MaterialTheme.colorScheme.surfaceVariant
                    else if (index == 0) OneMoneyTheme.colors.expenseSoft else OneMoneyTheme.colors.incomeSoft)
                .selectable(selected = selectedTab == index, role = Role.Tab, onClick = { onSelect(index) })
                .padding(horizontal = Spacing.md, vertical = Spacing.sm), contentAlignment = Alignment.Center) {
                Text(stringResource(label), maxLines = 1, softWrap = false, color = if (selectedTab != index) OneMoneyTheme.colors.secondaryText
                    else if (index == 0) OneMoneyTheme.colors.expenseRed else OneMoneyTheme.colors.incomeGreen)
            }
        }
    }
}
