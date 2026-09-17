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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    var resolvedItem by remember { mutableStateOf(videoItem) }
    var mediaSources = remember { mutableStateListOf<MediaSource>() }
    var relatedItems = remember { mutableStateListOf<VideoItem>() }
    var isLoadingMedia by remember { mutableStateOf(true) }
    var mediaError by remember { mutableStateOf<String?>(null) }

    val isFavorite by favoriteDao.isFavorite(videoItem.id).collectAsState(initial = false)

    LaunchedEffect(videoItem.id) {
        isLoadingMedia = true
        mediaError = null

        // Fetch details
        adapter?.getDetails(videoItem.detailUrl)?.onSuccess { detail ->
            resolvedItem = if (detail.thumbnailUrl.isBlank() || isPlaceholderOrLogo(detail.thumbnailUrl)) {
                detail.copy(thumbnailUrl = videoItem.thumbnailUrl.ifEmpty { detail.thumbnailUrl })
            } else {
                detail
            }
        }

        // Fetch playable media sources
        adapter?.getPlayableMedia(videoItem.detailUrl)?.onSuccess { sources ->
            mediaSources.clear()
            mediaSources.addAll(sources)
            isLoadingMedia = false
        }?.onFailure { err ->
            mediaError = err.message ?: "Failed to resolve media stream"
            isLoadingMedia = false
        }

        // Fetch related content
        adapter?.getRelatedContent(videoItem.detailUrl)?.onSuccess { rel ->
            relatedItems.clear()
            relatedItems.addAll(rel)
        }
    }

    var showStoragePermissionDialog by remember { mutableStateOf(false) }
    var showDownloadUnavailableDialog by remember { mutableStateOf(false) }

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
                if (mediaSources.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { onPlayClick(resolvedItem, mediaSources.first()) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                } else if (isLoadingMedia) {
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

                    // Legal Compliance Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Verified, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Authorized Public Source", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Play Button
                    Button(
                        onClick = {
                            if (mediaSources.isNotEmpty()) {
                                onPlayClick(resolvedItem, mediaSources.first())
                            }
                        },
                        enabled = mediaSources.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Play", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    // Download Button (Enabled strictly when authorized by provider or media capability and progressive file)
                    val primarySource = mediaSources.firstOrNull()
                    val canDownload = primarySource != null &&
                            primarySource.canDownload &&
                            (adapter?.hasCapability(ProviderCapability.DOWNLOAD) == true || primarySource.downloadUrl != null) &&
                            !primarySource.url.contains(".m3u8", ignoreCase = true) &&
                            !primarySource.url.contains(".mpd", ignoreCase = true)

                    val triggerDownload: () -> Unit = {
                        if (canDownload && primarySource != null) {
                            coroutineScope.launch {
                                try {
                                    val result = downloadRepository.enqueueAuthorizedDownload(
                                        resolvedItem,
                                        primarySource
                                    )
                                    if (result.isSuccess) {
                                        Toast.makeText(context, "Download started to /Movies/TheDesiTadka", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Download unavailable: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    com.thedesitadka.core.security.StreamHubLogger.e("DetailsScreen", "Download action error: ${e.message}")
                                    Toast.makeText(context, "Could not start download: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            if (canDownload) {
                                if (!StoragePermissionHelper.hasFullStorageAccess(context)) {
                                    showStoragePermissionDialog = true
                                } else {
                                    triggerDownload()
                                }
                            } else {
                                showDownloadUnavailableDialog = true
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = if (canDownload) "Download Media" else "Download Unavailable",
                            tint = if (canDownload) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f)
                        )
                    }

                    if (showDownloadUnavailableDialog) {
                        AlertDialog(
                            onDismissRequest = { showDownloadUnavailableDialog = false },
                            title = { Text("Direct Download Unavailable", fontWeight = FontWeight.Bold, color = Color.White) },
                            text = {
                                Text(
                                    "Direct offline download is unavailable for this media stream (HLS / segmented or stream-only source).\n\nWould you like to open the content page in your external browser to view or download directly from the web?",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showDownloadUnavailableDialog = false
                                        try {
                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(resolvedItem.detailUrl))
                                            context.startActivity(browserIntent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Open in External Browser", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDownloadUnavailableDialog = false }) {
                                    Text("Close", color = Color.White)
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

                if (mediaError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = mediaError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
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

                // Related Videos
                if (relatedItems.isNotEmpty()) {
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
