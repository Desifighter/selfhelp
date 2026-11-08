package com.supervision.app.data.repository

import com.supervision.app.data.local.dao.UsageRecordDao
import com.supervision.app.data.local.entity.UsageRecordEntity
import com.supervision.app.domain.model.UsageRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRepository @Inject constructor(
    private val dao: UsageRecordDao
) {
    suspend fun recordUsage(packageName: String, appName: String, minutes: Int, date: LocalDate = LocalDate.now()) {
        val dateStr = date.toString()
        val existing = dao.getByPackageAndDate(packageName, dateStr)
        if (existing != null) {
            dao.update(existing.copy(
                totalForegroundMinutes = minutes,
                lastUsedAt = System.currentTimeMillis(),
                launchCount = existing.launchCount + 1
            ))
        } else {
            dao.insertOrUpdate(UsageRecordEntity(
                packageName = packageName,
                appName = appName,
                date = dateStr,
                totalForegroundMinutes = minutes,
                lastUsedAt = System.currentTimeMillis(),
                launchCount = 1
            ))
        }
        Timber.d("Recorded usage for $packageName: $minutes min on $date")
    }

    suspend fun getUsageForToday(packageName: String): Int {
        val record = dao.getByPackageAndDate(packageName, LocalDate.now().toString())
        return record?.totalForegroundMinutes ?: 0
    }

    fun observeTodayUsage(): Flow<List<UsageRecord>> =
        dao.observeByDate(LocalDate.now().toString()).map { entities ->
            entities.map { it.toDomain() }
        }

    fun observeTotalMinutesToday(): Flow<Int?> =
        dao.observeTotalMinutesByDate(LocalDate.now().toString())

    fun observeAppHistory(packageName: String, days: Int = 30): Flow<List<UsageRecord>> =
        dao.observeHistoryForApp(packageName, days).map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun cleanupOldRecords(retainDays: Int = 90) {
        val cutoff = LocalDate.now().minusDays(retainDays.toLong()).toString()
        dao.deleteOlderThan(cutoff)
    }

    private fun UsageRecordEntity.toDomain(): UsageRecord {
        return UsageRecord(
            id = id,
            packageName = packageName,
            appName = appName,
            date = LocalDate.parse(date),
            totalForegroundMinutes = totalForegroundMinutes,
            lastUsedAt = lastUsedAt?.let { Instant.ofEpochMilli(it) },
            launchCount = launchCount
        )
    }
}
