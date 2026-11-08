package com.supervision.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_restrictions",
    indices = [
        Index(value = ["package_name"], unique = true)
    ]
)
data class AppRestrictionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    @ColumnInfo(name = "restriction_type")
    val restrictionType: String,

    @ColumnInfo(name = "daily_limit_minutes")
    val dailyLimitMinutes: Int? = null,

    @ColumnInfo(name = "is_essential")
    val isEssential: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
