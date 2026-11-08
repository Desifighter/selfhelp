package com.supervision.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "protection_plans")
data class ProtectionPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String = "Default Plan",

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = false,

    @ColumnInfo(name = "protection_mode")
    val protectionMode: String = "STANDARD",

    @ColumnInfo(name = "unknown_domain_policy")
    val unknownDomainPolicy: String = "BALANCED",

    @ColumnInfo(name = "start_date")
    val startDate: Long? = null,

    @ColumnInfo(name = "end_date")
    val endDate: Long? = null,

    @ColumnInfo(name = "active_days")
    val activeDays: String = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY",

    @ColumnInfo(name = "daily_reset_time")
    val dailyResetTime: String = "00:00",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
