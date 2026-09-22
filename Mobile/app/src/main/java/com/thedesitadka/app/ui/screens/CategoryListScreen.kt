package com.thedesitadka.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.thedesitadka.core.model.VideoItem
import com.thedesitadka.provider.ProviderEngine
import kotlinx.coroutines.launch

import com.thedesitadka.app.storage.NavigationStateStore
import com.thedesitadka.app.storage.SavedListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen(
    providerId: String,
    providerEngine: ProviderEngine,
    onCategoryClick: (title: String, url: String) -> Unit,
    onBackClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val adapter = remember(providerId) { providerEngine.getAdapter(providerId) }
    val info = remember(providerId) { adapter?.providerInfo }
    val cacheKey = remember(providerId) { "cat_list:$providerId" }

    val cachedState = remember(cacheKey) { NavigationStateStore.get(cacheKey) }
    val categoryItems = remember {
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

    fun loadCategories(page: Int, reset: Boolean = false) {
        if (!reset && (isLoading || isLoadingMore)) return
        if (reset) {
            isLoading = true
            currentPage = 1
        } else {
            isLoadingMore = true
        }
        errorMessage = null

        coroutineScope.launch {
            try {
                val configuredCategoryPath = adapter?.categoryNavPath?.trim()?.ifBlank { null }
                val categoriesPath = configuredCategoryPath ?: "/categories/"
                val result = providerEngine.getCategoryFeed(providerId, categoriesPath, page)
                result.onSuccess { feedPage ->
                    if (reset) categoryItems.clear()
                    val validItems = feedPage.items.filter {
                        val isAlphabet = it.title.trim().length == 1 && it.title.trim()[0].isLetter()
                        it.title.isNotBlank() &&
                        it.detailUrl.isNotBlank() &&
                        !it.title.matches(Regex("""^\d+$""")) &&
                        (it.title.length > 1 || isAlphabet) &&
                        !it.detailUrl.contains("/page/")
                    }
                    val existingUrls = categoryItems.map { it.detailUrl }.toSet()
                    val uniqueItems = validItems.filter { it.detailUrl !in existingUrls }
                    if (uniqueItems.isNotEmpty()) {
                        categoryItems.addAll(uniqueItems)
                        hasNextPage = feedPage.hasNextPage
                        currentPage = page
                    } else if (reset) {
                        // Fallback to adapter.getCategories() if feed was empty
                        adapter?.getCategories()?.onSuccess { cats ->
                            val mapped = cats.map { cat ->
                                VideoItem(
                                    id = cat.id,
                                    providerId = providerId,
                                    title = cat.name,
                                    detailUrl = cat.url,
                                    isCategory = true
                                )
                            }
                            categoryItems.addAll(mapped)
                            hasNextPage = false
                        }
                    }
                    isLoading = false
                    isLoadingMore = false
                    NavigationStateStore.updateItems(cacheKey, categoryItems.toList(), page, hasNextPage)
                }.onFailure { err ->
                    if (reset) {
                        // Fallback attempt
                        adapter?.getCategories()?.onSuccess { cats ->
                            if (cats.isNotEmpty()) {
                                categoryItems.clear()
                                val mapped = cats.map { cat ->
                                    VideoItem(
                                        id = cat.id,
                                        providerId = providerId,
                                        title = cat.name,
                                        detailUrl = cat.url,
                                        isCategory = true
                                    )
                                }
                                categoryItems.addAll(mapped)
                                hasNextPage = false
                                isLoading = false
                                isLoadingMore = false
                                NavigationStateStore.updateItems(cacheKey, categoryItems.toList(), 1, false)
                                return@launch
                            }
                        }
                        errorMessage = err.message ?: "Failed to load categories"
                    }
                    isLoading = false
                    isLoadingMore = false
                }
            } catch (t: Throwable) {
                errorMessage = t.message ?: "Failed to load categories"
                isLoading = false
                isLoadingMore = false
            }
        }
    }

    LaunchedEffect(providerId) {
        val existing = NavigationStateStore.get(cacheKey)
        if (existing == null || existing.items.isEmpty()) {
            loadCategories(1, reset = true)
        }
    }


    // Pagination listener
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && lastIndex >= categoryItems.size - 4 && !isLoading && !isLoadingMore && hasNextPage) {
                    loadCategories(currentPage + 1, reset = false)
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
                            text = "All Categories",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
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
                        NavigationStateStore.clear(cacheKey)
                        loadCategories(1, reset = true)
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
                isLoading && categoryItems.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading categories...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
                errorMessage != null && categoryItems.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorMessage ?: "Failed to load categories",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { loadCategories(1, reset = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                categoryItems.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No categories found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        state = gridState,
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            categoryItems,
                            key = { index, item ->
                                val base = (item.id.ifBlank { item.title }) + "_" + item.detailUrl
                                "${base}_$index"
                            }
                        ) { _, item ->
                            CategoryCard(
                                title = item.title,
                                thumbnailUrl = item.thumbnailUrl,
                                onClick = { onCategoryClick(item.title, item.detailUrl) }
                            )
                        }

                        if (isLoadingMore) {
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

@Composable
fun CategoryCard(
    title: String,
    thumbnailUrl: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
        ) {
            if (thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Dark gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.2f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // Category Badge top-start
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "CATEGORY",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            // Title bottom
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 13.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            )
        }
    }
}
