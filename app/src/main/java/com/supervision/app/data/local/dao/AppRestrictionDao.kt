package com.supervision.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.supervision.app.data.local.entity.AppRestrictionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRestrictionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AppRestrictionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<AppRestrictionEntity>)

    @Update
    suspend fun update(entity: AppRestrictionEntity)

    @Query("SELECT * FROM app_restrictions WHERE package_name = :packageName LIMIT 1")
    suspend fun getByPackageName(packageName: String): AppRestrictionEntity?

    @Query("SELECT * FROM app_restrictions WHERE package_name = :packageName LIMIT 1")
    fun observeByPackageName(packageName: String): Flow<AppRestrictionEntity?>

    @Query("SELECT * FROM app_restrictions")
    fun observeAll(): Flow<List<AppRestrictionEntity>>

    @Query("SELECT * FROM app_restrictions WHERE restriction_type = 'TIME_LIMITED'")
    suspend fun getTimeLimited(): List<AppRestrictionEntity>

    @Query("SELECT * FROM app_restrictions WHERE restriction_type = 'COMPLETELY_BLOCKED'")
    suspend fun getCompletelyBlocked(): List<AppRestrictionEntity>

    @Query("SELECT * FROM app_restrictions WHERE restriction_type = 'ALWAYS_ALLOWED'")
    suspend fun getAlwaysAllowed(): List<AppRestrictionEntity>

    @Query("SELECT * FROM app_restrictions WHERE is_essential = 1")
    suspend fun getEssentialApps(): List<AppRestrictionEntity>

    @Query("SELECT COUNT(*) FROM app_restrictions WHERE restriction_type != 'ALWAYS_ALLOWED'")
    fun observeRestrictedCount(): Flow<Int>

    @Query("DELETE FROM app_restrictions WHERE package_name = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    @Query("DELETE FROM app_restrictions")
    suspend fun deleteAll()
}

