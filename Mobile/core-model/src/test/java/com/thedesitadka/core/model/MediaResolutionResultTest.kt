package com.thedesitadka.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaResolutionResultTest {

    @Test
    fun testResolvingState() {
        val res = MediaResolutionResult.resolving(videoId = "video123", providerId = "xnxx")
        assertEquals(MediaResolutionState.RESOLVING, res.state)
        assertEquals("video123", res.videoId)
        assertEquals("xnxx", res.providerId)
        assertFalse(res.isPlayable)
        assertFalse(res.isDownloadable)
    }

    @Test
    fun testProgressiveMp4BecomesDownloadable() {
        val source = MediaSource(
            url = "https://cdn.example.com/video.mp4",
            type = MediaSourceType.PROGRESSIVE_MP4,
            mimeType = "video/mp4",
            canPlay = true,
            canDownload = true
        )
        val res = MediaResolutionResult.fromMediaSource(
            videoId = "v1",
            providerId = "aagmaal",
            source = source,
            isProviderDownloadAuthorized = true,
            title = "Awesome Video 2026!"
        )

        assertEquals(MediaResolutionState.DOWNLOADABLE, res.state)
        assertTrue(res.isPlayable)
        assertTrue(res.isDownloadable)
        assertEquals("Awesome_Video_2026_.mp4", res.filename)

        val mediaSource = res.toMediaSource()
        assertEquals("https://cdn.example.com/video.mp4", mediaSource.url)
        assertTrue(mediaSource.canPlay)
        assertTrue(mediaSource.canDownload)
    }

    @Test
    fun testHlsStreamIsPlayableButNotDownloadable() {
        val source = MediaSource(
            url = "https://cdn.example.com/playlist.m3u8",
            type = MediaSourceType.HLS,
            mimeType = "application/x-mpegURL",
            canPlay = true,
            canDownload = true
        )
        val res = MediaResolutionResult.fromMediaSource(
            videoId = "v2",
            providerId = "xnxx",
            source = source,
            isProviderDownloadAuthorized = true,
            title = "Live HLS Stream"
        )

        assertEquals(MediaResolutionState.PLAYABLE, res.state)
        assertTrue(res.isPlayable)
        assertFalse("HLS streams must not be marked downloadable for direct downloader", res.isDownloadable)

        val mediaSource = res.toMediaSource()
        assertTrue(mediaSource.canPlay)
        assertFalse(mediaSource.canDownload)
    }

    @Test
    fun testUnavailableAndErrorStates() {
        val unavail = MediaResolutionResult.unavailable("v3", "masahub", "No media found")
        assertEquals(MediaResolutionState.UNAVAILABLE, unavail.state)
        assertFalse(unavail.isPlayable)
        assertFalse(unavail.isDownloadable)
        assertEquals("No media found", unavail.errorMessage)

        val err = MediaResolutionResult.error("v4", "xnxx", "Network timeout")
        assertEquals(MediaResolutionState.ERROR, err.state)
        assertFalse(err.isPlayable)
        assertFalse(err.isDownloadable)
        assertEquals("Network timeout", err.errorMessage)
    }
}
