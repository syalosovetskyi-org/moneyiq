package org.syalosovetskyi.onemoney.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import org.syalosovetskyi.onemoney.ui.categories.categoryIconFor
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.util.formatBase

// ── Category detail bottom sheet ──────────────────────────────────────────────

@Composable
internal fun CategoryDetailSheet(
    cat:          OverviewCatRow,
    catColor:     Color,
    monthLabel:   String,
    onAddExpense: () -> Unit,
    onOperations: () -> Unit
) {
    // R8: з бюджетом прогрес і відсоток рахуються від бюджету, а перевитрата видна явно —
    // червона смуга й пілюля «перевитрата −X». Без бюджету — як раніше, частка від найбільшої категорії.
    val hasBudget = cat.budgetAmount > 0.0
    val overspend = if (hasBudget) cat.amount - cat.budgetAmount else 0.0
    val overspent = overspend > 0.0
    val progress  = if (hasBudget) (cat.amount / cat.budgetAmount).toFloat().coerceIn(0f, 1f) else cat.percent
    val percent   = if (hasBudget) Math.round(cat.amount / cat.budgetAmount * 100).toInt() else (cat.percent * 100).toInt()
    val errorColor = MaterialTheme.colorScheme.error
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Space for floating icon (top half in drag handle, bottom half in content)
            Spacer(Modifier.height(46.dp))

            // Category name (large, white)
            Text(
                cat.name,
                modifier   = Modifier.padding(horizontal = 20.dp),
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(10.dp))

            // Transaction count label + amount
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text     = if (cat.amount == 0.0) stringResource(R.string.ovr_no_operations) else stringResource(R.string.ovr_per_month),
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatBase(cat.amount),
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
            }

            Spacer(Modifier.height(8.dp))

            // Budget progress bar
            LinearProgressIndicator(
                progress   = { progress },
                modifier   = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(3.dp)
                    .clip(CircleShape),
                color      = if (overspent) errorColor else Color.White,
                trackColor = Color.White.copy(alpha = 0.25f)
            )

            Spacer(Modifier.height(6.dp))

            // "0%" label + month
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                )
                if (overspent) {
                    Surface(shape = CircleShape, color = errorColor) {
                        Text(
                            stringResource(R.string.budget_overspend) + " " + formatBase(-overspend),
                            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    monthLabel,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.weight(1f)
                )
                if (hasBudget) {
                    // Підпис, що це бюджет, а не ще одна сума витрат.
                    Text(
                        stringResource(R.string.ovr_in_budget) + " " + formatBase(cat.budgetAmount),
                        style      = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = Color.White
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Bottom action row ─────────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 32.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Витрата button
                Column(
                    modifier            = Modifier.clickable(onClick = onAddExpense),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE91E63).copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.ArrowDownward, null,
                            modifier = Modifier.size(26.dp),
                            tint     = Color(0xFFE91E63)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.tx_expense),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                    )
                }

                // Операції button
                Column(
                    modifier            = Modifier.clickable(onClick = onOperations),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF7E57C2).copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ReceiptLong, null,
                            modifier = Modifier.size(26.dp),
                            tint     = Color(0xFF7E57C2)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.nav_operations),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                    )
                }
            }
        }

        // ── Іконка категорії (виступає за верхній край) ──────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 20.dp)
                .offset(y = (-28).dp)
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, catColor.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = categoryIconFor(cat.icon),
                contentDescription = null,
                modifier           = Modifier.size(36.dp),
                tint               = catColor
            )
        }
    }
}
