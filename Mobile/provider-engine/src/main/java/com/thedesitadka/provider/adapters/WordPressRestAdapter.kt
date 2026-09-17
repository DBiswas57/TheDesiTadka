package com.thedesitadka.provider.adapters

import com.thedesitadka.core.model.Category
import com.thedesitadka.core.model.DownloadAvailability
import com.thedesitadka.core.model.FeedPage
import com.thedesitadka.core.model.MediaSource
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WordPressRestAdapter(
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

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getCategories(): Result<List<Category>> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "${config.baseUrl.trimEnd('/')}/wp-json/wp/v2/categories?per_page=20"
            val rawJson = NetworkClient.fetchString(endpoint)
            val array = json.parseToJsonElement(rawJson).jsonArray

            val categories = array.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val link = obj["link"]?.jsonPrimitive?.content ?: ""
                Category(id = id, name = name, url = link)
            }
            Result.success(categories)
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    override suspend fun getHomeFeed(page: Int): Result<FeedPage> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "${config.baseUrl.trimEnd('/')}/wp-json/wp/v2/posts?_embed&page=$page&per_page=12"
            val rawJson = NetworkClient.fetchString(endpoint)
            parsePostsJson(rawJson, page)
        } catch (e: Exception) {
            StreamHubLogger.e("WordPressRestAdapter", "Failed to fetch WP REST feed: ${e.message}")
            Result.failure(StreamHubError.NetworkError(0, "Failed to get WP REST feed: ${e.message}", e))
        }
    }

    override suspend fun search(query: String, page: Int): Result<FeedPage> = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val endpoint = "${config.baseUrl.trimEnd('/')}/wp-json/wp/v2/posts?_embed&search=$encoded&page=$page&per_page=12"
            val rawJson = NetworkClient.fetchString(endpoint)
            parsePostsJson(rawJson, page)
        } catch (e: Exception) {
            Result.failure(StreamHubError.NetworkError(0, "WP REST search failed: ${e.message}", e))
        }
    }

    override suspend fun getDetails(detailUrl: String): Result<VideoItem> = withContext(Dispatchers.IO) {
        HtmlSelectorAdapter(config).getDetails(detailUrl)
    }

    override suspend fun getPlayableMedia(detailUrl: String): Result<List<MediaSource>> = withContext(Dispatchers.IO) {
        HtmlSelectorAdapter(config).getPlayableMedia(detailUrl)
    }

    override suspend fun getRelatedContent(detailUrl: String): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        HtmlSelectorAdapter(config).getRelatedContent(detailUrl)
    }

    private fun parsePostsJson(rawJson: String, page: Int): Result<FeedPage> {
        val array = json.parseToJsonElement(rawJson).jsonArray
        val items = array.mapNotNull { element ->
            val post = element.jsonObject
            val id = post["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val link = post["link"]?.jsonPrimitive?.content ?: ""
            val title = post["title"]?.jsonObject?.get("rendered")?.jsonPrimitive?.content ?: ""
            val desc = post["excerpt"]?.jsonObject?.get("rendered")?.jsonPrimitive?.content ?: ""

            // Extract featured image from _embedded
            var thumb = ""
            val embedded = post["_embedded"]?.jsonObject
            val mediaArray = embedded?.get("wp:featuredmedia")?.jsonArray
            if (mediaArray != null && mediaArray.isNotEmpty()) {
                thumb = mediaArray[0].jsonObject["source_url"]?.jsonPrimitive?.content ?: ""
            }

            VideoItem(
                id = id,
                providerId = config.id,
                title = org.jsoup.Jsoup.parse(title).text(),
                description = org.jsoup.Jsoup.parse(desc).text(),
                thumbnailUrl = thumb,
                detailUrl = link,
                playbackAvailability = PlaybackAvailability.AVAILABLE,
                downloadAvailability = if (hasCapability(ProviderCapability.DOWNLOAD)) DownloadAvailability.AUTHORIZED else DownloadAvailability.NOT_SUPPORTED
            )
        }
        return Result.success(FeedPage(items = items, page = page, hasNextPage = items.size >= 10))
    }
}
