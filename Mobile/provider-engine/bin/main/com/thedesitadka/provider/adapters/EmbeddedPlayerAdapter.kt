package com.thedesitadka.provider.adapters

import com.thedesitadka.core.model.Category
import com.thedesitadka.core.model.FeedPage
import com.thedesitadka.core.model.MediaSource
import com.thedesitadka.core.model.MediaSourceType
import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.ProviderInfo
import com.thedesitadka.core.model.ProviderStatus
import com.thedesitadka.core.model.VideoItem
import com.thedesitadka.provider.ProviderAdapter

class EmbeddedPlayerAdapter(
    private val config: ProviderConfig
) : ProviderAdapter {

    private val delegate = HtmlSelectorAdapter(config)

    override val providerInfo: ProviderInfo = delegate.providerInfo

    override suspend fun getCategories(): Result<List<Category>> = delegate.getCategories()
    override suspend fun getHomeFeed(page: Int): Result<FeedPage> = delegate.getHomeFeed(page)
    override suspend fun search(query: String, page: Int): Result<FeedPage> = delegate.search(query, page)
    override suspend fun getDetails(detailUrl: String): Result<VideoItem> = delegate.getDetails(detailUrl)
    override suspend fun getRelatedContent(detailUrl: String): Result<List<VideoItem>> = delegate.getRelatedContent(detailUrl)

    override suspend fun getPlayableMedia(detailUrl: String): Result<List<MediaSource>> {
        val result = delegate.getPlayableMedia(detailUrl)
        if (result.isSuccess && result.getOrNull()?.isNotEmpty() == true) {
            return result
        }
        // If direct stream not found, provide authorized embedded web player
        return Result.success(
            listOf(
                MediaSource(
                    url = detailUrl,
                    type = MediaSourceType.EMBEDDED_WEB,
                    mimeType = "text/html"
                )
            )
        )
    }
}
