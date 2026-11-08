package com.supervision.app.data.mapper

import com.supervision.app.data.local.entity.DomainClassificationEntity
import com.supervision.app.domain.model.ClassificationCategory
import com.supervision.app.domain.model.ClassificationSource
import com.supervision.app.domain.model.ClassificationStatus
import com.supervision.app.domain.model.DomainClassification
import java.time.Instant

fun DomainClassificationEntity.toDomain(): DomainClassification {
    return DomainClassification(
        domain = domain,
        category = ClassificationCategory.valueOf(category),
        status = ClassificationStatus.valueOf(status),
        source = ClassificationSource.valueOf(source),
        confidence = confidence,
        firstDetectedAt = Instant.ofEpochMilli(firstDetectedAt),
        lastClassifiedAt = Instant.ofEpochMilli(lastClassifiedAt),
        expiresAt = expiresAt?.let { Instant.ofEpochMilli(it) },
        isAutomatic = isAutomatic,
        userOverride = userOverride
    )
}

fun DomainClassification.toEntity(): DomainClassificationEntity {
    return DomainClassificationEntity(
        domain = domain,
        category = category.name,
        status = status.name,
        source = source.name,
        confidence = confidence,
        firstDetectedAt = firstDetectedAt.toEpochMilli(),
        lastClassifiedAt = lastClassifiedAt.toEpochMilli(),
        expiresAt = expiresAt?.toEpochMilli(),
        isAutomatic = isAutomatic,
        userOverride = userOverride
    )
}
