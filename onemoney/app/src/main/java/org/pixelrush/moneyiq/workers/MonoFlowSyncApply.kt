package org.syalosovetskyi.onemoney.workers

import android.util.Log
import org.syalosovetskyi.onemoney.data.db.dao.AccountDao
import org.syalosovetskyi.onemoney.data.db.dao.CategoryDao
import org.syalosovetskyi.onemoney.data.db.dao.TransactionDao
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.util.BackupData
import org.syalosovetskyi.onemoney.util.normalizeImportedCategory
import org.syalosovetskyi.onemoney.util.planDefaultCategoryMerge

private const val TAG = "MonoFlowSync"

/**
 * Вікно перекриття інкрементального синка. Сервер фільтрує `since` за банківською датою
 * операції, а курсор береться з годинника телефона: операцію, яку сервер догрузив пізніше
 * (втрачений вебхук, опитування виписки), вузьке вікно губило б назавжди. Повторна
 * доставка ідемпотентна (REPLACE за id), невіддані правки пушаться до тягнення (serg/tasks#3045).
 */
const val MONOFLOW_SYNC_OVERLAP_MS = 7L * 24 * 60 * 60 * 1000

/** `since` для запиту: 0 (повний синк) лишається 0, інакше відступаємо на вікно перекриття. */
fun monoFlowSinceMs(lastSyncMs: Long): Long =
    if (lastSyncMs <= 0L) 0L else (lastSyncMs - MONOFLOW_SYNC_OVERLAP_MS).coerceAtLeast(0L)

/**
 * Застосування вже розібраної відповіді mono-flow до локальної БД. Винесено з
 * [MonoFlowSyncWorker.doWork], щоб сценарії «чиста установка / оновлення» гонялись у
 * тестах на in-memory Room без мережі та Hilt (serg/tasks#2673). Поведінка та сама:
 * insert/replace за id, нічого не видаляємо, окрім злитих стандартних категорій.
 */
suspend fun applyMonoFlowSync(
    accountDao: AccountDao,
    categoryDao: CategoryDao,
    transactionDao: TransactionDao,
    data: BackupData,
) {
    accountDao.insertAccounts(data.accounts)
    val normalizedCats = data.categories.map { normalizeImportedCategory(it) }
    categoryDao.insertCategories(normalizedCats)
    transactionDao.insertTransactions(data.transactions)
    mergeDefaultCategories(categoryDao, transactionDao, normalizedCats)
}

/** Стандартні категорії свіжої установки зливаються з однойменними серверними,
 *  інакше на головному екрані пари «Продукти / Продукти 0 ₴» (serg/tasks#2673). */
private suspend fun mergeDefaultCategories(
    cats: CategoryDao,
    transactions: TransactionDao,
    imported: List<CategoryEntity>,
) {
    val plan = planDefaultCategoryMerge(cats.getAllCategoriesOnce(), imported)
    if (plan.replace.isEmpty() && plan.reparent.isEmpty()) return
    for ((child, parent) in plan.reparent) cats.setParent(child, parent)
    for ((from, to) in plan.replace) {
        transactions.moveCategory(from, to)
        cats.moveChildren(from, to)
        cats.deleteById(from)
    }
    Log.i(TAG, "злито стандартних категорій: ${plan.replace.size}, перенесено підкатегорій: ${plan.reparent.size}")
}
