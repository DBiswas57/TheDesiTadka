package com.thedesitadka.app.media

import androidx.media3.common.Player

data class PlayerPlaybackState(
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val exoPlaybackState: Int = Player.STATE_IDLE,
    val playbackSpeed: Float = 1.0f,
    val isMuted: Boolean = false,
    val isBuffering: Boolean = false,
    val isSeeking: Boolean = false,
    val isPrepared: Boolean = false,
    val isEnded: Boolean = false,
    val errorMessage: String? = null,
    val errorCode: Int? = null
) {
    val isLive: Boolean
        get() = durationMs <= 0L && exoPlaybackState == Player.STATE_READY

    val progressRatio: Float
        get() = if (durationMs > 0L) {
            (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val bufferedRatio: Float
        get() = if (durationMs > 0L) {
            (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
}
