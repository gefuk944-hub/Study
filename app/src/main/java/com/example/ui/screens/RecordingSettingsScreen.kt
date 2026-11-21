package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.OrientationMode
import com.example.data.QualityPreset
import com.example.data.RecorderSettings
import com.example.data.VideoCodec
import com.example.data.VideoFps
import com.example.data.VideoResolution
import com.example.system.DeviceCapabilities
import com.example.system.SystemTelemetry
import com.example.system.VideoQualityTestResult
import com.example.ui.components.GamingCard
import com.example.ui.components.SectionLabel
import com.example.ui.components.SettingToggleRow
import com.example.ui.components.SettingsNavRow
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CarbonSurfaceHigh
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThermalAmber
import kotlin.math.roundToInt

@Composable
fun RecordingSettingsScreen(
    settings: RecorderSettings,
    capabilities: DeviceCapabilities,
    telemetry: SystemTelemetry,
    qualityTestRunning: Boolean,
    qualityTestResult: VideoQualityTestResult?,
    onSelectResolution: (VideoResolution) -> Unit,
    onSelectFps: (VideoFps) -> Unit,
    onSelectCodec: (VideoCodec) -> Unit,
    onApplyPreset: (QualityPreset) -> Unit,
    onApplyRecommended: () -> Unit,
    onSetManualBitrate: (Int) -> Unit,
    onUpdateSettings: ((RecorderSettings) -> RecorderSettings) -> Unit,
    onRunQualityTest: () -> Unit,
    onOpenPerformanceMonitor: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onOpenPermissions: () -> Unit
) {
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
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    TextButton(
                        onClick = onApplyRecommended,
                        modifier = Modifier.testTag("apply_recommended_quality_button")
                    ) {
                        Text("Auto-Optimal", color = ElectricBlue)
                    }
                }
            }

            // 1. Quality Preset
            item {
                Column {
                    SectionLabel("Quality Preset")
                    GamingCard {
                        val presets = listOf(
                            QualityPreset.LOW,
                            QualityPreset.MEDIUM,
                            QualityPreset.HIGH,
                            QualityPreset.ULTRA
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            presets.forEachIndexed { idx, preset ->
                                val supported = capabilities.isResolutionSupported(preset.resolution)
                                SegmentedButton(
                                    selected = settings.qualityPreset == preset,
                                    enabled = supported,
                                    onClick = { onApplyPreset(preset) },
                                    shape = SegmentedButtonDefaults.itemShape(idx, presets.size),
                                    modifier = Modifier.testTag("quality_preset_${preset.name.lowercase()}"),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = ElectricBlue,
                                        activeContentColor = Color.White,
                                        inactiveContainerColor = CarbonSurfaceHigh,
                                        inactiveContentColor = TextSecondary,
                                        activeBorderColor = ElectricBlue,
                                        inactiveBorderColor = CarbonBorder
                                    )
                                ) {
                                    Text(preset.label, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Recommended for ${"%.1f".format(capabilities.totalRamGb)} GB RAM: ${capabilities.recommendedResolution.shortLabel} @ ${capabilities.recommendedFps.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            // 2. Video Resolution, FPS, Codec & Orientation
            item {
                Column {
                    SectionLabel("Video Format")
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = CarbonSurface,
                        border = BorderStroke(1.dp, CarbonBorder.copy(alpha = 0.72f))
                    ) {
                        Column {
                            VideoResolution.entries.forEachIndexed { idx, res ->
                                val support = capabilities.resolutionSupport[res]
                                val isSupported = support?.isSupported == true
                                val isSelected = settings.resolution == res
                                if (idx > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = CarbonBorder.copy(alpha = 0.55f)
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = isSupported) { onSelectResolution(res) }
                                        .padding(horizontal = 16.dp, vertical = 13.dp)
                                        .testTag("resolution_option_${res.shortLabel}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = res.displayName,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            ),
                                            color = if (isSupported) TextPrimary else TextMuted
                                        )
                                        Text(
                                            text = if (isSupported) {
                                                val (w, h) = res.alignedDimensions(isLandscape = true)
                                                "${w}×${h} • ~${res.recommendedBitrateMbps} Mbps"
                                            } else {
                                                support?.reason ?: "Not supported by device encoder"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSupported) TextSecondary else TextMuted
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = ElectricBlue,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    GamingCard {
                        // Frame Rate
                        Text("Frame Rate (FPS)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            VideoFps.entries.forEachIndexed { idx, fps ->
                                val supported = capabilities.isFpsSupported(settings.resolution, fps)
                                SegmentedButton(
                                    selected = settings.fps == fps,
                                    enabled = supported,
                                    onClick = { onSelectFps(fps) },
                                    shape = SegmentedButtonDefaults.itemShape(idx, VideoFps.entries.size),
                                    modifier = Modifier.testTag("fps_option_${fps.value}"),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = ElectricBlue,
                                        activeContentColor = Color.White,
                                        inactiveContainerColor = CarbonSurfaceHigh,
                                        inactiveContentColor = TextSecondary,
                                        activeBorderColor = ElectricBlue,
                                        inactiveBorderColor = CarbonBorder
                                    )
                                ) {
                                    Text("${fps.value}")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Orientation
                        Text("Orientation", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            OrientationMode.entries.forEachIndexed { idx, mode ->
                                SegmentedButton(
                                    selected = settings.orientationMode == mode,
                                    onClick = { onUpdateSettings { it.copy(orientationMode = mode) } },
                                    shape = SegmentedButtonDefaults.itemShape(idx, OrientationMode.entries.size),
                                    modifier = Modifier.testTag("orientation_${mode.name.lowercase()}"),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = ElectricBlue,
                                        activeContentColor = Color.White,
                                        inactiveContainerColor = CarbonSurfaceHigh,
                                        inactiveContentColor = TextSecondary,
                                        activeBorderColor = ElectricBlue,
                                        inactiveBorderColor = CarbonBorder
                                    )
                                ) {
                                    Text(mode.label)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Codec
                        Text("Video Codec (MP4)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            VideoCodec.entries.forEachIndexed { idx, codec ->
                                val isSupported = capabilities.codecs[codec]?.isSupported == true
                                SegmentedButton(
                                    selected = settings.codec == codec,
                                    enabled = isSupported,
                                    onClick = { onSelectCodec(codec) },
                                    shape = SegmentedButtonDefaults.itemShape(idx, VideoCodec.entries.size),
                                    modifier = Modifier.testTag("codec_option_${codec.name.lowercase()}"),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = ElectricBlue,
                                        activeContentColor = Color.White,
                                        inactiveContainerColor = CarbonSurfaceHigh,
                                        inactiveContentColor = TextSecondary,
                                        activeBorderColor = ElectricBlue,
                                        inactiveBorderColor = CarbonBorder
                                    )
                                ) {
                                    Text(codec.displayName)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Bitrate, Stability & Hardware Test
            item {
                Column {
                    SectionLabel("Bitrate & Stability")
                    GamingCard {
                        SettingToggleRow(
                            title = "Auto Bitrate",
                            subtitle = "Matches resolution, FPS, and codec automatically",
                            checked = settings.autoBitrateOptimization,
                            onCheckedChange = { auto ->
                                onUpdateSettings { it.copy(autoBitrateOptimization = auto) }
                            }
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (settings.autoBitrateOptimization) "Effective Bitrate" else "Manual Bitrate",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextSecondary
                            )
                            Text(
                                "${"%.1f".format(settings.effectiveVideoBitrateMbps())} Mbps • ~${"%.0f".format(settings.estimatedMbPerMinute())} MB/min",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = TextPrimary
                            )
                        }
                        Slider(
                            value = if (settings.autoBitrateOptimization) {
                                settings.effectiveVideoBitrateMbps()
                            } else {
                                settings.bitrateMbps.toFloat()
                            },
                            onValueChange = { onSetManualBitrate(it.roundToInt()) },
                            valueRange = 2f..40f,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricBlue,
                                activeTrackColor = ElectricBlue,
                                inactiveTrackColor = CarbonSurfaceHigh
                            ),
                            modifier = Modifier.testTag("bitrate_slider")
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = CarbonBorder.copy(alpha = 0.55f)
                        )

                        SettingToggleRow(
                            title = "Adaptive Bitrate",
                            subtitle = "Lowers bitrate automatically if device overheats",
                            checked = settings.adaptiveBitrate,
                            onCheckedChange = { v -> onUpdateSettings { it.copy(adaptiveBitrate = v) } }
                        )
                        SettingToggleRow(
                            title = "Adaptive FPS",
                            subtitle = "Protects gameplay smoothness under heavy load",
                            checked = settings.adaptiveFps,
                            onCheckedChange = { v -> onUpdateSettings { it.copy(adaptiveFps = v) } }
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onRunQualityTest,
                            enabled = !qualityTestRunning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("run_video_quality_test_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                        ) {
                            if (qualityTestRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Testing Hardware Encoder...")
                            } else {
                                Text("Run Hardware Quality Test")
                            }
                        }

                        qualityTestResult?.let { result ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = (if (result.passed) SignalGreen else ThermalAmber).copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        if (result.passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (result.passed) SignalGreen else ThermalAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            "${result.encoderName} • ${result.resolutionLabel}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = TextPrimary
                                        )
                                        Text(
                                            result.summary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Native Grouped Navigation Rows (More)
            item {
                Column {
                    SectionLabel("More")
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = CarbonSurface,
                        border = BorderStroke(1.dp, CarbonBorder.copy(alpha = 0.72f))
                    ) {
                        Column {
                            SettingsNavRow(
                                icon = Icons.Default.Memory,
                                title = "Performance & Storage",
                                subtitle = "${"%.1f".format(telemetry.availableStorageGb)} GB free • ${"%.0f".format(telemetry.batteryTempCelsius)}°C",
                                onClick = onOpenPerformanceMonitor,
                                modifier = Modifier.testTag("settings_to_performance_button")
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = CarbonBorder.copy(alpha = 0.55f)
                            )
                            SettingsNavRow(
                                icon = Icons.Default.Tune,
                                title = "Floating HUD & Advanced",
                                subtitle = "Countdown ${if (settings.countdownSeconds == 0) "Off" else "${settings.countdownSeconds}s"} • Mic source",
                                onClick = onOpenAdvancedSettings,
                                modifier = Modifier.testTag("settings_to_advanced_button")
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = CarbonBorder.copy(alpha = 0.55f)
                            )
                            SettingsNavRow(
                                icon = Icons.Default.Security,
                                title = "Permissions & Privacy",
                                subtitle = "100% offline • No login required",
                                onClick = onOpenPermissions,
                                modifier = Modifier.testTag("settings_to_permissions_button")
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
