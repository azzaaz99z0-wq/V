package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.example.model.AppLanguage
import com.example.model.DownloadTask

class MediaDownloadForegroundService : Service() {

    private val tag = "DownloadFgService"
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "StreamVault::DownloadWakeLock"
            ).apply {
                acquire(15 * 60 * 1000L) // 15 minutes safe timeout
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not acquire wake lock", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Downloading media..."
        val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
        val totalBytes = intent?.getLongExtra(EXTRA_TOTAL_BYTES, 0L) ?: 0L
        val bytesDownloaded = intent?.getLongExtra(EXTRA_BYTES_DOWNLOADED, 0L) ?: 0L
        val speed = intent?.getStringExtra(EXTRA_SPEED) ?: ""
        val eta = intent?.getStringExtra(EXTRA_ETA) ?: ""
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID) ?: "current"

        val dummyTask = DownloadTask(
            id = taskId,
            videoTitle = title,
            resolution = "",
            formatId = "",
            ext = "mp4",
            webpageUrl = "",
            thumbnail = "",
            progress = progress,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            speed = speed,
            eta = eta
        )

        val notification = DownloadNotificationHelper.buildForegroundNotification(
            this,
            dummyTask,
            AppLanguage.ENGLISH
        )
        startForeground(DownloadNotificationHelper.NOTIFICATION_ID_FOREGROUND, notification)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (ignored: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_TOTAL_BYTES = "extra_total_bytes"
        const val EXTRA_BYTES_DOWNLOADED = "extra_bytes_downloaded"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_ETA = "extra_eta"

        fun startOrUpdate(
            context: Context,
            task: DownloadTask,
            language: AppLanguage
        ) {
            val intent = Intent(context, MediaDownloadForegroundService::class.java).apply {
                putExtra(EXTRA_TASK_ID, task.id)
                putExtra(EXTRA_TITLE, task.videoTitle)
                putExtra(EXTRA_PROGRESS, task.progress)
                putExtra(EXTRA_TOTAL_BYTES, task.totalBytes)
                putExtra(EXTRA_BYTES_DOWNLOADED, task.bytesDownloaded)
                putExtra(EXTRA_SPEED, task.speed)
                putExtra(EXTRA_ETA, task.eta)
            }
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w("DownloadFgService", "Could not start foreground service", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, MediaDownloadForegroundService::class.java)
                context.stopService(intent)
            } catch (ignored: Exception) {}
        }
    }
}
