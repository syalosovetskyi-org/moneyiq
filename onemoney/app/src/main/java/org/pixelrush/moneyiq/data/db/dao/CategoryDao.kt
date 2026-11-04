package org.syalosovetskyi.onemoney.data.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY sortOrder ASC, name ASC")
    fun getCategoriesByType(type: TransactionType): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Upsert
    suspend fun insertCategory(category: CategoryEntity): Long

    @Upsert
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Update
    suspend fun updateCategories(categories: List<CategoryEntity>)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllCategoriesOnce(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()
}
