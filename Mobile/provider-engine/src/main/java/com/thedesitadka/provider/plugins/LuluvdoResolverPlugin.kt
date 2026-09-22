package com.thedesitadka.provider.plugins

import com.thedesitadka.core.model.MediaSource
import com.thedesitadka.core.model.MediaSourceType
import com.thedesitadka.core.model.StreamHubError
import com.thedesitadka.core.network.NetworkClient
import com.thedesitadka.core.security.StreamHubLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

/**
 * Resolver plugin for external video hosting on LuluVdo, LuluStream, LuluVid, and PlayMogo.
 *
 * Architecture:
 * - Sites like watchxxxfree.xyz embed: `<iframe src="https://luluvdo.com/e/{slug}" ...>`.
 * - The embed page protects JWPlayer setup code with Dean Edwards packing:
 *   `eval(function(p,a,c,k,e,d)...)`.
 * - Unpacking this block reveals `jwplayer().setup({sources:[{file: "https://...master.m3u8..."}]})`.
 * - Playback & download require `Referer: https://{domain}/` and `Origin: https://{domain}`.
 */
class LuluvdoResolverPlugin : HostResolverPlugin {

    override val id: String = "luluvdo"
    override val name: String = "LuluVdo / LuluStream Video Host Resolver"
    override val supportedDomains: List<String> = listOf(
        "luluvdo.com",
        "lulustream.com",
        "luluvid.com",
        "playmogo.com",
        "cdn1.site"
    )

    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("luluvdo.") ||
                lower.contains("lulustream.") ||
                lower.contains("luluvid.") ||
                lower.contains("playmogo.") ||
                lower.contains("cdn1.site")
    }

    override suspend fun resolve(embedUrl: String, parentUrl: String?): Result<MediaSource> = withContext(Dispatchers.IO) {
        resolveSync(embedUrl, parentUrl)
    }

    /**
     * Resolves LuluVdo embed directly from pre-fetched HTML without redundant network fetch.
     */
    override fun resolveFromHtml(html: String, embedUrl: String, parentUrl: String?): Result<MediaSource> {
        return resolveSync(embedUrl, parentUrl, preloadedHtml = html)
    }


    /**
     * Synchronously resolves the LuluVdo embed URL to a direct MediaSource.
     */
    fun resolveSync(embedUrl: String, parentUrl: String? = null, preloadedHtml: String? = null): Result<MediaSource> {
        try {
            val normalizedUrl = normalizeEmbedUrl(embedUrl)
            var domain = extractDomain(normalizedUrl)

            // 1. Obtain embed page HTML
            val html = if (!preloadedHtml.isNullOrBlank()) {
                preloadedHtml
            } else {
                val reqHeaders = mutableMapOf(
                    "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
                    "Referer" to (parentUrl ?: "https://$domain/"),
                    "Origin" to "https://$domain"
                )
                try {
                    NetworkClient.fetchString(normalizedUrl, headers = reqHeaders)
                } catch (e: Exception) {
                    val slug = extractSlug(normalizedUrl)
                    if (slug != null && !domain.contains("luluvdo")) {
                        domain = "luluvdo.com"
                        val fallbackUrl = "https://luluvdo.com/e/$slug"
                        val fallbackHeaders = mutableMapOf(
                            "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
                            "Referer" to (parentUrl ?: "https://luluvdo.com/"),
                            "Origin" to "https://luluvdo.com"
                        )
                        NetworkClient.fetchString(fallbackUrl, headers = fallbackHeaders)
                    } else {
                        throw e
                    }
                }
            }

            if (html.contains("expired or has been deleted", ignoreCase = true)) {
                return Result.failure(StreamHubError.PlaybackError(404, "File expired or deleted on host: $normalizedUrl"))
            }

            // 2. Unpack Dean Edwards if present
            val unpacked = if (html.contains("eval(function(p,a,c,k,e")) {
                unpackDeanEdwards(html)
            } else {
                html
            }

            // 3. Extract stream URL
            val streamUrl = extractStreamUrl(unpacked)
                ?: return Result.failure(StreamHubError.PlaybackError(404, "Could not extract stream URL from LuluVdo embed: $normalizedUrl"))

            val isHls = streamUrl.contains(".m3u8")
            val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
            val mime = if (isHls) "application/x-mpegURL" else "video/mp4"

            val headers = mapOf(
                "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
                "Referer" to "https://$domain/",
                "Origin" to "https://$domain"
            )

            return Result.success(
                MediaSource(
                    url = streamUrl,
                    type = type,
                    mimeType = mime,
                    headersRequired = headers
                )
            )
        } catch (e: Exception) {
            StreamHubLogger.e("LuluvdoResolverPlugin", "Failed to resolve luluvdo for $embedUrl: ${e.message}")
            return Result.failure(StreamHubError.PlaybackError(500, "LuluVdo resolver failed: ${e.message}", e))
        }
    }

    /**
     * Extracts direct stream URL from unpacked JavaScript or HTML.
     */
    fun extractStreamUrl(unpacked: String): String? {
        val jwRegex = Regex("""(?:sources|file):\s*(?:\[\s*\{\s*(?:file|src)\s*:\s*["']([^"']+)["']|["']([^"']+\.(?:mp4|m3u8)[^"']*)["'])""")
        val jwMatch = jwRegex.find(unpacked)
        if (jwMatch != null) {
            val u = jwMatch.groupValues[1].ifEmpty { jwMatch.groupValues[2] }.trim()
            if (u.isNotBlank()) return u.replace(" ", "%20")
        }

        val directRegex = Regex("""https?://[^\s"'<>]+\.(?:mp4|m3u8)[^\s"'<>]*""")
        return directRegex.find(unpacked)?.value?.replace(" ", "%20")
    }

    /**
     * Extracts video slug from embed URL.
     */
    fun extractSlug(url: String): String? {
        val slugRegex = Regex("""/(?:e|d)/([A-Za-z0-9_-]+)""")
        val match = slugRegex.find(url)
        if (match != null) {
            return match.groupValues[1]
        }
        val directRegex = Regex("""(?:luluvdo|lulustream|luluvid|playmogo|cdn1)\.[a-z]+/([A-Za-z0-9_-]+)""")
        return directRegex.find(url)?.groupValues?.getOrNull(1)?.takeUnless { it == "e" || it == "d" }
    }

    /**
     * Unpacks Dean Edwards packed JavaScript code.
     */
    fun unpackDeanEdwards(script: String): String {
        val regex = Regex("""eval\(function\(p,a,c,k,e,[rd]\)\{.*?\}\)?\s*\(\s*['"](.+?)['"]\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*['"]([^'"]*)['"]\s*\.\s*split\s*\(\s*['"]\|['"]\s*\)""", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(script) ?: return script
        return try {
            val payload = match.groupValues[1]
            val radix = match.groupValues[2].toInt()
            val symTab = match.groupValues[4].split("|")
            val wordRegex = Regex("""\b[0-9a-zA-Z]+\b""")
            wordRegex.replace(payload) { mr ->
                val token = mr.value
                val idx = decodeBaseNToken(token, radix)
                if (idx in 0 until symTab.size && symTab[idx].isNotBlank()) {
                    symTab[idx]
                } else {
                    token
                }
            }
        } catch (e: Exception) {
            script
        }
    }

    private fun decodeBaseNToken(token: String, radix: Int): Int {
        val digits = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        var value = 0
        for (ch in token) {
            val idx = digits.indexOf(ch)
            if (idx < 0 || idx >= radix) return -1
            value = value * radix + idx
        }
        return value
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = URI(url)
            uri.host ?: "luluvdo.com"
        } catch (e: Exception) {
            "luluvdo.com"
        }
    }

    private fun normalizeEmbedUrl(url: String): String {
        var trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://$trimmed"
        }
        // Normalize /d/ to /e/
        val dRegex = Regex("""https?://(?:www\.)?([^/]+)/d/([A-Za-z0-9_-]+)""")
        val match = dRegex.find(trimmed)
        if (match != null) {
            val domain = match.groupValues[1]
            val slug = match.groupValues[2]
            return "https://$domain/e/$slug"
        }
        return trimmed
    }
}
