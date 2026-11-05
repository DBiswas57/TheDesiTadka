package com.thedesitadka.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thedesitadka.app.navigation.PlaylistNavigationContext
import com.thedesitadka.app.navigation.PlaylistSourceType
import com.thedesitadka.app.navigation.VideoPlaylistHelper
import com.thedesitadka.app.ui.components.EmptyStateView
import com.thedesitadka.app.ui.components.ErrorStateView
import com.thedesitadka.app.ui.components.VideoCard
import com.thedesitadka.app.ui.components.VideoCardSkeleton
import com.thedesitadka.core.model.VideoItem
import com.thedesitadka.provider.ProviderEngine
import com.thedesitadka.provider.SearchEvent
import java.util.Collections
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    var searchQuery by mutableStateOf("")
    var selectedProviderId by mutableStateOf<String?>(null)
    val searchResults = mutableStateListOf<VideoItem>()
    var isLoading by mutableStateOf(false)
    var completedSources by mutableStateOf(0)
    var totalSources by mutableStateOf(0)
    var errorMessage by mutableStateOf<String?>(null)

    private var searchJob: Job? = null
    private val seenKeys = Collections.synchronizedSet(mutableSetOf<String>())

    fun onQueryChanged(query: String, providerEngine: ProviderEngine) {
        searchQuery = query
        triggerSearch(query, selectedProviderId, providerEngine, debounceMs = 300L)
    }

    fun onProviderSelected(providerId: String?, providerEngine: ProviderEngine) {
        selectedProviderId = providerId
        triggerSearch(searchQuery, providerId, providerEngine, debounceMs = 0L)
    }

    fun retrySearch(providerEngine: ProviderEngine) {
        triggerSearch(searchQuery, selectedProviderId, providerEngine, debounceMs = 0L)
    }

    private fun triggerSearch(query: String, providerId: String?, providerEngine: ProviderEngine, debounceMs: Long = 300L) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            searchResults.clear()
            seenKeys.clear()
            isLoading = false
            completedSources = 0
            totalSources = 0
            errorMessage = null
            return
        }

        searchJob = viewModelScope.launch {
            if (debounceMs > 0L) {
                delay(debounceMs)
            }
            isLoading = true
            errorMessage = null
            searchResults.clear()
            seenKeys.clear()
            completedSources = 0
            totalSources = 0

            var foundAny = false

            providerEngine.searchFlow(trimmed, providerId, 1).collect { event ->
                when (event) {
                    is SearchEvent.Progress -> {
                        completedSources = event.completedProviders
                        totalSources = event.totalProviders
                    }
                    is SearchEvent.ProviderResult -> {
                        val newItems = mutableListOf<VideoItem>()
                        for (item in event.items) {
                            val normUrl = VideoPlaylistHelper.normalizeUrl(item.detailUrl)
                            val key = if (normUrl.isNotBlank()) normUrl else "${item.providerId}_${item.id}"
                            if (key.isNotBlank() && seenKeys.add(key)) {
                                newItems.add(item)
                            }
                        }
                        if (newItems.isNotEmpty()) {
                            foundAny = true
                            searchResults.addAll(newItems)
                        }
                    }
                    is SearchEvent.ProviderError -> {
                        if (!foundAny && searchResults.isEmpty() && providerId != null) {
                            errorMessage = event.error?.message ?: "Search failed for ${event.providerName}"
                        }
                    }
                    is SearchEvent.Completed -> {
                        isLoading = false
                    }
                }
            }
            isLoading = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    providerEngine: ProviderEngine,
    onVideoClick: (VideoItem) -> Unit,
    onVideoClickWithPlaylist: ((VideoItem, List<VideoItem>, PlaylistNavigationContext?) -> Unit)? = null,
    onBackClick: () -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val activeProviders = remember { providerEngine.getActiveProviders() }
    val searchQuery = viewModel.searchQuery
    val selectedProviderId = viewModel.selectedProviderId
    val searchResults = viewModel.searchResults
    val isLoading = viewModel.isLoading
    val completedSources = viewModel.completedSources
    val totalSources = viewModel.totalSources
    val errorMessage = viewModel.errorMessage

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                title = { Text("Search Media", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Input Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onQueryChanged(it, providerEngine) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search titles, topics, creators...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChanged("", providerEngine) }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = Color.White)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            // Provider Filter Chips
            if (activeProviders.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedProviderId == null,
                        onClick = { viewModel.onProviderSelected(null, providerEngine) },
                        label = { Text("All Sources") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.Black
                        )
                    )
                    activeProviders.forEach { provider ->
                        val isSelected = selectedProviderId == provider.id
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val next = if (isSelected) null else provider.id
                                viewModel.onProviderSelected(next, providerEngine)
                            },
                            label = { Text(provider.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }
            }

            // Live Search Progress Indicator while search is underway
            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    val progressFraction = if (totalSources > 0) completedSources.toFloat() / totalSources.toFloat() else null
                    if (progressFraction != null) {
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (totalSources > 1) {
                                "Searching ($completedSources/$totalSources sources)..."
                            } else {
                                "Searching source..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (searchResults.isNotEmpty()) {
                            Text(
                                text = "${searchResults.size} loaded",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Results Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 168.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Initial loading skeleton while waiting for the first site to respond
                if (isLoading && searchResults.isEmpty()) {
                    items(6) {
                        VideoCardSkeleton()
                    }
                } else if (!isLoading && errorMessage != null && searchResults.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ErrorStateView(
                            message = errorMessage!!,
                            onRetry = { viewModel.retrySearch(providerEngine) }
                        )
                    }
                } else if (!isLoading && searchQuery.trim().length >= 2 && searchResults.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyStateView(
                            title = "No Results Found",
                            subtitle = "Try broader search keywords or select a different provider."
                        )
                    }
                } else {
                    items(
                        items = searchResults,
                        key = { item -> item.providerId + "_" + (item.detailUrl.ifBlank { item.id }) }
                    ) { item ->
                        VideoCard(
                            videoItem = item,
                            onClick = {
                                if (onVideoClickWithPlaylist != null) {
                                    onVideoClickWithPlaylist(
                                        item,
                                        searchResults.toList(),
                                        PlaylistNavigationContext(
                                            sourceType = PlaylistSourceType.SEARCH,
                                            providerId = selectedProviderId ?: "",
                                            searchQuery = searchQuery,
                                            currentPage = 1,
                                            hasNextPage = false
                                        )
                                    )
                                } else {
                                    onVideoClick(item)
                                }
                            }
                        )
                    }

                    // Background loading indicator at bottom of grid while more sites stream in
                    if (isLoading && searchResults.isNotEmpty()) {
                        items(2) {
                            VideoCardSkeleton()
                        }
                    }
                }
            }
        }
    }
}
