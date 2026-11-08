package com.supervision.app.data.repository

import com.supervision.app.data.local.dao.BlockedRequestDao
import com.supervision.app.data.local.dao.TamperEventDao
import com.supervision.app.data.local.entity.BlockedRequestEntity
import com.supervision.app.data.local.entity.TamperEventEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockedRequestRepository @Inject constructor(
    private val blockedRequestDao: BlockedRequestDao,
    private val tamperEventDao: TamperEventDao
) {
    suspend fun recordBlockedRequest(
        domain: String,
        category: String,
        sourceApp: String? = null,
        wasAutoDiscovered: Boolean = false
    ) {
        blockedRequestDao.insert(
            BlockedRequestEntity(
                domain = domain,
                category = category,
                sourceApp = sourceApp,
                wasAutoDiscovered = wasAutoDiscovered
            )
        )
    }

    fun observeTotalBlockedCount(): Flow<Int> = blockedRequestDao.observeTotalCount()

    fun observeRecentBlocked(limit: Int = 50) = blockedRequestDao.observeRecent(limit)

    suspend fun recordTamperEvent(type: String, description: String, severity: String = "MEDIUM") {
        tamperEventDao.insert(
            TamperEventEntity(
                eventType = type,
                description = description,
                severity = severity
            )
        )
    }

    fun observeUnresolvedTamperCount(): Flow<Int> = tamperEventDao.observeUnresolvedCount()

    fun observeTamperEvents() = tamperEventDao.observeAll()
}

