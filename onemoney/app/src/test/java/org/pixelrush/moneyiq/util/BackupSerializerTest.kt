package org.syalosovetskyi.onemoney.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import org.syalosovetskyi.onemoney.data.db.entities.AccountType
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType

class BackupSerializerTest {

    private fun sampleBackup() = BackupData(
        version = BackupSerializer.BACKUP_VERSION,
        exportDate = 1_717_000_000_000L,
        accounts = listOf(
            AccountEntity(
                id = 1L, name = "Гаманець", type = AccountType.CASH,
                balance = 500.0, currency = "UAH", colorHex = "#4CAF50",
                icon = "account_balance_wallet", includeInTotal = true, isDefault = true,
                sortOrder = 0, description = "Основний", createdAt = 1_717_000_000_000L,
                creditLimit = 1_500.0
            )
        ),
        categories = listOf(
            CategoryEntity(
                id = 1L, name = "Продукти", type = TransactionType.EXPENSE,
                colorHex = "#03A9F4", icon = "shopping", budgetAmount = 0.0,
                budgetPeriod = "MONTHLY", isDefault = true, sortOrder = 1,
                archived = false, parentId = null, currencyCode = "EUR"
            )
        ),
        transactions = listOf(
            TransactionEntity(
                id = 1L, type = TransactionType.EXPENSE, amount = 100.0,
                accountId = 1L, toAccountId = null, categoryId = 1L,
                note = "Test", date = 1_717_000_000_000L, createdAt = 1_717_000_000_000L,
                repeatMode = "MONTHLY", reminderMode = "1_DAY", nextRepeatDate = 1_719_678_400_000L
            )
        )
    )

    // ── round-trip ───────────────────────────────────────────────────────────

    @Test
    fun `round trip preserves account fields`() {
        val original = sampleBackup()
        val restored = BackupSerializer.deserialize(BackupSerializer.serialize(original))

        val a = restored.accounts[0]
        assertEquals(1L, a.id)
        assertEquals("Гаманець", a.name)
        assertEquals(AccountType.CASH, a.type)
        assertEquals(500.0, a.balance, 0.001)
        assertEquals("UAH", a.currency)
        assertTrue(a.isDefault)
        assertTrue(a.includeInTotal)
        assertEquals("Основний", a.description)
        assertEquals(1_500.0, a.creditLimit, 0.001)
    }

    @Test
    fun `round trip preserves category fields`() {
        val original = sampleBackup()
        val restored = BackupSerializer.deserialize(BackupSerializer.serialize(original))

        val c = restored.categories[0]
        assertEquals(1L, c.id)
        assertEquals("Продукти", c.name)
        assertEquals(TransactionType.EXPENSE, c.type)
        assertEquals("#03A9F4", c.colorHex)
        assertEquals("shopping", c.icon)
        assertNull(c.parentId)
        assertEquals("EUR", c.currencyCode)
    }

    @Test
    fun `round trip preserves transaction fields`() {
        val original = sampleBackup()
        val restored = BackupSerializer.deserialize(BackupSerializer.serialize(original))

        val t = restored.transactions[0]
        assertEquals(1L, t.id)
        assertEquals(TransactionType.EXPENSE, t.type)
        assertEquals(100.0, t.amount, 0.001)
        assertEquals(1L, t.accountId)
        assertNull(t.toAccountId)
        assertEquals(1L, t.categoryId)
        assertEquals("Test", t.note)
        assertEquals("MONTHLY", t.repeatMode)
        assertEquals("1_DAY", t.reminderMode)
        assertEquals(1_719_678_400_000L, t.nextRepeatDate)
    }

    // ── null handling ────────────────────────────────────────────────────────

    @Test
    fun `deserialize handles null toAccountId`() {
        val backup = sampleBackup().copy(
            transactions = listOf(
                TransactionEntity(id = 2L, type = TransactionType.EXPENSE, amount = 50.0, accountId = 1L, toAccountId = null)
            )
        )
        val restored = BackupSerializer.deserialize(BackupSerializer.serialize(backup))
        assertNull(restored.transactions[0].toAccountId)
    }

    @Test
    fun `deserialize handles null categoryId`() {
        val backup = sampleBackup().copy(
            transactions = listOf(
                TransactionEntity(id = 2L, type = TransactionType.EXPENSE, amount = 50.0, accountId = 1L, categoryId = null)
            )
        )
        val restored = BackupSerializer.deserialize(BackupSerializer.serialize(backup))
        assertNull(restored.transactions[0].categoryId)
    }

    @Test
    fun `deserialize handles null parentId for root categories`() {
        val restored = BackupSerializer.deserialize(BackupSerializer.serialize(sampleBackup()))
        assertNull(restored.categories[0].parentId)
    }

    @Test
    fun `deserialize correctly restores non-null parentId`() {
        val backup = sampleBackup().copy(
            categories = listOf(
                CategoryEntity(id = 10L, name = "Sub", type = TransactionType.EXPENSE, parentId = 1L)
            )
        )
        val restored = BackupSerializer.deserialize(BackupSerializer.serialize(backup))
        assertEquals(1L, restored.categories[0].parentId)
    }

    @Test
    fun `deserialize defaults missing creditLimit to zero for old backups`() {
        val root = JSONObject(BackupSerializer.serialize(sampleBackup()))
        root.getJSONArray("accounts").getJSONObject(0).remove("creditLimit")

        val restored = BackupSerializer.deserialize(root.toString())

        assertEquals(0.0, restored.accounts[0].creditLimit, 0.001)
    }

