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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.unit.DpSize
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

@kotlin.OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    videoItem: VideoItem,
    mediaSource: MediaSource,
    watchHistoryDao: WatchHistoryDao,
    monetizationManager: MonetizationManager? = null,
    hasNext: Boolean = false,
    hasPrevious: Boolean = false,
    onNextClick: (() -> Unit)? = null,
    onPreviousClick: (() -> Unit)? = null,
    onOpenDownloadsClick: (() -> Unit)? = null,
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

    LaunchedEffect(videoItem.id, mediaSource.url) {
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

    LaunchedEffect(isLandscape, isFullscreen, showControls) {
        val shouldBeImmersive = isLandscape || isFullscreen || !showControls
        activity?.let { act ->
            setImmersiveMode(act, shouldBeImmersive)
        }
    }

    // Fullscreen and orientation toggle
    fun toggleFullscreen() {
        val nextFullscreen = !isFullscreen
        isFullscreen = nextFullscreen
        activity?.let { act ->
            if (nextFullscreen) {
                if (!isLandscape) {
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                setImmersiveMode(act, true)
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                setImmersiveMode(act, !showControls)
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

    var swipeFeedbackText by remember { mutableStateOf<String?>(null) }
    var swipeFeedbackIcon by remember { mutableStateOf<ImageVector?>(null) }

    LaunchedEffect(swipeFeedbackText) {
        if (swipeFeedbackText != null) {
            delay(1500)
            swipeFeedbackText = null
        }
    }

    val currentOnNextClick by rememberUpdatedState(onNextClick)
    val currentOnPreviousClick by rememberUpdatedState(onPreviousClick)
    val currentHasNext by rememberUpdatedState(hasNext)
    val currentHasPrevious by rememberUpdatedState(hasPrevious)

    val triggerNext: () -> Unit = {
        if (currentHasNext && currentOnNextClick != null) {
            swipeFeedbackText = "Next Video"
            swipeFeedbackIcon = Icons.Default.SkipNext
            Toast.makeText(context, "Playing next video…", Toast.LENGTH_SHORT).show()
            currentOnNextClick?.invoke()
        } else {
            swipeFeedbackText = "No next video in playlist"
            swipeFeedbackIcon = Icons.Default.SkipNext
            Toast.makeText(context, "No next video in playlist", Toast.LENGTH_SHORT).show()
        }
    }

    val triggerPrevious: () -> Unit = {
        if (currentHasPrevious && currentOnPreviousClick != null) {
            swipeFeedbackText = "Previous Video"
            swipeFeedbackIcon = Icons.Default.SkipPrevious
            Toast.makeText(context, "Playing previous video…", Toast.LENGTH_SHORT).show()
            currentOnPreviousClick?.invoke()
        } else {
            swipeFeedbackText = "No previous video in playlist"
            swipeFeedbackIcon = Icons.Default.SkipPrevious
            Toast.makeText(context, "No previous video in playlist", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ExoPlayer View (bottom layer)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = playerManager.getPlayer()
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Touch & Gesture Detection Layer (placed on top of AndroidView so native PlayerView doesn't swallow touches)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .playerGestureDetector(
                    onTap = { showControls = !showControls },
                    onSwipeLeft = triggerNext,
                    onSwipeRight = triggerPrevious
                )
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
        } else if (hasStartedPlaying && (state.isBuffering || state.isSeeking) && !showControls && state.errorMessage == null) {
            // Non-disruptive Buffering Indicator during active playback or seeking when controls are hidden
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
                    .playerGestureDetector(
                        onTap = { showControls = false },
                        onSwipeLeft = triggerNext,
                        onSwipeRight = triggerPrevious
                    )
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
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

                    // Downloads Quick Access
                    if (onOpenDownloadsClick != null) {
                        IconButton(onClick = onOpenDownloadsClick) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "View Downloads",
                                tint = Color.White
                            )
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

                // Center Play / Pause / 10s Rewind / 10s FastForward / Skip Previous / Skip Next (Shown only after initial loading)
                if (hasStartedPlaying) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Skip Previous
                        IconButton(
                            onClick = { onPreviousClick?.invoke() },
                            enabled = hasPrevious,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (hasPrevious) 0.15f else 0.05f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Video",
                                tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        IconButton(
                            onClick = { playerManager.seekBackward(10000L) },
                            enabled = !state.isBuffering && !state.isSeeking,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        val isBufferingOrSeeking = state.isBuffering || state.isSeeking
                        if (isBufferingOrSeeking) {
                            Box(
                                modifier = Modifier.size(68.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(52.dp),
                                    strokeWidth = 4.dp
                                )
                            }
                        } else {
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
                                    imageVector = if (state.isPlaying || (state.playWhenReady && !state.isEnded)) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (state.isPlaying || (state.playWhenReady && !state.isEnded)) "Pause" else "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { playerManager.seekForward(10000L) },
                            enabled = !state.isBuffering && !state.isSeeking,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Skip Next
                        IconButton(
                            onClick = { onNextClick?.invoke() },
                            enabled = hasNext,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (hasNext) 0.15f else 0.05f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Video",
                                tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                // Bottom Progress Scrubber
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
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
                        thumb = {
                            SliderDefaults.Thumb(
                                interactionSource = remember { MutableInteractionSource() },
                                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary),
                                thumbSize = DpSize(12.dp, 12.dp)
                            )
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

        // Central Swipe HUD feedback
        AnimatedVisibility(
            visible = swipeFeedbackText != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    swipeFeedbackIcon?.let { icon ->
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = swipeFeedbackText ?: "",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

private fun Modifier.playerGestureDetector(
    onTap: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val startX = down.position.x
        val startY = down.position.y
        var lastX = startX
        var lastY = startY
        var hasMovedSignificantly = false

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            lastX = change.position.x
            lastY = change.position.y
            val curDx = kotlin.math.abs(lastX - startX)
            val curDy = kotlin.math.abs(lastY - startY)
            if (curDx > 25f || curDy > 25f) {
                hasMovedSignificantly = true
            }
            if (change.changedToUp()) {
                val wasConsumedByChild = change.isConsumed
                if (!wasConsumedByChild) {
                    change.consume()
                    val totalDx = lastX - startX
                    val totalDy = lastY - startY
                    val absDx = kotlin.math.abs(totalDx)
                    val absDy = kotlin.math.abs(totalDy)

                    val threshold = 70f
                    if (absDx >= threshold && absDx > absDy * 0.7f) {
                        if (totalDx < 0) {
                            onSwipeLeft()
                        } else {
                            onSwipeRight()
                        }
                    } else if (!hasMovedSignificantly && absDx < 25f && absDy < 25f) {
                        onTap()
                    }
                }
                break
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
