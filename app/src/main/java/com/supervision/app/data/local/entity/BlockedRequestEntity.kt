package com.supervision.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocked_requests",
    indices = [
        Index(value = ["domain"]),
        Index(value = ["timestamp"]),
        Index(value = ["category"])
    ]
)
data class BlockedRequestEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "domain")
    val domain: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "source_app")
    val sourceApp: String? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "was_auto_discovered")
    val wasAutoDiscovered: Boolean = false
)
