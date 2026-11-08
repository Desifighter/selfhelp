package com.supervision.app.domain.model

import java.time.Instant
import java.time.LocalDate

data class UsageRecord(
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val date: LocalDate,
    val totalForegroundMinutes: Int = 0,
    val lastUsedAt: Instant? = null,
    val launchCount: Int = 0
)

data class DailyUsageSummary(
    val date: LocalDate,
    val totalScreenTimeMinutes: Int,
    val appUsages: List<UsageRecord>,
    val blockedAttempts: Int,
    val domainsDiscovered: Int
)
