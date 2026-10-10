package com.thedesitadka.app.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Provider : Screen("provider/{providerId}") {
        fun createRoute(providerId: String) = "provider/$providerId"
    }
    object CategoryList : Screen("category_list/{providerId}") {
        fun createRoute(providerId: String): String {
            val safeId = try { java.net.URLEncoder.encode(providerId.ifBlank { "unknown" }, "UTF-8") } catch (_: Exception) { providerId }
            return "category_list/$safeId"
        }
    }
    object CategoryContent : Screen("category_content/{providerId}/{title}?url={url}") {
        fun createRoute(providerId: String, title: String, url: String): String {
            val safeId = try { java.net.URLEncoder.encode(providerId.ifBlank { "unknown" }, "UTF-8") } catch (_: Exception) { providerId }
            val safeTitle = title.ifBlank { "Category" }
                .replace("/", " ")
                .replace("?", " ")
                .replace("#", " ")
                .replace("&", " ")
                .trim()
            val encTitle = try { java.net.URLEncoder.encode(safeTitle.ifBlank { "Category" }, "UTF-8") } catch (_: Exception) { "Category" }
            val encUrl = try { java.net.URLEncoder.encode(url, "UTF-8") } catch (_: Exception) { url }
            return "category_content/$safeId/$encTitle?url=$encUrl"
        }
    }
    object Search : Screen("search")
    object Details : Screen("details")
    object Player : Screen("player")
    object Downloads : Screen("downloads")
    object Settings : Screen("settings")
    object Diagnostics : Screen("diagnostics")
    object Filter : Screen("filter/{providerId}") {
        fun createRoute(providerId: String) = "filter/$providerId"
    }
}
