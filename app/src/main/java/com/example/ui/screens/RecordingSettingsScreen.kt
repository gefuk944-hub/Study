package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import com.example.ui.components.SectionHeader
import com.example.ui.components.SettingToggleRow
import com.example.ui.components.StatusPill
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CarbonSurfaceHigh
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonViolet
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    Text(
                        "Recording Engine",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        "Hardware-verified MediaCodec parameters • MP4 output",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    // Quick Navigation Pills to Sub-Settings Screens
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenPerformanceMonitor,
                            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("settings_to_performance_button")
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp), tint = CyberCyan)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Performance Monitor")
                        }
                        OutlinedButton(
                            onClick = onOpenAdvancedSettings,
                            border = BorderStroke(1.dp, NeonViolet.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("settings_to_advanced_button")
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonViolet)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Advanced Settings")
                        }
                        OutlinedButton(
                            onClick = onOpenPermissions,
                            border = BorderStroke(1.dp, SignalGreen.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("settings_to_permissions_button")
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp), tint = SignalGreen)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Permissions & Privacy")
                        }
                    }
                }
            }

            // Quality Presets + Device Recommendation
            item {
                GamingCard(accentColor = ElectricBlue) {
                    SectionHeader(
                        title = "Quality Presets",
                        subtitle = "Recommended for this device (${"%.1f".format(capabilities.totalRamGb)} GB RAM): ${capabilities.recommendedResolution.shortLabel} @ ${capabilities.recommendedFps.label}",
                        icon = Icons.Default.HighQuality,
                        accent = ElectricBlue
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            QualityPreset.LOW,
                            QualityPreset.MEDIUM,
                            QualityPreset.HIGH,
                            QualityPreset.ULTRA
                        ).forEach { preset ->
                            val supported = capabilities.isResolutionSupported(preset.resolution)
                            FilterChip(
                                selected = settings.qualityPreset == preset,
                                enabled = supported,
                                onClick = { onApplyPreset(preset) },
                                label = {
                                    Text("${preset.label} (${preset.resolution.shortLabel})")
                                },
                                modifier = Modifier.testTag("quality_preset_${preset.name.lowercase()}"),
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
                    OutlinedButton(
                        onClick = onApplyRecommended,
                        border = BorderStroke(1.dp, SignalGreen.copy(alpha = 0.6f)),
                        modifier = Modifier.testTag("apply_recommended_quality_button")
                    ) {
                        Text("Apply Optimal Settings for This Device", color = SignalGreen)
                    }
                }
            }

            // Hardware-Verified Resolutions
            item {
                GamingCard {
                    SectionHeader(
                        title = "Video Resolution",
                        subtitle = "Only hardware-supported encoder resolutions can be selected",
                        icon = Icons.Default.Videocam,
                        accent = ElectricBlue
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        VideoResolution.entries.forEach { res ->
                            val support = capabilities.resolutionSupport[res]
                            val isSupported = support?.isSupported == true
                            val isSelected = settings.resolution == res
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = isSupported) { onSelectResolution(res) }
                                    .testTag("resolution_option_${res.shortLabel}"),
                                shape = RoundedCornerShape(14.dp),
                                color = when {
                                    isSelected -> ElectricBlue.copy(alpha = 0.18f)
                                    !isSupported -> CarbonSurface.copy(alpha = 0.5f)
                                    else -> CarbonSurfaceHigh
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) ElectricBlue else CarbonBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = res.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isSupported) TextPrimary else TextMuted
                                        )
                                        Text(
                                            text = if (isSupported) {
                                                val (w, h) = res.alignedDimensions(isLandscape = true)
                                                "${w}×${h} • Recommended ${res.recommendedBitrateMbps} Mbps"
                                            } else {
                                                support?.reason ?: "Unsupported by hardware encoder"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSupported) TextSecondary else ThermalAmber
                                        )
                                    }
                                    StatusPill(
                                        text = when {
                                            isSelected -> "ACTIVE"
                                            isSupported -> "SUPPORTED"
                                            else -> "UNSUPPORTED"
                                        },
                                        color = when {
                                            isSelected -> SignalGreen
                                            isSupported -> ElectricBlue
                                            else -> TextMuted
                                        },
                                        leadingDot = false
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Hardware-Verified FPS + Codec
            item {
                GamingCard {
                    SectionHeader(
                        title = "Frame Rate (FPS) & Codec",
                        subtitle = "Verified for ${settings.resolution.shortLabel} on device encoder",
                        icon = Icons.Default.Speed,
                        accent = NeonViolet
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VideoFps.entries.forEach { fps ->
                            val supported = capabilities.isFpsSupported(settings.resolution, fps)
                            val selected = settings.fps == fps
                            FilterChip(
                                selected = selected,
                                enabled = supported,
                                onClick = { onSelectFps(fps) },
                                label = { Text("${fps.value}") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("fps_option_${fps.value}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonViolet,
                                    selectedLabelColor = Color.White,
                                    containerColor = CarbonSurfaceHigh,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    val unsupportedFpsNote = VideoFps.entries
                        .filter { !capabilities.isFpsSupported(settings.resolution, it) }
                        .map { it.label }
                    if (unsupportedFpsNote.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Disabled at ${settings.resolution.shortLabel}: ${unsupportedFpsNote.joinToString()} (exceeds encoder capability).",
                            style = MaterialTheme.typography.bodySmall,
                            color = ThermalAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Hardware Video Codec (MP4)", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        VideoCodec.entries.forEach { codec ->
                            val cSupport = capabilities.codecs[codec]
                            val isSupported = cSupport?.isSupported == true
                            val isSelected = settings.codec == codec
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = isSupported) { onSelectCodec(codec) }
                                    .testTag("codec_option_${codec.name.lowercase()}"),
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) NeonViolet.copy(alpha = 0.2f) else CarbonSurfaceHigh,
                                border = BorderStroke(1.dp, if (isSelected) NeonViolet else CarbonBorder)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        codec.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isSupported) TextPrimary else TextMuted
                                    )
                                    Text(
                                        when {
                                            !isSupported -> "Unavailable"
                                            cSupport.isHardwareAccelerated -> "Hardware Encoder"
                                            else -> "Software Fallback"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (cSupport?.isHardwareAccelerated == true) SignalGreen else ThermalAmber
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bitrate, File Size Estimate & Adaptive Protection
            item {
                GamingCard {
                    SectionHeader(
                        title = "Bitrate & Adaptive Engine",
                        subtitle = "Estimated file size: ~${"%.1f".format(settings.estimatedMbPerMinute())} MB/min • ~${settings.estimatedMaxMinutes(telemetry.availableStorageBytes)} min free",
                        icon = Icons.Default.Tune,
                        accent = CyberCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SettingToggleRow(
                        title = "Automatic Bitrate Optimization",
                        subtitle = "Calculates optimal bitrate from resolution, FPS, codec & performance mode",
                        checked = settings.autoBitrateOptimization,
                        onCheckedChange = { auto ->
                            onUpdateSettings { it.copy(autoBitrateOptimization = auto) }
                        },
                        accentColor = CyberCyan
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (settings.autoBitrateOptimization) "Effective Auto Bitrate" else "Manual Video Bitrate",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            "${"%.1f".format(settings.effectiveVideoBitrateMbps())} Mbps",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = CyberCyan
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
                        colors = SliderDefaults.colors(thumbColor = CyberCyan, activeTrackColor = CyberCyan),
                        modifier = Modifier.testTag("bitrate_slider")
                    )

                    SettingToggleRow(
                        title = "Adaptive Bitrate (Thermal Protection)",
                        subtitle = "Dynamically steps down encoder bitrate if device overheats during gameplay",
                        checked = settings.adaptiveBitrate,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(adaptiveBitrate = v) } },
                        accentColor = ElectricBlue
                    )
                    SettingToggleRow(
                        title = "Adaptive FPS Protection",
                        subtitle = "Allows safe frame pacing under heavy CPU/GPU game load to avoid lag",
                        checked = settings.adaptiveFps,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(adaptiveFps = v) } },
                        accentColor = ElectricBlue
                    )
                }
            }

            // Orientation & Hardware Video Quality Benchmark Test
            item {
                GamingCard(accentColor = CyberCyan) {
                    SectionHeader(
                        title = "Orientation & Hardware Test",
                        subtitle = "Benchmark your exact selected profile on this device's encoder",
                        icon = Icons.Default.ScreenRotation,
                        accent = CyberCyan
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OrientationMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.orientationMode == mode,
                                onClick = { onUpdateSettings { it.copy(orientationMode = mode) } },
                                label = { Text(mode.label) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("orientation_${mode.name.lowercase()}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue,
                                    selectedLabelColor = Color.White,
                                    containerColor = CarbonSurfaceHigh,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRunQualityTest,
                        enabled = !qualityTestRunning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("run_video_quality_test_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                    ) {
                        if (qualityTestRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Benchmarking Hardware Encoder...")
                        } else {
                            Text("Run Real Video Quality Test")
                        }
                    }

                    qualityTestResult?.let { result ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = (if (result.passed) SignalGreen else ThermalAmber).copy(alpha = 0.14f),
                            border = BorderStroke(
                                1.dp,
                                (if (result.passed) SignalGreen else ThermalAmber).copy(alpha = 0.5f)
                            )
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
                                    modifier = Modifier.size(20.dp)
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

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
