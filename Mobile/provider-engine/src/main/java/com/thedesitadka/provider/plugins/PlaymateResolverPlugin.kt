package com.thedesitadka.provider.plugins

import com.thedesitadka.core.model.MediaSource
import com.thedesitadka.core.model.MediaSourceType
import com.thedesitadka.core.model.StreamHubError
import com.thedesitadka.core.network.NetworkClient
import com.thedesitadka.core.security.StreamHubLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Resolver plugin for Playmate video hosting embeds (playmate.to).
 *
 * Architecture:
 * - Sites like masahub.net.im embed: `<iframe src="https://playmate.to/embed/{filecode}" ...>`.
 * - Endpoint: `POST https://playmate.to/api/s` with JSON `{"c": "{filecode}", "d": "android"}`.
 * - Headers:
 *   - `Referer: https://playmate.to/embed/{filecode}`
 *   - `Origin: https://playmate.to`
 *   - `Content-Type: application/json`
 * - Response JSON:
 *   `{"sx": "https://frv2.plauymito.live/hls/{token}/master.txt", ...}`
 * - The stream is HLS delivered with `.txt` extension disguised manifest.
 * - Playback & download require:
 *   - `Referer: https://playmate.to/`
 *   - `Origin: https://playmate.to`
 */
class PlaymateResolverPlugin : HostResolverPlugin {

    override val id: String = "playmate"
    override val name: String = "Playmate Video Host Resolver"
    override val supportedDomains: List<String> = listOf(
        "playmate.to",
        "plauymito.live",
        "playmate.me",
        "playmate.ws"
    )

    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("playmate.to") ||
                lower.contains("plauymito.live") ||
                lower.contains("playmate.me") ||
                lower.contains("playmate.ws")
    }

    override suspend fun resolve(embedUrl: String, parentUrl: String?): Result<MediaSource> = withContext(Dispatchers.IO) {
        resolveSync(embedUrl, parentUrl)
    }

    override fun resolveFromHtml(html: String, embedUrl: String, parentUrl: String?): Result<MediaSource>? {
        return resolveSync(embedUrl, parentUrl)
    }

    /**
     * Synchronously resolves the Playmate embed URL to a direct MediaSource.
     */
    fun resolveSync(embedUrl: String, parentUrl: String? = null): Result<MediaSource> {
        try {
            val filecode = extractFilecode(embedUrl)
                ?: return Result.failure(StreamHubError.PlaybackError(400, "Could not extract filecode from Playmate URL: $embedUrl"))

            val apiUrl = "https://playmate.to/api/s"
            val jsonBody = """{"c":"$filecode","d":"android"}"""

            val reqHeaders = mapOf(
                "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
                "Referer" to "https://playmate.to/embed/$filecode",
                "Origin" to "https://playmate.to",
                "Content-Type" to "application/json"
            )

            val responseString = NetworkClient.postJson(apiUrl, jsonBody, reqHeaders)
            val jsonElement = Json.parseToJsonElement(responseString) as? JsonObject
            val streamUrl = jsonElement?.get("sx")?.jsonPrimitive?.content

            if (streamUrl.isNullOrBlank()) {
                val errorMsg = jsonElement?.get("message")?.jsonPrimitive?.content
                    ?: "Playmate returned empty stream URL (file may be deleted or blocked)"
                return Result.failure(StreamHubError.PlaybackError(404, errorMsg))
            }

            val headers = mapOf(
                "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
                "Referer" to "https://playmate.to/",
                "Origin" to "https://playmate.to"
            )

            val isHls = streamUrl.contains("/hls/") || streamUrl.contains(".m3u8") || streamUrl.contains(".txt")
            val mediaType = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
            val mimeType = if (isHls) "application/vnd.apple.mpegurl" else "video/mp4"

            return Result.success(
                MediaSource(
                    url = streamUrl,
                    type = mediaType,
                    mimeType = mimeType,
                    headersRequired = headers
                )
            )
        } catch (e: Exception) {
            StreamHubLogger.e("PlaymateResolverPlugin", "Failed to resolve Playmate URL: $embedUrl: ${e.message}")
            return Result.failure(StreamHubError.PlaybackError(500, "Playmate resolver failed: ${e.message}", e))
        }
    }

    /**
     * Extracts video filecode from embed URL.
     */
    fun extractFilecode(url: String): String? {
        val slugRegex = Regex("""/(?:embed|e|d)/([A-Za-z0-9_-]+)""")
        val match = slugRegex.find(url)
        if (match != null) {
            return match.groupValues[1]
        }
        val clean = url.substringBefore("?").trimEnd('/')
        val lastSegment = clean.substringAfterLast("/")
        if (lastSegment.length >= 5 && !lastSegment.contains(".")) {
            return lastSegment
        }
        return null
    }
}
