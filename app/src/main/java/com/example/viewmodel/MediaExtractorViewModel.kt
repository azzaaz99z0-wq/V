package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AppLanguage
import com.example.model.AppStrings
import com.example.model.AppThemeMode
import com.example.model.AudioTrack
import com.example.model.DownloadSpeedMode
import com.example.model.DownloadStatus
import com.example.model.DownloadTask
import com.example.model.MediaFormat
import com.example.model.MediaInfo
import com.example.model.PlaylistItem
import com.example.service.DownloadNotificationHelper
import com.example.service.ExtractionException
import com.example.service.MediaDownloadForegroundService
import com.example.service.MediaDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

data class MediaExtractorUiState(
    val urlInput: String = "",
    val isExtracting: Boolean = false,
    val extractionProgressMessage: String = "",
    val extractionError: String? = null,
    val isNetworkError: Boolean = false,
    val mediaInfo: MediaInfo? = null,
    val selectedFormat: MediaFormat? = null,
    val selectedAudioTrack: AudioTrack? = null,
    val downloadTasks: List<DownloadTask> = emptyList(),
    val currentTab: Int = 0, // 0 = Download (Left), 1 = Downloads Queue (Right)
    val filterType: FormatFilter = FormatFilter.VIDEO,
    val snackbarMessage: String? = null,
    val language: AppLanguage = AppLanguage.ENGLISH, // English default
    val themeMode: AppThemeMode = AppThemeMode.LIGHT, // Light mode default
    val speedMode: DownloadSpeedMode = DownloadSpeedMode.SAFE_BALANCED,
    val videoSavePath: String = "Movies/StreamVault",
    val audioSavePath: String = "Music/StreamVault",
    val showSettingsDialog: Boolean = false
)

enum class FormatFilter {
    VIDEO,      // Sound + Picture (Video + Audio)
    AUDIO_ONLY, // Audio stream only (MP3/M4A)
    ALL         // All formats
}

class MediaExtractorViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "MediaExtractorVM"
    private val mediaDownloader = MediaDownloader(application)
    private val extractorManager = mediaDownloader.pythonManager

    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val tasksFile = File(application.filesDir, "streamvault_tasks.json")
    private val prefs = application.getSharedPreferences("streamvault_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(MediaExtractorUiState())
    val uiState: StateFlow<MediaExtractorUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
        loadPersistedTasks()
    }

    private fun loadPreferences() {
        val langCode = prefs.getString("pref_language", AppLanguage.ENGLISH.code) ?: AppLanguage.ENGLISH.code
        val lang = AppLanguage.values().find { it.code == langCode } ?: AppLanguage.ENGLISH

        val themeName = prefs.getString("pref_theme", AppThemeMode.LIGHT.name) ?: AppThemeMode.LIGHT.name
        val theme = try { AppThemeMode.valueOf(themeName) } catch (e: Exception) { AppThemeMode.LIGHT }

        val speedName = prefs.getString("pref_speed", DownloadSpeedMode.SAFE_BALANCED.name) ?: DownloadSpeedMode.SAFE_BALANCED.name
        val speed = try { DownloadSpeedMode.valueOf(speedName) } catch (e: Exception) { DownloadSpeedMode.SAFE_BALANCED }

        val videoPath = prefs.getString("pref_video_path", "Movies/StreamVault")
            ?.takeIf { it.isNotBlank() } ?: "Movies/StreamVault"
        val audioPath = prefs.getString("pref_audio_path", "Music/StreamVault")
            ?.takeIf { it.isNotBlank() } ?: "Music/StreamVault"

        _uiState.update {
            it.copy(
                language = lang,
                themeMode = theme,
                speedMode = speed,
                videoSavePath = videoPath,
                audioSavePath = audioPath
            )
        }
    }

    fun setLanguage(lang: AppLanguage) {
        _uiState.update { it.copy(language = lang) }
        prefs.edit().putString("pref_language", lang.code).apply()
    }

    fun setThemeMode(mode: AppThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        prefs.edit().putString("pref_theme", mode.name).apply()
    }

    fun setSpeedMode(mode: DownloadSpeedMode) {
        _uiState.update { it.copy(speedMode = mode) }
        prefs.edit().putString("pref_speed", mode.name).apply()
    }

    fun setVideoSavePath(path: String) {
        val cleaned = path.trim().trim('/').replace("\\", "/").ifBlank { "Movies/StreamVault" }
        _uiState.update {
            it.copy(
                videoSavePath = cleaned,
                snackbarMessage = "${AppStrings.get(it.language, "storage_path_saved")}: $cleaned"
            )
        }
        prefs.edit().putString("pref_video_path", cleaned).apply()
    }

    fun setAudioSavePath(path: String) {
        val cleaned = path.trim().trim('/').replace("\\", "/").ifBlank { "Music/StreamVault" }
        _uiState.update {
            it.copy(
                audioSavePath = cleaned,
                snackbarMessage = "${AppStrings.get(it.language, "storage_path_saved")}: $cleaned"
            )
        }
        prefs.edit().putString("pref_audio_path", cleaned).apply()
    }

    fun openSettings() {
        _uiState.update { it.copy(showSettingsDialog = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(showSettingsDialog = false) }
    }

    fun onUrlChanged(newUrl: String) {
        _uiState.update { it.copy(urlInput = newUrl, extractionError = null, isNetworkError = false) }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(currentTab = tabIndex) }
    }

    fun setFilter(filter: FormatFilter) {
        _uiState.update { it.copy(filterType = filter) }
    }

    fun selectFormat(format: MediaFormat) {
        _uiState.update { it.copy(selectedFormat = format) }
    }

    fun selectAudioTrack(track: AudioTrack) {
        _uiState.update { it.copy(selectedAudioTrack = track) }
    }

    fun clearError() {
        _uiState.update { it.copy(extractionError = null, isNetworkError = false, snackbarMessage = null) }
    }

    private fun sanitizeUrl(input: String): String {
        val trimmed = input.trim()
        val matcher = Pattern.compile("https?://[^\\s]+").matcher(trimmed)
        val extracted = if (matcher.find()) matcher.group(0) else trimmed
        return extracted.replace("(?<!:)//+".toRegex(), "/").trim()
    }

    fun fetchMediaInfo() {
        val rawInput = _uiState.value.urlInput
        val cleanUrl = sanitizeUrl(rawInput)
        val currentLang = _uiState.value.language

        if (cleanUrl.isBlank() || !cleanUrl.startsWith("http")) {
            _uiState.update {
                it.copy(
                    extractionError = AppStrings.getErrorByCode(currentLang, "unsupported_url"),
                    isNetworkError = false
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    urlInput = cleanUrl,
                    mediaInfo = null,
                    selectedFormat = null,
                    selectedAudioTrack = null,
                    isExtracting = true,
                    extractionProgressMessage = AppStrings.get(currentLang, "btn_analyzing"),
                    extractionError = null,
                    isNetworkError = false
                )
            }

            try {
                val result = extractorManager.extractFormats(cleanUrl)

                result.onSuccess { info ->
                    val defaultFormat = info.formats.firstOrNull { it.hasVideo && it.hasAudio }
                        ?: info.formats.firstOrNull()
                    val defaultAudio = info.audioTracks.firstOrNull { it.isOriginal }
                        ?: info.audioTracks.firstOrNull()

                    _uiState.update {
                        it.copy(
                            isExtracting = false,
                            extractionProgressMessage = "",
                            mediaInfo = info,
                            selectedFormat = defaultFormat,
                            selectedAudioTrack = defaultAudio,
                            extractionError = null,
                            isNetworkError = false
                        )
                    }
                }.onFailure { error ->
                    Log.e(tag, "Inspection failed for URL: $cleanUrl", error)
                    val errCode = if (error is ExtractionException) error.errorCode else "general"
                    val isNet = errCode == "network" || errCode == "timeout"
                    val friendlyMsg = AppStrings.getErrorByCode(currentLang, errCode)

                    _uiState.update {
                        it.copy(
                            isExtracting = false,
                            extractionProgressMessage = "",
                            extractionError = friendlyMsg,
                            isNetworkError = isNet
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Unexpected error during inspection", e)
                _uiState.update {
                    it.copy(
                        isExtracting = false,
                        extractionProgressMessage = "",
                        extractionError = AppStrings.getErrorByCode(currentLang, "general"),
                        isNetworkError = false
                    )
                }
            }
        }
    }

    fun startDownload(formatToDownload: MediaFormat? = null) {
        val stateSnapshot = _uiState.value
        val info = stateSnapshot.mediaInfo ?: return
        val format = formatToDownload ?: stateSnapshot.selectedFormat ?: return
        val chosenAudio = stateSnapshot.selectedAudioTrack

        val effectiveFormatId = if (chosenAudio != null && format.formatId.contains("+")) {
            val vId = format.formatId.split("+")[0]
            "$vId+${chosenAudio.id}"
        } else {
            format.formatId
        }

        val isAudio = format.ext.lowercase() in listOf("mp3", "m4a", "aac", "wav") || !format.hasVideo
        val destPath = if (isAudio) stateSnapshot.audioSavePath else stateSnapshot.videoSavePath

        val taskId = UUID.randomUUID().toString()
        val newTask = DownloadTask(
            id = taskId,
            videoTitle = info.title,
            resolution = format.resolution,
            formatId = effectiveFormatId,
            ext = format.ext,
            webpageUrl = info.webpageUrl,
            thumbnail = info.thumbnail,
            progress = 0,
            bytesDownloaded = 0L,
            totalBytes = format.filesizeBytes,
            audioTrackId = chosenAudio?.id ?: "",
            savePath = destPath,
            status = DownloadStatus.QUEUED
        )

        _uiState.update { current ->
            current.copy(
                downloadTasks = listOf(newTask) + current.downloadTasks,
                snackbarMessage = "${AppStrings.get(current.language, "status_queued")}: ${info.title.take(30)}"
            )
        }
        persistTasks()
        launchDownloadJob(newTask)
    }

    /**
     * Batch downloads all playlist items at optimized 360p resolution.
     */
    fun queueAllPlaylistItems(items: List<PlaylistItem>) {
        if (items.isEmpty()) return
        val destPath = _uiState.value.videoSavePath
        val newTasks = items.map { item ->
            DownloadTask(
                id = UUID.randomUUID().toString(),
                videoTitle = item.title,
                resolution = "360p",
                formatId = "18",
                ext = "mp4",
                webpageUrl = item.url,
                thumbnail = item.thumbnail,
                progress = 0,
                bytesDownloaded = 0L,
                totalBytes = 0L,
                savePath = destPath,
                status = DownloadStatus.QUEUED
            )
        }

        _uiState.update { current ->
            current.copy(
                downloadTasks = newTasks + current.downloadTasks,
                currentTab = 1,
                snackbarMessage = "${AppStrings.get(current.language, "status_queued")} (${items.size})"
            )
        }
        persistTasks()
        newTasks.forEach { launchDownloadJob(it) }
    }

    fun queuePlaylistItem(item: PlaylistItem) {
        val destPath = _uiState.value.videoSavePath
        val newTask = DownloadTask(
            id = UUID.randomUUID().toString(),
            videoTitle = item.title,
            resolution = "360p",
            formatId = "18",
            ext = "mp4",
            webpageUrl = item.url,
            thumbnail = item.thumbnail,
            progress = 0,
            bytesDownloaded = 0L,
            totalBytes = 0L,
            savePath = destPath,
            status = DownloadStatus.QUEUED
        )

        _uiState.update { current ->
            current.copy(
                downloadTasks = listOf(newTask) + current.downloadTasks,
                currentTab = 1,
                snackbarMessage = "${AppStrings.get(current.language, "status_queued")}: ${item.title.take(30)}"
            )
        }
        persistTasks()
        launchDownloadJob(newTask)
    }

    private fun launchDownloadJob(task: DownloadTask) {
        extractorManager.resetCancel(task.id)

        val job = viewModelScope.launch {
            val stateNow = _uiState.value
            val lang = stateNow.language
            val speedMode = stateNow.speedMode.name
            val videoPath = stateNow.videoSavePath
            val audioPath = stateNow.audioSavePath

            try {
                MediaDownloadForegroundService.startOrUpdate(getApplication(), task, lang)

                mediaDownloader.executeDownloadTask(
                    task = task,
                    language = lang,
                    speedMode = speedMode,
                    videoSavePath = videoPath,
                    audioSavePath = audioPath
                ) { updatedTask ->
                    if (updatedTask.status == DownloadStatus.COMPLETED) {
                        DownloadNotificationHelper.notifyDownloadCompleted(getApplication(), updatedTask, lang)
                        MediaDownloadForegroundService.stop(getApplication())

                        val isAudio = updatedTask.ext in listOf("mp3", "m4a", "aac", "wav") || updatedTask.resolution.contains("Audio")
                        val destinationText = updatedTask.savePath.ifBlank { if (isAudio) audioPath else videoPath }

                        _uiState.update { current ->
                            current.copy(
                                downloadTasks = current.downloadTasks.filterNot { it.id == task.id },
                                snackbarMessage = "${AppStrings.get(current.language, "status_saved")}: $destinationText (${updatedTask.videoTitle.take(28)})"
                            )
                        }
                        persistTasks()
                    } else if (updatedTask.status == DownloadStatus.DOWNLOADING) {
                        if (updatedTask.progress % 5 == 0) {
                            MediaDownloadForegroundService.startOrUpdate(getApplication(), updatedTask, lang)
                        }
                        updateTaskInState(updatedTask)
                    } else {
                        updateTaskInState(updatedTask)
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException && !extractorManager.isTaskCancelled(task.id)) {
                    Log.e(tag, "Task ${task.id} interrupted", e)
                    val rawMsg = e.message ?: ""
                    val isNet = rawMsg.contains("Errno 7") || rawMsg.contains("Network") || rawMsg.contains("timed out")
                    val friendlyMsg = if (isNet) {
                        AppStrings.getErrorByCode(lang, "network")
                    } else {
                        rawMsg.ifBlank { AppStrings.getErrorByCode(lang, "general") }
                    }

                    val failedTask = task.copy(
                        status = if (isNet) DownloadStatus.PAUSED else DownloadStatus.FAILED,
                        errorMessage = friendlyMsg,
                        speed = "",
                        eta = ""
                    )
                    updateTaskInState(failedTask)
                    DownloadNotificationHelper.notifyDownloadPaused(getApplication(), failedTask, lang)
                }
            } finally {
                downloadJobs.remove(task.id)
                if (downloadJobs.isEmpty()) {
                    MediaDownloadForegroundService.stop(getApplication())
                }
            }
        }
        downloadJobs[task.id] = job
    }

    fun pauseDownload(taskId: String) {
        extractorManager.requestCancel(taskId)
        downloadJobs[taskId]?.cancel()
        downloadJobs.remove(taskId)
        updateTaskInStateById(taskId) { it.copy(status = DownloadStatus.PAUSED, speed = "", eta = "") }
        persistTasks()
        if (downloadJobs.isEmpty()) {
            MediaDownloadForegroundService.stop(getApplication())
        }
    }

    fun resumeDownload(taskId: String) {
        val task = _uiState.value.downloadTasks.find { it.id == taskId } ?: return
        if (task.status == DownloadStatus.DOWNLOADING) return

        extractorManager.resetCancel(taskId)
        val updated = task.copy(status = DownloadStatus.QUEUED, errorMessage = null)
        updateTaskInState(updated)
        persistTasks()
        launchDownloadJob(updated)
    }

    fun cancelDownload(taskId: String) {
        val task = _uiState.value.downloadTasks.find { it.id == taskId }
        extractorManager.requestCancel(taskId)
        downloadJobs[taskId]?.cancel()
        downloadJobs.remove(taskId)
        if (task != null) {
            mediaDownloader.cleanUpTaskFiles(task)
        }
        updateTaskInStateById(taskId) { it.copy(status = DownloadStatus.CANCELLED, speed = "", eta = "") }
        persistTasks()
        if (downloadJobs.isEmpty()) {
            MediaDownloadForegroundService.stop(getApplication())
        }
    }

    fun retryDownload(taskId: String) {
        resumeDownload(taskId)
    }

    fun removeDownload(taskId: String) {
        val task = _uiState.value.downloadTasks.find { it.id == taskId }
        extractorManager.requestCancel(taskId)
        downloadJobs[taskId]?.cancel()
        downloadJobs.remove(taskId)
        if (task != null) {
            mediaDownloader.cleanUpTaskFiles(task)
        }
        _uiState.update { current ->
            current.copy(downloadTasks = current.downloadTasks.filterNot { it.id == taskId })
        }
        persistTasks()
        if (downloadJobs.isEmpty()) {
            MediaDownloadForegroundService.stop(getApplication())
        }
    }

    private fun updateTaskInState(task: DownloadTask) {
        _uiState.update { current ->
            current.copy(
                downloadTasks = current.downloadTasks.map {
                    if (it.id == task.id) task else it
                }
            )
        }
        if (task.progress % 10 == 0 || task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED) {
            persistTasks()
        }
    }

    private fun updateTaskInStateById(taskId: String, transform: (DownloadTask) -> DownloadTask) {
        _uiState.update { current ->
            current.copy(
                downloadTasks = current.downloadTasks.map {
                    if (it.id == taskId) transform(it) else it
                }
            )
        }
        persistTasks()
    }

    private fun persistTasks() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val array = JSONArray()
                _uiState.value.downloadTasks.filter { it.status != DownloadStatus.COMPLETED }.forEach { task ->
                    val obj = JSONObject().apply {
                        put("id", task.id)
                        put("videoTitle", task.videoTitle)
                        put("resolution", task.resolution)
                        put("formatId", task.formatId)
                        put("ext", task.ext)
                        put("webpageUrl", task.webpageUrl)
                        put("thumbnail", task.thumbnail)
                        put("progress", task.progress)
                        put("bytesDownloaded", task.bytesDownloaded)
                        put("totalBytes", task.totalBytes)
                        put("speed", task.speed)
                        put("eta", task.eta)
                        put("audioTrackId", task.audioTrackId)
                        put("savePath", task.savePath)
                        put("status", if (task.status == DownloadStatus.DOWNLOADING) DownloadStatus.PAUSED.name else task.status.name)
                        put("errorMessage", task.errorMessage ?: "")
                    }
                    array.put(obj)
                }
                tasksFile.writeText(array.toString())
            } catch (e: Exception) {
                Log.w(tag, "Could not persist tasks", e)
            }
        }
    }

    private fun loadPersistedTasks() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!tasksFile.exists()) return@launch
            try {
                val content = tasksFile.readText()
                val array = JSONArray(content)
                val loaded = mutableListOf<DownloadTask>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val rawStatus = obj.optString("status", DownloadStatus.PAUSED.name)
                    val status = try {
                        val parsed = DownloadStatus.valueOf(rawStatus)
                        if (parsed == DownloadStatus.DOWNLOADING) DownloadStatus.PAUSED else parsed
                    } catch (e: Exception) {
                        DownloadStatus.PAUSED
                    }

                    loaded.add(
                        DownloadTask(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            videoTitle = obj.optString("videoTitle", "Video"),
                            resolution = obj.optString("resolution", "Standard"),
                            formatId = obj.optString("formatId", "18"),
                            ext = obj.optString("ext", "mp4"),
                            webpageUrl = obj.optString("webpageUrl", ""),
                            thumbnail = obj.optString("thumbnail", ""),
                            progress = obj.optInt("progress", 0),
                            bytesDownloaded = obj.optLong("bytesDownloaded", 0L),
                            totalBytes = obj.optLong("totalBytes", 0L),
                            speed = obj.optString("speed", ""),
                            eta = obj.optString("eta", ""),
                            audioTrackId = obj.optString("audioTrackId", ""),
                            savePath = obj.optString("savePath", ""),
                            status = status,
                            errorMessage = obj.optString("errorMessage").ifBlank { null }
                        )
                    )
                }
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(downloadTasks = loaded) }
                }
            } catch (e: Exception) {
                Log.w(tag, "Could not load persisted tasks", e)
            }
        }
    }
}