    @Test
    fun `deserialize defaults missing category currencyCode to UAH for old backups`() {
        val root = JSONObject(BackupSerializer.serialize(sampleBackup()))
        root.getJSONArray("categories").getJSONObject(0).remove("currencyCode")

        val restored = BackupSerializer.deserialize(root.toString())

        assertEquals("UAH", restored.categories[0].currencyCode)
    }

    @Test
    fun `deserialize defaults missing repeat fields for old backups`() {
        val root = JSONObject(BackupSerializer.serialize(sampleBackup()))
        val tx = root.getJSONArray("transactions").getJSONObject(0)
        tx.remove("repeatMode")
        tx.remove("reminderMode")
        tx.remove("nextRepeatDate")

        val restored = BackupSerializer.deserialize(root.toString())

        assertEquals("NEVER", restored.transactions[0].repeatMode)
        assertEquals("NEVER", restored.transactions[0].reminderMode)
        assertNull(restored.transactions[0].nextRepeatDate)
    }

    @Test
    fun `deserialize handles absent nullable transaction ids for old backups`() {
        val root = JSONObject(BackupSerializer.serialize(sampleBackup()))
        val tx = root.getJSONArray("transactions").getJSONObject(0)
        tx.remove("toAccountId")
        tx.remove("categoryId")

        val restored = BackupSerializer.deserialize(root.toString())

        assertNull(restored.transactions[0].toAccountId)
        assertNull(restored.transactions[0].categoryId)
    }

    // ── JSON structure ───────────────────────────────────────────────────────

    @Test
    fun `serialize output contains version field`() {
        val json = BackupSerializer.serialize(sampleBackup())
        assertTrue(json.contains("\"version\""))
    }

    @Test
    fun `serialize output contains app field onemoney`() {
        val json = BackupSerializer.serialize(sampleBackup())
        assertTrue(json.contains("onemoney"))
    }

    @Test
    fun `serialize output contains exportDate`() {
        val json = BackupSerializer.serialize(sampleBackup())
        assertTrue(json.contains("\"exportDate\""))
    }

    // ── edge cases ───────────────────────────────────────────────────────────

    @Test
    fun `round trip with empty lists preserves structure`() {
        val backup = BackupData(
            version = 1, exportDate = 0L,
            accounts = emptyList(), categories = emptyList(), transactions = emptyList()
        )
        val restored = BackupSerializer.deserialize(BackupSerializer.serialize(backup))
        assertTrue(restored.accounts.isEmpty())
        assertTrue(restored.categories.isEmpty())
        assertTrue(restored.transactions.isEmpty())
    }

    @Test
    fun `version is preserved through round trip`() {
        val backup = sampleBackup()
        val restored = BackupSerializer.deserialize(BackupSerializer.serialize(backup))
        assertEquals(backup.version, restored.version)
    }

    @Test
    fun `exportDate is preserved through round trip`() {
        val backup = sampleBackup()
        val restored = BackupSerializer.deserialize(BackupSerializer.serialize(backup))
        assertEquals(backup.exportDate, restored.exportDate)
    }

    @Test
    fun `transfer transaction with toAccountId round trips correctly`() {
        val backup = sampleBackup().copy(
            accounts = listOf(
                AccountEntity(id = 1L, name = "From", balance = 1000.0),
                AccountEntity(id = 2L, name = "To", balance = 0.0)
            ),
            transactions = listOf(
                TransactionEntity(
                    id = 1L, type = TransactionType.TRANSFER, amount = 200.0,
                    accountId = 1L, toAccountId = 2L
                )
            )
        )
        val restored = BackupSerializer.deserialize(BackupSerializer.serialize(backup))
        assertNotNull(restored.transactions[0].toAccountId)
        assertEquals(2L, restored.transactions[0].toAccountId)
    }

    @Test
    fun `multiple transactions are all preserved`() {
        val backup = sampleBackup().copy(
            transactions = listOf(
                TransactionEntity(id = 1L, type = TransactionType.INCOME, amount = 500.0, accountId = 1L),
                TransactionEntity(id = 2L, type = TransactionType.EXPENSE, amount = 100.0, accountId = 1L),
                TransactionEntity(id = 3L, type = TransactionType.EXPENSE, amount = 50.0, accountId = 1L)
            )
        )
        val restored = BackupSerializer.deserialize(BackupSerializer.serialize(backup))
        assertEquals(3, restored.transactions.size)
    }

    @Test
    fun `foreign transfer preserves credited amount through backup`() {
        val original = sampleBackup().copy(transactions = listOf(
            TransactionEntity(id = 7L, type = TransactionType.TRANSFER,
                amount = 820.0, accountId = 1L, toAccountId = 2L, toAmount = 20.0)
        ))
        val restored = BackupSerializer.deserialize(BackupSerializer.serialize(original))
        assertEquals(20.0, restored.transactions.single().toAmount!!, 0.0)
    }

    @Test
    fun `old backup without credited amount remains readable`() {
        val root = JSONObject(BackupSerializer.serialize(sampleBackup()))
        root.getJSONArray("transactions").getJSONObject(0).remove("toAmount")
        assertNull(BackupSerializer.deserialize(root.toString()).transactions.single().toAmount)
    }
}
