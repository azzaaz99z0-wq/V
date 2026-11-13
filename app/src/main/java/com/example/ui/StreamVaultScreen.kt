package com.example.ui

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.AppLanguage
import com.example.model.AppStrings
import com.example.model.AudioTrack
import com.example.model.DownloadStatus
import com.example.model.DownloadTask
import com.example.model.MediaFormat
import com.example.model.MediaInfo
import com.example.model.PlaylistItem
import com.example.ui.theme.StreamAmber
import com.example.ui.theme.StreamCyan
import com.example.ui.theme.StreamEmerald
import com.example.ui.theme.StreamIndigo
import com.example.ui.theme.StreamRose
import com.example.viewmodel.FormatFilter
import com.example.viewmodel.MediaExtractorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamVaultScreen(
    viewModel: MediaExtractorViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val lang = uiState.language

    val activeCount = uiState.downloadTasks.count {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Keep Settings button strictly on the physical LEFT regardless of system/app RTL
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = viewModel::openSettings,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(48.dp)
                                .testTag("settings_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = AppStrings.get(lang, "app_title"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (uiState.currentTab == 0) AppStrings.get(lang, "tagline_extractor") else AppStrings.get(lang, "tagline_queue"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(
                                        Brush.linearGradient(listOf(StreamCyan, StreamIndigo))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            // Keep Download tab strictly on the LEFT and Downloads Queue tab strictly on the RIGHT
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    NavigationBarItem(
                        selected = uiState.currentTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        icon = { Icon(Icons.Default.Download, contentDescription = "Download") },
                        label = { Text(AppStrings.get(lang, "tab_download")) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF0F172A),
                            indicatorColor = StreamCyan
                        )
                    )

                    NavigationBarItem(
                        selected = uiState.currentTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        icon = {
                            if (activeCount > 0) {
                                BadgedBox(badge = { Badge { Text(activeCount.toString()) } }) {
                                    Icon(Icons.Default.Folder, contentDescription = "Downloads")
                                }
                            } else {
                                Icon(Icons.Default.Folder, contentDescription = "Downloads")
                            }
                        },
                        label = { Text(AppStrings.get(lang, "tab_downloads")) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF0F172A),
                            indicatorColor = StreamCyan
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        val contentDirection = if (lang == AppLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides contentDirection) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (uiState.currentTab == 0) {
                    ExtractorTabContent(
                        viewModel = viewModel,
                        onPaste = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            if (clipboard.hasPrimaryClip() &&
                                clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
                            ) {
                                val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!text.isNullOrBlank()) {
                                    viewModel.onUrlChanged(text.trim())
                                }
                            }
                        }
                    )
                } else {
                    DownloadsQueueTabContent(
                        tasks = uiState.downloadTasks,
                        videoSavePath = uiState.videoSavePath,
                        audioSavePath = uiState.audioSavePath,
                        language = lang,
                        onPause = viewModel::pauseDownload,
                        onResume = viewModel::resumeDownload,
                        onCancel = viewModel::cancelDownload,
                        onRetry = viewModel::retryDownload,
                        onRemove = viewModel::removeDownload,
                        onNavigateToExtractor = { viewModel.selectTab(0) }
                    )
                }

                if (uiState.showSettingsDialog) {
                    SettingsBottomSheet(
                        uiState = uiState,
                        onLanguageSelect = viewModel::setLanguage,
                        onThemeSelect = viewModel::setThemeMode,
                        onSpeedSelect = viewModel::setSpeedMode,
                        onVideoPathSave = viewModel::setVideoSavePath,
                        onAudioPathSave = viewModel::setAudioSavePath,
                        onDismiss = viewModel::closeSettings,
                        onContactSupport = {
                            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:streamvault.support1@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "[StreamVault Support & Feedback]")
                            }
                            try {
                                context.startActivity(emailIntent)
                            } catch (e: Exception) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "message/rfc822"
                                    putExtra(Intent.EXTRA_EMAIL, arrayOf("streamvault.support1@gmail.com"))
                                    putExtra(Intent.EXTRA_SUBJECT, "[StreamVault Support & Feedback]")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Send Email"))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ExtractorTabContent(
    viewModel: MediaExtractorViewModel,
    onPaste: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val lang = uiState.language

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                UrlInputCard(
                    url = uiState.urlInput,
                    isLoading = uiState.isExtracting,
                    progressMessage = uiState.extractionProgressMessage,
                    language = lang,
                    onUrlChange = viewModel::onUrlChanged,
                    onFetchClick = viewModel::fetchMediaInfo,
                    onPasteClick = onPaste
                )
            }
        }

        // Error Card
        uiState.extractionError?.let { errorMessage ->
            item {
                ErrorDisplayCard(
                    errorMessage = errorMessage,
                    isNetworkError = uiState.isNetworkError,
                    language = lang,
                    onRetry = viewModel::fetchMediaInfo,
                    onDismiss = viewModel::clearError,
                    modifier = Modifier.widthIn(max = 680.dp)
                )
            }
        }

        // Extracted Media Info Card
        uiState.mediaInfo?.let { mediaInfo ->
            if (mediaInfo.isPlaylist) {
                // Batch Playlist Card
                item {
                    PlaylistCard(
                        mediaInfo = mediaInfo,
                        language = lang,
                        onQueueAll = { viewModel.queueAllPlaylistItems(mediaInfo.playlistItems) },
                        onQueueItem = { item -> viewModel.queuePlaylistItem(item) },
                        modifier = Modifier.widthIn(max = 680.dp)
                    )
                }
            } else {
                item {
                    MediaInfoCard(
                        mediaInfo = mediaInfo,
                        selectedAudio = uiState.selectedAudioTrack,
                        onSelectAudio = viewModel::selectAudioTrack,
                        language = lang,
                        modifier = Modifier.widthIn(max = 680.dp)
                    )
                }

                // Filter Tabs
                item {
                    FormatFilterRow(
                        currentFilter = uiState.filterType,
                        language = lang,
                        onFilterSelected = viewModel::setFilter,
                        modifier = Modifier.widthIn(max = 680.dp)
                    )
                }

                // Formats List Header
                item {
                    val filteredFormats = getFilteredFormats(mediaInfo.formats, uiState.filterType)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 680.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${AppStrings.get(lang, "available_qualities")} (${filteredFormats.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (uiState.filterType == FormatFilter.AUDIO_ONLY) "Music Ready" else "Optimized Streams",
                            style = MaterialTheme.typography.labelSmall,
                            color = StreamEmerald,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                val formatsToDisplay = getFilteredFormats(mediaInfo.formats, uiState.filterType)
                items(formatsToDisplay, key = { it.formatId + it.resolution + it.ext }) { format ->
                    FormatItemCard(
                        format = format,
                        isSelected = format == uiState.selectedFormat,
                        language = lang,
                        onSelect = { viewModel.selectFormat(format) },
                        onDownload = {
                            viewModel.startDownload(format)
                            viewModel.selectTab(1)
                        },
                        modifier = Modifier.widthIn(max = 680.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistCard(
    mediaInfo: MediaInfo,
    language: AppLanguage,
    onQueueAll: () -> Unit,
    onQueueItem: (PlaylistItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(StreamIndigo.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = StreamIndigo, modifier = Modifier.size(26.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mediaInfo.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${mediaInfo.playlistItems.size} videos • ${mediaInfo.uploader}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onQueueAll,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StreamCyan, contentColor = Color(0xFF0F172A))
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(AppStrings.get(language, "btn_queue_all"), fontWeight = FontWeight.Bold)
            }

            Text(
                text = "Playlist Items (${mediaInfo.playlistItems.size})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                mediaInfo.playlistItems.take(20).forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.duration,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onQueueItem(item) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Download Item", tint = StreamCyan)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorDisplayCard(
    errorMessage: String,
    isNetworkError: Boolean,
    language: AppLanguage,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("error_display_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNetworkError) StreamAmber.copy(alpha = 0.12f) else StreamRose.copy(alpha = 0.12f)
        ),
        border = BorderStroke(
            1.dp,
            if (isNetworkError) StreamAmber.copy(alpha = 0.5f) else StreamRose.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isNetworkError) StreamAmber.copy(alpha = 0.2f) else StreamRose.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isNetworkError) Icons.Default.WifiOff else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = if (isNetworkError) StreamAmber else StreamRose,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isNetworkError) AppStrings.get(language, "err_network_title") else AppStrings.get(language, "err_attention"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isNetworkError) AppStrings.get(language, "err_check_internet") else AppStrings.get(language, "err_action_required"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isNetworkError) StreamAmber else StreamCyan
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(AppStrings.get(language, "btn_retry"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun DownloadsQueueTabContent(
    tasks: List<DownloadTask>,
    videoSavePath: String,
    audioSavePath: String,
    language: AppLanguage,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    onNavigateToExtractor: () -> Unit
) {
    if (tasks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = StreamCyan,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = AppStrings.get(language, "empty_queue_title"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = AppStrings.get(language, "empty_queue_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onNavigateToExtractor,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StreamCyan,
                        contentColor = Color(0xFF0F172A)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(AppStrings.get(language, "btn_download_new"), fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${AppStrings.get(language, "active_downloads")} (${tasks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = AppStrings.get(language, "resumable_badge"),
                        style = MaterialTheme.typography.labelSmall,
                        color = StreamCyan
                    )
                }
            }

            items(tasks, key = { it.id }) { task ->
                DownloadQueueItemCard(
                    task = task,
                    videoSavePath = videoSavePath,
                    audioSavePath = audioSavePath,
                    language = language,
                    onPause = { onPause(task.id) },
                    onResume = { onResume(task.id) },
                    onCancel = { onCancel(task.id) },
                    onRetry = { onRetry(task.id) },
                    onRemove = { onRemove(task.id) },
                    modifier = Modifier.widthIn(max = 680.dp)
                )
            }
        }
    }
}

/**
 * Natural, horizontal card for downloading tasks with full-width title,
 * FlowRow metadata tags so StatusPill is never squished or cut off,
 * clean horizontal "Downloaded / Total" metrics, and real-time speed & ETA.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadQueueItemCard(
    task: DownloadTask,
    videoSavePath: String,
    audioSavePath: String,
    language: AppLanguage,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAudio = task.ext in listOf("mp3", "m4a", "aac", "wav") || task.resolution.contains("Audio")
    val destinationFolder = task.savePath.ifBlank { if (isAudio) audioSavePath else videoSavePath }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("download_task_${task.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Natural Horizontal Title (full width)
            Text(
                text = task.videoTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // 2. Metadata Chips & Status Pill in FlowRow so nothing gets clipped!
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = StreamCyan.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${task.resolution} • ${task.ext.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = StreamCyan,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isAudio) StreamIndigo.copy(alpha = 0.15f) else StreamEmerald.copy(alpha = 0.15f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isAudio) Icons.Default.MusicNote else Icons.Default.Videocam,
                            contentDescription = null,
                            tint = if (isAudio) StreamIndigo else StreamEmerald,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = destinationFolder,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isAudio) StreamIndigo else StreamEmerald,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                StatusPill(task.status, language)
            }

            // 3. Smooth Full-Width Progress Bar
            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.QUEUED) {
                LinearProgressIndicator(
                    progress = { task.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = if (task.status == DownloadStatus.PAUSED) StreamAmber else StreamCyan,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                // Clean horizontal "Downloaded / Total" + Speed + ETA metrics row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val downloadedMb = task.bytesDownloaded / (1024f * 1024f)
                    val totalMb = task.totalBytes / (1024f * 1024f)
                    val sizeText = if (totalMb > 0) "%.1f / %.1f MB".format(downloadedMb, totalMb) else "%.1f MB".format(downloadedMb)

                    val metricsInfo = buildString {
                        append(sizeText)
                        if (task.speed.isNotBlank()) append(" • ${task.speed}")
                        if (task.eta.isNotBlank()) append(" • ${task.eta}")
                    }

                    Text(
                        text = metricsInfo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "${task.progress}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (task.status == DownloadStatus.PAUSED) StreamAmber else StreamCyan,
                        maxLines = 1
                    )
                }
            }

            // 4. Interrupted Notice
            if ((task.status == DownloadStatus.FAILED || task.status == DownloadStatus.PAUSED) && !task.errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StreamRose.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = StreamRose,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = task.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = StreamRose,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 5. Action Buttons Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (task.status) {
                    DownloadStatus.DOWNLOADING -> {
                        OutlinedButton(
                            onClick = onPause,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, tint = StreamAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(AppStrings.get(language, "btn_pause"), color = StreamAmber, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = StreamRose, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(AppStrings.get(language, "btn_cancel"), color = StreamRose, fontSize = 13.sp)
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        Button(
                            onClick = onResume,
                            colors = ButtonDefaults.buttonColors(containerColor = StreamCyan, contentColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(AppStrings.get(language, "btn_resume"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = StreamRose, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(AppStrings.get(language, "btn_cancel"), color = StreamRose, fontSize = 13.sp)
                        }
                    }
                    DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(containerColor = StreamCyan, contentColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(AppStrings.get(language, "btn_retry"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onRemove,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(AppStrings.get(language, "btn_remove"), fontSize = 13.sp)
                        }
                    }
                    DownloadStatus.QUEUED -> {
                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = StreamRose, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(AppStrings.get(language, "btn_cancel"), color = StreamRose, fontSize = 13.sp)
                        }
                    }
                    DownloadStatus.COMPLETED -> {}
                }
            }
        }
    }
}

@Composable
fun StatusPill(status: DownloadStatus, language: AppLanguage) {
    val (bgColor, textColor, key) = when (status) {
        DownloadStatus.DOWNLOADING -> Triple(StreamCyan.copy(alpha = 0.2f), StreamCyan, "status_downloading")
        DownloadStatus.PAUSED -> Triple(StreamAmber.copy(alpha = 0.2f), StreamAmber, "status_paused")
        DownloadStatus.QUEUED -> Triple(Color(0xFF334155), Color(0xFFCBD5E1), "status_queued")
        DownloadStatus.COMPLETED -> Triple(StreamEmerald.copy(alpha = 0.2f), StreamEmerald, "status_saved")
        DownloadStatus.FAILED -> Triple(StreamRose.copy(alpha = 0.2f), StreamRose, "status_interrupted")
        DownloadStatus.CANCELLED -> Triple(Color(0xFF334155), Color(0xFF94A3B8), "status_cancelled")
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = AppStrings.get(language, key),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun UrlInputCard(
    url: String,
    isLoading: Boolean,
    progressMessage: String,
    language: AppLanguage,
    onUrlChange: (String) -> Unit,
    onFetchClick: () -> Unit,
    onPasteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("url_input_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = AppStrings.get(language, "paste_url_title"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("url_text_field"),
                placeholder = {
                    Text(
                        text = AppStrings.get(language, "paste_url_hint"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (url.isNotBlank()) {
                            IconButton(
                                onClick = { onUrlChange("") },
                                modifier = Modifier.testTag("clear_url_button")
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear URL")
                            }
                        }
                        IconButton(
                            onClick = onPasteClick,
                            modifier = Modifier.testTag("paste_url_button")
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard", tint = StreamCyan)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StreamCyan,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Button(
                onClick = onFetchClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("fetch_info_button"),
                enabled = !isLoading && url.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StreamCyan,
                    contentColor = Color(0xFF0F172A)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = AppStrings.get(language, "btn_analyzing"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                } else {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppStrings.get(language, "btn_download"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            AnimatedVisibility(visible = isLoading && progressMessage.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = StreamCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = progressMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = StreamCyan
                    )
                }
            }
        }
    }
}

@Composable
fun MediaInfoCard(
    mediaInfo: MediaInfo,
    selectedAudio: AudioTrack?,
    onSelectAudio: (AudioTrack) -> Unit,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("media_info_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
            ) {
                if (mediaInfo.thumbnail.isNotBlank()) {
                    AsyncImage(
                        model = mediaInfo.thumbnail,
                        contentDescription = "Video thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = mediaInfo.duration,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = mediaInfo.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = mediaInfo.uploader,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${mediaInfo.formats.size} stream options",
                    style = MaterialTheme.typography.bodySmall,
                    color = StreamCyan,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Multi-Language Audio Track Selection (if more than 1 track exists)
            if (mediaInfo.audioTracks.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = AppStrings.get(language, "audio_track_label"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(mediaInfo.audioTracks) { track ->
                        val isSelected = track == selectedAudio
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectAudio(track) },
                            label = {
                                Text(
                                    text = if (track.isOriginal) "${track.displayName} ★" else track.displayName,
                                    fontSize = 12.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StreamIndigo.copy(alpha = 0.2f),
                                selectedLabelColor = StreamIndigo
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FormatFilterRow(
    currentFilter: FormatFilter,
    language: AppLanguage,
    onFilterSelected: (FormatFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        item {
            FilterChip(
                selected = currentFilter == FormatFilter.VIDEO,
                onClick = { onFilterSelected(FormatFilter.VIDEO) },
                label = { Text(AppStrings.get(language, "filter_complete_video")) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = StreamCyan.copy(alpha = 0.2f),
                    selectedLabelColor = StreamCyan
                )
            )
        }
        item {
            FilterChip(
                selected = currentFilter == FormatFilter.AUDIO_ONLY,
                onClick = { onFilterSelected(FormatFilter.AUDIO_ONLY) },
                label = { Text(AppStrings.get(language, "filter_audio_only")) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = StreamIndigo.copy(alpha = 0.2f),
                    selectedLabelColor = StreamIndigo
                )
            )
        }
        item {
            FilterChip(
                selected = currentFilter == FormatFilter.ALL,
                onClick = { onFilterSelected(FormatFilter.ALL) },
                label = { Text(AppStrings.get(language, "filter_all")) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = StreamCyan.copy(alpha = 0.2f),
                    selectedLabelColor = StreamCyan
                )
            )
        }
    }
}

@Composable
fun FormatItemCard(
    format: MediaFormat,
    isSelected: Boolean,
    language: AppLanguage,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) StreamCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val cardBackground = if (isSelected) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("format_card_${format.formatId}")
            .clickable { onSelect() }
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(getResolutionColor(format)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (format.hasVideo) Icons.Default.Videocam else Icons.Default.Audiotrack,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = format.resolution.replace("p", "P"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${format.resolution} (${format.ext.uppercase()})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = if (!format.hasVideo) "Audio Stream (Saved to Music)" else format.streamType,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!format.hasVideo) StreamIndigo else StreamEmerald,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Size: ${format.filesizeFormatted}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = StreamCyan
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onDownload,
                modifier = Modifier.testTag("download_button_${format.formatId}"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StreamCyan,
                    contentColor = Color(0xFF0F172A)
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = "Download stream", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = AppStrings.get(language, "btn_download"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}



private fun getResolutionColor(format: MediaFormat): Color {
    val res = format.resolution.lowercase()
    return when {
        !format.hasVideo -> StreamIndigo
        res.contains("best") -> StreamEmerald
        res.contains("2160") || res.contains("4k") -> StreamAmber
        res.contains("1440") || res.contains("2k") -> StreamAmber
        res.contains("1080") -> StreamCyan
        res.contains("720") -> Color(0xFF0284C7)
        res.contains("480") -> Color(0xFF475569)
        else -> Color(0xFF334155)
    }
}

private fun getFilteredFormats(formats: List<MediaFormat>, filter: FormatFilter): List<MediaFormat> {
    return when (filter) {
        FormatFilter.VIDEO -> formats.filter { it.hasVideo && it.hasAudio }
        FormatFilter.AUDIO_ONLY -> formats.filter { !it.hasVideo && it.hasAudio }
        FormatFilter.ALL -> formats
    }
}
