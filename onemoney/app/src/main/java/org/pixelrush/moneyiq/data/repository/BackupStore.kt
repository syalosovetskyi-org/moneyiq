package org.syalosovetskyi.onemoney.data.repository

import androidx.room.withTransaction
import org.syalosovetskyi.onemoney.data.db.AppDatabase
import org.syalosovetskyi.onemoney.util.BackupData
import org.syalosovetskyi.onemoney.util.BackupSerializer
import org.syalosovetskyi.onemoney.util.normalizeImportedCategory
import javax.inject.Inject

class BackupStore @Inject constructor(private val db: AppDatabase) {
    suspend fun snapshot(): BackupData = db.withTransaction {
        BackupData(BackupSerializer.BACKUP_VERSION, System.currentTimeMillis(),
            db.accountDao().getAllAccountsOnce(), db.categoryDao().getAllCategoriesOnce(),
            db.transactionDao().getAllTransactions())
    }

    suspend fun replace(data: BackupData) {
        BackupSerializer.validate(data)
        val categories = data.categories.map { normalizeImportedCategory(it) }
        db.withTransaction {
            db.transactionDao().deleteAllTransactions()
            db.accountDao().deleteAllAccounts()
            db.categoryDao().deleteAllCategories()
            db.accountDao().insertAccounts(data.accounts)
            db.categoryDao().insertCategories(categories)
            db.transactionDao().insertTransactions(data.transactions)
        }
    }
}
