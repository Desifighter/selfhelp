package com.supervision.app.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.supervision.app.presentation.apps.AppRestrictionsScreen
import com.supervision.app.presentation.apps.AppRestrictionsViewModel
import com.supervision.app.presentation.dashboard.DashboardScreen
import com.supervision.app.presentation.dashboard.DashboardViewModel
import com.supervision.app.presentation.onboarding.OnboardingScreen
import com.supervision.app.presentation.onboarding.OnboardingViewModel
import com.supervision.app.presentation.schedules.SchedulesScreen
import com.supervision.app.presentation.settings.SettingsScreen
import com.supervision.app.presentation.websites.WebsiteRestrictionsScreen
import com.supervision.app.presentation.websites.WebsiteRestrictionsViewModel

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Dashboard.route
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Screen.Dashboard.route,
        Screen.WebsiteRestrictions.route,
        Screen.AppRestrictions.route,
        Screen.Settings.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Screen.Dashboard.route,
                        onClick = {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                        label = { Text("Dashboard") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.WebsiteRestrictions.route,
                        onClick = {
                            navController.navigate(Screen.WebsiteRestrictions.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Language, contentDescription = "Websites") },
                        label = { Text("Websites") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.AppRestrictions.route,
                        onClick = {
                            navController.navigate(Screen.AppRestrictions.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Apps, contentDescription = "Apps") },
                        label = { Text("Apps") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Settings.route,
                        onClick = {
                            navController.navigate(Screen.Settings.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Onboarding.route) {
                val viewModel: OnboardingViewModel = hiltViewModel()
                OnboardingScreen(
                    viewModel = viewModel,
                    onFinish = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Dashboard.route) {
                val viewModel: DashboardViewModel = hiltViewModel()
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToWebsites = { navController.navigate(Screen.WebsiteRestrictions.route) },
                    onNavigateToApps = { navController.navigate(Screen.AppRestrictions.route) },
                    onNavigateToDynamicDetection = { navController.navigate(Screen.WebsiteRestrictions.route) }
                )
            }

            composable(Screen.WebsiteRestrictions.route) {
                val viewModel: WebsiteRestrictionsViewModel = hiltViewModel()
                WebsiteRestrictionsScreen(viewModel = viewModel)
            }

            composable(Screen.AppRestrictions.route) {
                val viewModel: AppRestrictionsViewModel = hiltViewModel()
                AppRestrictionsScreen(viewModel = viewModel)
            }

            composable(Screen.Schedules.route) {
                SchedulesScreen()
            }

            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}

