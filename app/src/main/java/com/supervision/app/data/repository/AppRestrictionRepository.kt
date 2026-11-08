package com.supervision.app.data.repository

import com.supervision.app.data.local.dao.AppRestrictionDao
import com.supervision.app.data.mapper.toDomain
import com.supervision.app.data.mapper.toEntity
import com.supervision.app.domain.model.AppRestriction
import com.supervision.app.domain.model.RestrictionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRestrictionRepository @Inject constructor(
    private val dao: AppRestrictionDao
) {
    fun observeAll(): Flow<List<AppRestriction>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun getRestriction(packageName: String): AppRestriction? =
        dao.getByPackageName(packageName)?.toDomain()

    suspend fun setRestriction(restriction: AppRestriction) {
        dao.insert(restriction.toEntity())
        Timber.d("Set restriction for ${restriction.packageName}: ${restriction.restrictionType}")
    }

    suspend fun getTimeLimitedApps(): List<AppRestriction> =
        dao.getTimeLimited().map { it.toDomain() }

    suspend fun getBlockedApps(): List<AppRestriction> =
        dao.getCompletelyBlocked().map { it.toDomain() }

    suspend fun isAppBlocked(packageName: String): Boolean {
        val restriction = dao.getByPackageName(packageName) ?: return false
        return restriction.restrictionType == RestrictionType.COMPLETELY_BLOCKED.name
    }

    suspend fun isAppTimeLimited(packageName: String): Boolean {
        val restriction = dao.getByPackageName(packageName) ?: return false
        return restriction.restrictionType == RestrictionType.TIME_LIMITED.name
    }

    suspend fun removeRestriction(packageName: String) =
        dao.deleteByPackageName(packageName)

    fun observeRestrictedCount(): Flow<Int> = dao.observeRestrictedCount()

    suspend fun initializeEssentialApps() {
        val defaultApps = listOf(
            AppRestriction("com.instagram.android", "Instagram", RestrictionType.TIME_LIMITED, dailyLimitMinutes = 30, isEssential = false),
            AppRestriction("com.twitter.android", "X (Twitter)", RestrictionType.COMPLETELY_BLOCKED, isEssential = false),
            AppRestriction("com.reddit.frontpage", "Reddit", RestrictionType.COMPLETELY_BLOCKED, isEssential = false),
            AppRestriction("com.zhiliaoapp.musically", "TikTok", RestrictionType.COMPLETELY_BLOCKED, isEssential = false),
            AppRestriction("com.microsoft.teams", "Microsoft Teams", RestrictionType.ALWAYS_ALLOWED, isEssential = true),
            AppRestriction("com.microsoft.office.outlook", "Outlook", RestrictionType.ALWAYS_ALLOWED, isEssential = true),
            AppRestriction("com.android.phone", "Phone", RestrictionType.ALWAYS_ALLOWED, isEssential = true),
            AppRestriction("com.android.messaging", "Messages", RestrictionType.ALWAYS_ALLOWED, isEssential = true),
            AppRestriction("com.google.android.apps.messaging", "Messages", RestrictionType.ALWAYS_ALLOWED, isEssential = true),
            AppRestriction("com.google.android.dialer", "Phone", RestrictionType.ALWAYS_ALLOWED, isEssential = true),
            AppRestriction("com.android.dialer", "Phone", RestrictionType.ALWAYS_ALLOWED, isEssential = true),
            AppRestriction("com.android.emergency", "Emergency", RestrictionType.ALWAYS_ALLOWED, isEssential = true)
        )
        defaultApps.forEach { app ->
            if (dao.getByPackageName(app.packageName) == null) {
                dao.insert(app.toEntity())
            }
        }
    }
}

