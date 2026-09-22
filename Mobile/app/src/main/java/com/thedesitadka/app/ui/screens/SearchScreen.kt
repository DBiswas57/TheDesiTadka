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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.thedesitadka.app.ui.components.EmptyStateView
import com.thedesitadka.app.ui.components.ErrorStateView
import com.thedesitadka.app.ui.components.VideoCard
import com.thedesitadka.app.ui.components.VideoCardSkeleton
import com.thedesitadka.core.model.VideoItem
import com.thedesitadka.provider.ProviderEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    var searchQuery by mutableStateOf("")
    var selectedProviderId by mutableStateOf<String?>(null)
    val searchResults = mutableStateListOf<VideoItem>()
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    private var searchJob: Job? = null

    fun onQueryChanged(query: String, providerEngine: ProviderEngine) {
        searchQuery = query
        triggerSearch(query, selectedProviderId, providerEngine)
    }

    fun onProviderSelected(providerId: String?, providerEngine: ProviderEngine) {
        selectedProviderId = providerId
        triggerSearch(searchQuery, providerId, providerEngine)
    }

    fun retrySearch(providerEngine: ProviderEngine) {
        triggerSearch(searchQuery, selectedProviderId, providerEngine, debounceMs = 0L)
    }

    private fun triggerSearch(query: String, providerId: String?, providerEngine: ProviderEngine, debounceMs: Long = 300L) {
        searchJob?.cancel()
        if (query.trim().length < 2) {
            searchResults.clear()
            isLoading = false
            return
        }

        searchJob = viewModelScope.launch {
            if (debounceMs > 0L) {
                delay(debounceMs)
            }
            isLoading = true
            errorMessage = null
            val result = providerEngine.search(query.trim(), providerId, 1)
            result.onSuccess { feedPage ->
                searchResults.clear()
                searchResults.addAll(feedPage.items)
                isLoading = false
            }.onFailure { err ->
                errorMessage = err.message ?: "Search failed"
                isLoading = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    providerEngine: ProviderEngine,
    onVideoClick: (VideoItem) -> Unit,
    onBackClick: () -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val activeProviders = remember { providerEngine.getActiveProviders() }
    val searchQuery = viewModel.searchQuery
    val selectedProviderId = viewModel.selectedProviderId
    val searchResults = viewModel.searchResults
    val isLoading = viewModel.isLoading
    val errorMessage = viewModel.errorMessage

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                title = { Text("Search Media", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
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

            Spacer(modifier = Modifier.height(8.dp))

            // Results Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 168.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (isLoading) {
                    items(4) {
                        VideoCardSkeleton()
                    }
                } else if (errorMessage != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ErrorStateView(
                            message = errorMessage!!,
                            onRetry = { viewModel.retrySearch(providerEngine) }
                        )
                    }
                } else if (searchQuery.length >= 2 && searchResults.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyStateView(
                            title = "No Results Found",
                            subtitle = "Try broader search keywords or select a different provider."
                        )
                    }
                } else {
                    items(searchResults) { item ->
                        VideoCard(
                            videoItem = item,
                            onClick = { onVideoClick(item) }
                        )
                    }
                }
            }
        }
    }
}
