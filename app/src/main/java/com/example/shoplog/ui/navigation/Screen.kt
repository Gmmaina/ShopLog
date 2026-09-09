package com.example.shoplog.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object CreateShopping : Screen("create_shopping")
    object EditShopping : Screen("edit_shopping/{listId}") {
        fun createRoute(listId: String) = "edit_shopping/$listId"
    }
    object History : Screen("history")
    object Analytics : Screen("analytics")
    object AnalyticsMonthDetail : Screen("analytics_detail/{monthYearKey}") {
        fun createRoute(monthYearKey: String) = "analytics_detail/$monthYearKey"
    }
    object Details : Screen("details/{listId}") {
        fun createRoute(listId: String) = "details/$listId"
    }
    object Settings : Screen("settings")
}
