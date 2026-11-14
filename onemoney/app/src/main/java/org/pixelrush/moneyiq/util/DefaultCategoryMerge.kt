package org.syalosovetskyi.onemoney.util

import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity

/**
 * Свежая установка засевает стандартные категории (isDefault, свои id), а синк mono-flow
 * приносит те же группы под серверными id — на главном экране получались пары
 * «Продукти 3 172 ₴ / Продукти 0 ₴» (serg/tasks#2673).
 *
 * План слияния: локальный стандартный корень с тем же именем и типом, что у серверного
 * корня, уходит в серверный; его подкатегории — в одноимённую серверную подкатегорию,
 * а если такой нет — переезжают под серверный корень.
 */
data class DefaultMergePlan(
    /** локальный id → серверный id: операции перевесить, локальную категорию удалить */
    val replace: Map<Long, Long>,
    /** локальная подкатегория → новый родитель (серверный корень) */
    val reparent: Map<Long, Long>,
)

private fun key(c: CategoryEntity) = c.name.trim().lowercase() to c.type

fun planDefaultCategoryMerge(local: List<CategoryEntity>, imported: List<CategoryEntity>): DefaultMergePlan {
    val importedIds = imported.map { it.id }.toSet()
    val importedRoots = imported.filter { it.parentId == null && !it.archived }.associateBy(::key)
    val replace = mutableMapOf<Long, Long>()
    val reparent = mutableMapOf<Long, Long>()
    for (root in local) {
        if (!root.isDefault || root.parentId != null || root.id in importedIds) continue
        val target = importedRoots[key(root)] ?: continue
        replace[root.id] = target.id
        val serverChildren = imported.filter { it.parentId == target.id }.associateBy(::key)
        for (child in local.filter { it.parentId == root.id && it.id !in importedIds }) {
            val twin = serverChildren[key(child)]
            if (twin != null) replace[child.id] = twin.id else reparent[child.id] = target.id
        }
    }
    return DefaultMergePlan(replace, reparent)
}
