package com.thedesitadka.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.thedesitadka.app.MainActivity
import com.thedesitadka.core.security.StreamHubLogger

object DownloadNotificationManager {

    const val CHANNEL_ID = "thedesitadka_downloads"
    const val NOTIFICATION_ID = 9001

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val existing = notificationManager?.getNotificationChannel(CHANNEL_ID)
                if (existing == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "TheDesiTadka Downloads",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Active media downloads progress and management"
                        setShowBadge(false)
                    }
                    notificationManager?.createNotificationChannel(channel)
                }
            } catch (e: Exception) {
                StreamHubLogger.w("DownloadNotificationManager", "Could not create notification channel: ${e.message}")
            }
        }
    }

    /**
     * Builds a safe ForegroundInfo compatible with Android 14+ (API 34+).
     * Specifies FOREGROUND_SERVICE_TYPE_DATA_SYNC on API 29+.
     */
    fun createForegroundInfo(
        context: Context,
        notification: Notification
    ): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    fun buildDownloadingNotification(
        context: Context,
        title: String,
        progress: Int,
        speedBytesPerSec: Long = 0L,
        etaSeconds: Long = 0L,
        downloadId: String = ""
    ): Notification {
        ensureNotificationChannel(context)

        val speedFormatted = if (speedBytesPerSec > 0) {
            val mb = speedBytesPerSec / (1024f * 1024f)
            String.format("%.1f MB/s", mb)
        } else ""

        val etaFormatted = if (etaSeconds > 0) {
            val min = etaSeconds / 60
            val sec = etaSeconds % 60
            String.format("ETA %02d:%02d", min, sec)
        } else ""

        val subtitle = listOf(speedFormatted, etaFormatted).filter { it.isNotBlank() }.joinToString(" • ")

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(if (subtitle.isNotBlank()) "$progress% ($subtitle)" else "$progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (progress in 0..100) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    fun buildCompletedNotification(
        context: Context,
        title: String,
        downloadId: String
    ): Notification {
        ensureNotificationChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Completed")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()
    }

    fun buildFailedNotification(
        context: Context,
        title: String,
        errorMessage: String?
    ): Notification {
        ensureNotificationChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText("$title: ${errorMessage ?: "Network error"}")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()
    }
}
