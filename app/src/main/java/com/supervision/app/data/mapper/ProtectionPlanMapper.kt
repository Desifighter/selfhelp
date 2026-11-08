package com.supervision.app.data.mapper

import com.supervision.app.data.local.entity.ProtectionPlanEntity
import com.supervision.app.domain.model.ProtectionMode
import com.supervision.app.domain.model.ProtectionPlan
import com.supervision.app.domain.model.UnknownDomainPolicy
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

fun ProtectionPlanEntity.toDomain(): ProtectionPlan {
    return ProtectionPlan(
        id = id,
        name = name,
        isActive = isActive,
        protectionMode = ProtectionMode.valueOf(protectionMode),
        unknownDomainPolicy = UnknownDomainPolicy.valueOf(unknownDomainPolicy),
        startDate = startDate?.let { Instant.ofEpochMilli(it) },
        endDate = endDate?.let { Instant.ofEpochMilli(it) },
        activeDays = activeDays.split(",").filter { it.isNotBlank() }.map { DayOfWeek.valueOf(it.trim()) }.toSet(),
        dailyResetTime = LocalTime.parse(dailyResetTime),
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt)
    )
}

fun ProtectionPlan.toEntity(): ProtectionPlanEntity {
    return ProtectionPlanEntity(
        id = id,
        name = name,
        isActive = isActive,
        protectionMode = protectionMode.name,
        unknownDomainPolicy = unknownDomainPolicy.name,
        startDate = startDate?.toEpochMilli(),
        endDate = endDate?.toEpochMilli(),
        activeDays = activeDays.joinToString(",") { it.name },
        dailyResetTime = dailyResetTime.toString(),
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli()
    )
}
