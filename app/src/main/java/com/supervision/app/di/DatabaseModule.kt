package com.supervision.app.di

import android.content.Context
import androidx.room.Room
import com.supervision.app.data.local.SupervisionDatabase
import com.supervision.app.data.local.dao.AppRestrictionDao
import com.supervision.app.data.local.dao.BlockedRequestDao
import com.supervision.app.data.local.dao.DomainClassificationDao
import com.supervision.app.data.local.dao.ProtectionPlanDao
import com.supervision.app.data.local.dao.TamperEventDao
import com.supervision.app.data.local.dao.UsageRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SupervisionDatabase {
        return Room.databaseBuilder(
            context,
            SupervisionDatabase::class.java,
            "supervision_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideDomainClassificationDao(db: SupervisionDatabase): DomainClassificationDao =
        db.domainClassificationDao()

    @Provides
    fun provideAppRestrictionDao(db: SupervisionDatabase): AppRestrictionDao =
        db.appRestrictionDao()

    @Provides
    fun provideUsageRecordDao(db: SupervisionDatabase): UsageRecordDao =
        db.usageRecordDao()

    @Provides
    fun provideProtectionPlanDao(db: SupervisionDatabase): ProtectionPlanDao =
        db.protectionPlanDao()

    @Provides
    fun provideTamperEventDao(db: SupervisionDatabase): TamperEventDao =
        db.tamperEventDao()

    @Provides
    fun provideBlockedRequestDao(db: SupervisionDatabase): BlockedRequestDao =
        db.blockedRequestDao()
}

