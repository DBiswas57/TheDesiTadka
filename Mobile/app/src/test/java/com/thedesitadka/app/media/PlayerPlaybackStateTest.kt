package com.thedesitadka.app.media

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPlaybackStateTest {

    @Test
    fun testProgressRatioCalculation() {
        val state = PlayerPlaybackState(
            currentPositionMs = 30000L,
            durationMs = 60000L,
            bufferedPositionMs = 45000L,
            isPlaying = true,
            exoPlaybackState = Player.STATE_READY
        )

        assertEquals(0.5f, state.progressRatio, 0.001f)
        assertEquals(0.75f, state.bufferedRatio, 0.001f)
        assertFalse(state.isLive)
    }

    @Test
    fun testZeroDurationSafeHandling() {
        val state = PlayerPlaybackState(
            currentPositionMs = 0L,
            durationMs = 0L,
            bufferedPositionMs = 0L
        )

        assertEquals(0f, state.progressRatio, 0.001f)
        assertEquals(0f, state.bufferedRatio, 0.001f)
    }

    @Test
    fun testLiveStreamDetection() {
        val liveState = PlayerPlaybackState(
            currentPositionMs = 12000L,
            durationMs = -1L,
            exoPlaybackState = Player.STATE_READY
        )

        assertTrue(liveState.isLive)
    }

    @Test
    fun testProgressClampedToOne() {
        val state = PlayerPlaybackState(
            currentPositionMs = 70000L,
            durationMs = 60000L
        )

        assertEquals(1.0f, state.progressRatio, 0.001f)
    }
}
