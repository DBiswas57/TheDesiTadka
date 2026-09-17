package com.thedesitadka.app.data

import com.thedesitadka.core.model.ProviderInfo
import com.thedesitadka.core.model.VideoItem
import com.thedesitadka.core.security.StreamHubLogger
import com.thedesitadka.provider.ProviderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

enum class ProviderFetchStatus {
    IDLE, LOADING, SUCCESS, ERROR
}

data class DashboardState(
    val videos: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val providerStatuses: Map<String, ProviderFetchStatus> = emptyMap(),
    val errorMessage: String? = null,
    val lastUpdated: Long = 0L
)

class DashboardRepository(
    private val providerEngine: ProviderEngine,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    companion object {
        private const val CACHE_TTL_MS = 10 * 60 * 1000L // 10 minutes cache validity
    }

    private val cache = ConcurrentHashMap<String, CachedCategoryData>()
    private val stateFlows = ConcurrentHashMap<String, MutableStateFlow<DashboardState>>()
    private val fetchJobs = ConcurrentHashMap<String, Job>()
    private val mutex = Mutex()

    private data class CachedCategoryData(
        val videos: List<VideoItem>,
        val timestamp: Long
    )

    fun getDashboardState(categoryId: String = "all"): StateFlow<DashboardState> {
        val flow = stateFlows.getOrPut(categoryId) {
            MutableStateFlow(DashboardState(isLoading = true))
        }

        // Check if we have valid memory cache
        val cached = cache[categoryId]
        if (cached != null && cached.videos.isNotEmpty()) {
            val isStale = (System.currentTimeMillis() - cached.timestamp) > CACHE_TTL_MS
            flow.update {
                it.copy(
                    videos = cached.videos,
                    isLoading = false,
                    isRefreshing = isStale,
                    lastUpdated = cached.timestamp
                )
            }
            if (isStale) {
                // Background refresh without blanking UI
                refreshInBackground(categoryId)
            }
        } else {
            // No cache: initial load
            refreshInBackground(categoryId)
        }

        return flow.asStateFlow()
    }

    fun refresh(categoryId: String = "all") {
        refreshInBackground(categoryId, forceRefresh = true)
    }

    private fun refreshInBackground(categoryId: String, forceRefresh: Boolean = false) {
        scope.launch {
            mutex.withLock {
                val existingJob = fetchJobs[categoryId]
                if (existingJob != null && existingJob.isActive) {
                    return@withLock // Deduplicate: already fetching
                }

                val flow = stateFlows.getOrPut(categoryId) {
                    MutableStateFlow(DashboardState(isLoading = true))
                }

                val hasData = flow.value.videos.isNotEmpty()
                flow.update {
                    it.copy(
                        isLoading = !hasData,
                        isRefreshing = hasData,
                        errorMessage = null
                    )
                }

                val job = launch {
                    fetchAggregatedVideos(categoryId, flow)
                }
                fetchJobs[categoryId] = job
            }
        }
    }

    private suspend fun fetchAggregatedVideos(
        categoryId: String,
        flow: MutableStateFlow<DashboardState>
    ) {
        val allActiveProviders = providerEngine.getActiveProviders()
        val providersToQuery = when (categoryId) {
            "free" -> allActiveProviders.filter { it.id != "premium_catalog" }
            "premium" -> allActiveProviders.filter { it.id == "premium_catalog" || it.capabilities.any { c -> c.name == "PREVIEW" } }
            else -> allActiveProviders
        }.ifEmpty { allActiveProviders }

        val statuses = ConcurrentHashMap<String, ProviderFetchStatus>()
        providersToQuery.forEach { statuses[it.id] = ProviderFetchStatus.LOADING }

        flow.update { it.copy(providerStatuses = statuses.toMap()) }

        // Structured multi-provider concurrent execution with failure isolation and bounded concurrency (max 4 concurrent requests)
        val semaphore = Semaphore(4)
        val results = supervisorScope {
            providersToQuery.map { provider ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val start = System.currentTimeMillis()
                        try {
                            val feedResult = providerEngine.getHomeFeed(provider.id, page = 1)
                            val duration = System.currentTimeMillis() - start
                            if (feedResult.isSuccess) {
                                val items = feedResult.getOrNull()?.items ?: emptyList()
                                statuses[provider.id] = ProviderFetchStatus.SUCCESS
                                StreamHubLogger.log(
                                    StreamHubLogger.Category.PROVIDER,
                                    "INFO",
                                    "PROVIDER_FETCH: id=${provider.id}, items=${items.size}, time=${duration}ms"
                                )
                                items
                            } else {
                                statuses[provider.id] = ProviderFetchStatus.ERROR
                                val err = feedResult.exceptionOrNull()?.message ?: "Unknown error"
                                StreamHubLogger.w("DashboardRepository", "Provider '${provider.id}' error ($duration ms): $err")
                                emptyList()
                            }
                        } catch (e: Exception) {
                            statuses[provider.id] = ProviderFetchStatus.ERROR
                            StreamHubLogger.e("DashboardRepository", "Exception in provider '${provider.id}': ${e.message}")
                            emptyList()
                        }
                    }
                }
            }
        }

        val allItems = results.map { it.await() }.flatten()

        // Deduplicate items based on ID and detailUrl
        val seenIds = HashSet<String>()
        val seenUrls = HashSet<String>()
        val deduplicated = allItems.filter { item ->
            val isNewId = seenIds.add(item.id)
            val isNewUrl = if (item.detailUrl.isNotBlank()) seenUrls.add(item.detailUrl) else true
            isNewId && isNewUrl
        }

        val now = System.currentTimeMillis()

        // Only overwrite cache if we received items or had empty cache
        if (deduplicated.isNotEmpty() || cache[categoryId] == null) {
            cache[categoryId] = CachedCategoryData(deduplicated, now)
        }

        val finalVideos = if (deduplicated.isNotEmpty()) deduplicated else cache[categoryId]?.videos ?: emptyList()

        flow.update {
            it.copy(
                videos = finalVideos,
                isLoading = false,
                isRefreshing = false,
                providerStatuses = statuses.toMap(),
                errorMessage = if (finalVideos.isEmpty()) "Unable to load media feeds. Tap to retry." else null,
                lastUpdated = now
            )
        }
    }
}
