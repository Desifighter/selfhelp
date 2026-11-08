package com.supervision.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "usage_records",
    indices = [
        Index(value = ["package_name", "date"], unique = true),
        Index(value = ["date"])
    ]
)
data class UsageRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "total_foreground_minutes")
    val totalForegroundMinutes: Int = 0,

    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long? = null,

    @ColumnInfo(name = "launch_count")
    val launchCount: Int = 0
)
