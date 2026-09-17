package com.thedesitadka.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class MediaResolutionState {
    UNKNOWN,
    RESOLVING,
    READY,
    PLAYABLE,
    DOWNLOADABLE,
    UNAVAILABLE,
    ERROR
}

@Serializable
data class MediaResolutionResult(
    val videoId: String,
    val mediaUri: String = "",
    val mediaType: MediaSourceType = MediaSourceType.PROGRESSIVE_MP4,
    val mimeType: String = "video/mp4",
    val headersRequired: Map<String, String> = emptyMap(),
    val filename: String? = null,
    val providerId: String = "",
    val isPlayable: Boolean = false,
    val isDownloadable: Boolean = false,
    val state: MediaResolutionState = MediaResolutionState.UNKNOWN,
    val errorMessage: String? = null
) {
    fun toMediaSource(): MediaSource {
        return MediaSource(
            url = mediaUri,
            type = mediaType,
            mimeType = mimeType,
            headersRequired = headersRequired,
            canPlay = isPlayable,
            canDownload = isDownloadable,
            downloadUrl = if (isDownloadable) mediaUri else null,
            fileExtension = if (mediaType == MediaSourceType.HLS) "m3u8" else "mp4"
        )
    }

    companion object {
        fun resolving(videoId: String, providerId: String): MediaResolutionResult {
            return MediaResolutionResult(
                videoId = videoId,
                providerId = providerId,
                state = MediaResolutionState.RESOLVING
            )
        }

        fun fromMediaSource(
            videoId: String,
            providerId: String,
            source: MediaSource,
            isProviderDownloadAuthorized: Boolean = true,
            title: String? = null
        ): MediaResolutionResult {
            val isPlayable = source.canPlay && source.url.isNotBlank() && source.type != MediaSourceType.EMBEDDED_WEB
            // Download manager supports direct progressive files (not segmented HLS/DASH)
            val isStreamingProtocol = source.type == MediaSourceType.HLS ||
                    source.type == MediaSourceType.DASH ||
                    source.url.contains(".m3u8", ignoreCase = true) ||
                    source.url.contains(".mpd", ignoreCase = true)

            val isDownloadable = isPlayable &&
                    isProviderDownloadAuthorized &&
                    !isStreamingProtocol &&
                    (source.canDownload || source.downloadType == DownloadType.DIRECT_HTTP || source.downloadUrl != null)

            val state = when {
                isDownloadable -> MediaResolutionState.DOWNLOADABLE
                isPlayable -> MediaResolutionState.PLAYABLE
                else -> MediaResolutionState.UNAVAILABLE
            }

            val sanitizedTitle = title?.replace(Regex("[^a-zA-Z0-9._-]"), "_")?.take(60)
            val filename = if (!sanitizedTitle.isNullOrBlank()) "$sanitizedTitle.mp4" else null

            return MediaResolutionResult(
                videoId = videoId,
                mediaUri = source.url,
                mediaType = source.type,
                mimeType = source.mimeType,
                headersRequired = source.headersRequired,
                filename = filename,
                providerId = providerId,
                isPlayable = isPlayable,
                isDownloadable = isDownloadable,
                state = state
            )
        }

        fun unavailable(videoId: String, providerId: String, reason: String): MediaResolutionResult {
            return MediaResolutionResult(
                videoId = videoId,
                providerId = providerId,
                state = MediaResolutionState.UNAVAILABLE,
                errorMessage = reason
            )
        }

        fun error(videoId: String, providerId: String, error: String): MediaResolutionResult {
            return MediaResolutionResult(
                videoId = videoId,
                providerId = providerId,
                state = MediaResolutionState.ERROR,
                errorMessage = error
            )
        }
    }
}
