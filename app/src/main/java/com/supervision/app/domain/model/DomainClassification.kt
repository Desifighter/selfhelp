package com.supervision.app.domain.model

import java.time.Instant

enum class ClassificationCategory {
    PORNOGRAPHY,
    NUDITY,
    OTHER_ADULT_MATERIAL,
    SEX_EDUCATION,
    SAFE,
    SOCIAL_MEDIA,
    UNKNOWN,
    UNCERTAIN
}

enum class ClassificationStatus {
    CONFIRMED,
    PENDING,
    FAILED,
    EXPIRED,
    USER_OVERRIDE
}

enum class ClassificationSource {
    BLOCKLIST,
    ALLOWLIST,
    EXTERNAL_API,
    REPUTATION_FEED,
    ON_DEVICE,
    USER_MANUAL,
    DEFAULT_POLICY
}

data class DomainClassification(
    val domain: String,
    val category: ClassificationCategory,
    val status: ClassificationStatus,
    val source: ClassificationSource,
    val confidence: Float? = null,
    val firstDetectedAt: Instant = Instant.now(),
    val lastClassifiedAt: Instant = Instant.now(),
    val expiresAt: Instant? = null,
    val isAutomatic: Boolean = true,
    val userOverride: Boolean = false
) {
    val isBlocked: Boolean
        get() = category in setOf(
            ClassificationCategory.PORNOGRAPHY,
            ClassificationCategory.NUDITY,
            ClassificationCategory.OTHER_ADULT_MATERIAL
        ) && status == ClassificationStatus.CONFIRMED

    val needsReclassification: Boolean
        get() = status == ClassificationStatus.EXPIRED ||
                (expiresAt != null && Instant.now().isAfter(expiresAt))
}
