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
import java.net.URI

/**
 * Resolver plugin for external video hosting on Vidara (vidara.so, vidara.to, vidara.net, vidara.cc, gbsagbo.com).
 *
 * Architecture:
 * - Sites like livecamrips.to embed:
 *   `<iframe id="iframe-video" src="https://vidara.so/e/{filecode}"></iframe>`.
 * - Vidara's web player calls client-side API endpoint:
 *   `POST https://{domain}/api/stream` with JSON `{"filecode":"{filecode}","device":"mobile"}`.
 * - The API responds with JSON containing `streaming_url` (HLS master playlist or MP4) and metadata.
 * - Direct playback requires headers:
 *   `User-Agent`, `Referer: https://{domain}/`, `Origin: https://{domain}`.
 */
class VidaraResolverPlugin : HostResolverPlugin {

    override val id: String = "vidara"
    override val name: String = "Vidara Video Host Resolver"
    override val supportedDomains: List<String> = listOf(
        "vidara.so",
        "vidara.to",
        "vidara.net",
        "vidara.cc",
        "gbsagbo.com"
    )

    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase().trim()
        return lower.contains("vidara.") ||
                lower.contains("vidara/") ||
                lower.contains("gbsagbo.com")
    }

    override suspend fun resolve(embedUrl: String, parentUrl: String?): Result<MediaSource> = withContext(Dispatchers.IO) {
        resolveSync(embedUrl, parentUrl)
    }

    override fun resolveFromHtml(html: String, embedUrl: String, parentUrl: String?): Result<MediaSource>? {
        return resolveSync(embedUrl, parentUrl, preloadedHtml = html)
    }

    /**
     * Synchronously resolves a Vidara embed URL to a direct MediaSource.
     */
    fun resolveSync(embedUrl: String, parentUrl: String? = null, preloadedHtml: String? = null): Result<MediaSource> {
        try {
            val normalizedUrl = normalizeEmbedUrl(embedUrl)
            val filecode = extractFilecode(normalizedUrl)
                ?: return Result.failure(StreamHubError.PlaybackError(400, "Could not extract filecode from Vidara embed: $embedUrl"))

            val domain = extractDomain(normalizedUrl)

            // 1. If HTML is preloaded, check for direct streaming_url or setupPlayer
            if (!preloadedHtml.isNullOrBlank()) {
                val directMatch = extractStreamFromHtml(preloadedHtml)
                if (directMatch != null) {
                    return Result.success(buildMediaSource(directMatch, domain))
                }
            }

            // 2. Call /api/stream endpoint
            val apiUrl = "https://$domain/api/stream"
            val jsonPayload = """{"filecode":"$filecode","device":"mobile"}"""

            val reqHeaders = mapOf(
                "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
                "Referer" to normalizedUrl,
                "Origin" to "https://$domain",
                "Content-Type" to "application/json"
            )

            val responseBody = try {
                NetworkClient.postJson(apiUrl, jsonPayload, reqHeaders)
            } catch (e: Exception) {
                // Fallback to vidara.so if alternate/mirror domain failed
                if (domain != "vidara.so") {
                    val fallbackApi = "https://vidara.so/api/stream"
                    val fallbackHeaders = mapOf(
                        "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
                        "Referer" to "https://vidara.so/e/$filecode",
                        "Origin" to "https://vidara.so",
                        "Content-Type" to "application/json"
                    )
                    NetworkClient.postJson(fallbackApi, jsonPayload, fallbackHeaders)
                } else {
                    throw e
                }
            }

            val jsonElement = try {
                Json.parseToJsonElement(responseBody) as? JsonObject
            } catch (_: Exception) {
                null
            }

            val errorMsg = jsonElement?.get("error")?.jsonPrimitive?.content
            if (!errorMsg.isNullOrBlank()) {
                return Result.failure(StreamHubError.PlaybackError(404, "Vidara error: $errorMsg"))
            }

            val streamingUrl = jsonElement?.get("streaming_url")?.jsonPrimitive?.content
            if (streamingUrl.isNullOrBlank()) {
                return Result.failure(StreamHubError.PlaybackError(404, "No streaming_url in Vidara response"))
            }

            return Result.success(buildMediaSource(streamingUrl, domain))
        } catch (e: Exception) {
            StreamHubLogger.e("VidaraResolverPlugin", "Failed to resolve Vidara embed $embedUrl: ${e.message}")
            return Result.failure(StreamHubError.PlaybackError(500, "Vidara resolver failed: ${e.message}", e))
        }
    }

    private fun buildMediaSource(streamUrl: String, domain: String): MediaSource {
        val isHls = streamUrl.contains(".m3u8")
        val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
        val mime = if (isHls) "application/vnd.apple.mpegurl" else "video/mp4"

        val headers = mapOf(
            "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
            "Referer" to "https://$domain/",
            "Origin" to "https://$domain"
        )

        return MediaSource(
            url = streamUrl,
            type = type,
            mimeType = mime,
            headersRequired = headers
        )
    }

    fun extractFilecode(url: String): String? {
        val trimmed = url.trim()
        val eMatch = Regex("""(?:/e/|/embed/|filecode=)([a-zA-Z0-9_-]+)""").find(trimmed)
        if (eMatch != null) return eMatch.groupValues[1]

        val lastSeg = trimmed.substringAfterLast('/').substringBefore('?').substringBefore('#')
        if (lastSeg.isNotBlank() && lastSeg.length in 6..40 && !lastSeg.contains('.')) {
            return lastSeg
        }
        return null
    }

    fun extractStreamFromHtml(html: String): String? {
        val match = Regex("""(?:sources|file):\s*(?:\[\s*\{\s*(?:file|src)\s*:\s*["']([^"']+)["']|["']([^"']+\.(?:mp4|m3u8)[^"']*)["'])""").find(html)
        if (match != null) {
            val u = match.groupValues[1].ifEmpty { match.groupValues[2] }.trim()
            if (u.isNotBlank()) return u
        }
        val directMatch = Regex("""https?://[^\s"'<>]+\.(?:mp4|m3u8)[^\s"'<>]*""").find(html)
        return directMatch?.value
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = URI(url)
            uri.host ?: "vidara.so"
        } catch (e: Exception) {
            "vidara.so"
        }
    }

    private fun normalizeEmbedUrl(url: String): String {
        var trimmed = url.trim()
        if (trimmed.startsWith("//")) {
            trimmed = "https:$trimmed"
        } else if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://$trimmed"
        }
        return trimmed
    }
}
