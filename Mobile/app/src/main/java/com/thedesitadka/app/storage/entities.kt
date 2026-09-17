package com.thedesitadka.app.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val title: String,
    val thumbnailUrl: String,
    val detailUrl: String,
    val lastPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val title: String,
    val thumbnailUrl: String,
    val detailUrl: String,
    val addedAt: Long = System.currentTimeMillis()
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    RETRYING,
    COMPLETED,
    FAILED,
    CANCELLED,
    DELETED
}

@Entity(tableName = "downloads")
data class DownloadRecordEntity(
    @PrimaryKey val id: String, // downloadId
    val contentId: String = id,
    val providerId: String,
    val title: String,
    val thumbnailUrl: String,
    val mediaUrl: String,
    val localFilePath: String = "",
    val mimeType: String = "video/mp4",
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val progress: Int = 0,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val error: String? = null,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
