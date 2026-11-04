package org.syalosovetskyi.onemoney.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.syalosovetskyi.onemoney.data.db.entities.TxOverrideEntity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class TxOverrideDaoTest {
    private lateinit var db: AppDatabase
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun close() { db.close() }
    private fun edit(time: Long) = TxOverrideEntity(txId = 1, note = "Changed", categoryName = "Food",
        txType = "EXPENSE", updatedAt = time)

    @Test fun stale_acknowledgement_cannot_mark_newer_edit_synced() = runTest {
        val dao = db.txOverrideDao()
        dao.upsert(edit(1))
        dao.upsert(edit(2))
        dao.markSynced(1, 1)
        assertEquals(1, dao.pendingCount(5))
        dao.markSynced(1, 2)
        assertEquals(0, dao.pendingCount(5))
        assertTrue(dao.getById(1)!!.synced)
    }

    @Test fun rejected_edits_exhaust_retry_budget_but_remain_inspectable() = runTest {
        val dao = db.txOverrideDao()
        dao.upsert(edit(1))
        repeat(5) { dao.markRejected(1, 1, "Rejected") }
        assertEquals(0, dao.pendingCount(5))
        assertTrue(dao.getPending(5).isEmpty())
        assertEquals(1, dao.deadCount(5))
        assertEquals("Rejected", dao.getById(1)!!.lastError)
    }
}
