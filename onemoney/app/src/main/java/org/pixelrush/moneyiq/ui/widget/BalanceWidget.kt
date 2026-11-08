package org.syalosovetskyi.onemoney.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.first
import org.syalosovetskyi.onemoney.data.db.ALL_MIGRATIONS
import org.syalosovetskyi.onemoney.MainActivity
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.data.db.AppDatabase
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme
import androidx.room.Room
import java.text.NumberFormat
import java.util.*

/**
 * 2×1 виджет — доходы / расходы за текущий месяц.
 */
class BalanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Прямой доступ к БД (без Hilt — Glance receiver не поддерживает @AndroidEntryPoint)
        val db = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "onemoney.db"
        )
            .addMigrations(*ALL_MIGRATIONS)
            .build()

        val (from, to) = monthRange()
        val totals = try {
            org.syalosovetskyi.onemoney.util.currencyTotals(db.transactionDao().getTransactionsByDateRange(from, to).first())
        } finally { db.close() }
        provideContent {
            Content(context, totals)
        }
    }

    @Composable
    private fun Content(context: Context, totals: Map<String, org.syalosovetskyi.onemoney.util.CurrencyTotal>) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        fun fmt(v: Double): String {
            val nf = NumberFormat.getNumberInstance(Locale.getDefault())
            nf.maximumFractionDigits = 0
            return nf.format(v)
        }

        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFF7C581C)))
                .clickable(actionStartActivity(intent))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Доходы
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = context.getString(R.string.widget_income),
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFA5D6A7)),
                        fontSize = 10.sp
                    )
                )
                Text(
                    text = totals.entries.joinToString(" · ") { (currency, total) -> "${fmt(total.income)} $currency" }.ifEmpty { "0 UAH" },
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFA5D6A7)),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // Разделитель
            Box(
                modifier = GlanceModifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(ColorProvider(Color.White.copy(alpha = 0.25f)))
            ) {}

            // Расходы
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = context.getString(R.string.widget_expense),
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFEF9A9A)),
                        fontSize = 10.sp
                    )
                )
                Text(
                    text = totals.entries.joinToString(" · ") { (currency, total) -> "${fmt(total.expense)} $currency" }.ifEmpty { "0 UAH" },
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFEF9A9A)),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }

    private fun monthRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val from = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        return from to cal.timeInMillis
    }
}

class BalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BalanceWidget()
}
