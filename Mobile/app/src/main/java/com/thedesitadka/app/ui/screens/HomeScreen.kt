package com.thedesitadka.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thedesitadka.app.R
import com.thedesitadka.app.data.DashboardRepository
import com.thedesitadka.app.storage.PreferenceStore
import com.thedesitadka.app.storage.WatchHistoryEntity
import com.thedesitadka.app.ui.challenge.CloudflareChallengeActivity
import com.thedesitadka.app.ui.components.EmptyStateView
import com.thedesitadka.app.ui.components.ErrorStateView
import com.thedesitadka.app.ui.components.HeroCarousel
import com.thedesitadka.app.ui.components.VideoCard
import com.thedesitadka.app.ui.components.VideoCardSkeleton
import com.thedesitadka.app.ui.theme.LocalAppPalette
import androidx.compose.foundation.border
import com.thedesitadka.core.model.ContentCategoryDefinition
import com.thedesitadka.core.model.ProviderInfo
import com.thedesitadka.core.model.VideoItem
import com.thedesitadka.provider.ProviderEngine
import kotlinx.coroutines.flow.Flow
import com.thedesitadka.app.monetization.AdPlacementType
import com.thedesitadka.app.monetization.MonetizationManager
import com.thedesitadka.app.monetization.ui.AdSlotView
import com.thedesitadka.app.navigation.PlaylistNavigationContext
import com.thedesitadka.app.navigation.PlaylistSourceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    providerEngine: ProviderEngine,
    dashboardRepository: DashboardRepository,
    watchHistoryFlow: Flow<List<WatchHistoryEntity>>,
    preferenceStore: PreferenceStore? = null,
    monetizationManager: MonetizationManager? = null,
    onVideoClick: (VideoItem) -> Unit,
    onVideoClickWithPlaylist: ((VideoItem, List<VideoItem>, PlaylistNavigationContext?) -> Unit)? = null,
    onProviderClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onCustomizeSourcesClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val allProviders = remember { providerEngine.getActiveProviders() }
    val selectedHomeProviders by preferenceStore?.selectedHomeProvidersFlow?.collectAsState(
        initial = emptySet()
    ) ?: remember { mutableStateOf(emptySet()) }

    // Preserve selected category across navigation
    var selectedCategoryId by rememberSaveable { mutableStateOf("all") }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    var providerSearchQuery by remember { mutableStateOf("") }

    // Filter providers based on selected category:
    // "All" -> user's customized home content selection.
    // Other categories -> preset providers for that category, completely independent of customized home content.
    val activeProviders = remember(selectedCategoryId, allProviders, selectedHomeProviders) {
        val isAllCategory = selectedCategoryId.equals("all", ignoreCase = true)
        if (isAllCategory) {
            if (selectedHomeProviders.isNotEmpty()) {
                allProviders.filter { it.id in selectedHomeProviders }
            } else {
                allProviders
            }
        } else {
            val categoryDef = ContentCategoryDefinition.DEFAULT_CATEGORIES.find { it.id.equals(selectedCategoryId, ignoreCase = true) }
            when {
                categoryDef != null && categoryDef.providerIds.isNotEmpty() -> {
                    allProviders.filter { it.id in categoryDef.providerIds }
                }
                selectedCategoryId.equals("downloadable", ignoreCase = true) -> {
                    allProviders.filter { p -> p.capabilities.any { c -> c.name == "DOWNLOAD" } }
                }
                selectedCategoryId.equals("movies", ignoreCase = true) -> {
                    allProviders.filter { it.id in listOf("movienerds", "cineapse", "prmovies", "prmovies_church") }
                }
                else -> allProviders
            }
        }
    }

    val challengeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            reloadTrigger++
        }
    }

    val dashboardState by remember(selectedCategoryId) { dashboardRepository.getDashboardState(selectedCategoryId) }.collectAsState()
    val aggregatedVideos = dashboardState.videos
    val isLoading = dashboardState.isLoading
    val isRefreshing = dashboardState.isRefreshing
    val errorMessage = dashboardState.errorMessage
    var challengeProvider by remember { mutableStateOf<ProviderInfo?>(null) }

    val watchHistory by watchHistoryFlow.collectAsState(initial = emptyList())
    val categories = ContentCategoryDefinition.DEFAULT_CATEGORIES

    val carouselItems = remember(aggregatedVideos) {
        aggregatedVideos
            .filter { it.thumbnailUrl.isNotBlank() }
            .distinctBy { it.id }
            .shuffled()
            .take(10)
    }



    LaunchedEffect(selectedCategoryId, reloadTrigger) {
        if (reloadTrigger > 0) {
            dashboardRepository.refresh(selectedCategoryId)
        } else {
            val state = dashboardRepository.getDashboardState(selectedCategoryId).value
            if (state.videos.isEmpty() && !state.isLoading) {
                dashboardRepository.refresh(selectedCategoryId)
            }
        }
    }

    val gridState = rememberLazyGridState()

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "TheDesiTadka Logo",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "TheDesi",
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontSize = 22.sp
                        )
                        Text(
                            text = "Tadka",
                            fontWeight = FontWeight.Black,
                            color = palette.primary,
                            fontSize = 22.sp
                        )
                    }
                },
                actions = {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            color = palette.primary,
                            modifier = Modifier
                                .size(22.dp)
                                .padding(end = 6.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        IconButton(onClick = { dashboardRepository.refresh(selectedCategoryId) }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Feeds",
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.glassSurface.copy(alpha = 0.95f))
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            state = gridState,
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. Categories Row Filter with 3D Glassmorphic Glow
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategoryId == cat.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) palette.gradientBrush
                                    else Brush.linearGradient(listOf(palette.surfaceVariant, palette.surfaceVariant))
                                )
                                .border(
                                    border = BorderStroke(
                                        width = 1.dp,
                                        brush = if (isSelected) palette.glassBorder
                                        else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.04f)))
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable { selectedCategoryId = cat.id }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cat.name,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 12.sp,
                                color = if (isSelected) palette.onPrimary else Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            // 2. Hero Carousel (Up to 10 items)
            if (carouselItems.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HeroCarousel(
                        items = carouselItems,
                        onWatchClick = {
                            if (onVideoClickWithPlaylist != null) {
                                onVideoClickWithPlaylist(
                                    it,
                                    if (aggregatedVideos.isNotEmpty()) aggregatedVideos else carouselItems,
                                    PlaylistNavigationContext(
                                        sourceType = PlaylistSourceType.DASHBOARD,
                                        categoryUrl = selectedCategoryId
                                    )
                                )
                            } else {
                                onVideoClick(it)
                            }
                        }
                    )
                }
            }

            if (watchHistory.isNotEmpty()) {
                val historyVideos = watchHistory.map {
                    VideoItem(
                        id = it.id,
                        providerId = it.providerId,
                        title = it.title,
                        thumbnailUrl = it.thumbnailUrl,
                        detailUrl = it.detailUrl
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        Text(
                            text = "CONTINUE WATCHING",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(historyVideos) { histVideo ->
                                Box(
                                    modifier = Modifier
                                        .width(180.dp)
                                        .clickable {
                                            if (onVideoClickWithPlaylist != null) {
                                                onVideoClickWithPlaylist(
                                                    histVideo,
                                                    historyVideos,
                                                    PlaylistNavigationContext(
                                                        sourceType = PlaylistSourceType.DASHBOARD
                                                    )
                                                )
                                            } else {
                                                onVideoClick(histVideo)
                                            }
                                        }
                                ) {
                                    VideoCard(
                                        videoItem = histVideo,
                                        onClick = {
                                            if (onVideoClickWithPlaylist != null) {
                                                onVideoClickWithPlaylist(
                                                    histVideo,
                                                    historyVideos,
                                                    PlaylistNavigationContext(
                                                        sourceType = PlaylistSourceType.DASHBOARD
                                                    )
                                                )
                                            } else {
                                                onVideoClick(histVideo)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Dedicated Providers Section with search + alphabetical sort
            if (activeProviders.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val displayedProviders = remember(activeProviders, providerSearchQuery) {
                        activeProviders
                            .filter { providerSearchQuery.isBlank() || it.name.contains(providerSearchQuery, ignoreCase = true) }
                            .sortedBy { it.name.lowercase() }
                    }
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (providerSearchQuery.isBlank()) "MEDIA PROVIDERS (${activeProviders.size})" else "MEDIA PROVIDERS (${displayedProviders.size}/${activeProviders.size})",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                            )
                            // Compact inline search box
                            BasicTextField(
                                value = providerSearchQuery,
                                onValueChange = { providerSearchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontSize = 11.sp
                                ),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .width(130.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (providerSearchQuery.isEmpty()) {
                                            Text(
                                                text = "Search...",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(displayedProviders) { provider ->
                                Card(
                                    modifier = Modifier
                                        .border(
                                            border = BorderStroke(
                                                width = 1.dp,
                                                brush = Brush.linearGradient(
                                                    listOf(
                                                        Color.White.copy(alpha = 0.15f),
                                                        palette.primary.copy(alpha = 0.25f),
                                                        Color.White.copy(alpha = 0.05f)
                                                    )
                                                )
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onProviderClick(provider.id) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = palette.cardSurface)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = provider.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Latest Media Section Header — title with site count + inline Customize button
            item(span = { GridItemSpan(maxLineSpan) }) {
                val contributingProviders = activeProviders
                val isAllCategory = selectedCategoryId.equals("all", ignoreCase = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LATEST MEDIA (${contributingProviders.size})",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    )
                    if (onCustomizeSourcesClick != null && isAllCategory) {
                        Row(
                            modifier = Modifier.clickable { onCustomizeSourcesClick() },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Customize sources",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Customize",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }

            // Loading Skeletons
            if (isLoading && aggregatedVideos.isEmpty()) {
                items(6) {
                    Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                        VideoCardSkeleton()
                    }
                }
            }

            // Cloudflare / Error State
            if (errorMessage != null && aggregatedVideos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val challengeProviderId = dashboardState.challengeProviderId
                    val targetProvider = if (!challengeProviderId.isNullOrBlank()) {
                        activeProviders.find { it.id == challengeProviderId }
                    } else {
                        challengeProvider ?: activeProviders.firstOrNull()
                    }
                    val isCloudflare = challengeProviderId != null || challengeProvider != null ||
                            errorMessage?.contains("Cloudflare security challenge detected", ignoreCase = true) == true ||
                            errorMessage?.contains("Cloudflare security verification required", ignoreCase = true) == true ||
                            errorMessage?.contains("cf-challenge", ignoreCase = true) == true

                    if (isCloudflare) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Security Verification Required",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                val providerDisplayName = targetProvider?.name ?: "A provider"
                                Text(
                                    text = "$providerDisplayName is protected by Cloudflare security. Tap below to complete verification and unlock media.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        val intent = CloudflareChallengeActivity.createIntent(
                                            context,
                                            targetProvider?.baseUrl ?: "https://prmovies.church/",
                                            targetProvider?.name ?: "PRMovies Church"
                                        )
                                        challengeLauncher.launch(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Verify Site Access", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        ErrorStateView(
                            message = errorMessage ?: "Failed to load content",
                            onRetry = { dashboardRepository.refresh(selectedCategoryId) }
                        )
                    }
                }
            }

            // Empty State
            if (!isLoading && aggregatedVideos.isEmpty() && errorMessage == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyStateView(
                        title = "No Content",
                        subtitle = "No media items returned for this category."
                    )
                }
            }

            // Aggregated Media Grid Items
            val remainingVideos = aggregatedVideos.filterNot { it.id in carouselItems.map { c -> c.id } }
                .ifEmpty { aggregatedVideos }
            val batch1 = remainingVideos.take(8)
            val batch2 = remainingVideos.drop(8)

            items(batch1, key = { it.id }) { item ->
                VideoCard(
                    videoItem = item,
                    onClick = {
                        if (onVideoClickWithPlaylist != null) {
                            onVideoClickWithPlaylist(
                                item,
                                if (aggregatedVideos.isNotEmpty()) aggregatedVideos else remainingVideos,
                                PlaylistNavigationContext(
                                    sourceType = PlaylistSourceType.DASHBOARD,
                                    categoryUrl = selectedCategoryId
                                )
                            )
                        } else {
                            onVideoClick(item)
                        }
                    }
                )
            }

            if (monetizationManager != null && batch1.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AdSlotView(
                        placement = AdPlacementType.HOME_FEED,
                        monetizationManager = monetizationManager
                    )
                }
            }

            items(batch2, key = { it.id }) { item ->
                VideoCard(
                    videoItem = item,
                    onClick = {
                        if (onVideoClickWithPlaylist != null) {
                            onVideoClickWithPlaylist(
                                item,
                                if (aggregatedVideos.isNotEmpty()) aggregatedVideos else remainingVideos,
                                PlaylistNavigationContext(
                                    sourceType = PlaylistSourceType.DASHBOARD,
                                    categoryUrl = selectedCategoryId
                                )
                            )
                        } else {
                            onVideoClick(item)
                        }
                    }
                )
            }

            // Pagination loader
            if (isLoading && aggregatedVideos.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

