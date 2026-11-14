package org.syalosovetskyi.onemoney.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType.EXPENSE
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType.INCOME

class DefaultCategoryMergeTest {
    private fun cat(id: Long, name: String, parent: Long? = null, default: Boolean = false, type: org.syalosovetskyi.onemoney.data.db.entities.TransactionType = EXPENSE) =
        CategoryEntity(id = id, name = name, type = type, colorHex = "#000000", icon = "category",
            isDefault = default, parentId = parent)

    @Test
    fun freshInstallDefaultsMergeIntoServerGroups() {
        val local = listOf(
            cat(1, "Продукти", default = true), cat(2, "Здоров'я", default = true),
            cat(3, "Аптека", parent = 2), cat(4, "Лікар", parent = 2),
            cat(5, "Мій скарбничок", default = false),       // своя — не трогаем
            cat(6, "Транспорт", default = true),             // на сервере нет — остаётся
        )
        val server = listOf(cat(100, "Продукти"), cat(200, "здоров'я "), cat(201, "Аптека", parent = 200))
        val plan = planDefaultCategoryMerge(local + server, server)
        assertEquals(mapOf(1L to 100L, 2L to 200L, 3L to 201L), plan.replace)
        assertEquals(mapOf(4L to 200L), plan.reparent)
    }

    @Test
    fun typeMustMatchAndServerIdsUntouched() {
        val local = listOf(cat(1, "Подарунки", default = true, type = INCOME), cat(100, "Подарунки"))
        val plan = planDefaultCategoryMerge(local, listOf(cat(100, "Подарунки")))
        assertEquals(emptyMap<Long, Long>(), plan.replace)
    }

    @Test
    fun archivedServerRootIsNotATarget() {
        val server = listOf(cat(100, "Продукти").copy(archived = true))
        val plan = planDefaultCategoryMerge(listOf(cat(1, "Продукти", default = true)) + server, server)
        assertEquals(emptyMap<Long, Long>(), plan.replace)
    }
}
