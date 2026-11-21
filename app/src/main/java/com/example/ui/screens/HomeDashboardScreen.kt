package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RecordedVideoItem
import com.example.data.RecorderSettings
import com.example.recording.RecordingPhase
import com.example.recording.RecordingSessionState
import com.example.system.DeviceCapabilities
import com.example.system.SystemTelemetry
import com.example.ui.components.SectionLabel
import com.example.ui.components.SegmentedAudioMeter
import com.example.ui.components.StatusPill
import com.example.ui.components.formatBytesHuman
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CarbonSurfaceHigh
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.RecordCrimson
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThermalAmber
import com.example.viewmodel.PermissionSnapshot

@Composable
fun HomeDashboardScreen(
    settings: RecorderSettings,
    recordingState: RecordingSessionState,
    telemetry: SystemTelemetry,
    capabilities: DeviceCapabilities,
    permissions: PermissionSnapshot,
    recentVideos: List<RecordedVideoItem>,
    videoCount: Int,
    screenshotCount: Int,
    onRequestStartRecording: () -> Unit,
    onTogglePauseResume: () -> Unit,
    onStopRecording: () -> Unit,
    onRequestScreenshot: () -> Unit,
    onToggleMicMute: () -> Unit,
    onToggleGameAudio: (Boolean) -> Unit,
    onToggleMicEnabled: (Boolean) -> Unit,
    onToggleFloatingOverlay: (Boolean) -> Unit,
    onStepDownThermal: () -> Unit,
    onOpenAudioStudio: () -> Unit,
    onOpenRecordingSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenVideo: (RecordedVideoItem) -> Unit,
    onOpenPerformanceMonitor: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    val isRecording = recordingState.phase == RecordingPhase.RECORDING
    val isPaused = recordingState.phase == RecordingPhase.PAUSED
    val isActive = recordingState.isActiveOrPaused

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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Clean Native Top App Bar (no neon clutter)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Zero Recorder",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = "${settings.resolution.shortLabel} • ${settings.fps.value} FPS • ${settings.codec.shortLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = onOpenPerformanceMonitor,
                            modifier = Modifier.testTag("nav_tile_performance")
                        ) {
                            Icon(
                                Icons.Default.Speed,
                                contentDescription = "Performance Monitor",
                                tint = TextSecondary
                            )
                        }
                        IconButton(
                            onClick = onOpenAdvancedSettings,
                            modifier = Modifier.testTag("nav_tile_advanced")
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "Advanced Settings",
                                tint = TextSecondary
                            )
                        }
                        IconButton(
                            onClick = onOpenPermissions,
                            modifier = Modifier.testTag("nav_tile_permissions")
                        ) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = "Permissions and Privacy",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }

            // Thermal Warning Banner (only shown if phone is genuinely hot)
            item {
                AnimatedVisibility(
                    visible = telemetry.thermalHealth.isWarning || recordingState.thermalWarningMessage != null
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ThermalAmber.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, ThermalAmber.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Thermal warning",
                                tint = ThermalAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Device warm (${"%.0f".format(telemetry.batteryTempCelsius)}°C)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ThermalAmber
                                )
                                Text(
                                    recordingState.thermalWarningMessage ?: "Lower bitrate & FPS to keep gameplay smooth.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = onStepDownThermal,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = ThermalAmber,
                                    contentColor = ObsidianVoid
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("thermal_step_down_button")
                            ) {
                                Text("Lower", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            // 2. Centered Pro Studio Recorder Stage (Clean matte surface, no wallpaper!)
            item {
                RecorderStageCard(
                    settings = settings,
                    recordingState = recordingState,
                    isRecording = isRecording,
                    isPaused = isPaused,
                    isActive = isActive,
                    hasOverlayPerm = permissions.hasOverlay,
                    onRequestStartRecording = onRequestStartRecording,
                    onTogglePauseResume = onTogglePauseResume,
                    onStopRecording = onStopRecording,
                    onRequestScreenshot = onRequestScreenshot,
                    onToggleMicMute = onToggleMicMute,
                    onToggleFloatingOverlay = onToggleFloatingOverlay
                )
            }

            // 3. Quick Setup Controls (Grouped Native Android List)
            item {
                Column {
                    SectionLabel("Quick Setup")
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = CarbonSurface,
                        border = BorderStroke(1.dp, CarbonBorder.copy(alpha = 0.72f))
                    ) {
                        Column {
                            QuickToggleRow(
                                icon = Icons.Default.SportsEsports,
                                title = "Game & Teammates Audio",
                                subtitle = if (!capabilities.supportsInternalAudioCapture) {
                                    "Requires Android 10 or newer"
                                } else if (settings.gameAudioEnabled) {
                                    "Internal stereo • ${(settings.gameAudioVolume * 100).toInt()}%"
                                } else {
                                    "Off"
                                },
                                checked = settings.gameAudioEnabled && capabilities.supportsInternalAudioCapture,
                                enabled = capabilities.supportsInternalAudioCapture,
                                onCheckedChange = onToggleGameAudio,
                                onRowClick = onOpenAudioStudio
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = CarbonBorder.copy(alpha = 0.55f)
                            )
                            QuickToggleRow(
                                icon = Icons.Default.Mic,
                                title = "Microphone",
                                subtitle = when {
                                    !permissions.hasMicrophone -> "Tap to configure mic permission"
                                    !settings.micEnabled -> "Off"
                                    settings.micMuted -> "Muted"
                                    else -> "${settings.voicePickupRange.shortLabel} range • NC ${settings.noiseCancellation.label}"
                                },
                                checked = settings.micEnabled,
                                enabled = true,
                                onCheckedChange = onToggleMicEnabled,
                                onRowClick = onOpenAudioStudio
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = CarbonBorder.copy(alpha = 0.55f)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onOpenRecordingSettings)
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .testTag("video_profile_quick_card"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Video Quality",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        color = TextPrimary
                                    )
                                    Text(
                                        "${settings.resolution.displayName} • ${settings.fps.label} • ~${"%.0f".format(settings.estimatedMbPerMinute())} MB/min",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Compact Device Status Strip (Storage • RAM • Temp)
            item {
                Column {
                    SectionLabel("Device Status")
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenPerformanceMonitor)
                            .testTag("open_performance_monitor_link"),
                        shape = RoundedCornerShape(16.dp),
                        color = CarbonSurface,
                        border = BorderStroke(1.dp, CarbonBorder.copy(alpha = 0.72f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CompactMetricColumn(
                                label = "Free Storage",
                                value = "${"%.1f".format(telemetry.availableStorageGb)} GB",
                                caption = "~${settings.estimatedMaxMinutes(telemetry.availableStorageBytes)} min",
                                valueColor = if (telemetry.isLowStorage) RecordCrimson else TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(34.dp)
                                    .background(CarbonBorder.copy(alpha = 0.6f))
                            )
                            CompactMetricColumn(
                                label = "Memory",
                                value = "${(telemetry.ramUsageFraction * 100).toInt()}%",
                                caption = "${telemetry.availableRamMb} MB free",
                                valueColor = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(34.dp)
                                    .background(CarbonBorder.copy(alpha = 0.6f))
                            )
                            CompactMetricColumn(
                                label = "Battery Temp",
                                value = "${"%.0f".format(telemetry.batteryTempCelsius)}°C",
                                caption = telemetry.thermalHealth.label,
                                valueColor = if (telemetry.thermalHealth.isWarning) ThermalAmber else SignalGreen,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 5. Recent Clips (Immediate access right from Home)
            item {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RECENT RECORDINGS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                letterSpacing = 0.9.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = TextMuted
                        )
                        TextButton(
                            onClick = onOpenLibrary,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.testTag("nav_tile_library")
                        ) {
                            Text(
                                text = "Library ($videoCount)",
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricBlue
                            )
                        }
                    }

                    if (recentVideos.isEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenLibrary),
                            shape = RoundedCornerShape(16.dp),
                            color = CarbonSurface,
                            border = BorderStroke(1.dp, CarbonBorder.copy(alpha = 0.72f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(CarbonSurfaceHigh),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "No recordings yet",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        color = TextPrimary
                                    )
                                    Text(
                                        if (screenshotCount > 0) "$screenshotCount screenshots saved in Gallery"
                                        else "Clips save directly to Movies/ZeroRecorder",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = CarbonSurface,
                            border = BorderStroke(1.dp, CarbonBorder.copy(alpha = 0.72f))
                        ) {
                            Column {
                                recentVideos.take(2).forEachIndexed { idx, clip ->
                                    if (idx > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = CarbonBorder.copy(alpha = 0.55f)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenVideo(clip) }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(CarbonSurfaceHigh),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = "Play ${clip.displayName}",
                                                tint = TextPrimary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                clip.displayName,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "${clip.formattedDuration} • ${clip.resolutionLabel} • ${clip.formattedSize}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = TextMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun RecorderStageCard(
    settings: RecorderSettings,
    recordingState: RecordingSessionState,
    isRecording: Boolean,
    isPaused: Boolean,
    isActive: Boolean,
    hasOverlayPerm: Boolean,
    onRequestStartRecording: () -> Unit,
    onTogglePauseResume: () -> Unit,
    onStopRecording: () -> Unit,
    onRequestScreenshot: () -> Unit,
    onToggleMicMute: () -> Unit,
    onToggleFloatingOverlay: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CarbonSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isRecording) RecordCrimson.copy(alpha = 0.55f) else CarbonBorder.copy(alpha = 0.72f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Pill
            StatusPill(
                text = when (recordingState.phase) {
                    RecordingPhase.RECORDING -> "RECORDING"
                    RecordingPhase.PAUSED -> "PAUSED"
                    RecordingPhase.COUNTDOWN -> "STARTING IN ${recordingState.countdownRemaining}"
                    RecordingPhase.FINALIZING -> "SAVING VIDEO"
                    RecordingPhase.IDLE -> "READY"
                },
                color = when (recordingState.phase) {
                    RecordingPhase.RECORDING -> RecordCrimson
                    RecordingPhase.PAUSED -> ThermalAmber
                    RecordingPhase.COUNTDOWN -> ElectricBlue
                    RecordingPhase.FINALIZING -> ElectricBlue
                    RecordingPhase.IDLE -> SignalGreen
                },
                leadingDot = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Crisp Tabular Timer Display
            Text(
                text = when {
                    recordingState.phase == RecordingPhase.COUNTDOWN -> "00:0${recordingState.countdownRemaining}"
                    isActive -> recordingState.formattedTimer
                    else -> "00:00"
                },
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                ),
                color = when {
                    isRecording -> TextPrimary
                    isPaused -> ThermalAmber
                    else -> TextPrimary.copy(alpha = 0.88f)
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isActive) {
                    "${formatBytesHuman(recordingState.estimatedCurrentBytes)} • ${recordingState.captureModeLabel}"
                } else {
                    "Audio: ${if (settings.gameAudioEnabled) "Game + Team" else "No Game"} • ${if (settings.micEnabled) "Mic (${settings.voicePickupRange.shortLabel})" else "Mic Off"}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            // Live Audio Level Meters while recording
            if (isActive) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CarbonSurfaceHigh)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiveMeterRow(
                        label = "Game & Team",
                        status = recordingState.audioTelemetry.gameStatus.label,
                        level = recordingState.audioTelemetry.gameLevel,
                        muted = !settings.gameAudioEnabled || settings.gameAudioMuted
                    )
                    LiveMeterRow(
                        label = "Microphone",
                        status = if (recordingState.micMuted) "Muted" else recordingState.audioTelemetry.micStatus.label,
                        level = recordingState.audioTelemetry.micLevel,
                        muted = !settings.micEnabled || recordingState.micMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // One-Hand Control Dock
            if (!isActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Screenshot
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            onClick = onRequestScreenshot,
                            shape = CircleShape,
                            color = CarbonSurfaceHigh,
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("take_screenshot_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = "Take screenshot",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Shot", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }

                    // Center: Iconic Hardware-Style Circular Record Trigger
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            onClick = onRequestStartRecording,
                            enabled = recordingState.phase == RecordingPhase.IDLE,
                            shape = CircleShape,
                            color = RecordCrimson.copy(alpha = 0.16f),
                            border = BorderStroke(2.5.dp, RecordCrimson),
                            modifier = Modifier
                                .size(76.dp)
                                .testTag("start_recording_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(RecordCrimson)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (recordingState.phase == RecordingPhase.FINALIZING) "Saving" else "Record",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary
                        )
                    }

                    // Right: Floating Overlay Toggle
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val overlayOn = recordingState.floatingOverlayActive
                        Surface(
                            onClick = { onToggleFloatingOverlay(!overlayOn) },
                            shape = CircleShape,
                            color = if (overlayOn) ElectricBlue.copy(alpha = 0.22f) else CarbonSurfaceHigh,
                            border = if (overlayOn) BorderStroke(1.dp, ElectricBlue) else null,
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("toggle_floating_panel_card")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.PictureInPictureAlt,
                                    contentDescription = if (hasOverlayPerm) "Toggle floating controls" else "Grant floating overlay permission",
                                    tint = if (overlayOn) ElectricBlue else TextPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (overlayOn) "HUD On" else "HUD",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (overlayOn) ElectricBlue else TextSecondary
                        )
                    }
                }
            } else {
                // Active Recording Dock: Pause/Resume — Stop — Mute Mic — Shot
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onStopRecording,
                        modifier = Modifier
                            .weight(1.25f)
                            .height(50.dp)
                            .testTag("stop_recording_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RecordCrimson,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    }

                    FilledTonalButton(
                        onClick = onTogglePauseResume,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("pause_resume_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CarbonSurfaceHigh,
                            contentColor = TextPrimary
                        )
                    ) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPaused) "Resume" else "Pause")
                    }

                    Surface(
                        onClick = onToggleMicMute,
                        shape = RoundedCornerShape(14.dp),
                        color = if (recordingState.micMuted) RecordCrimson.copy(alpha = 0.18f) else CarbonSurfaceHigh,
                        border = if (recordingState.micMuted) BorderStroke(1.dp, RecordCrimson) else null,
                        modifier = Modifier
                            .size(50.dp)
                            .testTag("live_mic_mute_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (recordingState.micMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = if (recordingState.micMuted) "Unmute microphone" else "Mute microphone",
                                tint = if (recordingState.micMuted) RecordCrimson else TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onRequestScreenshot,
                        shape = RoundedCornerShape(14.dp),
                        color = CarbonSurfaceHigh,
                        modifier = Modifier
                            .size(50.dp)
                            .testTag("live_screenshot_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Capture screenshot",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveMeterRow(
    label: String,
    status: String,
    level: Float,
    muted: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
            Text(status, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        Spacer(modifier = Modifier.height(5.dp))
        SegmentedAudioMeter(level = level, muted = muted)
    }
}

@Composable
private fun QuickToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onRowClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRowClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked && enabled) ElectricBlue else TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (enabled) TextPrimary else TextMuted
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ElectricBlue,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = CarbonSurfaceHigh,
                uncheckedBorderColor = CarbonBorder
            )
        )
    }
}

@Composable
private fun CompactMetricColumn(
    label: String,
    value: String,
    caption: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor
        )
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
