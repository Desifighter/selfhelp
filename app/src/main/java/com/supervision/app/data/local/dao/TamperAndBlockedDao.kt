package com.supervision.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.supervision.app.data.local.entity.TamperEventEntity
import com.supervision.app.data.local.entity.BlockedRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TamperEventDao {

    @Insert
    suspend fun insert(entity: TamperEventEntity): Long

    @Query("SELECT * FROM tamper_events ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TamperEventEntity>>

    @Query("SELECT * FROM tamper_events WHERE resolved = 0 ORDER BY timestamp DESC")
    fun observeUnresolved(): Flow<List<TamperEventEntity>>

    @Query("SELECT COUNT(*) FROM tamper_events WHERE resolved = 0")
    fun observeUnresolvedCount(): Flow<Int>

    @Query("UPDATE tamper_events SET resolved = 1 WHERE id = :id")
    suspend fun resolve(id: Long)

    @Query("DELETE FROM tamper_events WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

@Dao
interface BlockedRequestDao {

    @Insert
    suspend fun insert(entity: BlockedRequestEntity): Long

    @Query("SELECT * FROM blocked_requests ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<BlockedRequestEntity>>

    @Query("SELECT COUNT(*) FROM blocked_requests")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM blocked_requests WHERE timestamp >= :since")
    fun observeCountSince(since: Long): Flow<Int>

    @Query("SELECT domain, COUNT(*) as count FROM blocked_requests GROUP BY domain ORDER BY count DESC LIMIT :limit")
    suspend fun getTopBlockedDomains(limit: Int = 10): List<DomainBlockCount>

    @Query("DELETE FROM blocked_requests WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

data class DomainBlockCount(
    val domain: String,
    val count: Int
)

