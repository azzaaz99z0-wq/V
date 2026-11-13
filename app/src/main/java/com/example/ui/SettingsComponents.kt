package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AppLanguage
import com.example.model.AppStrings
import com.example.model.AppThemeMode
import com.example.model.DownloadSpeedMode
import com.example.ui.theme.StreamCyan
import com.example.ui.theme.StreamEmerald
import com.example.ui.theme.StreamIndigo
import com.example.viewmodel.MediaExtractorUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    uiState: MediaExtractorUiState,
    onLanguageSelect: (AppLanguage) -> Unit,
    onThemeSelect: (AppThemeMode) -> Unit,
    onSpeedSelect: (DownloadSpeedMode) -> Unit,
    onVideoPathSave: (String) -> Unit,
    onAudioPathSave: (String) -> Unit,
    onDismiss: () -> Unit,
    onContactSupport: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lang = uiState.language

    var showLanguageDialog by remember { mutableStateOf(false) }
    var editingVideoPath by remember { mutableStateOf(false) }
    var editingAudioPath by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = StreamCyan, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = AppStrings.get(lang, "settings_title"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // 1. Dark Mode Checkbox (Checked = Dark Mode, Unchecked = Light Mode)
            SettingsSectionCard(
                title = AppStrings.get(lang, "theme_title"),
                icon = Icons.Default.DarkMode
            ) {
                val isDark = uiState.themeMode == AppThemeMode.DARK
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onThemeSelect(if (isDark) AppThemeMode.LIGHT else AppThemeMode.DARK)
                        }
                        .testTag("dark_mode_checkbox_row")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = AppStrings.get(lang, "theme_dark_checkbox"),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = AppStrings.get(lang, "theme_dark_desc"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Checkbox(
                            checked = isDark,
                            onCheckedChange = { checked ->
                                onThemeSelect(if (checked) AppThemeMode.DARK else AppThemeMode.LIGHT)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = StreamCyan,
                                checkmarkColor = Color(0xFF0F172A)
                            ),
                            modifier = Modifier.testTag("dark_mode_checkbox")
                        )
                    }
                }
            }

            // 2. Language Selector Row -> Opens Floating Dialog with Radio List + Done Button
            SettingsSectionCard(
                title = AppStrings.get(lang, "language_title"),
                icon = Icons.Default.Language
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showLanguageDialog = true }
                        .testTag("language_picker_row")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.language.nativeName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = StreamCyan
                            )
                            Text(
                                text = AppStrings.get(lang, "language_desc"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Select Language",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. Editable Storage Locations -> Opens Floating Path Editor Dialog
            SettingsSectionCard(
                title = AppStrings.get(lang, "storage_title"),
                icon = Icons.Default.Folder
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Video Save Path Row
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { editingVideoPath = true }
                            .testTag("edit_video_path_row")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = StreamEmerald, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = AppStrings.get(lang, "storage_video_title"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = uiState.videoSavePath,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = AppStrings.get(lang, "storage_tap_to_edit"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StreamCyan
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Video Path",
                                tint = StreamCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Audio Save Path Row
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { editingAudioPath = true }
                            .testTag("edit_audio_path_row")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = StreamIndigo, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = AppStrings.get(lang, "storage_audio_title"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = uiState.audioSavePath,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = AppStrings.get(lang, "storage_tap_to_edit"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StreamCyan
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Audio Path",
                                tint = StreamCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // 4. Download Speed & Stability
            SettingsSectionCard(
                title = AppStrings.get(lang, "speed_title"),
                icon = Icons.Default.Speed
            ) {
                Text(
                    text = AppStrings.get(lang, "speed_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DownloadSpeedMode.values().forEach { mode ->
                        val isSelected = mode == uiState.speedMode
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) StreamCyan.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSpeedSelect(mode) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSpeedSelect(mode) },
                                    colors = RadioButtonDefaults.colors(selectedColor = StreamCyan)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (lang == AppLanguage.ARABIC) mode.labelAr else mode.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 5. Contact Us & Feedback Section
            SettingsSectionCard(
                title = AppStrings.get(lang, "support_title"),
                icon = Icons.Default.Email
            ) {
                Text(
                    text = AppStrings.get(lang, "feedback_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StreamCyan.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, tint = StreamCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = AppStrings.get(lang, "support_email"),
                            fontWeight = FontWeight.Bold,
                            color = StreamCyan,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onContactSupport,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StreamCyan,
                        contentColor = Color(0xFF0F172A)
                    )
                ) {
                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(AppStrings.get(lang, "btn_send_email"), fontWeight = FontWeight.Bold)
                }
            }

            // 6. About StreamVault -> Compact Clickable Row (Opens Modern Floating Dialog on Tap)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showAboutDialog = true }
                    .testTag("about_app_row")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(StreamCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = StreamCyan, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = AppStrings.get(lang, "about_title"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = AppStrings.get(lang, "about_tap_desc"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "View About",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Floating Language Picker Dialog with "Done" Button
    if (showLanguageDialog) {
        LanguagePickerDialog(
            currentLanguage = uiState.language,
            uiLanguage = lang,
            onConfirm = { chosen ->
                onLanguageSelect(chosen)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    // Floating Video Path Editor Dialog
    if (editingVideoPath) {
        EditSavePathDialog(
            title = AppStrings.get(lang, "storage_edit_video_title"),
            initialPath = uiState.videoSavePath,
            defaultPath = "Movies/StreamVault",
            presets = listOf("Movies/StreamVault", "Movies/Videos", "Download/StreamVault"),
            language = lang,
            onSave = { newPath ->
                onVideoPathSave(newPath)
                editingVideoPath = false
            },
            onDismiss = { editingVideoPath = false }
        )
    }

    // Floating Audio Path Editor Dialog
    if (editingAudioPath) {
        EditSavePathDialog(
            title = AppStrings.get(lang, "storage_edit_audio_title"),
            initialPath = uiState.audioSavePath,
            defaultPath = "Music/StreamVault",
            presets = listOf("Music/StreamVault", "Music/Audio", "Download/StreamVault Audio"),
            language = lang,
            onSave = { newPath ->
                onAudioPathSave(newPath)
                editingAudioPath = false
            },
            onDismiss = { editingAudioPath = false }
        )
    }

    // Modern Floating About StreamVault Dialog
    if (showAboutDialog) {
        AboutAppDialog(
            language = lang,
            onDismiss = { showAboutDialog = false }
        )
    }
}

@Composable
fun LanguagePickerDialog(
    currentLanguage: AppLanguage,
    uiLanguage: AppLanguage,
    onConfirm: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    var tempSelection by remember(currentLanguage) { mutableStateOf(currentLanguage) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Language, contentDescription = null, tint = StreamCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = AppStrings.get(uiLanguage, "language_title"),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppLanguage.values().forEach { itemLang ->
                    val selected = itemLang == tempSelection
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) StreamCyan.copy(alpha = 0.14f) else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { tempSelection = itemLang }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { tempSelection = itemLang },
                                colors = RadioButtonDefaults.colors(selectedColor = StreamCyan)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = itemLang.nativeName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = itemLang.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(tempSelection) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = StreamCyan,
                    contentColor = Color(0xFF0F172A)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("language_done_button")
            ) {
                Text(AppStrings.get(uiLanguage, "btn_done"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.get(uiLanguage, "btn_cancel_dialog"))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditSavePathDialog(
    title: String,
    initialPath: String,
    defaultPath: String,
    presets: List<String>,
    language: AppLanguage,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textValue by remember(initialPath) { mutableStateOf(initialPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = StreamCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_path_textfield"),
                    placeholder = { Text(AppStrings.get(language, "storage_path_hint")) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = StreamCyan
                    )
                )

                Text(
                    text = AppStrings.get(language, "storage_presets_label"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presets.forEach { preset ->
                        SuggestionChip(
                            onClick = { textValue = preset },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                TextButton(
                    onClick = { textValue = defaultPath },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(AppStrings.get(language, "btn_reset_default"), color = StreamCyan)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(textValue.ifBlank { defaultPath }) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = StreamCyan,
                    contentColor = Color(0xFF0F172A)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("save_path_button")
            ) {
                Text(AppStrings.get(language, "btn_save"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.get(language, "btn_cancel_dialog"))
            }
        }
    )
}

@Composable
fun AboutAppDialog(
    language: AppLanguage,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(StreamCyan, StreamIndigo))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = AppStrings.get(language, "app_title"),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = AppStrings.get(language, "about_version"),
                        style = MaterialTheme.typography.labelMedium,
                        color = StreamCyan
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = AppStrings.get(language, "about_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = AppStrings.get(language, "about_features_title"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = StreamCyan
                )

                listOf("about_feature_1", "about_feature_2", "about_feature_3").forEach { key ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StreamEmerald,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = AppStrings.get(language, key),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = StreamCyan,
                    contentColor = Color(0xFF0F172A)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(AppStrings.get(language, "btn_done"), fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = StreamCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
