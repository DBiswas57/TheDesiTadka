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
    val metadata: Map<String, String> = emptyMap()
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
