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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NoiseAware
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.MicTestPhase
import com.example.audio.MicTestUiState
import com.example.data.AudioPreset
import com.example.data.NoiseCancellationLevel
import com.example.data.RecorderSettings
import com.example.recording.RecordingSessionState
import com.example.system.DeviceCapabilities
import com.example.ui.components.GamingCard
import com.example.ui.components.GradientAccentDivider
import com.example.ui.components.SectionHeader
import com.example.ui.components.SegmentedAudioMeter
import com.example.ui.components.SettingToggleRow
import com.example.ui.components.StatusPill
import com.example.ui.theme.CarbonBorder
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
import kotlin.math.roundToInt

@Composable
fun AudioStudioScreen(
    settings: RecorderSettings,
    recordingState: RecordingSessionState,
    micTestState: MicTestUiState,
    capabilities: DeviceCapabilities,
    permissions: PermissionSnapshot,
    onApplyPreset: (AudioPreset) -> Unit,
    onSaveCustomPreset: () -> Unit,
    onRestoreMixerDefaults: () -> Unit,
    onUpdateSettings: ((RecorderSettings) -> RecorderSettings) -> Unit,
    onRequestMicPermission: () -> Unit,
    onToggleLiveMicMonitor: () -> Unit,
    onStartVoiceTest: () -> Unit,
    onReplayVoiceTest: () -> Unit,
    onStopMicTest: () -> Unit
) {
    val isLiveRecording = recordingState.isActiveOrPaused
    val liveMicLevel = when {
        micTestState.phase != MicTestPhase.IDLE -> micTestState.outputLevel
        isLiveRecording -> recordingState.audioTelemetry.micLevel
        else -> 0f
    }

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
            // Header & Presets
            item {
                Column {
                    Text(
                        "Audio Studio",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        "3-Source Synchronized Mixer • Mic-Only DSP Processing",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AudioPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = settings.audioPreset == preset,
                                onClick = { onApplyPreset(preset) },
                                label = { Text(preset.label) },
                                modifier = Modifier.testTag("audio_preset_${preset.name.lowercase()}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue,
                                    selectedLabelColor = Color.White,
                                    containerColor = CarbonSurfaceHigh,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onSaveCustomPreset,
                            border = BorderStroke(1.dp, NeonViolet.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("save_custom_audio_preset_button")
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonViolet)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Custom", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                        }
                        OutlinedButton(
                            onClick = onRestoreMixerDefaults,
                            border = BorderStroke(1.dp, CarbonBorder),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("restore_audio_defaults_button")
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextSecondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Mixer", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                        }
                    }
                }
            }

            // CHANNEL 1: GAME AUDIO
            item {
                MixerChannelCard(
                    title = "1. Game Audio",
                    subtitle = if (capabilities.supportsInternalAudioCapture)
                        "Internal playback capture • Footsteps, gunshots & OST preserved"
                    else
                        "Requires Android 10 (API 29+) for internal game audio capture",
                    icon = Icons.Default.SportsEsports,
                    accent = ElectricBlue,
                    enabled = settings.gameAudioEnabled && capabilities.supportsInternalAudioCapture,
                    muted = settings.gameAudioMuted,
                    volume = settings.gameAudioVolume,
                    liveLevel = if (isLiveRecording) recordingState.audioTelemetry.gameLevel else 0f,
                    statusLabel = when {
                        !capabilities.supportsInternalAudioCapture -> "UNSUPPORTED"
                        !settings.gameAudioEnabled -> "OFF"
                        settings.gameAudioMuted -> "MUTED"
                        isLiveRecording -> recordingState.audioTelemetry.gameStatus.label.uppercase()
                        else -> "ORIGINAL HQ"
                    },
                    onToggleEnabled = { checked ->
                        if (capabilities.supportsInternalAudioCapture) {
                            onUpdateSettings { it.copy(gameAudioEnabled = checked) }
                        }
                    },
                    onToggleMute = {
                        onUpdateSettings { it.copy(gameAudioMuted = !it.gameAudioMuted) }
                    },
                    onVolumeChange = { vol ->
                        onUpdateSettings { it.copy(gameAudioVolume = vol, audioPreset = AudioPreset.CUSTOM) }
                    },
                    sliderTestTag = "game_audio_volume_slider",
                    muteTestTag = "game_audio_mute_button"
                ) {
                    Spacer(modifier = Modifier.height(6.dp))
                    SettingToggleRow(
                        title = "Original Audio Quality Mode",
                        subtitle = "Zero noise cancellation on game sounds to preserve footsteps & spatial cues",
                        checked = settings.gameOriginalAudioMode,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(gameOriginalAudioMode = checked) }
                        },
                        enabled = capabilities.supportsInternalAudioCapture,
                        accentColor = ElectricBlue
                    )
                }
            }

            // CHANNEL 2: MY MICROPHONE + REAL VOICE TEST
            item {
                MixerChannelCard(
                    title = "2. My Microphone",
                    subtitle = "High-clarity player voice mixed into video • Source: ${settings.micSource.label}",
                    icon = Icons.Default.Mic,
                    accent = NeonViolet,
                    enabled = settings.micEnabled,
                    muted = settings.micMuted,
                    volume = settings.micVolume,
                    liveLevel = liveMicLevel,
                    statusLabel = when {
                        !permissions.hasMicrophone -> "GRANT MIC"
                        !settings.micEnabled -> "OFF"
                        settings.micMuted -> "MUTED"
                        micTestState.phase == MicTestPhase.RECORDING_SAMPLE -> "REC TEST"
                        micTestState.phase == MicTestPhase.PLAYING_SAMPLE -> "PLAYING"
                        micTestState.phase == MicTestPhase.LIVE_MONITORING -> "${micTestState.outputDb.roundToInt()} dBFS"
                        isLiveRecording -> recordingState.audioTelemetry.micStatus.label.uppercase()
                        else -> "READY"
                    },
                    onToggleEnabled = { checked ->
                        onUpdateSettings { it.copy(micEnabled = checked) }
                    },
                    onToggleMute = {
                        onUpdateSettings { it.copy(micMuted = !it.micMuted) }
                    },
                    onVolumeChange = { vol ->
                        onUpdateSettings { it.copy(micVolume = vol, audioPreset = AudioPreset.CUSTOM) }
                    },
                    sliderTestTag = "mic_volume_slider",
                    muteTestTag = "mic_mute_button"
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    // Mic Gain Boost Slider (-6 dB to +15 dB)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Microphone Gain", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(
                            "${if (settings.micGainDb >= 0) "+" else ""}${"%.1f".format(settings.micGainDb)} dB",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonViolet
                        )
                    }
                    Slider(
                        value = settings.micGainDb,
                        onValueChange = { db ->
                            onUpdateSettings { it.copy(micGainDb = db, audioPreset = AudioPreset.CUSTOM) }
                        },
                        valueRange = -6f..15f,
                        colors = SliderDefaults.colors(thumbColor = NeonViolet, activeTrackColor = NeonViolet),
                        modifier = Modifier.testTag("mic_gain_slider")
                    )

                    GradientAccentDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Real Live Mic Monitor & 5-Second Voice Test
                    Text(
                        "Live Voice Test (Pre-Recording)",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Text(
                        "Test your actual microphone through Noise Cancellation, Wind Filter, and Voice Enhancement before gaming.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (!permissions.hasMicrophone) {
                        Button(
                            onClick = onRequestMicPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                            modifier = Modifier.testTag("audio_studio_grant_mic_button")
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant Microphone Permission")
                        }
                    } else {
                        if (micTestState.phase == MicTestPhase.RECORDING_SAMPLE || micTestState.phase == MicTestPhase.PLAYING_SAMPLE) {
                            LinearProgressIndicator(
                                progress = { micTestState.sampleProgress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = if (micTestState.phase == MicTestPhase.RECORDING_SAMPLE) RecordCrimson else CyberCyan,
                                trackColor = CarbonSurfaceHigh
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (micTestState.phase != MicTestPhase.IDLE) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Noise Floor: ${micTestState.noiseFloorDb.roundToInt()} dB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    "NR Attenuation: ${micTestState.gainReductionDb.roundToInt()} dB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyberCyan
                                )
                                Text(
                                    if (micTestState.clippingProtected) "LIMITER ACTIVE" else "CLEAN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (micTestState.clippingProtected) ThermalAmber else SignalGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onToggleLiveMicMonitor,
                                enabled = !isLiveRecording && micTestState.phase != MicTestPhase.RECORDING_SAMPLE,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mic_live_monitor_button"),
                                border = BorderStroke(
                                    1.dp,
                                    if (micTestState.phase == MicTestPhase.LIVE_MONITORING) CyberCyan else CarbonBorder
                                )
                            ) {
                                Icon(
                                    if (micTestState.phase == MicTestPhase.LIVE_MONITORING) Icons.Default.Stop else Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = CyberCyan
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (micTestState.phase == MicTestPhase.LIVE_MONITORING) "Stop Meter" else "Live Meter",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            Button(
                                onClick = {
                                    if (micTestState.phase == MicTestPhase.RECORDING_SAMPLE || micTestState.phase == MicTestPhase.PLAYING_SAMPLE) {
                                        onStopMicTest()
                                    } else {
                                        onStartVoiceTest()
                                    }
                                },
                                enabled = !isLiveRecording,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (micTestState.phase == MicTestPhase.RECORDING_SAMPLE) RecordCrimson else NeonViolet
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mic_voice_test_button")
                            ) {
                                Icon(
                                    if (micTestState.phase == MicTestPhase.RECORDING_SAMPLE) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    when (micTestState.phase) {
                                        MicTestPhase.RECORDING_SAMPLE -> "Recording 5s..."
                                        MicTestPhase.PLAYING_SAMPLE -> "Stop Replay"
                                        else -> "5s Voice Test"
                                    },
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        if (micTestState.hasRecordedSample && micTestState.phase == MicTestPhase.IDLE) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = onReplayVoiceTest,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("mic_replay_sample_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Replay Processed Voice Test")
                            }
                        }

                        micTestState.errorMessage?.let { err ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(err, style = MaterialTheme.typography.bodySmall, color = RecordCrimson)
                        }
                    }
                }
            }

            // CHANNEL 3: TEAMMATE VOICE CHAT
            item {
                MixerChannelCard(
                    title = "3. Teammate Voice Chat",
                    subtitle = if (capabilities.supportsInternalAudioCapture)
                        "Captures USAGE_VOICE_COMMUNICATION alongside game audio when app/OS permits"
                    else
                        "Requires Android 10+ (AudioPlaybackCapture API)",
                    icon = Icons.Default.Groups,
                    accent = CyberCyan,
                    enabled = settings.teamVoiceEnabled && capabilities.supportsInternalAudioCapture,
                    muted = settings.teamVoiceMuted,
                    volume = settings.teamVoiceVolume,
                    liveLevel = if (isLiveRecording) recordingState.audioTelemetry.teamLevel else 0f,
                    statusLabel = when {
                        !capabilities.supportsInternalAudioCapture -> "ANDROID 10+"
                        !settings.teamVoiceEnabled -> "OFF"
                        settings.teamVoiceMuted -> "MUTED"
                        isLiveRecording -> recordingState.audioTelemetry.teamStatus.label.uppercase()
                        else -> "ACTIVE READY"
                    },
                    onToggleEnabled = { checked ->
                        if (capabilities.supportsInternalAudioCapture) {
                            onUpdateSettings { it.copy(teamVoiceEnabled = checked) }
                        }
                    },
                    onToggleMute = {
                        onUpdateSettings { it.copy(teamVoiceMuted = !it.teamVoiceMuted) }
                    },
                    onVolumeChange = { vol ->
                        onUpdateSettings { it.copy(teamVoiceVolume = vol, audioPreset = AudioPreset.CUSTOM) }
                    },
                    sliderTestTag = "team_voice_volume_slider",
                    muteTestTag = "team_voice_mute_button"
                ) {
                    Spacer(modifier = Modifier.height(6.dp))
                    SettingToggleRow(
                        title = "Smart Voice Balance (Auto-Ducking)",
                        subtitle = "Gently lowers game explosions (~3.5 dB) when you or teammates speak",
                        checked = settings.smartVoiceBalance,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(smartVoiceBalance = checked) }
                        },
                        accentColor = CyberCyan
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CarbonSurfaceHigh,
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(16.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isLiveRecording && recordingState.audioTelemetry.teamVoiceNote.isNotBlank()) {
                                    recordingState.audioTelemetry.teamVoiceNote
                                } else {
                                    "Android Transparency: Zero Recorder captures squad voice chat via Android AudioPlaybackCapture. If a specific game blocks internal voice capture (ALLOW_CAPTURE_BY_NONE or Bluetooth SCO routing), Zero Recorder detects it honestly—switching game chat to speakerphone or Discord with media routing is the supported alternative."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // SECTION 4: BACKGROUND NOISE CANCELLATION (MIC-ONLY)
            item {
                GamingCard(accentColor = ElectricBlue) {
                    SectionHeader(
                        title = "Background Noise Cancellation",
                        subtitle = "Mic-only adaptive noise gate + hardware NS (never affects game or team audio)",
                        icon = Icons.Default.NoiseAware,
                        accent = ElectricBlue,
                        trailing = {
                            StatusPill(
                                text = settings.noiseCancellation.label.uppercase(),
                                color = if (settings.noiseCancellation == NoiseCancellationLevel.OFF) TextMuted else SignalGreen
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NoiseCancellationLevel.entries.forEach { level ->
                            FilterChip(
                                selected = settings.noiseCancellation == level,
                                onClick = {
                                    onUpdateSettings {
                                        it.copy(noiseCancellation = level, audioPreset = AudioPreset.CUSTOM)
                                    }
                                },
                                label = { Text(level.label) },
                                modifier = Modifier.testTag("nc_level_${level.name.lowercase()}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue,
                                    selectedLabelColor = Color.White,
                                    containerColor = CarbonSurfaceHigh,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = buildString {
                            append("Targets steady fan, AC, room hum, and low keyboard noise with smooth knee curves so your voice stays natural. ")
                            append(
                                if (capabilities.supportsHardwareNoiseSuppressor)
                                    "Hardware Android NoiseSuppressor: Available."
                                else
                                    "Hardware Android NoiseSuppressor unavailable — using Zero on-device adaptive DSP."
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // SECTION 5: DEDICATED WIND NOISE REDUCTION (INDEPENDENT)
            item {
                GamingCard(accentColor = CyberCyan) {
                    SectionHeader(
                        title = "Wind Noise Reduction",
                        subtitle = "Independent 2nd-order Butterworth high-pass filter & gust damper (Mic-only)",
                        icon = Icons.Default.Air,
                        accent = CyberCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SettingToggleRow(
                        title = "Enable Wind Reduction",
                        subtitle = "Filters low-frequency wind rumble & breath pops (85 Hz – 240 Hz) while keeping vocals clear",
                        checked = settings.windReductionEnabled,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(windReductionEnabled = checked) }
                        },
                        accentColor = CyberCyan,
                        testTag = "wind_reduction_switch"
                    )
                    if (settings.windReductionEnabled) {
                        val cutoffHz = (85 + settings.windReductionIntensity * 155).roundToInt()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Wind Filter Intensity", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(
                                "${(settings.windReductionIntensity * 100).roundToInt()}% (${cutoffHz} Hz HPF)",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyberCyan
                            )
                        }
                        Slider(
                            value = settings.windReductionIntensity,
                            onValueChange = { intensity ->
                                onUpdateSettings { it.copy(windReductionIntensity = intensity) }
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(thumbColor = CyberCyan, activeTrackColor = CyberCyan),
                            modifier = Modifier.testTag("wind_intensity_slider")
                        )
                    }
                    Text(
                        "Software DSP high-pass filtering runs offline on all Android microphones (hardware wind microphones vary by device).",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            // SECTION 6: ON-DEVICE DSP VOICE ENHANCEMENT (HONEST OFFLINE PROCESSING)
            item {
                GamingCard(accentColor = NeonViolet) {
                    SectionHeader(
                        title = "On-Device DSP Voice Enhancement",
                        subtitle = "Offline studio clarity chain • Zero cloud upload or fake AI claims",
                        icon = Icons.Default.AutoFixHigh,
                        accent = NeonViolet
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SettingToggleRow(
                        title = "Voice Presence & Clarity EQ",
                        subtitle = "Real 3.2 kHz presence boost + RMS speech leveling for crisp callouts",
                        checked = settings.voiceEnhancementEnabled,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(voiceEnhancementEnabled = checked, audioPreset = AudioPreset.CUSTOM) }
                        },
                        accentColor = NeonViolet,
                        testTag = "voice_enhancement_switch"
                    )
                    SettingToggleRow(
                        title = "Automatic Gain Control (AGC)",
                        subtitle = if (capabilities.supportsHardwareAgc)
                            "Hardware + DSP automatic mic leveling to prevent quiet whispers"
                        else
                            "On-device DSP automatic leveler (hardware AGC unavailable on this device)",
                        checked = settings.autoGainControl,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(autoGainControl = checked) }
                        },
                        accentColor = NeonViolet
                    )
                    SettingToggleRow(
                        title = "Acoustic Echo Reduction",
                        subtitle = if (capabilities.supportsHardwareEchoCanceler)
                            "Uses Android AcousticEchoCanceler to reduce speaker bleed into mic"
                        else
                            "Device lacks hardware AcousticEchoCanceler; use headset for best echo isolation",
                        checked = settings.echoReduction,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(echoReduction = checked) }
                        },
                        enabled = capabilities.supportsHardwareEchoCanceler,
                        accentColor = NeonViolet
                    )
                    SettingToggleRow(
                        title = "Soft-Knee Clipping Protection",
                        subtitle = "Master peak limiter prevents distortion during loud shouts & explosions",
                        checked = settings.clippingProtection,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(clippingProtection = checked) }
                        },
                        accentColor = SignalGreen
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun MixerChannelCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    enabled: Boolean,
    muted: Boolean,
    volume: Float,
    liveLevel: Float,
    statusLabel: String,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    sliderTestTag: String,
    muteTestTag: String,
    extraContent: @Composable () -> Unit = {}
) {
    GamingCard(accentColor = if (enabled && !muted) accent else CarbonBorder) {
        SectionHeader(
            title = title,
            subtitle = subtitle,
            icon = icon,
            accent = accent,
            trailing = {
                StatusPill(
                    text = statusLabel,
                    color = when {
                        !enabled -> TextMuted
                        muted -> RecordCrimson
                        else -> accent
                    }
                )
            }
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Real-time Segmented Audio Level Meter
        SegmentedAudioMeter(
            level = liveLevel,
            activeColor = accent,
            muted = !enabled || muted
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleMute,
                enabled = enabled,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(muteTestTag)
            ) {
                Icon(
                    imageVector = if (muted || !enabled) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (muted) "Unmute $title" else "Mute $title",
                    tint = if (muted || !enabled) RecordCrimson else accent
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Channel Volume",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (enabled) TextPrimary else TextMuted
                    )
                    Text(
                        "${(volume * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (enabled && !muted) accent else TextMuted
                    )
                }
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    enabled = enabled,
                    valueRange = 0f..1.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = CarbonSurfaceHigh
                    ),
                    modifier = Modifier.testTag(sliderTestTag)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))
            androidx.compose.material3.Switch(
                checked = enabled,
                onCheckedChange = onToggleEnabled
            )
        }

        extraContent()
    }
}
