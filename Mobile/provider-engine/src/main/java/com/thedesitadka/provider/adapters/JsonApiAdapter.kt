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
import com.thedesitadka.provider.ProviderAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class JsonApiAdapter(
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

    override suspend fun getCategories(): Result<List<Category>> = Result.success(emptyList())

    override suspend fun getHomeFeed(page: Int): Result<FeedPage> = withContext(Dispatchers.IO) {
        try {
            val endpoint = config.apiConfig?.postsEndpoint ?: "/api/videos"
            val targetUrl = "${config.baseUrl.trimEnd('/')}$endpoint"
            val raw = NetworkClient.fetchString(targetUrl, config.apiConfig?.customHeaders ?: emptyMap())
            val array = json.parseToJsonElement(raw).jsonArray

            val items = array.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val title = obj["title"]?.jsonPrimitive?.content ?: ""
                val thumb = obj["thumbnail"]?.jsonPrimitive?.content ?: ""
                val url = obj["url"]?.jsonPrimitive?.content ?: ""
                val desc = obj["description"]?.jsonPrimitive?.content ?: ""

                VideoItem(
                    id = id,
                    providerId = config.id,
                    title = title,
                    description = desc,
                    thumbnailUrl = thumb,
                    detailUrl = url,
                    playbackAvailability = PlaybackAvailability.AVAILABLE,
                    downloadAvailability = if (hasCapability(ProviderCapability.DOWNLOAD)) DownloadAvailability.AUTHORIZED else DownloadAvailability.NOT_SUPPORTED
                )
            }
            Result.success(FeedPage(items = items, page = page, hasNextPage = items.size >= 10))
        } catch (e: Exception) {
            Result.failure(StreamHubError.NetworkError(0, "JSON API feed failed: ${e.message}", e))
        }
    }

    override suspend fun search(query: String, page: Int): Result<FeedPage> = getHomeFeed(page)

    override suspend fun getDetails(detailUrl: String): Result<VideoItem> = HtmlSelectorAdapter(config).getDetails(detailUrl)

    override suspend fun getPlayableMedia(detailUrl: String): Result<List<MediaSource>> = HtmlSelectorAdapter(config).getPlayableMedia(detailUrl)

    override suspend fun getRelatedContent(detailUrl: String): Result<List<VideoItem>> = Result.success(emptyList())
}
