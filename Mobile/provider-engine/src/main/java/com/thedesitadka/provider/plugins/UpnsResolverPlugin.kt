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
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Resolver plugin for external video hosting on UPNS / VideoSH (videosh.upns.live, upns.live, upnshare.com, upns.co).
 *
 * Architecture:
 * - Sites like livecamrips.to embed:
 *   `<iframe id="iframe-video" src="https://videosh.upns.live/#{videoId}&ua=1"></iframe>`.
 * - The web player fetches `https://videosh.upns.live/api/v1/video?id={videoId}&w={w}&h={h}&r={referer}`.
 * - The response is encrypted with AES-128-CBC using:
 *   Key: "kiemtienmua911ca" (16 bytes)
 *   IV: "1234567890oiuytr" (16 bytes)
 * - Decrypted JSON contains multiple stream candidates:
 *   `cfNative`: Direct signed Cloudflare master HLS playlist
 *   `source`: Direct storage cluster master HLS playlist
 *   `hlsVideoTiktok`: TikTok proxy HLS stream
 * - Direct playback requires headers:
 *   `User-Agent`, `Referer: https://{domain}/`, `Origin: https://{domain}`.
 */
class UpnsResolverPlugin : HostResolverPlugin {

    override val id: String = "upns"
    override val name: String = "UPNS & VideoSH Video Host Resolver"
    override val supportedDomains: List<String> = listOf(
        "videosh.upns.live",
        "upns.live",
        "upnshare.com",
        "upns.co"
    )

