package org.syalosovetskyi.onemoney.data.repository

import kotlinx.coroutines.flow.Flow
import org.syalosovetskyi.onemoney.data.db.dao.CategoryDao
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.util.suggestCategoryStyle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(private val dao: CategoryDao) {

    fun getAll(): Flow<List<CategoryEntity>> = dao.getAllCategories()

    fun getByType(type: TransactionType): Flow<List<CategoryEntity>> = dao.getCategoriesByType(type)

    suspend fun getById(id: Long): CategoryEntity? = dao.getCategoryById(id)

    suspend fun save(category: CategoryEntity): Long = dao.insertCategory(category)

    /**
     * A subcategory always has its parent's type (#3037 R17): a subcategory is saved
     * with the parent's type whatever the caller passed, and changing a root
     * category's type carries its subcategories along.
     */
    suspend fun update(category: CategoryEntity) {
        val parentType = category.parentId?.let { dao.getCategoryById(it)?.type }
        val toSave = if (parentType != null && parentType != category.type)
            category.copy(type = parentType) else category
        dao.updateCategory(toSave)
        if (toSave.parentId == null) {
            val stale = dao.getAllCategoriesOnce()
                .filter { it.parentId == toSave.id && it.type != toSave.type }
            if (stale.isNotEmpty()) dao.updateCategories(stale.map { it.copy(type = toSave.type) })
        }
    }

    suspend fun updateAll(categories: List<CategoryEntity>) = dao.updateCategories(categories)

    suspend fun delete(category: CategoryEntity) = dao.deleteCategory(category)

    suspend fun repairIconKeys() {
        val validKeys = setOf(
            "category", "shopping", "restaurant", "car", "bus", "taxi", "gas_station",
            "parking", "home", "key", "work", "laptop", "school", "health", "pharmacy",
            "doctor", "percent", "gavel", "flight", "music", "movie", "gaming", "telegram",
            "dating", "ticket", "money", "coffee", "pets", "gift", "phone", "sports",
            "wifi", "delivery", "devices", "transfer", "family", "receipt", "beauty",
            "ai", "aliexpress", "cloud", "clothes", "grocery", "volunteer", "theater",
            "celebration", "spa",
            // Extended icons added 2026-05-31
            "flower", "souvenir", "store", "shoes", "tools", "hardware", "toys",
            "fitness", "dental", "server", "train", "hotel", "book", "auto_parts"
        )
        // Name-based overrides: force specific icons for well-known category names
        val nameOverrides = listOf(
            // Utilities / internet / phone — must override even if ‘family’ (a valid key).
            // "язок" is apostrophe-agnostic: matches "зв’язок", "зв’язок", "зв’язок".
            "комунал"             to "home",
            "язок"                to "phone",
            "інтернет"            to "wifi",
            "взуття"              to "shoes",
            "взутт"               to "shoes",
            "rozetka"             to "store",
            "розетка"             to "store",
            "ebay"                to "store",
            "маркетплейс"         to "store",
            "marketplace"         to "store",
            "квіти"               to "flower",
            "цвіти"               to "flower",
            "сувенір"             to "souvenir",
            "іграшк"              to "toys",
            "інструмент"          to "tools",
            "будматеріал"         to "hardware",
            "спортивні товари"    to "fitness",
            "спорттовар"          to "fitness",
            "стоматолог"          to "dental",
            "дантист"             to "dental",
            "хостінг"             to "server",
            "hosting"             to "server",
            "залізниц"            to "train",
            "готель"              to "hotel",
            "книги"               to "book",
            "книга"               to "book",
            "автозапч"            to "auto_parts",
            "запчастин"           to "auto_parts",
            "громадськ"           to "bus",
            "краса"               to "beauty",
            "азс"                 to "gas_station",
            "dating"              to "dating",
            "gaming"              to "gaming",
        )

        // Generic icons that may have been assigned incorrectly to named categories.
        // "family" is valid for Сім'я but wrong for Зв'язок/Інтернет/Комунальні.
        val genericIcons = setOf("category", "family")

        dao.getAllCategoriesOnce().forEach { cat ->
            val nameLower = cat.name.lowercase().trim()
            val forced = nameOverrides.firstOrNull { nameLower.contains(it.first) }?.second
            val newIcon = when {
                forced != null && forced != cat.icon -> forced
                cat.icon in genericIcons -> {
                    val suggested = suggestCategoryStyle(cat.name, cat.type).first
                    if (suggested != "category" && suggested != cat.icon) suggested else cat.icon
                }
                cat.icon !in validKeys -> suggestCategoryStyle(cat.name, cat.type).first
                else                   -> cat.icon
            }
            if (newIcon != cat.icon) {
                dao.updateCategory(cat.copy(icon = newIcon))
            }
        }
    }

    suspend fun repairDefaultColors() {
        val canonical = mapOf(
            "продукти"   to "#4AAFE8",
            "ресторація" to "#4659BE",
            "дозвілля"   to "#F73579",
            "транспорт"  to "#FFA834",
            "здоров'я"   to "#48B456",
            "подарунки"  to "#F34B4D",
            "сім'я"      to "#7A48F2",
            "покупки"    to "#7B5947",
            "робота"     to "#1565C0",
        )
        dao.getAllCategoriesOnce()
            .filter { it.parentId == null }  // всі кореневі, незалежно від isDefault
            .forEach { cat ->
                // нормалізуємо: замінюємо всі варіанти апострофа на стандартний
                val key = cat.name.lowercase().trim()
                    .replace("’", "'").replace("ʼ", "'").replace("`", "'")
                val target = canonical[key]
                if (target != null && !cat.colorHex.equals(target, ignoreCase = true)) {
                    dao.updateCategory(cat.copy(colorHex = target))
                }
            }
    }

    suspend fun seedDefaults() {
        if (dao.count() > 0) return

        val E = TransactionType.EXPENSE
        val I = TransactionType.INCOME

        // ── Витрати: кореневі категорії (order matches 1Money reference) ─────
        val prodId   = dao.insertCategory(CategoryEntity(name = "Продукти",   type = E, colorHex = "#4AAFE8", icon = "grocery",    isDefault = true, sortOrder = 1))
        val restId   = dao.insertCategory(CategoryEntity(name = "Ресторація", type = E, colorHex = "#4659BE", icon = "restaurant", isDefault = true, sortOrder = 2))
        val dozId    = dao.insertCategory(CategoryEntity(name = "Дозвілля",   type = E, colorHex = "#F73579", icon = "theater",    isDefault = true, sortOrder = 3))
        val transId  = dao.insertCategory(CategoryEntity(name = "Транспорт",  type = E, colorHex = "#FFA834", icon = "bus",        isDefault = true, sortOrder = 4))
        val healthId = dao.insertCategory(CategoryEntity(name = "Здоров'я",   type = E, colorHex = "#48B456", icon = "volunteer",  isDefault = true, sortOrder = 5))
        val giftId   = dao.insertCategory(CategoryEntity(name = "Подарунки",  type = E, colorHex = "#F34B4D", icon = "gift",       isDefault = true, sortOrder = 6))
        val familyId = dao.insertCategory(CategoryEntity(name = "Сім'я",      type = E, colorHex = "#7A48F2", icon = "family",     isDefault = true, sortOrder = 7))
        val shopId   = dao.insertCategory(CategoryEntity(name = "Покупки",    type = E, colorHex = "#7B5947", icon = "shopping",   isDefault = true, sortOrder = 8))
        /* Робота — додаткова категорія */
        dao.insertCategory(CategoryEntity(name = "Робота",     type = E, colorHex = "#1565C0", icon = "work",       isDefault = true, sortOrder = 9))

        // ── Витрати: підкатегорії ────────────────────────────────────────────
        dao.insertCategories(listOf(
            // Ресторація
            CategoryEntity(name = "Food delivery", type = E, colorHex = "#FF6F00", icon = "delivery",    parentId = restId,   sortOrder = 1),
            CategoryEntity(name = "Ресторани",     type = E, colorHex = "#E53935", icon = "restaurant",  parentId = restId,   sortOrder = 2),
            CategoryEntity(name = "Кафе",          type = E, colorHex = "#795548", icon = "coffee",      parentId = restId,   sortOrder = 3),
            // Дозвілля
            CategoryEntity(name = "Кіно",         type = E, colorHex = "#9C27B0", icon = "movie",      parentId = dozId,    sortOrder = 1),
            CategoryEntity(name = "Gaming",        type = E, colorHex = "#607D8B", icon = "gaming",     parentId = dozId,    sortOrder = 2),
            CategoryEntity(name = "Хобі",         type = E, colorHex = "#5C6BC0", icon = "school",     parentId = dozId,    sortOrder = 3),
            CategoryEntity(name = "Спорт",        type = E, colorHex = "#F44336", icon = "sports",     parentId = dozId,    sortOrder = 4),
            // Транспорт
            CategoryEntity(name = "Таксі",        type = E, colorHex = "#FDD835", icon = "taxi",        parentId = transId,  sortOrder = 1),
            CategoryEntity(name = "Паркінг",      type = E, colorHex = "#78909C", icon = "parking",    parentId = transId,  sortOrder = 2),
            CategoryEntity(name = "Пальне",       type = E, colorHex = "#FF8F00", icon = "gas_station", parentId = transId, sortOrder = 3),
            // Здоров'я
            CategoryEntity(name = "Аптека",       type = E, colorHex = "#43A047", icon = "pharmacy",   parentId = healthId, sortOrder = 1),
            CategoryEntity(name = "Лікар",        type = E, colorHex = "#D81B60", icon = "doctor",     parentId = healthId, sortOrder = 2),
            // Сім'я
            CategoryEntity(name = "Комуналка",    type = E, colorHex = "#546E7A", icon = "home",       parentId = familyId, sortOrder = 1),
            CategoryEntity(name = "Оренда",       type = E, colorHex = "#9C27B0", icon = "key",        parentId = familyId, sortOrder = 2),
            CategoryEntity(name = "Зв'язок",      type = E, colorHex = "#3F51B5", icon = "phone",      parentId = familyId, sortOrder = 3),
            CategoryEntity(name = "Інтернет",     type = E, colorHex = "#00BCD4", icon = "wifi",       parentId = familyId, sortOrder = 4),
            // Покупки
            CategoryEntity(name = "AliExpress",   type = E, colorHex = "#FF6D00", icon = "aliexpress", parentId = shopId,   sortOrder = 1),
            CategoryEntity(name = "Одяг",         type = E, colorHex = "#00838F", icon = "clothes",    parentId = shopId,   sortOrder = 2),
            CategoryEntity(name = "Електроніка",  type = E, colorHex = "#607D8B", icon = "devices",    parentId = shopId,   sortOrder = 3),
            CategoryEntity(name = "Краса",        type = E, colorHex = "#AD1457", icon = "beauty",     parentId = shopId,   sortOrder = 4),
        ))

        // ── Доходи ───────────────────────────────────────────────────────────
        dao.insertCategories(listOf(
            CategoryEntity(name = "Зарплата", type = I, colorHex = "#4CAF50", icon = "work",     isDefault = true, sortOrder = 1),
            CategoryEntity(name = "Фриланс",  type = I, colorHex = "#26A69A", icon = "laptop",   isDefault = true, sortOrder = 2),
            CategoryEntity(name = "Інше",     type = I, colorHex = "#78909C", icon = "category", isDefault = true, sortOrder = 3),
        ))
    }
}
