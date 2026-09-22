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
import com.thedesitadka.core.model.isCategoryUrl
import com.thedesitadka.core.network.CloudflareChallengeException
import com.thedesitadka.core.network.NetworkClient
import com.thedesitadka.core.security.StreamHubLogger
import com.thedesitadka.provider.ProviderAdapter
import com.thedesitadka.core.config.DomainResolver
import com.thedesitadka.provider.plugins.HostResolverEngine
import com.thedesitadka.provider.plugins.VixeoResolverPlugin
import com.thedesitadka.provider.plugins.FirestreamResolverPlugin
import com.thedesitadka.provider.plugins.LuluvdoResolverPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class HtmlSelectorAdapter(
    val config: ProviderConfig,
    private val domainResolver: DomainResolver? = null
) : ProviderAdapter {

    override val categoryNavPath: String?
        get() = config.navigation?.categories

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
        if (config.id == "prmovies_church" || config.id == "prmovies" || config.id.startsWith("prmovies")) {
            val preset = com.thedesitadka.provider.plugins.PrmoviesMenuCategories.getCategories(config.id, activeBaseUrl)
            if (preset.isNotEmpty()) {
                return@withContext Result.success(preset)
            }
        }
        try {
            getEffectiveBaseUrl()
            val catPath = nav?.categories ?: "/"
            val targetUrl = resolveUrl(catPath)
            val html = NetworkClient.fetchString(targetUrl)
            val categories = parseCategoriesHtml(html)
            Result.success(categories)
        } catch (e: Exception) {
            StreamHubLogger.e("HtmlSelectorAdapter", "Failed to fetch categories: ${e.message}")
            Result.failure(StreamHubError.ParsingError(config.id, "Failed to get categories: ${e.message}", e))
        }
    }

    override suspend fun getCategoryFeed(categoryUrl: String, page: Int): Result<FeedPage> = withContext(Dispatchers.IO) {
        try {
            getEffectiveBaseUrl()
            val fullUrl = resolveUrl(categoryUrl)
            val pagedUrl = buildPagedUrl(fullUrl, page)
            val html = NetworkClient.fetchString(pagedUrl)
            parseListingHtml(html, page)
        } catch (e: Exception) {
            StreamHubLogger.e("HtmlSelectorAdapter", "Failed to get category feed for '$categoryUrl' (page $page): ${e.message}")
            val code = (e as? CloudflareChallengeException)?.statusCode ?: 0
            Result.failure(StreamHubError.NetworkError(code, e.message ?: "Failed to get category feed", e))
        }
    }

    internal fun buildPagedUrl(fullUrl: String, page: Int): String {
        if (page <= 1) return fullUrl

        val cleanUrl = fullUrl.substringBefore("?")
        val query = if (fullUrl.contains("?")) fullUrl.substringAfter("?") else null

        // 1. Check if query already has a pagination parameter (e.g. p=2, page=2)
        if (!query.isNullOrBlank()) {
            if (Regex("""([?&](?:p|page))=\d+""").containsMatchIn(query)) {
                val updatedQuery = query.replace(Regex("""([?&](?:p|page))=\d+"""), "$1=$page")
                return "$cleanUrl?$updatedQuery"
            }
            if (config.id == "hqporner" || cleanUrl.contains("hqporner.com")) {
                return "$cleanUrl?$query&p=$page"
            }
            if (config.id in listOf("pornhub", "xvideos", "youporn", "redtube", "tube8", "freeonestube", "xhamster") || query.contains("s=") || query.contains("q=") || query.contains("k=") || query.contains("search=")) {
                val param = if (config.id == "xvideos" && query.contains("k=")) "p" else "page"
                return "$cleanUrl?$query&$param=$page"
            }
        }

        // 2. WordPress /page/\d+/ pattern
        if (Regex("""/page/\d+/?""").containsMatchIn(cleanUrl)) {
            val replaced = cleanUrl.replace(Regex("""/page/\d+/?"""), "/page/$page/")
            return if (!query.isNullOrBlank()) "$replaced?$query" else replaced
        }

        // 2.5 BrazzPw theme slug pagination: /videos/.../theme-slug/ -> /videos/.../page/{page}/theme-slug/
        if (config.id == "brazzpw" || cleanUrl.contains("brazzpw")) {
            val trimmed = cleanUrl.trimEnd('/')
            val segments = trimmed.split("/")
            if (segments.size >= 2 && segments.last().contains("-")) {
                val lastSegment = segments.last()
                val prefix = trimmed.removeSuffix("/$lastSegment")
                val res = "$prefix/page/$page/$lastSegment/"
                return if (!query.isNullOrBlank()) "$res?$query" else res
            }
        }

        // 2.6 WordPress /page/{page}/ pagination for WordPress-based providers
        val isWordPress = (config.navigation?.page?.contains("/page/") == true ||
                config.id.startsWith("aagmaal") || cleanUrl.contains("aagmaal") ||
                config.id == "netfapx" || cleanUrl.contains("netfapx.com")) &&
                !(config.id == "brazzpw" || cleanUrl.contains("brazzpw"))
        if (isWordPress) {
            val res = cleanUrl.trimEnd('/') + "/page/$page/"
            return if (!query.isNullOrBlank()) "$res?$query" else res
        }

        // 3. HQPorner pagination rules
        if (config.id == "hqporner" || cleanUrl.contains("hqporner.com")) {
            val trimmed = cleanUrl.trimEnd('/')
            // Root home pagination: https://hqporner.com or https://hqporner.com/ -> /hdporn/2
            if (trimmed.endsWith("hqporner.com")) {
                val res = "$trimmed/hdporn/$page"
                return if (!query.isNullOrBlank()) "$res?$query" else res
            }
            // If already on /hdporn/\d+ pagination page
            if (Regex("""/hdporn/(\d+)$""").containsMatchIn(trimmed)) {
                val replaced = trimmed.replace(Regex("""/hdporn/\d+$"""), "/hdporn/$page")
                return if (!query.isNullOrBlank()) "$replaced?$query" else replaced
            }
            // Category/Actress pagination with existing numeric suffix: e.g. /category/deepthroat/2 -> /category/deepthroat/3
            if (!cleanUrl.endsWith(".html") && Regex("""/(\d+)$""").containsMatchIn(trimmed)) {
                val replaced = trimmed.replace(Regex("""/(\d+)$"""), "/$page")
                return if (!query.isNullOrBlank()) "$replaced?$query" else replaced
            }
            // Category/Actress pagination initial page: e.g. /category/deepthroat -> /category/deepthroat/2
            val res = "$trimmed/$page"
            return if (!query.isNullOrBlank()) "$res?$query" else res
        }

        // 3.5 PornHouse and PornHD4K pagination (/page-{page})
        if (config.id == "pornhouse" || config.id == "pornhd4k" || cleanUrl.contains("pornhouse.me") || cleanUrl.contains("pornhd4k.net")) {
            val trimmed = cleanUrl.trimEnd('/')
            if (Regex("""/page-\d+$""").containsMatchIn(trimmed)) {
                val replaced = trimmed.replace(Regex("""/page-\d+$"""), "/page-$page")
                return if (!query.isNullOrBlank()) "$replaced?$query" else replaced
            }
            val res = "$trimmed/page-$page"
            return if (!query.isNullOrBlank()) "$res?$query" else res
        }

        // 3.6 SxyPrn offset-based pagination (/orgasm/{offset} or /{offset})
        if (config.id == "sxyprn" || cleanUrl.contains("sxyprn.com")) {
            val offset = (page - 1) * 30
            val trimmed = cleanUrl.trimEnd('/')
            if (Regex("""/orgasm/\d+$""").containsMatchIn(trimmed)) {
                val replaced = trimmed.replace(Regex("""/orgasm/\d+$"""), "/orgasm/$offset")
                return if (!query.isNullOrBlank()) "$replaced?$query" else replaced
            }
            if (trimmed.endsWith("sxyprn.com") || trimmed.endsWith("sxyprn.com/")) {
                val res = "$trimmed/orgasm/$offset"
                return if (!query.isNullOrBlank()) "$res?$query" else res
            }
            if (Regex("""/\d+$""").containsMatchIn(trimmed)) {
                val replaced = trimmed.replace(Regex("""/\d+$"""), "/$offset")
                return if (!query.isNullOrBlank()) "$replaced?$query" else replaced
            }
            val res = "$trimmed/$offset"
            return if (!query.isNullOrBlank()) "$res?$query" else res
        }

        // 4. If URL ends with a numeric page segment already (e.g. /2/, /channels/brazzers/2/, /models-2/2/)
        // But distinguish from single video IDs like /video/12345/ or single video HTML pages
        val isSingleVideo = Regex("""/videos?/\d+/?$""").containsMatchIn(cleanUrl) || cleanUrl.contains(".html")
        if (!isSingleVideo && Regex("""/(\d+)/?$""").containsMatchIn(cleanUrl)) {
            val replaced = cleanUrl.replace(Regex("""/(\d+)/?$"""), "/$page/")
            return if (!query.isNullOrBlank()) "$replaced?$query" else replaced
        }

        // 5. WordPress / Netfapx / PRMovies pagination
        if (config.id == "netfapx" || config.id.startsWith("prmovies") || cleanUrl.contains("netfapx.com") || cleanUrl.contains("prmovies.")) {
            val trimmed = cleanUrl.trimEnd('/')
            if (Regex("""/page/\d+$""").containsMatchIn(trimmed)) {
                val replaced = trimmed.replace(Regex("""/page/\d+$"""), "/page/$page")
                return if (!query.isNullOrBlank()) "$replaced/?$query" else "$replaced/"
            }
            val res = "$trimmed/page/$page/"
            return if (!query.isNullOrBlank()) "$res?$query" else res
        }

        // 5.5 Porn4Days pagination (/newest -> /newest/page2/, /newest/page2/ -> /newest/page3/)
        if (config.id == "porn4days" || cleanUrl.contains("porn4days.pw")) {
            val trimmed = cleanUrl.trimEnd('/')
            if (Regex("""/page\d+$""").containsMatchIn(trimmed)) {
                val replaced = trimmed.replace(Regex("""/page\d+$"""), "/page$page/")
                return if (!query.isNullOrBlank()) "$replaced?$query" else replaced
            }
            val res = "$trimmed/page$page/"
            return if (!query.isNullOrBlank()) "$res?$query" else res
        }

        // 5.6 XNXX / XVideos pagination
        if (config.id == "xnxx" || cleanUrl.contains("xnxx.com") || cleanUrl.contains("xnxx.")) {
            val trimmed = cleanUrl.trimEnd('/')
            if (trimmed.endsWith("/best") || trimmed.contains("/best/")) {
                val base = trimmed.substringBefore("/best") + "/best"
                return if (!query.isNullOrBlank()) "$base/$page?$query" else "$base/$page"
            }
            if (trimmed.contains("/search/")) {
                return if (!query.isNullOrBlank()) "$trimmed/$page?$query" else "$trimmed/$page"
            }
            val res = "$trimmed/$page"
            return if (!query.isNullOrBlank()) "$res?$query" else res
        }

        if (config.id == "xvideos" || cleanUrl.contains("xvideos")) {
            val trimmed = cleanUrl.trimEnd('/')
            if (trimmed.endsWith("/new") || trimmed.contains("/new/")) {
                val base = trimmed.substringBefore("/new") + "/new"
                return if (!query.isNullOrBlank()) "$base/$page/?$query" else "$base/$page/"
            }
            return if (!query.isNullOrBlank()) "$trimmed/$page/?$query" else "$trimmed/$page/"
        }

        // 5.7 xHamster path pagination (/newest -> /newest/2, /best -> /best/2)
        if (config.id == "xhamster" || cleanUrl.contains("xhamster")) {
            val trimmed = cleanUrl.trimEnd('/')
            if (Regex("""/\d+$""").containsMatchIn(trimmed)) {
                val replaced = trimmed.replace(Regex("""/\d+$"""), "/$page")
                return if (!query.isNullOrBlank()) "$replaced?$query" else replaced
            }
            val res = "$trimmed/$page"
            return if (!query.isNullOrBlank()) "$res?$query" else res
        }

        // 5.8 PornHub / RedTube / YouPorn query-based page fallback
        if (config.id in listOf("pornhub", "redtube", "youporn") || cleanUrl.contains("pornhub") || cleanUrl.contains("redtube") || cleanUrl.contains("youporn") || cleanUrl.contains("you-porn")) {
            return if (query.isNullOrBlank()) "$cleanUrl?page=$page" else "$cleanUrl?$query&page=$page"
        }

        // 5.9 YouJizz pagination (/most-popular/2.html or /search/...-2.html)
        if (config.id == "youjizz" || cleanUrl.contains("youjizz")) {
            if (cleanUrl.contains(".html")) {
                val replaced = cleanUrl.replace(Regex("""/\d+\.html"""), "/$page.html")
                    .replace(Regex("""-(\d+)\.html"""), "-$page.html")
                return if (replaced != cleanUrl) replaced else cleanUrl.replace(".html", "/$page.html")
            }
            val trimmed = cleanUrl.trimEnd('/')
            return "$trimmed/$page.html"
        }

        // 5.10 TNAFlix & EmpFlix pagination (/featured/2)
        if (config.id in listOf("tnaflix", "empflix") || cleanUrl.contains("tnaflix") || cleanUrl.contains("empflix")) {
            val trimmed = cleanUrl.trimEnd('/')
            if (Regex("""/\d+$""").containsMatchIn(trimmed)) {
                val replaced = trimmed.replace(Regex("""/\d+$"""), "/$page")
                return if (!query.isNullOrBlank()) "$replaced?$query" else replaced
            }
            val res = if (trimmed.endsWith("/featured") || trimmed.contains("/category/") || trimmed.contains("/channels/")) {
                "$trimmed/$page"
            } else {
                "$trimmed/featured/$page"
            }
            return if (!query.isNullOrBlank()) "$res?$query" else res
        }

        // 5.11 Eporner pagination (/0/2/ or /cat/.../2/)
        if (config.id == "eporner" || cleanUrl.contains("eporner")) {
            val trimmed = cleanUrl.trimEnd('/')
            if (Regex("""/\d+$""").containsMatchIn(trimmed)) {
                val replaced = trimmed.replace(Regex("""/\d+$"""), "/$page")
                return if (!query.isNullOrBlank()) "$replaced/?$query" else "$replaced/"
            }
            val res = "$trimmed/$page/"
            return if (!query.isNullOrBlank()) "$res?$query" else res
        }

        // 5.12 DrTuber & NuVid pagination
        if (config.id in listOf("drtuber", "nuvid") || cleanUrl.contains("drtuber") || cleanUrl.contains("nuvid")) {
            val trimmed = cleanUrl.trimEnd('/')
            if (Regex("""/\d+$""").containsMatchIn(trimmed)) {
                val replaced = trimmed.replace(Regex("""/\d+$"""), "/$page")
                return if (!query.isNullOrBlank()) "$replaced/?$query" else "$replaced/"
            }
            val res = if (config.id == "nuvid" || cleanUrl.contains("nuvid")) "$trimmed/$page" else "$trimmed/$page/"
            return if (!query.isNullOrBlank()) "$res?$query" else res
        }

        // 5.13 DefineBabe pagination (?page={page})
        if (config.id == "definebabe" || cleanUrl.contains("definebabe.com")) {
            return if (query.isNullOrBlank()) "$cleanUrl?page=$page" else "$cleanUrl?$query&page=$page"
        }

        // 5.14 3Movs pagination (/latest-updates/{page}/)
        if (config.id == "three_movs" || cleanUrl.contains("3movs.com")) {
            val trimmed = cleanUrl.trimEnd('/')
            if (Regex("""/\d+$""").containsMatchIn(trimmed)) {
                val replaced = trimmed.replace(Regex("""/\d+$"""), "/$page")
                return if (!query.isNullOrBlank()) "$replaced/?$query" else "$replaced/"
            }
            val res = if (trimmed.endsWith("/latest-updates") || trimmed.contains("/categories/") || trimmed.contains("/channels/")) {
                "$trimmed/$page/"
            } else {
                "$trimmed/latest-updates/$page/"
            }
            return if (!query.isNullOrBlank()) "$res?$query" else res
        }

        // 5.15 AVS Network pagination (txxx, upornia, hdzog)
        if (config.id in listOf("txxx", "upornia", "hdzog") || cleanUrl.contains("txxx.com") || cleanUrl.contains("upornia.com") || cleanUrl.contains("hdzog.com")) {
            if (fullUrl.contains("/api/json/videos2/")) {
                var replaced = fullUrl.replace(Regex("""\.\.\d+\.all"""), "..$page.all")
                replaced = replaced.replace(Regex("""categories\.([^.]+)\.\d+\."""), "categories.$1.$page.")
                return replaced
            }
            if (fullUrl.contains("/api/videos2.php")) {
                return fullUrl.replace(Regex("""search\.\.\d+\."""), "search..$page.")
            }
        }

        // 5.16 TMDB / MovieNerds / Cineapse pagination
        if (config.id in listOf("movienerds", "cineapse") || cleanUrl.contains("api.themoviedb.org") || cleanUrl.contains("/tmdb/")) {
            val clean = cleanUrl.replace(Regex("""[&?]page=\d+"""), "")
            val delimiter = if (clean.contains("?")) "&" else "?"
            return "$clean${delimiter}page=$page"
        }

        // 6. Default tube sites (fpo, hello, max, ok_porn, ok_xxx, pornstars_tube, etc.)
        // Append /{page}/ to category, channel, model, search, or root path
        val base = cleanUrl.trimEnd('/')
        val res = "$base/$page/"
        return if (!query.isNullOrBlank()) "$res?$query" else res
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
                    if (config.id == "hqporner" || activeBaseUrl.contains("hqporner")) {
                        "$searchPath&p=$page"
                    } else {
                        "/page/$page/" + searchPath.substring(1)
                    }
                } else if (searchPath.contains("?")) {
                    val pathPart = searchPath.substringBefore("?")
                    val queryPart = searchPath.substringAfter("?")
                    if (config.id == "hqporner" || activeBaseUrl.contains("hqporner")) {
                        "$pathPart?$queryPart&p=$page"
                    } else {
                        "${pathPart.removeSuffix("/")}/page/$page/?$queryPart"
                    }
                } else {
                    // Path-based search like /search/{query}/
                    if (config.id in listOf("max", "ok_porn", "ok_xxx", "fpo") ||
                        activeBaseUrl.contains("max.porn") || activeBaseUrl.contains("ok.porn") ||
                        activeBaseUrl.contains("ok.xxx") || activeBaseUrl.contains("fpo.xxx")) {
                        "${searchPath.removeSuffix("/")}/$page/"
                    } else {
                        "${searchPath.removeSuffix("/")}/page/$page"
                    }
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
        if (isCloudflareChallenge(html)) {
            return Result.failure(CloudflareChallengeException(
                url = detailUrl,
                host = try { URI(detailUrl).host ?: activeBaseUrl } catch (e: Exception) { activeBaseUrl },
                statusCode = 403,
                message = "Cloudflare security challenge detected on $detailUrl"
            ))
        }

        val trimmed = html.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                val jsonEl = Json.parseToJsonElement(trimmed)
                if (jsonEl is kotlinx.serialization.json.JsonObject) {
                    val id = jsonEl["id"]?.jsonPrimitive?.content ?: detailUrl.hashCode().toString()
                    val title = jsonEl["title"]?.jsonPrimitive?.content ?: jsonEl["name"]?.jsonPrimitive?.content ?: "Title $id"
                    val overview = jsonEl["overview"]?.jsonPrimitive?.content ?: ""
                    val posterPath = jsonEl["poster_path"]?.jsonPrimitive?.content
                    val backdropPath = jsonEl["backdrop_path"]?.jsonPrimitive?.content
                    val thumb = when {
                        !posterPath.isNullOrBlank() -> "https://image.tmdb.org/t/p/w500$posterPath"
                        !backdropPath.isNullOrBlank() -> "https://image.tmdb.org/t/p/w500$backdropPath"
                        else -> ""
                    }
                    val mediaType = if (jsonEl.containsKey("seasons") || jsonEl.containsKey("number_of_seasons")) "tv" else "movie"
                    val meta = mutableMapOf<String, String>()
                    meta["mediaType"] = mediaType
                    meta["tmdbId"] = id
                    jsonEl["imdb_id"]?.jsonPrimitive?.content?.let { meta["imdbId"] = it }
                    jsonEl["number_of_seasons"]?.jsonPrimitive?.content?.let { meta["seasonsCount"] = it }
                    jsonEl["number_of_episodes"]?.jsonPrimitive?.content?.let { meta["episodesCount"] = it }

                    val downloadAvail = if (hasCapability(ProviderCapability.DOWNLOAD)) {
                        DownloadAvailability.AUTHORIZED
                    } else {
                        DownloadAvailability.NOT_SUPPORTED
                    }

                    return Result.success(
                        VideoItem(
                            id = id,
                            providerId = config.id,
                            title = title,
                            description = overview,
                            thumbnailUrl = thumb,
                            detailUrl = detailUrl,
                            playbackAvailability = PlaybackAvailability.AVAILABLE,
                            downloadAvailability = downloadAvail,
                            metadata = meta
                        )
                    )
                }
            } catch (e: Exception) {
                StreamHubLogger.w("HtmlSelectorAdapter", "Failed to parse JSON details: ${e.message}")
            }
        }

        try {
            val doc = Jsoup.parse(html, activeBaseUrl)

            val title = doc.select(selectors?.detailTitle ?: "h1").text().trim().ifEmpty {
                doc.title().substringBefore(" - ").substringBefore(" | ").trim()
            }

            val desc = selectors?.detailDescription?.let { sel ->
                doc.select(sel).firstOrNull()?.text()?.trim()
            } ?: ""

            // Extract thumbnail with meta (og:image), poster, data-src, src fallbacks
            var thumb = ""
            val thumbEl = selectors?.detailThumbnail?.let { doc.select(it).firstOrNull() }
            val candidateThumb = extractValidThumbnail(
                el = doc.body() ?: doc,
                imgEl = thumbEl ?: doc.select("img.video-main-thumb, img.video-img, meta[property='og:image']").firstOrNull(),
                specifiedAttr = null
            )
            if (!isPlaceholderOrLogo(candidateThumb)) {
                thumb = candidateThumb
            }
            if (thumb.isEmpty()) {
                val ogImg = doc.select("meta[property='og:image'], meta[name='twitter:image']").attr("content")
                if (!isPlaceholderOrLogo(ogImg)) {
                    thumb = ogImg
                }
            }
            if (thumb.isEmpty()) {
                // Select poster from main video player, avoiding recommendation or related trailers (.wpst-trailer, .trailer, .loop-video)
                val poster = doc.select("video:not(.wpst-trailer):not(.trailer)[poster]").attr("poster")
                    .ifEmpty {
                        doc.select("video[poster]").firstOrNull { v ->
                            v.closest(".wpst-trailer, .trailer, .video-preview-item, .related, .recommendations") == null
                        }?.attr("poster") ?: ""
                    }.ifEmpty { doc.select("video[poster]").attr("poster") }
                if (!isPlaceholderOrLogo(poster)) {
                    thumb = poster
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
        if (config.id == "movienerds" || activeBaseUrl.contains("movienerds.site")) {
            return@withContext resolveMovieNerdsMedia(detailUrl)
        }
        if (config.id == "cineapse" || activeBaseUrl.contains("cineapse.net")) {
            return@withContext resolveCineapseMedia(detailUrl)
        }
        try {
            getEffectiveBaseUrl()
            val fullUrl = resolveUrl(detailUrl)
            val html = NetworkClient.fetchString(fullUrl)
            if (isCloudflareChallenge(html)) {
                return@withContext Result.failure(CloudflareChallengeException(
                    url = fullUrl,
                    host = try { URI(fullUrl).host ?: activeBaseUrl } catch (e: Exception) { activeBaseUrl },
                    statusCode = 403,
                    message = "Cloudflare security challenge detected on $fullUrl"
                ))
            }

            // 1. Direct/Internal Media: Inspect page for native video, HLS/MP4 streams, KVS/proprietary players
            val directMediaResult = parsePlayableMediaHtml(html, fullUrl)
            if (directMediaResult.isSuccess && directMediaResult.getOrThrow().isNotEmpty()) {
                return@withContext directMediaResult
            }

            // 2. External Video Hosting: If no direct media found, check registered host plugins (Firestream, Luluvdo, Vixeo)
            val doc = Jsoup.parse(html, activeBaseUrl)
            val hostResolved = HostResolverEngine.resolveFirstSupportedEmbed(doc, fullUrl)
            if (hostResolved != null && hostResolved.isSuccess) {
                return@withContext Result.success(listOf(hostResolved.getOrThrow()))
            }

            // 3. Fallback or structured error
            if (directMediaResult.isSuccess && directMediaResult.getOrThrow().isEmpty()) {
                val candidateHosts = HostResolverEngine.extractCandidateUrls(doc)
                val msg = if (candidateHosts.isNotEmpty()) {
                    "External host resolution failed for: ${candidateHosts.joinToString()}"
                } else {
                    "No streamable media source or supported host embed found on page"
                }
                Result.failure(StreamHubError.PlaybackError(404, msg))
            } else {
                directMediaResult
            }
        } catch (e: CloudflareChallengeException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(StreamHubError.PlaybackError(500, "Failed to resolve media: ${e.message}", e))
        }
    }


    fun parsePlayableMediaHtml(html: String, fullUrl: String): Result<List<MediaSource>> {
        if (config.id == "movienerds" || fullUrl.contains("movienerds.site")) {
            val res = parseMovieNerdsStreamJson(html, fullUrl)
            if (res.isSuccess && res.getOrThrow().isNotEmpty()) return res
        }
        if (config.id == "cineapse" || fullUrl.contains("cineapse.net")) {
            return Result.success(buildCineapseMediaSources(fullUrl))
        }
        if (isCloudflareChallenge(html)) {
            return Result.failure(CloudflareChallengeException(
                url = fullUrl,
                host = try { URI(fullUrl).host ?: activeBaseUrl } catch (e: Exception) { activeBaseUrl },
                statusCode = 403,
                message = "Cloudflare security challenge detected on $fullUrl"
            ))
        }
        return try {
            val doc = Jsoup.parse(html, activeBaseUrl)

            val sources = mutableListOf<MediaSource>()
            val defaultHeaders = mapOf(
                "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
                "Referer" to "$activeBaseUrl/",
                "Origin" to activeBaseUrl
            )

            // 1. Try direct video source selectors
            val videoSourceSelector = selectors?.videoSource ?: "meta[itemprop*='contentUrl'], meta[itemprop*='contentURL'], meta[itemprop*='contenturl'], div.kt-player[data-url], div[data-url*='/hls/'], div[data-url*='.mp4'], video source[src], video[src], source[type='video/mp4']"
            val videoElements = doc.select(videoSourceSelector)
            for (el in videoElements) {
                // Skip hover preview trailers / teasers in recommendation cards
                val isTeaserPreview = el.hasClass("wpst-trailer") || el.parent()?.hasClass("wpst-trailer") == true
                    || el.hasClass("hvp_player")
                    || el.closest(".wpst-trailer, .trailer, .video-preview-item") != null
                    || (el.closest(".post_el_small") != null && el.closest(".post_el_post") == null)
                if (isTeaserPreview) {
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
                    if (!href.contains(".mp4") && !href.contains(".m3u8") && !href.contains(".vid")) {
                        continue
                    }
                }
                if (el.tagName().equals("script", ignoreCase = true)) {
                    continue
                }
                val src = el.attr("src")
                    .ifEmpty { el.attr("data-src") }
                    .ifEmpty { el.attr("data-url") }
                    .ifEmpty { el.attr("content") }
                    .ifEmpty { if (el.tagName().equals("a", ignoreCase = true) || el.tagName().equals("link", ignoreCase = true)) el.attr("href") else "" }
                if (src.isNotBlank()) {
                    val rawSrcTrim = src.trim()
                    if (rawSrcTrim.startsWith("blob:", ignoreCase = true) || rawSrcTrim.startsWith("data:", ignoreCase = true) || rawSrcTrim.startsWith("javascript:", ignoreCase = true)) {
                        continue
                    }
                    val resolved = resolveUrl(rawSrcTrim)
                    val lower = resolved.lowercase()
                    if (lower.startsWith("blob:") || lower.startsWith("data:") || lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") || lower.contains(".webp") || lower.contains("/screenshots/")) {
                        continue
                    }
                    if (lower.endsWith(".js") || lower.contains(".js?") || lower.contains("/smartpop/") || lower.contains("whitetrafsa") || lower.contains("hipodi")
                        || lower.contains("mavrtracktor") || lower.endsWith(".html") || lower.endsWith(".php")
                        || lower.contains("a-ads") || lower.contains("banner") || lower.contains("popunder")) {
                        continue
                    }
                    val isHls = isHlsStream(resolved) || el.attr("type").contains("mpegurl", ignoreCase = true)
                    val mime = when {
                        isHls -> "application/x-mpegURL"
                        resolved.contains(".mpd") -> "application/dash+xml"
                        else -> el.attr("type").ifEmpty { "video/mp4" }
                    }
                    val type = when {
                        isHls -> MediaSourceType.HLS
                        resolved.contains(".mpd") -> MediaSourceType.DASH
                        else -> MediaSourceType.PROGRESSIVE_MP4
                    }
                    val headers = resolveHeadersForStream(resolved, defaultHeaders)
                    val rawQual = el.attr("label").ifEmpty { el.attr("title") }.ifEmpty { el.attr("data-res") }.ifEmpty { el.attr("res") }
                    val qual = when {
                        rawQual.isNotBlank() && !rawQual.equals("auto", ignoreCase = true) -> rawQual
                        resolved.contains("_1080p") || resolved.contains("-1080p") -> "1080p"
                        resolved.contains("_720p") || resolved.contains("-720p") -> "720p"
                        resolved.contains("_480p") || resolved.contains("-480p") -> "480p"
                        resolved.contains("_360p") || resolved.contains("-360p") -> "360p"
                        resolved.contains("_240p") || resolved.contains("-240p") -> "240p"
                        rawQual.isNotBlank() -> rawQual
                        else -> "auto"
                    }
                    sources.add(MediaSource(url = resolved, type = type, mimeType = mime, quality = qual, headersRequired = headers))
                }
            }

            // 2. Check KVS / Script player media link and script extractor (canonical direct player streams)
            if (sources.isEmpty()) {
                // Check videojs / JSON sources array: {'src':'https:...', 'type':'video/mp4', 'label':'720p'}
                val videoJsRegex = Regex("""['"]?src['"]?\s*:\s*['"]([^'"]+\.(?:mp4|m3u8)[^'"]*)['"](?:[^}]*?['"]?(?:label|res)['"]?\s*:\s*['"]?([a-zA-Z0-9]+)['"]?)?""")
                for (match in videoJsRegex.findAll(html)) {
                    val rawUrl = match.groupValues[1].replace("\\/", "/")
                    val streamUrl = resolveUrl(rawUrl)
                    val rawQual = match.groupValues.getOrNull(2)?.trim() ?: ""
                    val qual = when {
                        rawQual.isNotBlank() && !rawQual.equals("auto", ignoreCase = true) -> rawQual
                        Regex("""(?i)[/_.-]?(1080|720|480|360|240)p""").containsMatchIn(streamUrl) -> {
                            val m = Regex("""(?i)[/_.-]?(1080|720|480|360|240)p""").find(streamUrl)
                            m?.groupValues?.get(1)?.let { "${it}p" } ?: "auto"
                        }
                        else -> "auto"
                    }
                    if (sources.none { it.url == streamUrl }) {
                        val isHls = isHlsStream(streamUrl)
                        val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
                        val headers = resolveHeadersForStream(streamUrl, defaultHeaders)
                        sources.add(MediaSource(url = streamUrl, type = type, mimeType = if (isHls) "application/x-mpegURL" else "video/mp4", quality = qual, headersRequired = headers))
                    }
                }

                // Check both video_alt_url (HQ) and video_url (LQ) with HQ first
                val altRegex = Regex("""['"]?video_alt_url['"]?\s*[:=]\s*['"]([^'"]+\.(?:mp4|m3u8)[^'"]*)['"]""")
                val altMatch = altRegex.find(html)
                if (altMatch != null) {
                    val streamUrl = resolveUrl(altMatch.groupValues[1].replace("\\/", "/"))
                    if (sources.none { it.url == streamUrl }) {
                        val isHls = isHlsStream(streamUrl)
                        val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
                        val headers = resolveHeadersForStream(streamUrl, defaultHeaders)
                        sources.add(MediaSource(url = streamUrl, type = type, mimeType = if (isHls) "application/x-mpegURL" else "video/mp4", quality = "HQ", headersRequired = headers))
                    }
                }

                val stdRegex = Regex("""['"]?(?:video_url|video_url_text|file)['"]?\s*[:=]\s*['"]([^'"]+\.(?:mp4|m3u8)[^'"]*)['"]|['"]src['"]\s*:\s*['"]([^'"]+\.(?:mp4|m3u8)[^'"]*)['"]""")
                val stdMatch = stdRegex.find(html)
                if (stdMatch != null) {
                    val rawUrl = (stdMatch.groupValues[1].ifEmpty { stdMatch.groupValues.getOrNull(2) ?: "" }).replace("\\/", "/")
                    if (rawUrl.isNotBlank()) {
                        val streamUrl = resolveUrl(rawUrl)
                        if (sources.none { it.url == streamUrl }) {
                            val isHls = isHlsStream(streamUrl)
                            val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
                            val headers = resolveHeadersForStream(streamUrl, defaultHeaders)
                            sources.add(MediaSource(url = streamUrl, type = type, mimeType = if (isHls) "application/x-mpegURL" else "video/mp4", quality = "LQ", headersRequired = headers))
                        }
                    }
                }
            }

            // 2.45 PlayerJS format extractor (DefineBabe, etc.)
            if (sources.isEmpty()) {
                val playerJsRegex = Regex("""['"]?(?:file|video)['"]?\s*:\s*['"](\[[^'"]+\][^'"]+|//[^\s'"]+/player/get_video\.php[^\s'"]*)['"]""")
                val pjsMatch = playerJsRegex.find(html)
                if (pjsMatch != null) {
                    val rawVal = pjsMatch.groupValues[1]
                    val parts = rawVal.split(",")
                    for (part in parts) {
                        val trimmedPart = part.trim()
                        if (trimmedPart.isBlank()) continue
                        val qualMatch = Regex("""^\[([0-9a-zA-Z]+)\](.*)$""").find(trimmedPart)
                        val qual = qualMatch?.groupValues?.get(1) ?: "720p"
                        val rawUrl = (qualMatch?.groupValues?.get(2) ?: trimmedPart).trim()
                        val fullMediaUrl = when {
                            rawUrl.startsWith("//") -> "https:$rawUrl"
                            rawUrl.startsWith("http") -> rawUrl
                            else -> resolveUrl(rawUrl)
                        }
                        val headers = resolveHeadersForStream(fullMediaUrl, defaultHeaders)
                        if (sources.none { it.url == fullMediaUrl }) {
                            sources.add(
                                MediaSource(
                                    url = fullMediaUrl,
                                    type = MediaSourceType.PROGRESSIVE_MP4,
                                    mimeType = "video/mp4",
                                    quality = qual,
                                    headersRequired = headers
                                )
                            )
                        }
                    }
                }
            }

            // 2.5 Aylo network videoUrl extractor (PornHub, RedTube, YouPorn, Tube8)
            if (sources.isEmpty()) {
                val ayloMediaRegex = Regex(""""videoUrl"\s*:\s*"([^"]+)"""")
                for (match in ayloMediaRegex.findAll(html)) {
                    val rawUrl = match.groupValues[1].replace("\\/", "/")
                    if (rawUrl.contains(".m3u8") || rawUrl.contains(".mp4")) {
                        val streamUrl = resolveUrl(rawUrl)
                        if (sources.none { it.url == streamUrl }) {
                            val isHls = isHlsStream(streamUrl)
                            val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
                            val qual = when {
                                streamUrl.contains("1080P") || streamUrl.contains("1080p") -> "1080p"
                                streamUrl.contains("720P") || streamUrl.contains("720p") -> "720p"
                                streamUrl.contains("480P") || streamUrl.contains("480p") -> "480p"
                                streamUrl.contains("360P") || streamUrl.contains("360p") -> "360p"
                                streamUrl.contains("240P") || streamUrl.contains("240p") -> "240p"
                                else -> "auto"
                            }
                            val headers = resolveHeadersForStream(streamUrl, defaultHeaders)
                            sources.add(MediaSource(url = streamUrl, type = type, mimeType = if (isHls) "application/x-mpegURL" else "video/mp4", quality = qual, headersRequired = headers))
                        }
                    }
                }
            }

            // 2.6 FreeOnes / VideoJS unescaped sources array extractor
            if (sources.isEmpty()) {
                val vjsSrcRegex = Regex("""\{[^{}]*?"src"\s*:\s*"([^"]+\.(?:mp4|m3u8)[^"]*)"[^{}]*?\}""")
                for (match in vjsSrcRegex.findAll(html)) {
                    val rawUrl = match.groupValues[1].replace("\\/", "/")
                    val streamUrl = resolveUrl(rawUrl)
                    if (sources.none { it.url == streamUrl }) {
                        val qualMatch = Regex(""""(?:label|res)"\s*:\s*"?([0-9a-zA-Z]+)"?""").find(match.value)
                        val qual = qualMatch?.groupValues?.getOrNull(1) ?: "auto"
                        val isHls = isHlsStream(streamUrl)
                        val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
                        val headers = resolveHeadersForStream(streamUrl, defaultHeaders)
                        sources.add(MediaSource(url = streamUrl, type = type, mimeType = if (isHls) "application/x-mpegURL" else "video/mp4", quality = qual, headersRequired = headers))
                    }
                }
            }

            // 2.7 xHamster direct HLS stream regex
            if (sources.isEmpty()) {
                val xhHlsRegex = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""")
                val xhMatch = xhHlsRegex.find(html)
                if (xhMatch != null) {
                    val streamUrl = resolveUrl(xhMatch.value.replace("\\/", "/"))
                    if (sources.none { it.url == streamUrl }) {
                        val headers = resolveHeadersForStream(streamUrl, defaultHeaders)
                        sources.add(MediaSource(url = streamUrl, type = MediaSourceType.HLS, mimeType = "application/x-mpegURL", quality = "auto", headersRequired = headers))
                    }
                }
            }

            // 3. Xvideos / XNXX proprietary setVideoUrl extractor
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

            // 3.1 YouJizz dataEncodings / mp4Encodings JSON array extractor
            if (sources.isEmpty() && (config.id == "youjizz" || activeBaseUrl.contains("youjizz") || html.contains("dataEncodings"))) {
                val yjRegex = Regex("""["']filename["']\s*:\s*["'](\\?/[^"']+\.mp4[^"']*)["']""")
                val yjMatches = yjRegex.findAll(html)
                for (match in yjMatches) {
                    var raw = match.groupValues[1].replace("\\/", "/")
                    if (raw.startsWith("//")) raw = "https:$raw"
                    val qualMatch = Regex("""-(\d{3,4})-(?:h264|hevc)""").find(raw) ?: Regex("""(?i)[/_.-]?(1080|720|480|360|240)p""").find(raw)
                    val quality = qualMatch?.groupValues?.get(1)?.let { "${it}p" } ?: "360p"
                    sources.add(MediaSource(
                        url = raw,
                        type = MediaSourceType.PROGRESSIVE_MP4,
                        mimeType = "video/mp4",
                        quality = quality,
                        headersRequired = defaultHeaders
                    ))
                }
            }

            // 3.2 DrTuber & NuVid CDN poster/ID stream reconstructor
            if (sources.isEmpty() && (config.id in listOf("drtuber", "nuvid") || activeBaseUrl.contains("drtuber") || activeBaseUrl.contains("nuvid"))) {
                val cdnPosterRegex = Regex("""(?:poster|src)=["'](https?://[a-zA-Z0-9_\-\.]+(?:drtst|nvdst)\.com/media/videos/tmb/(\d+))/player/\d+\.jpg["']""")
                val cdnMatch = cdnPosterRegex.find(html)
                if (cdnMatch != null) {
                    val baseMedia = cdnMatch.groupValues[1]
                    val vidId = cdnMatch.groupValues[2]
                    val directUrl = "$baseMedia/$vidId.mp4"
                    sources.add(MediaSource(
                        url = directUrl,
                        type = MediaSourceType.PROGRESSIVE_MP4,
                        mimeType = "video/mp4",
                        quality = "720p",
                        headersRequired = defaultHeaders
                    ))
                }
            }

            // 3.3 Eporner direct download link or gvideo extractor
            if (sources.isEmpty() && (config.id == "eporner" || activeBaseUrl.contains("eporner"))) {
                val gvideoMatch = Regex("""(https?://gvideo\.eporner\.com/[^"'\s\`\)]+\.mp4[^"'\s\`\)]*)""").find(html)
                if (gvideoMatch != null) {
                    sources.add(MediaSource(
                        url = gvideoMatch.groupValues[1],
                        type = MediaSourceType.PROGRESSIVE_MP4,
                        mimeType = "video/mp4",
                        quality = "720p",
                        headersRequired = defaultHeaders
                    ))
                } else {
                    val dloadMatch = Regex("""href=["'](/dload/[^"']+\.mp4)""").find(html)
                    if (dloadMatch != null) {
                        val resolved = resolveUrl(dloadMatch.groupValues[1])
                        sources.add(MediaSource(
                            url = resolved,
                            type = MediaSourceType.PROGRESSIVE_MP4,
                            mimeType = "video/mp4",
                            quality = "480p",
                            headersRequired = defaultHeaders
                        ))
                    }
                }
            }

            // 4. Check iframe embeds and host links (e.g. clean-tube-player, /e/ embeds, tube279, luluvdo, streamtape)
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
                        || lowerUrl.contains("exoclick") || lowerUrl.contains("juicyads") || lowerUrl.contains("adtng.com")
                        || lowerUrl.contains("trafficfactory") || lowerUrl.contains("tsyndicate") || lowerUrl.contains("etadirect")
                        || lowerUrl.contains("traffichaus") || lowerUrl.contains("stripchat") || lowerUrl.contains("sex-games"))
                }.sortedByDescending { u ->
                    val lu = u.lowercase()
                    when {
                        HostResolverEngine.canHandle(u) -> 200
                        lu.contains("luluvdo") || lu.contains("lulustream") || lu.contains("luluvid") -> 100
                        lu.contains("/e/") || lu.contains("tube279") || lu.contains("streamtape") || lu.contains("cdn1") -> 90
                        lu.contains("player") || lu.contains("embed") -> 50
                        else -> 10
                    }
                }

                for (urlCandidate in filteredCandidates) {
                    // Build list of URLs to try for this candidate (e.g. cdn1.site / luluvdo -> lulustream / luluvid mirrors)
                    val urlsToTry = mutableListOf(urlCandidate)
                    if (urlCandidate.contains("cdn1.site/e/") || urlCandidate.contains("luluvid.com/e/") || urlCandidate.contains("luluvdo.com/e/") || urlCandidate.contains("lulustream.com/e/") || urlCandidate.contains("playmogo.com/e/")) {
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
                    } else if (urlCandidate.contains("luluvdo.com/d/") || urlCandidate.contains("lulustream.com/d/") || urlCandidate.contains("luluvid.com/d/") || urlCandidate.contains("playmogo.com/d/")) {
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
                                "Referer" to if (iframeSrc.contains("/player/") || iframeSrc.contains("player/?")) iframeSrc else "https://$iframeHost/",
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
                                            val streamHeaders = resolveHeadersForStream(src, embedHeaders)
                                            sources.add(MediaSource(url = resolveUrl(src), type = type, mimeType = mime, headersRequired = streamHeaders))
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
                                            val streamHeaders = resolveHeadersForStream(streamUrl, embedHeaders)
                                            sources.add(MediaSource(url = resolveUrl(streamUrl), type = type, mimeType = mime, headersRequired = streamHeaders))
                                        }
                                    }
                                    if (sources.isNotEmpty()) break
                                }
                            } catch (e: Exception) {
                                StreamHubLogger.w("HtmlSelectorAdapter", "Could not parse query param from iframe: ${e.message}")
                            }
                        }

                        // Case B: Fetch iframe page HTML and parse nested video elements or stream URLs
                        if (sources.isEmpty() && (HostResolverEngine.canHandle(iframeSrc) || iframeSrc.contains("player") || iframeSrc.contains("embed") || iframeSrc.contains("video") || iframeSrc.contains("/e/") || iframeSrc.contains("tube279") || iframeSrc.contains("lulu") || iframeSrc.contains("streamtape") || iframeSrc.contains("cdn1"))) {
                            try {
                                val iframeHtml = try {
                                    NetworkClient.fetchString(iframeSrc, headers = mapOf("Referer" to fullUrl))
                                } catch (e: Exception) {
                                    if (!iframeHost.isNullOrBlank()) {
                                        NetworkClient.fetchString(iframeSrc, headers = mapOf("Referer" to "https://$iframeHost/"))
                                    } else throw e
                                }
                                if (isCloudflareChallenge(iframeHtml)) {
                                    throw CloudflareChallengeException(
                                        url = iframeSrc,
                                        host = iframeHost ?: URI(iframeSrc).host ?: activeBaseUrl,
                                        statusCode = 403,
                                        message = "Cloudflare security challenge detected on player ($iframeSrc)"
                                    )
                                }
                                val iframeSources = parseIframePlayerMedia(iframeHtml, iframeSrc, fullUrl, embedHeaders)
                                if (iframeSources.isNotEmpty()) {
                                    sources.addAll(iframeSources)
                                    break
                                }
                            } catch (e: CloudflareChallengeException) {
                                throw e
                            } catch (e: Exception) {
                                StreamHubLogger.w("HtmlSelectorAdapter", "Could not fetch nested player iframe ($iframeSrc): ${e.message}")
                            }
                        }
                    }
                    if (sources.isNotEmpty()) break
                }
            }

            // 5. Check for direct download or tracking link button (e.g. AagMaal tube279)
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

            // 6. Direct download link if it's explicitly an mp4/m3u8 video (not a screenshot/image)
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
                            val streamUrl = resolveUrl(href)
                            val isHls = isHlsStream(streamUrl)
                            val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
                            val headers = resolveHeadersForStream(streamUrl, defaultHeaders)
                            sources.add(MediaSource(url = streamUrl, type = type, mimeType = if (isHls) "application/x-mpegURL" else "video/mp4", headersRequired = headers))
                            break
                        }
                    }
                }
            }

            // 6.5 Dynamic AJAX JWPlayer resolver for pornhouse.me and pornhd4k.net
            if (sources.isEmpty()) {
                val uuidEl = doc.selectFirst("input#uuid, a[episode-id]")
                val uuid = uuidEl?.attr("value")?.ifBlank { null }
                    ?: uuidEl?.attr("episode-id")?.ifBlank { null }
                    ?: Regex("""(?:uuid|episode-id)["']?\s*[:=]\s*["']([A-Za-z0-9_-]+)["']""").find(html)?.groupValues?.get(1)
                if (!uuid.isNullOrBlank()) {
                    try {
                        val resolvedSources = resolvePornhousePornhd4kMedia(uuid, fullUrl)
                        if (resolvedSources.isNotEmpty()) {
                            sources.addAll(resolvedSources)
                        }
                    } catch (e: Exception) {
                        StreamHubLogger.w("HtmlSelectorAdapter", "Failed to resolve pornhouse/pornhd4k media: ${e.message}")
                    }
                }
            }

            // 6.6 Dynamic AJAX stream resolver for netfapx.com
            if (sources.isEmpty() && (config.id == "netfapx" || activeBaseUrl.contains("netfapx.com") || fullUrl.contains("netfapx.com"))) {
                val postId = Regex("""["']post_id["']\s*:\s*["']?(\d+)""").find(html)?.groupValues?.get(1)
                    ?: Regex("""["']postId["']\s*:\s*["']?(\d+)""").find(html)?.groupValues?.get(1)
                    ?: Regex("""data-ulike-id=["'](\d+)["']""").find(html)?.groupValues?.get(1)
                    ?: Regex("""id=["']post-(\d+)["']""").find(html)?.groupValues?.get(1)
                    ?: Regex("""postid-(\d+)""").find(html)?.groupValues?.get(1)
                if (!postId.isNullOrBlank()) {
                    try {
                        val netfapxSources = resolveNetfapxMedia(postId, fullUrl)
                        if (netfapxSources.isNotEmpty()) {
                            sources.addAll(netfapxSources)
                        }
                    } catch (e: Exception) {
                        StreamHubLogger.w("HtmlSelectorAdapter", "Failed to resolve netfapx media: ${e.message}")
                    }
                }
            }

            // 6.7 Dynamic stream resolver for AVS network (txxx.com, upornia.com, hdzog.com)
            if (sources.isEmpty() && (config.id in listOf("txxx", "upornia", "hdzog") || activeBaseUrl.contains("txxx.com") || activeBaseUrl.contains("upornia.com") || activeBaseUrl.contains("hdzog.com") || fullUrl.contains("txxx.com") || fullUrl.contains("upornia.com") || fullUrl.contains("hdzog.com"))) {
                val vidId = Regex("""/(?:videos?|video)/(\d+)""").find(fullUrl)?.groupValues?.get(1)
                    ?: Regex("""["']video_id["']\s*:\s*["']?(\d+)""").find(html)?.groupValues?.get(1)
                    ?: Regex("""video_id=(\d+)""").find(fullUrl)?.groupValues?.get(1)
                if (!vidId.isNullOrBlank()) {
                    try {
                        val avsSources = resolveAvsMedia(vidId, fullUrl)
                        if (avsSources.isNotEmpty()) {
                            sources.addAll(avsSources)
                        }
                    } catch (e: Exception) {
                        StreamHubLogger.w("HtmlSelectorAdapter", "Failed to resolve AVS media: ${e.message}")
                    }
                }
                // Fallback to preview trailer MP4 (pv) if full stream not resolved
                if (sources.isEmpty()) {
                    val pvMatch = Regex("""["']pv["']\s*:\s*["']([^"']+\.mp4)""").find(html)
                    if (pvMatch != null) {
                        val rawPv = pvMatch.groupValues[1]
                        val pvUrl = if (rawPv.startsWith("http")) rawPv else "https://$rawPv"
                        sources.add(
                            MediaSource(
                                url = pvUrl,
                                type = MediaSourceType.PROGRESSIVE_MP4,
                                mimeType = "video/mp4",
                                quality = "Preview",
                                headersRequired = mapOf("Referer" to "$activeBaseUrl/", "User-Agent" to NetworkClient.DEFAULT_USER_AGENT)
                            )
                        )
                    }
                }
            }

            // 7. Fallback: Direct regex scan in page HTML for public progressive MP4 / HLS streams
            if (sources.isEmpty()) {
                val mp4Regex = Regex("""https?://[^\s"'<>]+\.(?:mp4|m3u8)[^\s"'<>]*""")
                val match = mp4Regex.find(html)
                if (match != null && !match.value.contains("wp-content") && !match.value.contains("preview")) {
                    val streamUrl = match.value.replace(" ", "%20")
                    val isHls = isHlsStream(streamUrl)
                    val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
                    val headers = resolveHeadersForStream(streamUrl, defaultHeaders)
                    sources.add(MediaSource(url = streamUrl, type = type, mimeType = if (isHls) "application/x-mpegURL" else "video/mp4", headersRequired = headers))
                }
            }

            if (sources.isEmpty()) {
                Result.failure(StreamHubError.PlaybackError(404, "No authorized playable media stream found for $fullUrl"))
            } else {
                val deduplicated = sources.groupBy { it.url }.map { (_, group) ->
                    group.firstOrNull { !it.quality.equals("auto", ignoreCase = true) } ?: group.first()
                }
                val sorted = deduplicated.sortedByDescending { s ->
                    val q = s.quality.lowercase()
                    when {
                        q.contains("1080") -> 1080
                        q.contains("720") -> 720
                        q.contains("480") -> 480
                        q.contains("360") -> 360
                        q.contains("240") -> 240
                        q.contains("hq") -> 700
                        q.contains("lq") -> 300
                        else -> 0
                    }
                }
                Result.success(sorted)
            }
        } catch (e: CloudflareChallengeException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(StreamHubError.PlaybackError(500, "Failed to resolve media: ${e.message}", e))
        }
    }

    internal fun parseIframePlayerMedia(
        iframeHtml: String,
        iframeSrc: String,
        fullUrl: String,
        embedHeaders: Map<String, String>
    ): List<MediaSource> {
        val sources = mutableListOf<MediaSource>()
        val hostPlugin = HostResolverEngine.findPluginForUrl(iframeSrc)
        if (hostPlugin != null) {
            val hostRes = hostPlugin.resolveFromHtml(iframeHtml, iframeSrc, fullUrl)
                ?: runBlocking { hostPlugin.resolve(iframeSrc, fullUrl) }
            if (hostRes.isSuccess) {
                sources.add(hostRes.getOrThrow())
                return sources
            }
        }

        val unpacked = if (iframeHtml.contains("eval(function(p,a,c,k,e")) unpackDeanEdwards(iframeHtml) else iframeHtml
        val iframeDoc = Jsoup.parse(unpacked, iframeSrc)
        val innerVideos = iframeDoc.select("video source[src], video[src], source[type='video/mp4']")
        for (v in innerVideos) {
            val s = v.attr("src").ifEmpty { v.attr("data-src") }
            if (s.isNotBlank()) {
                val cleanS = s.replace(" ", "%20")
                val isHls = cleanS.contains(".m3u8")
                val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
                sources.add(MediaSource(url = resolveUrl(cleanS, iframeSrc), type = type, mimeType = if (isHls) "application/x-mpegURL" else "video/mp4", headersRequired = embedHeaders))
            }
        }
        if (sources.isEmpty()) {
            val jwRegex = Regex("""(?:sources|file|src):\s*(?:\[\s*\{\s*(?:file|src)\s*:\s*["']([^"']+)["']|["']([^"']+\.(?:mp4|m3u8)[^"']*)["'])""")
            val jwMatch = jwRegex.find(unpacked)
            if (jwMatch != null) {
                val u = (jwMatch.groupValues[1].ifEmpty { jwMatch.groupValues[2] }).replace(" ", "%20")
                val isHls = u.contains(".m3u8")
                val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
                sources.add(MediaSource(url = resolveUrl(u, iframeSrc), type = type, mimeType = if (isHls) "application/x-mpegURL" else "video/mp4", headersRequired = embedHeaders))
            }
        }
        if (sources.isEmpty()) {
            val playerSrcRegex = Regex("""(?:\.src\(\s*\{[^}]*?src:\s*|['"]?src['"]?\s*:\s*)["']([^"']+\.(?:mp4|m3u8)[^"']*)["']""")
            val playerMatch = playerSrcRegex.find(unpacked)
            if (playerMatch != null) {
                val u = playerMatch.groupValues[1].replace(" ", "%20")
                val isHls = u.contains(".m3u8")
                val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
                sources.add(MediaSource(url = resolveUrl(u, iframeSrc), type = type, mimeType = if (isHls) "application/x-mpegURL" else "video/mp4", headersRequired = embedHeaders))
            }
        }
        if (sources.isEmpty()) {
            val mp4Regex = Regex("""https?://[^\s"'<>]+\.(?:mp4|m3u8)[^\s"'<>]*""")
            val match = mp4Regex.find(unpacked)
            if (match != null) {
                val u = match.value.replace(" ", "%20")
                val isHls = u.contains(".m3u8")
                val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
                sources.add(MediaSource(url = resolveUrl(u, iframeSrc), type = type, mimeType = if (isHls) "application/x-mpegURL" else "video/mp4", headersRequired = embedHeaders))
            }
        }
        return sources
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

    fun parseCategoriesHtml(html: String): List<Category> {
        val trimmed = html.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                val jsonEl = Json.parseToJsonElement(trimmed)
                if (jsonEl is kotlinx.serialization.json.JsonObject) {
                    val catsArray = jsonEl["categories"]?.let { if (it is kotlinx.serialization.json.JsonArray) it else null }
                    if (catsArray != null) {
                        return catsArray.mapNotNull { catItem ->
                            val obj = catItem as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                            val dir = obj["dir"]?.jsonPrimitive?.content ?: ""
                            val title = obj["title"]?.jsonPrimitive?.content ?: dir
                            val id = obj["category_id"]?.jsonPrimitive?.content ?: dir
                            if (dir.isNotBlank() && title.isNotBlank()) {
                                val catUrl = resolveUrl("/api/json/videos2/86400/str/latest-updates/60/categories.$dir.1.all...json")
                                Category(id = id, name = title, url = catUrl)
                            } else null
                        }.distinctBy { it.id }
                    }

                    val genresArray = jsonEl["genres"]?.let { if (it is kotlinx.serialization.json.JsonArray) it else null }
                    if (genresArray != null) {
                        return genresArray.mapNotNull { gItem ->
                            val obj = gItem as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                            val url = when (config.id) {
                                "movienerds" -> "https://api.themoviedb.org/3/discover/movie?api_key=43dcff37851866de14d8acdce668a509&with_genres=$id"
                                "cineapse" -> "https://cineapse.net/tmdb/discover/movie?with_genres=$id"
                                else -> resolveUrl("/genre/$id")
                            }
                            Category(id = id, name = name, url = url)
                        }.distinctBy { it.id }
                    }

                    val groupsArray = jsonEl["groups"]?.let { if (it is kotlinx.serialization.json.JsonArray) it else null }
                    if (groupsArray != null) {
                        val cats = mutableListOf<Category>()
                        for (gItem in groupsArray) {
                            val gObj = gItem as? kotlinx.serialization.json.JsonObject ?: continue
                            val groupTitle = gObj["title"]?.jsonPrimitive?.content ?: gObj["name"]?.jsonPrimitive?.content ?: ""
                            val itemsArray = gObj["items"]?.let { if (it is kotlinx.serialization.json.JsonArray) it else null } ?: continue
                            for (item in itemsArray) {
                                val iObj = item as? kotlinx.serialization.json.JsonObject ?: continue
                                val label = iObj["label"]?.jsonPrimitive?.content
                                    ?: iObj["name"]?.jsonPrimitive?.content
                                    ?: iObj["title"]?.jsonPrimitive?.content
                                    ?: continue
                                val value = iObj["value"]?.jsonPrimitive?.content
                                    ?: iObj["id"]?.jsonPrimitive?.content
                                    ?: continue
                                val kind = iObj["kind"]?.jsonPrimitive?.content ?: "genre"
                                val displayName = if (groupTitle.isNotBlank() && !groupTitle.equals("Genre", ignoreCase = true) && !groupTitle.equals("Genres", ignoreCase = true)) {
                                    "$groupTitle: $label"
                                } else {
                                    label
                                }
                                val catUrl = if (kind == "provider") {
                                    "https://cineapse.net/tmdb/discover/movie?with_watch_providers=$value"
                                } else {
                                    "https://cineapse.net/tmdb/discover/movie?with_genres=$value"
                                }
                                cats.add(Category(id = "${kind}_$value", name = displayName, url = catUrl))
                            }
                        }
                        if (cats.isNotEmpty()) return cats.distinctBy { it.id }
                    }
                }
            } catch (e: Exception) {
                StreamHubLogger.w("HtmlSelectorAdapter", "Failed to parse JSON categories: ${e.message}")
            }
        }
        val doc = Jsoup.parse(html, activeBaseUrl)
        val categoryElements = doc.select(
            "#menu a, ul.top-menu a, a[href*='/category/'], a[href*='/categories/'], a[href*='/channels/'], a[href*='/models/'], a[href*='/pornstars/'], a[href*='/sites/'], a[href*='/studios/'], a[href*='/studio/'], a[href*='/tags/'], a[href*='/tag/'], a[href*='/genre/'], a[href*='/genres/'], a[href*='/ott/'], a[href*='/series/'], a[href*='/paysite/'], a[href*='/girls'], a[href*='/actress/'], a[href*='/actresses/'], a[href*='/view/'], a[href*='?cat='], a[href*='search/?s='], a[href*='search?s='], .category-list a, .categories a, div.thumb-cat p.title a, .thumb-block.thumb-cat p.title a, a.taxonomy-item-card, div.cat-thumb a, div.alphabet a, .alphabet a, a.btn[href*='pornstars.tube']"
        )
        return categoryElements.mapNotNull { el ->
            // Filter out pagination container items and page number links
            val isPagination = el.parents().any { p ->
                p.hasClass("pagination") || p.hasClass("nav-links") || p.hasClass("wp-pagenavi") ||
                p.hasClass("pages") || p.hasClass("page-numbers") || p.hasClass("button-nav") ||
                p.hasClass("pagination-list") || (p.tagName().equals("ul", ignoreCase = true) && p.parent()?.hasClass("pagination") == true)
            } || el.hasClass("page-numbers") || el.hasClass("inactive") || el.hasClass("current") || el.hasClass("page-link")
            if (isPagination) return@mapNotNull null

            var rawName = el.select(".taxonomy-name, .cat-name, span.title, p.title").firstOrNull()?.text()?.trim()
                ?.ifEmpty { null }
                ?: el.text().substringBefore("\n").trim()
            if (rawName.isBlank()) {
                val img = el.select("img").firstOrNull()
                rawName = img?.attr("alt")?.trim()?.ifEmpty { null }
                    ?: img?.attr("src")?.substringAfterLast('/')?.substringBeforeLast('.')?.replace("-", " ")?.replace("_", " ")?.replace("Porn Videos", "", ignoreCase = true)?.trim()
                    ?: ""
            }
            val name = rawName.replace(Regex("""\s+"""), " ")
            val href = el.attr("href").trim()
            val lowerName = name.lowercase()
            val lowerHref = href.lowercase()

            if (name.isNotBlank() && href.isNotBlank()) {
                val isAlphabetLetter = (name.length == 1 && name[0].isLetter()) ||
                        (name.equals("All", ignoreCase = true) && (href.contains("pornstars.tube") || activeBaseUrl.contains("pornstars.tube") || href.endsWith(".tube/")))

                // Reject pure digits, pagination labels, self index links, photos/gallery, and non-content
                if (name.matches(Regex("""^\d+$""")) || (!isAlphabetLetter && name.length <= 1) ||
                    (!isAlphabetLetter && lowerName in listOf("next", "prev", "previous", "first", "last", "end", "»", "«", "next »", "« prev", "more")) ||
                    lowerName == "all categories" || lowerHref.trimEnd('/').endsWith("/categories") ||
                    lowerHref.contains("/page/") || lowerHref.contains("/photos/") || lowerHref.contains("/gallery/") ||
                    lowerHref.endsWith("/ott/") || lowerHref.endsWith("/series/") || (!isAlphabetLetter && isNonContentLink(name, href))) {
                    return@mapNotNull null
                }
                val pathSegments = href.substringBefore('?').trimEnd('/').split("/").filter { it.isNotBlank() }
                val id = if (href.contains("?s=")) {
                    href.substringAfter("?s=").trimEnd('/')
                } else if (isAlphabetLetter) {
                    name.uppercase()
                } else if (pathSegments.size >= 2 && (pathSegments.last().contains("-new-") || pathSegments.last().contains("free-brazz") || pathSegments.last().length > 20)) {
                    pathSegments[pathSegments.size - 2]
                } else {
                    href.trimEnd('/').substringAfterLast('/')
                }
                val resolvedUrl = if (isAlphabetLetter && name.equals("All", ignoreCase = true)) {
                    resolveUrl("/models/")
                } else {
                    resolveUrl(href)
                }
                Category(id = id, name = name, url = resolvedUrl)
            } else null
        }.distinctBy { "${it.name.lowercase()}_${it.id}" }
    }

    fun parseListingHtml(html: String, page: Int): Result<FeedPage> {
        if (isCloudflareChallenge(html)) {
            return Result.failure(CloudflareChallengeException(
                url = activeBaseUrl,
                host = try { URI(activeBaseUrl).host ?: activeBaseUrl } catch (e: Exception) { activeBaseUrl },
                statusCode = 403,
                message = "Cloudflare security challenge detected on $activeBaseUrl"
            ))
        }
        val trimmed = html.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                val jsonEl = Json.parseToJsonElement(trimmed)
                if (jsonEl is kotlinx.serialization.json.JsonObject) {
                    val vidsArray = jsonEl["videos"]?.let { if (it is kotlinx.serialization.json.JsonArray) it else null }
                    if (vidsArray != null) {
                        val items = vidsArray.mapNotNull { vidItem ->
                            val obj = vidItem as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                            val id = obj["video_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                            val title = obj["title"]?.jsonPrimitive?.content ?: "Video $id"
                            val dir = obj["dir"]?.jsonPrimitive?.content ?: ""
                            val scr = obj["scr"]?.jsonPrimitive?.content
                                ?: obj["thumb"]?.jsonPrimitive?.content
                                ?: ""
                            val duration = obj["duration"]?.jsonPrimitive?.content
                            val durationSec = parseDurationToSeconds(duration)
                            val pv = obj["pv"]?.jsonPrimitive?.content
                            val previewUrl = if (!pv.isNullOrBlank()) {
                                if (pv.startsWith("http")) pv else "https://$pv"
                            } else null
                            val detailUrl = resolveUrl("/videos/$id/$dir/")
                            VideoItem(
                                id = id,
                                providerId = config.id,
                                title = title,
                                thumbnailUrl = resolveUrl(scr),
                                durationSeconds = durationSec,
                                detailUrl = detailUrl,
                                metadata = if (previewUrl != null) mapOf("previewUrl" to previewUrl) else emptyMap()
                            )
                        }
                        return Result.success(FeedPage(items = items, page = page, hasNextPage = items.size >= 60))
                    }

                    val resultsArray = jsonEl["results"]?.let { if (it is kotlinx.serialization.json.JsonArray) it else null }
                    if (resultsArray != null) {
                        val items = resultsArray.mapNotNull { itemEl ->
                            val obj = itemEl as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                            val title = obj["title"]?.jsonPrimitive?.content 
                                ?: obj["name"]?.jsonPrimitive?.content 
                                ?: "Title $id"
                            val overview = obj["overview"]?.jsonPrimitive?.content ?: ""
                            val posterPath = obj["poster_path"]?.jsonPrimitive?.content
                            val backdropPath = obj["backdrop_path"]?.jsonPrimitive?.content
                            val thumb = when {
                                !posterPath.isNullOrBlank() -> "https://image.tmdb.org/t/p/w500$posterPath"
                                !backdropPath.isNullOrBlank() -> "https://image.tmdb.org/t/p/w500$backdropPath"
                                else -> ""
                            }
                            val mediaType = obj["media_type"]?.jsonPrimitive?.content ?: if (obj.containsKey("title")) "movie" else "tv"
                            val voteAvg = obj["vote_average"]?.jsonPrimitive?.content ?: ""
                            val releaseDate = obj["release_date"]?.jsonPrimitive?.content ?: obj["first_air_date"]?.jsonPrimitive?.content ?: ""

                            val detailUrl = when (config.id) {
                                "movienerds" -> "https://api.themoviedb.org/3/$mediaType/$id?api_key=43dcff37851866de14d8acdce668a509&append_to_response=credits,recommendations,similar,external_ids"
                                "cineapse" -> "https://cineapse.net/tmdb/$mediaType/$id?append_to_response=external_ids"
                                else -> resolveUrl("/$mediaType/$id")
                            }

                            val meta = mutableMapOf<String, String>()
                            meta["mediaType"] = mediaType
                            meta["tmdbId"] = id
                            if (voteAvg.isNotBlank()) meta["rating"] = voteAvg
                            if (releaseDate.isNotBlank()) meta["releaseDate"] = releaseDate

                            VideoItem(
                                id = id,
                                providerId = config.id,
                                title = title,
                                description = overview,
                                thumbnailUrl = thumb,
                                detailUrl = detailUrl,
                                playbackAvailability = PlaybackAvailability.AVAILABLE,
                                downloadAvailability = DownloadAvailability.AUTHORIZED,
                                metadata = meta
                            )
                        }
                        val totalPages = jsonEl["total_pages"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
                        return Result.success(FeedPage(items = items, page = page, hasNextPage = page < totalPages))
                    }

                    val episodesArray = jsonEl["episodes"]?.let { if (it is kotlinx.serialization.json.JsonArray) it else null }
                    if (episodesArray != null) {
                        val items = episodesArray.mapNotNull { epEl ->
                            val obj = epEl as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                            val epNum = obj["episode_number"]?.jsonPrimitive?.content ?: "1"
                            val sNum = obj["season_number"]?.jsonPrimitive?.content ?: "1"
                            val epName = obj["name"]?.jsonPrimitive?.content ?: "Episode $epNum"
                            val stillPath = obj["still_path"]?.jsonPrimitive?.content
                            val thumb = if (!stillPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w500$stillPath" else ""
                            val overview = obj["overview"]?.jsonPrimitive?.content ?: ""
                            val showId = obj["show_id"]?.jsonPrimitive?.content ?: ""

                            val detailUrl = when (config.id) {
                                "movienerds" -> "https://api.movienerds.online/api/stream?tmdbId=$showId&type=tv&season=$sNum&episode=$epNum"
                                "cineapse" -> "https://cineapse.net/stream/tv/$showId/$sNum/$epNum"
                                else -> resolveUrl("/tv/$showId/$sNum/$epNum")
                            }
                            VideoItem(
                                id = id,
                                providerId = config.id,
                                title = "S${sNum}E${epNum}: $epName",
                                description = overview,
                                thumbnailUrl = thumb,
                                detailUrl = detailUrl,
                                playbackAvailability = PlaybackAvailability.AVAILABLE,
                                downloadAvailability = DownloadAvailability.AUTHORIZED,
                                metadata = mapOf("mediaType" to "tv", "season" to sNum, "episode" to epNum, "tmdbId" to showId)
                            )
                        }
                        return Result.success(FeedPage(items = items, page = page, hasNextPage = false))
                    }
                }
            } catch (e: Exception) {
                StreamHubLogger.w("HtmlSelectorAdapter", "Failed to parse JSON listing: ${e.message}")
            }
        }
        val doc = Jsoup.parse(html, activeBaseUrl)
        val itemSelector = selectors?.item ?: "article, div.post, div.video-item, div.item"
        val elements = doc.select(itemSelector)

        val isFsiBlog = config.id.contains("fsiblog", ignoreCase = true)

        val items = elements.mapNotNull { el ->
            // Carousel and slider filter: omit repeating slider slides and clones
            val isInCarousel = el.hasClass("slide") || el.hasClass("bx-clone") ||
                    el.hasClass("swiper-slide") || el.hasClass("slick-slide") || el.hasClass("slick-cloned") ||
                    el.parents().any { p ->
                        p.hasClass("bx-wrapper") || p.hasClass("featured-carousel") ||
                        p.hasClass("carousel") || p.hasClass("featured-slider") ||
                        p.hasClass("owl-carousel") || p.hasClass("swiper-wrapper") ||
                        p.hasClass("swiper-container") || p.hasClass("pfb-slider-group") ||
                        p.hasClass("slider-cat") || p.hasClass("items-categories") ||
                        p.hasClass("slick-slider") || p.hasClass("navigation") ||
                        p.hasClass("mob-nav") || p.hasClass("fluid_slider")
                    }
            if (isInCarousel) {
                return@mapNotNull null
            }

            // Exclude pagination container elements if matched by generic selector
            val isPagination = el.hasClass("pagination") || el.hasClass("button-nav") || el.hasClass("page-numbers") ||
                    el.hasClass("page") || el.hasClass("jump") || el.hasClass("last") || el.hasClass("next") || el.hasClass("prev") ||
                    el.parents().any { p ->
                        p.hasClass("pagination") || p.hasClass("button-nav") || p.hasClass("page-numbers") || p.hasClass("nav-links") ||
                        p.hasClass("vp-pagi-bar") || p.hasClass("vp-home-bar") || p.hasClass("vp-ann-bar")
                    }
            if (isPagination) {
                return@mapNotNull null
            }


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

            val isCat = isCategoryUrl(href)
            val durText = selectors?.duration?.let { sel ->
                el.select(sel).firstOrNull()?.text()?.trim()?.ifEmpty { null }
            }
            val durSecs = parseDurationToSeconds(durText)

            if (href.isNotBlank() && cleanedTitle.isNotBlank() && !isNonContentLink(cleanedTitle, href)) {
                VideoItem(
                    id = href.hashCode().toString(),
                    providerId = config.id,
                    title = cleanedTitle,
                    thumbnailUrl = resolveUrl(thumb),
                    detailUrl = resolveUrl(href),
                    durationSeconds = durSecs,
                    isCategory = isCat
                )
            } else null

        }.distinctBy { it.detailUrl }

        val hasNextPage = doc.select("a.next, .pagination .next, a[rel='next'], .vp-pagi-bar a.next, .vp-pagi-bar a:contains(Next)").isNotEmpty() || items.size >= 10

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

        // 0. If imgEl or el is a meta tag (e.g. meta[property='og:image']), extract content attribute
        imgEl?.let {
            if (it.tagName().equals("meta", ignoreCase = true)) {
                val content = it.attr("content")
                if (content.isNotBlank()) candidates.add(content)
            }
        }
        if (el.tagName().equals("meta", ignoreCase = true)) {
            val content = el.attr("content")
            if (content.isNotBlank()) candidates.add(content)
        }

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
            candidates.add(img.attr("data-mzl"))
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

    private fun parseDurationToSeconds(durationStr: String?): Long? {
        if (durationStr.isNullOrBlank()) return null
        val parts = durationStr.trim().split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> null
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

    private fun isHlsStream(streamUrl: String): Boolean {
        val lower = streamUrl.lowercase()
        return lower.contains(".m3u8") || lower.contains("/hls/") ||
                activeBaseUrl.contains("hello.porn") || activeBaseUrl.contains("max.porn") ||
                activeBaseUrl.contains("ok.porn") || activeBaseUrl.contains("ok.xxx") ||
                activeBaseUrl.contains("perfectgirls.xxx") || activeBaseUrl.contains("pornhat.com") ||
                lower.contains("max.porn") || lower.contains("ok.porn") ||
                lower.contains("ok.xxx") || lower.contains("perfectgirls.xxx") ||
                lower.contains("pornhat.com") || lower.contains("privatehost.com")
    }

    private fun resolveHeadersForStream(streamUrl: String, baseHeaders: Map<String, String>): Map<String, String> {
        val lower = streamUrl.lowercase()
        val targetReferer = when {
            lower.contains("twimg.com") -> null
            lower.contains("pornhouse.me") || lower.contains("cdn.pornhouse.me") -> "https://pornhouse.me/"
            lower.contains("pornhd4k.net") || lower.contains("cdnamz.me") -> "https://pornhd4k.net/"
            lower.contains("pornmz.com") -> "https://pornmz.com/"
            lower.contains("pornstars.tube") -> "https://pornstars.tube/"
            lower.contains("sxyprn.com") || lower.contains("trafficdeposit.com") || lower.contains("bxcdn.net") || lower.contains("bkcdn.net") -> "https://sxyprn.com/"
            lower.contains("tube279.com") || lower.contains("siesta583") -> "https://tube279.com/"
            lower.contains("tnmr.org") || lower.contains("lulucdn") || lower.contains("lulustream") || lower.contains("luluvdo") -> "https://luluvdo.com/"
            lower.contains("tpead.net") || lower.contains("streamtape.com") || lower.contains("tapecontent.net") -> "https://streamtape.com/"
            lower.contains("xhpingcdn") || lower.contains("xhcdn") || lower.contains("xhamster") -> "https://xhamster.desi/"
            lower.contains("max.porn") || (activeBaseUrl.contains("max.porn") && lower.contains("privatehost.com")) -> "https://max.porn/"
            lower.contains("ok.porn") || (activeBaseUrl.contains("ok.porn") && lower.contains("privatehost.com")) -> "https://ok.porn/"
            lower.contains("ok.xxx") || (activeBaseUrl.contains("ok.xxx") && lower.contains("privatehost.com")) -> "https://ok.xxx/"
            lower.contains("perfectgirls.xxx") || (activeBaseUrl.contains("perfectgirls.xxx") && lower.contains("privatehost.com")) -> "https://www.perfectgirls.xxx/"
            lower.contains("pornhat.com") || (activeBaseUrl.contains("pornhat.com") && lower.contains("privatehost.com")) -> "https://www.pornhat.com/"
            lower.contains("hello.porn") || lower.contains("privatehost.com") -> "https://hello.porn/"
            lower.contains("fpo.xxx") -> "https://www.fpo.xxx/"
            lower.contains("bigcdn.cc") || lower.contains("mydaddy.cc") || lower.contains("hqporner") -> "https://hqporner.com/"
            lower.contains("netfapx.com") || lower.contains("videos.netfapx.com") -> "https://netfapx.com/"
            lower.contains("porn4days.pw") || lower.contains("iceyfile.net") -> "https://porn4days.pw/"
            lower.contains("kamababa") -> "https://www.mykamababa.com/"
            lower.contains("chiggywiggy") -> "https://chiggywiggy.com/"
            lower.contains("definebabe.com") -> "https://www.definebabe.com/"
            lower.contains("3movs.com") -> "https://www.3movs.com/"
            lower.contains("txxx.com") || lower.contains("txxx.tube") -> "https://txxx.com/"
            lower.contains("upornia.com") -> "https://upornia.com/"
            lower.contains("hdzog.com") -> "https://hdzog.com/"
            else -> baseHeaders["Referer"] ?: if (activeBaseUrl.isNotBlank()) "$activeBaseUrl/" else null
        }
        return if (targetReferer != null) {
            val uri = try { URI(targetReferer) } catch (e: Exception) { null }
            val origin = if (uri != null) "${uri.scheme}://${uri.host}" else targetReferer.removeSuffix("/")
            baseHeaders + mapOf("Referer" to targetReferer, "Origin" to origin)
        } else if (lower.contains("twimg.com")) {
            baseHeaders.filterKeys { it != "Referer" && it != "Origin" }
        } else {
            baseHeaders
        }
    }

    private fun isCloudflareChallenge(html: String): Boolean {
        if (html.isBlank()) return false
        val lower = html.lowercase()
        return lower.contains("<title>just a moment...</title>") ||
               (lower.contains("just a moment...") && (lower.contains("cloudflare") || lower.contains("turnstile") || lower.contains("challenge"))) ||
               lower.contains("attention required! | cloudflare") ||
               lower.contains("cf-browser-verification") ||
               lower.contains("id=\"challenge-stage\"") ||
               lower.contains("id=\"challenge-form\"") ||
               lower.contains("id=\"challenge-running\"") ||
               (lower.contains("challenge-platform") && (lower.contains("/turnstile/") || lower.contains("orchestrate/chl_api")))
    }

    internal fun resolveUrl(path: String, baseUri: String = activeBaseUrl): String {
        return try {
            val trimmed = path.trim()
            if (trimmed.startsWith("//")) {
                "https:$trimmed"
            } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                val normalizedBase = if (baseUri.endsWith("/")) baseUri else "$baseUri/"
                val normalizedPath = if (trimmed.startsWith("/")) trimmed.substring(1) else trimmed
                val base = URI(normalizedBase)
                base.resolve(normalizedPath).toString()
            }
        } catch (e: Exception) {
            path
        }
    }

    private fun isNonContentLink(title: String, href: String): Boolean {
        val t = title.lowercase().trim()
        val h = href.lowercase().trim()

        // Filter pure numbers and pagination controls
        if (t.matches(Regex("""^\d+$""")) || (t.length <= 1 && !t.all { it.isLetter() })) {
            return true
        }
        if (t in listOf("next", "prev", "previous", "first", "last", "»", "«", "next »", "« prev", "more")) {
            return true
        }
        if (h.contains("/page/") && (t.matches(Regex("""^\d+$""")) || t in listOf("next", "prev", "previous", "»", "«"))) {
            return true
        }
        if (h.startsWith("/?q=") || h.startsWith("/?s=")) {
            return true
        }

        val nonContentKeywords = listOf(
            "about", "about us", "privacy", "privacy policy", "terms", "terms of service", "terms of use", "terms & conditions",
            "contact", "contact us", "dmca", "2257", "18 u.s.c. 2257", "disclaimer", "faq", "f.a.q.", "login", "log in",
            "register", "sign in", "sign up", "signup", "signin", "submit video", "submit", "upload", "advertise",
            "advertising", "theporndude", "feedback", "report", "cookie policy", "legal", "notice"
        )
        if (nonContentKeywords.any { t == it || t.startsWith("$it ") || t.endsWith(" $it") }) {
            return true
        }
        val nonContentPaths = listOf(
            "/about", "/privacy", "/terms", "/contact", "/dmca", "/2257", "/disclaimer", "/faq",
            "/login", "/register", "/signup", "/signin", "/theporndude", "/submit-video", "/advertise",
            "/random-porn", "/terms-of-use.php", "/privacy-policy.php", "/contact.php", "/join.php"
        )
        if (nonContentPaths.any { h.endsWith(it) || h.endsWith("$it/") || h.contains("$it?") || h.contains("$it#") || h.contains("/$it/") }) {
            return true
        }

        val shortcutPaths = listOf("/top", "/top/", "/new", "/new/", "/best", "/best/", "/history", "/history/")
        if (shortcutPaths.any { h.endsWith(it) || h == it }) {
            return true
        }

        if (h.startsWith("http://") || h.startsWith("https://")) {
            val activeHost = try { URI(activeBaseUrl).host ?: "" } catch (e: Exception) { "" }
            val linkHost = try { URI(h).host ?: "" } catch (e: Exception) { "" }
            if (activeHost.isNotBlank() && linkHost.isNotBlank() && !linkHost.contains(activeHost.removePrefix("www.")) && !activeHost.contains(linkHost.removePrefix("www."))) {
                val externalSpam = listOf("theporndude", "homo.xxx", "xlivrdr", "zline0", "chaturbate", "livejasmin", "cams")
                if (externalSpam.any { h.contains(it) }) {
                    return true
                }
            }
        }

        return false
    }

    internal fun resolvePornhousePornhd4kMedia(uuid: String, fullUrl: String): List<MediaSource> {
        val host = try { URI(fullUrl).host ?: activeBaseUrl } catch (e: Exception) { activeBaseUrl }
        val base = if (host.startsWith("http")) host else "https://$host"
        val allowedChars = ('a'..'z') + ('0'..'9')
        val salt = (1..6).map { allowedChars.random() }.joinToString("")
        val secret = "98126avrbi6m49vd7shxkn985"
        val rawToHash = "$uuid$salt$secret"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(rawToHash.toByteArray(Charsets.UTF_8))
        val hash = digest.joinToString("") { "%02x".format(it) }

        val ajaxUrl = "$base/ajax/get_sources/$uuid/$hash?count=1&mobile=false"
        val cookieName = "826avrbi6m49vd7shxkn985m${uuid}k06twz87wwxtp3dqiicks2df"
        val headers = mapOf(
            "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
            "Referer" to fullUrl,
            "X-Requested-With" to "XMLHttpRequest",
            "Cookie" to "$cookieName=$salt",
            "Accept" to "application/json, text/javascript, */*; q=0.01"
        )
        val jsonResponse = NetworkClient.fetchString(ajaxUrl, headers = headers)
        return parsePornhousePornhd4kJsonResponse(jsonResponse, base)
    }

    internal fun parsePornhousePornhd4kJsonResponse(jsonStr: String, baseUrl: String): List<MediaSource> {
        val results = mutableListOf<MediaSource>()
        try {
            val root = Json.parseToJsonElement(jsonStr).jsonObject
            val playlist = root["playlist"]?.jsonArray
            if (playlist != null) {
                for (item in playlist) {
                    val sources = item.jsonObject["sources"]?.jsonArray
                    if (sources != null) {
                        for (s in sources) {
                            val file = s.jsonObject["file"]?.jsonPrimitive?.content ?: ""
                            val label = s.jsonObject["label"]?.jsonPrimitive?.content?.ifBlank { "720p" } ?: "720p"
                            if (file.isNotBlank()) {
                                val isHls = file.contains(".m3u8")
                                val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
                                val mime = if (isHls) "application/x-mpegURL" else "video/mp4"
                                val headers = mapOf(
                                    "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
                                    "Referer" to "$baseUrl/",
                                    "Origin" to baseUrl
                                )
                                results.add(
                                    MediaSource(
                                        url = file,
                                        type = type,
                                        quality = label,
                                        mimeType = mime,
                                        headersRequired = headers
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            StreamHubLogger.w("HtmlSelectorAdapter", "Failed to parse pornhouse/pornhd4k json response: ${e.message}")
            val fileRegex = Regex(""""file"\s*:\s*"([^"]+)"""")
            fileRegex.findAll(jsonStr).forEach { match ->
                val file = match.groupValues[1].replace("\\/", "/")
                if (file.isNotBlank()) {
                    val isHls = file.contains(".m3u8")
                    val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
                    val mime = if (isHls) "application/x-mpegURL" else "video/mp4"
                    results.add(
                        MediaSource(
                            url = file,
                            type = type,
                            quality = "720p",
                            mimeType = mime,
                            headersRequired = mapOf(
                                "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
                                "Referer" to "$baseUrl/",
                                "Origin" to baseUrl
                            )
                        )
                    )
                }
            }
        }
        return results
    }

    internal fun resolveNetfapxMedia(postId: String, fullUrl: String): List<MediaSource> {
        val ajaxUrl = "https://netfapx.com/wp-admin/admin-ajax.php"
        val ajaxHeaders = mapOf(
            "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
            "Referer" to fullUrl,
            "Origin" to "https://netfapx.com",
            "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
            "X-Requested-With" to "XMLHttpRequest"
        )
        // 1. Try action=get_video_url
        var streamUrl: String? = null
        try {
            val resp = NetworkClient.postForm(ajaxUrl, mapOf("action" to "get_video_url", "idpost" to postId), headers = ajaxHeaders)
            val trimmed = resp.trim()
            if (trimmed.startsWith("http") && (trimmed.contains(".mp4") || trimmed.contains(".m3u8"))) {
                streamUrl = trimmed
            }
        } catch (e: Exception) {
            StreamHubLogger.w("HtmlSelectorAdapter", "Netfapx get_video_url failed for post $postId: ${e.message}")
        }

        // 2. Fallback: try action=get_download_url
        if (streamUrl.isNullOrBlank()) {
            try {
                val resp = NetworkClient.postForm(ajaxUrl, mapOf("action" to "get_download_url", "idpost" to postId), headers = ajaxHeaders)
                val trimmed = resp.trim()
                if (trimmed.startsWith("http") && (trimmed.contains(".mp4") || trimmed.contains(".m3u8"))) {
                    streamUrl = trimmed
                }
            } catch (e: Exception) {
                StreamHubLogger.w("HtmlSelectorAdapter", "Netfapx get_download_url failed for post $postId: ${e.message}")
            }
        }

        if (!streamUrl.isNullOrBlank()) {
            val isHls = isHlsStream(streamUrl)
            val type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
            val headers = resolveHeadersForStream(streamUrl, mapOf(
                "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
                "Referer" to "https://netfapx.com/"
            ))
            return listOf(MediaSource(
                url = streamUrl,
                type = type,
                quality = "720p",
                mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                headersRequired = headers
            ))
        }
        return emptyList()
    }

    internal fun resolveAvsMedia(vidId: String, fullUrl: String): List<MediaSource> {
        val host = try { URI(fullUrl).host ?: activeBaseUrl } catch (e: Exception) { activeBaseUrl }
        val base = if (host.startsWith("http")) host else "https://$host"
        val videoFileUrl = "$base/api/videofile.php?video_id=$vidId&lifetime=8640000"
        val headers = mapOf(
            "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
            "Referer" to "$base/"
        )
        val jsonResp = NetworkClient.fetchString(videoFileUrl, headers = headers)
        val jsonEl = Json.parseToJsonElement(jsonResp)
        val results = mutableListOf<MediaSource>()
        if (jsonEl is kotlinx.serialization.json.JsonArray && jsonEl.isNotEmpty()) {
            for (item in jsonEl) {
                val itemObj = item as? kotlinx.serialization.json.JsonObject ?: continue
                val encUrl = itemObj["video_url"]?.jsonPrimitive?.content
                if (!encUrl.isNullOrBlank()) {
                    val decodedPath = base164Decode(encUrl)
                    if (decodedPath.isNotBlank()) {
                        val streamUrl = if (decodedPath.startsWith("http")) decodedPath else "$base$decodedPath"
                        val isHls = streamUrl.contains(".m3u8")
                        val qual = itemObj["format"]?.jsonPrimitive?.content ?: if (decodedPath.contains("_hq")) "HQ" else "auto"
                        results.add(
                            MediaSource(
                                url = streamUrl,
                                type = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4,
                                mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                                quality = qual,
                                headersRequired = mapOf("Referer" to "$base/", "User-Agent" to NetworkClient.DEFAULT_USER_AGENT)
                            )
                        )
                    }
                }
            }
        }
        return results
    }

    internal fun base164Decode(encoded: String): String {
        val table = "\u0410\u0412\u0421D\u0415FGHIJKL\u041cNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789.,~"
        val cleaned = encoded.replace(Regex("""[^\u0410\u0412\u0421\u0415\u041cA-Za-z0-9\.,~]"""), "")
        val sb = StringBuilder()
        var i = 0
        while (i < cleaned.length) {
            val c1 = if (i < cleaned.length) table.indexOf(cleaned[i++]) else 0
            val c2 = if (i < cleaned.length) table.indexOf(cleaned[i++]) else 0
            val c3 = if (i < cleaned.length) table.indexOf(cleaned[i++]) else 0
            val c4 = if (i < cleaned.length) table.indexOf(cleaned[i++]) else 0
            if (c1 < 0 || c2 < 0) break
            val r = (c1 shl 2) or (c2 shr 4)
            val o = ((15 and c2) shl 4) or ((if (c3 >= 0) c3 else 0) shr 2)
            val l = (((3 and (if (c3 >= 0) c3 else 0)) shl 6) or (if (c4 >= 0) c4 else 0))
            sb.append(r.toChar())
            if (c3 in 0 until 64) sb.append(o.toChar())
            if (c4 in 0 until 64) sb.append(l.toChar())
        }
        return try {
            URLDecoder.decode(sb.toString(), StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            sb.toString()
        }
    }

    private suspend fun resolveMovieNerdsMedia(detailUrl: String): Result<List<MediaSource>> {
        val tmdbId = Regex("""(?:tmdbId=|/(?:movie|tv)/)(\d+)""").find(detailUrl)?.groupValues?.get(1)
            ?: Regex("""\b(\d{3,8})\b""").find(detailUrl)?.groupValues?.get(1)
            ?: return Result.failure(StreamHubError.PlaybackError(400, "Could not extract TMDB ID from $detailUrl"))

        val isTv = detailUrl.contains("/tv/") || detailUrl.contains("type=tv")
        val mediaType = if (isTv) "tv" else "movie"
        val season = Regex("""(?:season|season/)=(\d+)""").find(detailUrl)?.groupValues?.get(1) ?: "1"
        val episode = Regex("""(?:episode|episode/)=(\d+)""").find(detailUrl)?.groupValues?.get(1) ?: "1"

        val streamApiUrl = "https://api.movienerds.online/api/stream?tmdbId=$tmdbId&type=$mediaType&season=$season&episode=$episode"
        val mnHeaders = mapOf(
            "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
            "Accept" to "application/json",
            "x-client-app" to "movienerds",
            "x-app-key" to "mn_sec_2026_stream_auth",
            "Referer" to "https://movienerds.site/",
            "Origin" to "https://movienerds.site"
        )

        val streamJson = try {
            NetworkClient.fetchString(streamApiUrl, headers = mnHeaders)
        } catch (e: Exception) {
            return Result.failure(StreamHubError.PlaybackError(500, "Failed to fetch MovieNerds stream: ${e.message}", e))
        }

        // Fetch subtitles
        val subApiUrl = "https://api.movienerds.online/api/stream/subtitles?tmdbId=$tmdbId&type=$mediaType&season=$season&episode=$episode"
        val subJson = try {
            NetworkClient.fetchString(subApiUrl, headers = mnHeaders)
        } catch (e: Exception) { null }

        return parseMovieNerdsStreamJson(streamJson, detailUrl, subJson)
    }

    internal fun parseMovieNerdsStreamJson(streamJson: String, detailUrl: String, subJson: String? = null): Result<List<MediaSource>> {
        val mnHeaders = mapOf(
            "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
            "Referer" to "https://movienerds.site/",
            "Origin" to "https://movienerds.site"
        )
        val jsonEl = try { Json.parseToJsonElement(streamJson) } catch (e: Exception) { return Result.failure(e) }
        val rootObj = jsonEl as? kotlinx.serialization.json.JsonObject ?: return Result.failure(StreamHubError.ParsingError(config.id, "Invalid JSON root"))
        val streamsArray = rootObj["streams"] as? kotlinx.serialization.json.JsonArray ?: return Result.failure(StreamHubError.ParsingError(config.id, "No streams found in response"))

        val subtitleUrls = mutableListOf<String>()
        if (!subJson.isNullOrBlank()) {
            try {
                val subEl = Json.parseToJsonElement(subJson) as? kotlinx.serialization.json.JsonObject
                val subsArray = subEl?.get("subtitles") as? kotlinx.serialization.json.JsonArray
                subsArray?.forEach { subItem ->
                    val sObj = subItem as? kotlinx.serialization.json.JsonObject ?: return@forEach
                    val sUrl = sObj["url"]?.jsonPrimitive?.content ?: return@forEach
                    val sLang = sObj["language"]?.jsonPrimitive?.content ?: sObj["lang"]?.jsonPrimitive?.content ?: "en"
                    subtitleUrls.add("$sLang::$sUrl")
                }
            } catch (e: Exception) {
                // non-fatal
            }
        }

        val sources = mutableListOf<MediaSource>()
        for (item in streamsArray) {
            val sObj = item as? kotlinx.serialization.json.JsonObject ?: continue
            val rawUrl = sObj["url"]?.jsonPrimitive?.content ?: continue
            val server = sObj["server"]?.jsonPrimitive?.content ?: "Stream"
            val quality = sObj["quality"]?.jsonPrimitive?.content ?: "1080p"
            val typeStr = sObj["type"]?.jsonPrimitive?.content ?: "hls"
            val audio = sObj["audio"]?.jsonPrimitive?.content ?: ""
            val isHls = typeStr.equals("hls", ignoreCase = true) || rawUrl.contains(".m3u8")
            val mediaType = if (isHls) MediaSourceType.HLS else MediaSourceType.PROGRESSIVE_MP4
            val mime = if (isHls) "application/x-mpegURL" else "video/mp4"
            val label = if (audio.isNotBlank()) "$server ($quality - $audio)" else "$server ($quality)"

            val meta = mutableMapOf<String, String>()
            meta["server"] = server
            if (audio.isNotBlank()) meta["audio"] = audio
            if (subtitleUrls.isNotEmpty()) {
                meta["subtitles"] = subtitleUrls.joinToString("||")
            }

            sources.add(
                MediaSource(
                    url = rawUrl,
                    type = mediaType,
                    mimeType = mime,
                    quality = label,
                    headersRequired = mnHeaders,
                    metadata = meta
                )
            )
        }
        return if (sources.isNotEmpty()) Result.success(sources) else Result.failure(StreamHubError.PlaybackError(404, "No playable streams returned from MovieNerds"))
    }

    private fun resolveCineapseMedia(detailUrl: String): Result<List<MediaSource>> {
        return Result.success(buildCineapseMediaSources(detailUrl))
    }

    internal fun buildCineapseMediaSources(detailUrl: String): List<MediaSource> {
        val tmdbId = Regex("""(?:tmdbId=|/movie/|/tv/|movie/|tv/|-)(\d+)""").find(detailUrl)?.groupValues?.get(1)
            ?: Regex("""\b(\d{3,8})\b""").find(detailUrl)?.groupValues?.get(1)
            ?: "533535"

        val isTv = detailUrl.contains("/tv/") || detailUrl.contains("type=tv")
        val season = Regex("""(?:season/|/tv/\d+/|season=)(\d+)""").find(detailUrl)?.groupValues?.get(1) ?: "1"
        val episode = Regex("""(?:episode/|/tv/\d+/\d+/|episode=)(\d+)""").find(detailUrl)?.groupValues?.get(1) ?: "1"

        val cineapseHeaders = mapOf(
            "User-Agent" to NetworkClient.DEFAULT_USER_AGENT,
            "Referer" to "https://cineapse.net/",
            "Origin" to "https://cineapse.net"
        )

        val sources = mutableListOf<MediaSource>()

        // 1. VidLink Master
        val vidlinkUrl = if (isTv) "https://vidlink.pro/tv/$tmdbId/$season/$episode" else "https://vidlink.pro/movie/$tmdbId"
        sources.add(
            MediaSource(
                url = vidlinkUrl,
                type = MediaSourceType.EMBEDDED_WEB,
                mimeType = "text/html",
                quality = "VidLink 1080p Master (Multi-Server)",
                headersRequired = cineapseHeaders,
                metadata = mapOf("server" to "VidLink", "tmdbId" to tmdbId)
            )
        )

        // 2. CineSrc Direct
        val cinesrcUrl = if (isTv) "https://cinesrc.com/embed/tv/$tmdbId/$season/$episode" else "https://cinesrc.com/embed/movie/$tmdbId"
        sources.add(
            MediaSource(
                url = cinesrcUrl,
                type = MediaSourceType.EMBEDDED_WEB,
                mimeType = "text/html",
                quality = "CineSrc Direct 1080p",
                headersRequired = cineapseHeaders,
                metadata = mapOf("server" to "CineSrc", "tmdbId" to tmdbId)
            )
        )

        // 3. Solar / Embed.su
        val solarUrl = if (isTv) "https://embed.su/embed/tv/$tmdbId/$season/$episode" else "https://embed.su/embed/movie/$tmdbId"
        sources.add(
            MediaSource(
                url = solarUrl,
                type = MediaSourceType.EMBEDDED_WEB,
                mimeType = "text/html",
                quality = "Solar Stream 1080p",
                headersRequired = cineapseHeaders,
                metadata = mapOf("server" to "Solar", "tmdbId" to tmdbId)
            )
        )

        // 4. VidSrc CC
        val vidsrcUrl = if (isTv) "https://vidsrc.cc/v2/embed/tv/$tmdbId/$season/$episode" else "https://vidsrc.cc/v2/embed/movie/$tmdbId"
        sources.add(
            MediaSource(
                url = vidsrcUrl,
                type = MediaSourceType.EMBEDDED_WEB,
                mimeType = "text/html",
                quality = "VidSrc CC 1080p",
                headersRequired = cineapseHeaders,
                metadata = mapOf("server" to "VidSrc CC", "tmdbId" to tmdbId)
            )
        )

        // 5. 2Embed Multi-Host
        val embed2Url = if (isTv) "https://www.2embed.cc/embedtv/$tmdbId&s=$season&e=$episode" else "https://www.2embed.cc/embed/$tmdbId"
        sources.add(
            MediaSource(
                url = embed2Url,
                type = MediaSourceType.EMBEDDED_WEB,
                mimeType = "text/html",
                quality = "2Embed Multi-Host 720p",
                headersRequired = cineapseHeaders,
                metadata = mapOf("server" to "2Embed", "tmdbId" to tmdbId)
            )
        )

        // 6. Cineapse Native Web Player
        val webPlayerUrl = if (isTv) "https://cineapse.net/stream/tv/$tmdbId" else "https://cineapse.net/stream/movie/$tmdbId"
        sources.add(
            MediaSource(
                url = webPlayerUrl,
                type = MediaSourceType.EMBEDDED_WEB,
                mimeType = "text/html",
                quality = "Cineapse Native Web Player",
                headersRequired = cineapseHeaders,
                metadata = mapOf("server" to "Cineapse", "tmdbId" to tmdbId)
            )
        )

        return sources
    }
}
