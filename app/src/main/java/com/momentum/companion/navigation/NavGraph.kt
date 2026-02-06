package com.momentum.companion.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.momentum.companion.ui.dashboard.DashboardScreen
import com.momentum.companion.ui.permissions.PermissionsScreen
import com.momentum.companion.ui.settings.LogsScreen
import com.momentum.companion.ui.settings.SettingsScreen
import com.momentum.companion.ui.setup.SetupScreen

sealed class Screen(val route: String) {
    data object Setup : Screen("setup")
    data object Permissions : Screen("permissions")
    data object Dashboard : Screen("dashboard")
    data object Settings : Screen("settings")
    data object Logs : Screen("logs")
}

@Composable
fun MomentumNavGraph(
    navController: NavHostController,
    isConfigured: Boolean,
) {
    NavHost(
        navController = navController,
        startDestination = if (isConfigured) Screen.Dashboard.route else Screen.Setup.route,
    ) {
        composable(Screen.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Permissions.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.Permissions.route) {
            PermissionsScreen(
                onPermissionsGranted = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Permissions.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onDisconnect = {
                    navController.navigate(Screen.Setup.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onViewLogs = { navController.navigate(Screen.Logs.route) },
            )
        }

        composable(Screen.Logs.route) {
            LogsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
