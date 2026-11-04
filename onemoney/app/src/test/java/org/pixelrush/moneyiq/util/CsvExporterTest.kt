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

    @Test
    fun `decimal comma locale exports amount as one column`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val context = mockk<Context>()
            every { context.cacheDir } returns folder.root
            val tx = TransactionWithDetails(
                id = 1L, type = TransactionType.EXPENSE, amount = 1234.56,
                accountId = 1L, accountName = "Cash", accountColor = "#000000",
                accountCurrency = "EUR", toAccountId = null, toAccountName = null,
                categoryId = null, categoryName = null, categoryColor = null,
                categoryIcon = null, note = "test", date = 0L
            )
            val row = CsvExporter.buildCsvFile(context, listOf(tx)).readLines()[1]
            assertEquals(7, row.split(',').size)
            assertTrue(row.contains(",1234.56,"))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
