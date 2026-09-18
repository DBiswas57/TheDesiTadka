package com.thedesitadka.provider.adapters

import com.thedesitadka.core.model.Category
import com.thedesitadka.core.model.DownloadAvailability
import com.thedesitadka.core.model.FeedPage
import com.thedesitadka.core.model.MediaSource
import com.thedesitadka.core.model.MediaSourceType
import com.thedesitadka.core.model.PlaybackAvailability
import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.ProviderInfo
import com.thedesitadka.core.model.ProviderStatus
import com.thedesitadka.core.model.StreamHubError
import com.thedesitadka.core.model.VideoItem
import com.thedesitadka.core.network.CloudflareChallengeException
import com.thedesitadka.core.network.NetworkClient
import com.thedesitadka.core.security.StreamHubLogger
import com.thedesitadka.provider.ProviderAdapter
import com.thedesitadka.core.config.DomainResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

class HtmlSelectorAdapter(
    private val config: ProviderConfig,
    private val domainResolver: DomainResolver? = null
) : ProviderAdapter {

    @Volatile
    private var activeBaseUrl: String = config.baseUrl

    private suspend fun getEffectiveBaseUrl(): String {
        return domainResolver?.let { resolver ->
            try {
                val resolved = resolver.resolveActiveDomain(config)
                activeBaseUrl = resolved
                resolved
            } catch (e: Exception) {
                config.baseUrl
            }
        } ?: config.baseUrl
    }

    override val providerInfo: ProviderInfo = ProviderInfo(
        id = config.id,
        name = config.name,
        icon = config.icon,
        description = config.description,
        enabled = config.enabled,
        capabilities = config.capabilities,
        baseUrl = config.baseUrl,
        status = if (config.enabled) ProviderStatus.ENABLED else ProviderStatus.DISABLED,
        contentPolicy = config.contentPolicy
    )

    private val nav = config.navigation
    private val selectors = config.selectors

    override suspend fun getCategories(): Result<List<Category>> = withContext(Dispatchers.IO) {
        if (!hasCapability(ProviderCapability.CATEGORY)) {
            return@withContext Result.success(emptyList())
        }
        try {
            getEffectiveBaseUrl()
            val catPath = nav?.categories ?: "/"
            val targetUrl = resolveUrl(catPath)
            val html = NetworkClient.fetchString(targetUrl)
            val doc = Jsoup.parse(html, activeBaseUrl)

            val categoryElements = doc.select("a[href*='/category/'], a[href*='/categories/'], a[href*='/ott/'], a[href*='/series/'], .category-list a, .categories a, div.thumb-cat p.title a, .thumb-block.thumb-cat p.title a, a.taxonomy-item-card")
            val categories = categoryElements.mapNotNull { el ->
                val rawName = el.select(".taxonomy-name, .cat-name, span.title, p.title").firstOrNull()?.text()?.trim()
                    ?.ifEmpty { null }
                    ?: el.text().substringBefore("\n").trim()
                val name = rawName.replace(Regex("""\s+"""), " ")
                val href = el.attr("href").trim()
                if (name.isNotBlank() && href.isNotBlank()) {
                    if (href.contains("/photos/", ignoreCase = true) || href.contains("/creators/", ignoreCase = true) || href.contains("/pornstars/", ignoreCase = true)
                        || href.endsWith("/ott/") || href.endsWith("/series/")) {
                        return@mapNotNull null
                    }
                    val id = href.trimEnd('/').substringAfterLast('/')
                    Category(id = id, name = name, url = resolveUrl(href))
                } else null
            }.distinctBy { it.id }

            Result.success(categories)
        } catch (e: Exception) {
            StreamHubLogger.e("HtmlSelectorAdapter", "Failed to fetch categories: ${e.message}")
            Result.failure(StreamHubError.ParsingError(config.id, "Failed to get categories: ${e.message}", e))
        }
    }

    override suspend fun getHomeFeed(page: Int): Result<FeedPage> = withContext(Dispatchers.IO) {
        try {
            getEffectiveBaseUrl()
            val pagePath = if (page <= 1) {
                nav?.home ?: "/"
            } else {
                (nav?.page ?: "/page/{page}/").replace("{page}", page.toString())
            }
            val targetUrl = resolveUrl(pagePath)
            val html = NetworkClient.fetchString(targetUrl)
            parseListingHtml(html, page)
        } catch (e: Exception) {
            StreamHubLogger.e("HtmlSelectorAdapter", "Failed to get home feed (page $page): ${e.message}")
            val code = (e as? CloudflareChallengeException)?.statusCode ?: 0
            Result.failure(StreamHubError.NetworkError(code, e.message ?: "Failed to get home feed", e))
        }
    }

    override suspend fun search(query: String, page: Int): Result<FeedPage> = withContext(Dispatchers.IO) {
        if (!hasCapability(ProviderCapability.SEARCH)) {
            return@withContext Result.failure(StreamHubError.ProviderUnavailable(config.id, "Search not supported"))
        }
        try {
            getEffectiveBaseUrl()
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            var searchPath = (nav?.search ?: "/?s={query}").replace("{query}", encodedQuery)
            if (page > 1) {
                searchPath = if (searchPath.startsWith("/?")) {
                    "/page/$page/" + searchPath.substring(1)
                } else if (searchPath.contains("?")) {
                    val pathPart = searchPath.substringBefore("?")
                    val queryPart = searchPath.substringAfter("?")
                    "${pathPart.removeSuffix("/")}/page/$page/?$queryPart"
                } else {
                    "${searchPath.removeSuffix("/")}/page/$page"
                }
            }
            val targetUrl = resolveUrl(searchPath)
            val html = NetworkClient.fetchString(targetUrl)
            parseListingHtml(html, page)
        } catch (e: Exception) {
            StreamHubLogger.e("HtmlSelectorAdapter", "Failed to search '$query': ${e.message}")
            val code = (e as? CloudflareChallengeException)?.statusCode ?: 0
            Result.failure(StreamHubError.NetworkError(code, e.message ?: "Search failed", e))
        }
    }

    override suspend fun getDetails(detailUrl: String): Result<VideoItem> = withContext(Dispatchers.IO) {
        try {
            getEffectiveBaseUrl()
            val fullUrl = resolveUrl(detailUrl)
            val html = NetworkClient.fetchString(fullUrl)
            parseDetailsHtml(html, fullUrl)
        } catch (e: Exception) {
            StreamHubLogger.e("HtmlSelectorAdapter", "Failed to get details for $detailUrl: ${e.message}")
            Result.failure(StreamHubError.ParsingError(config.id, "Failed to get details: ${e.message}", e))
        }
    }

    internal fun parseDetailsHtml(html: String, detailUrl: String): Result<VideoItem> {
        try {
            val doc = Jsoup.parse(html, activeBaseUrl)

            val title = doc.select(selectors?.detailTitle ?: "h1").text().trim().ifEmpty {
                doc.title().substringBefore(" - ").substringBefore(" | ").trim()
            }

            val desc = selectors?.detailDescription?.let { sel ->
                doc.select(sel).firstOrNull()?.text()?.trim()
            } ?: ""

            // Extract thumbnail with poster, content (og:image), data-src, src fallbacks
            var thumb = ""
            val thumbEl = selectors?.detailThumbnail?.let { doc.select(it).firstOrNull() }
            val candidateThumb = extractValidThumbnail(
                el = doc.body() ?: doc,
                imgEl = thumbEl ?: doc.select("img.video-main-thumb, img.video-img").firstOrNull(),
                specifiedAttr = null
            )
            if (!isPlaceholderOrLogo(candidateThumb)) {
                thumb = candidateThumb
            }
            if (thumb.isEmpty()) {
                val poster = doc.select("video[poster]").attr("poster")
                if (!isPlaceholderOrLogo(poster)) {
                    thumb = poster
                }
            }
            if (thumb.isEmpty()) {
                val ogImg = doc.select("meta[property='og:image']").attr("content")
                if (!isPlaceholderOrLogo(ogImg)) {
                    thumb = ogImg
                }
            }

            // Provider-specific fallback (e.g. Fry99 / mmsbee synthesizes thumbnail from video id)
            if (thumb.isEmpty() || isPlaceholderOrLogo(thumb)) {
                if (config.id == "fry99" || activeBaseUrl.contains("fry99") || html.contains("fry99")) {
                    val idMatch = Regex("""(?:/id/|id=|report\.php\?id=)(\d+)""").find(html)
                    if (idMatch != null) {
                        val id = idMatch.groupValues[1]
                        thumb = "https://fry99.cc/pictures/$id.jpg"
                    }
                }
            }

            val downloadAvail = if (hasCapability(ProviderCapability.DOWNLOAD)) {
                DownloadAvailability.AUTHORIZED
            } else {
                DownloadAvailability.NOT_SUPPORTED
            }

            val item = VideoItem(
                id = detailUrl.hashCode().toString(),
                providerId = config.id,
                title = title,
                description = desc,
                thumbnailUrl = resolveUrl(thumb),
                detailUrl = detailUrl,
                playbackAvailability = PlaybackAvailability.AVAILABLE,
                downloadAvailability = downloadAvail
            )
            return Result.success(item)
        } catch (e: Exception) {
            return Result.failure(StreamHubError.ParsingError(config.id, "Failed to parse details: ${e.message}", e))
        }
    }

    override suspend fun getPlayableMedia(detailUrl: String): Result<List<MediaSource>> = withContext(Dispatchers.IO) {
        try {
            getEffectiveBaseUrl()
            val fullUrl = resolveUrl(detailUrl)
            val html = NetworkClient.fetchString(fullUrl)
            parsePlayableMediaHtml(html, fullUrl)
        } catch (e: Exception) {
            Result.failure(StreamHubError.PlaybackError(500, "Failed to resolve media: ${e.message}", e))
        }
    }

    fun parsePlayableMediaHtml(html: String, fullUrl: String): Result<List<MediaSource>> {
        return try {
            val doc = Jsoup.parse(html, activeBaseUrl)

            val sources = mutableListOf<MediaSource>()
            val defaultHeaders = mapOf(
                "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
                "Referer" to "$activeBaseUrl/",
                "Origin" to activeBaseUrl
            )

            // 1. Try direct video source selectors
            val videoSourceSelector = selectors?.videoSource ?: "meta[itemprop*='contentUrl'], meta[itemprop*='contentURL'], meta[itemprop*='contenturl'], video source[src], video[src], source[type='video/mp4']"
            val videoElements = doc.select(videoSourceSelector)
            for (el in videoElements) {
                // Skip hover preview trailers / teasers in recommendation cards
                if (el.hasClass("wpst-trailer") || el.parent()?.hasClass("wpst-trailer") == true
                    || el.closest(".wpst-trailer, .trailer, .video-preview-item") != null) {
                    continue
                }
                if (el.tagName().equals("iframe", ignoreCase = true)) {
                    val iframeSrc = el.attr("src")
                    if (!iframeSrc.contains(".mp4") && !iframeSrc.contains(".m3u8")) {
                        continue
                    }
                }
                if (el.tagName().equals("a", ignoreCase = true)) {
                    val href = el.attr("href")
                    if (!href.contains(".mp4") && !href.contains(".m3u8")) {
                        continue
                    }
                }
                val src = el.attr("src")
                    .ifEmpty { el.attr("data-src") }
                    .ifEmpty { el.attr("content") }
                    .ifEmpty { if (el.tagName().equals("a", ignoreCase = true) || el.tagName().equals("link", ignoreCase = true)) el.attr("href") else "" }
                if (src.isNotBlank()) {
                    val resolved = resolveUrl(src)
                    val lower = resolved.lowercase()
                    if (lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") || lower.contains(".webp") || lower.contains("/screenshots/")) {
                        continue
                    }
                    if (lower.contains("/smartpop/") || lower.contains("whitetrafsa") || lower.contains("hipodi")
                        || lower.contains("mavrtracktor") || lower.endsWith(".html") || lower.endsWith(".php")
                        || lower.contains("a-ads") || lower.contains("banner") || lower.contains("popunder")) {
                        continue
                    }
                    val mime = el.attr("type").ifEmpty { if (resolved.contains(".m3u8")) "application/x-mpegURL" else "video/mp4" }
                    val type = when {
                        resolved.contains(".m3u8") || mime.contains("mpegurl") -> MediaSourceType.HLS
                        resolved.contains(".mpd") -> MediaSourceType.DASH
                        else -> MediaSourceType.PROGRESSIVE_MP4
                    }
                    val headers = resolveHeadersForStream(resolved, defaultHeaders)
                    sources.add(MediaSource(url = resolved, type = type, mimeType = mime, headersRequired = headers))
                }
            }

            // 2. Check iframe embeds and host links (e.g. clean-tube-player, /e/ embeds, tube279, luluvdo, streamtape)
            if (sources.isEmpty()) {
                val candidateUrls = mutableListOf<String>()

                // Check iframes in page
                val iframes = doc.select("iframe[src*='player'], iframe[src*='embed'], iframe[src*='/e/'], iframe[src*='cdn1'], iframe[src*='tube279'], iframe[src*='lulu'], iframe[src*='streamtape'], iframe[src]")
                for (iframe in iframes) {
                    val src = resolveUrl(iframe.attr("src"))
                    if (src.isNotBlank()) candidateUrls.add(src)
                }

                // Also check meta embed tags (e.g. PornX11 meta itemprop="embedURL")
                val metaEmbeds = doc.select("meta[itemprop*='embedURL'], meta[itemprop*='embedUrl'], meta[itemprop*='embedurl'], meta[property='og:video'], meta[name='twitter:player']")
                for (m in metaEmbeds) {
                    val content = resolveUrl(m.attr("content"))
                    if (content.isNotBlank()) candidateUrls.add(content)
                }

                // Also check host links with download/embed patterns
                val hostLinks = doc.select("a[href*='tube279'], a[href*='luluvdo'], a[href*='lulustream'], a[href*='cdn1.site'], a[href*='streamtape']")
                for (a in hostLinks) {
                    val href = resolveUrl(a.attr("href"))
                    if (href.isNotBlank()) {
                        if (href.contains("/d/")) {
                            candidateUrls.add(href.replace("/d/", "/e/"))
                        }
                        candidateUrls.add(href)
                    }
                }

                // Filter out ads and sort candidate URLs so dedicated video players run first
                val filteredCandidates = candidateUrls.filter { urlCandidate ->
                    val lowerUrl = urlCandidate.lowercase()
                    !(lowerUrl.contains("whitetrafsa") || lowerUrl.contains("mavrtracktor") || lowerUrl.contains("hipodi")
                        || lowerUrl.contains("google") || lowerUrl.contains("doubleclick") || lowerUrl.contains("recaptcha")
                        || lowerUrl.contains("adservice") || lowerUrl.contains("smartpop") || lowerUrl.contains("videobaba")
                        || lowerUrl.contains("revive") || lowerUrl.contains("javascript:")
                        || lowerUrl.contains("a-ads") || lowerUrl.contains("ad.a-ads") || lowerUrl.contains("banner")
                        || lowerUrl.contains("popunder") || lowerUrl.contains("syndication") || lowerUrl.contains("adsterra")
                        || lowerUrl.contains("exoclick") || lowerUrl.contains("juicyads"))
                }.sortedByDescending { u ->
                    val lu = u.lowercase()
                    when {
                        lu.contains("luluvdo") || lu.contains("lulustream") || lu.contains("luluvid") -> 100
                        lu.contains("/e/") || lu.contains("tube279") || lu.contains("streamtape") || lu.contains("cdn1") -> 90
                        lu.contains("player") || lu.contains("embed") -> 50
                        else -> 10
                    }
                }

                for (urlCandidate in filteredCandidates) {
                    // Build list of URLs to try for this candidate (e.g. cdn1.site / luluvdo -> lulustream / luluvid mirrors)
                    val urlsToTry = mutableListOf(urlCandidate)
                    if (urlCandidate.contains("cdn1.site/e/") || urlCandidate.contains("luluvid.com/e/") || urlCandidate.contains("luluvdo.com/e/") || urlCandidate.contains("lulustream.com/e/")) {
                        val fileCode = urlCandidate.substringAfter("/e/").substringBefore("?").substringBefore("/").substringBefore("&")
                        if (fileCode.isNotBlank()) {
                            val mirrors = listOf(
                                "https://luluvdo.com/e/$fileCode",
                                "https://lulustream.com/e/$fileCode",
                                "https://luluvid.com/e/$fileCode"
                            )
                            for (mirror in mirrors) {
                                if (!urlsToTry.contains(mirror)) urlsToTry.add(mirror)
                            }
                        }
                    } else if (urlCandidate.contains("luluvdo.com/d/") || urlCandidate.contains("lulustream.com/d/") || urlCandidate.contains("luluvid.com/d/")) {
                        val fileCode = urlCandidate.substringAfter("/d/").substringBefore("?").substringBefore("/").substringBefore("&")
                        if (fileCode.isNotBlank()) {
                            urlsToTry.add(0, "https://luluvdo.com/e/$fileCode")
                            urlsToTry.add("https://lulustream.com/e/$fileCode")
                            urlsToTry.add("https://luluvid.com/e/$fileCode")
                        }
                    }

                    for (iframeSrc in urlsToTry) {
                        val iframeHost = try { URI(iframeSrc).host } catch (e: Exception) { null }
                        val embedHeaders = if (!iframeHost.isNullOrBlank()) {
                            mapOf(
                                "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
                                "Referer" to "https://$iframeHost/",
                                "Origin" to "https://$iframeHost"
                            )
                        } else defaultHeaders

                        // Case A: player-x.php?q= base64 payload
                        if (iframeSrc.contains("?q=") || iframeSrc.contains("&q=")) {
                            try {
                                val uri = URI(iframeSrc)
                                val query = uri.rawQuery ?: ""
                                val rawQ = query.split("&").find { it.startsWith("q=") }?.substringAfter("q=")
                                if (!rawQ.isNullOrBlank()) {
                                    val urlDecodedQ = try { URLDecoder.decode(rawQ, "UTF-8") } catch (e: Exception) { rawQ }
                                    val decodedBytes = Base64.getDecoder().decode(urlDecodedQ)
                                    val decodedStr = String(decodedBytes, StandardCharsets.UTF_8)
                                    val unquoted = try { URLDecoder.decode(decodedStr, "UTF-8") } catch (e: Exception) { decodedStr }

                                    // 1. Parse using Jsoup body fragment
                                    val fragment = Jsoup.parseBodyFragment(unquoted)
                                    val tagSources = fragment.select("source[src], video[src]")
                                    for (ts in tagSources) {
                                        var src = ts.attr("src").trim()
                                        if (src.isNotBlank()) {
                                            src = src.replace(" ", "%20")
                                            val isHls = src.contains(".m3u8")
                                            val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
                                            val mime = ts.attr("type").ifEmpty { if (isHls) "application/x-mpegURL" else "video/mp4" }
                                            sources.add(MediaSource(url = resolveUrl(src), type = type, mimeType = mime, headersRequired = embedHeaders))
                                        }
                                    }

                                    // 2. Fallback: regex search on unquoted and decodedStr
                                    if (sources.isEmpty()) {
                                        val streamRegex = Regex("""https?://[^\s"'<>]+\.(?:mp4|m3u8)[^\s"'<>]*""")
                                        val match = streamRegex.find(unquoted) ?: streamRegex.find(decodedStr)
                                        if (match != null) {
                                            val streamUrl = match.value.replace(" ", "%20")
                                            val isHls = streamUrl.contains(".m3u8")
                                            val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
                                            val mime = if (isHls) "application/x-mpegURL" else "video/mp4"
                                            sources.add(MediaSource(url = resolveUrl(streamUrl), type = type, mimeType = mime, headersRequired = embedHeaders))
                                        }
                                    }
                                    if (sources.isNotEmpty()) break
                                }
                            } catch (e: Exception) {
                                StreamHubLogger.w("HtmlSelectorAdapter", "Could not parse query param from iframe: ${e.message}")
                            }
                        }

                        // Case B: Fetch iframe page HTML and parse nested video elements or stream URLs
                        if (sources.isEmpty() && (iframeSrc.contains("player") || iframeSrc.contains("embed") || iframeSrc.contains("video") || iframeSrc.contains("/e/") || iframeSrc.contains("tube279") || iframeSrc.contains("lulu") || iframeSrc.contains("streamtape") || iframeSrc.contains("cdn1"))) {
                            try {
                                val iframeHtml = try {
                                    NetworkClient.fetchString(iframeSrc, headers = mapOf("Referer" to fullUrl))
                                } catch (e: Exception) {
                                    if (!iframeHost.isNullOrBlank()) {
                                        NetworkClient.fetchString(iframeSrc, headers = mapOf("Referer" to "https://$iframeHost/"))
                                    } else throw e
                                }
                                if (iframeHtml.isNotBlank() && !iframeHtml.contains("Just a moment...") && !iframeHtml.contains("Attention Required! | Cloudflare")) {
                                    val unpacked = if (iframeHtml.contains("eval(function(p,a,c,k,e")) unpackDeanEdwards(iframeHtml) else iframeHtml
                                    val iframeDoc = Jsoup.parse(unpacked, iframeSrc)
                                    val innerVideos = iframeDoc.select("video source[src], video[src], source[type='video/mp4']")
                                    for (v in innerVideos) {
                                        val s = v.attr("src").ifEmpty { v.attr("data-src") }
                                        if (s.isNotBlank()) {
                                            val cleanS = s.replace(" ", "%20")
                                            val isHls = cleanS.contains(".m3u8")
                                            val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
                                            sources.add(MediaSource(url = resolveUrl(cleanS), type = type, mimeType = if (isHls) "application/x-mpegURL" else "video/mp4", headersRequired = embedHeaders))
                                        }
                                    }
                                    if (sources.isEmpty()) {
                                        val jwRegex = Regex("""(?:sources|file):\s*(?:\[\s*\{\s*(?:file|src)\s*:\s*["']([^"']+)["']|["']([^"']+\.(?:mp4|m3u8)[^"']*)["'])""")
                                        val jwMatch = jwRegex.find(unpacked)
                                        if (jwMatch != null) {
                                            val u = (jwMatch.groupValues[1].ifEmpty { jwMatch.groupValues[2] }).replace(" ", "%20")
                                            val isHls = u.contains(".m3u8")
                                            val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
                                            sources.add(MediaSource(url = resolveUrl(u), type = type, mimeType = if (isHls) "application/x-mpegURL" else "video/mp4", headersRequired = embedHeaders))
                                        }
                                    }
                                    if (sources.isEmpty()) {
                                        val mp4Regex = Regex("""https?://[^\s"'<>]+\.(?:mp4|m3u8)[^\s"'<>]*""")
                                        val match = mp4Regex.find(unpacked)
                                        if (match != null) {
                                            val u = match.value.replace(" ", "%20")
                                            val isHls = u.contains(".m3u8")
                                            val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
                                            sources.add(MediaSource(url = resolveUrl(u), type = type, mimeType = if (isHls) "application/x-mpegURL" else "video/mp4", headersRequired = embedHeaders))
                                        }
                                    }
                                }
                                if (sources.isNotEmpty()) break
                            } catch (e: Exception) {
                                StreamHubLogger.w("HtmlSelectorAdapter", "Could not fetch nested player iframe ($iframeSrc): ${e.message}")
                            }
                        }
                    }
                    if (sources.isNotEmpty()) break
                }
            }

            // 3. Check for direct download or tracking link button (e.g. AagMaal tube279)
            if (sources.isEmpty()) {
                val trackingLink = doc.select("a#tracking-url, a.button[href*='tube279'], a.btn-download[href*='.mp4']").firstOrNull()
                if (trackingLink != null) {
                    val href = resolveUrl(trackingLink.attr("href"))
                    // Only accept if it is an actual direct media file, NEVER an HTML landing page
                    if (href.isNotBlank() && (href.contains(".mp4") || href.contains(".m3u8"))) {
                        sources.add(MediaSource(url = href, type = MediaSourceType.PROGRESSIVE_MP4, mimeType = "video/mp4", headersRequired = defaultHeaders))
                    }
                }
            }

            // 4. KVS (Kernel Video Sharing) media link and script extractor
            if (sources.isEmpty()) {
                // First check script flashvars (canonical player stream with auth token)
                val kvsRegex = Regex("""(?:video_url|video_alt_url|video_url_text):\s*['"]([^'"]+\.(?:mp4|m3u8)[^'"]*)['"]""")
                val match = kvsRegex.find(html)
                if (match != null) {
                    val streamUrl = match.groupValues[1]
                    val type = if (streamUrl.contains(".m3u8")) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
                    sources.add(MediaSource(url = resolveUrl(streamUrl), type = type, mimeType = if (type == MediaSourceType.HLS) "application/x-mpegURL" else "video/mp4", headersRequired = defaultHeaders))
                }

                // Fallback: direct download link if it's explicitly an mp4/m3u8 video (not a screenshot/image)
                if (sources.isEmpty()) {
                    val kvsLinks = doc.select("a[href*='/videos/get_file/'], a[href*='get_file']")
                    for (a in kvsLinks) {
                        val href = a.attr("href").trim()
                        if (href.isNotBlank()) {
                            val lowerHref = href.lowercase()
                            val isVideo = (lowerHref.contains(".mp4") || lowerHref.contains(".m3u8")) &&
                                    !lowerHref.contains(".jpg") && !lowerHref.contains(".jpeg") &&
                                    !lowerHref.contains(".png") && !lowerHref.contains(".webp") &&
                                    !lowerHref.contains("/screenshots/")
                            if (isVideo) {
                                sources.add(MediaSource(url = resolveUrl(href), type = MediaSourceType.PROGRESSIVE_MP4, mimeType = "video/mp4", headersRequired = defaultHeaders))
                                break
                            }
                        }
                    }
                }
            }

            // 5. Xvideos / XNXX proprietary setVideoUrl extractor
            if (sources.isEmpty()) {
                val xvHighRegex = Regex("""html5player\.setVideoUrlHigh\(['"]([^'"]+)['"]\)""")
                val xvLowRegex = Regex("""html5player\.setVideoUrlLow\(['"]([^'"]+)['"]\)""")
                val xvHlsRegex = Regex("""html5player\.setVideoHLS\(['"]([^'"]+)['"]\)""")

                val xvMatch = xvHlsRegex.find(html) ?: xvHighRegex.find(html) ?: xvLowRegex.find(html)
                if (xvMatch != null) {
                    val streamUrl = xvMatch.groupValues[1]
                    val type = if (streamUrl.contains(".m3u8")) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
                    sources.add(MediaSource(url = streamUrl, type = type, mimeType = if (type == MediaSourceType.HLS) "application/x-mpegURL" else "video/mp4", headersRequired = defaultHeaders))
                }
            }

            // 6. Fallback: Direct regex scan in page HTML for public progressive MP4 / HLS streams
            if (sources.isEmpty()) {
                val mp4Regex = Regex("""https?://[^\s"'<>]+\.(?:mp4|m3u8)[^\s"'<>]*""")
                val match = mp4Regex.find(html)
                if (match != null && !match.value.contains("wp-content") && !match.value.contains("preview")) {
                    val streamUrl = match.value.replace(" ", "%20")
                    val isHls = streamUrl.contains(".m3u8")
                    val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
                    val headers = resolveHeadersForStream(streamUrl, defaultHeaders)
                    sources.add(MediaSource(url = streamUrl, type = type, mimeType = if (isHls) "application/x-mpegURL" else "video/mp4", headersRequired = headers))
                }
            }

            if (sources.isEmpty()) {
                Result.failure(StreamHubError.PlaybackError(404, "No authorized playable media stream found for $fullUrl"))
            } else {
                Result.success(sources)
            }
        } catch (e: Exception) {
            Result.failure(StreamHubError.PlaybackError(500, "Failed to resolve media: ${e.message}", e))
        }
    }

    override suspend fun getRelatedContent(detailUrl: String): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        try {
            getEffectiveBaseUrl()
            val fullUrl = resolveUrl(detailUrl)
            val html = NetworkClient.fetchString(fullUrl)
            val doc = Jsoup.parse(html, activeBaseUrl)

            val relatedSelector = selectors?.relatedItems ?: ".related-videos article, .related article, .video-sidebar article"
            val elements = doc.select(relatedSelector)
            val items = elements.mapNotNull { el ->
                val linkEl = el.select("a").firstOrNull() ?: return@mapNotNull null
                val href = linkEl.attr("href").trim()
                val title = el.select(selectors?.title ?: "h2, .title, a").text().trim()
                val thumbAttrRelated = selectors?.thumbnailAttr
                val imgElRelated = el.select(selectors?.thumbnail ?: "img").firstOrNull()
                val thumb = extractValidThumbnail(el, imgElRelated, thumbAttrRelated)

                if (href.isNotBlank() && title.isNotBlank()) {
                    VideoItem(
                        id = href.hashCode().toString(),
                        providerId = config.id,
                        title = title,
                        thumbnailUrl = resolveUrl(thumb),
                        detailUrl = resolveUrl(href)
                    )
                } else null
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.success(emptyList()) // Graceful fallback
        }
    }

    fun parseListingHtml(html: String, page: Int): Result<FeedPage> {
        val doc = Jsoup.parse(html, activeBaseUrl)
        val itemSelector = selectors?.item ?: "article, div.post, div.video-item, div.item"
        val elements = doc.select(itemSelector)

        val isFsiBlog = config.id.contains("fsiblog", ignoreCase = true)

        val items = elements.mapNotNull { el ->
            val linkEl = if (el.tagName().equals("a", ignoreCase = true) && el.hasAttr("href")) el else el.select(selectors?.detailUrl ?: "a").firstOrNull() ?: return@mapNotNull null
            val href = linkEl.attr("href").trim()
            if (href.isBlank() || href.contains("THUMBNUM", ignoreCase = true) || href.startsWith("#") || href.startsWith("javascript:")) {
                return@mapNotNull null
            }
            val classNames = el.className().lowercase()
            val hrefLower = href.lowercase()

            // FSIBlog Video-Only Filter: Exclude photo galleries and stories
            if (isFsiBlog) {
                val isGallery = classNames.contains("gallery") || classNames.contains("photo") || classNames.contains("story") ||
                        hrefLower.contains("/photos/") || hrefLower.contains("/gallery/") || hrefLower.contains("/stories/")
                val isVideo = classNames.contains("porn-video") || classNames.contains("video") || hrefLower.contains("/porn-video/")
                if (isGallery && !isVideo) {
                    return@mapNotNull null
                }
            }

            var rawTitle = ""
            val titleSelector = selectors?.title
            if (!titleSelector.isNullOrBlank()) {
                val foundEl = el.select(titleSelector).firstOrNull()
                if (foundEl != null) {
                    rawTitle = foundEl.attr("data-title").ifEmpty {
                        foundEl.attr("title").ifEmpty { foundEl.text() }
                    }.trim()
                }
            }
            if (rawTitle.isBlank()) {
                rawTitle = linkEl.attr("data-title").ifEmpty {
                    linkEl.attr("title").ifEmpty {
                        el.select("header.entry-header span, .entry-title, h3.vtitle, h2, .title").text()
                    }
                }.trim()
            }
            if (rawTitle.isBlank()) {
                rawTitle = linkEl.text().trim()
            }
            // Check if rawTitle is blank, or just a duration/resolution badge (e.g. "09:29", "11:01", "HD")
            val isDurationOrBadge = rawTitle.isBlank() ||
                    rawTitle.matches(Regex("""^(?:\d{1,2}:\d{2}(?::\d{2})?|HD|FHD|4K|3K|2K|SD|\d+%)$"""))
            if (isDurationOrBadge) {
                val altText = el.select("img[alt]").attr("alt").trim()
                    .ifEmpty { el.select("a.video-thumb-info__name, [class*='thumb-info__name'], [data-role='video-title']").text().trim() }
                    .ifEmpty { linkEl.attr("title").trim() }
                if (altText.isNotBlank()) {
                    rawTitle = altText
                }
            }

            // Clean duration, resolution badges, view counts from raw title if prefixed
            var cleanedTitle = rawTitle
                .replace(Regex("""^(?:HD|FHD|4K|3K|2K|SD|\d+:\d+|\d+%\s*)+\s*"""), "")
                .trim()
                .ifEmpty { rawTitle }

            if (cleanedTitle.matches(Regex("""^(?:\d{1,2}:\d{2}(?::\d{2})?|HD|FHD|4K|3K|2K|SD|\d+%)$"""))) {
                val altText = el.select("img[alt]").attr("alt").trim()
                    .ifEmpty { el.select("a.video-thumb-info__name, [class*='thumb-info__name'], [data-role='video-title']").text().trim() }
                if (altText.isNotBlank()) {
                    cleanedTitle = altText
                }
            }

            val imgEl = if (el.tagName().equals("img", ignoreCase = true)) el else el.select(selectors?.thumbnail ?: "img").firstOrNull()
            val thumbAttr = selectors?.thumbnailAttr

            val thumb = extractValidThumbnail(el, imgEl, thumbAttr)

            if (href.isNotBlank() && cleanedTitle.isNotBlank()) {
                VideoItem(
                    id = href.hashCode().toString(),
                    providerId = config.id,
                    title = cleanedTitle,
                    thumbnailUrl = resolveUrl(thumb),
                    detailUrl = resolveUrl(href)
                )
            } else null
        }.distinctBy { it.detailUrl }

        val hasNextPage = doc.select("a.next, .pagination .next, a[rel='next']").isNotEmpty() || items.size >= 10

        return Result.success(
            FeedPage(
                items = items,
                page = page,
                hasNextPage = hasNextPage
            )
        )
    }

    private fun extractValidThumbnail(el: Element, imgEl: Element?, specifiedAttr: String?): String {
        val candidates = mutableListOf<String>()

        // 1. Specified attribute(s) if present (supports comma-separated list like "data-bg, style")
        if (!specifiedAttr.isNullOrBlank() && specifiedAttr != "src") {
            val attrTokens = specifiedAttr.split(",").map { it.trim() }
            for (attr in attrTokens) {
                if (el.hasAttr(attr)) candidates.add(el.attr(attr))
                imgEl?.let { if (it.hasAttr(attr)) candidates.add(it.attr(attr)) }
            }
        }

        // 2. High priority lazy attributes on img
        imgEl?.let { img ->
            candidates.add(img.attr("data-webp"))
            candidates.add(img.attr("data-src"))
            candidates.add(img.attr("data-lazy-src"))
            candidates.add(img.attr("data-original"))
            candidates.add(img.attr("data-srcset"))
            val srcset = img.attr("srcset")
            if (srcset.isNotBlank()) {
                candidates.add(srcset.substringBefore(" ").substringBefore(","))
            }
            candidates.add(img.attr("data-fallback-src"))
            candidates.add(img.attr("src"))
        }

        // 3. Container attributes and video poster
        val videoPoster = el.select("video[poster]").attr("poster").ifEmpty { el.attr("poster") }
        if (videoPoster.isNotBlank()) candidates.add(videoPoster)

        candidates.add(el.attr("data-bg"))
        candidates.add(el.attr("data-src"))
        candidates.add(el.attr("data-main-thumb"))
        candidates.add(el.attr("data-poster"))

        // Filter candidates for valid image URLs (must not be blank, data: SVG/GIF, or site logos/icons)
        for (cand in candidates) {
            var trimmed = cand.trim()
            if (trimmed.contains("url(")) {
                val match = Regex("""url\(['"]?([^'")]+)['"]?\)""").find(trimmed)
                if (match != null) {
                    trimmed = match.groupValues[1].trim()
                }
            }
            if (trimmed.isNotBlank() && !isPlaceholderOrLogo(trimmed)) {
                return trimmed
            }
        }

        // 4. Background image style fallback
        val bgEl = el.select(".thumb, [style*='background-image'], [style*='url(']").firstOrNull() ?: (if (el.hasAttr("style")) el else null)
        if (bgEl != null) {
            val style = bgEl.attr("style")
            val match = Regex("""url\(['"]?([^'")]+)['"]?\)""").find(style)
            if (match != null) {
                val bgUrl = match.groupValues[1].trim()
                if (bgUrl.isNotBlank() && !isPlaceholderOrLogo(bgUrl)) {
                    return bgUrl
                }
            }
        }

        return ""
    }

    internal fun isPlaceholderOrLogo(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        val lower = url.lowercase()
        return lower.contains("logo") ||
               lower.contains("favicon") ||
               lower.contains("placeholder") ||
               lower.contains("default-thumb") ||
               lower.contains("site-icon") ||
               lower.contains("header-icon") ||
               lower.startsWith("data:")
    }

    internal fun unpackDeanEdwards(script: String): String {
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

    private fun resolveHeadersForStream(streamUrl: String, baseHeaders: Map<String, String>): Map<String, String> {
        val lower = streamUrl.lowercase()
        val targetReferer = when {
            lower.contains("tube279.com") || lower.contains("siesta583") -> "https://tube279.com/"
            lower.contains("tnmr.org") || lower.contains("lulucdn") || lower.contains("lulustream") || lower.contains("luluvdo") -> "https://luluvdo.com/"
            lower.contains("tpead.net") || lower.contains("streamtape.com") || lower.contains("tapecontent.net") -> "https://streamtape.com/"
            lower.contains("xhpingcdn") || lower.contains("xhcdn") || lower.contains("xhamster") -> "https://xhamster.desi/"
            else -> null
        }
        return if (targetReferer != null) {
            val uri = try { URI(targetReferer) } catch (e: Exception) { null }
            val origin = if (uri != null) "${uri.scheme}://${uri.host}" else targetReferer.removeSuffix("/")
            baseHeaders + mapOf("Referer" to targetReferer, "Origin" to origin)
        } else {
            baseHeaders
        }
    }

    private fun resolveUrl(path: String): String {
        return try {
            val trimmed = path.trim()
            if (trimmed.startsWith("//")) {
                "https:$trimmed"
            } else {
                val base = URI(activeBaseUrl)
                base.resolve(trimmed).toString()
            }
        } catch (e: Exception) {
            path
        }
    }
}
