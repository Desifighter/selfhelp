package com.supervision.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.supervision.app.data.local.entity.ProtectionPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtectionPlanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ProtectionPlanEntity): Long

    @Update
    suspend fun update(entity: ProtectionPlanEntity)

    @Query("SELECT * FROM protection_plans WHERE is_active = 1 LIMIT 1")
    suspend fun getActivePlan(): ProtectionPlanEntity?

    @Query("SELECT * FROM protection_plans WHERE is_active = 1 LIMIT 1")
    fun observeActivePlan(): Flow<ProtectionPlanEntity?>

    @Query("SELECT * FROM protection_plans ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ProtectionPlanEntity>>

    @Query("SELECT * FROM protection_plans WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ProtectionPlanEntity?

    @Query("UPDATE protection_plans SET is_active = 0 WHERE is_active = 1")
    suspend fun deactivateAll()

    @Query("UPDATE protection_plans SET is_active = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun activate(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM protection_plans WHERE id = :id")
    suspend fun deleteById(id: Long)
}

