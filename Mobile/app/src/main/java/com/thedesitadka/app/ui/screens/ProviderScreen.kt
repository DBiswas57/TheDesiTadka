package com.thedesitadka.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.thedesitadka.app.ui.challenge.CloudflareChallengeActivity
import com.thedesitadka.app.ui.components.EmptyStateView
import com.thedesitadka.app.ui.components.ErrorStateView
import com.thedesitadka.app.ui.components.VideoCard
import com.thedesitadka.app.ui.components.VideoCardSkeleton
import com.thedesitadka.core.model.Category
import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.VideoItem
import com.thedesitadka.core.model.isCategoryUrl
import com.thedesitadka.provider.ProviderEngine
import kotlinx.coroutines.launch

import com.thedesitadka.app.storage.NavigationStateStore
import com.thedesitadka.app.storage.SavedListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProviderScreen(
    providerId: String,
    providerEngine: ProviderEngine,
    onVideoClick: (VideoItem) -> Unit,
    onAllCategoriesClick: () -> Unit = {},
    onCategoryItemClick: (title: String, url: String) -> Unit = { _, _ -> },
    onFilterClick: () -> Unit = {},
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val adapter = remember { providerEngine.getAdapter(providerId) }
    val info = remember { adapter?.providerInfo }
    val cacheKey = remember(providerId) { "provider:$providerId" }

    var reloadTrigger by remember { mutableIntStateOf(0) }

    val challengeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            reloadTrigger++
        }
    }

    val cachedState = remember(cacheKey) { NavigationStateStore.get(cacheKey) }

    var feedItems = remember {
        mutableStateListOf<VideoItem>().apply {
            if (cachedState != null && cachedState.items.isNotEmpty()) {
                addAll(cachedState.items)
            }
        }
    }
    var categories = remember {
        mutableStateListOf<Category>().apply {
            if (cachedState != null && cachedState.categories.isNotEmpty()) {
                addAll(cachedState.categories)
            }
        }
    }
    var selectedCategory by remember { mutableStateOf<Category?>(cachedState?.selectedCategory) }
    var isLoading by remember { mutableStateOf(cachedState == null || cachedState.items.isEmpty()) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableIntStateOf(cachedState?.currentPage ?: 1) }
    var hasNextPage by remember { mutableStateOf(cachedState?.hasNextPage ?: true) }

    var searchQuery by rememberSaveable { mutableStateOf(cachedState?.searchQuery ?: "") }
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            delay(150)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
    }

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
        if (!reset && feedItems.isEmpty()) return
        if (reset) {
            isLoading = true
            isLoadingMore = false
            currentPage = 1
        } else {
            isLoadingMore = true
        }
        errorMessage = null
        coroutineScope.launch {
            val result = if (searchQuery.isNotBlank()) {
                adapter?.search(searchQuery.trim(), page) ?: providerEngine.search(searchQuery.trim(), providerId, page)
            } else if (selectedCategory != null) {
                if (selectedCategory!!.url.isNotBlank()) {
                    providerEngine.getCategoryFeed(providerId, selectedCategory!!.url, page)
                } else {
                    adapter?.search(selectedCategory!!.name, page) ?: providerEngine.getHomeFeed(providerId, page)
                }
            } else {
                providerEngine.getHomeFeed(providerId, page)
            }
            result.onSuccess { feedPage ->
                if (reset) feedItems.clear()
                val validItems = feedPage.items.filter { it.title.isNotBlank() && it.detailUrl.isNotBlank() }
                val existingUrls = feedItems.map { it.detailUrl }.toSet()
                val uniqueNewItems = validItems.filter { it.detailUrl !in existingUrls }
                feedItems.addAll(uniqueNewItems)
                hasNextPage = feedPage.hasNextPage && uniqueNewItems.isNotEmpty()
                currentPage = page
                isLoading = false
                isLoadingMore = false
                NavigationStateStore.updateItems(
                    key = cacheKey,
                    items = feedItems.toList(),
                    page = page,
                    hasNext = hasNextPage,
                    categories = categories.toList(),
                    selectedCategory = selectedCategory,
                    searchQuery = searchQuery
                )
            }.onFailure { err ->
                if (reset) {
                    errorMessage = err.message ?: "Failed to load content"
                }
                isLoading = false
                isLoadingMore = false
            }
        }
    }

    LaunchedEffect(providerId) {
        val existing = NavigationStateStore.get(cacheKey)
        if (existing == null || existing.items.isEmpty()) {
            loadContent(1, reset = true)
        }
        if (categories.isEmpty()) {
            adapter?.let { adp ->
                if (adp.hasCapability(ProviderCapability.CATEGORY)) {
                    adp.getCategories().onSuccess { cats ->
                        categories.clear()
                        categories.addAll(cats)
                        NavigationStateStore.updateItems(
                            key = cacheKey,
                            items = feedItems.toList(),
                            page = currentPage,
                            hasNext = hasNextPage,
                            categories = cats,
                            selectedCategory = selectedCategory,
                            searchQuery = searchQuery
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(selectedCategory) {
        if (searchQuery.isBlank() && feedItems.isEmpty()) {
            loadContent(1, reset = true)
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            searchJob?.cancel()
            searchJob = coroutineScope.launch {
                delay(400)
                loadContent(1, reset = true)
            }
        } else if (isSearchExpanded) {
            loadContent(1, reset = true)
        }
    }

    // Re-load after Cloudflare challenge is solved
    LaunchedEffect(reloadTrigger) {
        if (reloadTrigger > 0) {
            NavigationStateStore.clear(cacheKey)
            loadContent(1, reset = true)
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (feedItems.isNotEmpty() && lastIndex != null && lastIndex >= feedItems.size - 4 && !isLoading && !isLoadingMore && hasNextPage) {
                    loadContent(currentPage + 1, reset = false)
                }
            }
    }


    BackHandler(enabled = isSearchExpanded || searchQuery.isNotBlank()) {
        keyboardController?.hide()
        searchQuery = ""
        isSearchExpanded = false
        loadContent(1, reset = true)
    }

    Scaffold(
        topBar = {
            if (isSearchExpanded) {
                TopAppBar(
                    windowInsets = TopAppBarDefaults.windowInsets,
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .focusRequester(focusRequester),
                            placeholder = { Text("Search ${info?.name ?: providerId}...", fontSize = 14.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    keyboardController?.hide()
                                    if (searchQuery.isNotBlank()) {
                                        loadContent(1, reset = true)
                                    }
                                }
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        loadContent(1, reset = true)
                                    }) {
                                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = Color.White)
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            keyboardController?.hide()
                            searchQuery = ""
                            isSearchExpanded = false
                            loadContent(1, reset = true)
                        }) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            keyboardController?.hide()
                            if (searchQuery.isNotBlank()) {
                                loadContent(1, reset = true)
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            } else {
                TopAppBar(
                    windowInsets = TopAppBarDefaults.windowInsets,
                    title = { Text(info?.name ?: "Provider", fontWeight = FontWeight.Bold, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            try {
                                val hasCategoryCap = adapter?.hasCapability(ProviderCapability.CATEGORY) == true
                                val catNav = adapter?.categoryNavPath?.trim()
                                if (categories.isNotEmpty() || hasCategoryCap || !catNav.isNullOrBlank()) {
                                    onAllCategoriesClick()
                                } else {
                                    android.widget.Toast.makeText(context, "No categories found", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Throwable) {
                                android.widget.Toast.makeText(context, "No categories found", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(imageVector = Icons.Default.GridView, contentDescription = "All Categories", tint = Color.White)
                        }
                        if (providerId.startsWith("prmovies")) {
                            IconButton(onClick = onFilterClick) {
                                Icon(imageVector = Icons.Default.FilterList, contentDescription = "Advanced Filter", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = { isSearchExpanded = true }) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search provider", tint = Color.White)
                        }
                        IconButton(onClick = {
                            val targetUrl = info?.baseUrl ?: "https://brazzpw.xyz/"
                            val intent = CloudflareChallengeActivity.createIntent(
                                context,
                                targetUrl,
                                info?.name ?: providerId
                            )
                            challengeLauncher.launch(intent)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Verify Security Clearance",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
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
            // Provider Header Info
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp)
                ) {
                    Text(
                        text = info?.name ?: providerId,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Official Media Source",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    if (!info?.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = info?.description ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    // Capability badges
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        info?.capabilities?.forEach { cap ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.4f))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = cap.name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            // Categories Filter Bar (only show when provider has categories available)
            if (categories.isNotEmpty()) {
                val hasAlphabetJump = categories.any { it.name.trim().length == 1 && it.name.trim()[0].isLetter() }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasAlphabetJump) {
                            Text(
                                text = "Quick jump:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = {
                                if (selectedCategory != null) {
                                    selectedCategory = null
                                    loadContent(1, reset = true)
                                }
                            },
                            label = { Text("All", fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.Black
                            )
                        )
                        categories.forEach { cat ->
                            val catName = cat.name.trim()
                            val isAlphabet = catName.length == 1 && catName[0].isLetter()
                            val isExcluded = !isAlphabet && (
                                catName.equals("All Categories", ignoreCase = true) ||
                                catName.equals("All", ignoreCase = true) ||
                                cat.url.endsWith("/categories/") ||
                                catName.matches(Regex("""^\d+$""")) ||
                                catName.length <= 1 ||
                                cat.url.contains("/page/")
                            )
                            if (!isExcluded) {
                                FilterChip(
                                    selected = selectedCategory?.id == cat.id,
                                    onClick = {
                                        if (catName.equals("All", ignoreCase = true)) {
                                            if (selectedCategory != null) {
                                                selectedCategory = null
                                                loadContent(1, reset = true)
                                            }
                                        } else if (isAlphabet) {
                                            selectedCategory = cat
                                            loadContent(1, reset = true)
                                        } else if (isCategoryUrl(cat.url)) {
                                            onCategoryItemClick(cat.name, cat.url)
                                        } else {
                                            selectedCategory = cat
                                            loadContent(1, reset = true)
                                        }
                                    },
                                    label = { Text(cat.name, fontWeight = FontWeight.SemiBold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Loading Skeletons
            if (isLoading && feedItems.isEmpty() && errorMessage == null) {
                items(6) {
                    Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                        VideoCardSkeleton()
                    }
                }
            }

            // Error State
            if (errorMessage != null && feedItems.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val isCloudflare = errorMessage?.contains("Cloudflare", ignoreCase = true) == true ||
                        errorMessage?.contains("403", ignoreCase = true) == true ||
                        errorMessage?.contains("challenge", ignoreCase = true) == true ||
                        errorMessage?.contains("security", ignoreCase = true) == true

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
                                    text = "This provider is protected by Cloudflare security. Tap below to complete the human verification and unlock access.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        val targetUrl = info?.baseUrl ?: "https://fry99.cc/"
                                        val intent = CloudflareChallengeActivity.createIntent(
                                            context,
                                            targetUrl,
                                            info?.name ?: "Provider"
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
                            message = errorMessage!!,
                            onRetry = { loadContent(1, reset = true) }
                        )
                    }
                }
            }

            // Empty State
            if (!isLoading && feedItems.isEmpty() && errorMessage == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (searchQuery.isNotBlank()) {
                        EmptyStateView(
                            title = "No Results Found",
                            subtitle = "No videos matched \"$searchQuery\" on ${info?.name ?: providerId}."
                        )
                    } else {
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
                                    text = "No Content Visible",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "If this provider requires security verification or Cloudflare clearance, tap below to solve the challenge in WebView and unlock content.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            val targetUrl = info?.baseUrl ?: "https://brazzpw.xyz/"
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
                                        Text("Verify Security Clearance", color = Color.Black, fontWeight = FontWeight.Bold)
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
            }

            // Feed Items Grid
            items(feedItems) { item ->
                VideoCard(
                    videoItem = item,
                    onClick = { onVideoClick(item) }
                )
            }

            // Bottom loading indicator
            if (isLoadingMore && feedItems.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}
