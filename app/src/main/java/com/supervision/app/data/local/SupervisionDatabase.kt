package com.supervision.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.supervision.app.data.local.dao.AppRestrictionDao
import com.supervision.app.data.local.dao.BlockedRequestDao
import com.supervision.app.data.local.dao.DomainClassificationDao
import com.supervision.app.data.local.dao.ProtectionPlanDao
import com.supervision.app.data.local.dao.TamperEventDao
import com.supervision.app.data.local.dao.UsageRecordDao
import com.supervision.app.data.local.entity.AppRestrictionEntity
import com.supervision.app.data.local.entity.BlockedRequestEntity
import com.supervision.app.data.local.entity.DomainClassificationEntity
import com.supervision.app.data.local.entity.ProtectionPlanEntity
import com.supervision.app.data.local.entity.TamperEventEntity
import com.supervision.app.data.local.entity.UsageRecordEntity

@Database(
    entities = [
        DomainClassificationEntity::class,
        AppRestrictionEntity::class,
        UsageRecordEntity::class,
        ProtectionPlanEntity::class,
        TamperEventEntity::class,
        BlockedRequestEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class SupervisionDatabase : RoomDatabase() {
    abstract fun domainClassificationDao(): DomainClassificationDao
    abstract fun appRestrictionDao(): AppRestrictionDao
    abstract fun usageRecordDao(): UsageRecordDao
    abstract fun protectionPlanDao(): ProtectionPlanDao
    abstract fun tamperEventDao(): TamperEventDao
    abstract fun blockedRequestDao(): BlockedRequestDao
}

