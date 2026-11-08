package com.supervision.app.presentation.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Dashboard : Screen("dashboard")
    data object WebsiteRestrictions : Screen("websites")
    data object DynamicDetection : Screen("dynamic_detection")
    data object SocialMedia : Screen("social_media")
    data object AppRestrictions : Screen("apps")
    data object Schedules : Screen("schedules")
    data object ProtectionPlans : Screen("plans")
    data object UsageAnalytics : Screen("analytics")
    data object SecurityTamper : Screen("security")
    data object Settings : Screen("settings")
}

