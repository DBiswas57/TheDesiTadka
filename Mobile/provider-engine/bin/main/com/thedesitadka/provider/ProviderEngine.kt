package com.thedesitadka.provider

import com.thedesitadka.core.model.Category
import com.thedesitadka.core.model.FeedPage
import com.thedesitadka.core.model.MediaSource
import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.ProviderInfo
import com.thedesitadka.core.model.ProviderManifest
import com.thedesitadka.core.model.ProviderStatus
import com.thedesitadka.core.model.StreamHubError
import com.thedesitadka.core.model.VideoItem
import com.thedesitadka.core.security.StreamHubLogger
import com.thedesitadka.provider.adapters.EmbeddedPlayerAdapter
import com.thedesitadka.provider.adapters.HtmlSelectorAdapter
import com.thedesitadka.provider.adapters.JsonApiAdapter
import com.thedesitadka.provider.adapters.RssFeedAdapter
import com.thedesitadka.provider.adapters.WordPressRestAdapter
import com.thedesitadka.core.config.DomainResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

class ProviderEngine(
    val healthMonitor: ProviderHealthMonitor = ProviderHealthMonitor(),
    val domainResolver: DomainResolver? = null
) {

    private val adapters = ConcurrentHashMap<String, ProviderAdapter>()

    /**
     * Synchronizes registered adapters with the remote [manifest].
     * Dynamically adds newly configured providers, updates existing ones,
     * and removes or disables retired ones without requiring APK rebuilds.
     */
    @Synchronized
    fun updateFromManifest(manifest: ProviderManifest) {
        val incomingIds = manifest.providers.map { it.id }.toSet()

        // Remove retired providers
        val iterator = adapters.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!incomingIds.contains(entry.key)) {
                StreamHubLogger.i("ProviderEngine", "Unloading retired provider: ${entry.key}")
                iterator.remove()
            }
        }

        // Register or reconfigure providers
        for (providerConfig in manifest.providers) {
            if (!providerConfig.enabled) {
                adapters.remove(providerConfig.id)
                healthMonitor.setStatus(providerConfig.id, ProviderStatus.DISABLED)
                continue
            }

            try {
                val adapter = createAdapter(providerConfig)
                adapters[providerConfig.id] = adapter
                healthMonitor.setStatus(providerConfig.id, ProviderStatus.ENABLED)
                StreamHubLogger.i("ProviderEngine", "Loaded provider '${providerConfig.id}' with strategy '${providerConfig.adapter}'")
            } catch (e: Exception) {
                healthMonitor.setStatus(providerConfig.id, ProviderStatus.CONFIG_ERROR)
                StreamHubLogger.e("ProviderEngine", "Failed to instantiate provider '${providerConfig.id}': ${e.message}")
            }
        }
    }

    fun getAdapter(providerId: String): ProviderAdapter? {
        return adapters[providerId]
    }

    fun getAllAdapters(): List<ProviderAdapter> {
        return adapters.values.toList()
    }

    fun getActiveProviders(): List<ProviderInfo> {
        return adapters.values.map { adapter ->
            val info = adapter.providerInfo
            val currentStatus = healthMonitor.getStatus(info.id, info.status)
            info.copy(status = currentStatus)
        }
    }

    suspend fun getHomeFeed(providerId: String, page: Int = 1): Result<FeedPage> {
        val adapter = adapters[providerId]
            ?: return Result.failure(StreamHubError.ProviderUnavailable(providerId, "Provider '$providerId' not found or disabled"))

        return try {
            val result = adapter.getHomeFeed(page)
            if (result.isSuccess) {
                healthMonitor.recordSuccess(providerId)
            } else {
                healthMonitor.recordError(providerId)
            }
            result
        } catch (e: Exception) {
            healthMonitor.recordError(providerId)
            Result.failure(StreamHubError.NetworkError(0, "Feed error: ${e.message}", e))
        }
    }

    suspend fun search(query: String, providerId: String? = null, page: Int = 1): Result<FeedPage> = coroutineScope {
        if (providerId != null) {
            val adapter = adapters[providerId]
                ?: return@coroutineScope Result.failure(StreamHubError.ProviderUnavailable(providerId, "Provider not found"))
            return@coroutineScope adapter.search(query, page)
        }

        // Global search across all active search-capable providers
        val activeSearchAdapters = adapters.values.filter { it.hasCapability(ProviderCapability.SEARCH) }
        if (activeSearchAdapters.isEmpty()) {
            return@coroutineScope Result.success(FeedPage(items = emptyList(), page = page, hasNextPage = false))
        }

        val deferredResults = activeSearchAdapters.map { adapter ->
            async {
                try {
                    adapter.search(query, page).getOrNull()?.items ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

        val combinedItems = deferredResults.awaitAll().flatten()
        Result.success(FeedPage(items = combinedItems, page = page, hasNextPage = false))
    }

    suspend fun getDetails(providerId: String, detailUrl: String): Result<VideoItem> {
        val adapter = adapters[providerId]
            ?: return Result.failure(StreamHubError.ProviderUnavailable(providerId, "Provider not found"))
        return adapter.getDetails(detailUrl)
    }

    suspend fun getPlayableMedia(providerId: String, detailUrl: String): Result<List<MediaSource>> {
        val adapter = adapters[providerId]
            ?: return Result.failure(StreamHubError.ProviderUnavailable(providerId, "Provider not found"))
        return adapter.getPlayableMedia(detailUrl)
    }

    suspend fun getRelatedContent(providerId: String, detailUrl: String): Result<List<VideoItem>> {
        val adapter = adapters[providerId]
            ?: return Result.failure(StreamHubError.ProviderUnavailable(providerId, "Provider not found"))
        return adapter.getRelatedContent(detailUrl)
    }

    private fun createAdapter(config: ProviderConfig): ProviderAdapter {
        return when (config.adapter.lowercase()) {
            "html_selector" -> HtmlSelectorAdapter(config, domainResolver)
            "wordpress_rest" -> WordPressRestAdapter(config)
            "rss" -> RssFeedAdapter(config)
            "json_api" -> JsonApiAdapter(config)
            "embedded_player" -> EmbeddedPlayerAdapter(config)
            else -> throw IllegalArgumentException("Unknown adapter strategy: ${config.adapter}")
        }
    }
}
