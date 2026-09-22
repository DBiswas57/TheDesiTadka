package com.thedesitadka.app.data

import com.thedesitadka.app.storage.PreferenceStore
import com.thedesitadka.core.model.ContentCategoryDefinition
import com.thedesitadka.core.model.ProviderInfo
import com.thedesitadka.core.model.VideoItem
import com.thedesitadka.core.network.CloudflareChallengeException
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
    val challengeProviderId: String? = null,
    val lastUpdated: Long = 0L
)

class DashboardRepository(
    private val providerEngine: ProviderEngine,
    private val preferenceStore: PreferenceStore? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    companion object {
        private const val CACHE_TTL_MS = 10 * 60 * 1000L // 10 minutes cache validity
        private const val FETCH_COOLDOWN_MS = 5_000L // 5 seconds cooldown to prevent rapid loops
    }

    private val cache = ConcurrentHashMap<String, CachedCategoryData>()
    private val stateFlows = ConcurrentHashMap<String, MutableStateFlow<DashboardState>>()
    private val fetchJobs = ConcurrentHashMap<String, Job>()
    private val lastFetchTimes = ConcurrentHashMap<String, Long>()
    private val mutex = Mutex()

    init {
        preferenceStore?.selectedHomeProvidersFlow?.let { flow ->
            scope.launch {
                var previousSet: Set<String>? = null
                flow.collect { currentSet ->
                    if (previousSet != null && previousSet != currentSet) {
                        clearCacheAndReload()
                    }
                    previousSet = currentSet
                }
            }
        }
    }

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
        if (cached != null) {
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
                refreshInBackground(categoryId)
            }
        } else {
            // No cache: initial load if cooldown elapsed
            val lastAttempt = lastFetchTimes[categoryId] ?: 0L
            if ((System.currentTimeMillis() - lastAttempt) > FETCH_COOLDOWN_MS) {
                refreshInBackground(categoryId)
            }
        }

        return flow.asStateFlow()
    }

    fun refresh(categoryId: String = "all") {
        refreshInBackground(categoryId, forceRefresh = true)
    }

    fun clearCacheAndReload() {
        cache.clear()
        val categories = stateFlows.keys().toList()
        if (categories.isEmpty()) {
            refresh("all")
        } else {
            categories.forEach { refresh(it) }
        }
    }

    private fun refreshInBackground(categoryId: String, forceRefresh: Boolean = false) {
        scope.launch {
            mutex.withLock {
                val existingJob = fetchJobs[categoryId]
                if (existingJob != null && existingJob.isActive) {
                    return@withLock // Deduplicate: already fetching
                }

                val lastAttempt = lastFetchTimes[categoryId] ?: 0L
                if (!forceRefresh && (System.currentTimeMillis() - lastAttempt) < FETCH_COOLDOWN_MS) {
                    return@withLock // Cooldown active, suppress redundant rapid fetch
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
        val isSelectionDone = preferenceStore?.isHomeSelectionCompleted() ?: false
        val selectedHomeProviders = preferenceStore?.getSelectedHomeProviders() ?: emptySet()
        val candidateProviders = if (isSelectionDone && selectedHomeProviders.isNotEmpty()) {
            allActiveProviders.filter { it.id in selectedHomeProviders }
        } else if (isSelectionDone && selectedHomeProviders.isEmpty()) {
            emptyList()
        } else {
            allActiveProviders
        }

        if (candidateProviders.isEmpty()) {
            flow.update {
                it.copy(
                    videos = emptyList(),
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = if (isSelectionDone && selectedHomeProviders.isEmpty()) "No sites selected for Home. Please customize sources in Settings." else null
                )
            }
            return
        }

        val categoryDef = ContentCategoryDefinition.DEFAULT_CATEGORIES.find { it.id == categoryId }
        val providersToQuery = when {
            categoryDef != null && categoryDef.providerIds.isNotEmpty() -> {
                candidateProviders.filter { it.id in categoryDef.providerIds }
            }
            categoryId == "downloadable" -> {
                candidateProviders.filter { it.capabilities.any { c -> c.name == "DOWNLOAD" } }
            }
            categoryId == "movies" -> {
                candidateProviders.filter { it.id in listOf("movienerds", "cineapse", "prmovies", "prmovies_church") }
            }
            categoryId == "free" -> candidateProviders.filter { it.id != "premium_catalog" }
            else -> candidateProviders
        }.ifEmpty { candidateProviders }

        val statuses = ConcurrentHashMap<String, ProviderFetchStatus>()
        providersToQuery.forEach { statuses[it.id] = ProviderFetchStatus.LOADING }

        flow.update { it.copy(providerStatuses = statuses.toMap()) }

        val collectedVideos = java.util.Collections.synchronizedList(mutableListOf<VideoItem>())
        val seenIds = ConcurrentHashMap.newKeySet<String>()
        val seenUrls = ConcurrentHashMap.newKeySet<String>()

        var detectedChallengeProviderId: String? = null
        var lastErrorMessage: String? = null

        // Structured multi-provider concurrent execution with failure isolation and progressive streaming
        val semaphore = Semaphore(12)
        supervisorScope {
            providersToQuery.forEach { provider ->
                launch(Dispatchers.IO) {
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

                                if (items.isNotEmpty()) {
                                    val newItems = items.filter { item ->
                                        val isNewId = seenIds.add(item.id)
                                        val isNewUrl = if (item.detailUrl.isNotBlank()) seenUrls.add(item.detailUrl) else true
                                        isNewId && isNewUrl
                                    }
                                    if (newItems.isNotEmpty()) {
                                        collectedVideos.addAll(newItems)
                                        val currentSnapshot = collectedVideos.toList()
                                        flow.update { current ->
                                            current.copy(
                                                videos = currentSnapshot,
                                                isLoading = true,
                                                providerStatuses = statuses.toMap(),
                                                lastUpdated = System.currentTimeMillis()
                                            )
                                        }
                                    } else {
                                        flow.update { it.copy(providerStatuses = statuses.toMap()) }
                                    }
                                } else {
                                    flow.update { it.copy(providerStatuses = statuses.toMap()) }
                                }
                            } else {
                                statuses[provider.id] = ProviderFetchStatus.ERROR
                                val exception = feedResult.exceptionOrNull()
                                val err = exception?.message ?: "Unknown error"
                                val isCloudflare = exception is CloudflareChallengeException ||
                                        err.contains("cloudflare", ignoreCase = true) ||
                                        err.contains("security", ignoreCase = true) ||
                                        err.contains("turnstile", ignoreCase = true)
                                if (isCloudflare && detectedChallengeProviderId == null) {
                                    detectedChallengeProviderId = provider.id
                                }
                                lastErrorMessage = err
                                StreamHubLogger.w("DashboardRepository", "Provider '${provider.id}' error ($duration ms): $err")
                                flow.update { it.copy(providerStatuses = statuses.toMap()) }
                            }
                        } catch (e: Exception) {
                            statuses[provider.id] = ProviderFetchStatus.ERROR
                            val isCloudflare = e is CloudflareChallengeException ||
                                    e.message?.contains("cloudflare", ignoreCase = true) == true ||
                                    e.message?.contains("security", ignoreCase = true) == true
                            if (isCloudflare && detectedChallengeProviderId == null) {
                                detectedChallengeProviderId = provider.id
                            }
                            lastErrorMessage = e.message
                            StreamHubLogger.e("DashboardRepository", "Exception in provider '${provider.id}': ${e.message}")
                            flow.update { it.copy(providerStatuses = statuses.toMap()) }
                        }
                    }
                }
            }
        }

        // All concurrent providers have completed
        val finalVideos = collectedVideos.toList()
        val now = System.currentTimeMillis()
        lastFetchTimes[categoryId] = now

        // Only overwrite cache if we received items or had empty cache
        if (finalVideos.isNotEmpty() || cache[categoryId] == null) {
            cache[categoryId] = CachedCategoryData(finalVideos, now)
        }

        val videosToDisplay = if (finalVideos.isNotEmpty()) finalVideos else cache[categoryId]?.videos ?: emptyList()

        val finalError = if (videosToDisplay.isEmpty()) {
            if (detectedChallengeProviderId != null) {
                "Cloudflare security verification required for $detectedChallengeProviderId"
            } else {
                lastErrorMessage ?: "Unable to load media feeds. Tap to retry."
            }
        } else null

        flow.update {
            it.copy(
                videos = videosToDisplay,
                isLoading = false,
                isRefreshing = false,
                providerStatuses = statuses.toMap(),
                errorMessage = finalError,
                challengeProviderId = detectedChallengeProviderId,
                lastUpdated = now
            )
        }
    }
}