    companion object {
        // Derived AES-128 key and IV matching the web player's Z() and J() functions
        private val DEFAULT_KEY = "kiemtienmua911ca".toByteArray(Charsets.UTF_8)
        private val DEFAULT_IV = "1234567890oiuytr".toByteArray(Charsets.UTF_8)
    }

    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase().trim()
        return lower.contains("upns.live") ||
                lower.contains("videosh.upns") ||
                lower.contains("upnshare.com") ||
                lower.contains("upns.co")
    }

    override suspend fun resolve(embedUrl: String, parentUrl: String?): Result<MediaSource> = withContext(Dispatchers.IO) {
        resolveSync(embedUrl, parentUrl)
    }

    override fun resolveFromHtml(html: String, embedUrl: String, parentUrl: String?): Result<MediaSource>? {
        return resolveSync(embedUrl, parentUrl, preloadedHtml = html)
    }

    /**
     * Synchronously resolves a UPNS / VideoSH embed URL to a direct MediaSource.
     */
    fun resolveSync(embedUrl: String, parentUrl: String? = null, preloadedHtml: String? = null): Result<MediaSource> {
        try {
            val normalizedUrl = normalizeEmbedUrl(embedUrl)
            val videoId = extractVideoId(normalizedUrl)
                ?: return Result.failure(StreamHubError.PlaybackError(400, "Could not extract videoId from UPNS embed: $embedUrl"))

            val domain = extractDomain(normalizedUrl)
            val referrerHost = parentUrl?.let { extractDomain(it) } ?: "livecamrips.to"

            // Request /api/v1/video endpoint
            val apiUrl = "https://$domain/api/v1/video?id=$videoId&w=1920&h=1080&r=$referrerHost"
            val reqHeaders = mapOf(
                "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
                "Referer" to normalizedUrl,
                "Origin" to "https://$domain",
                "Accept" to "*/*"
            )

            val rawResponse = try {
                NetworkClient.fetchStringWithProxyFallback(apiUrl, headers = reqHeaders)
            } catch (e: Exception) {
                // Try fallback to videosh.upns.live if another domain was used
                if (domain != "videosh.upns.live") {
                    val fallbackUrl = "https://videosh.upns.live/api/v1/video?id=$videoId&w=1920&h=1080&r=$referrerHost"
                    val fallbackHeaders = mapOf(
                        "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
                        "Referer" to "https://videosh.upns.live/#$videoId&ua=1",
                        "Origin" to "https://videosh.upns.live",
                        "Accept" to "*/*"
                    )
                    NetworkClient.fetchStringWithProxyFallback(fallbackUrl, headers = fallbackHeaders)
                } else {
                    throw e
                }
            }

            // Check if response is raw JSON error or hex encrypted payload
            val decryptedJsonStr = if (rawResponse.trim().startsWith("{")) {
                rawResponse
            } else {
                decryptPayload(rawResponse.trim())
            }

            val jsonElement = try {
                Json.parseToJsonElement(decryptedJsonStr) as? JsonObject
            } catch (_: Exception) {
                null
            }

            val errorMsg = jsonElement?.get("message")?.jsonPrimitive?.content
                ?: jsonElement?.get("error")?.jsonPrimitive?.content
            if (!errorMsg.isNullOrBlank()) {
                return Result.failure(StreamHubError.PlaybackError(404, "UPNS error: $errorMsg"))
            }

            // Extract best stream: cfNative > source > hlsVideoTiktok
            val cfNative = jsonElement?.get("cfNative")?.jsonPrimitive?.content
            val source = jsonElement?.get("source")?.jsonPrimitive?.content
            val tiktok = jsonElement?.get("hlsVideoTiktok")?.jsonPrimitive?.content

            val chosenStream = if (!cfNative.isNullOrBlank()) cfNative
            else if (!source.isNullOrBlank()) source
            else if (!tiktok.isNullOrBlank()) tiktok
            else null

            if (chosenStream.isNullOrBlank()) {
                return Result.failure(StreamHubError.PlaybackError(404, "No playable stream found in UPNS response for $videoId"))
            }

            val resolvedStreamUrl = normalizeStreamUrl(chosenStream, domain)
            val isHls = resolvedStreamUrl.contains(".m3u8")
            val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
            val mime = if (isHls) "application/vnd.apple.mpegurl" else "video/mp4"

            val headers = mapOf(
                "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
                "Referer" to "https://$domain/",
                "Origin" to "https://$domain"
            )

            return Result.success(
                MediaSource(
                    url = resolvedStreamUrl,
                    type = type,
                    mimeType = mime,
                    headersRequired = headers
                )
            )
        } catch (e: Exception) {
            StreamHubLogger.e("UpnsResolverPlugin", "Failed to resolve UPNS embed $embedUrl: ${e.message}")
            return Result.failure(StreamHubError.PlaybackError(500, "UPNS resolver failed: ${e.message}", e))
        }
    }

    fun decryptPayload(hexString: String): String {
        val cleanHex = hexString.trim()
        val cipherBytes = ByteArray(cleanHex.length / 2)
        for (i in 0 until cleanHex.length step 2) {
            cipherBytes[i / 2] = cleanHex.substring(i, i + 2).toInt(16).toByte()
        }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(DEFAULT_KEY, "AES")
        val ivSpec = IvParameterSpec(DEFAULT_IV)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        val decryptedBytes = cipher.doFinal(cipherBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    fun extractVideoId(url: String): String? {
        val trimmed = url.trim()
        // 1. Hash: #c1nk65 or #c1nk65&ua=1 or #c1nk65&amp;ua=1
        val hashIdx = trimmed.indexOf('#')
        if (hashIdx != -1) {
            val hashPart = trimmed.substring(hashIdx + 1)
            val id = hashPart.substringBefore('&').substringBefore('?').substringBefore('/')
            if (id.isNotBlank()) return id
        }
        // 2. Query param: ?id=c1nk65
        val paramMatch = Regex("""[?&]id=([a-zA-Z0-9_-]+)""").find(trimmed)
        if (paramMatch != null) return paramMatch.groupValues[1]

        // 3. Path segment: /e/c1nk65, /v/c1nk65, /embed/c1nk65
        val pathMatch = Regex("""/(?:e|v|video|embed)/([a-zA-Z0-9_-]+)""").find(trimmed)
        if (pathMatch != null) return pathMatch.groupValues[1]

        val lastSeg = trimmed.substringAfterLast('/').substringBefore('?').substringBefore('#')
        if (lastSeg.isNotBlank() && lastSeg.length in 4..32 && !lastSeg.contains('.')) {
            return lastSeg
        }
        return null
    }

    private fun normalizeStreamUrl(streamUrl: String, defaultHost: String): String {
        var trimmed = streamUrl.trim()
        if (trimmed.startsWith("//")) {
            return "https:$trimmed"
        }
        if (trimmed.startsWith("/")) {
            return "https://$defaultHost$trimmed"
        }
        return trimmed
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = URI(url)
            uri.host ?: "videosh.upns.live"
        } catch (e: Exception) {
            "videosh.upns.live"
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
