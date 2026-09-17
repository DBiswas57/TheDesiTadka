package com.thedesitadka.app.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Provider : Screen("provider/{providerId}") {
        fun createRoute(providerId: String) = "provider/$providerId"
    }
    object Search : Screen("search")
    object Details : Screen("details")
    object Player : Screen("player")
    object Downloads : Screen("downloads")
    object Settings : Screen("settings")
    object Diagnostics : Screen("diagnostics")
}
