package com.bsolutions.wallet.data.local.dao;

import androidx.room.*;
import com.bsolutions.wallet.data.local.entity.CategoryEntity;
import kotlinx.coroutines.flow.Flow;

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE ownerId = :ownerId AND isDeleted = 0")
    fun getAllCategories(ownerId: String): Flow<List<CategoryEntity>>
    
    @Query("SELECT * FROM categories WHERE ownerId = :ownerId AND id = :id AND isDeleted = 0")
    suspend fun getCategoryById(ownerId: String, id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE ownerId = :ownerId AND id = :id")
    suspend fun getCategoryByIdIncludingDeleted(ownerId: String, id: String): CategoryEntity?

    @Query("SELECT id FROM categories WHERE ownerId = :ownerId")
    suspend fun getAllCategoryIdsIncludingDeleted(ownerId: String): List<String>

    @Query("SELECT * FROM categories WHERE ownerId = :ownerId AND needsSync = 1 ORDER BY id")
    suspend fun getCategoriesNeedingSync(ownerId: String): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories WHERE ownerId = :ownerId AND needsSync = 1")
    fun countNeedingSync(ownerId: String): Flow<Int>

    @Query("UPDATE categories SET needsSync = 0 WHERE ownerId = :ownerId AND id = :id")
    suspend fun markCategorySynced(ownerId: String, id: String)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)
    
    @Update
    suspend fun updateCategory(category: CategoryEntity)
    
    @Query("UPDATE categories SET isDeleted = 1, needsSync = 1 WHERE ownerId = :ownerId AND id = :id")
    suspend fun softDeleteCategory(ownerId: String, id: String)
}
