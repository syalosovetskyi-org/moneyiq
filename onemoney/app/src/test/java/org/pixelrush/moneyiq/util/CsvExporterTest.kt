package org.syalosovetskyi.onemoney.util

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.syalosovetskyi.onemoney.data.db.dao.TransactionWithDetails
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import java.util.Locale

class CsvExporterTest {
    @get:Rule val folder = TemporaryFolder()

    private fun tx(note: String = "test", amount: Double = 1234.56, type: TransactionType = TransactionType.EXPENSE) =
        TransactionWithDetails(
            id = 1L, type = type, amount = amount,
            accountId = 1L, accountName = "Cash", accountColor = "#000000",
            accountCurrency = "EUR", toAccountId = null, toAccountName = null,
            categoryId = null, categoryName = null, categoryColor = null,
            categoryIcon = null, note = note, date = 0L
        )

    private val labels = CsvExporter.Labels(
        header = listOf("Дата", "Тип", "Сума", "Рахунок", "Рахунок зарахування", "Категорія", "Нотатка"),
        types = TransactionType.entries.associateWith { "T_${it.name}" },
    )

    private fun csv(locale: Locale, vararg rows: TransactionWithDetails): List<String> {
        val sb = StringBuilder()
        CsvExporter.writeCsv(sb, rows.toList(), labels, locale)
        return sb.toString().removePrefix("﻿").split("\r\n").filter { it.isNotEmpty() }
    }

    @Test
    fun `decimal comma locale uses semicolon and keeps amount in one column`() {
        val lines = csv(Locale.forLanguageTag("uk-UA"), tx())
        assertEquals("Дата;Тип;Сума;Рахунок;Рахунок зарахування;Категорія;Нотатка", lines[0])
        val row = lines[1].split(';')
        assertEquals(7, row.size)
        assertEquals("1234,56", row[2])
        assertEquals("T_EXPENSE", row[1])
    }

    @Test
    fun `decimal point locale uses comma separator`() {
        val row = csv(Locale.US, tx())[1].split(',')
        assertEquals(7, row.size)
        assertEquals("1234.56", row[2])
    }

    @Test
    fun `fields with separator quote or newline are quoted per RFC 4180`() {
        val row = csv(Locale.forLanguageTag("uk-UA"), tx(note = "кава; \"лате\""))[1]
        assertTrue(row, row.endsWith(";\"кава; \"\"лате\"\"\""))
        // A comma is not special when the separator is «;».
        assertTrue(csv(Locale.forLanguageTag("uk-UA"), tx(note = "a,b"))[1].endsWith(";a,b"))
    }

    @Test
    fun `file uses Zerno brand and interface strings`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val context = mockk<Context>()
            every { context.cacheDir } returns folder.root
            every { context.getString(any()) } answers { "s${firstArg<Int>()}" }
            val file = CsvExporter.buildCsvFile(context, listOf(tx()))
            assertTrue(file.name, file.name.startsWith("zerno_"))
            val lines = file.readLines()
            assertEquals(7, lines[0].removePrefix("﻿").split(';').size)
            val row = lines[1].split(';')
            assertEquals(7, row.size)
            assertEquals("1234,56", row[2])
        } finally {
            Locale.setDefault(previous)
        }
    }
}
