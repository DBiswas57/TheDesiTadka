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
import com.thedesitadka.core.network.NetworkClient
import com.thedesitadka.core.security.StreamHubLogger
import com.thedesitadka.provider.ProviderAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URI

class RssFeedAdapter(
    private val config: ProviderConfig
) : ProviderAdapter {

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

    override suspend fun getCategories(): Result<List<Category>> = withContext(Dispatchers.IO) {
        val feedResult = getHomeFeed(1)
        feedResult.map { feedPage ->
            feedPage.items.mapNotNull { it.category }
                .distinct()
                .map { Category(id = it.lowercase().replace(" ", "-"), name = it) }
        }
    }

    override suspend fun getHomeFeed(page: Int): Result<FeedPage> = withContext(Dispatchers.IO) {
        try {
            val feedUrl = if (page <= 1) {
                "${config.baseUrl.trimEnd('/')}/feed/"
            } else {
                "${config.baseUrl.trimEnd('/')}/feed/?paged=$page"
            }
            val xml = NetworkClient.fetchString(feedUrl)
            parseRss(xml, page)
        } catch (e: Exception) {
            StreamHubLogger.e("RssFeedAdapter", "Failed to fetch RSS feed: ${e.message}")
            Result.failure(StreamHubError.NetworkError(0, "Failed to get RSS feed: ${e.message}", e))
        }
    }

    override suspend fun search(query: String, page: Int): Result<FeedPage> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val searchUrl = "${config.baseUrl.trimEnd('/')}/feed/?s=$encodedQuery&paged=$page"
            val xml = NetworkClient.fetchString(searchUrl)
            parseRss(xml, page)
        } catch (e: Exception) {
            Result.failure(StreamHubError.NetworkError(0, "RSS search failed: ${e.message}", e))
        }
    }

    override suspend fun getDetails(detailUrl: String): Result<VideoItem> = withContext(Dispatchers.IO) {
        // Fetch from HTML using detail fallback
        HtmlSelectorAdapter(config).getDetails(detailUrl)
    }

    override suspend fun getPlayableMedia(detailUrl: String): Result<List<MediaSource>> = withContext(Dispatchers.IO) {
        HtmlSelectorAdapter(config).getPlayableMedia(detailUrl)
    }

    override suspend fun getRelatedContent(detailUrl: String): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        HtmlSelectorAdapter(config).getRelatedContent(detailUrl)
    }

    private fun parseRss(xml: String, page: Int): Result<FeedPage> {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val itemElements = doc.select("item")

        val items = itemElements.mapNotNull { el ->
            val title = el.select("title").text().trim()
            val link = el.select("link").text().trim()
            val desc = el.select("description").text().trim()
            val pubDate = el.select("pubDate").text().trim()
            val category = el.select("category").firstOrNull()?.text()?.trim()

            // Thumbnail extraction
            var thumb = el.select("media\\:thumbnail, media|thumbnail").attr("url")
            if (thumb.isBlank()) {
                val imgInDesc = Jsoup.parse(desc).select("img").firstOrNull()
                thumb = imgInDesc?.attr("src") ?: ""
            }

            // Enclosure video check
            val enclosure = el.select("enclosure[type^='video/']")
            val videoUrl = enclosure.attr("url")

            val downloadAvail = if (videoUrl.isNotBlank() && hasCapability(ProviderCapability.DOWNLOAD)) {
                DownloadAvailability.AUTHORIZED
            } else {
                DownloadAvailability.NOT_SUPPORTED
            }

            if (title.isNotBlank() && link.isNotBlank()) {
                VideoItem(
                    id = link.hashCode().toString(),
                    providerId = config.id,
                    title = title,
                    description = Jsoup.parse(desc).text(),
                    thumbnailUrl = thumb,
                    detailUrl = link,
                    publishedAt = pubDate,
                    category = category,
                    playbackAvailability = PlaybackAvailability.AVAILABLE,
                    downloadAvailability = downloadAvail
                )
            } else null
        }

        return Result.success(FeedPage(items = items, page = page, hasNextPage = items.size >= 10))
    }
}
