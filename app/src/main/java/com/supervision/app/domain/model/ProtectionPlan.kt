package com.supervision.app.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

data class ProtectionPlan(
    val id: Long = 0,
    val name: String = "Default Plan",
    val isActive: Boolean = false,
    val protectionMode: ProtectionMode = ProtectionMode.STANDARD,
    val unknownDomainPolicy: UnknownDomainPolicy = UnknownDomainPolicy.STRICT,
    val startDate: Instant? = null,
    val endDate: Instant? = null,
    val activeDays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val dailyResetTime: LocalTime = LocalTime.MIDNIGHT,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    val isExpired: Boolean
        get() = endDate != null && Instant.now().isAfter(endDate)

    val daysRemaining: Long
        get() {
            val end = endDate ?: return Long.MAX_VALUE
            val now = Instant.now()
            return if (now.isBefore(end)) {
                java.time.Duration.between(now, end).toDays()
            } else 0
        }
}

enum class ProtectionMode {
    STANDARD,
    ENHANCED
}

enum class UnknownDomainPolicy {
    STRICT,
    BALANCED
}
