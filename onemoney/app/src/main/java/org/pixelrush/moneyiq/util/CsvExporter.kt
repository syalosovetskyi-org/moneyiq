package org.syalosovetskyi.onemoney.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.syalosovetskyi.onemoney.R
import org.syalosovetskyi.onemoney.data.db.dao.TransactionWithDetails
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import java.io.File
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

object CsvExporter {

    /** Column titles and transaction type names in the interface language. */
    internal data class Labels(
        val header: List<String>,
        val types: Map<TransactionType, String>,
    )

    /**
     * Експортує операції в CSV-файл і повертає Intent для поширення.
     */
    fun export(context: Context, transactions: List<TransactionWithDetails>): Intent {
        val file = buildCsvFile(context, transactions)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.csv_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    internal fun buildCsvFile(
        context: Context,
        transactions: List<TransactionWithDetails>
    ): File {
        val dir = File(context.cacheDir, "export").also { it.mkdirs() }
        val fileName = "zerno_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT).format(Date())}.csv"
        val file = File(dir, fileName)
        file.bufferedWriter(Charsets.UTF_8).use { out ->
            writeCsv(out, transactions, labels(context), Locale.getDefault())
        }
        return file
    }

    internal fun labels(context: Context) = Labels(
        header = listOf(
            R.string.csv_col_date, R.string.csv_col_type, R.string.csv_col_amount,
            R.string.csv_col_account, R.string.csv_col_to_account,
            R.string.csv_col_category, R.string.csv_col_note,
        ).map { context.getString(it) },
        types = mapOf(
            TransactionType.INCOME   to context.getString(R.string.csv_type_income),
            TransactionType.EXPENSE  to context.getString(R.string.csv_type_expense),
            TransactionType.TRANSFER to context.getString(R.string.csv_type_transfer),
            TransactionType.BORROW   to context.getString(R.string.csv_type_borrow),
            TransactionType.LEND     to context.getString(R.string.csv_type_lend),
            TransactionType.REPAY    to context.getString(R.string.csv_type_repay),
        ),
    )

    /**
     * Field separator for [locale]: spreadsheets in decimal-comma locales (uk, ru, de…)
     * expect «;» between columns, otherwise «,» — so the file opens in columns by
     * double-click, and amounts stay numbers there.
     */
    internal fun separatorFor(locale: Locale): Char =
        if (DecimalFormatSymbols.getInstance(locale).decimalSeparator == ',') ';' else ','

    internal fun writeCsv(
        out: Appendable,
        transactions: List<TransactionWithDetails>,
        labels: Labels,
        locale: Locale,
    ) {
        val sep = separatorFor(locale)
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
        val symbols = DecimalFormatSymbols.getInstance(locale)
        // BOM — щоб Excel розпізнав UTF-8
        out.append('﻿')
        out.append(labels.header.joinToString(sep.toString()) { it.csvEscape(sep) }).append("\r\n")
        transactions.forEach { tx ->
            // No grouping separator: «1 234,56» would be text, not a number, in a spreadsheet.
            val amount = "%.2f".format(Locale.ROOT, tx.amount).replace('.', symbols.decimalSeparator)
            val cols = listOf(
                dateFmt.format(Date(tx.date)),
                labels.types[tx.type] ?: tx.type.name,
                amount, // number we produce ourselves — never neutralised
                tx.accountName.neutralizeFormula(),
                (tx.toAccountName ?: "").neutralizeFormula(),
                (tx.categoryName ?: "").neutralizeFormula(),
                tx.note.neutralizeFormula(),
            )
            out.append(cols.joinToString(sep.toString()) { it.csvEscape(sep) }).append("\r\n")
        }
    }

    /**
     * CSV/formula injection: user text such as «=HYPERLINK(...)» would run as a
     * formula when the file is opened in Excel/Sheets. A leading apostrophe makes
     * the spreadsheet treat it as plain text (OWASP CSV Injection).
     */
    internal fun String.neutralizeFormula(): String =
        if (isNotEmpty() && this[0] in FORMULA_PREFIXES) "'$this" else this

    private val FORMULA_PREFIXES = charArrayOf('=', '+', '-', '@', '\t', '\r')

    /** RFC 4180: quote a field that holds the separator, a quote or a line break. */
    internal fun String.csvEscape(sep: Char): String =
        if (indexOf(sep) >= 0 || contains('"') || contains('\n') || contains('\r')) {
            "\"${replace("\"", "\"\"")}\""
        } else this
}
