package com.supervision.app.domain.model

data class AppRestriction(
    val packageName: String,
    val appName: String,
    val restrictionType: RestrictionType,
    val dailyLimitMinutes: Int? = null,
    val usedTodayMinutes: Int = 0,
    val isEssential: Boolean = false
) {
    val remainingMinutes: Int?
        get() = dailyLimitMinutes?.let { (it - usedTodayMinutes).coerceAtLeast(0) }

    val isLimitReached: Boolean
        get() = when (restrictionType) {
            RestrictionType.COMPLETELY_BLOCKED -> true
            RestrictionType.TIME_LIMITED -> remainingMinutes == 0
            RestrictionType.ALWAYS_ALLOWED -> false
        }
}

enum class RestrictionType {
    ALWAYS_ALLOWED,
    TIME_LIMITED,
    COMPLETELY_BLOCKED
}
