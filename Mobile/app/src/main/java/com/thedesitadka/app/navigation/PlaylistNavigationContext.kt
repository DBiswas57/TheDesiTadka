package com.thedesitadka.app.navigation

import com.thedesitadka.core.model.VideoItem

enum class PlaylistSourceType {
    CATEGORY,
    PROVIDER_FEED,
    DASHBOARD,
    SEARCH,
    SERIES,
    RELATED
}

data class PlaylistNavigationContext(
    val sourceType: PlaylistSourceType = PlaylistSourceType.DASHBOARD,
    val providerId: String = "",
    val categoryUrl: String = "",
    val searchQuery: String = "",
    val currentPage: Int = 1,
    val hasNextPage: Boolean = false
)

object VideoPlaylistHelper {
    fun normalizeUrl(url: String): String {
        if (url.isBlank()) return ""
        return url.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("//")
            .trimEnd('/')
            .lowercase()
    }

    fun isSameVideo(v1: VideoItem, v2: VideoItem): Boolean {
        if (v1.id.isNotBlank() && v2.id.isNotBlank() && v1.id == v2.id) return true
        val u1 = normalizeUrl(v1.detailUrl)
        val u2 = normalizeUrl(v2.detailUrl)
        if (u1.isNotBlank() && u2.isNotBlank()) {
            if (u1 == u2) return true
            if (u1.endsWith(u2) || u2.endsWith(u1)) return true
            val path1 = u1.substringAfter('/')
            val path2 = u2.substringAfter('/')
            if (path1.isNotBlank() && path1 == path2) return true
        }
        if (v1.providerId.isNotBlank() && v1.providerId == v2.providerId && v1.title.isNotBlank() && v2.title.isNotBlank()) {
            if (v1.title.trim().equals(v2.title.trim(), ignoreCase = true)) return true
        }
        return false
    }

    fun findVideoIndex(playlist: List<VideoItem>, target: VideoItem): Int {
        return playlist.indexOfFirst { isSameVideo(it, target) }
    }
}
