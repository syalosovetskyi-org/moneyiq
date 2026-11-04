package org.syalosovetskyi.onemoney.util

import org.junit.Assert.*
import org.junit.Test
import org.syalosovetskyi.onemoney.data.db.entities.*

class BackupValidationTest {
    private val account = AccountEntity(id = 1L, name = "Cash")
    private fun data(tx: TransactionEntity) = BackupData(1, 0L, listOf(account), emptyList(), listOf(tx))

    @Test(expected = IllegalArgumentException::class)
    fun `missing source account rejected before writes`() {
        BackupSerializer.validate(data(TransactionEntity(id = 1L, type = TransactionType.EXPENSE,
            amount = 10.0, accountId = 2L)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing destination rejected`() {
        BackupSerializer.validate(data(TransactionEntity(id = 1L, type = TransactionType.TRANSFER,
            amount = 10.0, accountId = 1L, toAccountId = 2L)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate ids rejected`() {
        BackupSerializer.validate(BackupData(1, 0L, listOf(account, account), emptyList(), emptyList()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non finite money rejected`() {
        BackupSerializer.validate(data(TransactionEntity(id = 1L, type = TransactionType.EXPENSE,
            amount = Double.NaN, accountId = 1L)))
    }

    @Test
    fun `valid empty backup accepted`() {
        BackupSerializer.validate(BackupData(1, 0L, emptyList(), emptyList(), emptyList()))
    }
}
