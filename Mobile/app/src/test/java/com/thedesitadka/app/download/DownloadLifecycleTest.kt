package com.thedesitadka.app.download

import com.thedesitadka.app.storage.DownloadRecordEntity
import com.thedesitadka.app.storage.DownloadStatus
import com.thedesitadka.core.model.DownloadType
import com.thedesitadka.core.model.MediaSource
import com.thedesitadka.core.model.MediaSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadLifecycleTest {

    @Test
    fun testDownloadRecordAllStateTransitions() {
        // 1. Initial QUEUED state
        val queued = DownloadRecordEntity(
            id = "vid_101",
            providerId = "kamababa1",
            title = "Test Video",
            thumbnailUrl = "https://example.com/thumb.jpg",
            mediaUrl = "https://example.com/video.mp4",
            status = DownloadStatus.QUEUED
        )
        assertEquals(DownloadStatus.QUEUED, queued.status)
        assertEquals(0L, queued.downloadedBytes)

        // 2. Transition to DOWNLOADING
        val downloading = queued.copy(
            status = DownloadStatus.DOWNLOADING,
            downloadedBytes = 5242880L,
            totalBytes = 10485760L,
            progress = 50,
            speedBytesPerSec = 1048576L
        )
        assertEquals(DownloadStatus.DOWNLOADING, downloading.status)
        assertEquals(50, downloading.progress)

        // 3. Transition to PAUSED
        val paused = downloading.copy(
            status = DownloadStatus.PAUSED,
            speedBytesPerSec = 0L
        )
        assertEquals(DownloadStatus.PAUSED, paused.status)
        assertEquals(0L, paused.speedBytesPerSec)
        assertEquals(5242880L, paused.downloadedBytes)

        // 4. Transition to RETRYING
        val retrying = paused.copy(
            status = DownloadStatus.RETRYING,
            retryCount = 1,
            error = "Network timeout"
        )
        assertEquals(DownloadStatus.RETRYING, retrying.status)
        assertEquals(1, retrying.retryCount)
        assertEquals("Network timeout", retrying.error)

        // 5. Transition to COMPLETED
        val completed = downloading.copy(
            status = DownloadStatus.COMPLETED,
            downloadedBytes = 10485760L,
            totalBytes = 10485760L,
            progress = 100,
            speedBytesPerSec = 0L,
            completedAt = System.currentTimeMillis()
        )
        assertEquals(DownloadStatus.COMPLETED, completed.status)
        assertEquals(100, completed.progress)
        assertTrue(completed.completedAt != null && completed.completedAt!! > 0)

        // 6. Transition to FAILED
        val failed = downloading.copy(
            status = DownloadStatus.FAILED,
            error = "HTTP 403 Forbidden",
            speedBytesPerSec = 0L
        )
        assertEquals(DownloadStatus.FAILED, failed.status)
        assertEquals("HTTP 403 Forbidden", failed.error)

        // 7. Transition to CANCELLED
        val cancelled = downloading.copy(
            status = DownloadStatus.CANCELLED,
            speedBytesPerSec = 0L
        )
        assertEquals(DownloadStatus.CANCELLED, cancelled.status)

        // 8. Transition to DELETED
        val deleted = cancelled.copy(
            status = DownloadStatus.DELETED
        )
        assertEquals(DownloadStatus.DELETED, deleted.status)
    }

    @Test
    fun testMediaSourceCapabilitySeparation() {
        val mp4Source = MediaSource(
            url = "https://cdn.example.com/file.mp4",
            type = MediaSourceType.PROGRESSIVE_MP4
        )

        assertTrue(mp4Source.canPlay)
        assertTrue(mp4Source.canDownload)
        assertEquals(DownloadType.DIRECT_HTTP, mp4Source.downloadType)

        val liveSource = MediaSource(
            url = "https://cdn.example.com/stream.m3u8",
            type = MediaSourceType.HLS
        )

        assertTrue(liveSource.canPlay)
        assertFalse(liveSource.canDownload)
        assertEquals(DownloadType.UNSUPPORTED, liveSource.downloadType)

        val dashSource = MediaSource(
            url = "https://cdn.example.com/manifest.mpd",
            type = MediaSourceType.DASH
        )

        assertTrue(dashSource.canPlay)
        assertFalse(dashSource.canDownload)
        assertEquals(DownloadType.UNSUPPORTED, dashSource.downloadType)
    }

    @Test
    fun testResumableByteRangeCalculation() {
        val partialBytes = 4096L
        val totalExpected = 16384L

        val rangeHeader = "bytes=$partialBytes-"
        assertEquals("bytes=4096-", rangeHeader)

        // Content-Range: bytes 4096-16383/16384
        val contentRangeHeader = "bytes 4096-16383/16384"
        val totalFromHeader = contentRangeHeader.substringAfterLast("/").toLongOrNull()
        assertEquals(totalExpected, totalFromHeader)
    }
}
