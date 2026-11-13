package com.example.service

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.model.AudioTrack
import com.example.model.MediaFormat
import com.example.model.MediaInfo
import com.example.model.PlaylistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class ExtractionException(
    val errorCode: String,
    message: String
) : Exception(message)

data class PythonDownloadResult(
    val status: String,
    val filePath: String,
    val videoPath: String? = null,
    val audioPath: String? = null,
    val isNetworkError: Boolean = false,
    val errorCode: String = "general",
    val errorMessage: String? = null
)

class PythonExtractorManager(private val context: Context) {

    private val tag = "PythonExtractorManager"
    private val cancelledTasks = ConcurrentHashMap.newKeySet<String>()

    init {
        ensurePythonInitialized(context)
    }

    private fun ensurePythonInitialized(context: Context) {
        if (!Python.isStarted()) {
            Log.d(tag, "Starting Chaquopy Python runtime...")
            Python.start(AndroidPlatform(context.applicationContext))
            Log.d(tag, "Chaquopy Python runtime started.")
        }
    }

    private fun getExtractorModule(): com.chaquo.python.PyObject {
        ensurePythonInitialized(context)
        val py = Python.getInstance()
        val extractor = py.getModule("extractor")
        try {
            val importlib = py.getModule("importlib")
            importlib.callAttr("reload", extractor)
        } catch (e: Exception) {
            Log.w(tag, "Could not reload extractor module: ${e.message}")
        }
        return extractor
    }

    fun requestCancel(taskId: String) {
        cancelledTasks.add(taskId)
        try {
            val extractor = getExtractorModule()
            extractor.callAttr("cancel_task", taskId)
            Log.d(tag, "Requested cancellation for task: $taskId")
        } catch (e: Exception) {
            Log.w(tag, "Error setting cancel flag", e)
        }
    }

    fun isTaskCancelled(taskId: String): Boolean {
        return cancelledTasks.contains(taskId)
    }

    fun resetCancel(taskId: String) {
        cancelledTasks.remove(taskId)
        try {
            val extractor = getExtractorModule()
            extractor.callAttr("reset_task_cancellation", taskId)
            Log.d(tag, "Reset cancellation for task: $taskId")
        } catch (e: Exception) {
            Log.w(tag, "Error resetting cancel flag", e)
        }
    }

    /**
     * Extracts media formats, playlists, and multi-language audio tracks from URL.
     */
    suspend fun extractFormats(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        try {
            val extractorModule = getExtractorModule()
            Log.d(tag, "Inspecting media URL: $url")
            val pyResult = extractorModule.callAttr("extract_media_info", url.trim())
            val jsonString = pyResult.toString()

            val rootJson = JSONObject(jsonString)
            val status = rootJson.optString("status")

            if (status == "error") {
                val errorCode = rootJson.optString("error_code", "general")
                val errorMsg = rootJson.optString("message", "Could not extract media details.")
                return@withContext Result.failure(ExtractionException(errorCode, errorMsg))
            }

            val isPlaylist = rootJson.optBoolean("is_playlist", false)
            val title = rootJson.optString("title", "Untitled Video")
            val duration = rootJson.optString("duration", "Unknown")
            val durationSeconds = rootJson.optLong("duration_seconds", 0L)
            val uploader = rootJson.optString("uploader", "Unknown Source")
            val thumbnail = rootJson.optString("thumbnail", "")
            val webpageUrl = rootJson.optString("webpage_url", url)

            val playlistItems = mutableListOf<PlaylistItem>()
            val playlistArray = rootJson.optJSONArray("playlist_items")
            if (playlistArray != null) {
                for (i in 0 until playlistArray.length()) {
                    val pObj = playlistArray.getJSONObject(i)
                    playlistItems.add(
                        PlaylistItem(
                            id = pObj.optString("id", i.toString()),
                            title = pObj.optString("title", "Video #$i"),
                            url = pObj.optString("url", ""),
                            duration = pObj.optString("duration", "Unknown"),
                            thumbnail = pObj.optString("thumbnail", "")
                        )
                    )
                }
            }

            val formatsArray = rootJson.optJSONArray("formats")
            val formatsList = mutableListOf<MediaFormat>()
            if (formatsArray != null) {
                for (i in 0 until formatsArray.length()) {
                    val fmtObj = formatsArray.getJSONObject(i)
                    formatsList.add(
                        MediaFormat(
                            formatId = fmtObj.optString("format_id", ""),
                            resolution = fmtObj.optString("resolution", "Standard"),
                            ext = fmtObj.optString("ext", "mp4"),
                            filesizeBytes = fmtObj.optLong("filesize_bytes", 0L),
                            filesizeFormatted = fmtObj.optString("filesize_formatted", "Unknown size"),
                            streamType = fmtObj.optString("stream_type", "Video"),
                            hasAudio = fmtObj.optBoolean("has_audio", true),
                            hasVideo = fmtObj.optBoolean("has_video", true),
                            fps = fmtObj.optInt("fps", 0),
                            formatNote = fmtObj.optString("format_note", ""),
                            streamUrl = fmtObj.optString("stream_url", ""),
                            qualityScore = fmtObj.optInt("quality_score", 0),
                            height = fmtObj.optInt("height", 0)
                        )
                    )
                }
            }

            val audioTracks = mutableListOf<AudioTrack>()
            val audioArray = rootJson.optJSONArray("audio_tracks")
            if (audioArray != null) {
                for (i in 0 until audioArray.length()) {
                    val aObj = audioArray.getJSONObject(i)
                    audioTracks.add(
                        AudioTrack(
                            id = aObj.optString("id", ""),
                            language = aObj.optString("language", "default"),
                            displayName = aObj.optString("display_name", "Original"),
                            isOriginal = aObj.optBoolean("is_original", false),
                            bitrate = aObj.optInt("bitrate", 128)
                        )
                    )
                }
            }

            val mediaInfo = MediaInfo(
                title = title,
                duration = duration,
                durationSeconds = durationSeconds,
                uploader = uploader,
                thumbnail = thumbnail,
                webpageUrl = webpageUrl,
                formats = formatsList,
                audioTracks = audioTracks,
                isPlaylist = isPlaylist,
                playlistItems = playlistItems
            )

            Result.success(mediaInfo)
        } catch (e: Exception) {
            Log.e(tag, "Exception during media extraction", e)
            val errStr = e.message ?: ""
            val isNetwork = errStr.contains("Errno 7") || errStr.contains("Network") || errStr.contains("nodename")
            val code = if (isNetwork) "network" else "general"
            Result.failure(ExtractionException(code, errStr))
        }
    }

