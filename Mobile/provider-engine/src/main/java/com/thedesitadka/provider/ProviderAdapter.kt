package com.thedesitadka.provider

import com.thedesitadka.core.model.Category
import com.thedesitadka.core.model.FeedPage
import com.thedesitadka.core.model.MediaSource
import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.model.ProviderInfo
import com.thedesitadka.core.model.VideoItem

interface ProviderAdapter {

    val providerInfo: ProviderInfo

    fun hasCapability(capability: ProviderCapability): Boolean {
        return providerInfo.capabilities.contains(capability)
    }

    suspend fun getCategories(): Result<List<Category>>

    suspend fun getHomeFeed(page: Int = 1): Result<FeedPage>

    suspend fun search(query: String, page: Int = 1): Result<FeedPage>

    suspend fun getDetails(detailUrl: String): Result<VideoItem>

    suspend fun getPlayableMedia(detailUrl: String): Result<List<MediaSource>>

    suspend fun getRelatedContent(detailUrl: String): Result<List<VideoItem>>
}
