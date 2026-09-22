package com.thedesitadka.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class PlaybackAvailability {
    AVAILABLE,
    GEO_RESTRICTED,
    AUTH_REQUIRED,
    UNAVAILABLE
}

@Serializable
enum class DownloadAvailability {
    AUTHORIZED,
    RESTRICTED,
    NOT_SUPPORTED
}

@Serializable
data class VideoItem(
    val id: String,
    val providerId: String,
    val title: String,
    val description: String = "",
    val thumbnailUrl: String = "",
    val durationSeconds: Long? = null,
    val publishedAt: String? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val detailUrl: String,
    val playbackAvailability: PlaybackAvailability = PlaybackAvailability.AVAILABLE,
    val downloadAvailability: DownloadAvailability = DownloadAvailability.NOT_SUPPORTED,
    val metadata: Map<String, String> = emptyMap(),
    val isCategory: Boolean = false
)

@Serializable
data class FeedPage(
    val items: List<VideoItem>,
    val page: Int,
    val hasNextPage: Boolean,
    val totalPages: Int? = null
)

@Serializable
data class Category(
    val id: String,
    val name: String,
    val url: String = ""
)

fun isCategoryUrl(url: String): Boolean {
    val lower = url.lowercase().trim()
    // Single video pages with ID or date path are playable items, never categories
    if (Regex("""/videos?/\d+""").containsMatchIn(lower) ||
        Regex("""/hdporn/\d+""").containsMatchIn(lower) ||
        Regex("""/\d{4}/\d{2}/""").containsMatchIn(lower)) {
        return false
    }
    return lower.contains("/category/") ||
            lower.contains("/categories/") ||
            lower.endsWith("/categories") ||
            lower.contains("/channels/") ||
            lower.contains("/channel/") ||
            lower.contains("/models/") ||
            lower.contains("/model/") ||
            lower.contains("/models-") ||
            lower.contains("/pornstars/") ||
            lower.contains("/pornstar/") ||
            lower.contains("/girls") ||
            lower.contains("/actress/") ||
            lower.contains("/actresses/") ||
            lower.contains("/actors/") ||
            lower.contains("/studios/") ||
            lower.contains("/studio/") ||
            lower.contains("/tags/") ||
            lower.contains("/tag/") ||
            lower.contains("/genre/") ||
            lower.contains("/genres/") ||
            lower.contains("/sites/") ||
            lower.contains("/site/") ||
            lower.contains("/paysite/") ||
            lower.contains("/paysitelist") ||
            lower.contains("search/?s=") ||
            lower.contains("search?s=") ||
            lower.contains("/ott/") ||
            lower.endsWith("/ott") ||
            lower.contains("/series/") ||
            lower.contains("/sortby/") ||
            lower.contains("/today") ||
            lower.contains("/updated") ||
            lower.contains("filter=") ||
            lower.contains("/label/") ||
            lower.contains("/labels/") ||
            lower.contains("/pmvideo/") ||
            lower.contains("/popular/") ||
            lower.contains("/star/") ||
            lower.contains("?cat=") ||
            lower.contains("&cat=") ||
            Regex("""/[a-z]/?$""").containsMatchIn(lower) ||
            Regex("""/[a-z]/\d+/?$""").containsMatchIn(lower)
}


