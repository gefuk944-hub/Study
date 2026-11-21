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
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.data.VoicePickupRange
import com.example.recording.RecordingSessionState
import com.example.system.DeviceCapabilities
import com.example.ui.components.GamingCard
import com.example.ui.components.SectionLabel
import com.example.ui.components.SegmentedAudioMeter
import com.example.ui.components.SettingToggleRow
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Clean Top Header + Presets
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Audio Studio",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = onSaveCustomPreset,
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.testTag("save_custom_audio_preset_button")
                            ) {
                                Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save")
                            }
                            TextButton(
                                onClick = onRestoreMixerDefaults,
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.testTag("restore_audio_defaults_button")
                            ) {
                                Text("Reset", color = TextSecondary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

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
                                    containerColor = CarbonSurface,
                                    labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = settings.audioPreset == preset,
                                    borderColor = CarbonBorder
                                )
                            )
                        }
                    }
                }
            }

            // 2. VOICE ISOLATION & NOISE BLOCKING (Hero Studio Section)
            item {
                Column {
                    SectionLabel("Voice Isolation & Background Noise")
                    GamingCard {
                        // Voice Pickup Range (Proximity Gate)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Voice Pickup Range",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = TextPrimary
                            )
                            Text(
                                settings.voicePickupRange.shortLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricBlue
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            VoicePickupRange.entries.forEachIndexed { idx, range ->
                                SegmentedButton(
                                    selected = settings.voicePickupRange == range,
                                    onClick = {
                                        onUpdateSettings {
                                            it.copy(voicePickupRange = range, audioPreset = AudioPreset.CUSTOM)
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = idx,
                                        count = VoicePickupRange.entries.size
                                    ),
                                    modifier = Modifier.testTag("voice_range_${range.name.lowercase()}"),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = ElectricBlue,
                                        activeContentColor = Color.White,
                                        inactiveContainerColor = CarbonSurfaceHigh,
                                        inactiveContentColor = TextSecondary,
                                        activeBorderColor = ElectricBlue,
                                        inactiveBorderColor = CarbonBorder
                                    )
                                ) {
                                    Text(range.shortLabel, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = settings.voicePickupRange.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 14.dp),
                            color = CarbonBorder.copy(alpha = 0.55f)
                        )

                        // Spectral Noise Cancellation Strength
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Background Noise Removal",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = TextPrimary
                            )
                            Text(
                                settings.noiseCancellation.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (settings.noiseCancellation == NoiseCancellationLevel.OFF) TextMuted else SignalGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            NoiseCancellationLevel.entries.forEachIndexed { idx, level ->
                                SegmentedButton(
                                    selected = settings.noiseCancellation == level,
                                    onClick = {
                                        onUpdateSettings {
                                            it.copy(noiseCancellation = level, audioPreset = AudioPreset.CUSTOM)
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = idx,
                                        count = NoiseCancellationLevel.entries.size
                                    ),
                                    modifier = Modifier.testTag("nc_level_${level.name.lowercase()}"),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = ElectricBlue,
                                        activeContentColor = Color.White,
                                        inactiveContainerColor = CarbonSurfaceHigh,
                                        inactiveContentColor = TextSecondary,
                                        activeBorderColor = ElectricBlue,
                                        inactiveBorderColor = CarbonBorder
                                    )
                                ) {
                                    val shortText = if (level == NoiseCancellationLevel.MEDIUM) "Med" else level.label
                                    Text(shortText, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Real-time FFT spectral filter removes fan, AC, and room noise even while you speak (mic only).",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = CarbonBorder.copy(alpha = 0.55f)
                        )

                        // Clear Voice EQ, Wind Filter, Clipping Limiter
                        SettingToggleRow(
                            title = "Clear Voice EQ",
                            subtitle = "Removes heavy/boomy low tones and keeps your speech crisp and natural",
                            checked = settings.voiceEnhancementEnabled,
                            onCheckedChange = { checked ->
                                onUpdateSettings { it.copy(voiceEnhancementEnabled = checked, audioPreset = AudioPreset.CUSTOM) }
                            },
                            testTag = "voice_enhancement_switch"
                        )

                        SettingToggleRow(
                            title = "Wind & Breath Filter",
                            subtitle = "Blocks strong fan airflow and close mic breath pops",
                            checked = settings.windReductionEnabled,
                            onCheckedChange = { checked ->
                                onUpdateSettings { it.copy(windReductionEnabled = checked) }
                            },
                            testTag = "wind_reduction_switch"
                        )

                        if (settings.windReductionEnabled) {
                            val cutoffHz = (130 + settings.windReductionIntensity * 100).roundToInt()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Filter Strength", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                                Text("${cutoffHz} Hz high-pass", style = MaterialTheme.typography.labelMedium, color = ElectricBlue)
                            }
                            Slider(
                                value = settings.windReductionIntensity,
                                onValueChange = { intensity ->
                                    onUpdateSettings { it.copy(windReductionIntensity = intensity) }
                                },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(thumbColor = ElectricBlue, activeTrackColor = ElectricBlue),
                                modifier = Modifier.testTag("wind_intensity_slider")
                            )
                        }

                        SettingToggleRow(
                            title = "Clipping Protection",
                            subtitle = "Prevents distortion when shouting during intense moments",
                            checked = settings.clippingProtection,
                            onCheckedChange = { checked ->
                                onUpdateSettings { it.copy(clippingProtection = checked) }
                            }
                        )
                    }
                }
            }

            // 3. MY MICROPHONE + 5-SECOND VOICE TEST
            item {
                Column {
                    SectionLabel("Microphone")
                    MixerChannelCard(
                        title = "My Voice",
                        subtitle = "48 kHz • ${settings.micSource.label}",
                        icon = Icons.Default.Mic,
                        enabled = settings.micEnabled,
                        muted = settings.micMuted,
                        volume = settings.micVolume,
                        liveLevel = liveMicLevel,
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
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mic Gain", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                            Text(
                                "${if (settings.micGainDb >= 0) "+" else ""}${"%.1f".format(settings.micGainDb)} dB",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                        }
                        Slider(
                            value = settings.micGainDb.coerceIn(-6f, 12f),
                            onValueChange = { db ->
                                onUpdateSettings { it.copy(micGainDb = db, audioPreset = AudioPreset.CUSTOM) }
                            },
                            valueRange = -6f..12f,
                            enabled = settings.micEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricBlue,
                                activeTrackColor = ElectricBlue,
                                inactiveTrackColor = CarbonSurfaceHigh
                            ),
                            modifier = Modifier.testTag("mic_gain_slider")
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = CarbonBorder.copy(alpha = 0.55f)
                        )

                        Text(
                            "Test Your Voice",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary
                        )
                        Text(
                            "Record a 5-second clip and listen back to check voice clarity and noise blocking.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (!permissions.hasMicrophone) {
                            Button(
                                onClick = onRequestMicPermission,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                modifier = Modifier.testTag("audio_studio_grant_mic_button")
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Allow Microphone")
                            }
                        } else {
                            if (micTestState.phase == MicTestPhase.RECORDING_SAMPLE || micTestState.phase == MicTestPhase.PLAYING_SAMPLE) {
                                LinearProgressIndicator(
                                    progress = { micTestState.sampleProgress.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp),
                                    color = if (micTestState.phase == MicTestPhase.RECORDING_SAMPLE) RecordCrimson else ElectricBlue,
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
                                        "Blocked: ${micTestState.gainReductionDb.roundToInt()} dB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricBlue
                                    )
                                    Text(
                                        if (micTestState.clippingProtected) "Limiter" else "Clean",
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
                                        if (micTestState.phase == MicTestPhase.LIVE_MONITORING) ElectricBlue else CarbonBorder
                                    )
                                ) {
                                    Icon(
                                        if (micTestState.phase == MicTestPhase.LIVE_MONITORING) Icons.Default.Stop else Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (micTestState.phase == MicTestPhase.LIVE_MONITORING) "Stop" else "Live Meter",
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
                                        containerColor = if (micTestState.phase == MicTestPhase.RECORDING_SAMPLE) RecordCrimson else ElectricBlue
                                    ),
                                    modifier = Modifier
                                        .weight(1.2f)
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
                                            MicTestPhase.RECORDING_SAMPLE -> "Recording..."
                                            MicTestPhase.PLAYING_SAMPLE -> "Stop Replay"
                                            else -> "5s Record & Play"
                                        },
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }

                            if (micTestState.hasRecordedSample && micTestState.phase == MicTestPhase.IDLE) {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = onReplayVoiceTest,
                                    border = BorderStroke(1.dp, CarbonBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("mic_replay_sample_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Replay Test Clip")
                                }
                            }

                            micTestState.errorMessage?.let { err ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(err, style = MaterialTheme.typography.bodySmall, color = RecordCrimson)
                            }
                        }
                    }
                }
            }

            // 4. GAME & TEAMMATES AUDIO (MERGED UNIFIED CHANNEL)
            item {
                Column {
                    SectionLabel("Game & Teammates")
                    MixerChannelCard(
                        title = "Game & Teammates Audio",
                        subtitle = if (capabilities.supportsInternalAudioCapture) {
                            "Records game sound, footsteps & squad chat together"
                        } else {
                            "Requires Android 10 or newer"
                        },
                        icon = Icons.Default.SportsEsports,
                        enabled = settings.gameAudioEnabled && capabilities.supportsInternalAudioCapture,
                        muted = settings.gameAudioMuted,
                        volume = settings.gameAudioVolume,
                        liveLevel = if (isLiveRecording) recordingState.audioTelemetry.gameLevel else 0f,
                        onToggleEnabled = { checked ->
                            if (capabilities.supportsInternalAudioCapture) {
                                onUpdateSettings {
                                    it.copy(gameAudioEnabled = checked, teamVoiceEnabled = checked)
                                }
                            }
                        },
                        onToggleMute = {
                            onUpdateSettings {
                                val nextMute = !it.gameAudioMuted
                                it.copy(gameAudioMuted = nextMute, teamVoiceMuted = nextMute)
                            }
                        },
                        onVolumeChange = { vol ->
                            onUpdateSettings {
                                it.copy(
                                    gameAudioVolume = vol,
                                    teamVoiceVolume = vol,
                                    audioPreset = AudioPreset.CUSTOM
                                )
                            }
                        },
                        sliderTestTag = "game_audio_volume_slider",
                        muteTestTag = "game_audio_mute_button"
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = CarbonBorder.copy(alpha = 0.55f)
                        )
                        SettingToggleRow(
                            title = "Original Stereo Quality",
                            subtitle = "Keeps game and squad audio untouched with zero noise filtering",
                            checked = settings.gameOriginalAudioMode,
                            onCheckedChange = { checked ->
                                onUpdateSettings { it.copy(gameOriginalAudioMode = checked) }
                            },
                            enabled = capabilities.supportsInternalAudioCapture
                        )
                        SettingToggleRow(
                            title = "Balance Game While Speaking",
                            subtitle = "Gently lowers loud game effects slightly while you talk",
                            checked = settings.smartVoiceBalance,
                            onCheckedChange = { checked ->
                                onUpdateSettings { it.copy(smartVoiceBalance = checked) }
                            },
                            enabled = capabilities.supportsInternalAudioCapture
                        )

                        if (isLiveRecording && recordingState.audioTelemetry.teamVoiceNote.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CarbonSurfaceHigh
                            ) {
                                Text(
                                    text = recordingState.audioTelemetry.teamVoiceNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(14.dp)) }
        }
    }
}

