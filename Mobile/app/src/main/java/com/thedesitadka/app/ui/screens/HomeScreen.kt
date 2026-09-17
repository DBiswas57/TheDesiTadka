package com.thedesitadka.app.ui.screens

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thedesitadka.app.R
import com.thedesitadka.app.data.DashboardRepository
import com.thedesitadka.app.storage.WatchHistoryEntity
import com.thedesitadka.app.ui.challenge.CloudflareChallengeActivity
import com.thedesitadka.app.ui.components.EmptyStateView
import com.thedesitadka.app.ui.components.ErrorStateView
import com.thedesitadka.app.ui.components.HeroCarousel
import com.thedesitadka.app.ui.components.VideoCard
import com.thedesitadka.app.ui.components.VideoCardSkeleton
import com.thedesitadka.core.model.ContentCategoryDefinition
import com.thedesitadka.core.model.ProviderInfo
import com.thedesitadka.core.model.VideoItem
import com.thedesitadka.provider.ProviderEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import com.thedesitadka.app.monetization.AdPlacementType
import com.thedesitadka.app.monetization.MonetizationManager
import com.thedesitadka.app.monetization.ui.AdSlotView
import com.thedesitadka.app.update.AppUpdateManager
import com.thedesitadka.app.update.UpdateInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    providerEngine: ProviderEngine,
    dashboardRepository: DashboardRepository,
    watchHistoryFlow: Flow<List<WatchHistoryEntity>>,
    monetizationManager: MonetizationManager? = null,
    onVideoClick: (VideoItem) -> Unit,
    onProviderClick: (String) -> Unit,
    onSearchClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeProviders = remember { providerEngine.getActiveProviders() }

    // Preserve selected category across navigation
    var selectedCategoryId by rememberSaveable { mutableStateOf("all") }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    val challengeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            reloadTrigger++
        }
    }

    val dashboardState by dashboardRepository.getDashboardState(selectedCategoryId).collectAsState()
    val aggregatedVideos = dashboardState.videos
    val isLoading = dashboardState.isLoading
    val isRefreshing = dashboardState.isRefreshing
    val errorMessage = dashboardState.errorMessage
    var challengeProvider by remember { mutableStateOf<ProviderInfo?>(null) }

    val watchHistory by watchHistoryFlow.collectAsState(initial = emptyList())
    val categories = ContentCategoryDefinition.DEFAULT_CATEGORIES

    // Mandatory startup update verification state
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var updateErrorMessage by remember { mutableStateOf<String?>(null) }
    var startupBlockReason by remember { mutableStateOf<String?>(null) }
    var startupCheckTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(startupCheckTrigger) {
        startupBlockReason = null
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = if (network != null) cm.getNetworkCapabilities(network) else null
        val isOnline = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!isOnline) {
            startupBlockReason = "Offline Access Prohibited\n\nTheDesiTadka requires an active internet connection to securely verify application integrity and official release status. Please connect to the internet and retry."
            return@LaunchedEffect
        }

        val result = AppUpdateManager.checkForUpdates()
        result.onSuccess { info ->
            if (info.isUpdateAvailable) {
                updateInfo = info
                showUpdateDialog = true
            } else {
                showUpdateDialog = false
            }
        }.onFailure { err ->
            startupBlockReason = "Security & Update Check Blocked\n\nUnable to reach the official release service (${err.message ?: "Connection Refused"}).\n\nThe update service may be blocked or unreachable. Offline and unverified use is prohibited to protect application security."
        }
    }

    LaunchedEffect(selectedCategoryId, reloadTrigger) {
        if (reloadTrigger > 0) {
            dashboardRepository.refresh(selectedCategoryId)
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
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "TheDesi",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 22.sp
                        )
                        Text(
                            text = "Tadka",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 22.sp
                        )
                    }
                },
                actions = {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 6.dp),
                            strokeWidth = 2.dp
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (startupBlockReason != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Security Verification Required",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = startupBlockReason ?: "",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { startupCheckTrigger++ },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry Verification", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 168.dp),
            state = gridState,
            contentPadding = PaddingValues(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. Categories Row Filter
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategoryId == cat.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.Black,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = Color.White
                            )
                        )
                    }
                }
            }

            // 2. Hero Featured Item
            if (aggregatedVideos.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HeroCarousel(
                        featuredItem = aggregatedVideos.firstOrNull(),
                        onWatchClick = { onVideoClick(it) }
                    )
                }
            }

            // 3. Continue Watching / History Row
            if (watchHistory.isNotEmpty()) {
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
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(watchHistory) { history ->
                                Box(
                                    modifier = Modifier
                                        .width(180.dp)
                                        .clickable {
                                            onVideoClick(
                                                VideoItem(
                                                    id = history.id,
                                                    providerId = history.providerId,
                                                    title = history.title,
                                                    thumbnailUrl = history.thumbnailUrl,
                                                    detailUrl = history.detailUrl
                                                )
                                            )
                                        }
                                ) {
                                    VideoCard(
                                        videoItem = VideoItem(
                                            id = history.id,
                                            providerId = history.providerId,
                                            title = history.title,
                                            thumbnailUrl = history.thumbnailUrl,
                                            detailUrl = history.detailUrl
                                        ),
                                        onClick = {
                                            onVideoClick(
                                                VideoItem(
                                                    id = history.id,
                                                    providerId = history.providerId,
                                                    title = history.title,
                                                    thumbnailUrl = history.thumbnailUrl,
                                                    detailUrl = history.detailUrl
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Dedicated Providers Section (Separate from content items)
            if (activeProviders.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MEDIA PROVIDERS",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                            )
                            Text(
                                text = "${activeProviders.size} Sources",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(activeProviders) { provider ->
                                Card(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .clickable { onProviderClick(provider.id) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Text(
                                            text = provider.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = provider.id.uppercase(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Browse Feed →",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Latest Aggregated Media Section Header
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "LATEST MEDIA",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
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
                    val isCloudflare = challengeProvider != null ||
                            errorMessage?.contains("Cloudflare", ignoreCase = true) == true ||
                            errorMessage?.contains("Security", ignoreCase = true) == true

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
                                Text(
                                    text = "A provider is protected by Cloudflare security. Tap below to complete verification and unlock media.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                val targetProvider = challengeProvider ?: activeProviders.firstOrNull()
                                Button(
                                    onClick = {
                                        val intent = CloudflareChallengeActivity.createIntent(
                                            context,
                                            targetProvider?.baseUrl ?: "https://fry99.cc/",
                                            targetProvider?.name ?: "Provider"
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
            val remainingVideos = aggregatedVideos.drop(1)
            val batch1 = remainingVideos.take(8)
            val batch2 = remainingVideos.drop(8)

            items(batch1, key = { it.id }) { item ->
                Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                    VideoCard(
                        videoItem = item,
                        onClick = { onVideoClick(item) }
                    )
                }
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
                Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                    VideoCard(
                        videoItem = item,
                        onClick = { onVideoClick(item) }
                    )
                }
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

    if (showUpdateDialog && updateInfo != null) {
        val info = updateInfo!!
        val isMandatory = info.isForceUpdate || com.thedesitadka.app.BuildConfig.VERSION_CODE < 3
        AlertDialog(
            onDismissRequest = {
                if (!isMandatory && !isDownloadingUpdate) showUpdateDialog = false
            },
            title = {
                Text(
                    if (isMandatory) "Mandatory Update Required: v${info.latestVersionName}"
                    else "App Update Available: v${info.latestVersionName}"
                )
            },
            text = {
                Column {
                    Text(
                        if (isMandatory) "A required security & content update must be installed to continue using TheDesiTadka."
                        else "A new official release of TheDesiTadka is ready to install.",
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Release: ${info.releaseTitle}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    if (info.releaseNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(info.releaseNotes.take(250), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isDownloadingUpdate) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Downloading and verifying package integrity...", fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = downloadProgress,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (updateErrorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(updateErrorMessage ?: "", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                if (!isDownloadingUpdate) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isDownloadingUpdate = true
                                downloadProgress = 0f
                                updateErrorMessage = null
                                val downloadResult = AppUpdateManager.downloadAndVerifyUpdate(
                                    context = context,
                                    updateInfo = info,
                                    onProgress = { p -> downloadProgress = p }
                                )
                                isDownloadingUpdate = false
                                downloadResult.onSuccess { apkFile ->
                                    showUpdateDialog = false
                                    AppUpdateManager.launchInstallIntent(context, apkFile)
                                }.onFailure { err ->
                                    updateErrorMessage = "Verification failed: ${err.message}"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Download & Install", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (!isMandatory && !isDownloadingUpdate) {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text("Later")
                    }
                }
            }
        )
    }
}

