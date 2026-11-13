package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.model.AppLanguage
import com.example.model.AppStrings
import com.example.model.DownloadTask

object DownloadNotificationHelper {

    const val CHANNEL_ID = "streamvault_downloads_channel"
    const val NOTIFICATION_ID_FOREGROUND = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "StreamVault Downloads"
            val descriptionText = "Shows progress and completion status for media downloads"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildForegroundNotification(
        context: Context,
        task: DownloadTask,
        language: AppLanguage
    ): android.app.Notification {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val downloadedMb = task.bytesDownloaded / (1024f * 1024f)
        val totalMb = task.totalBytes / (1024f * 1024f)
        val etaPart = if (task.eta.isNotBlank()) " • ${task.eta}" else ""
        val contentText = if (totalMb > 0) {
            "%.1f / %.1f MB (%d%%) • %s%s".format(downloadedMb, totalMb, task.progress, task.speed, etaPart)
        } else {
            "%.1f MB • %s%s".format(downloadedMb, task.speed, etaPart)
        }

        val titlePrefix = AppStrings.get(language, "notif_downloading")

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("$titlePrefix ${task.videoTitle.take(30)}")
            .setContentText(contentText)
            .setProgress(100, task.progress, task.totalBytes <= 0)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    fun notifyDownloadCompleted(context: Context, task: DownloadTask, language: AppLanguage) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val isAudio = task.ext in listOf("mp3", "m4a", "aac", "wav") || task.resolution.contains("Audio")
        val destFolder = if (isAudio) "Music/StreamVault" else "Movies/StreamVault"

        val title = AppStrings.get(language, "notif_completed_title")
        val body = "${AppStrings.get(language, "notif_completed_body")} $destFolder (${task.videoTitle.take(35)})"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(task.id.hashCode(), notification)
        } catch (ignored: SecurityException) {}
    }

    fun notifyDownloadPaused(context: Context, task: DownloadTask, language: AppLanguage) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val title = AppStrings.get(language, "notif_paused_title")
        val body = AppStrings.get(language, "notif_paused_body")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(task.id.hashCode(), notification)
        } catch (ignored: SecurityException) {}
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        try {
            NotificationManagerCompat.from(context).cancel(notificationId)
        } catch (ignored: SecurityException) {}
    }
}
