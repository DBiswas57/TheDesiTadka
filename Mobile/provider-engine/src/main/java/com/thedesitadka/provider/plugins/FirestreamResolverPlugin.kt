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
import java.net.URI

/**
 * Resolver plugin for third-party hosted video iframes on FireStream
 * (firestream.site, firestream.to, and associated CDN nodes).
 *
 * Architecture:
 * - Sites like watchxxxfree.xyz embed: `<iframe src="https://firestream.site/e/{slug}" ...>`.
 * - The embed page contains a hidden plain-text token element:
 *   `<script id="token-blob" type="text/plain">{base64_blob}</script>`.
 * - A POST request to `https://{domain}/api/videos/{slug}/resolve` with body `{"blob": "{tokenBlob}"}`
 *   returns `{ "signedVideoUrl": "https://fr-cdn-*.firestream.to/encodings/.../video.m3u8?md5=...&expires=..." }`.
 * - Streaming & downloading require `Referer: https://{domain}/` and `Origin: https://{domain}`.
 */
class FirestreamResolverPlugin : HostResolverPlugin {

    override val id: String = "firestream"
    override val name: String = "FireStream Video Host Resolver"
    override val supportedDomains: List<String> = listOf(
        "firestream.site",
        "firestream.to",
        "firestream.me",
        "firestream.cc",
        "firestream.org"
    )

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("firestream.")
    }

    override suspend fun resolve(embedUrl: String, parentUrl: String?): Result<MediaSource> = withContext(Dispatchers.IO) {
        resolveSync(embedUrl, parentUrl)
    }

    /**
     * Resolves FireStream embed directly from pre-fetched HTML without redundant network fetch.
     */
    override fun resolveFromHtml(html: String, embedUrl: String, parentUrl: String?): Result<MediaSource> {
        return resolveSync(embedUrl, parentUrl, preloadedHtml = html)
    }


    /**
     * Synchronously resolves the FireStream embed URL to a direct MediaSource.
     */
    fun resolveSync(embedUrl: String, parentUrl: String? = null, preloadedHtml: String? = null): Result<MediaSource> {
        try {
            val normalizedUrl = normalizeEmbedUrl(embedUrl)
            val domain = extractDomain(normalizedUrl)
            val slug = extractSlug(normalizedUrl)
                ?: return Result.failure(StreamHubError.PlaybackError(400, "Could not extract video slug from $embedUrl"))

            // 1. Obtain embed page HTML
            val html = if (!preloadedHtml.isNullOrBlank()) {
                preloadedHtml
            } else {
                val reqHeaders = mutableMapOf(
                    "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
                    "Referer" to (parentUrl ?: "https://$domain/"),
                    "Origin" to "https://$domain"
                )
                NetworkClient.fetchString(normalizedUrl, headers = reqHeaders)
            }

            // 2. Check if signedVideoUrl is already directly present in HTML scripts
            val directStreamUrl = extractDirectStreamUrl(html)
            if (directStreamUrl != null) {
                return Result.success(buildMediaSource(directStreamUrl, domain))
            }

            // 3. Extract token-blob
            val tokenBlob = extractTokenBlob(html)
            if (tokenBlob.isNullOrBlank()) {
                return Result.failure(StreamHubError.PlaybackError(404, "token-blob element not found in FireStream embed: $normalizedUrl"))
            }

            // 4. Call /api/videos/{slug}/resolve
            val resolveUrl = "https://$domain/api/videos/$slug/resolve"
            val postHeaders = mapOf(
                "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
                "Content-Type" to "application/json",
                "Referer" to normalizedUrl,
                "Origin" to "https://$domain"
            )
            val postPayload = "{\"blob\":\"$tokenBlob\"}"

            val resolveResponse = NetworkClient.postJson(resolveUrl, postPayload, headers = postHeaders)
            val streamUrl = parseResolveResponse(resolveResponse)
                ?: return Result.failure(StreamHubError.PlaybackError(404, "No signedVideoUrl returned from FireStream resolve API"))

            return Result.success(buildMediaSource(streamUrl, domain))
        } catch (e: Exception) {
            StreamHubLogger.e("FirestreamResolverPlugin", "Failed to resolve firestream for $embedUrl: ${e.message}")
            return Result.failure(StreamHubError.PlaybackError(500, "FireStream resolver failed: ${e.message}", e))
        }
    }

    /**
     * Extracts token-blob text from HTML.
     */
    fun extractTokenBlob(html: String): String? {
        val doc = Jsoup.parse(html)
        val blobEl = doc.select("script#token-blob").firstOrNull()
        val blobText = blobEl?.data()?.trim().takeUnless { it.isNullOrBlank() }
            ?: blobEl?.text()?.trim().takeUnless { it.isNullOrBlank() }

        if (!blobText.isNullOrBlank()) {
            return blobText
        }

        // Regex fallback
        val regex = Regex("""id=["']token-blob["'][^>]*>(.*?)<""")
        return regex.find(html)?.groupValues?.getOrNull(1)?.trim()?.takeUnless { it.isBlank() }
    }

    /**
     * Extracts video slug from embed URL.
     */
    fun extractSlug(url: String): String? {
        val slugRegex = Regex("""/(?:e|v|embed)/([A-Za-z0-9_-]+)""")
        val match = slugRegex.find(url)
        if (match != null) {
            return match.groupValues[1]
        }

        val directRegex = Regex("""firestream\.[a-z]+/([A-Za-z0-9_-]+)""")
        val directMatch = directRegex.find(url)
        if (directMatch != null) {
            val s = directMatch.groupValues[1]
            if (s != "e" && s != "v" && s != "embed" && s != "api") {
                return s
            }
        }
        return null
    }

    /**
     * Extracts domain host from URL.
     */
    fun extractDomain(url: String): String {
        return try {
            val uri = URI(url)
            uri.host ?: "firestream.site"
        } catch (e: Exception) {
            "firestream.site"
        }
    }

    /**
     * Parses the JSON response from /api/videos/{slug}/resolve.
     */
    fun parseResolveResponse(jsonStr: String): String? {
        return try {
            val element = jsonParser.parseToJsonElement(jsonStr)
            element.jsonObject["signedVideoUrl"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            val regex = Regex(""""signedVideoUrl"\s*:\s*"([^"]+)"""")
            regex.find(jsonStr)?.groupValues?.getOrNull(1)
        }
    }

    /**
     * Searches for any direct or pre-resolved stream URL in HTML.
     */
    fun extractDirectStreamUrl(html: String): String? {
        val regexSigned = Regex(""""signedVideoUrl"\s*:\s*"(https?://[^"]+)"""")
        val matchSigned = regexSigned.find(html)
        if (matchSigned != null && !matchSigned.groupValues[1].contains("null")) {
            return matchSigned.groupValues[1].replace("\\/", "/")
        }

        val regexCdn = Regex("""https?://[^\s"'<>]*firestream\.to[^\s"'<>]*\.(?:m3u8|mp4)[^\s"'<>]*""")
        return regexCdn.find(html)?.value?.replace(" ", "%20")
    }

    private fun buildMediaSource(streamUrl: String, domain: String): MediaSource {
        val isHls = streamUrl.contains(".m3u8")
        val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
        val mime = if (isHls) "application/x-mpegURL" else "video/mp4"

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

    private fun normalizeEmbedUrl(url: String): String {
        var trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://$trimmed"
        }
        return trimmed
    }
}
