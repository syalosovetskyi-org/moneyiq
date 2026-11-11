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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme
import org.syalosovetskyi.onemoney.ui.theme.Spacing

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
internal fun CategoryTypeSegment(selectedTab: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(modifier.heightIn(min = 44.dp)
        .clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
        .selectableGroup()) {
        listOf(R.string.common_expenses, R.string.common_incomes).forEachIndexed { index, label ->
            Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(50))
                .background(if (selectedTab != index) MaterialTheme.colorScheme.surfaceVariant
                    else if (index == 0) OneMoneyTheme.colors.expenseSoft else OneMoneyTheme.colors.incomeSoft)
                .selectable(selected = selectedTab == index, role = Role.Tab, onClick = { onSelect(index) })
                .padding(Spacing.sm), contentAlignment = Alignment.Center) {
                Text(stringResource(label), color = if (selectedTab != index) OneMoneyTheme.colors.secondaryText
                    else if (index == 0) OneMoneyTheme.colors.expenseRed else OneMoneyTheme.colors.incomeGreen)
            }
        }
    }
}
