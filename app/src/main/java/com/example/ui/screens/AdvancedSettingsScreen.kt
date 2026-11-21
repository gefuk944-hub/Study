package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.FloatingPanelSize
import com.example.data.MediaStoreRepository
import com.example.data.MicAudioSource
import com.example.data.RecorderSettings
import com.example.ui.components.GamingCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.SettingToggleRow
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CarbonSurfaceHigh
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.RecordCrimson
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun AdvancedSettingsScreen(
    settings: RecorderSettings,
    hasOverlayPermission: Boolean,
    onBack: () -> Unit,
    onUpdateSettings: ((RecorderSettings) -> RecorderSettings) -> Unit,
    onToggleFloatingOverlay: (Boolean) -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onRestoreAllDefaults: () -> Unit
) {
    var confirmReset by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("adv_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Column {
                        Text("Advanced Settings", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                        Text("All preferences save locally on device via DataStore", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }

            // Floating Control Panel Customization
            item {
                GamingCard(accentColor = ElectricBlue) {
                    SectionHeader(
                        title = "Floating Game Control Panel",
                        subtitle = "Lightweight movable overlay for one-hand control over games",
                        icon = Icons.Default.PictureInPictureAlt,
                        accent = ElectricBlue
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SettingToggleRow(
                        title = "Show Floating Control Panel",
                        subtitle = if (hasOverlayPermission) "Draggable edge-snapping HUD active" else "Tap to grant Display Over Other Apps permission",
                        checked = settings.floatingControlEnabled,
                        onCheckedChange = onToggleFloatingOverlay,
                        accentColor = ElectricBlue,
                        testTag = "adv_floating_toggle"
                    )
                    SettingToggleRow(
                        title = "Compact Pill Mode",
                        subtitle = "Start collapsed to a tiny orb to prevent screen obstruction",
                        checked = settings.floatingCompactMode,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(floatingCompactMode = v) } },
                        accentColor = ElectricBlue
                    )
                    SettingToggleRow(
                        title = "Hide Overlay While Recording",
                        subtitle = "Automatically hides floating HUD during capture for clean gameplay footage",
                        checked = settings.hideFloatingWhileRecording,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(hideFloatingWhileRecording = v) } },
                        accentColor = NeonViolet
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Panel Transparency", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(
                            "${(settings.floatingOpacity * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyberCyan
                        )
                    }
                    Slider(
                        value = settings.floatingOpacity,
                        onValueChange = { alpha -> onUpdateSettings { it.copy(floatingOpacity = alpha) } },
                        valueRange = 0.30f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = CyberCyan, activeTrackColor = CyberCyan),
                        modifier = Modifier.testTag("floating_opacity_slider")
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Panel Size", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FloatingPanelSize.entries.forEach { sz ->
                            FilterChip(
                                selected = settings.floatingSize == sz,
                                onClick = { onUpdateSettings { it.copy(floatingSize = sz) } },
                                label = { Text(sz.label) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue,
                                    selectedLabelColor = Color.White,
                                    containerColor = CarbonSurfaceHigh,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }

            // Countdown, Auto-Stop & Duration Limit
            item {
                GamingCard(accentColor = NeonViolet) {
                    SectionHeader(
                        title = "Countdown & Auto-Stop Guard",
                        subtitle = "Prevents corrupted recordings and full-disk crashes",
                        icon = Icons.Default.Timer,
                        accent = NeonViolet
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Recording Countdown", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 3, 5, 10).forEach { sec ->
                            FilterChip(
                                selected = settings.countdownSeconds == sec,
                                onClick = { onUpdateSettings { it.copy(countdownSeconds = sec) } },
                                label = { Text(if (sec == 0) "Off" else "${sec}s") },
                                modifier = Modifier.testTag("countdown_${sec}s"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonViolet,
                                    selectedLabelColor = Color.White,
                                    containerColor = CarbonSurfaceHigh,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Auto-Stop Free Storage Reserve", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(512, 1024, 2048).forEach { mb ->
                            FilterChip(
                                selected = settings.autoStopLowStorageMb == mb,
                                onClick = { onUpdateSettings { it.copy(autoStopLowStorageMb = mb) } },
                                label = { Text(if (mb >= 1024) "${mb / 1024} GB" else "$mb MB") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyberCyan,
                                    selectedLabelColor = ObsidianVoid,
                                    containerColor = CarbonSurfaceHigh,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Max Recording Duration Limit", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0, 15, 30, 60, 120).forEach { mins ->
                            FilterChip(
                                selected = settings.maxDurationMinutes == mins,
                                onClick = { onUpdateSettings { it.copy(maxDurationMinutes = mins) } },
                                label = { Text(if (mins == 0) "Unlimited" else "${mins}m") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue,
                                    selectedLabelColor = Color.White,
                                    containerColor = CarbonSurfaceHigh,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }

            // Microphone Hardware Source Selector
            item {
                GamingCard {
                    SectionHeader(
                        title = "Microphone Audio Source",
                        subtitle = settings.micSource.description,
                        icon = Icons.Default.Mic,
                        accent = CyberCyan
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MicAudioSource.entries.forEach { src ->
                            FilterChip(
                                selected = settings.micSource == src,
                                onClick = { onUpdateSettings { it.copy(micSource = src) } },
                                label = { Text(src.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyberCyan,
                                    selectedLabelColor = ObsidianVoid,
                                    containerColor = CarbonSurfaceHigh,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }

            // Notification, Timer, Haptics & Touch Indicators
            item {
                GamingCard {
                    SectionHeader(
                        title = "Controls, Feedback & Folders",
                        subtitle = "Custom behavior while gaming",
                        icon = Icons.Default.NotificationsActive,
                        accent = ElectricBlue
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SettingToggleRow(
                        title = "Show Recording Timer",
                        subtitle = "Display live elapsed duration on HUD and notification",
                        checked = settings.showRecordingTimer,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(showRecordingTimer = v) } }
                    )
                    SettingToggleRow(
                        title = "Notification Quick Controls",
                        subtitle = "Pause, Mute Mic, and Stop buttons directly in status shade",
                        checked = settings.notificationControlsEnabled,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(notificationControlsEnabled = v) } }
                    )
                    SettingToggleRow(
                        title = "Haptic Feedback",
                        subtitle = "Subtle tactile click on record start/stop & screenshot capture",
                        checked = settings.hapticFeedbackEnabled,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(hapticFeedbackEnabled = v) } }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.TouchApp, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Show Screen Touches", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text(
                                    "Android restricts third-party apps from toggling 'Show taps'. Open Developer Options to enable system touch dots.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(onClick = onOpenDeveloperOptions) {
                            Text("System")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Videos: ${MediaStoreRepository.VIDEO_FOLDER}/  •  Shots: ${MediaStoreRepository.SCREENSHOT_FOLDER}/",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Restore All Defaults
            item {
                OutlinedButton(
                    onClick = { confirmReset = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("restore_all_defaults_button"),
                    border = BorderStroke(1.dp, RecordCrimson.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = RecordCrimson)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restore All Settings to Defaults", color = RecordCrimson)
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = CarbonSurface,
            title = { Text("Restore Defaults?", color = TextPrimary) },
            text = {
                Text(
                    "Reset all video, audio mixer, noise cancellation, and overlay settings back to factory defaults? Your saved recordings will not be touched.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRestoreAllDefaults()
                        confirmReset = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RecordCrimson)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            }
        )
    }
}
