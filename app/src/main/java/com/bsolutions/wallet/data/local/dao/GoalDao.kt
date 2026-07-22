package com.bsolutions.wallet.data.local.dao

import androidx.room.*
import com.bsolutions.wallet.data.local.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE ownerId = :ownerId AND isDeleted = 0 ORDER BY isCompleted ASC, targetDate ASC")
    fun getAllGoals(ownerId: String): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE ownerId = :ownerId AND id = :id AND isDeleted = 0")
    suspend fun getGoalById(ownerId: String, id: String): GoalEntity?

    @Query("SELECT * FROM goals WHERE ownerId = :ownerId AND needsSync = 1 ORDER BY id")
    suspend fun getNeedingSync(ownerId: String): List<GoalEntity>

    @Query("SELECT COUNT(*) FROM goals WHERE ownerId = :ownerId AND needsSync = 1")
    fun countNeedingSync(ownerId: String): Flow<Int>

    @Query("UPDATE goals SET needsSync = 0 WHERE ownerId = :ownerId AND id = :id")
    suspend fun markSynced(ownerId: String, id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalEntity)

    @Update
    suspend fun updateGoal(goal: GoalEntity)

    @Query("UPDATE goals SET isDeleted = 1, needsSync = 1 WHERE ownerId = :ownerId AND id = :id")
    suspend fun softDeleteGoal(ownerId: String, id: String)
}
