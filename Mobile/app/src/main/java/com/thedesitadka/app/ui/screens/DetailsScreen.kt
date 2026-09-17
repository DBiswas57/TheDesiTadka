package com.thedesitadka.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.thedesitadka.app.download.StoragePermissionHelper
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    onPlayClick: (VideoItem, MediaSource) -> Unit,
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

    var resolvedItem by remember { mutableStateOf(videoItem) }
    var mediaSources = remember { mutableStateListOf<MediaSource>() }
    var relatedItems = remember { mutableStateListOf<VideoItem>() }
    var isLoadingMedia by remember { mutableStateOf(true) }
    var mediaError by remember { mutableStateOf<String?>(null) }
    var currentRequestId by remember { mutableStateOf(0L) }
    var resolutionResult by remember { mutableStateOf<MediaResolutionResult?>(null) }
    var mediaResolutionState by remember { mutableStateOf(MediaResolutionState.UNKNOWN) }

    val isFavorite by favoriteDao.isFavorite(videoItem.id).collectAsState(initial = false)
    val isCollection = mediaSources.isEmpty() && relatedItems.isNotEmpty()

    LaunchedEffect(videoItem.id, videoItem.detailUrl, reloadTrigger) {
        val reqId = System.currentTimeMillis()
        currentRequestId = reqId
        mediaResolutionState = MediaResolutionState.RESOLVING
        resolutionResult = MediaResolutionResult.resolving(videoItem.id, videoItem.providerId)
        isLoadingMedia = true
        mediaError = null
        mediaSources.clear()
        relatedItems.clear()

        // Fetch details
        val detailResult = adapter?.getDetails(videoItem.detailUrl)
        if (currentRequestId != reqId) return@LaunchedEffect

        detailResult?.onSuccess { detail ->
            if (currentRequestId != reqId) return@onSuccess
            resolvedItem = if (detail.thumbnailUrl.isBlank() || isPlaceholderOrLogo(detail.thumbnailUrl)) {
                detail.copy(thumbnailUrl = videoItem.thumbnailUrl.ifEmpty { detail.thumbnailUrl })
            } else {
                detail
            }
        }

        // Fetch playable media sources using unified resolver
        val mediaResult = adapter?.getPlayableMedia(videoItem.detailUrl)
        if (currentRequestId != reqId) return@LaunchedEffect

        mediaResult?.onSuccess { sources ->
            if (currentRequestId != reqId) return@onSuccess
            mediaSources.clear()
            mediaSources.addAll(sources)
            isLoadingMedia = false
            if (sources.isNotEmpty()) {
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
            } else {
                resolutionResult = MediaResolutionResult.unavailable(videoItem.id, videoItem.providerId, "No media source available")
                mediaResolutionState = MediaResolutionState.UNAVAILABLE
            }
        }?.onFailure { err ->
            if (currentRequestId != reqId) return@onFailure
            mediaError = err.message ?: "Failed to resolve media stream"
            resolutionResult = MediaResolutionResult.error(videoItem.id, videoItem.providerId, mediaError ?: "Error")
            mediaResolutionState = MediaResolutionState.ERROR
            isLoadingMedia = false
        }

        // Fetch related content / category collection videos
        val relResult = adapter?.getRelatedContent(videoItem.detailUrl)
        if (currentRequestId != reqId) return@LaunchedEffect

        relResult?.onSuccess { rel ->
            if (currentRequestId != reqId) return@onSuccess
            relatedItems.clear()
            relatedItems.addAll(rel)
        }
    }

    var showStoragePermissionDialog by remember { mutableStateOf(false) }
    var showDownloadUnavailableDialog by remember { mutableStateOf(false) }
    var isDownloadResolving by remember { mutableStateOf(false) }
    var downloadErrorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                title = { Text(resolvedItem.title, maxLines = 1, fontWeight = FontWeight.Bold, color = Color.White) },
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
        val isResolving = mediaResolutionState == MediaResolutionState.RESOLVING
        val canPlay = !isCollection && (mediaResolutionState == MediaResolutionState.PLAYABLE || mediaResolutionState == MediaResolutionState.DOWNLOADABLE) && resolutionResult != null

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
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
                            .clickable { onPlayClick(resolvedItem, resolutionResult!!.toMediaSource()) },
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

                Spacer(modifier = Modifier.height(16.dp))

                if (!isCollection) {
                    val triggerDownload: () -> Unit = {
                        if (!StoragePermissionHelper.hasFullStorageAccess(context)) {
                            showStoragePermissionDialog = true
                        } else {
                            coroutineScope.launch {
                                // If media has already been resolved and is playable
                                if (resolutionResult != null && resolutionResult!!.isPlayable) {
                                    val mediaSourceToDownload = resolutionResult!!.toMediaSource()
                                    try {
                                        val result = downloadRepository.enqueueAuthorizedDownload(
                                            resolvedItem,
                                            mediaSourceToDownload
                                        )
                                        if (result.isSuccess) {
                                            Toast.makeText(context, "Download started to /Movies/TheDesiTadka", Toast.LENGTH_SHORT).show()
                                        } else {
                                            downloadErrorMessage = result.exceptionOrNull()?.message ?: "Download is not available for this video."
                                            showDownloadUnavailableDialog = true
                                        }
                                    } catch (e: Exception) {
                                        com.thedesitadka.core.security.StreamHubLogger.e("DetailsScreen", "Download action error: ${e.message}")
                                        downloadErrorMessage = e.message ?: "Download is not available for this video."
                                        showDownloadUnavailableDialog = true
                                    }
                                } else {
                                    // If media is not yet resolved, resolve link now using the same process as Play
                                    isDownloadResolving = true
                                    try {
                                        val mediaResult = adapter?.getPlayableMedia(videoItem.detailUrl)
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

                                                val mediaSourceToDownload = res.toMediaSource()
                                                val enqueueResult = downloadRepository.enqueueAuthorizedDownload(
                                                    resolvedItem,
                                                    mediaSourceToDownload
                                                )
                                                if (enqueueResult.isSuccess) {
                                                    Toast.makeText(context, "Download started to /Movies/TheDesiTadka", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    downloadErrorMessage = enqueueResult.exceptionOrNull()?.message ?: "Download is not available for this video."
                                                    showDownloadUnavailableDialog = true
                                                }
                                            } else {
                                                downloadErrorMessage = "Download is not available for this video."
                                                showDownloadUnavailableDialog = true
                                            }
                                        } else {
                                            downloadErrorMessage = mediaResult?.exceptionOrNull()?.message ?: "Download is not available for this video."
                                            showDownloadUnavailableDialog = true
                                        }
                                    } catch (e: Exception) {
                                        com.thedesitadka.core.security.StreamHubLogger.e("DetailsScreen", "Download resolution error: ${e.message}")
                                        downloadErrorMessage = e.message ?: "Download is not available for this video."
                                        showDownloadUnavailableDialog = true
                                    } finally {
                                        isDownloadResolving = false
                                    }
                                }
                            }
                        }
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
                                    onPlayClick(resolvedItem, resolutionResult!!.toMediaSource())
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

                    if (showDownloadUnavailableDialog) {
                        AlertDialog(
                            onDismissRequest = { showDownloadUnavailableDialog = false },
                            title = { Text("Download Unavailable", fontWeight = FontWeight.Bold, color = Color.White) },
                            text = {
                                Text(
                                    downloadErrorMessage ?: "Download is not available for this video.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = { showDownloadUnavailableDialog = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("OK", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                    }

                    if (showStoragePermissionDialog) {
                        AlertDialog(
                            onDismissRequest = { showStoragePermissionDialog = false },
                            title = { Text("Storage Access Permission", fontWeight = FontWeight.Bold, color = Color.White) },
                            text = {
                                Text(
                                    "TheDesiTadka saves videos directly to your device storage (/Movies/TheDesiTadka/) like 1DM so they appear in your Gallery and file manager.\n\nGrant Storage Permission for 1DM-style direct downloads, or proceed with standard download.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showStoragePermissionDialog = false
                                        StoragePermissionHelper.requestFullStorageAccess(context)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Grant Access", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showStoragePermissionDialog = false
                                        triggerDownload()
                                    }
                                ) {
                                    Text("Download Now", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    }

                    // Favorite Button
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                if (isFavorite) {
                                    favoriteDao.removeFavorite(resolvedItem.id)
                                } else {
                                    favoriteDao.addFavorite(
                                        FavoriteEntity(
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

                if (mediaError != null && mediaSources.isEmpty() && !isCollection) {
                    val isCloudflare = mediaError?.contains("cloudflare", ignoreCase = true) == true ||
                        mediaError?.contains("403") == true ||
                        mediaError?.contains("challenge", ignoreCase = true) == true ||
                        mediaError?.contains("security", ignoreCase = true) == true

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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                        Spacer(modifier = Modifier.height(10.dp))
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
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(relatedItems) { relItem ->
                            Box(modifier = Modifier.width(180.dp)) {
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
