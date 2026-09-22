package com.thedesitadka.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.thedesitadka.app.ui.challenge.CloudflareChallengeActivity
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thedesitadka.app.ui.components.VideoCard
import com.thedesitadka.core.model.VideoItem
import com.thedesitadka.provider.ProviderEngine
import kotlinx.coroutines.launch

import com.thedesitadka.app.storage.NavigationStateStore
import com.thedesitadka.app.storage.SavedListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryContentScreen(
    providerId: String,
    categoryTitle: String,
    categoryUrl: String,
    providerEngine: ProviderEngine,
    onVideoClick: (VideoItem) -> Unit,
    onBackClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val adapter = remember(providerId) { providerEngine.getAdapter(providerId) }
    val info = remember(providerId) { adapter?.providerInfo }
    val cacheKey = remember(providerId, categoryUrl) { "cat_content:$providerId:$categoryUrl" }

    val context = LocalContext.current
    var reloadTrigger by remember { mutableIntStateOf(0) }

    val challengeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            reloadTrigger++
        }
    }

    val cachedState = remember(cacheKey) { NavigationStateStore.get(cacheKey) }
    val videoItems = remember {
        mutableStateListOf<VideoItem>().apply {
            if (cachedState != null && cachedState.items.isNotEmpty()) {
                addAll(cachedState.items)
            }
        }
    }
    var isLoading by remember { mutableStateOf(cachedState == null || cachedState.items.isEmpty()) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableIntStateOf(cachedState?.currentPage ?: 1) }
    var hasNextPage by remember { mutableStateOf(cachedState?.hasNextPage ?: true) }

    val gridState = rememberLazyGridState()

    // Restore scroll position from cache on enter
    LaunchedEffect(cacheKey) {
        if (cachedState != null && (cachedState.firstVisibleItemIndex > 0 || cachedState.firstVisibleItemScrollOffset > 0)) {
            gridState.scrollToItem(cachedState.firstVisibleItemIndex, cachedState.firstVisibleItemScrollOffset)
        }
    }

    // Continuously persist scroll position
    LaunchedEffect(gridState, cacheKey) {
        snapshotFlow { Pair(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) }
            .collect { (idx, offset) ->
                NavigationStateStore.updateScroll(cacheKey, idx, offset)
            }
    }

    fun loadContent(page: Int, reset: Boolean = false) {
        if (!reset && (isLoading || isLoadingMore)) return
        if (!reset && videoItems.isEmpty()) return
        if (reset) {
            isLoading = true
            isLoadingMore = false
            currentPage = 1
        } else {
            isLoadingMore = true
        }
        errorMessage = null

        coroutineScope.launch {
            val result = providerEngine.getCategoryFeed(providerId, categoryUrl, page)
            result.onSuccess { feedPage ->
                if (reset) videoItems.clear()
                val validItems = feedPage.items.filter { it.title.isNotBlank() && it.detailUrl.isNotBlank() }
                val existingUrls = videoItems.map { it.detailUrl }.toSet()
                val uniqueItems = validItems.filter { it.detailUrl !in existingUrls }
                videoItems.addAll(uniqueItems)
                hasNextPage = feedPage.hasNextPage && uniqueItems.isNotEmpty()
                currentPage = page
                isLoading = false
                isLoadingMore = false
                NavigationStateStore.updateItems(cacheKey, videoItems.toList(), page, hasNextPage)
            }.onFailure { err ->
                if (reset) {
                    errorMessage = err.message ?: "Failed to load category videos"
                }
                isLoading = false
                isLoadingMore = false
            }
        }
    }

    LaunchedEffect(providerId, categoryUrl) {
        val existing = NavigationStateStore.get(cacheKey)
        if (existing == null || existing.items.isEmpty()) {
            loadContent(1, reset = true)
        }
    }

    LaunchedEffect(reloadTrigger) {
        if (reloadTrigger > 0) {
            NavigationStateStore.clear(cacheKey)
            loadContent(1, reset = true)
        }
    }

    // Pagination listener
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (videoItems.isNotEmpty() && lastIndex != null && lastIndex >= videoItems.size - 4 && !isLoading && !isLoadingMore && hasNextPage) {
                    loadContent(currentPage + 1, reset = false)
                }
            }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                title = {
                    Column {
                        Text(
                            text = categoryTitle.ifBlank { "Category" },
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp,
                            maxLines = 1
                        )
                        Text(
                            text = info?.name ?: providerId,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val targetUrl = if (categoryUrl.startsWith("http")) categoryUrl else (info?.baseUrl ?: categoryUrl)
                        val intent = CloudflareChallengeActivity.createIntent(
                            context,
                            targetUrl,
                            info?.name ?: providerId
                        )
                        challengeLauncher.launch(intent)
                    }) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = "Security Clearance", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        NavigationStateStore.clear(cacheKey)
                        loadContent(1, reset = true)
                    }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading && videoItems.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading videos...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
                errorMessage != null && videoItems.isEmpty() -> {
                    val isCloudflare = errorMessage?.contains("cloudflare", ignoreCase = true) == true ||
                        errorMessage?.contains("403") == true ||
                        errorMessage?.contains("challenge", ignoreCase = true) == true ||
                        errorMessage?.contains("security", ignoreCase = true) == true

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isCloudflare) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
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
                                        text = "This provider is protected by Cloudflare. Tap below to verify security clearance and unlock category content.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                val targetUrl = if (categoryUrl.startsWith("http")) categoryUrl else (info?.baseUrl ?: categoryUrl)
                                                val intent = CloudflareChallengeActivity.createIntent(
                                                    context,
                                                    targetUrl,
                                                    info?.name ?: providerId
                                                )
                                                challengeLauncher.launch(intent)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Verify Clearance", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { loadContent(1, reset = true) },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Retry", color = Color.White)
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = errorMessage ?: "Failed to load category videos",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { loadContent(1, reset = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                videoItems.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
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
                                    text = "No Videos Visible",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "If this category requires Cloudflare security verification, tap below to solve the challenge in WebView.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            val targetUrl = if (categoryUrl.startsWith("http")) categoryUrl else (info?.baseUrl ?: categoryUrl)
                                            val intent = CloudflareChallengeActivity.createIntent(
                                                context,
                                                targetUrl,
                                                info?.name ?: providerId
                                            )
                                            challengeLauncher.launch(intent)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Verify Clearance", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { loadContent(1, reset = true) },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Reload", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 168.dp),
                        state = gridState,
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 32.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(videoItems, key = { it.id + it.detailUrl }) { video ->
                            VideoCard(
                                videoItem = video,
                                onClick = { onVideoClick(video) }
                            )
                        }

                        if (isLoadingMore && videoItems.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
