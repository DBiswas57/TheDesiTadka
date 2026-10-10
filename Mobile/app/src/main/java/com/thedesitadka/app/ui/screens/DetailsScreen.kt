package com.thedesitadka.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Verified
import com.thedesitadka.app.download.StoragePermissionHelper
import com.thedesitadka.app.download.NotificationPermissionHelper
import com.thedesitadka.app.storage.DownloadStatus
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.thedesitadka.core.model.MediaResolutionResult
import com.thedesitadka.core.model.MediaResolutionState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.thedesitadka.app.download.DownloadRepository
import com.thedesitadka.app.storage.FavoriteDao
import com.thedesitadka.app.storage.FavoriteEntity
import com.thedesitadka.app.ui.components.VideoCard
import com.thedesitadka.core.model.DownloadAvailability
import com.thedesitadka.core.model.MediaSource
import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.model.VideoItem
import com.thedesitadka.app.monetization.AdPlacementType
import com.thedesitadka.app.monetization.MonetizationManager
import com.thedesitadka.app.monetization.ui.AdSlotView
import com.thedesitadka.provider.ProviderEngine
import android.app.Activity
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.style.TextAlign
import com.thedesitadka.app.ui.challenge.CloudflareChallengeActivity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    videoItem: VideoItem,
    providerEngine: ProviderEngine,
    favoriteDao: FavoriteDao,
    downloadRepository: DownloadRepository,
    monetizationManager: MonetizationManager? = null,
    onOpenDownloadsClick: () -> Unit = {},
    onNextVideoClick: ((VideoItem) -> Boolean)? = null,
    onPreviousVideoClick: ((VideoItem) -> Boolean)? = null,
    onPlaylistUpdated: ((List<VideoItem>) -> Unit)? = null,
    onPlayClick: (VideoItem, MediaSource) -> Unit,
    onPlayClickWithPlaylist: ((VideoItem, MediaSource, List<VideoItem>) -> Unit)? = null,
    onRelatedClick: (VideoItem) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val adapter = remember { providerEngine.getAdapter(videoItem.providerId) }

    var reloadTrigger by remember { mutableIntStateOf(0) }
    val challengeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            reloadTrigger++
        }
    }

    var pendingDownloadAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (StoragePermissionHelper.hasFullStorageAccess(context)) {
            Toast.makeText(context, "Storage access granted. Starting download...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Saving to public Movies/TheDesiTadka storage...", Toast.LENGTH_SHORT).show()
        }
        pendingDownloadAction?.invoke()
        pendingDownloadAction = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pendingDownloadAction?.invoke()
        } else {
            Toast.makeText(context, "Download cancelled: Storage permission is required to save media", Toast.LENGTH_SHORT).show()
        }
        pendingDownloadAction = null
    }

    val proceedWithStorageAndDownload: (() -> Unit) -> Unit = { action ->
        if (StoragePermissionHelper.hasFullStorageAccess(context) || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            action()
        } else {
            pendingDownloadAction = action
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val action = pendingDownloadAction
        pendingDownloadAction = null
        if (action != null) {
            proceedWithStorageAndDownload(action)
        }
    }

    val requestPermissionAndDownload: (() -> Unit) -> Unit = { action ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationPermissionHelper.hasNotificationPermission(context)
        ) {
            pendingDownloadAction = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            proceedWithStorageAndDownload(action)
        }
    }

    var resolvedItem by remember { mutableStateOf(videoItem) }
    var mediaSources = remember { mutableStateListOf<MediaSource>() }
    var relatedItems = remember { mutableStateListOf<VideoItem>() }
    var isLoadingMedia by remember { mutableStateOf(true) }
    var mediaError by remember { mutableStateOf<String?>(null) }
    var currentRequestId by remember { mutableStateOf(0L) }
    var resolutionResult by remember { mutableStateOf<MediaResolutionResult?>(null) }
    var mediaResolutionState by remember { mutableStateOf(MediaResolutionState.UNKNOWN) }

    var selectedSeasonNumber by remember { mutableIntStateOf(1) }
    var selectedEpisodeNumber by remember { mutableIntStateOf(1) }
    var activeStreamUrl by remember { mutableStateOf(videoItem.detailUrl) }

    var selectedLanguage by remember { mutableStateOf<String?>(null) }
    var selectedQuality by remember { mutableStateOf<String?>(null) }
    var selectedServer by remember { mutableStateOf<String?>(null) }

    val isFavorite by favoriteDao.isFavorite(videoItem.id).collectAsState(initial = false)
    val isCollection = mediaSources.isEmpty() && relatedItems.isNotEmpty()

    // 1. Fetch details & related content
    LaunchedEffect(videoItem.id, videoItem.detailUrl, reloadTrigger) {
        val reqId = System.currentTimeMillis()
        currentRequestId = reqId
        mediaResolutionState = MediaResolutionState.RESOLVING
        resolutionResult = MediaResolutionResult.resolving(videoItem.id, videoItem.providerId)
        isLoadingMedia = true
        mediaError = null
        relatedItems.clear()

        // Fetch details
        val detailResult = adapter?.getDetails(videoItem.detailUrl)
        if (currentRequestId != reqId) return@LaunchedEffect

        detailResult?.onSuccess { detail ->
            if (currentRequestId != reqId) return@onSuccess
            val reliableThumb = if (videoItem.thumbnailUrl.isNotBlank() && !isPlaceholderOrLogo(videoItem.thumbnailUrl)) {
                videoItem.thumbnailUrl
            } else if (detail.thumbnailUrl.isNotBlank() && !isPlaceholderOrLogo(detail.thumbnailUrl)) {
                detail.thumbnailUrl
            } else {
                videoItem.thumbnailUrl
            }
            resolvedItem = detail.copy(thumbnailUrl = reliableThumb)

            val isSeriesProvider = videoItem.providerId in listOf("movienerds", "cineapse", "prmovies_church")
            if (isSeriesProvider && detail.isSeries && detail.seasons.isNotEmpty()) {
                val firstSeason = detail.seasons.firstOrNull { it.episodes.isNotEmpty() } ?: detail.seasons.first()
                selectedSeasonNumber = firstSeason.seasonNumber
                val firstEp = firstSeason.episodes.firstOrNull()
                if (firstEp != null) {
                    selectedEpisodeNumber = firstEp.episodeNumber
                    activeStreamUrl = firstEp.streamUrl.ifBlank { firstEp.detailUrl.ifBlank { videoItem.detailUrl } }
                }
            } else {
                activeStreamUrl = videoItem.detailUrl
            }
        }

        // Fetch related content / category collection videos
        val relResult = adapter?.getRelatedContent(videoItem.detailUrl)
        if (currentRequestId != reqId) return@LaunchedEffect

        relResult?.onSuccess { rel ->
            if (currentRequestId != reqId) return@onSuccess
            relatedItems.clear()
            relatedItems.addAll(rel)
            if (rel.isNotEmpty()) {
                onPlaylistUpdated?.invoke(listOf(videoItem) + rel)
            }
        }
    }

    // 2. Fetch playable media sources for active stream URL (supports episodes and quality changes)
    LaunchedEffect(activeStreamUrl, reloadTrigger) {
        if (activeStreamUrl.isBlank()) return@LaunchedEffect
        val reqId = System.currentTimeMillis()
        currentRequestId = reqId
        mediaResolutionState = MediaResolutionState.RESOLVING
        resolutionResult = MediaResolutionResult.resolving(videoItem.id, videoItem.providerId)
        isLoadingMedia = true
        mediaError = null
        mediaSources.clear()

        val mediaResult = adapter?.getPlayableMedia(activeStreamUrl)
        if (currentRequestId != reqId) return@LaunchedEffect

        mediaResult?.onSuccess { sources ->
            if (currentRequestId != reqId) return@onSuccess
            mediaSources.clear()
            mediaSources.addAll(sources)
            isLoadingMedia = false

            if (sources.isNotEmpty()) {
                val isSeriesProvider = videoItem.providerId in listOf("movienerds", "cineapse", "prmovies_church")
                val availableLanguages = if (isSeriesProvider) {
                    sources.mapNotNull { it.language.takeIf { l -> l.isNotBlank() } }.distinct()
                } else emptyList()

                val availableQualities = sources.mapNotNull {
                    it.resolution?.takeIf { r -> r.isNotBlank() }
                        ?: it.quality.takeIf { q -> q.isNotBlank() && (q.contains("p", ignoreCase = true) || q.contains("4k", ignoreCase = true) || q.contains("hd", ignoreCase = true)) }
                }.distinct()

                val availableServers = sources.mapNotNull { it.metadata["server"]?.takeIf { s -> s.isNotBlank() } }.distinct()

                if (availableLanguages.size > 1 && (selectedLanguage == null || selectedLanguage !in availableLanguages)) {
                    selectedLanguage = availableLanguages.firstOrNull()
                } else if (availableLanguages.size <= 1) {
                    selectedLanguage = null
                }

                if (availableQualities.size > 1 && (selectedQuality == null || selectedQuality !in availableQualities)) {
                    selectedQuality = availableQualities.firstOrNull { it.contains("1080") }
                        ?: availableQualities.firstOrNull { it.contains("720") }
                        ?: availableQualities.firstOrNull()
                } else if (availableQualities.size <= 1) {
                    selectedQuality = null
                }

                if (availableServers.size > 1 && (selectedServer == null || selectedServer !in availableServers)) {
                    selectedServer = availableServers.firstOrNull()
                } else if (availableServers.size <= 1) {
                    selectedServer = null
                }
            }
        }?.onFailure { err ->
            if (currentRequestId != reqId) return@onFailure
            mediaError = err.message ?: "Failed to resolve media stream"
            resolutionResult = MediaResolutionResult.error(videoItem.id, videoItem.providerId, mediaError ?: "Error")
            mediaResolutionState = MediaResolutionState.ERROR
            isLoadingMedia = false
        }
    }

    val activeSource: MediaSource? = remember(mediaSources.toList(), selectedLanguage, selectedQuality, selectedServer) {
        if (mediaSources.isEmpty()) return@remember null
        val matched = mediaSources.filter { src ->
            (selectedLanguage == null || src.language.isBlank() || src.language.equals(selectedLanguage, ignoreCase = true) || src.metadata["audio"]?.equals(selectedLanguage, ignoreCase = true) == true) &&
            (selectedQuality == null || (if (!src.resolution.isNullOrBlank()) src.resolution.equals(selectedQuality, ignoreCase = true) else (src.quality.equals(selectedQuality, ignoreCase = true) || src.quality.contains(selectedQuality!!, ignoreCase = true)))) &&
            (selectedServer == null || src.metadata["server"].isNullOrBlank() || src.metadata["server"]?.equals(selectedServer, ignoreCase = true) == true)
        }
        matched.firstOrNull() ?: mediaSources.firstOrNull()
    }

    LaunchedEffect(activeSource, mediaSources.size, isLoadingMedia) {
        if (activeSource != null) {
            val isProviderDownloadAuthorized = adapter?.hasCapability(ProviderCapability.DOWNLOAD) == true
            val res = MediaResolutionResult.fromMediaSource(
                videoId = videoItem.id,
                providerId = videoItem.providerId,
                source = activeSource,
                isProviderDownloadAuthorized = isProviderDownloadAuthorized,
                title = resolvedItem.title
            )
            resolutionResult = res
            mediaResolutionState = res.state
        } else if (mediaSources.isEmpty() && !isLoadingMedia) {
            resolutionResult = MediaResolutionResult.unavailable(videoItem.id, videoItem.providerId, "No media source available")
            mediaResolutionState = MediaResolutionState.UNAVAILABLE
        }
    }

    var isDownloadResolving by remember { mutableStateOf(false) }

    val allDownloads by downloadRepository.getAllDownloads().collectAsState(initial = emptyList())
    val activeDownloadsCount = remember(allDownloads) {
        allDownloads.count {
            it.status == DownloadStatus.DOWNLOADING ||
            it.status == DownloadStatus.QUEUED ||
            it.status == DownloadStatus.RETRYING
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var swipeDragX by remember { mutableFloatStateOf(0f) }

    val handlePlay: (VideoItem, MediaSource) -> Unit = { item, src ->
        val playlist = if (resolvedItem.isSeries && resolvedItem.seasons.isNotEmpty()) {
            resolvedItem.seasons.flatMap { season ->
                season.episodes.map { ep ->
                    val epTitleClean = ep.title.removePrefix("Episode ${ep.episodeNumber}").trim().ifBlank { ep.title }
                    resolvedItem.copy(
                        id = "${resolvedItem.id}_s${season.seasonNumber}_e${ep.episodeNumber}",
                        title = "${resolvedItem.title} - S${season.seasonNumber}E${ep.episodeNumber}: $epTitleClean",
                        detailUrl = ep.streamUrl.ifBlank { ep.detailUrl.ifBlank { resolvedItem.detailUrl } }
                    )
                }
            }
        } else {
            emptyList()
        }
        if (onPlayClickWithPlaylist != null) {
            onPlayClickWithPlaylist(item, src, playlist)
        } else {
            onPlayClick(item, src)
        }
    }

    fun navigateToNextVideo() {
        // 1. If it's a series, prioritize moving to next episode
        if (resolvedItem.isSeries && resolvedItem.seasons.isNotEmpty()) {
            val currentSeason = resolvedItem.seasons.firstOrNull { it.seasonNumber == selectedSeasonNumber }
            val currentEpIndex = currentSeason?.episodes?.indexOfFirst { it.episodeNumber == selectedEpisodeNumber } ?: -1
            if (currentSeason != null && currentEpIndex in 0 until currentSeason.episodes.size - 1) {
                val nextEp = currentSeason.episodes[currentEpIndex + 1]
                selectedEpisodeNumber = nextEp.episodeNumber
                activeStreamUrl = nextEp.streamUrl.ifBlank { nextEp.detailUrl.ifBlank { videoItem.detailUrl } }
                Toast.makeText(context, "Playing Episode ${nextEp.episodeNumber}: ${nextEp.title}", Toast.LENGTH_SHORT).show()
                return
            } else {
                val nextSeason = resolvedItem.seasons.firstOrNull { it.seasonNumber == selectedSeasonNumber + 1 }
                val nextEp = nextSeason?.episodes?.firstOrNull()
                if (nextSeason != null && nextEp != null) {
                    selectedSeasonNumber = nextSeason.seasonNumber
                    selectedEpisodeNumber = nextEp.episodeNumber
                    activeStreamUrl = nextEp.streamUrl.ifBlank { nextEp.detailUrl.ifBlank { videoItem.detailUrl } }
                    Toast.makeText(context, "Playing Season ${nextSeason.seasonNumber} Episode ${nextEp.episodeNumber}: ${nextEp.title}", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }

        // 2. Playlist navigation from MainActivity
        if (onNextVideoClick != null && onNextVideoClick(videoItem)) {
            return
        }

        // 3. Fallback to related items if available
        if (relatedItems.isNotEmpty()) {
            val currentIndex = relatedItems.indexOfFirst { it.id == videoItem.id || it.detailUrl == videoItem.detailUrl }
            val nextRel = if (currentIndex in 0 until relatedItems.size - 1) {
                relatedItems[currentIndex + 1]
            } else if (currentIndex == -1) {
                relatedItems.first()
            } else {
                null
            }
            if (nextRel != null) {
                Toast.makeText(context, "Next: ${nextRel.title}", Toast.LENGTH_SHORT).show()
                onRelatedClick(nextRel)
                return
            }
        }

        // 4. No next video available
        Toast.makeText(context, "No next video available", Toast.LENGTH_SHORT).show()
    }

    fun navigateToPreviousVideo() {
        // 1. If it's a series, prioritize moving to previous episode
        if (resolvedItem.isSeries && resolvedItem.seasons.isNotEmpty()) {
            val currentSeason = resolvedItem.seasons.firstOrNull { it.seasonNumber == selectedSeasonNumber }
            val currentEpIndex = currentSeason?.episodes?.indexOfFirst { it.episodeNumber == selectedEpisodeNumber } ?: -1
            if (currentSeason != null && currentEpIndex > 0) {
                val prevEp = currentSeason.episodes[currentEpIndex - 1]
                selectedEpisodeNumber = prevEp.episodeNumber
                activeStreamUrl = prevEp.streamUrl.ifBlank { prevEp.detailUrl.ifBlank { videoItem.detailUrl } }
                Toast.makeText(context, "Playing Episode ${prevEp.episodeNumber}: ${prevEp.title}", Toast.LENGTH_SHORT).show()
                return
            } else if (selectedSeasonNumber > 1) {
                val prevSeason = resolvedItem.seasons.firstOrNull { it.seasonNumber == selectedSeasonNumber - 1 }
                val prevEp = prevSeason?.episodes?.lastOrNull()
                if (prevSeason != null && prevEp != null) {
                    selectedSeasonNumber = prevSeason.seasonNumber
                    selectedEpisodeNumber = prevEp.episodeNumber
                    activeStreamUrl = prevEp.streamUrl.ifBlank { prevEp.detailUrl.ifBlank { videoItem.detailUrl } }
                    Toast.makeText(context, "Playing Season ${prevSeason.seasonNumber} Episode ${prevEp.episodeNumber}: ${prevEp.title}", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }

        // 2. Playlist navigation from MainActivity
        if (onPreviousVideoClick != null && onPreviousVideoClick(videoItem)) {
            return
        }

        // 3. Fallback to related items if available
        if (relatedItems.isNotEmpty()) {
            val currentIndex = relatedItems.indexOfFirst { it.id == videoItem.id || it.detailUrl == videoItem.detailUrl }
            if (currentIndex > 0) {
                val prevRel = relatedItems[currentIndex - 1]
                Toast.makeText(context, "Previous: ${prevRel.title}", Toast.LENGTH_SHORT).show()
                onRelatedClick(prevRel)
                return
            }
        }

        // 4. No previous video available
        Toast.makeText(context, "No previous video available", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                title = { Text(resolvedItem.title, maxLines = 1, fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Quick Downloads Access with Active Downloads Badge
                    IconButton(onClick = onOpenDownloadsClick) {
                        BadgedBox(
                            badge = {
                                if (activeDownloadsCount > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                        Text(
                                            text = "$activeDownloadsCount",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "View Downloads",
                                tint = Color.White
                            )
                        }
                    }

                    IconButton(onClick = {
                        val intent = CloudflareChallengeActivity.createIntent(
                            context,
                            videoItem.detailUrl,
                            resolvedItem.title
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
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        val isResolving = mediaResolutionState == MediaResolutionState.RESOLVING
        val canPlay = !isCollection && (mediaResolutionState == MediaResolutionState.PLAYABLE || mediaResolutionState == MediaResolutionState.DOWNLOADABLE) && resolutionResult != null

        val currentSeasonObj = resolvedItem.seasons.firstOrNull { it.seasonNumber == selectedSeasonNumber }
        val currentEpObj = currentSeasonObj?.episodes?.firstOrNull { it.episodeNumber == selectedEpisodeNumber }
        val currentActiveItem = if (resolvedItem.isSeries && currentEpObj != null) {
            val epTitleClean = currentEpObj.title.removePrefix("Episode $selectedEpisodeNumber").trim().ifBlank { currentEpObj.title }
            resolvedItem.copy(
                title = "${resolvedItem.title} - S${selectedSeasonNumber}E${selectedEpisodeNumber}: $epTitleClean"
            )
        } else {
            resolvedItem
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Upper Video Preview & Quick Playlist Navigation with Horizontal Swipe Detection
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(currentActiveItem.id, selectedEpisodeNumber) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startX = down.position.x
                            val startY = down.position.y
                            var lastX = startX
                            var lastY = startY

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                lastX = change.position.x
                                lastY = change.position.y
                                if (change.changedToUp()) {
                                    val wasConsumedByChild = change.isConsumed
                                    if (!wasConsumedByChild) {
                                        val totalDx = lastX - startX
                                        val totalDy = lastY - startY
                                        val absDx = kotlin.math.abs(totalDx)
                                        val absDy = kotlin.math.abs(totalDy)
                                        val threshold = 70f
                                        if (absDx >= threshold && absDx > absDy * 0.7f) {
                                            change.consume()
                                            if (totalDx < 0) {
                                                navigateToNextVideo()
                                            } else {
                                                navigateToPreviousVideo()
                                            }
                                        }
                                    }
                                    break
                                }
                            }
                        }
                    }
            ) {
                // Large Backdrop / Video Preview with Play Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    if (resolvedItem.thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(resolvedItem.thumbnailUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                )
                            )
                    )

                    // Central Play Overlay
                    if (canPlay && resolutionResult != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { handlePlay(currentActiveItem, activeSource ?: resolutionResult!!.toMediaSource()) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    } else if (!isCollection && isResolving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                // Quick Playlist Navigation Row (Previous / Swipe hint / Next)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { navigateToPreviousVideo() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Previous", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    Text(
                        text = "⇄ Swipe left/right to change",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    TextButton(
                        onClick = { navigateToNextVideo() }
                    ) {
                        Text("Next", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Info & Metadata
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = resolvedItem.providerId.uppercase(),
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Legal Compliance / Collection Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background((if (isCollection) MaterialTheme.colorScheme.primary else Color(0xFF10B981)).copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = if (isCollection) MaterialTheme.colorScheme.primary else Color(0xFF10B981),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isCollection) "Category Collection" else "Authorized Public Source",
                            color = if (isCollection) MaterialTheme.colorScheme.primary else Color(0xFF10B981),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = resolvedItem.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )

                // Movie & Series Interactive Controls: ONLY for the 3 authorized series providers (movienerds, cineapse, prmovies_church)
                val isSeriesProvider = videoItem.providerId in listOf("movienerds", "cineapse", "prmovies_church")
                val isTrueSeries = isSeriesProvider && resolvedItem.isSeries && resolvedItem.seasons.any { it.episodes.isNotEmpty() }
                if (isTrueSeries) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Season Selector Header & Tabs
                            Text(
                                text = "SEASONS (${resolvedItem.seasons.size})",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(resolvedItem.seasons) { season ->
                                    val isSeasonSelected = (season.seasonNumber == selectedSeasonNumber)
                                    FilterChip(
                                        selected = isSeasonSelected,
                                        onClick = {
                                            if (selectedSeasonNumber != season.seasonNumber) {
                                                selectedSeasonNumber = season.seasonNumber
                                                val firstEp = season.episodes.firstOrNull()
                                                if (firstEp != null) {
                                                    selectedEpisodeNumber = firstEp.episodeNumber
                                                    activeStreamUrl = firstEp.streamUrl.ifBlank { firstEp.detailUrl.ifBlank { videoItem.detailUrl } }
                                                }
                                            }
                                        },
                                        label = { Text(season.name, fontWeight = FontWeight.SemiBold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = Color.Black
                                        )
                                    )
                                }
                            }

                            val currentSeason = resolvedItem.seasons.firstOrNull { it.seasonNumber == selectedSeasonNumber } ?: resolvedItem.seasons.first()
                            if (currentSeason.episodes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "EPISODES (${currentSeason.episodes.size})",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(currentSeason.episodes) { ep ->
                                        val isEpSelected = (ep.episodeNumber == selectedEpisodeNumber)
                                        Card(
                                            modifier = Modifier
                                                .width(135.dp)
                                                .clickable {
                                                    if (selectedEpisodeNumber != ep.episodeNumber) {
                                                        selectedEpisodeNumber = ep.episodeNumber
                                                        activeStreamUrl = ep.streamUrl.ifBlank { ep.detailUrl.ifBlank { videoItem.detailUrl } }
                                                    }
                                                },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isEpSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.4f)
                                            ),
                                            border = if (isEpSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = "EP ${ep.episodeNumber}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isEpSelected) MaterialTheme.colorScheme.primary else Color.White
                                                    )
                                                    if (isEpSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.PlayArrow,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = ep.title,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White,
                                                    maxLines = 2
                                                )
                                                if (ep.duration.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = ep.duration,
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
                    }
                }

                // Playback Options: Quality / Resolution (if multiple available), Audio Language (for the 3 series/movie sites only), Content Provider / Server (if multiple available)
                val availableLanguages = remember(mediaSources.toList(), resolvedItem.languages) {
                    if (isSeriesProvider) {
                        val fromSources = mediaSources.mapNotNull { it.language.takeIf { l -> l.isNotBlank() } }.distinct()
                        if (fromSources.isNotEmpty()) fromSources else resolvedItem.languages
                    } else emptyList()
                }
                val availableQualities = remember(mediaSources.toList(), resolvedItem.qualities) {
                    val fromSources = mediaSources.mapNotNull {
                        it.resolution?.takeIf { r -> r.isNotBlank() }
                            ?: it.quality.takeIf { q -> q.isNotBlank() && (q.contains("p", ignoreCase = true) || q.contains("4k", ignoreCase = true) || q.contains("hd", ignoreCase = true)) }
                    }.distinct()
                    if (fromSources.isNotEmpty()) fromSources else resolvedItem.qualities.filter { q -> q.contains("p", ignoreCase = true) || q.contains("4k", ignoreCase = true) || q.contains("hd", ignoreCase = true) }
                }
                val availableServers = remember(mediaSources.toList()) {
                    mediaSources.mapNotNull { it.metadata["server"]?.takeIf { s -> s.isNotBlank() } }.distinct()
                }

                val hasPlaybackOptions = availableQualities.size > 1 || availableServers.size > 1 || (isSeriesProvider && availableLanguages.size > 1)

                if (hasPlaybackOptions && !isCollection) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Quality / Resolution Chips (Only when multiple resolutions available)
                            if (availableQualities.size > 1) {
                                Text(
                                    text = "RESOLUTION / QUALITY",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(availableQualities) { qual ->
                                        val isQualSelected = selectedQuality?.equals(qual, ignoreCase = true) == true
                                        FilterChip(
                                            selected = isQualSelected,
                                            onClick = { selectedQuality = qual },
                                            label = { Text(qual, fontWeight = FontWeight.SemiBold) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = Color.Black
                                            )
                                        )
                                    }
                                }
                            }

                            // Audio Language Chips (Only for series/movie sites when multiple languages available)
                            if (isSeriesProvider && availableLanguages.size > 1) {
                                if (availableQualities.size > 1) Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "AUDIO LANGUAGE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(availableLanguages) { lang ->
                                        val isLangSelected = selectedLanguage?.equals(lang, ignoreCase = true) == true
                                        FilterChip(
                                            selected = isLangSelected,
                                            onClick = { selectedLanguage = lang },
                                            label = { Text(lang, fontWeight = FontWeight.SemiBold) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = Color.Black
                                            )
                                        )
                                    }
                                }
                            }

                            // Server / Content Provider Chips (Only when multiple servers available)
                            if (availableServers.size > 1) {
                                if (availableQualities.size > 1 || (isSeriesProvider && availableLanguages.size > 1)) Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "SERVER / PROVIDER",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(availableServers) { srv ->
                                        val isSrvSelected = selectedServer?.equals(srv, ignoreCase = true) == true
                                        FilterChip(
                                            selected = isSrvSelected,
                                            onClick = { selectedServer = srv },
                                            label = { Text(srv, fontWeight = FontWeight.SemiBold) },
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
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isCollection) {
                    val executeDownload: () -> Unit = {
                        coroutineScope.launch {
                            Toast.makeText(context, "Resolving download…", Toast.LENGTH_SHORT).show()
                            isDownloadResolving = true
                            try {
                                val mediaSourceToDownload: MediaSource? = activeSource ?: if (resolutionResult != null && resolutionResult!!.isPlayable) {
                                    resolutionResult!!.toMediaSource()
                                } else {
                                    val mediaResult = adapter?.getPlayableMedia(activeStreamUrl)
                                    if (mediaResult != null && mediaResult.isSuccess) {
                                        val sources = mediaResult.getOrThrow()
                                        if (sources.isNotEmpty()) {
                                            mediaSources.clear()
                                            mediaSources.addAll(sources)
                                            isLoadingMedia = false
                                            val primary = sources.first()
                                            val isProviderDownloadAuthorized = adapter?.hasCapability(ProviderCapability.DOWNLOAD) == true
                                            val res = MediaResolutionResult.fromMediaSource(
                                                videoId = videoItem.id,
                                                providerId = videoItem.providerId,
                                                source = primary,
                                                isProviderDownloadAuthorized = isProviderDownloadAuthorized,
                                                title = resolvedItem.title
                                            )
                                            resolutionResult = res
                                            mediaResolutionState = res.state
                                            res.toMediaSource()
                                        } else null
                                    } else null
                                }

                                if (mediaSourceToDownload == null) {
                                    Toast.makeText(context, "Download failed: No downloadable stream found", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }

                                val result = downloadRepository.enqueueAuthorizedDownload(
                                    currentActiveItem,
                                    mediaSourceToDownload
                                )
                                if (result.isSuccess) {
                                    Toast.makeText(context, "Download started. Check notification bar for progress.", Toast.LENGTH_SHORT).show()
                                    coroutineScope.launch {
                                        val snackbarResult = snackbarHostState.showSnackbar(
                                            message = "Download queued: ${currentActiveItem.title}",
                                            actionLabel = "VIEW",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (snackbarResult == SnackbarResult.ActionPerformed) {
                                            onOpenDownloadsClick()
                                        }
                                    }
                                } else {
                                    val err = result.exceptionOrNull()?.message ?: "Download unavailable"
                                    Toast.makeText(context, "Download failed: $err", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                com.thedesitadka.core.security.StreamHubLogger.e("DetailsScreen", "Download action error: ${e.message}")
                                Toast.makeText(context, "Download failed: ${e.message ?: "Error"}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isDownloadResolving = false
                            }
                        }
                    }

                    val triggerDownload: () -> Unit = {
                        requestPermissionAndDownload(executeDownload)
                    }

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Play Button
                        Button(
                            onClick = {
                                if (canPlay && resolutionResult != null) {
                                    handlePlay(currentActiveItem, activeSource ?: resolutionResult!!.toMediaSource())
                                }
                            },
                            enabled = canPlay,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isResolving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Resolving…", color = Color.Black, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Play", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Download Button (ALWAYS ENABLED, resolves link on click if needed)
                        OutlinedButton(
                            onClick = {
                                if (!isDownloadResolving) {
                                    triggerDownload()
                                }
                            },
                            enabled = !isDownloadResolving,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isDownloadResolving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download Media",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Favorite Button
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (isFavorite) {
                                        favoriteDao.removeFavorite(resolvedItem.id)
                                    } else {
                                        favoriteDao.addFavorite(
                                            com.thedesitadka.app.storage.FavoriteEntity(
                                                id = resolvedItem.id,
                                                providerId = resolvedItem.providerId,
                                                title = resolvedItem.title,
                                                thumbnailUrl = resolvedItem.thumbnailUrl,
                                                detailUrl = resolvedItem.detailUrl
                                            )
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) Color.Red else Color.White
                            )
                        }
                    }
                }

                // Live In-Page Download Status Card
                val currentDownloadRecord = remember(allDownloads, currentActiveItem.id, currentActiveItem.title, activeStreamUrl) {
                    allDownloads.firstOrNull {
                        it.id == currentActiveItem.id ||
                        it.contentId == currentActiveItem.id ||
                        (it.mediaUrl.isNotBlank() && it.mediaUrl == activeStreamUrl) ||
                        it.title.equals(currentActiveItem.title, ignoreCase = true)
                    }
                }

                if (currentDownloadRecord != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (currentDownloadRecord.status) {
                                DownloadStatus.COMPLETED -> Color(0xFF10B981).copy(alpha = 0.15f)
                                DownloadStatus.FAILED, DownloadStatus.CANCELLED -> Color(0xFFEF4444).copy(alpha = 0.15f)
                                DownloadStatus.PAUSED -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                            }
                        ),
                        border = BorderStroke(
                            1.dp,
                            when (currentDownloadRecord.status) {
                                DownloadStatus.COMPLETED -> Color(0xFF10B981)
                                DownloadStatus.FAILED, DownloadStatus.CANCELLED -> Color(0xFFEF4444)
                                DownloadStatus.PAUSED -> Color(0xFFF59E0B)
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = when (currentDownloadRecord.status) {
                                            DownloadStatus.COMPLETED -> Icons.Default.CheckCircle
                                            DownloadStatus.FAILED, DownloadStatus.CANCELLED -> Icons.Default.ErrorOutline
                                            DownloadStatus.PAUSED -> Icons.Default.PauseCircle
                                            else -> Icons.Default.Download
                                        },
                                        contentDescription = null,
                                        tint = when (currentDownloadRecord.status) {
                                            DownloadStatus.COMPLETED -> Color(0xFF10B981)
                                            DownloadStatus.FAILED, DownloadStatus.CANCELLED -> Color(0xFFEF4444)
                                            DownloadStatus.PAUSED -> Color(0xFFF59E0B)
                                            else -> MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when (currentDownloadRecord.status) {
                                            DownloadStatus.COMPLETED -> "Saved Offline"
                                            DownloadStatus.DOWNLOADING -> "Downloading • ${currentDownloadRecord.progress}%"
                                            DownloadStatus.QUEUED -> "Queued in Downloads"
                                            DownloadStatus.RETRYING -> "Retrying Download"
                                            DownloadStatus.PAUSED -> "Paused (${currentDownloadRecord.progress}%)"
                                            DownloadStatus.FAILED -> "Download Failed"
                                            DownloadStatus.CANCELLED -> "Download Cancelled"
                                            else -> "Status: ${currentDownloadRecord.status}"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                }

                                if (currentDownloadRecord.status == DownloadStatus.DOWNLOADING && currentDownloadRecord.speedBytesPerSec > 0) {
                                    val speedText = when {
                                        currentDownloadRecord.speedBytesPerSec >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB/s", currentDownloadRecord.speedBytesPerSec / (1024f * 1024f))
                                        currentDownloadRecord.speedBytesPerSec >= 1024 -> String.format(java.util.Locale.US, "%.1f KB/s", currentDownloadRecord.speedBytesPerSec / 1024f)
                                        else -> "${currentDownloadRecord.speedBytesPerSec} B/s"
                                    }
                                    Text(
                                        text = speedText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (currentDownloadRecord.status == DownloadStatus.DOWNLOADING || currentDownloadRecord.status == DownloadStatus.QUEUED || currentDownloadRecord.status == DownloadStatus.RETRYING || currentDownloadRecord.status == DownloadStatus.PAUSED) {
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { (currentDownloadRecord.progress.coerceIn(0, 100)) / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = if (currentDownloadRecord.status == DownloadStatus.PAUSED) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary,
                                    trackColor = Color.White.copy(alpha = 0.12f)
                                )
                            }

                            if (currentDownloadRecord.error != null && (currentDownloadRecord.status == DownloadStatus.FAILED || currentDownloadRecord.status == DownloadStatus.CANCELLED)) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = currentDownloadRecord.error ?: "Error during download",
                                    fontSize = 11.sp,
                                    color = Color(0xFFEF4444)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onOpenDownloadsClick,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("View in Downloads", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                when (currentDownloadRecord.status) {
                                    DownloadStatus.DOWNLOADING -> {
                                        OutlinedButton(
                                            onClick = { coroutineScope.launch { downloadRepository.pauseDownload(currentDownloadRecord.id) } },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Pause", fontSize = 12.sp, color = Color.White)
                                        }
                                        OutlinedButton(
                                            onClick = { coroutineScope.launch { downloadRepository.cancelDownload(currentDownloadRecord.id) } },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Cancel", fontSize = 12.sp, color = Color.White)
                                        }
                                    }
                                    DownloadStatus.PAUSED -> {
                                        OutlinedButton(
                                            onClick = { coroutineScope.launch { downloadRepository.resumeDownload(currentDownloadRecord.id) } },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Resume", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                                        OutlinedButton(
                                            onClick = { coroutineScope.launch { downloadRepository.retryDownload(currentDownloadRecord.id) } },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Retry", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    DownloadStatus.COMPLETED -> {
                                        OutlinedButton(
                                            onClick = {
                                                val path = currentDownloadRecord.localFilePath.trim()
                                                val playUrl = when {
                                                    path.startsWith("content://") || path.startsWith("file://") -> path
                                                    path.startsWith("/") -> {
                                                        val file = java.io.File(path)
                                                        if (file.exists()) Uri.fromFile(file).toString() else path
                                                    }
                                                    else -> path
                                                }
                                                val source = MediaSource(
                                                    url = playUrl,
                                                    type = com.thedesitadka.core.model.MediaSourceType.PROGRESSIVE_MP4
                                                )
                                                handlePlay(currentActiveItem, source)
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Play Offline", fontSize = 12.sp, color = Color(0xFF10B981))
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }

                if (mediaError != null && mediaSources.isEmpty() && !isCollection) {
                    val isCloudflare = mediaError?.contains("Cloudflare security challenge detected", ignoreCase = true) == true ||
                        mediaError?.contains("Cloudflare security verification required", ignoreCase = true) == true ||
                        mediaError?.contains("cf-challenge", ignoreCase = true) == true

                    if (isCloudflare) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Security Verification Required",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "This media provider is protected by Cloudflare. Tap below to verify and unlock full playback.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        val intent = CloudflareChallengeActivity.createIntent(
                                            context,
                                            videoItem.detailUrl,
                                            resolvedItem.title
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
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = mediaError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { reloadTrigger++ },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(videoItem.detailUrl))
                                        context.startActivity(browserIntent)
                                    } catch (_: Exception) {}
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Open in Browser", color = Color.White)
                            }
                        }
                    }
                }

                // Description
                if (resolvedItem.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "DESCRIPTION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = resolvedItem.description,
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }

                // Ad Placement on Detail Page
                if (monetizationManager != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    AdSlotView(
                        placement = AdPlacementType.CONTENT_DETAIL,
                        monetizationManager = monetizationManager
                    )
                }

                // Category Collection Video Grid (e.g. XNXX category pages)
                if (mediaSources.isEmpty() && relatedItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "CATEGORY COLLECTION (${relatedItems.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select any video below to watch or download:",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    for (chunk in relatedItems.chunked(2)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (relItem in chunk) {
                                Box(modifier = Modifier.weight(1f)) {
                                    VideoCard(
                                        videoItem = relItem,
                                        onClick = { onRelatedClick(relItem) }
                                    )
                                }
                            }
                            if (chunk.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else if (relatedItems.isNotEmpty()) {
                    // Standard related content row for single video pages
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "RELATED CONTENT",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(relatedItems) { relItem ->
                            Box(modifier = Modifier.width(170.dp)) {
                                VideoCard(
                                    videoItem = relItem,
                                    onClick = { onRelatedClick(relItem) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isPlaceholderOrLogo(url: String?): Boolean {
    if (url.isNullOrBlank()) return true
    val lower = url.lowercase()
    return lower.contains("logo") ||
           lower.contains("favicon") ||
           lower.contains("placeholder") ||
           lower.contains("default-thumb") ||
           lower.contains("site-icon") ||
           lower.contains("header-icon") ||
           lower.startsWith("data:")
}
