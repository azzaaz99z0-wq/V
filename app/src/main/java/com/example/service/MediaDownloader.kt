package com.example.service

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.model.AppLanguage
import com.example.model.AppStrings
import com.example.model.DownloadStatus
import com.example.model.DownloadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

class MediaDownloader(private val context: Context) {

    private val tag = "MediaDownloader"
    val pythonManager = PythonExtractorManager(context)

    fun getDownloadTempFile(task: DownloadTask): File {
        val cacheDir = File(context.cacheDir, "downloads").apply { mkdirs() }
        val ext = if (task.ext.isNotBlank()) task.ext.lowercase() else "mp4"
        val sanitizedTitle = task.videoTitle
            .replace("[^a-zA-Z0-9.-]".toRegex(), "_")
            .take(35)
            .ifBlank { "StreamVault" }
        return File(cacheDir, "${task.id}_${sanitizedTitle}.$ext")
    }

    /**
     * Clean up cache and part files. Called when user explicitly cancels/removes the task
     * or after successful MediaStore export.
     */
    fun cleanUpTaskFiles(task: DownloadTask) {
        val tempFile = getDownloadTempFile(task)
        val basePath = tempFile.absolutePath
        val candidateFiles = listOf(
            tempFile,
            File("$basePath.part"),
            File("$basePath.ytdl"),
            File("$basePath.video.mp4"),
            File("$basePath.video.mp4.part"),
            File("$basePath.audio.m4a"),
            File("$basePath.audio.m4a.part")
        )
        candidateFiles.forEach { file ->
            try {
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                Log.w(tag, "Could not delete file ${file.name}", e)
            }
        }
    }

