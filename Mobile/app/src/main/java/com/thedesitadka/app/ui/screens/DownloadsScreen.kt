package com.thedesitadka.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.thedesitadka.app.download.StoragePermissionHelper
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import java.io.File
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.thedesitadka.app.monetization.AdPlacementType
import com.thedesitadka.app.monetization.MonetizationManager
import com.thedesitadka.app.monetization.ui.AdSlotView
import com.thedesitadka.app.download.DownloadRepository
import com.thedesitadka.app.storage.DownloadRecordEntity
import com.thedesitadka.app.storage.DownloadStatus
import com.thedesitadka.app.ui.components.EmptyStateView
import com.thedesitadka.core.model.MediaSource
import com.thedesitadka.core.model.MediaSourceType
import com.thedesitadka.core.model.VideoItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    downloadRepository: DownloadRepository,
    monetizationManager: MonetizationManager? = null,
    onPlayOfflineClick: (VideoItem, MediaSource) -> Unit,
    onBackClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val allDownloads by downloadRepository.getAllDownloads().collectAsState(initial = emptyList())

    val filterOptions = listOf("All", "Downloading", "Paused", "Completed", "Failed")
    var selectedFilter by remember { mutableStateOf("All") }

    var isRefreshing by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<DownloadRecordEntity?>(null) }
    var showClearListDialog by remember { mutableStateOf(false) }

    // Reconcile database records with filesystem on screen entry
    LaunchedEffect(Unit) {
        downloadRepository.reconcileWithFilesystem()
    }

    val filteredDownloads = when (selectedFilter) {
        "Downloading" -> allDownloads.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RETRYING }
        "Paused" -> allDownloads.filter { it.status == DownloadStatus.PAUSED }
        "Completed" -> allDownloads.filter { it.status == DownloadStatus.COMPLETED }
        "Failed" -> allDownloads.filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }
        else -> allDownloads
    }

    // Delete Confirmation Dialog with distinct "Remove from List" vs "Delete Permanently"
    if (itemToDelete != null) {
        val target = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Download", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column {
                    Text(
                        text = target.title,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Choose whether to remove this entry from your list while preserving the saved video file on your device, or permanently delete the file from storage.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = target.id
                        itemToDelete = null
                        coroutineScope.launch { downloadRepository.deletePermanently(id) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete Permanently", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val id = target.id
                            itemToDelete = null
                            coroutineScope.launch { downloadRepository.removeFromList(id) }
                        }
                    ) {
                        Text("Remove from List")
                    }
                    Button(
                        onClick = { itemToDelete = null },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text("Cancel", color = Color.White)
                    }
                }
            }
        )
    }

    // Clear List Confirmation Dialog
    if (showClearListDialog) {
        AlertDialog(
            onDismissRequest = { showClearListDialog = false },
            title = { Text("Clear Downloads", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Text(
                    text = "Do you want to clear your download records? You can clear the list while preserving the downloaded video files on your device storage, or permanently delete all associated media files.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearListDialog = false
                        coroutineScope.launch { downloadRepository.clearList(deleteFiles = true) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete All Files", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showClearListDialog = false
                            coroutineScope.launch { downloadRepository.clearList(deleteFiles = false) }
                        }
                    ) {
                        Text("Clear List Only")
                    }
                    Button(
                        onClick = { showClearListDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text("Cancel", color = Color.White)
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                title = { Text("Downloads", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (allDownloads.isNotEmpty()) {
                        IconButton(onClick = { showClearListDialog = true }) {
                            Icon(imageVector = Icons.Default.ClearAll, contentDescription = "Clear Downloads", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                coroutineScope.launch {
                    downloadRepository.reconcileWithFilesystem()
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val context = LocalContext.current
                var hasStorageAccess by remember { mutableStateOf(StoragePermissionHelper.hasFullStorageAccess(context)) }

                // 1DM-Style Storage Access Banner if not granted
                if (!hasStorageAccess) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SdStorage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Direct Storage Access (1DM Mode)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Enable All Files access to save directly into /Movies/TheDesiTadka/ with instant Gallery indexing.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    StoragePermissionHelper.requestFullStorageAccess(context)
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Grant", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Filter Tabs Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filterOptions.forEach { filter ->
                        val isSelected = selectedFilter == filter
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.Black,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = Color.White
                            )
                        )
                    }
                }

                if (filteredDownloads.isEmpty()) {
                    EmptyStateView(
                        title = "No $selectedFilter Downloads",
                        subtitle = "Authorized media downloaded for offline playback will appear here. Pull down to refresh."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredDownloads, key = { it.id }) { record ->
                            DownloadItemCard(
                                record = record,
                                onPlayClick = {
                                    val video = VideoItem(
                                        id = record.id,
                                        providerId = record.providerId,
                                        title = record.title,
                                        thumbnailUrl = record.thumbnailUrl,
                                        detailUrl = ""
                                    )
                                    val path = record.localFilePath.trim()
                                    val playUrl = when {
                                        path.startsWith("content://") || path.startsWith("file://") -> path
                                        path.startsWith("/") -> {
                                            val file = File(path)
                                            if (file.exists()) Uri.fromFile(file).toString() else path
                                        }
                                        else -> path
                                    }
                                    val source = MediaSource(
                                        url = playUrl,
                                        type = MediaSourceType.PROGRESSIVE_MP4
                                    )
                                    onPlayOfflineClick(video, source)
                                },
                                onPauseClick = {
                                    coroutineScope.launch { downloadRepository.pauseDownload(record.id) }
                                },
                                onResumeClick = {
                                    coroutineScope.launch { downloadRepository.resumeDownload(record.id) }
                                },
                                onRetryClick = {
                                    coroutineScope.launch { downloadRepository.retryDownload(record.id) }
                                },
                                onCancelClick = {
                                    coroutineScope.launch { downloadRepository.cancelDownload(record.id) }
                                },
                                onDeleteClick = {
                                    itemToDelete = record
                                }
                            )
                        }

                        if (monetizationManager != null) {
                            item {
                                AdSlotView(
                                    placement = AdPlacementType.DOWNLOAD_SCREEN,
                                    monetizationManager = monetizationManager
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadItemCard(
    record: DownloadRecordEntity,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onRetryClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail
                Box(
                    modifier = Modifier
                        .size(width = 96.dp, height = 58.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = record.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title & Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = record.status.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = when (record.status) {
                                DownloadStatus.COMPLETED -> Color(0xFF10B981)
                                DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                                DownloadStatus.QUEUED -> Color(0xFFFBBF24)
                                DownloadStatus.PAUSED -> Color(0xFF9CA3AF)
                                DownloadStatus.FAILED -> Color(0xFFEF4444)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )

                        if (record.status == DownloadStatus.DOWNLOADING && record.progress >= 0) {
                            Text(
                                text = " • ${record.progress}%",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Progress bar and stats during downloading
            if (record.status == DownloadStatus.DOWNLOADING || record.status == DownloadStatus.PAUSED || record.status == DownloadStatus.QUEUED) {
                Spacer(modifier = Modifier.height(10.dp))
                val ratio = if (record.totalBytes > 0L) {
                    (record.downloadedBytes.toFloat() / record.totalBytes.toFloat()).coerceIn(0f, 1f)
                } else 0f

                if (record.status == DownloadStatus.QUEUED && record.downloadedBytes == 0L) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val sizeText = if (record.status == DownloadStatus.QUEUED && record.downloadedBytes == 0L) {
                        "Connecting & allocating storage..."
                    } else {
                        "${formatFileSize(record.downloadedBytes)} / ${formatFileSize(record.totalBytes)}"
                    }
                    Text(text = sizeText, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))

                    if (record.status == DownloadStatus.DOWNLOADING && record.speedBytesPerSec > 0L) {
                        val speedMb = record.speedBytesPerSec / (1024f * 1024f)
                        val etaText = if (record.etaSeconds > 0L) {
                            val min = record.etaSeconds / 60
                            val sec = record.etaSeconds % 60
                            String.format("%.1f MB/s • ETA %02d:%02d", speedMb, min, sec)
                        } else {
                            String.format("%.1f MB/s", speedMb)
                        }
                        Text(text = etaText, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Error info if failed
            if (record.status == DownloadStatus.FAILED && !record.error.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Error: ${record.error}",
                    color = Color(0xFFEF4444),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (record.status) {
                    DownloadStatus.DOWNLOADING -> {
                        OutlinedButton(
                            onClick = onPauseClick,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pause", fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onCancelClick,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel", fontSize = 11.sp)
                        }
                    }
                    DownloadStatus.QUEUED -> {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        OutlinedButton(
                            onClick = onCancelClick,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel", fontSize = 11.sp)
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        Button(
                            onClick = onResumeClick,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resume", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onCancelClick,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Cancel", fontSize = 11.sp)
                        }
                    }
                    DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                        Button(
                            onClick = onRetryClick,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onDeleteClick,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Delete", fontSize = 11.sp)
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        Button(
                            onClick = onPlayClick,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onDeleteClick,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", fontSize = 11.sp)
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val kb = bytes / 1024f
    val mb = kb / 1024f
    val gb = mb / 1024f
    return if (gb >= 1.0f) {
        String.format("%.2f GB", gb)
    } else {
        String.format("%.1f MB", mb)
    }
}
