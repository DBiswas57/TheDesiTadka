package com.thedesitadka.provider

import com.thedesitadka.core.config.DomainResolver
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

sealed class SearchEvent {
    data class ProviderResult(
        val providerId: String,
        val providerName: String,
        val items: List<VideoItem>
    ) : SearchEvent()

    data class ProviderError(
        val providerId: String,
        val providerName: String,
        val error: Throwable?
    ) : SearchEvent()

    data class Progress(
        val completedProviders: Int,
        val totalProviders: Int
    ) : SearchEvent()

    data class Completed(
        val totalItemsFound: Int
    ) : SearchEvent()
}

class ProviderEngine(
    val healthMonitor: ProviderHealthMonitor = ProviderHealthMonitor(),
    val domainResolver: DomainResolver? = null,
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
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

    fun registerAdapter(adapter: ProviderAdapter) {
        adapters[adapter.providerInfo.id] = adapter
    }

    fun getAllAdapters(): List<ProviderAdapter> {
        return adapters.values.sortedBy { it.providerInfo.name.lowercase() }
    }

    fun getActiveProviders(): List<ProviderInfo> {
        return adapters.values.map { adapter ->
            val info = adapter.providerInfo
            val currentStatus = healthMonitor.getStatus(info.id, info.status)
            info.copy(status = currentStatus)
        }.sortedBy { it.name.lowercase() }
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

    /**
     * Executes multi-threaded parallel search across providers, streaming results
     * as soon as each individual provider finishes its query.
     */
    fun searchFlow(
        query: String,
        providerId: String? = null,
        page: Int = 1,
        perProviderTimeoutMs: Long = 15_000L
    ): Flow<SearchEvent> = channelFlow {
        if (providerId != null) {
            val adapter = adapters[providerId]
            if (adapter == null) {
                send(SearchEvent.ProviderError(providerId, providerId, StreamHubError.ProviderUnavailable(providerId, "Provider '$providerId' not found")))
                send(SearchEvent.Completed(0))
                return@channelFlow
            }

            send(SearchEvent.Progress(completedProviders = 0, totalProviders = 1))
            try {
                val res = withTimeoutOrNull(perProviderTimeoutMs) {
                    adapter.search(query, page)
                }
                if (res != null && res.isSuccess) {
                    val validItems = res.getOrNull()?.items?.filter { it.title.isNotBlank() && it.detailUrl.isNotBlank() } ?: emptyList()
                    send(SearchEvent.ProviderResult(adapter.providerInfo.id, adapter.providerInfo.name, validItems))
                    send(SearchEvent.Progress(completedProviders = 1, totalProviders = 1))
                    send(SearchEvent.Completed(validItems.size))
                } else {
                    val err = res?.exceptionOrNull() ?: StreamHubError.NetworkError(0, "Search timed out for $providerId")
                    send(SearchEvent.ProviderError(adapter.providerInfo.id, adapter.providerInfo.name, err))
                    send(SearchEvent.Progress(completedProviders = 1, totalProviders = 1))
                    send(SearchEvent.Completed(0))
                }
            } catch (e: Exception) {
                send(SearchEvent.ProviderError(adapter.providerInfo.id, adapter.providerInfo.name, e))
                send(SearchEvent.Progress(completedProviders = 1, totalProviders = 1))
                send(SearchEvent.Completed(0))
            }
            return@channelFlow
        }

        // Global parallel search across all active search-capable providers
        val activeSearchAdapters = adapters.values.filter { it.hasCapability(ProviderCapability.SEARCH) }
        val total = activeSearchAdapters.size
        if (total == 0) {
            send(SearchEvent.Completed(0))
            return@channelFlow
        }

        send(SearchEvent.Progress(completedProviders = 0, totalProviders = total))

        val completedCount = AtomicInteger(0)
        val totalFound = AtomicInteger(0)

        val jobs = activeSearchAdapters.map { adapter ->
            launch(ioDispatcher) {
                val info = adapter.providerInfo
                try {
                    val result = withTimeoutOrNull(perProviderTimeoutMs) {
                        adapter.search(query, page)
                    }
                    if (result != null && result.isSuccess) {
                        val items = result.getOrNull()?.items?.filter {
                            it.title.isNotBlank() && it.detailUrl.isNotBlank()
                        } ?: emptyList()
                        if (items.isNotEmpty()) {
                            totalFound.addAndGet(items.size)
                            send(SearchEvent.ProviderResult(info.id, info.name, items))
                        }
                    } else if (result != null && result.isFailure) {
                        send(SearchEvent.ProviderError(info.id, info.name, result.exceptionOrNull()))
                    }
                } catch (e: Exception) {
                    send(SearchEvent.ProviderError(info.id, info.name, e))
                } finally {
                    val done = completedCount.incrementAndGet()
                    send(SearchEvent.Progress(completedProviders = done, totalProviders = total))
                }
            }
        }

        jobs.joinAll()
        send(SearchEvent.Completed(totalFound.get()))
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

        val allItems = Collections.synchronizedList(mutableListOf<VideoItem>())
        searchFlow(query, providerId = null, page = page).collect { event ->
            if (event is SearchEvent.ProviderResult) {
                allItems.addAll(event.items)
            }
        }

        Result.success(FeedPage(items = allItems.toList(), page = page, hasNextPage = false))
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

    suspend fun getCategoryFeed(providerId: String, categoryUrl: String, page: Int = 1): Result<FeedPage> {
        val adapter = adapters[providerId]
            ?: return Result.failure(StreamHubError.ProviderUnavailable(providerId, "Provider not found"))
        return adapter.getCategoryFeed(categoryUrl, page)
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
