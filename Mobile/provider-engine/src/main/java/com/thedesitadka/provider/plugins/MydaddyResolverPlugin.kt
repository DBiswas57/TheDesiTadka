package com.thedesitadka.provider.plugins

import com.thedesitadka.core.model.MediaSource
import com.thedesitadka.core.model.MediaSourceType
import com.thedesitadka.core.model.StreamHubError
import com.thedesitadka.core.network.NetworkClient
import com.thedesitadka.core.security.StreamHubLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Resolver plugin for external video hosting on MyDaddy (mydaddy.cc) and its CDN (bigcdn.cc).
 *
 * Architecture:
 * - hqporner.com embeds an external iframe: `<iframe src="//mydaddy.cc/video/{id}/" ...>`.
 * - mydaddy.cc validates Referer header: if missing or unauthorized, it returns "This domain has been blocked".
 * - When fetched with Referer: https://hqporner.com/, it serves FluidPlayer markup embedding dynamic
 *   video sources on bigcdn.cc (e.g. //s36.bigcdn.cc/pubs/.../1080.mp4, 720.mp4, 360.mp4).
 * - This plugin fetches the embed page with the parent Referer, extracts the stream URLs,
 *   selects the highest quality (1080p > 720p > 360p), and attaches the necessary playback/download headers.
 */
class MydaddyResolverPlugin : HostResolverPlugin {

    override val id: String = "mydaddy"
    override val name: String = "MyDaddy Video Host Resolver"
    override val supportedDomains: List<String> = listOf("mydaddy.cc", "bigcdn.cc")

    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase().trim()
        return lower.contains("mydaddy.cc") || lower.contains("bigcdn.cc")
    }

    override suspend fun resolve(embedUrl: String, parentUrl: String?): Result<MediaSource> = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = normalizeEmbedUrl(embedUrl)
            val parent = parentUrl ?: "https://hqporner.com/"
            val reqHeaders = mapOf(
                "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
                "Referer" to parent,
                "Origin" to "https://hqporner.com"
            )

            val html = NetworkClient.fetchString(normalizedUrl, headers = reqHeaders)
            parseMydaddyHtml(html, normalizedUrl, parent)
        } catch (e: Exception) {
            StreamHubLogger.e("MydaddyResolverPlugin", "Failed to resolve mydaddy stream for $embedUrl: ${e.message}")
            Result.failure(StreamHubError.PlaybackError(500, "MyDaddy resolver failed: ${e.message}", e))
        }
    }

    override fun resolveFromHtml(html: String, embedUrl: String, parentUrl: String?): Result<MediaSource>? {
        val res = parseMydaddyHtml(html, normalizeEmbedUrl(embedUrl), parentUrl ?: "https://hqporner.com/")
        return if (res.isSuccess) res else null
    }

    /**
     * Parses the MyDaddy HTML page and extracts the video stream sources.
     * Publicly accessible for deterministic unit testing.
     */
    fun parseMydaddyHtml(html: String, embedUrl: String, parentUrl: String? = null): Result<MediaSource> {
        try {
            if (html.contains("This domain has been blocked", ignoreCase = true)) {
                return Result.failure(StreamHubError.PlaybackError(403, "MyDaddy host blocked the domain or referer"))
            }

            data class StreamCandidate(val url: String, val quality: String, val rank: Int)
            val candidates = mutableListOf<StreamCandidate>()

            // 1. Parse using regex to catch both static HTML and JS string literals (e.g. $("#jw").html("<video...><source src=..."))
            // Match pattern: <source src="(?://|https?:)?//s\d+\.bigcdn\.cc/..." title="..."
            val sourceRegex = Regex("""(?:<source\s+[^>]*src=|source\s+src=)[\\\"\']+([^\\\"\'\s>]+)[\\\"\']+(?:[^>]*title=[\\\"\']+([^\\\"\'\s>]+)[\\\"\']+)?(?:[^>]*type=[\\\"\']+([^\\\"\'\s>]+)[\\\"\']+)?(?:[^>]*)>""", RegexOption.IGNORE_CASE)
            for (match in sourceRegex.findAll(html)) {
                var rawSrc = match.groupValues[1].replace("\\/", "/").trim()
                if (rawSrc.startsWith("//")) rawSrc = "https:$rawSrc"
                val rawTitle = match.groupValues[2].ifEmpty {
                    when {
                        rawSrc.contains("1080.mp4") -> "1080p Full HD"
                        rawSrc.contains("720.mp4") -> "720p HD"
                        rawSrc.contains("480.mp4") -> "480p"
                        rawSrc.contains("360.mp4") -> "360p"
                        else -> "Default"
                    }
                }
                val rank = getQualityRank(rawTitle, rawSrc)
                if (rawSrc.isNotBlank() && candidates.none { it.url == rawSrc }) {
                    candidates.add(StreamCandidate(rawSrc, rawTitle, rank))
                }
            }

            // 2. Fallback: Parse Jsoup <source> elements in case Jsoup DOM parsed them directly
            val doc = Jsoup.parse(html, embedUrl)
            for (sourceEl in doc.select("source[src]")) {
                var src = sourceEl.attr("src").trim()
                if (src.startsWith("//")) src = "https:$src"
                val title = sourceEl.attr("title").ifEmpty {
                    when {
                        src.contains("1080.mp4") -> "1080p Full HD"
                        src.contains("720.mp4") -> "720p HD"
                        src.contains("360.mp4") -> "360p"
                        else -> "Default"
                    }
                }
                val rank = getQualityRank(title, src)
                if (src.isNotBlank() && candidates.none { it.url == src }) {
                    candidates.add(StreamCandidate(src, title, rank))
                }
            }

            // 3. Fallback: Extract download links (e.g. <a href='//s8.bigcdn.cc/.../1080.mp4'>1080p Full HD</a>)
            val downloadLinkRegex = Regex("""<a\s+[^>]*href=[\\\"\']+([^\\\"\'\s>]+\.mp4)[\\\"\'][^>]*>([^<]*)</a>""", RegexOption.IGNORE_CASE)
            for (match in downloadLinkRegex.findAll(html)) {
                var href = match.groupValues[1].replace("\\/", "/").trim()
                if (href.startsWith("//")) href = "https:$href"
                val linkText = match.groupValues[2].trim()
                val rank = getQualityRank(linkText, href)
                if (href.isNotBlank() && candidates.none { it.url == href }) {
                    candidates.add(StreamCandidate(href, linkText.ifEmpty { "MP4" }, rank))
                }
            }

            // 4. Fallback: Direct bigcdn.cc regex search
            if (candidates.isEmpty()) {
                val directCdnRegex = Regex("""(?:https?:)?//[a-zA-Z0-9.\-]+\.bigcdn\.cc/[^"'\s<>\\]+\.mp4""")
                for (match in directCdnRegex.findAll(html)) {
                    var src = match.value.replace("\\/", "/").trim()
                    if (src.startsWith("//")) src = "https:$src"
                    val rank = getQualityRank("", src)
                    if (candidates.none { it.url == src }) {
                        candidates.add(StreamCandidate(src, "MP4", rank))
                    }
                }
            }

            if (candidates.isEmpty()) {
                return Result.failure(StreamHubError.PlaybackError(404, "No video stream sources found in MyDaddy response"))
            }

            // Sort candidates by quality rank descending (highest quality first)
            val sorted = candidates.sortedByDescending { it.rank }
            val bestCandidate = sorted.first()

            val referer = parentUrl ?: "https://hqporner.com/"
            val headers = mapOf(
                "Referer" to referer,
                "Origin" to "https://hqporner.com",
                "User-Agent" to NetworkClient.DEFAULT_USER_AGENT
            )

            val mediaSource = MediaSource(
                url = bestCandidate.url,
                type = MediaSourceType.PROGRESSIVE_MP4,
                quality = bestCandidate.quality,
                mimeType = "video/mp4",
                headersRequired = headers
            )

            return Result.success(mediaSource)
        } catch (e: Exception) {
            StreamHubLogger.e("MydaddyResolverPlugin", "Error parsing MyDaddy HTML: ${e.message}")
            return Result.failure(StreamHubError.PlaybackError(500, "Error parsing MyDaddy HTML: ${e.message}", e))
        }
    }

    private fun getQualityRank(title: String, url: String): Int {
        val t = title.lowercase()
        val u = url.lowercase()
        return when {
            t.contains("2160") || t.contains("4k") || u.contains("2160") || u.contains("4k") -> 2160
            t.contains("1080") || u.contains("1080") -> 1080
            t.contains("720") || u.contains("720") -> 720
            t.contains("480") || u.contains("480") -> 480
            t.contains("360") || u.contains("360") -> 360
            else -> 100
        }
    }

    private fun normalizeEmbedUrl(url: String): String {
        var clean = url.trim()
        if (clean.startsWith("//")) {
            clean = "https:$clean"
        }
        return clean
    }
}
