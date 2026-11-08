package com.supervision.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tamper_events",
    indices = [
        Index(value = ["event_type"]),
        Index(value = ["timestamp"])
    ]
)
data class TamperEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "event_type")
    val eventType: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "severity")
    val severity: String = "MEDIUM",

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "resolved")
    val resolved: Boolean = false
)
