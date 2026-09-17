package com.thedesitadka.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class MediaSourceType {
    HLS,
    DASH,
    PROGRESSIVE_MP4,
    WEBM,
    EMBEDDED_WEB
}

@Serializable
enum class DownloadType {
    DIRECT_HTTP,
    HLS_OFFLINE,
    DASH_OFFLINE,
    UNSUPPORTED
}

@Serializable
data class PlaybackCapability(
    val canPlay: Boolean = true,
    val streamUrl: String,
    val mimeType: String = "video/mp4",
    val requiresCookies: Boolean = false,
    val requiresReferer: Boolean = false
)

@Serializable
data class DownloadCapability(
    val canDownload: Boolean = false,
    val downloadType: DownloadType = DownloadType.UNSUPPORTED,
    val downloadUrl: String? = null,
    val fileExtension: String = "mp4",
    val estimatedBytes: Long? = null,
    val isResumable: Boolean = true
)

@Serializable
data class MediaSource(
    val url: String,
    val type: MediaSourceType,
    val mimeType: String = "video/mp4",
    val quality: String = "auto",
    val resolution: String? = null,
    val headersRequired: Map<String, String> = emptyMap(),
    val expiresAt: Long? = null,
    val streamUrl: String = url,
    val canPlay: Boolean = true,
    val canDownload: Boolean = (type == MediaSourceType.PROGRESSIVE_MP4),
    val downloadType: DownloadType = if (type == MediaSourceType.PROGRESSIVE_MP4) DownloadType.DIRECT_HTTP else DownloadType.UNSUPPORTED,
    val downloadUrl: String? = if (type == MediaSourceType.PROGRESSIVE_MP4) url else null,
    val fileExtension: String = if (type == MediaSourceType.HLS) "m3u8" else "mp4",
    val sizeBytes: Long? = null
)

