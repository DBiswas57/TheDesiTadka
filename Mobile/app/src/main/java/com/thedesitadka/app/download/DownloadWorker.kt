package com.thedesitadka.app.download

import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
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
        const val BUFFER_SIZE = 65536 // 64 KB buffer for high-speed streaming I/O
        const val MAX_RETRIES = 3
    }

    private val downloadDao = AppDatabase.getInstance(context).downloadDao()

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return Result.failure()
        val mediaUrl = inputData.getString(KEY_MEDIA_URL) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Media Download"
        val providerId = inputData.getString(KEY_PROVIDER_ID) ?: ""

        StreamHubLogger.log(
            StreamHubLogger.Category.DOWNLOAD,
            "INFO",
            "DOWNLOAD_STARTED: id=$downloadId, provider=$providerId, title='$title'"
        )

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
        safeSetForeground(
            DownloadNotificationManager.createForegroundInfo(
                context,
                DownloadNotificationManager.buildDownloadingNotification(context, title, 0, 0L, 0L, downloadId)
            )
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
                urlLower.contains("ixiporn") || providerId == "ixiporn" -> "https://ixiporn.live/"
                urlLower.contains("wowuncut") || providerId == "wowuncut" -> "https://wowuncut.com/"
                urlLower.contains("antarvasna") || providerId == "antarvasnabf" -> "https://antarvasnabf.com/"
                urlLower.contains("ixifile") || urlLower.contains("cdn2.ixifile.xyz") -> {
                    if (providerId == "wowuncut") "https://wowuncut.com/" else "https://ixiporn.live/"
                }
                urlLower.contains("tube279.com") || urlLower.contains("siesta583") -> "https://tube279.com/"
                urlLower.contains("tnmr.org") || urlLower.contains("lulucdn") || urlLower.contains("lulustream") || urlLower.contains("luluvdo") -> "https://luluvdo.com/"
                urlLower.contains("tpead.net") || urlLower.contains("streamtape.com") || urlLower.contains("tapecontent.net") -> "https://streamtape.com/"
                urlLower.contains("mydown.biz") || urlLower.contains("masahub") || providerId == "masahub2" -> "https://masahub2.com/"
                urlLower.contains("pvtcdn.com") || urlLower.contains("masa49") || providerId == "masa49" -> "https://www.masa49.nl/"
                urlLower.contains("kamababa") || providerId == "kamababa1" -> "https://www.kamababa1.com/"
                urlLower.contains("fry99") || providerId == "fry99" -> "https://fry99.cc/"
                urlLower.contains("hitmaal") || providerId == "hitmaal" -> "https://hitmaal.io/"
                urlLower.contains("fsiblog") || providerId == "fsiblogxx" -> "https://fsiblogxx.com/"
                urlLower.contains("webxseries") || providerId == "webxseries" -> "https://webxseries.hot/"
                urlLower.contains("desisex") || providerId == "desisex" -> "https://desisex.site/"
                urlLower.contains("pornx11") || providerId == "pornx11" -> "https://pornx11.com/"
                urlLower.contains("aagmaal") || providerId == "aagmaal" || providerId == "aagmaal_com" -> "https://aagmaal.com/"
                urlLower.contains("xhpingcdn") || urlLower.contains("xhcdn") || urlLower.contains("xhamster") || providerId == "xhamster" -> "https://xhamster.desi/"
                else -> "https://${android.net.Uri.parse(mediaUrl).host ?: "example.com"}/"
            }
            requestBuilder.header("Referer", referer)

            // Inject Cookies if available
            try {
                val cookies = CookieManager.getInstance().getCookie(mediaUrl)
                if (!cookies.isNullOrBlank()) {
                    requestBuilder.header("Cookie", cookies)
                }
            } catch (e: Exception) {
                // Non-fatal
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
                            safeSetForeground(
                                DownloadNotificationManager.createForegroundInfo(
                                    context,
                                    DownloadNotificationManager.buildDownloadingNotification(
                                        context, title, progress, currentSpeed, etaSeconds, downloadId
                                    )
                                )
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
            postFailedNotification(record.title, errorMessage)
            return Result.failure()
        }
    }

    private suspend fun safeSetForeground(info: ForegroundInfo) {
        try {
            setForeground(info)
        } catch (e: Exception) {
            StreamHubLogger.w("DownloadWorker", "Could not set foreground service: ${e.message}")
        }
    }

    private fun postCompletedNotification(title: String, downloadId: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val notification = DownloadNotificationManager.buildCompletedNotification(context, title, downloadId)
            notificationManager?.notify(downloadId.hashCode(), notification)
        } catch (e: Exception) {
            StreamHubLogger.w("DownloadWorker", "Could not post completion notification: ${e.message}")
        }
    }

    private fun postFailedNotification(title: String, error: String?) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val notification = DownloadNotificationManager.buildFailedNotification(context, title, error)
            notificationManager?.notify(title.hashCode(), notification)
        } catch (e: Exception) {
            StreamHubLogger.w("DownloadWorker", "Could not post failure notification: ${e.message}")
        }
    }
}
