package com.thedesitadka.app.download

import android.app.Notification
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.webkit.CookieManager
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.thedesitadka.app.storage.AppDatabase
import com.thedesitadka.app.storage.DownloadRecordEntity
import com.thedesitadka.app.storage.DownloadStatus
import com.thedesitadka.core.network.NetworkClient
import com.thedesitadka.core.security.FileNameSanitizer
import com.thedesitadka.core.security.StreamHubLogger
import com.thedesitadka.core.security.UrlSecurityValidator
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class DownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_MEDIA_URL = "media_url"
        const val KEY_TITLE = "title"
        const val KEY_PROVIDER_ID = "provider_id"
        const val KEY_IS_PAUSED = "is_paused"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_MIME_TYPE = "mime_type"
        const val KEY_IS_HLS = "is_hls"
        const val BUFFER_SIZE = 65536 // 64 KB buffer for high-speed streaming I/O
        const val MAX_RETRIES = 3
    }

    private val downloadDao = AppDatabase.getInstance(context).downloadDao()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: ""
        val title = inputData.getString(KEY_TITLE) ?: "Media Download"
        val notifId = DownloadNotificationManager.getNotificationId(downloadId)
        val initialNotif = DownloadNotificationManager.buildDownloadingNotification(
            context, title, 0, 0L, 0L, downloadId
        )
        return DownloadNotificationManager.createForegroundInfo(context, initialNotif, notifId)
    }

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return Result.failure()
        val mediaUrl = inputData.getString(KEY_MEDIA_URL) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Media Download"
        val providerId = inputData.getString(KEY_PROVIDER_ID) ?: ""
        val isWifiOnly = inputData.getBoolean(KEY_WIFI_ONLY, false)
        val mimeType = inputData.getString(KEY_MIME_TYPE) ?: ""
        val isHlsExplicit = inputData.getBoolean(KEY_IS_HLS, false)

        StreamHubLogger.log(
            StreamHubLogger.Category.DOWNLOAD,
            "INFO",
            "DOWNLOAD_STARTED: id=$downloadId, provider=$providerId, title='$title', wifiOnly=$isWifiOnly"
        )

        // Wi-Fi Only pre-check: verify network is Wi-Fi or unmetered
        if (isWifiOnly && !isWifiOrUnmeteredNetwork(context)) {
            StreamHubLogger.w("DownloadWorker", "DOWNLOAD_WAITING_WIFI: ID $downloadId waiting for Wi-Fi connection")
            val existing = downloadDao.getDownload(downloadId)
            if (existing != null) {
                downloadDao.updateDownload(
                    existing.copy(
                        status = DownloadStatus.QUEUED,
                        error = "Waiting for Wi-Fi network",
                        speedBytesPerSec = 0L
                    )
                )
            }
            return Result.retry()
        }

        // Validate URL Safety
        if (!UrlSecurityValidator.isUrlSafe(mediaUrl)) {
            StreamHubLogger.e("DownloadWorker", "DOWNLOAD_FAILED: Blocked download of unsafe URL: $mediaUrl")
            val record = downloadDao.getDownload(downloadId)
            if (record != null) {
                downloadDao.updateDownload(
                    record.copy(
                        status = DownloadStatus.FAILED,
                        error = "Blocked unsafe or non-HTTPS URL"
                    )
                )
            }
            return Result.failure()
        }

        // Initialize notification & promotion to foreground service safely
        val notifId = DownloadNotificationManager.getNotificationId(downloadId)
        val initialNotif = DownloadNotificationManager.buildDownloadingNotification(context, title, 0, 0L, 0L, downloadId)
        safeSetForeground(
            DownloadNotificationManager.createForegroundInfo(context, initialNotif, notifId),
            notifId,
            initialNotif
        )

        val record = downloadDao.getDownload(downloadId)
        if (record == null) {
            StreamHubLogger.e("DownloadWorker", "DOWNLOAD_FAILED: Download record not found for ID: $downloadId")
            return Result.failure()
        }

        // Centralized Filename Sanitization
        val finalFileName = FileNameSanitizer.buildSafeFileName(title, downloadId, "mp4")
        val partFileName = FileNameSanitizer.buildSafeFileName(title, downloadId, "part")

        // 1DM-Style Dual-Engine Storage Setup: Never save in private app data folders
        val publicMoviesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "TheDesiTadka")
        val directPartFile = File(publicMoviesDir, partFileName)
        val directFinalFile = File(publicMoviesDir, finalFileName)

        var isDirectFileMode = false
        var directRaf: RandomAccessFile? = null
        var mediaStoreUri: Uri? = null
        var mediaStorePfd: ParcelFileDescriptor? = null
        var mediaStoreStream: FileOutputStream? = null

        // Try direct POSIX access first (1DM Mode)
        try {
            if (!publicMoviesDir.exists()) publicMoviesDir.mkdirs()
            directRaf = RandomAccessFile(directPartFile, "rw")
            isDirectFileMode = true
            StreamHubLogger.i("DownloadWorker", "Direct POSIX Storage Engine active at: ${directPartFile.absolutePath}")
        } catch (e: Exception) {
            StreamHubLogger.w("DownloadWorker", "Direct POSIX write unavailable (${e.message}). Initializing MediaStore Engine for public storage...")
            directRaf = null
            isDirectFileMode = false
        }

        // If direct file access is blocked by Scoped Storage, initialize MediaStore engine in Movies/TheDesiTadka
        if (!isDirectFileMode) {
            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, finalFileName)
                    put(MediaStore.Video.Media.TITLE, title)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/TheDesiTadka")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }
                val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IOException("Failed to create MediaStore entry in public Movies/TheDesiTadka")

                mediaStoreUri = uri
                val pfd = context.contentResolver.openFileDescriptor(uri, "rw")
                    ?: context.contentResolver.openFileDescriptor(uri, "w")
                    ?: throw IOException("Failed to open file descriptor for MediaStore entry: $uri")
                mediaStorePfd = pfd
                mediaStoreStream = FileOutputStream(pfd.fileDescriptor)
                StreamHubLogger.i("DownloadWorker", "MediaStore Public Storage Engine active at: $uri")
            } catch (e: Exception) {
                StreamHubLogger.e("DownloadWorker", "Both Direct POSIX and MediaStore storage failed: ${e.message}")
                return handleRetryableError(record, "Storage initialization error: ${e.message}")
            }
        }

        val destinationDisplayPath = if (isDirectFileMode) directFinalFile.absolutePath else (mediaStoreUri?.toString() ?: "/storage/emulated/0/Movies/TheDesiTadka/$finalFileName")

        var currentRecord = record.copy(
            status = DownloadStatus.DOWNLOADING,
            localFilePath = destinationDisplayPath,
            error = null
        )

        try {
            downloadDao.updateDownload(currentRecord)

            val existingBytes = if (isDirectFileMode && directPartFile.exists()) directPartFile.length() else 0L

            val requestBuilder = Request.Builder().url(mediaUrl)
            requestBuilder.header("User-Agent", NetworkClient.DEFAULT_USER_AGENT)

            // Inject Referer based on provider or URL host
            val urlLower = mediaUrl.lowercase()
            val referer = when {
                urlLower.contains("twimg.com") -> ""
                urlLower.contains("pornhouse.me") || urlLower.contains("cdn.pornhouse.me") || providerId == "pornhouse" -> "https://pornhouse.me/"
                urlLower.contains("pornhd4k.net") || urlLower.contains("cdnamz.me") || providerId == "pornhd4k" -> "https://pornhd4k.net/"
                urlLower.contains("pornmz.com") || providerId == "pornmz" -> "https://pornmz.com/"
                urlLower.contains("pornstars.tube") || providerId == "pornstars_tube" -> "https://pornstars.tube/"
                urlLower.contains("sxyprn.com") || urlLower.contains("trafficdeposit.com") || urlLower.contains("bxcdn.net") || urlLower.contains("bkcdn.net") || providerId == "sxyprn" -> "https://sxyprn.com/"
                urlLower.contains("max.porn") || providerId == "max" -> "https://max.porn/"
                urlLower.contains("ok.porn") || providerId == "ok_porn" -> "https://ok.porn/"
                urlLower.contains("ok.xxx") || providerId == "ok_xxx" -> "https://ok.xxx/"
                urlLower.contains("perfectgirls.xxx") || providerId == "perfectgirls" -> "https://www.perfectgirls.xxx/"
                urlLower.contains("pornhat.com") || providerId == "pornhat" -> "https://www.pornhat.com/"
                urlLower.contains("netfapx.com") || urlLower.contains("videos.netfapx.com") || providerId == "netfapx" -> "https://netfapx.com/"
                urlLower.contains("porn4days.pw") || urlLower.contains("iceyfile.net") || providerId == "porn4days" -> "https://porn4days.pw/"
                urlLower.contains("bigcdn.cc") || urlLower.contains("mydaddy.cc") || urlLower.contains("hqporner") || providerId == "hqporner" -> "https://hqporner.com/"
                urlLower.contains("ixiporn") || providerId == "ixiporn" -> "https://ixiporn.live/"
                urlLower.contains("wowuncut") || providerId == "wowuncut" -> "https://wowuncut.com/"
                urlLower.contains("antarvasna") || providerId == "antarvasnabf" -> "https://antarvasnabf.com/"
                urlLower.contains("ixifile") || urlLower.contains("cdn2.ixifile.xyz") -> {
                    if (providerId == "wowuncut") "https://wowuncut.com/" else "https://ixiporn.live/"
                }
                urlLower.contains("tube279.com") || urlLower.contains("siesta583") -> "https://tube279.com/"
                urlLower.contains("tnmr.org") || urlLower.contains("lulucdn") || urlLower.contains("lulustream") || urlLower.contains("luluvdo") -> "https://luluvdo.com/"
                urlLower.contains("tpead.net") || urlLower.contains("streamtape.com") || urlLower.contains("tapecontent.net") -> "https://streamtape.com/"
                urlLower.contains("mydown.biz") || urlLower.contains("masahub") || providerId == "masahub2" || providerId == "lalamasa" || urlLower.contains("lalamasa") -> if (providerId == "lalamasa" || urlLower.contains("lalamasa")) "https://lalamasa.mobi/" else "https://masahub2.com/"
                urlLower.contains("streamoupload") || providerId == "watchoerotic" || providerId?.startsWith("prmovies") == true -> "https://streamoupload.xyz/"
                urlLower.contains("pvtcdn.com") || urlLower.contains("masa49") || providerId == "masa49" -> "https://www.masa49.nl/"
                urlLower.contains("kamababa") || providerId == "kamababa1" -> "https://www.mykamababa.com/"
                urlLower.contains("fry99") || providerId == "fry99" -> "https://fry99.cc/"
                urlLower.contains("hitmaal") || providerId == "hitmaal" -> "https://hitmaal.io/"
                urlLower.contains("fsiblog") || providerId == "fsiblogxx" -> "https://fsiblogxx.com/"
                urlLower.contains("webxseries") || providerId == "webxseries" -> "https://webxseries.hot/"
                urlLower.contains("desisex") || providerId == "desisex" -> "https://desisex.site/"
                urlLower.contains("pornx11") || providerId == "pornx11" -> "https://pornx11.com/"
                urlLower.contains("xhpingcdn") || urlLower.contains("xhcdn") || urlLower.contains("xhamster") || providerId == "xhamster" -> "https://xhamster.desi/"
                urlLower.contains("chiggywiggy") || providerId == "chiggywiggy" -> "https://chiggywiggy.com/"
                urlLower.contains("desibabe") || providerId == "desibabe" || urlLower.contains("downloaddirect") -> "https://desibabe.to/"
                urlLower.contains("desigirlxx") || providerId == "desigirlxx" || urlLower.contains("playmate.to") -> "https://desigirlxx.beer/"
                urlLower.contains("desivideo") || providerId == "desivideo" -> "https://desivideo.net/"
                urlLower.contains("definebabe.com") || providerId == "definebabe" -> "https://www.definebabe.com/"
                urlLower.contains("3movs.com") || providerId == "three_movs" -> "https://www.3movs.com/"
                urlLower.contains("txxx.com") || urlLower.contains("txxx.tube") || providerId == "txxx" -> "https://txxx.com/"
                urlLower.contains("upornia.com") || providerId == "upornia" -> "https://upornia.com/"
                urlLower.contains("hdzog.com") || providerId == "hdzog" -> "https://hdzog.com/"
                urlLower.contains("hello.porn") || urlLower.contains("privatehost.com") || providerId == "hello" -> "https://hello.porn/"
                urlLower.contains("movienerds") || providerId == "movienerds" -> "https://movienerds.site/"
                urlLower.contains("cineapse") || providerId == "cineapse" -> "https://cineapse.net/"
                else -> "https://${android.net.Uri.parse(mediaUrl).host ?: "example.com"}/"
            }
            if (referer.isNotBlank()) {
                requestBuilder.header("Referer", referer)
            }

            // Inject Cookies if available
            val cookies = try { CookieManager.getInstance().getCookie(mediaUrl) } catch (e: Exception) { null }
            if (!cookies.isNullOrBlank()) {
                requestBuilder.header("Cookie", cookies)
            }

            // Multi-layered HLS pre-flight detection
            val isKnownHls = isHlsExplicit ||
                    mimeType.contains("mpegurl", ignoreCase = true) ||
                    record.mimeType.contains("mpegurl", ignoreCase = true) ||
                    urlLower.contains(".m3u8") ||
                    urlLower.contains("/hls/") ||
                    providerId in listOf("hello", "max", "ok_porn", "ok_xxx", "perfectgirls", "pornhat") ||
                    urlLower.contains("hello.porn") ||
                    urlLower.contains("max.porn") ||
                    urlLower.contains("ok.porn") ||
                    urlLower.contains("ok.xxx") ||
                    urlLower.contains("perfectgirls.xxx") ||
                    urlLower.contains("pornhat.com") ||
                    urlLower.contains("privatehost.com")

            if (isKnownHls) {
                return downloadHlsStream(
                    downloadId = downloadId,
                    mediaUrl = mediaUrl,
                    title = title,
                    providerId = providerId,
                    referer = referer,
                    cookies = cookies,
                    isDirectFileMode = isDirectFileMode,
                    directPartFile = directPartFile,
                    directFinalFile = directFinalFile,
                    directRaf = directRaf,
                    mediaStoreStream = mediaStoreStream,
                    mediaStoreUri = mediaStoreUri,
                    mediaStorePfd = mediaStorePfd,
                    destinationDisplayPath = destinationDisplayPath,
                    currentRecord = currentRecord
                )
            }

            // HTTP Range request for resuming partial downloads in direct mode
            var isResume = false
            if (isDirectFileMode && existingBytes > 0L) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
                isResume = true
            }

            val request = requestBuilder.build()
            StreamHubLogger.log(
                StreamHubLogger.Category.DOWNLOAD,
                "INFO",
                "DOWNLOAD_REQUEST: id=$downloadId, directMode=$isDirectFileMode, resuming=$isResume, existingBytes=$existingBytes"
            )

            NetworkClient.okHttpClient.newCall(request).execute().use { response ->
                val responseCode = response.code
                val effectiveUrl = response.request.url.toString()
                val contentType = response.header("Content-Type", "")?.lowercase() ?: ""

                // Dynamic Response Stream Sniffing:
                // If the response is an HLS playlist (MIME type, redirected URL, or #EXTM3U peek)
                val peekBytes = try { response.peekBody(512).string().trimStart() } catch (e: Exception) { "" }
                val isDynamicHls = contentType.contains("mpegurl") ||
                        contentType.contains("x-mpegurl") ||
                        effectiveUrl.contains(".m3u8", ignoreCase = true) ||
                        effectiveUrl.contains("/hls/", ignoreCase = true) ||
                        peekBytes.startsWith("#EXTM3U") ||
                        peekBytes.startsWith("#EXT-X-")

                if (isDynamicHls) {
                    StreamHubLogger.i(
                        "DownloadWorker",
                        "Dynamic HLS stream detected for ID $downloadId: $mediaUrl (effective: $effectiveUrl, Content-Type: $contentType). Routing to HLS segment downloader..."
                    )
                    return downloadHlsStream(
                        downloadId = downloadId,
                        mediaUrl = effectiveUrl,
                        title = title,
                        providerId = providerId,
                        referer = referer,
                        cookies = cookies,
                        isDirectFileMode = isDirectFileMode,
                        directPartFile = directPartFile,
                        directFinalFile = directFinalFile,
                        directRaf = directRaf,
                        mediaStoreStream = mediaStoreStream,
                        mediaStoreUri = mediaStoreUri,
                        mediaStorePfd = mediaStorePfd,
                        destinationDisplayPath = destinationDisplayPath,
                        currentRecord = currentRecord
                    )
                }

                StreamHubLogger.log(
                    StreamHubLogger.Category.DOWNLOAD,
                    "INFO",
                    "DOWNLOAD_RESPONSE: id=$downloadId, code=$responseCode"
                )

                // Handle HTTP response codes
                val appendMode: Boolean
                var totalBytes = currentRecord.totalBytes

                if (isResume && responseCode == 416) {
                    StreamHubLogger.w("DownloadWorker", "HTTP 416 Range Not Satisfiable. Resetting partial file...")
                    if (isDirectFileMode && directPartFile.exists()) {
                        directPartFile.delete()
                    }
                    return handleRetryableError(currentRecord, "Range reset - retrying from beginning")
                } else if (isResume && responseCode == 206) {
                    appendMode = true
                    val contentRange = response.header("Content-Range")
                    if (!contentRange.isNullOrBlank() && contentRange.contains("/")) {
                        totalBytes = contentRange.substringAfterLast("/").toLongOrNull() ?: totalBytes
                    }
                } else if (response.isSuccessful) {
                    appendMode = false
                    val len = response.body?.contentLength() ?: -1L
                    if (len > 0L) totalBytes = len
                } else if (responseCode in 400..499) {
                    // Client error (401, 403, 404): non-retryable
                    val err = "HTTP $responseCode: ${if (responseCode == 403) "Forbidden / Expired URL" else "Not Found"}"
                    StreamHubLogger.e("DownloadWorker", "DOWNLOAD_FAILED: id=$downloadId, non-retryable error: $err")
                    currentRecord = currentRecord.copy(status = DownloadStatus.FAILED, error = err, speedBytesPerSec = 0L)
                    downloadDao.updateDownload(currentRecord)
                    postFailedNotification(title, err)
                    return Result.failure()
                } else {
                    // Server error (5xx)
                    val err = "HTTP $responseCode: Server Error"
                    StreamHubLogger.w("DownloadWorker", "DOWNLOAD_ERROR: id=$downloadId, code=$responseCode, will retry")
                    return handleRetryableError(currentRecord, err)
                }

                val body = response.body ?: return handleRetryableError(currentRecord, "Empty response body from server")
                val inputStream = body.byteStream()

                if (isDirectFileMode && directRaf != null) {
                    if (appendMode) {
                        directRaf.seek(existingBytes)
                    } else {
                        directRaf.setLength(0L)
                    }
                }

                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                var currentDownloaded = if (appendMode && isDirectFileMode) existingBytes else 0L

                var lastSampleTime = System.currentTimeMillis()
                var bytesReadSinceSample = 0L
                var currentSpeed = 0L
                var etaSeconds = 0L
                var lastUiUpdateTime = 0L

                try {
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (isStopped) {
                            val isPaused = inputData.getBoolean(KEY_IS_PAUSED, false)
                            val finalStatus = if (isPaused) DownloadStatus.PAUSED else DownloadStatus.CANCELLED
                            StreamHubLogger.log(
                                StreamHubLogger.Category.DOWNLOAD,
                                "INFO",
                                "DOWNLOAD_${finalStatus.name}: id=$downloadId, downloaded=$currentDownloaded"
                            )
                            currentRecord = currentRecord.copy(
                                status = finalStatus,
                                downloadedBytes = currentDownloaded,
                                totalBytes = totalBytes,
                                speedBytesPerSec = 0L
                            )
                            downloadDao.updateDownload(currentRecord)
                            if (finalStatus == DownloadStatus.CANCELLED) {
                                if (isDirectFileMode) {
                                    directPartFile.delete()
                                } else {
                                    mediaStoreUri?.let { uri ->
                                        try { context.contentResolver.delete(uri, null, null) } catch (e: Exception) {}
                                    }
                                }
                            }
                            return Result.failure()
                        }

                        if (isDirectFileMode && directRaf != null) {
                            directRaf.write(buffer, 0, bytesRead)
                        } else if (mediaStoreStream != null) {
                            mediaStoreStream.write(buffer, 0, bytesRead)
                        }

                        currentDownloaded += bytesRead
                        bytesReadSinceSample += bytesRead

                        val now = System.currentTimeMillis()
                        val deltaSample = now - lastSampleTime
                        if (deltaSample >= 500) { // Update speed metrics every 500ms
                            currentSpeed = (bytesReadSinceSample * 1000) / deltaSample
                            if (currentSpeed > 0 && totalBytes > currentDownloaded) {
                                etaSeconds = (totalBytes - currentDownloaded) / currentSpeed
                            }
                            lastSampleTime = now
                            bytesReadSinceSample = 0L
                        }

                        // Update DB & Notification periodically (every 1 sec)
                        if (now - lastUiUpdateTime >= 1000L) {
                            lastUiUpdateTime = now

                            // Verify Wi-Fi connectivity has not been lost mid-download
                            if (isWifiOnly && !isWifiOrUnmeteredNetwork(context)) {
                                StreamHubLogger.w("DownloadWorker", "DOWNLOAD_INTERRUPTED_WIFI: Wi-Fi lost for id=$downloadId")
                                currentRecord = currentRecord.copy(
                                    status = DownloadStatus.QUEUED,
                                    downloadedBytes = currentDownloaded,
                                    totalBytes = totalBytes,
                                    error = "Waiting for Wi-Fi network",
                                    speedBytesPerSec = 0L
                                )
                                downloadDao.updateDownload(currentRecord)
                                return Result.retry()
                            }

                            val progress = if (totalBytes > 0) ((currentDownloaded * 100) / totalBytes).toInt() else -1
                            setProgress(
                                workDataOf(
                                    "progress" to progress,
                                    "speed" to currentSpeed,
                                    "eta" to etaSeconds
                                )
                            )
                            currentRecord = currentRecord.copy(
                                status = DownloadStatus.DOWNLOADING,
                                downloadedBytes = currentDownloaded,
                                totalBytes = totalBytes,
                                progress = progress,
                                speedBytesPerSec = currentSpeed,
                                etaSeconds = etaSeconds
                            )
                            downloadDao.updateDownload(currentRecord)
                            val progressNotif = DownloadNotificationManager.buildDownloadingNotification(
                                context, title, progress, currentSpeed, etaSeconds, downloadId
                            )
                            safeSetForeground(
                                DownloadNotificationManager.createForegroundInfo(
                                    context,
                                    progressNotif,
                                    notifId
                                ),
                                notifId,
                                progressNotif
                            )
                        }
                    }

                    if (isDirectFileMode && directRaf != null) {
                        directRaf.fd.sync()
                    } else if (mediaStoreStream != null) {
                        mediaStoreStream.flush()
                    }
                } finally {
                    try { directRaf?.close() } catch (e: Exception) {}
                    try { mediaStoreStream?.flush(); mediaStoreStream?.close() } catch (e: Exception) {}
                    try { mediaStorePfd?.close() } catch (e: Exception) {}
                    try { inputStream.close() } catch (e: Exception) {}
                }

                // Finalize Download
                val finalPath: String
                if (isDirectFileMode) {
                    // Atomic Rename: .part -> .mp4
                    if (directFinalFile.exists()) directFinalFile.delete()
                    val renameSuccess = directPartFile.renameTo(directFinalFile)
                    if (!renameSuccess) {
                        directPartFile.copyTo(directFinalFile, overwrite = true)
                        directPartFile.delete()
                    }
                    finalPath = directFinalFile.absolutePath

                    // Index file in MediaStore so it appears in device gallery and file managers
                    try {
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(directFinalFile.absolutePath),
                            arrayOf("video/mp4"),
                            null
                        )
                    } catch (e: Exception) {
                        StreamHubLogger.w("DownloadWorker", "MediaScanner failed: ${e.message}")
                    }
                } else {
                    // MediaStore mode: finalize by un-pending
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaStoreUri != null) {
                        try {
                            val completedValues = ContentValues().apply {
                                put(MediaStore.Video.Media.IS_PENDING, 0)
                            }
                            context.contentResolver.update(mediaStoreUri, completedValues, null, null)
                        } catch (e: Exception) {
                            StreamHubLogger.w("DownloadWorker", "Could not mark MediaStore entry as non-pending: ${e.message}")
                        }
                    }
                    finalPath = mediaStoreUri?.toString() ?: "/storage/emulated/0/Movies/TheDesiTadka/$finalFileName"
                }

                currentRecord = currentRecord.copy(
                    status = DownloadStatus.COMPLETED,
                    localFilePath = finalPath,
                    downloadedBytes = currentDownloaded,
                    totalBytes = currentDownloaded,
                    progress = 100,
                    speedBytesPerSec = 0L,
                    etaSeconds = 0L,
                    completedAt = System.currentTimeMillis()
                )
                downloadDao.updateDownload(currentRecord)

                StreamHubLogger.log(
                    StreamHubLogger.Category.DOWNLOAD,
                    "INFO",
                    "DOWNLOAD_COMPLETED: id=$downloadId, path='$finalPath' ($currentDownloaded bytes)"
                )

                postCompletedNotification(title, downloadId)
            }

            return Result.success()
        } catch (e: Exception) {
            StreamHubLogger.e("DownloadWorker", "DOWNLOAD_FAILED: id=$downloadId, error: ${e.message}")
            return when (e) {
                is SocketTimeoutException, is UnknownHostException, is IOException -> {
                    handleRetryableError(currentRecord, e.message ?: "Network error")
                }
                else -> {
                    currentRecord = currentRecord.copy(
                        status = DownloadStatus.FAILED,
                        error = e.message ?: "Unexpected error",
                        speedBytesPerSec = 0L
                    )
                    downloadDao.updateDownload(currentRecord)
                    postFailedNotification(title, e.message)
                    Result.failure()
                }
            }
        }
    }

    private suspend fun handleRetryableError(
        record: DownloadRecordEntity,
        errorMessage: String
    ): Result {
        val nextRetryCount = record.retryCount + 1
        if (nextRetryCount <= MAX_RETRIES) {
            downloadDao.updateDownload(
                record.copy(
                    status = DownloadStatus.RETRYING,
                    error = errorMessage,
                    retryCount = nextRetryCount,
                    speedBytesPerSec = 0L
                )
            )
            StreamHubLogger.log(
                StreamHubLogger.Category.DOWNLOAD,
                "INFO",
                "DOWNLOAD_RETRY: id=${record.id}, attempt=$nextRetryCount/$MAX_RETRIES"
            )
            return Result.retry()
        } else {
            downloadDao.updateDownload(
                record.copy(
                    status = DownloadStatus.FAILED,
                    error = "Failed after $MAX_RETRIES attempts: $errorMessage",
                    speedBytesPerSec = 0L
                )
            )
            postFailedNotification(record.title, errorMessage, record.id)
            return Result.failure()
        }
    }

    private suspend fun safeSetForeground(
        info: ForegroundInfo,
        notificationId: Int = DownloadNotificationManager.NOTIFICATION_ID,
        notification: Notification? = null
    ) {
        try {
            setForeground(info)
        } catch (e: Exception) {
            StreamHubLogger.w("DownloadWorker", "Could not set foreground service: ${e.message}")
        }
        if (notification != null) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.notify(notificationId, notification)
            } catch (e: Exception) {
                StreamHubLogger.w("DownloadWorker", "Could not update direct notification: ${e.message}")
            }
        }
    }

    private fun postCompletedNotification(title: String, downloadId: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(DownloadNotificationManager.getNotificationId(downloadId))
            val notification = DownloadNotificationManager.buildCompletedNotification(context, title, downloadId)
            val compId = DownloadNotificationManager.getCompletedNotificationId(downloadId)
            notificationManager?.notify(compId, notification)
        } catch (e: Exception) {
            StreamHubLogger.w("DownloadWorker", "Could not post completion notification: ${e.message}")
        }
    }

    private fun postFailedNotification(title: String, error: String?, downloadId: String = "") {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (downloadId.isNotBlank()) {
                notificationManager?.cancel(DownloadNotificationManager.getNotificationId(downloadId))
            }
            val notification = DownloadNotificationManager.buildFailedNotification(context, title, error, downloadId)
            val notifId = if (downloadId.isNotBlank()) DownloadNotificationManager.getCompletedNotificationId(downloadId) else title.hashCode()
            notificationManager?.notify(notifId, notification)
        } catch (e: Exception) {
            StreamHubLogger.w("DownloadWorker", "Could not post failure notification: ${e.message}")
        }
    }

    private fun isWifiOrUnmeteredNetwork(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun downloadHlsStream(
        downloadId: String,
        mediaUrl: String,
        title: String,
        providerId: String,
        referer: String,
        cookies: String?,
        isDirectFileMode: Boolean,
        directPartFile: File,
        directFinalFile: File,
        directRaf: RandomAccessFile?,
        mediaStoreStream: FileOutputStream?,
        mediaStoreUri: Uri?,
        mediaStorePfd: ParcelFileDescriptor?,
        destinationDisplayPath: String,
        currentRecord: DownloadRecordEntity
    ): Result {
        StreamHubLogger.i("DownloadWorker", "Starting HLS stream download for ID $downloadId: $mediaUrl")
        var activeRecord = currentRecord
        val notifId = DownloadNotificationManager.getNotificationId(downloadId)
        try {
            fun resolveHlsUrl(baseUrl: String, rel: String): String {
                val trimmed = rel.trim()
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
                return try {
                    java.net.URI(baseUrl).resolve(trimmed).toString()
                } catch (e: Exception) {
                    if (baseUrl.endsWith("/")) "$baseUrl$trimmed" else "$baseUrl/$trimmed"
                }
            }

            // If direct RAF was opened, ensure starting cleanly from offset 0 for segment assembly
            try { directRaf?.setLength(0L) } catch (e: Exception) {}

            val playlistRequest = Request.Builder()
                .url(mediaUrl)
                .header("User-Agent", NetworkClient.DEFAULT_USER_AGENT)
                .apply { if (referer.isNotBlank()) header("Referer", referer) }
                .apply { if (!cookies.isNullOrBlank()) header("Cookie", cookies) }
                .build()

            var effectivePlaylistUrl = mediaUrl
            val playlistContent = NetworkClient.okHttpClient.newCall(playlistRequest).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("HTTP ${resp.code} while loading HLS playlist from $mediaUrl")
                }
                effectivePlaylistUrl = resp.request.url.toString()
                resp.body?.string() ?: ""
            }

            if (playlistContent.isBlank()) {
                return handleRetryableError(activeRecord, "Failed to load HLS playlist content")
            }

            var targetMediaPlaylistUrl = effectivePlaylistUrl
            var mediaPlaylistContent = playlistContent

            // Master playlist multi-variant resolution: pick highest bandwidth stream
            if (playlistContent.contains("#EXT-X-STREAM-INF")) {
                val lines = playlistContent.lines()
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
                                    bestVariantUrl = resolveHlsUrl(effectivePlaylistUrl, nextLine)
                                }
                                break
                            }
                        }
                    }
                }

                if (bestVariantUrl != null) {
                    StreamHubLogger.i("DownloadWorker", "Selected HLS variant stream: $bestVariantUrl (bandwidth: $maxBandwidth)")
                    val variantRequest = Request.Builder()
                        .url(bestVariantUrl)
                        .header("User-Agent", NetworkClient.DEFAULT_USER_AGENT)
                        .apply { if (referer.isNotBlank()) header("Referer", referer) }
                        .apply { if (!cookies.isNullOrBlank()) header("Cookie", cookies) }
                        .build()

                    mediaPlaylistContent = NetworkClient.okHttpClient.newCall(variantRequest).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            throw IOException("HTTP ${resp.code} loading variant playlist $bestVariantUrl")
                        }
                        targetMediaPlaylistUrl = resp.request.url.toString()
                        resp.body?.string() ?: ""
                    }
                }
            }

            val mediaLines = mediaPlaylistContent.lines()
            val segmentUrls = mutableListOf<String>()
            var keyUrl: String? = null
            var keyBytes: ByteArray? = null
            var explicitIvBytes: ByteArray? = null
            var mediaSequence = 0L

            for (line in mediaLines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                    mediaSequence = trimmed.substringAfter(":").toLongOrNull() ?: 0L
                } else if (trimmed.startsWith("#EXT-X-KEY:")) {
                    val method = Regex("""METHOD=([A-Z0-9-]+)""").find(trimmed)?.groupValues?.get(1)
                    if (method == "AES-128") {
                        val uriMatch = Regex("""URI="([^"]+)"""").find(trimmed)
                        if (uriMatch != null) {
                            keyUrl = resolveHlsUrl(targetMediaPlaylistUrl, uriMatch.groupValues[1])
                            val ivMatch = Regex("""IV=0x([0-9a-fA-F]+)""").find(trimmed)
                            if (ivMatch != null) {
                                val ivHex = ivMatch.groupValues[1]
                                explicitIvBytes = hexStringToByteArray(ivHex)
                            }
                        }
                    }
                } else if (trimmed.startsWith("#EXT-X-MAP:")) {
                    val uriMatch = Regex("""URI="([^"]+)"""").find(trimmed)
                    if (uriMatch != null) {
                        val initSegUrl = resolveHlsUrl(targetMediaPlaylistUrl, uriMatch.groupValues[1])
                        segmentUrls.add(initSegUrl)
                    }
                } else if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                    segmentUrls.add(resolveHlsUrl(targetMediaPlaylistUrl, trimmed))
                }
            }

            if (segmentUrls.isEmpty()) {
                StreamHubLogger.e("DownloadWorker", "No video segments found in HLS playlist")
                return handleRetryableError(activeRecord, "No video segments found in HLS playlist")
            }

            if (!keyUrl.isNullOrBlank()) {
                try {
                    val keyRequest = Request.Builder()
                        .url(keyUrl)
                        .header("User-Agent", NetworkClient.DEFAULT_USER_AGENT)
                        .apply { if (referer.isNotBlank()) header("Referer", referer) }
                        .apply { if (!cookies.isNullOrBlank()) header("Cookie", cookies) }
                        .build()
                    keyBytes = NetworkClient.okHttpClient.newCall(keyRequest).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.bytes() else null
                    }
                    StreamHubLogger.i("DownloadWorker", "Loaded AES key for HLS stream ($keyUrl, ${keyBytes?.size} bytes)")
                } catch (e: Exception) {
                    StreamHubLogger.w("DownloadWorker", "Failed to load AES key ($keyUrl): ${e.message}")
                }
            }

            StreamHubLogger.i("DownloadWorker", "HLS stream contains ${segmentUrls.size} segments (encrypted=${keyBytes != null})")

            val totalSegments = segmentUrls.size
            var downloadedBytes = 0L
            val startTime = System.currentTimeMillis()
            var lastProgressTime = 0L

            for ((index, segUrl) in segmentUrls.withIndex()) {
                if (isStopped) {
                    StreamHubLogger.i("DownloadWorker", "DownloadWorker stopped during HLS download (at segment $index/$totalSegments)")
                    try { directRaf?.close() } catch (e: Exception) {}
                    try { mediaStoreStream?.close() } catch (e: Exception) {}
                    try { mediaStorePfd?.close() } catch (e: Exception) {}
                    return Result.retry()
                }

                var segData: ByteArray? = null
                var lastErr: Exception? = null
                for (attempt in 1..3) {
                    try {
                        val segRequest = Request.Builder()
                            .url(segUrl)
                            .header("User-Agent", NetworkClient.DEFAULT_USER_AGENT)
                            .apply { if (referer.isNotBlank()) header("Referer", referer) }
                            .apply { if (!cookies.isNullOrBlank()) header("Cookie", cookies) }
                            .build()

                        segData = NetworkClient.okHttpClient.newCall(segRequest).execute().use { resp ->
                            if (!resp.isSuccessful) {
                                throw IOException("HTTP ${resp.code} while downloading HLS segment $index")
                            }
                            resp.body?.bytes() ?: ByteArray(0)
                        }
                        break
                    } catch (e: Exception) {
                        lastErr = e
                        if (attempt < 3) {
                            kotlinx.coroutines.delay(400L * attempt)
                        }
                    }
                }

                if (segData == null) {
                    throw lastErr ?: IOException("Failed to download segment $index after 3 attempts")
                }

                val outputBytes = if (keyBytes != null && segData.isNotEmpty()) {
                    try {
                        val seq = mediaSequence + index
                        val iv = explicitIvBytes ?: sequenceToIv(seq)
                        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
                        val keySpec = SecretKeySpec(keyBytes, "AES")
                        val ivSpec = IvParameterSpec(iv)
                        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                        cipher.doFinal(segData)
                    } catch (e: Exception) {
                        StreamHubLogger.w("DownloadWorker", "Decryption warning on segment $index: ${e.message}")
                        segData
                    }
                } else {
                    segData
                }

                if (isDirectFileMode && directRaf != null) {
                    directRaf.write(outputBytes)
                } else if (mediaStoreStream != null) {
                    mediaStoreStream.write(outputBytes)
                    mediaStoreStream.flush()
                }

                downloadedBytes += outputBytes.size

                val now = System.currentTimeMillis()
                if (now - lastProgressTime >= 1000L || index == segmentUrls.lastIndex) {
                    lastProgressTime = now
                    val progressPercent = (((index + 1).toDouble() / totalSegments) * 100).toInt().coerceIn(0, 100)
                    val elapsedSeconds = (now - startTime) / 1000.0
                    val speed = if (elapsedSeconds > 0) (downloadedBytes / elapsedSeconds).toLong() else 0L
                    val estimatedTotal = if (index > 0) (downloadedBytes * totalSegments) / (index + 1) else downloadedBytes
                    val remainingSegments = totalSegments - (index + 1)
                    val etaSeconds = if (index > 0 && speed > 0) ((downloadedBytes / (index + 1)) * remainingSegments) / speed else 0L

                    activeRecord = activeRecord.copy(
                        status = DownloadStatus.DOWNLOADING,
                        downloadedBytes = downloadedBytes,
                        totalBytes = estimatedTotal,
                        progress = progressPercent,
                        speedBytesPerSec = speed,
                        etaSeconds = etaSeconds
                    )
                    downloadDao.updateDownload(activeRecord)

                    setProgress(
                        workDataOf(
                            "progress" to progressPercent,
                            "speed" to speed,
                            "eta" to etaSeconds
                        )
                    )

                    val hlsNotif = DownloadNotificationManager.buildDownloadingNotification(
                        context, title, progressPercent, speed, etaSeconds, downloadId
                    )
                    safeSetForeground(
                        DownloadNotificationManager.createForegroundInfo(
                            context,
                            hlsNotif,
                            notifId
                        ),
                        notifId,
                        hlsNotif
                    )
                }
            }

            try { directRaf?.close() } catch (e: Exception) {}
            try { mediaStoreStream?.close() } catch (e: Exception) {}
            try { mediaStorePfd?.close() } catch (e: Exception) {}

            if (isDirectFileMode) {
                if (directPartFile.exists()) {
                    if (directFinalFile.exists()) directFinalFile.delete()
                    val renamed = directPartFile.renameTo(directFinalFile)
                    if (!renamed) {
                        directPartFile.copyTo(directFinalFile, overwrite = true)
                        directPartFile.delete()
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaStoreUri != null) {
                val completeValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(mediaStoreUri, completeValues, null, null)
            }

            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destinationDisplayPath),
                    arrayOf("video/mp4"),
                    null
                )
            } catch (e: Exception) {}

            val finalRecord = activeRecord.copy(
                status = DownloadStatus.COMPLETED,
                progress = 100,
                downloadedBytes = downloadedBytes,
                totalBytes = downloadedBytes,
                speedBytesPerSec = 0L,
                localFilePath = destinationDisplayPath,
                completedAt = System.currentTimeMillis()
            )
            downloadDao.updateDownload(finalRecord)
            postCompletedNotification(title, downloadId)
            StreamHubLogger.i("DownloadWorker", "HLS download successfully completed: $destinationDisplayPath (${downloadedBytes} bytes)")
            return Result.success()

        } catch (e: Exception) {
            try { directRaf?.close() } catch (ignore: Exception) {}
            try { mediaStoreStream?.close() } catch (ignore: Exception) {}
            try { mediaStorePfd?.close() } catch (ignore: Exception) {}
            StreamHubLogger.e("DownloadWorker", "HLS download error for $downloadId: ${e.message}")
            return handleRetryableError(activeRecord, "HLS download error: ${e.message}")
        }
    }

    private fun sequenceToIv(seq: Long): ByteArray {
        val iv = ByteArray(16)
        var s = seq
        for (i in 15 downTo 8) {
            iv[i] = (s and 0xFF).toByte()
            s = s shr 8
        }
        return iv
    }

    private fun hexStringToByteArray(hexStr: String): ByteArray {
        val clean = hexStr.removePrefix("0x").removePrefix("0X")
        val len = clean.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
