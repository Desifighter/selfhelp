package com.supervision.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRestrictionModelTest {

    @Test
    fun `time limited app calculates remaining minutes correctly`() {
        val app = AppRestriction(
            packageName = "com.instagram.android",
            appName = "Instagram",
            restrictionType = RestrictionType.TIME_LIMITED,
            dailyLimitMinutes = 30,
            usedTodayMinutes = 10
        )

        assertEquals(20, app.remainingMinutes)
        assertFalse(app.isLimitReached)
    }

    @Test
    fun `time limited app reports limit reached when time is exhausted`() {
        val app = AppRestriction(
            packageName = "com.instagram.android",
            appName = "Instagram",
            restrictionType = RestrictionType.TIME_LIMITED,
            dailyLimitMinutes = 30,
            usedTodayMinutes = 30
        )

        assertEquals(0, app.remainingMinutes)
        assertTrue(app.isLimitReached)
    }

    @Test
    fun `completely blocked app is always limit reached`() {
        val app = AppRestriction(
            packageName = "com.twitter.android",
            appName = "X",
            restrictionType = RestrictionType.COMPLETELY_BLOCKED
        )

        assertTrue(app.isLimitReached)
    }

    @Test
    fun `always allowed app is never limit reached`() {
        val app = AppRestriction(
            packageName = "com.microsoft.teams",
            appName = "Microsoft Teams",
            restrictionType = RestrictionType.ALWAYS_ALLOWED,
            isEssential = true,
            usedTodayMinutes = 240
        )

        assertFalse(app.isLimitReached)
    }
}