    /**
     * Downloads video stream with ETA, speed metrics, HTTP 416 auto-recovery, and speedMode.
     */
    suspend fun downloadViaPython(
        url: String,
        formatId: String,
        outputFilePath: String,
        taskId: String,
        speedMode: String = "SAFE_BALANCED",
        onProgress: (downloadedBytes: Long, totalBytes: Long, speed: Double, eta: String, streamStage: Int) -> Unit
    ): Result<PythonDownloadResult> = withContext(Dispatchers.IO) {
        try {
            resetCancel(taskId)
            val extractorModule = getExtractorModule()

            val listener = object {
                fun onProgress(downloaded: Long, total: Long, speed: Double, eta: String, streamStage: Int) {
                    if (!isTaskCancelled(taskId)) {
                        onProgress(downloaded, total, speed, eta, streamStage)
                    }
                }
                fun onFinished() {}
            }

            val pyResult = extractorModule.callAttr(
                "download_video_stream",
                url.trim(),
                formatId,
                outputFilePath,
                listener,
                taskId,
                speedMode
            )

            val resultJson = JSONObject(pyResult.toString())
            val status = resultJson.optString("status")

            when (status) {
                "success" -> {
                    Result.success(
                        PythonDownloadResult(
                            status = "success",
                            filePath = resultJson.optString("file_path", outputFilePath)
                        )
                    )
                }
                "mux_needed" -> {
                    Result.success(
                        PythonDownloadResult(
                            status = "mux_needed",
                            filePath = resultJson.optString("output_path", outputFilePath),
                            videoPath = resultJson.optString("video_path"),
                            audioPath = resultJson.optString("audio_path")
                        )
                    )
                }
                "aborted" -> {
                    Result.success(
                        PythonDownloadResult(
                            status = "aborted",
                            filePath = outputFilePath
                        )
                    )
                }
                else -> {
                    val isNetwork = resultJson.optBoolean("is_network_error", false)
                    val errCode = resultJson.optString("error_code", if (isNetwork) "network" else "general")
                    val msg = resultJson.optString("message", "Download interrupted.")
                    Result.success(
                        PythonDownloadResult(
                            status = "error",
                            filePath = outputFilePath,
                            isNetworkError = isNetwork,
                            errorCode = errCode,
                            errorMessage = msg
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Download exception", e)
            val errStr = e.message ?: ""
            val isNetwork = errStr.contains("Errno 7") || errStr.contains("Network") || errStr.contains("timed out")
            val errCode = when {
                isNetwork -> "network"
                errStr.contains("416") -> "http_416"
                else -> "general"
            }
            Result.success(
                PythonDownloadResult(
                    status = "error",
                    filePath = outputFilePath,
                    isNetworkError = isNetwork,
                    errorCode = errCode,
                    errorMessage = errStr
                )
            )
        }
    }
}
