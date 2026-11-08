package com.supervision.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.supervision.app.data.local.entity.DomainClassificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainClassificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DomainClassificationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<DomainClassificationEntity>)

    @Update
    suspend fun update(entity: DomainClassificationEntity)

    @Query("SELECT * FROM domain_classifications WHERE domain = :domain LIMIT 1")
    suspend fun getByDomain(domain: String): DomainClassificationEntity?

    @Query("SELECT * FROM domain_classifications WHERE domain = :domain LIMIT 1")
    fun observeByDomain(domain: String): Flow<DomainClassificationEntity?>

    @Query("SELECT * FROM domain_classifications WHERE category IN ('PORNOGRAPHY', 'NUDITY', 'OTHER_ADULT_MATERIAL') AND status = 'CONFIRMED'")
    suspend fun getAllBlockedDomains(): List<DomainClassificationEntity>

    @Query("SELECT * FROM domain_classifications WHERE category IN ('PORNOGRAPHY', 'NUDITY', 'OTHER_ADULT_MATERIAL') AND status = 'CONFIRMED'")
    fun observeBlockedDomains(): Flow<List<DomainClassificationEntity>>

    @Query("SELECT domain FROM domain_classifications WHERE category IN ('PORNOGRAPHY', 'NUDITY', 'OTHER_ADULT_MATERIAL') AND status = 'CONFIRMED'")
    suspend fun getAllBlockedDomainNames(): List<String>

    @Query("SELECT * FROM domain_classifications WHERE category = 'SAFE' AND status = 'CONFIRMED'")
    suspend fun getAllAllowedDomains(): List<DomainClassificationEntity>

    @Query("SELECT domain FROM domain_classifications WHERE category = 'SAFE' AND user_override = 1")
    suspend fun getAllAllowlistedDomainNames(): List<String>

    @Query("SELECT * FROM domain_classifications WHERE status = 'PENDING'")
    suspend fun getPendingClassifications(): List<DomainClassificationEntity>

    @Query("SELECT * FROM domain_classifications WHERE is_automatic = 1 ORDER BY last_classified_at DESC")
    fun observeAutoDiscoveredDomains(): Flow<List<DomainClassificationEntity>>

    @Query("SELECT COUNT(*) FROM domain_classifications WHERE is_automatic = 1 AND category IN ('PORNOGRAPHY', 'NUDITY', 'OTHER_ADULT_MATERIAL')")
    fun observeAutoBlockedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM domain_classifications WHERE category IN ('PORNOGRAPHY', 'NUDITY', 'OTHER_ADULT_MATERIAL') AND status = 'CONFIRMED'")
    fun observeTotalBlockedCount(): Flow<Int>

    @Query("SELECT * FROM domain_classifications WHERE last_classified_at < :threshold AND status != 'CONFIRMED'")
    suspend fun getExpiredClassifications(threshold: Long): List<DomainClassificationEntity>

    @Query("SELECT * FROM domain_classifications ORDER BY last_classified_at DESC LIMIT :limit")
    fun observeRecentClassifications(limit: Int = 20): Flow<List<DomainClassificationEntity>>

    @Query("DELETE FROM domain_classifications WHERE domain = :domain")
    suspend fun deleteByDomain(domain: String)

    @Query("DELETE FROM domain_classifications WHERE status = 'PENDING' AND last_classified_at < :threshold")
    suspend fun cleanupStalePending(threshold: Long)
}

