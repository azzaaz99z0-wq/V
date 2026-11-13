package com.example.model

data class AudioTrack(
    val id: String,
    val language: String,
    val displayName: String,
    val isOriginal: Boolean = false,
    val bitrate: Int = 128
)

data class MediaFormat(
    val formatId: String,
    val resolution: String,
    val ext: String,
    val filesizeBytes: Long,
    val filesizeFormatted: String,
    val streamType: String,
    val hasAudio: Boolean,
    val hasVideo: Boolean,
    val fps: Int = 0,
    val formatNote: String = "",
    val streamUrl: String = "",
    val qualityScore: Int = 0,
    val height: Int = 0
)

data class PlaylistItem(
    val id: String,
    val title: String,
    val url: String,
    val duration: String,
    val thumbnail: String = ""
)

data class MediaInfo(
    val title: String,
    val duration: String,
    val durationSeconds: Long,
    val uploader: String,
    val thumbnail: String,
    val webpageUrl: String,
    val formats: List<MediaFormat>,
    val audioTracks: List<AudioTrack> = emptyList(),
    val isPlaylist: Boolean = false,
    val playlistItems: List<PlaylistItem> = emptyList()
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadTask(
    val id: String,
    val videoTitle: String,
    val resolution: String,
    val formatId: String,
    val ext: String,
    val webpageUrl: String,
    val thumbnail: String = "",
    val progress: Int = 0, // 0 - 100
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val speed: String = "",
    val eta: String = "",
    val audioTrackId: String = "",
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val localUri: String? = null,
    val savePath: String = "",
    val errorMessage: String? = null
)
