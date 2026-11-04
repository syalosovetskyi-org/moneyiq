package org.syalosovetskyi.onemoney.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.syalosovetskyi.onemoney.data.db.dao.TransactionWithDetails
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object CsvExporter {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    /**
     * Экспортирует транзакции в CSV-файл и возвращает Intent для шаринга.
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
            putExtra(Intent.EXTRA_SUBJECT, "onemoney — экспорт транзакций")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    internal fun buildCsvFile(
        context: Context,
        transactions: List<TransactionWithDetails>
    ): File {
        val dir = File(context.cacheDir, "export").also { it.mkdirs() }
        val fileName = "onemoney_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.csv"
        val file = File(dir, fileName)

        file.bufferedWriter(Charsets.UTF_8).use { out ->
            // BOM для корректного открытия в Excel
            out.write("﻿")
            // Заголовок
            out.write("Дата,Тип,Сумма,Счёт,Счёт назначения,Категория,Заметка\n")
            transactions.forEach { tx ->
                val cols = listOf(
                    dateFmt.format(Date(tx.date)),
                    tx.type.label(),
                    "%.2f".format(Locale.ROOT, tx.amount),
                    tx.accountName.csvEscape(),
                    (tx.toAccountName ?: "").csvEscape(),
                    (tx.categoryName ?: "").csvEscape(),
                    tx.note.csvEscape()
                )
                out.write(cols.joinToString(","))
                out.write("\n")
            }
        }
        return file
    }

    private fun TransactionType.label() = when (this) {
        TransactionType.INCOME   -> "Доход"
        TransactionType.EXPENSE  -> "Расход"
        TransactionType.TRANSFER -> "Перевод"
        TransactionType.BORROW   -> "Взять в долг"
        TransactionType.LEND     -> "Дать в долг"
        TransactionType.REPAY    -> "Вернуть долг"
    }

    /** Экранирование для CSV: кавычки вокруг строк с запятой/кавычкой/переносом. */
    private fun String.csvEscape(): String {
        return if (contains(',') || contains('"') || contains('\n')) {
            "\"${replace("\"", "\"\"")}\""
        } else this
    }
}