    /**
     * Executes the download task with real-time speed (MB/s) and ETA tracking,
     * localized non-technical error reporting, custom save paths, and automatic Gallery/Music indexing.
     */
    suspend fun executeDownloadTask(
        task: DownloadTask,
        language: AppLanguage = AppLanguage.ENGLISH,
        speedMode: String = "SAFE_BALANCED",
        videoSavePath: String = "Movies/StreamVault",
        audioSavePath: String = "Music/StreamVault",
        onUpdate: (DownloadTask) -> Unit
    ): Result<DownloadTask> = withContext(Dispatchers.IO) {
        val sanitizedTitle = task.videoTitle
            .replace("[^a-zA-Z0-9.-]".toRegex(), "_")
            .take(40)
            .ifBlank { "StreamVault_Media" }

        val ext = if (task.ext.isNotBlank()) task.ext.lowercase() else "mp4"
        val cleanRes = task.resolution.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val fileName = "${sanitizedTitle}_${cleanRes}.$ext"

        var currentTask = task.copy(status = DownloadStatus.DOWNLOADING, errorMessage = null)
        onUpdate(currentTask)

        val tempFile = getDownloadTempFile(task)
        val targetTotalBytes = if (task.totalBytes > 0) task.totalBytes else 0L

        try {
            coroutineContext.ensureActive()
            var downloadSucceeded = false

            val pythonResult = pythonManager.downloadViaPython(
                url = task.webpageUrl,
                formatId = task.formatId,
                outputFilePath = tempFile.absolutePath,
                taskId = task.id,
                speedMode = speedMode,
                onProgress = { downloadedBytes, totalBytes, speedBytesPerSec, etaStr, _ ->
                    if (pythonManager.isTaskCancelled(task.id)) return@downloadViaPython

                    val finalTotal = when {
                        targetTotalBytes > 0 -> targetTotalBytes
                        totalBytes > 0 -> totalBytes
                        else -> downloadedBytes
                    }

                    val effectiveDownloaded = if (finalTotal > 0) {
                        maxOf(currentTask.bytesDownloaded, downloadedBytes).coerceAtMost(finalTotal)
                    } else {
                        maxOf(currentTask.bytesDownloaded, downloadedBytes)
                    }

                    val pct = if (finalTotal > 0) {
                        ((effectiveDownloaded * 100) / finalTotal).toInt().coerceIn(1, 99)
                    } else {
                        ((effectiveDownloaded / (1024 * 1024)).toInt() % 100).coerceIn(1, 99)
                    }

                    val speedStr = if (speedBytesPerSec > 0) {
                        val mb = speedBytesPerSec / (1024.0 * 1024.0)
                        if (mb >= 1.0) "%.1f MB/s".format(mb) else "%.0f KB/s".format(speedBytesPerSec / 1024.0)
                    } else ""

                    currentTask = currentTask.copy(
                        progress = pct,
                        bytesDownloaded = effectiveDownloaded,
                        totalBytes = finalTotal,
                        speed = speedStr,
                        eta = etaStr,
                        status = DownloadStatus.DOWNLOADING
                    )
                    onUpdate(currentTask)
                }
            )

            if (pythonManager.isTaskCancelled(task.id)) {
                currentTask = currentTask.copy(status = DownloadStatus.PAUSED, speed = "", eta = "")
                onUpdate(currentTask)
                return@withContext Result.success(currentTask)
            }

            coroutineContext.ensureActive()

            var resultingDownloadedFile = tempFile

            if (pythonResult.isSuccess) {
                val res = pythonResult.getOrThrow()
                when (res.status) {
                    "aborted" -> {
                        currentTask = currentTask.copy(status = DownloadStatus.PAUSED, speed = "", eta = "")
                        onUpdate(currentTask)
                        return@withContext Result.success(currentTask)
                    }
                    "mux_needed" -> {
                        val videoPath = res.videoPath
                        val audioPath = res.audioPath
                        if (!videoPath.isNullOrBlank() && !audioPath.isNullOrBlank()) {
                            val vFile = File(videoPath)
                            val aFile = File(audioPath)
                            Log.d(tag, "Muxing video ($videoPath) and audio ($audioPath) to ${tempFile.absolutePath}")
                            val muxSuccess = AndroidMediaMuxer.muxVideoAndAudio(vFile, aFile, tempFile)
                            if (muxSuccess && tempFile.exists() && tempFile.length() > 0) {
                                resultingDownloadedFile = tempFile
                                downloadSucceeded = true
                                vFile.delete()
                                aFile.delete()
                            } else if (vFile.exists() && vFile.length() > 0) {
                                resultingDownloadedFile = vFile
                                downloadSucceeded = true
                                aFile.delete()
                            }
                        }
                    }
                    "success" -> {
                        val finalPath = res.filePath
                        if (File(finalPath).exists() && File(finalPath).length() > 0) {
                            resultingDownloadedFile = File(finalPath)
                            downloadSucceeded = true
                        }
                    }
                    "error" -> {
                        val friendlyMsg = AppStrings.getErrorByCode(language, res.errorCode)
                        currentTask = currentTask.copy(
                            status = if (res.isNetworkError) DownloadStatus.PAUSED else DownloadStatus.FAILED,
                            errorMessage = friendlyMsg,
                            speed = "",
                            eta = ""
                        )
                        onUpdate(currentTask)
                        return@withContext Result.failure(Exception(friendlyMsg))
                    }
                }
            }

            if (!downloadSucceeded && tempFile.exists() && tempFile.length() > 0) {
                downloadSucceeded = true
                resultingDownloadedFile = tempFile
            }

            if (!downloadSucceeded || !resultingDownloadedFile.exists() || resultingDownloadedFile.length() == 0L) {
                val errCode = pythonResult.getOrNull()?.errorCode ?: "general"
                val friendlyMsg = AppStrings.getErrorByCode(language, errCode)
                currentTask = currentTask.copy(
                    status = DownloadStatus.PAUSED,
                    errorMessage = friendlyMsg,
                    speed = "",
                    eta = ""
                )
                onUpdate(currentTask)
                throw IllegalStateException(friendlyMsg)
            }

            coroutineContext.ensureActive()

            val extLower = ext.lowercase()
            val isAudio = extLower in listOf("mp3", "m4a", "aac", "wav", "ogg", "flac") ||
                    task.resolution.contains("Audio", ignoreCase = true)
            val isVideo = !isAudio

            val targetSubfolder = if (isVideo) videoSavePath else audioSavePath
            val savedUri = saveAndIndexToGallery(resultingDownloadedFile, fileName, isVideo, ext, targetSubfolder)

            currentTask = currentTask.copy(
                progress = 100,
                bytesDownloaded = resultingDownloadedFile.length(),
                totalBytes = resultingDownloadedFile.length(),
                speed = "",
                eta = "",
                status = DownloadStatus.COMPLETED,
                localUri = savedUri
            )
            onUpdate(currentTask)

            resultingDownloadedFile.delete()
            cleanUpTaskFiles(task)

            Result.success(currentTask)

        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException || pythonManager.isTaskCancelled(task.id)) {
                currentTask = currentTask.copy(status = DownloadStatus.PAUSED, speed = "", eta = "")
                onUpdate(currentTask)
                return@withContext Result.success(currentTask)
            }
            Log.e(tag, "Download task ${task.id} interrupted", e)
            val rawMsg = e.message ?: ""
            val isNet = rawMsg.contains("Errno 7") || rawMsg.contains("Network") || rawMsg.contains("timed out")
            val friendlyMsg = if (isNet) {
                AppStrings.getErrorByCode(language, "network")
            } else {
                rawMsg.ifBlank { AppStrings.getErrorByCode(language, "general") }
            }

            currentTask = currentTask.copy(
                status = if (isNet) DownloadStatus.PAUSED else DownloadStatus.FAILED,
                errorMessage = friendlyMsg,
                speed = "",
                eta = ""
            )
            onUpdate(currentTask)
            Result.failure(e)
        }
    }

    /**
     * Normalizes user-edited save paths safely for Android Scoped Storage (API 29+).
     */
    private fun normalizeRelativePath(customPath: String, isVideo: Boolean): String {
        val cleaned = customPath.trim().trim('/').replace("\\", "/")
        if (cleaned.isBlank()) {
            return if (isVideo) "${Environment.DIRECTORY_MOVIES}/StreamVault" else "${Environment.DIRECTORY_MUSIC}/StreamVault"
        }
        val low = cleaned.lowercase()
        return if (isVideo) {
            if (low.startsWith("movies") || low.startsWith("dcim") || low.startsWith("download")) cleaned
            else "${Environment.DIRECTORY_MOVIES}/$cleaned"
        } else {
            if (low.startsWith("music") || low.startsWith("podcasts") || low.startsWith("ringtones") || low.startsWith("download")) cleaned
            else "${Environment.DIRECTORY_MUSIC}/$cleaned"
        }
    }

    private suspend fun saveAndIndexToGallery(
        sourceFile: File,
        displayName: String,
        isVideo: Boolean,
        extension: String,
        customSavePath: String
    ): String = withContext(Dispatchers.IO) {
        val mimeType = if (isVideo) {
            when (extension) {
                "mp4" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "3gp" -> "video/3gpp"
                else -> "video/mp4"
            }
        } else {
            when (extension) {
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "aac" -> "audio/aac"
                "wav" -> "audio/x-wav"
                "ogg" -> "audio/ogg"
                else -> "audio/mpeg"
            }
        }

        var savedPath = ""
        val relativeDir = normalizeRelativePath(customSavePath, isVideo)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val useDownloadsCollection = relativeDir.lowercase().startsWith("download")
            val collectionUri = when {
                useDownloadsCollection -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
                isVideo -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val itemUri = context.contentResolver.insert(collectionUri, contentValues)
                ?: throw IllegalStateException("Could not create media entry")

            context.contentResolver.openOutputStream(itemUri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            }

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(itemUri, contentValues, null, null)

            savedPath = itemUri.toString()
            Log.d(tag, "Saved to MediaStore ($relativeDir): $savedPath")

            try {
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                    data = itemUri
                }
                context.sendBroadcast(mediaScanIntent)
            } catch (e: Exception) {
                Log.w(tag, "Failed to broadcast media scanner intent", e)
            }

        } else {
            val rootPublic = Environment.getExternalStorageDirectory()
            val subDir = File(rootPublic, relativeDir).apply { mkdirs() }
            val destFile = File(subDir, displayName)

            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            savedPath = destFile.absolutePath

            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf(mimeType)
            ) { path, uri ->
                Log.d(tag, "MediaScanner indexed file: $path -> $uri")
            }
        }

        savedPath
    }
}
