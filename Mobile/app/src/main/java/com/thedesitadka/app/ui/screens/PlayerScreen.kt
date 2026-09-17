package com.thedesitadka.app.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.util.Rational
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.thedesitadka.app.media.MediaPlayerManager
import com.thedesitadka.app.storage.WatchHistoryDao
import com.thedesitadka.app.storage.WatchHistoryEntity
import com.thedesitadka.core.model.MediaSource
import com.thedesitadka.core.model.VideoItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.thedesitadka.app.monetization.AdPlacementType
import com.thedesitadka.app.monetization.MonetizationManager
import com.thedesitadka.app.monetization.ui.AdSlotView

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoItem: VideoItem,
    mediaSource: MediaSource,
    watchHistoryDao: WatchHistoryDao,
    monetizationManager: MonetizationManager? = null,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    val playerManager = remember {
        MediaPlayerManager(context) { currentPos, dur ->
            coroutineScope.launch {
                watchHistoryDao.upsertHistory(
                    WatchHistoryEntity(
                        id = videoItem.id,
                        providerId = videoItem.providerId,
                        title = videoItem.title,
                        thumbnailUrl = videoItem.thumbnailUrl,
                        detailUrl = videoItem.detailUrl,
                        lastPositionMs = currentPos,
                        durationMs = dur
                    )
                )
            }
        }
    }

    val state by playerManager.playbackStateFlow.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var speedMenuExpanded by remember { mutableStateOf(false) }

    // Drag to seek tracking
    var isDragging by remember { mutableStateOf(false) }
    var dragProgressRatio by remember { mutableFloatStateOf(0f) }

    var hasStartedPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(videoItem.id) {
        val existing = watchHistoryDao.getHistoryItem(videoItem.id)
        val resumePos = existing?.lastPositionMs ?: 0L
        playerManager.prepareAndPlay(mediaSource, resumePositionMs = resumePos)
    }

    // Detect first playback start: dismiss initial loading overlay and hide controls only when truly playing
    LaunchedEffect(state.isPlaying, state.isBuffering, state.isSeeking) {
        if (state.isPlaying && !state.isBuffering && !state.isSeeking && !hasStartedPlaying) {
            hasStartedPlaying = true
            showControls = false
        }
    }

    // Auto-hide controls after 4 seconds of uninterrupted playback (never while seeking or buffering)
    LaunchedEffect(showControls, state.isPlaying, state.isBuffering, state.isSeeking, isDragging) {
        if (showControls && state.isPlaying && !state.isBuffering && !state.isSeeking && !isDragging && !isScreenLocked) {
            delay(4000)
            showControls = false
        }
    }

    // Handle device rotation and orientation changes
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(isLandscape) {
        isFullscreen = isLandscape
        activity?.let { act ->
            setImmersiveMode(act, isLandscape)
        }
    }

    // Fullscreen and orientation toggle
    fun toggleFullscreen() {
        val nextFullscreen = !isFullscreen
        isFullscreen = nextFullscreen
        activity?.let { act ->
            if (nextFullscreen) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                setImmersiveMode(act, true)
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                setImmersiveMode(act, false)
            }
        }
    }

    // Enable sensor orientation on entry; restore orientation and system bars on exit
    DisposableEffect(Unit) {
        activity?.let { act ->
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                setImmersiveMode(act, false)
            }
            playerManager.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (isScreenLocked) {
                    showControls = !showControls
                } else {
                    showControls = !showControls
                }
            }
    ) {
        // ExoPlayer View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = playerManager.getPlayer()
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Initial Preparing / Buffering Overlay (Before playback begins)
        val isInitialPreparing = (!hasStartedPlaying || (state.isBuffering && state.currentPositionMs == 0L)) &&
                state.errorMessage == null && !state.isEnded
        if (isInitialPreparing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(52.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (state.isBuffering) "Buffering stream..." else "Loading video stream...",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
        } else if (hasStartedPlaying && (state.isBuffering || state.isSeeking) && state.errorMessage == null) {
            // Non-disruptive Buffering Indicator during active playback or seeking
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center),
                strokeWidth = 4.dp
            )
        }

        // Error State View with Retry
        if (state.errorMessage != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Playback Error",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.errorMessage ?: "Unknown error",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { playerManager.prepareAndPlay(mediaSource, state.currentPositionMs) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry Playback", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Screen Lock Indicator (When Locked)
        if (isScreenLocked && showControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = { isScreenLocked = false },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Unlock Controls",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Full Controls Overlay
        AnimatedVisibility(
            visible = showControls && !isScreenLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (isFullscreen) toggleFullscreen() else onBackClick()
                    }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = videoItem.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            fontSize = 14.sp
                        )
                        Text(
                            text = videoItem.providerId.uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Mute / Unmute
                    IconButton(onClick = { playerManager.toggleMute() }) {
                        Icon(
                            imageVector = if (state.isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                            contentDescription = "Mute",
                            tint = Color.White
                        )
                    }

                    // Speed Dropdown
                    Box {
                        IconButton(onClick = { speedMenuExpanded = true }) {
                            Icon(imageVector = Icons.Default.Speed, contentDescription = "Speed", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = speedMenuExpanded,
                            onDismissRequest = { speedMenuExpanded = false }
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${speed}x",
                                            fontWeight = if (state.playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal,
                                            color = if (state.playbackSpeed == speed) MaterialTheme.colorScheme.primary else Color.White
                                        )
                                    },
                                    onClick = {
                                        playerManager.setSpeed(speed)
                                        speedMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Lock Screen Controls
                    IconButton(onClick = { isScreenLocked = true }) {
                        Icon(imageVector = Icons.Default.LockOpen, contentDescription = "Lock Screen", tint = Color.White)
                    }

                    // PiP Button
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
                    ) {
                        IconButton(onClick = {
                            try {
                                val params = PictureInPictureParams.Builder()
                                    .setAspectRatio(Rational(16, 9))
                                    .build()
                                activity?.enterPictureInPictureMode(params)
                            } catch (e: Exception) {
                                // ignore
                            }
                        }) {
                            Icon(imageVector = Icons.Default.PictureInPicture, contentDescription = "PiP", tint = Color.White)
                        }
                    }

                    // Fullscreen Toggle
                    IconButton(onClick = { toggleFullscreen() }) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen",
                            tint = Color.White
                        )
                    }
                }

                // Center Play / Pause / 10s Rewind / 10s FastForward (Shown only after initial loading)
                if (hasStartedPlaying) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { playerManager.seekBackward(10000L) },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    if (state.isPlaying) playerManager.pause() else playerManager.play()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        IconButton(
                            onClick = { playerManager.seekForward(10000L) },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                // Bottom Progress Scrubber
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    val currentSliderValue = if (isDragging) dragProgressRatio else state.progressRatio

                    Slider(
                        value = currentSliderValue,
                        onValueChange = { ratio ->
                            isDragging = true
                            dragProgressRatio = ratio
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            if (state.durationMs > 0L) {
                                val targetMs = (dragProgressRatio * state.durationMs).toLong()
                                playerManager.seekTo(targetMs)
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val displayCurrentPos = if (isDragging && state.durationMs > 0L) {
                            (dragProgressRatio * state.durationMs).toLong()
                        } else {
                            state.currentPositionMs
                        }

                        Text(
                            text = formatTime(displayCurrentPos),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = if (state.isLive) "LIVE" else formatTime(state.durationMs),
                            color = if (state.isLive) MaterialTheme.colorScheme.primary else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Non-intrusive Portrait Companion Banner (outside video viewport, non-blocking)
        if (!isFullscreen && !isLandscape && !isScreenLocked && monetizationManager != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                AdSlotView(
                    placement = AdPlacementType.PLAYER_COMPANION,
                    monetizationManager = monetizationManager
                )
            }
        }
    }
}

private fun setImmersiveMode(activity: Activity, enable: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val controller = activity.window.insetsController
        if (enable) {
            controller?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        }
    } else {
        @Suppress("DEPRECATION")
        val decorView = activity.window.decorView
        if (enable) {
            decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        } else {
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
