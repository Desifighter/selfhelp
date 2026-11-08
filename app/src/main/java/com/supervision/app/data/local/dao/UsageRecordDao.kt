package com.supervision.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.supervision.app.data.local.entity.UsageRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: UsageRecordEntity): Long

    @Update
    suspend fun update(entity: UsageRecordEntity)

    @Query("SELECT * FROM usage_records WHERE package_name = :packageName AND date = :date LIMIT 1")
    suspend fun getByPackageAndDate(packageName: String, date: String): UsageRecordEntity?

    @Query("SELECT * FROM usage_records WHERE date = :date ORDER BY total_foreground_minutes DESC")
    fun observeByDate(date: String): Flow<List<UsageRecordEntity>>

    @Query("SELECT * FROM usage_records WHERE date = :date ORDER BY total_foreground_minutes DESC")
    suspend fun getByDate(date: String): List<UsageRecordEntity>

    @Query("SELECT SUM(total_foreground_minutes) FROM usage_records WHERE date = :date")
    fun observeTotalMinutesByDate(date: String): Flow<Int?>

    @Query("SELECT * FROM usage_records WHERE package_name = :packageName ORDER BY date DESC LIMIT :limit")
    fun observeHistoryForApp(packageName: String, limit: Int = 30): Flow<List<UsageRecordEntity>>

    @Query("DELETE FROM usage_records WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)
}

