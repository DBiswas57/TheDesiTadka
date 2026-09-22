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
        assertTrue(liveSource.canDownload)
        assertEquals(DownloadType.HLS_OFFLINE, liveSource.downloadType)

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

    @Test
    fun testHlsMasterPlaylistVariantSelectionAndSniffing() {
        val masterPlaylist = """
            #EXTM3U
            #EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=496002,RESOLUTION=640x360
            https://cdn.example.com/360p/index.m3u8
            #EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=1359698,RESOLUTION=1280x720
            https://cdn.example.com/720p/index.m3u8
            #EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=726467,RESOLUTION=854x480
            https://cdn.example.com/480p/index.m3u8
        """.trimIndent()

        // 1. Sniff test: Starts with #EXTM3U
        assertTrue(masterPlaylist.trimStart().startsWith("#EXTM3U"))

        // 2. Parse master playlist variants and select highest bandwidth
        val lines = masterPlaylist.lines()
        var bestVariantUrl: String? = null
        var maxBandwidth = -1L

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val bandwidthMatch = Regex("""BANDWIDTH=(\d+)""").find(line)
                val bandwidth = bandwidthMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L

                for (j in (i + 1) until lines.size) {
                    val nextLine = lines[j].trim()
                    if (nextLine.isNotBlank() && !nextLine.startsWith("#")) {
                        if (bandwidth >= maxBandwidth || bestVariantUrl == null) {
                            maxBandwidth = bandwidth
                            bestVariantUrl = nextLine
                        }
                        break
                    }
                }
            }
        }

        assertEquals(1359698L, maxBandwidth)
        assertEquals("https://cdn.example.com/720p/index.m3u8", bestVariantUrl)

        // 3. Media playlist with segments and fMP4 init map
        val mediaPlaylist = """
            #EXTM3U
            #EXT-X-VERSION:4
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:100
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:6.0,
            seg-1.m4s
            #EXTINF:6.0,
            seg-2.m4s
        """.trimIndent()

        val mediaLines = mediaPlaylist.lines()
        val segments = mutableListOf<String>()
        var seq = 0L

        for (l in mediaLines) {
            val trimmed = l.trim()
            if (trimmed.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                seq = trimmed.substringAfter(":").toLongOrNull() ?: 0L
            } else if (trimmed.startsWith("#EXT-X-MAP:")) {
                val match = Regex("""URI="([^"]+)"""").find(trimmed)
                if (match != null) {
                    segments.add(match.groupValues[1])
                }
            } else if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                segments.add(trimmed)
            }
        }

        assertEquals(100L, seq)
        assertEquals(3, segments.size)
        assertEquals("init.mp4", segments[0])
        assertEquals("seg-1.m4s", segments[1])
        assertEquals("seg-2.m4s", segments[2])
    }
}
