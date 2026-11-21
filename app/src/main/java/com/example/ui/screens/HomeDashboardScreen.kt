package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.RecorderSettings
import com.example.recording.RecordingPhase
import com.example.recording.RecordingSessionState
import com.example.system.DeviceCapabilities
import com.example.system.SystemTelemetry
import com.example.ui.components.GamingCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.SegmentedAudioMeter
import com.example.ui.components.StatusPill
import com.example.ui.components.TelemetryStatTile
import com.example.ui.components.formatBytesHuman
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CarbonSurfaceHigh
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonViolet
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
    videoCount: Int,
    screenshotCount: Int,
    onRequestStartRecording: () -> Unit,
    onTogglePauseResume: () -> Unit,
    onStopRecording: () -> Unit,
    onRequestScreenshot: () -> Unit,
    onToggleMicMute: () -> Unit,
    onToggleFloatingOverlay: (Boolean) -> Unit,
    onStepDownThermal: () -> Unit,
    onOpenAudioStudio: () -> Unit,
    onOpenRecordingSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Gaming Hero Cockpit Card
            item {
                HeroCockpitCard(
                    settings = settings,
                    recordingState = recordingState,
                    isRecording = isRecording,
                    isPaused = isPaused,
                    isActive = isActive,
                    onRequestStartRecording = onRequestStartRecording,
                    onTogglePauseResume = onTogglePauseResume,
                    onStopRecording = onStopRecording,
                    onRequestScreenshot = onRequestScreenshot,
                    onToggleMicMute = onToggleMicMute
                )
            }

            // Thermal Warning Banner (if device is heating up)
            item {
                AnimatedVisibility(
                    visible = telemetry.thermalHealth.isWarning || recordingState.thermalWarningMessage != null
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ThermalAmber.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, ThermalAmber.copy(alpha = 0.55f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Thermal warning",
                                tint = ThermalAmber,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Thermal Protection Alert",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ThermalAmber
                                )
                                Text(
                                    recordingState.thermalWarningMessage
                                        ?: "Device temp ${"%.1f".format(telemetry.batteryTempCelsius)}°C (${telemetry.thermalStatusLabel}). Reduce load to prevent frame drops.",
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
                                Text("Cool Down", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            // 2. Unified Studio Audio Matrix Card (Game + Teammates + Studio Mic)
            item {
                GamingCard(
                    accentColor = if (isActive) ElectricBlue else CarbonBorder,
                    modifier = Modifier.clickable(onClick = onOpenAudioStudio)
                ) {
                    SectionHeader(
                        title = "Studio Audio Matrix",
                        subtitle = "Preset: ${settings.audioPreset.label} • NC: ${settings.noiseCancellation.label}",
                        icon = Icons.Default.GraphicEq,
                        accent = CyberCyan,
                        trailing = {
                            StatusPill(
                                text = if (isActive) "MIXING LIVE" else "STUDIO",
                                color = if (isActive) SignalGreen else ElectricBlue
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    AudioMiniChannelRow(
                        label = "Game & Teammates Audio",
                        enabled = settings.gameAudioEnabled && !settings.gameAudioMuted && capabilities.supportsInternalAudioCapture,
                        volumePct = (settings.gameAudioVolume * 100).toInt(),
                        level = if (isActive) recordingState.audioTelemetry.gameLevel else 0f,
                        statusText = if (isActive) recordingState.audioTelemetry.gameStatus.label
                        else if (!capabilities.supportsInternalAudioCapture) "Android 10+" else "Game + Team HQ",
                        accent = ElectricBlue
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    AudioMiniChannelRow(
                        label = "My Microphone (Studio)",
                        enabled = settings.micEnabled && !settings.micMuted && permissions.hasMicrophone,
                        volumePct = (settings.micVolume * 100).toInt(),
                        level = if (isActive) recordingState.audioTelemetry.micLevel else 0f,
                        statusText = when {
                            !permissions.hasMicrophone -> "Grant Mic"
                            settings.micMuted -> "Muted"
                            isActive -> recordingState.audioTelemetry.micStatus.label
                            else -> if (settings.windReductionEnabled) "Studio + Wind" else "Studio DSP"
                        },
                        accent = NeonViolet
                    )

                    if (isActive && recordingState.audioTelemetry.teamVoiceNote.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = recordingState.audioTelemetry.teamVoiceNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            // 3. One-Hand Quick Actions: Floating Panel + Quality Profile
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onToggleFloatingOverlay(!recordingState.floatingOverlayActive) }
                            .testTag("toggle_floating_panel_card"),
                        shape = RoundedCornerShape(18.dp),
                        color = if (recordingState.floatingOverlayActive) ElectricBlue.copy(alpha = 0.18f) else CarbonSurface,
                        border = BorderStroke(
                            1.dp,
                            if (recordingState.floatingOverlayActive) ElectricBlue else CarbonBorder
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.PictureInPictureAlt,
                                    contentDescription = "Floating gameplay controls",
                                    tint = if (recordingState.floatingOverlayActive) CyberCyan else ElectricBlue
                                )
                                StatusPill(
                                    text = if (recordingState.floatingOverlayActive) "ON" else "OFF",
                                    color = if (recordingState.floatingOverlayActive) SignalGreen else TextMuted,
                                    leadingDot = false
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "Floating HUD",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                if (permissions.hasOverlay) "Control over games" else "Tap to grant overlay",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onOpenRecordingSettings)
                            .testTag("video_profile_quick_card"),
                        shape = RoundedCornerShape(18.dp),
                        color = CarbonSurface,
                        border = BorderStroke(1.dp, CarbonBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.SportsEsports,
                                    contentDescription = "Video profile",
                                    tint = NeonViolet
                                )
                                StatusPill(
                                    text = settings.codec.shortLabel,
                                    color = NeonViolet,
                                    leadingDot = false
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "${settings.resolution.shortLabel} • ${settings.fps.value} FPS",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                "${"%.1f".format(settings.effectiveVideoBitrateMbps())} Mbps • ~${"%.0f".format(settings.estimatedMbPerMinute())} MB/m",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 4. Device Telemetry & Storage Glance
            item {
                GamingCard {
                    SectionHeader(
                        title = "System & Storage",
                        subtitle = "${settings.performanceMode.label} Mode • Offline Local Storage",
                        icon = Icons.Default.Memory,
                        accent = ElectricBlue,
                        trailing = {
                            Text(
                                text = "Monitor →",
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricBlue,
                                modifier = Modifier
                                    .clickable(onClick = onOpenPerformanceMonitor)
                                    .padding(4.dp)
                                    .testTag("open_performance_monitor_link")
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "FREE SPACE",
                            value = "${"%.1f".format(telemetry.availableStorageGb)} GB",
                            subvalue = "~${settings.estimatedMaxMinutes(telemetry.availableStorageBytes)}m left",
                            accent = if (telemetry.isLowStorage) RecordCrimson else CyberCyan,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "RAM USED",
                            value = "${(telemetry.ramUsageFraction * 100).toInt()}%",
                            subvalue = "${telemetry.availableRamMb} MB free",
                            accent = ElectricBlue,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "THERMAL",
                            value = "${"%.0f".format(telemetry.batteryTempCelsius)}°C",
                            subvalue = telemetry.thermalHealth.label,
                            accent = if (telemetry.thermalHealth.isWarning) ThermalAmber else SignalGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 5. Quick Access Command Grid (Library, Performance, Advanced, Privacy)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DashboardNavTile(
                            title = "Gallery & Clips",
                            subtitle = "$videoCount videos • $screenshotCount shots",
                            icon = Icons.Default.VideoLibrary,
                            accent = ElectricBlue,
                            onClick = onOpenLibrary,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("nav_tile_library")
                        )
                        DashboardNavTile(
                            title = "Performance",
                            subtitle = "4GB RAM & Thermal",
                            icon = Icons.Default.Memory,
                            accent = CyberCyan,
                            onClick = onOpenPerformanceMonitor,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("nav_tile_performance")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DashboardNavTile(
                            title = "Advanced",
                            subtitle = "Timer, HUD & Auto-stop",
                            icon = Icons.Default.Tune,
                            accent = NeonViolet,
                            onClick = onOpenAdvancedSettings,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("nav_tile_advanced")
                        )
                        DashboardNavTile(
                            title = "Privacy & Perms",
                            subtitle = "100% Local • No Login",
                            icon = Icons.Default.Security,
                            accent = SignalGreen,
                            onClick = onOpenPermissions,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("nav_tile_permissions")
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun HeroCockpitCard(
    settings: RecorderSettings,
    recordingState: RecordingSessionState,
    isRecording: Boolean,
    isPaused: Boolean,
    isActive: Boolean,
    onRequestStartRecording: () -> Unit,
    onTogglePauseResume: () -> Unit,
    onStopRecording: () -> Unit,
    onRequestScreenshot: () -> Unit,
    onToggleMicMute: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = CarbonSurface,
        border = BorderStroke(
            width = 1.2.dp,
            brush = Brush.linearGradient(
                if (isRecording) listOf(RecordCrimson, NeonViolet)
                else listOf(ElectricBlue, NeonViolet)
            )
        ),
        tonalElevation = 4.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_gaming_hero_bg_1795182511168),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.42f,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ObsidianVoid.copy(alpha = 0.50f),
                                CarbonSurface.copy(alpha = 0.88f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ZERO RECORDER",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                letterSpacing = 1.8.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = TextPrimary
                        )
                        Text(
                            text = if (isActive) {
                                "${recordingState.resolutionLabel} • ${recordingState.activeFps} FPS • ${"%.1f".format(recordingState.activeBitrateMbps)} Mbps"
                            } else {
                                "Hardware Engine Ready • Offline Local Capture"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    StatusPill(
                        text = when (recordingState.phase) {
                            RecordingPhase.RECORDING -> "LIVE REC"
                            RecordingPhase.PAUSED -> "PAUSED"
                            RecordingPhase.COUNTDOWN -> "IN ${recordingState.countdownRemaining}S"
                            RecordingPhase.FINALIZING -> "SAVING"
                            RecordingPhase.IDLE -> "STANDBY"
                        },
                        color = when (recordingState.phase) {
                            RecordingPhase.RECORDING -> RecordCrimson
                            RecordingPhase.PAUSED -> ThermalAmber
                            RecordingPhase.COUNTDOWN -> CyberCyan
                            RecordingPhase.FINALIZING -> NeonViolet
                            RecordingPhase.IDLE -> SignalGreen
                        }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Live Timer & Size Strip while recording
                if (isActive || recordingState.phase == RecordingPhase.COUNTDOWN) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ObsidianVoid.copy(alpha = 0.72f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (recordingState.phase == RecordingPhase.COUNTDOWN) {
                                "STARTING IN ${recordingState.countdownRemaining}..."
                            } else {
                                recordingState.formattedTimer
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (isPaused) ThermalAmber else RecordCrimson
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = formatBytesHuman(recordingState.estimatedCurrentBytes),
                                style = MaterialTheme.typography.labelLarge,
                                color = CyberCyan
                            )
                            Text(
                                text = recordingState.captureModeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Primary One-Hand Action Bar
                if (!isActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onRequestStartRecording,
                            enabled = recordingState.phase == RecordingPhase.IDLE,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("start_recording_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricBlue,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                tint = RecordCrimson,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (recordingState.phase == RecordingPhase.FINALIZING) "Saving..." else "Start Recording",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        FilledTonalButton(
                            onClick = onRequestScreenshot,
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("take_screenshot_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = NeonViolet.copy(alpha = 0.25f),
                                contentColor = TextPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Take screenshot",
                                tint = CyberCyan
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onStopRecording,
                            modifier = Modifier
                                .weight(1.2f)
                                .height(52.dp)
                                .testTag("stop_recording_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RecordCrimson,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop", style = MaterialTheme.typography.titleMedium)
                        }

                        FilledTonalButton(
                            onClick = onTogglePauseResume,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("pause_resume_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = CarbonSurfaceHigh,
                                contentColor = TextPrimary
                            )
                        ) {
                            Icon(
                                if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isPaused) "Resume" else "Pause"
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPaused) "Resume" else "Pause")
                        }

                        OutlinedButton(
                            onClick = onToggleMicMute,
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("live_mic_mute_button"),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (recordingState.micMuted) RecordCrimson else CarbonBorder
                            )
                        ) {
                            Icon(
                                if (recordingState.micMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = if (recordingState.micMuted) "Unmute microphone" else "Mute microphone",
                                tint = if (recordingState.micMuted) RecordCrimson else CyberCyan
                            )
                        }

                        OutlinedButton(
                            onClick = onRequestScreenshot,
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("live_screenshot_button"),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            border = BorderStroke(1.dp, CarbonBorder)
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Capture screenshot",
                                tint = NeonViolet
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioMiniChannelRow(
    label: String,
    enabled: Boolean,
    volumePct: Int,
    level: Float,
    statusText: String,
    accent: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (enabled) accent else TextMuted)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) TextPrimary else TextMuted
                )
            }
            Text(
                text = "$statusText • $volumePct%",
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) TextSecondary else TextMuted
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        SegmentedAudioMeter(
            level = level,
            activeColor = accent,
            muted = !enabled
        )
    }
}

@Composable
private fun DashboardNavTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = CarbonSurface,
        border = BorderStroke(1.dp, CarbonBorder)
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
