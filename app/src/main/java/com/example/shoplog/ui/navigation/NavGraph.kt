package com.example.shoplog.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.shoplog.ui.screens.analytics.AnalyticsMonthDetailScreen
import com.example.shoplog.ui.screens.analytics.AnalyticsMonthDetailViewModel
import com.example.shoplog.ui.screens.analytics.AnalyticsScreen
import com.example.shoplog.ui.screens.analytics.AnalyticsViewModel
import com.example.shoplog.ui.screens.details.ShoppingDetailsScreen
import com.example.shoplog.ui.screens.details.ShoppingDetailsViewModel
import com.example.shoplog.ui.screens.history.HistoryScreen
import com.example.shoplog.ui.screens.history.HistoryViewModel
import com.example.shoplog.ui.screens.home.HomeScreen
import com.example.shoplog.ui.screens.home.HomeViewModel
import com.example.shoplog.ui.screens.settings.SettingsScreen
import com.example.shoplog.ui.screens.settings.SettingsViewModel
import com.example.shoplog.ui.screens.shopping.CreateEditShoppingScreen
import com.example.shoplog.ui.screens.shopping.CreateEditShoppingViewModel

@Composable
fun MainNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = listOf(
        BottomNavItem("Home", Screen.Home.route, Icons.Default.Home),
        BottomNavItem("History", Screen.History.route, Icons.Default.History),
        BottomNavItem("Analytics", Screen.Analytics.route, Icons.Default.Analytics),
        BottomNavItem("Settings", Screen.Settings.route, Icons.Default.Settings)
    )

    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.History.route,
        Screen.Analytics.route,
        Screen.Settings.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Home Destination
            composable(Screen.Home.route) {
                val viewModel = hiltViewModel<HomeViewModel>()
                HomeScreen(
                    viewModel = viewModel,
                    onCreateNewShopping = { listId ->
                        navController.navigate(Screen.EditShopping.createRoute(listId))
                    },
                    onContinueDraft = { listId ->
                        navController.navigate(Screen.EditShopping.createRoute(listId))
                    },
                    onViewSavedDetails = { listId ->
                        navController.navigate(Screen.Details.createRoute(listId))
                    },
                    onViewHistory = {
                        navController.navigate(Screen.History.route)
                    }
                )
            }

            // Create/Edit Shopping Destination
            composable(
                route = Screen.EditShopping.route,
                arguments = listOf(navArgument("listId") { type = NavType.StringType })
            ) { backStackEntry ->
                val listId = backStackEntry.arguments?.getString("listId") ?: "new"
                val viewModel = hiltViewModel<CreateEditShoppingViewModel>()
                CreateEditShoppingScreen(
                    listIdParam = listId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onSaved = { savedListId ->
                        navController.navigate(Screen.Details.createRoute(savedListId)) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                )
            }

            // History Destination
            composable(Screen.History.route) {
                val viewModel = hiltViewModel<HistoryViewModel>()
                HistoryScreen(
                    viewModel = viewModel,
                    onViewDetails = { listId ->
                        navController.navigate(Screen.Details.createRoute(listId))
                    }
                )
            }

            // Analytics Destination
            composable(Screen.Analytics.route) {
                val viewModel = hiltViewModel<AnalyticsViewModel>()
                AnalyticsScreen(
                    viewModel = viewModel,
                    onSelectMonth = { monthYearKey ->
                        navController.navigate(Screen.AnalyticsMonthDetail.createRoute(monthYearKey))
                    }
                )
            }

            // Analytics Month Detail Destination
            composable(
                route = Screen.AnalyticsMonthDetail.route,
                arguments = listOf(navArgument("monthYearKey") { type = NavType.StringType })
            ) {
                val viewModel = hiltViewModel<AnalyticsMonthDetailViewModel>()
                AnalyticsMonthDetailScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onViewListDetails = { listId ->
                        navController.navigate(Screen.Details.createRoute(listId))
                    }
                )
            }

            // Details Destination
            composable(
                route = Screen.Details.route,
                arguments = listOf(navArgument("listId") { type = NavType.StringType })
            ) { backStackEntry ->
                val listId = backStackEntry.arguments?.getString("listId") ?: ""
                val viewModel = hiltViewModel<ShoppingDetailsViewModel>()
                ShoppingDetailsScreen(
                    listIdParam = listId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onEditList = { editListId ->
                        navController.navigate(Screen.EditShopping.createRoute(editListId))
                    }
                )
            }

            // Settings Destination
            composable(Screen.Settings.route) {
                val viewModel = hiltViewModel<SettingsViewModel>()
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

private data class BottomNavItem(
    val label: String,
    val route: String,
    val icon: ImageVector
)
