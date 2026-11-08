package com.supervision.app.data.repository

import com.supervision.app.data.local.dao.DomainClassificationDao
import com.supervision.app.data.mapper.toDomain
import com.supervision.app.data.mapper.toEntity
import com.supervision.app.domain.model.ClassificationCategory
import com.supervision.app.domain.model.ClassificationSource
import com.supervision.app.domain.model.ClassificationStatus
import com.supervision.app.domain.model.DomainClassification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DomainClassificationRepository @Inject constructor(
    private val dao: DomainClassificationDao
) {
    suspend fun getClassification(domain: String): DomainClassification? {
        val normalized = normalizeDomain(domain)
        return dao.getByDomain(normalized)?.toDomain()
    }

    suspend fun isBlocked(domain: String): Boolean {
        val normalized = normalizeDomain(domain)
        val classification = dao.getByDomain(normalized)?.toDomain()
        if (classification?.isBlocked == true) return true

        val parts = normalized.split(".")
        for (i in 1 until parts.size - 1) {
            val parentDomain = parts.subList(i, parts.size).joinToString(".")
            val parentClassification = dao.getByDomain(parentDomain)?.toDomain()
            if (parentClassification?.isBlocked == true) return true
        }

        return false
    }

    suspend fun isAllowlisted(domain: String): Boolean {
        val normalized = normalizeDomain(domain)
        val classification = dao.getByDomain(normalized)?.toDomain()
        return classification?.category == ClassificationCategory.SAFE &&
                classification.userOverride
    }

    suspend fun saveClassification(classification: DomainClassification) {
        val entity = classification.toEntity()
        dao.insert(entity)
        Timber.d("Saved classification for ${classification.domain}: ${classification.category}")
    }

    suspend fun blockDomain(
        domain: String,
        category: ClassificationCategory,
        source: ClassificationSource,
        confidence: Float? = null
    ) {
        val normalized = normalizeDomain(domain)
        val classification = DomainClassification(
            domain = normalized,
            category = category,
            status = ClassificationStatus.CONFIRMED,
            source = source,
            confidence = confidence,
            firstDetectedAt = Instant.now(),
            lastClassifiedAt = Instant.now(),
            isAutomatic = source != ClassificationSource.USER_MANUAL
        )
        saveClassification(classification)
        Timber.i("Blocked domain: $normalized (category: $category, source: $source)")
    }

    suspend fun allowDomain(domain: String) {
        val normalized = normalizeDomain(domain)
        val classification = DomainClassification(
            domain = normalized,
            category = ClassificationCategory.SAFE,
            status = ClassificationStatus.CONFIRMED,
            source = ClassificationSource.USER_MANUAL,
            isAutomatic = false,
            userOverride = true
        )
        saveClassification(classification)
        Timber.i("Allowlisted domain: $normalized")
    }

    suspend fun getAllBlockedDomains(): List<String> = dao.getAllBlockedDomainNames()

    suspend fun getAllAllowlistedDomains(): List<String> = dao.getAllAllowlistedDomainNames()

    fun observeBlockedDomains(): Flow<List<DomainClassification>> =
        dao.observeBlockedDomains().map { entities -> entities.map { it.toDomain() } }

    fun observeAutoDiscoveredDomains(): Flow<List<DomainClassification>> =
        dao.observeAutoDiscoveredDomains().map { entities -> entities.map { it.toDomain() } }

    fun observeBlockedCount(): Flow<Int> = dao.observeTotalBlockedCount()

    fun observeAutoBlockedCount(): Flow<Int> = dao.observeAutoBlockedCount()

    fun observeRecentClassifications(limit: Int = 20): Flow<List<DomainClassification>> =
        dao.observeRecentClassifications(limit).map { entities -> entities.map { it.toDomain() } }

    suspend fun getPendingClassifications(): List<DomainClassification> =
        dao.getPendingClassifications().map { it.toDomain() }

    private fun normalizeDomain(domain: String): String {
        return domain.lowercase()
            .removePrefix("www.")
            .removePrefix("http://")
            .removePrefix("https://")
            .removeSuffix("/")
            .trim()
    }
}
