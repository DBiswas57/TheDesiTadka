package com.thedesitadka.app.download

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.thedesitadka.app.storage.AppDatabase
import com.thedesitadka.app.storage.DownloadRecordEntity
import com.thedesitadka.app.storage.DownloadStatus
import com.thedesitadka.core.model.DownloadType
import com.thedesitadka.core.model.MediaSource
import com.thedesitadka.core.model.MediaSourceType
import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.model.StreamHubError
import com.thedesitadka.core.model.VideoItem
import com.thedesitadka.core.security.StreamHubLogger
import com.thedesitadka.provider.ProviderEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class DownloadRepository(
    private val context: Context,
    private val providerEngine: ProviderEngine
) {

    private val downloadDao = AppDatabase.getInstance(context).downloadDao()
    private val workManager = WorkManager.getInstance(context)

    fun getAllDownloads(): Flow<List<DownloadRecordEntity>> = downloadDao.getAllDownloads()

    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadRecordEntity>> =
        downloadDao.getDownloadsByStatus(status)

    /**
     * Enqueues an authorized download safely with duplicate protection and error isolation.
     */
    suspend fun enqueueAuthorizedDownload(
        videoItem: VideoItem,
        mediaSource: MediaSource,
        wifiOnly: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val adapter = providerEngine.getAdapter(videoItem.providerId)
                ?: return@withContext Result.failure(StreamHubError.ProviderUnavailable(videoItem.providerId, "Provider not found"))

            // Check if provider permits media download OR media source exposes explicit download capability
            val isDownloadAuthorized = adapter.hasCapability(ProviderCapability.DOWNLOAD) ||
                    mediaSource.canDownload ||
                    mediaSource.downloadType != DownloadType.UNSUPPORTED

            if (!isDownloadAuthorized) {
                StreamHubLogger.w("DownloadRepository", "Rejected download: Provider '${videoItem.providerId}' does not permit downloads")
                return@withContext Result.failure(
                    StreamHubError.SecurityError(
                        "UNAUTHORIZED_DOWNLOAD",
                        "Media from '${videoItem.providerId}' is not authorized for offline download"
                    )
                )
            }

            val rawDownloadUrl = mediaSource.downloadUrl ?: mediaSource.url
            if (rawDownloadUrl.isBlank()) {
                return@withContext Result.failure(
                    StreamHubError.DownloadError(message = "No valid download URL available for this content")
                )
            }

            // HLS (.m3u8) and DASH (.mpd) cannot be saved as single progressive MP4 files
            if (rawDownloadUrl.contains(".m3u8", ignoreCase = true) ||
                rawDownloadUrl.contains(".mpd", ignoreCase = true) ||
                mediaSource.type == MediaSourceType.HLS ||
                mediaSource.type == MediaSourceType.DASH) {
                return@withContext Result.failure(
                    StreamHubError.DownloadError(message = "Streaming protocol (${mediaSource.type}) does not support direct file download")
                )
            }

            val downloadId = videoItem.id

            // Duplicate Download Protection
            val existing = downloadDao.getDownload(downloadId)
            if (existing != null && (existing.status == DownloadStatus.DOWNLOADING ||
                        existing.status == DownloadStatus.QUEUED ||
                        existing.status == DownloadStatus.RETRYING)) {
                StreamHubLogger.i("DownloadRepository", "Download '$downloadId' is already active.")
                return@withContext Result.success(downloadId)
            }

            val record = DownloadRecordEntity(
                id = downloadId,
                contentId = videoItem.id,
                providerId = videoItem.providerId,
                title = videoItem.title,
                thumbnailUrl = videoItem.thumbnailUrl,
                mediaUrl = rawDownloadUrl,
                mimeType = mediaSource.mimeType,
                status = DownloadStatus.QUEUED
            )
            downloadDao.insertDownload(record)

            startWorker(downloadId, rawDownloadUrl, videoItem.title, videoItem.providerId, wifiOnly)
            StreamHubLogger.log(
                StreamHubLogger.Category.DOWNLOAD,
                "INFO",
                "DOWNLOAD_ENQUEUED: id=$downloadId, title='${videoItem.title}'"
            )
            Result.success(downloadId)
        } catch (e: Exception) {
            StreamHubLogger.e("DownloadRepository", "Failed to enqueue download for ${videoItem.id}: ${e.message}")
            Result.failure(e)
        }
    }

    private fun startWorker(
        downloadId: String,
        mediaUrl: String,
        title: String,
        providerId: String,
        wifiOnly: Boolean = false,
        isPaused: Boolean = false
    ) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(constraints)
                .setInputData(
                    workDataOf(
                        DownloadWorker.KEY_DOWNLOAD_ID to downloadId,
                        DownloadWorker.KEY_MEDIA_URL to mediaUrl,
                        DownloadWorker.KEY_TITLE to title,
                        DownloadWorker.KEY_PROVIDER_ID to providerId,
                        DownloadWorker.KEY_IS_PAUSED to isPaused
                    )
                )
                .addTag("download_${downloadId}")
                .build()

            workManager.enqueueUniqueWork(
                "download_${downloadId}",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        } catch (e: Exception) {
            StreamHubLogger.e("DownloadRepository", "Could not start DownloadWorker for $downloadId: ${e.message}")
        }
    }

    suspend fun pauseDownload(downloadId: String) = withContext(Dispatchers.IO) {
        try {
            workManager.cancelUniqueWork("download_${downloadId}")
            val existing = downloadDao.getDownload(downloadId)
            if (existing != null) {
                downloadDao.updateDownload(existing.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0L))
            }
        } catch (e: Exception) {
            StreamHubLogger.e("DownloadRepository", "Could not pause download $downloadId: ${e.message}")
        }
    }

    suspend fun resumeDownload(downloadId: String) = withContext(Dispatchers.IO) {
        try {
            val existing = downloadDao.getDownload(downloadId) ?: return@withContext
            downloadDao.updateDownload(existing.copy(status = DownloadStatus.QUEUED, error = null))
            startWorker(existing.id, existing.mediaUrl, existing.title, existing.providerId)
        } catch (e: Exception) {
            StreamHubLogger.e("DownloadRepository", "Could not resume download $downloadId: ${e.message}")
        }
    }

    suspend fun retryDownload(downloadId: String) = withContext(Dispatchers.IO) {
        try {
            val existing = downloadDao.getDownload(downloadId) ?: return@withContext
            downloadDao.updateDownload(
                existing.copy(
                    status = DownloadStatus.RETRYING,
                    error = null,
                    retryCount = existing.retryCount + 1
                )
            )
            startWorker(existing.id, existing.mediaUrl, existing.title, existing.providerId)
        } catch (e: Exception) {
            StreamHubLogger.e("DownloadRepository", "Could not retry download $downloadId: ${e.message}")
        }
    }

    suspend fun cancelDownload(downloadId: String) = withContext(Dispatchers.IO) {
        try {
            workManager.cancelUniqueWork("download_${downloadId}")
            val existing = downloadDao.getDownload(downloadId)
            if (existing != null) {
                downloadDao.updateDownload(existing.copy(status = DownloadStatus.CANCELLED, speedBytesPerSec = 0L))
                if (existing.localFilePath.isNotBlank()) {
                    File(existing.localFilePath).delete()
                    val partFile = File(existing.localFilePath.replace(".mp4", ".part"))
                    if (partFile.exists()) partFile.delete()
                }
            }
        } catch (e: Exception) {
            StreamHubLogger.e("DownloadRepository", "Could not cancel download $downloadId: ${e.message}")
        }
    }

    suspend fun deleteDownload(downloadId: String) = deletePermanently(downloadId)

    /**
     * Removes the item from the application's download list/database.
     * Preserves the downloaded physical file on the user's storage.
     */
    suspend fun removeFromList(downloadId: String) = withContext(Dispatchers.IO) {
        try {
            workManager.cancelUniqueWork("download_${downloadId}")
            downloadDao.deleteDownload(downloadId)
            StreamHubLogger.i("DownloadRepository", "Removed download '$downloadId' from list (preserved physical file)")
        } catch (e: Exception) {
            StreamHubLogger.e("DownloadRepository", "Could not remove download $downloadId from list: ${e.message}")
        }
    }

    /**
     * Permanently deletes both the database record and the physical downloaded file from disk.
     */
    suspend fun deletePermanently(downloadId: String) = withContext(Dispatchers.IO) {
        try {
            workManager.cancelUniqueWork("download_${downloadId}")
            val existing = downloadDao.getDownload(downloadId)
            if (existing != null && existing.localFilePath.isNotBlank()) {
                val file = File(existing.localFilePath)
                if (file.exists()) file.delete()
                val partFile = File(existing.localFilePath.replace(".mp4", ".part"))
                if (partFile.exists()) partFile.delete()
            }
            downloadDao.deleteDownload(downloadId)
            StreamHubLogger.i("DownloadRepository", "Permanently deleted download '$downloadId' and associated disk files")
        } catch (e: Exception) {
            StreamHubLogger.e("DownloadRepository", "Could not permanently delete download $downloadId: ${e.message}")
        }
    }

    /**
     * Clears download list records. If [deleteFiles] is true, deletes physical media files as well.
     */
    suspend fun clearList(deleteFiles: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            if (deleteFiles) {
                val all = downloadDao.getAllDownloadsList()
                for (rec in all) {
                    try {
                        workManager.cancelUniqueWork("download_${rec.id}")
                        if (rec.localFilePath.isNotBlank()) {
                            val f = File(rec.localFilePath)
                            if (f.exists()) f.delete()
                            val part = File(rec.localFilePath.replace(".mp4", ".part"))
                            if (part.exists()) part.delete()
                        }
                    } catch (e: Exception) {
                        // continue deleting remaining
                    }
                }
            }
            downloadDao.clearAll()
            StreamHubLogger.i("DownloadRepository", "Cleared download list (deleteFiles=$deleteFiles)")
        } catch (e: Exception) {
            StreamHubLogger.e("DownloadRepository", "Could not clear download list: ${e.message}")
        }
    }

    /**
     * Reconciles the Room database with the actual device filesystem.
     * Detects manually deleted or missing files and updates record state safely.
     */
    suspend fun reconcileWithFilesystem() = withContext(Dispatchers.IO) {
        try {
            val all = downloadDao.getAllDownloadsList()
            for (rec in all) {
                if (rec.status == DownloadStatus.COMPLETED) {
                    if (rec.localFilePath.isNotBlank()) {
                        val path = rec.localFilePath.trim()
                        val fileExists = if (path.startsWith("content://")) {
                            try {
                                context.contentResolver.openFileDescriptor(Uri.parse(path), "r")?.use { pfd ->
                                    pfd.statSize > 0L
                                } ?: false
                            } catch (e: Exception) {
                                false
                            }
                        } else {
                            val file = File(path)
                            file.exists() && file.length() > 0L
                        }

                        if (!fileExists) {
                            downloadDao.updateDownload(
                                rec.copy(
                                    status = DownloadStatus.FAILED,
                                    error = "File missing from disk or deleted externally"
                                )
                            )
                        }
                    }
                }
            }
            recoverInterruptedDownloads()
            StreamHubLogger.i("DownloadRepository", "Filesystem reconciliation completed successfully")
        } catch (e: Exception) {
            StreamHubLogger.w("DownloadRepository", "Filesystem reconciliation warning: ${e.message}")
        }
    }

    suspend fun retryAllFailed() = withContext(Dispatchers.IO) {
        try {
            val failed = downloadDao.getFailedDownloads()
            for (item in failed) {
                retryDownload(item.id)
            }
        } catch (e: Exception) {
            StreamHubLogger.e("DownloadRepository", "Could not retry failed downloads: ${e.message}")
        }
    }

    suspend fun clearCompleted() = withContext(Dispatchers.IO) {
        try {
            downloadDao.deleteCompleted()
        } catch (e: Exception) {
            StreamHubLogger.e("DownloadRepository", "Could not clear completed downloads: ${e.message}")
        }
    }

    suspend fun deleteCompleted() = clearCompleted()

    suspend fun recoverInterruptedDownloads() = withContext(Dispatchers.IO) {
        try {
            val interrupted = downloadDao.getInterruptedDownloads()
            for (rec in interrupted) {
                downloadDao.updateDownload(
                    rec.copy(
                        status = DownloadStatus.PAUSED,
                        speedBytesPerSec = 0L,
                        error = "Interrupted by app termination"
                    )
                )
            }
            if (interrupted.isNotEmpty()) {
                StreamHubLogger.i("DownloadRepository", "Recovered ${interrupted.size} interrupted downloads to PAUSED state")
            }
        } catch (e: Exception) {
            StreamHubLogger.w("DownloadRepository", "Failed to recover interrupted downloads: ${e.message}")
        }
    }
}