@Composable
private fun MixerChannelCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    muted: Boolean,
    volume: Float,
    liveLevel: Float,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    sliderTestTag: String,
    muteTestTag: String,
    extraContent: @Composable () -> Unit = {}
) {
    GamingCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled && !muted) ElectricBlue else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (enabled) TextPrimary else TextMuted
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggleEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ElectricBlue,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = CarbonSurfaceHigh,
                    uncheckedBorderColor = CarbonBorder
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        SegmentedAudioMeter(
            level = liveLevel,
            muted = !enabled || muted
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleMute,
                enabled = enabled,
                modifier = Modifier
                    .size(44.dp)
                    .testTag(muteTestTag)
            ) {
                Icon(
                    imageVector = if (muted || !enabled) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (muted) "Unmute $title" else "Mute $title",
                    tint = if (muted || !enabled) RecordCrimson else TextSecondary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Volume",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (enabled) TextSecondary else TextMuted
                    )
                    Text(
                        "${(volume * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (enabled && !muted) TextPrimary else TextMuted
                    )
                }
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    enabled = enabled,
                    valueRange = 0f..1.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricBlue,
                        activeTrackColor = ElectricBlue,
                        inactiveTrackColor = CarbonSurfaceHigh
                    ),
                    modifier = Modifier.testTag(sliderTestTag)
                )
            }
        }

        extraContent()
    }
}
