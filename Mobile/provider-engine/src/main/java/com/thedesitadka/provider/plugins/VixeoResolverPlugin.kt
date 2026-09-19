package com.thedesitadka.provider.plugins

import com.thedesitadka.core.model.MediaSource
import com.thedesitadka.core.model.MediaSourceType
import com.thedesitadka.core.model.StreamHubError
import com.thedesitadka.core.network.NetworkClient
import com.thedesitadka.core.security.StreamHubLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Resolver plugin for external video hosting on Vixeo.io (and Vidsonic CDN).
 *
 * Architecture:
 * - Vixeo.io embeds a player container: `<div id="streamsonic-player-root" data-config="...">`.
 * - The `data-config` attribute is Base64 encoded JSON containing playback configuration.
 * - Inside the configuration, `source` is an obfuscated string of pipe-separated hex byte pairs.
 * - Removing pipes, decoding hex bytes, and reversing the resulting string reveals the real
 *   streaming endpoint on Vidsonic CDN (e.g. https://sfy-01-fr.vidsonic.net/secure/.../index.m3u8).
 * - Requires Referer: https://vixeo.io/ and Origin: https://vixeo.io headers for playback & download.
 */
class VixeoResolverPlugin : HostResolverPlugin {

    override val id: String = "vixeo"
    override val name: String = "Vixeo.io Video Host Resolver"
    override val supportedDomains: List<String> = listOf("vixeo.io", "vidsonic.net")

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("vixeo.io")
    }

    override suspend fun resolve(embedUrl: String, parentUrl: String?): Result<MediaSource> = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = normalizeEmbedUrl(embedUrl)
            val reqHeaders = mutableMapOf(
                "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
                "Referer" to (parentUrl ?: "https://vixeo.io/"),
                "Origin" to "https://vixeo.io"
            )

            val html = NetworkClient.fetchString(normalizedUrl, headers = reqHeaders)
            parseVixeoHtml(html, normalizedUrl)
        } catch (e: Exception) {
            StreamHubLogger.e("VixeoResolverPlugin", "Failed to resolve vixeo stream for $embedUrl: ${e.message}")
            Result.failure(StreamHubError.PlaybackError(500, "Vixeo resolver failed: ${e.message}", e))
        }
    }

    /**
     * Parses the Vixeo HTML page and resolves the media stream.
     * Accessible for direct offline testing.
     */
    fun parseVixeoHtml(html: String, embedUrl: String): Result<MediaSource> {
        try {
            val doc = Jsoup.parse(html, embedUrl)

            // 1. Primary: Extract data-config from #streamsonic-player-root
            val playerRoot = doc.select("#streamsonic-player-root").firstOrNull()
            var rawB64 = playerRoot?.attr("data-config")?.trim().orEmpty()

            // Regex fallback if not found via selector
            if (rawB64.isBlank()) {
                val configRegex = Regex("""(?:id=["']streamsonic-player-root["'][^>]*data-config=["']|data-config=["'])([^"']+)["']""")
                val match = configRegex.find(html)
                if (match != null) {
                    rawB64 = match.groupValues[1].trim()
                }
            }

            if (rawB64.isNotBlank()) {
                val decodedConfigJson = try {
                    val decodedBytes = Base64.getDecoder().decode(rawB64)
                    String(decodedBytes, StandardCharsets.UTF_8)
                } catch (e: Exception) {
                    ""
                }

                if (decodedConfigJson.isNotBlank()) {
                    var sourceEncoded = ""
                    var isMp4 = false

                    try {
                        val jsonElement = jsonParser.parseToJsonElement(decodedConfigJson)
                        val jsonObj = jsonElement.jsonObject
                        sourceEncoded = jsonObj["source"]?.jsonPrimitive?.content.orEmpty()
                        isMp4 = jsonObj["isMp4"]?.jsonPrimitive?.content?.toBoolean() ?: false
                    } catch (e: Exception) {
                        // Fallback regex parsing of JSON
                        val sourceRegex = Regex(""""source"\s*:\s*"([^"]+)"""")
                        val mp4Regex = Regex(""""isMp4"\s*:\s*(true|false)""")
                        sourceRegex.find(decodedConfigJson)?.let {
                            sourceEncoded = it.groupValues[1]
                        }
                        mp4Regex.find(decodedConfigJson)?.let {
                            isMp4 = it.groupValues[1].toBoolean()
                        }
                    }

                    if (sourceEncoded.isNotBlank()) {
                        val streamUrl = decodeHexReversed(sourceEncoded)
                        if (streamUrl.isNotBlank() && (streamUrl.startsWith("http://") || streamUrl.startsWith("https://"))) {
                            val isHls = !isMp4 && streamUrl.contains(".m3u8")
                            val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
                            val mime = if (isHls) "application/x-mpegURL" else "video/mp4"

                            val headers = mapOf(
                                "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
                                "Referer" to "https://vixeo.io/",
                                "Origin" to "https://vixeo.io"
                            )

                            return Result.success(
                                MediaSource(
                                    url = streamUrl,
                                    type = type,
                                    mimeType = mime,
                                    headersRequired = headers
                                )
                            )
                        }
                    }
                }
            }

            // 2. Fallback: Check for directly embedded vidsonic m3u8 or mp4 URLs
            val fallbackRegex = Regex("""https?://[^\s"'<>]*(?:vidsonic\.net|vixeo\.io)[^\s"'<>]*\.(?:m3u8|mp4)[^\s"'<>]*""")
            val fallbackMatch = fallbackRegex.find(html)
            if (fallbackMatch != null) {
                val streamUrl = fallbackMatch.value.replace(" ", "%20")
                val isHls = streamUrl.contains(".m3u8")
                return Result.success(
                    MediaSource(
                        url = streamUrl,
                        type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4,
                        mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                        headersRequired = mapOf(
                            "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
                            "Referer" to "https://vixeo.io/",
                            "Origin" to "https://vixeo.io"
                        )
                    )
                )
            }

            return Result.failure(StreamHubError.PlaybackError(404, "Could not resolve stream from Vixeo embed: $embedUrl"))
        } catch (e: Exception) {
            return Result.failure(StreamHubError.PlaybackError(500, "Error parsing Vixeo embed: ${e.message}", e))
        }
    }

    /**
     * Decodes the Vixeo obfuscated source string:
     * 1. Strips pipe '|' separators.
     * 2. Converts each pair of hexadecimal characters to an ASCII/latin1 character.
     * 3. Reverses the resulting string.
     */
    fun decodeHexReversed(encoded: String): String {
        val clean = encoded.replace("|", "").trim()
        if (clean.length < 2 || clean.length % 2 != 0) return ""

        val sb = StringBuilder(clean.length / 2)
        var i = 0
        while (i < clean.length) {
            val hexPair = clean.substring(i, i + 2)
            val charCode = hexPair.toIntOrNull(16) ?: return ""
            sb.append(charCode.toChar())
            i += 2
        }
        return sb.reverse().toString()
    }

    private fun normalizeEmbedUrl(url: String): String {
        var trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://$trimmed"
        }
        // If url is vixeo.io/ID, normalize to vixeo.io/e/ID
        val directIdRegex = Regex("""https?://(?:www\.)?vixeo\.io/([A-Za-z0-9_-]+)$""")
        val match = directIdRegex.find(trimmed)
        if (match != null) {
            val id = match.groupValues[1]
            if (id != "e" && id != "embed" && id != "d") {
                return "https://vixeo.io/e/$id"
            }
        }
        return trimmed
    }
}
