package com.supervision.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "domain_classifications",
    indices = [
        Index(value = ["domain"], unique = true),
        Index(value = ["category"]),
        Index(value = ["status"]),
        Index(value = ["last_classified_at"])
    ]
)
data class DomainClassificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "domain")
    val domain: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "confidence")
    val confidence: Float? = null,

    @ColumnInfo(name = "first_detected_at")
    val firstDetectedAt: Long,

    @ColumnInfo(name = "last_classified_at")
    val lastClassifiedAt: Long,

    @ColumnInfo(name = "expires_at")
    val expiresAt: Long? = null,

    @ColumnInfo(name = "is_automatic")
    val isAutomatic: Boolean = true,

    @ColumnInfo(name = "user_override")
    val userOverride: Boolean = false
)
