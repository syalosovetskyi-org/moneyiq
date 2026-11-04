package org.syalosovetskyi.onemoney.ui.reports

import org.syalosovetskyi.onemoney.util.parseColorHex
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.data.db.dao.CategorySpending
import org.syalosovetskyi.onemoney.util.formatMoney
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme

@Composable
fun ReportsScreen(
    onNavigateBack: () -> Unit = {},
    padding: PaddingValues = PaddingValues(),
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        // Период-навигация
        PeriodSelector(
            label = state.periodLabel,
            onPrev = viewModel::prevMonth,
            onNext = viewModel::nextMonth
        )

        val catItems = if (selectedTab == 0) state.expenseByCategory else state.incomeByCategory
        val catTotal = if (selectedTab == 0) state.expense else state.income

        LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
            // Сводные карточки
            item { SummaryCards(income = state.income, expense = state.expense) }

            // Вкладки + круговая диаграмма
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(OneMoneyTheme.dimens.largeRadius),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Color.Transparent
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text(stringResource(R.string.common_expenses)) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text(stringResource(R.string.common_incomes)) }
                            )
                        }
                        Spacer(Modifier.height(16.dp))

                        if (catItems.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.PieChart, null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.reports_no_data),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DonutChart(
                                    segments = catItems.map { cat ->
                                        val c = parseColorHex(cat.categoryColor, Color.Gray)
                                        c to if (catTotal > 0) (cat.total / catTotal).toFloat() else 0f
                                    },
                                    centerLabel = formatMoney(catTotal),
                                    modifier = Modifier.size(150.dp)
                                )
                                Spacer(Modifier.width(16.dp))
                                // Легенда
                                Column(modifier = Modifier.weight(1f)) {
                                    catItems.take(6).forEach { cat ->
                                        val c = parseColorHex(cat.categoryColor, Color.Gray)
                                        val pct = if (catTotal > 0) cat.total / catTotal * 100 else 0.0
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(c)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                cat.categoryName,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1
                                            )
                                            Text(
                                                "${String.format("%.0f", pct)}%",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Список категорий с прогресс-барами
            if (catItems.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.reports_by_category),
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(catItems.size) { i ->
                    CategorySpendingRow(cat = catItems[i], total = catTotal)
                }
            }
        }
    }
}

// ── Компоненты ─────────────────────────────────────────────────────────────────

@Composable
private fun DonutChart(
    segments: List<Pair<Color, Float>>,
    centerLabel: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = size.minDimension * 0.22f
            val radius = size.minDimension / 2f - strokeWidth / 2f
            val total = segments.sumOf { it.second.toDouble() }.toFloat()
            var startAngle = -90f

            if (total <= 0f) {
                drawArc(
                    color = Color.LightGray.copy(alpha = 0.3f),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    style = Stroke(width = strokeWidth),
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2)
                )
            } else {
                segments.forEach { (color, fraction) ->
                    val sweep = (360f * fraction) - 2f
                    if (sweep > 0f) {
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2)
                        )
                    }
                    startAngle += 360f * fraction
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                centerLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun PeriodSelector(label: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronLeft, stringResource(R.string.common_back)) }
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, stringResource(R.string.reports_next)) }
    }
}

@Composable
private fun SummaryCards(income: Double, expense: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryCard(stringResource(R.string.common_incomes), income, OneMoneyTheme.colors.incomeGreen, Modifier.weight(1f))
        SummaryCard(stringResource(R.string.common_expenses), expense, OneMoneyTheme.colors.expenseRed, Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    val diff = income - expense
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(OneMoneyTheme.dimens.cardRadius),
        colors = CardDefaults.cardColors(
            containerColor = if (diff >= 0) OneMoneyTheme.colors.incomeGreen.copy(alpha = 0.12f) else OneMoneyTheme.colors.expenseRed.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.reports_period_balance), style = MaterialTheme.typography.bodyMedium)
            Text(
                (if (diff >= 0) "+" else "") + formatMoney(diff),
                fontWeight = FontWeight.Bold,
                color = if (diff >= 0) OneMoneyTheme.colors.incomeGreen else OneMoneyTheme.colors.expenseRed
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SummaryCard(label: String, amount: Double, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(OneMoneyTheme.dimens.cardRadius),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text(
                formatMoney(amount),
                fontWeight = FontWeight.Bold,
                color = color,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun CategorySpendingRow(cat: CategorySpending, total: Double) {
    val color = parseColorHex(cat.categoryColor, Color.Gray)
    val percent = if (total > 0) (cat.total / total * 100).toFloat() else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Category, null, modifier = Modifier.size(16.dp), tint = color)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    cat.categoryName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatMoney(cat.total),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "${String.format("%.1f", percent)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = color,
            trackColor = color.copy(alpha = 0.1f)
        )
        Spacer(Modifier.height(8.dp))
    }
}
