package org.syalosovetskyi.onemoney.workers

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.syalosovetskyi.onemoney.data.db.dao.AccountDao
import org.syalosovetskyi.onemoney.data.db.entities.TransactionEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType

class RepeatTransactionBalanceTest {
    @Test
    fun `foreign recurring transfers and repayments credit stored destination amount`() = runTest {
        for (type in listOf(TransactionType.TRANSFER, TransactionType.REPAY)) {
            val dao = mockk<AccountDao>(relaxed = true)
            updateRepeatBalance(dao, TransactionEntity(type = type, amount = 820.0,
                accountId = 1L, toAccountId = 2L, toAmount = 20.0))
            coVerify { dao.updateBalance(1L, -820.0) }
            coVerify { dao.updateBalance(2L, 20.0) }
            coVerify(exactly = 0) { dao.updateBalance(2L, 820.0) }
        }
    }

    @Test
    fun `legacy recurring transfers retain same amount fallback`() = runTest {
        val dao = mockk<AccountDao>(relaxed = true)
        updateRepeatBalance(dao, TransactionEntity(type = TransactionType.TRANSFER,
            amount = 50.0, accountId = 1L, toAccountId = 2L))
        coVerify { dao.updateBalance(2L, 50.0) }
    }
}
