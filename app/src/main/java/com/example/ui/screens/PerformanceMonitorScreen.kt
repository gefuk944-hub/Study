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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.PerformanceMode
import com.example.data.RecorderSettings
import com.example.system.DeviceCapabilities
import com.example.system.SystemTelemetry
import com.example.ui.components.GamingCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.TelemetryStatTile
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonSurfaceHigh
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.RecordCrimson
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThermalAmber

@Composable
fun PerformanceMonitorScreen(
    settings: RecorderSettings,
    telemetry: SystemTelemetry,
    capabilities: DeviceCapabilities,
    onBack: () -> Unit,
    onSelectPerformanceMode: (PerformanceMode) -> Unit,
    onStepDownThermal: () -> Unit,
    onApplyRecommended: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("perf_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Column {
                        Text("Performance Monitor", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                        Text(
                            if (capabilities.isLowRamTier) "4GB RAM Optimization Active" else "Live Hardware, Thermal & Storage Telemetry",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (capabilities.isLowRamTier) CyberCyan else TextSecondary
                        )
                    }
                }
            }

            // Live RAM & CPU Card
            item {
                GamingCard(accentColor = ElectricBlue) {
                    SectionHeader(
                        title = "Memory & Processor",
                        subtitle = "${telemetry.cpuCores} CPU Cores • Total RAM ${"%.1f".format(capabilities.totalRamGb)} GB",
                        icon = Icons.Default.Memory,
                        accent = ElectricBlue,
                        trailing = {
                            StatusPill(
                                text = "${(telemetry.ramUsageFraction * 100).toInt()}% USED",
                                color = if (telemetry.ramUsageFraction > 0.85f) ThermalAmber else CyberCyan
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { telemetry.ramUsageFraction },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (telemetry.ramUsageFraction > 0.85f) ThermalAmber else ElectricBlue,
                        trackColor = CarbonSurfaceHigh
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Used: ${telemetry.usedRamMb} MB", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Text("Available: ${telemetry.availableRamMb} MB", style = MaterialTheme.typography.labelMedium, color = SignalGreen)
                    }
                }
            }

            // Thermal & Battery Card
            item {
                GamingCard(accentColor = if (telemetry.thermalHealth.isWarning) ThermalAmber else SignalGreen) {
                    SectionHeader(
                        title = "Thermal & Battery Guard",
                        subtitle = telemetry.thermalStatusLabel,
                        icon = Icons.Default.Thermostat,
                        accent = if (telemetry.thermalHealth.isWarning) ThermalAmber else SignalGreen,
                        trailing = {
                            StatusPill(
                                text = telemetry.thermalHealth.label.uppercase(),
                                color = if (telemetry.thermalHealth.isWarning) ThermalAmber else SignalGreen
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryStatTile(
                            label = "BATTERY TEMP",
                            value = "${"%.1f".format(telemetry.batteryTempCelsius)}°C",
                            subvalue = if (telemetry.thermalHealth.isWarning) "Reduce load advised" else "Safe recording range",
                            accent = if (telemetry.thermalHealth.isWarning) ThermalAmber else SignalGreen,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "BATTERY LEVEL",
                            value = "${telemetry.batteryPercent}%",
                            subvalue = if (telemetry.isCharging) "Charging (extra heat)" else "On Battery",
                            accent = CyberCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (telemetry.thermalHealth.isWarning) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onStepDownThermal,
                            colors = ButtonDefaults.buttonColors(containerColor = ThermalAmber, contentColor = ObsidianVoid),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reduce Resolution, FPS & Bitrate Now")
                        }
                    }
                }
            }

            // Storage & Recording Duration Estimator
            item {
                GamingCard(accentColor = if (telemetry.isLowStorage) RecordCrimson else CyberCyan) {
                    SectionHeader(
                        title = "Storage & Capacity",
                        subtitle = "Zero Recorder never deletes your existing videos automatically",
                        icon = Icons.Default.Storage,
                        accent = if (telemetry.isLowStorage) RecordCrimson else CyberCyan
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { telemetry.usedStorageFraction },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (telemetry.isLowStorage) RecordCrimson else CyberCyan,
                        trackColor = CarbonSurfaceHigh
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "AVAILABLE",
                            value = "${"%.1f".format(telemetry.availableStorageGb)} GB",
                            subvalue = "of ${"%.0f".format(telemetry.totalStorageGb)} GB",
                            accent = if (telemetry.isLowStorage) RecordCrimson else CyberCyan,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "EST. SIZE",
                            value = "${"%.1f".format(settings.estimatedMbPerMinute())} MB/m",
                            subvalue = "${"%.1f".format(settings.effectiveVideoBitrateMbps())} Mbps",
                            accent = ElectricBlue,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "MAX TIME",
                            value = "${settings.estimatedMaxMinutes(telemetry.availableStorageBytes)} min",
                            subvalue = "Reserve ${settings.autoStopLowStorageMb}MB",
                            accent = SignalGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Performance Mode Selector
            item {
                GamingCard(accentColor = NeonViolet) {
                    SectionHeader(
                        title = "Optimization Mode",
                        subtitle = "Controls encoder bitrate ceiling & HUD polling to avoid gameplay lag",
                        icon = Icons.Default.Speed,
                        accent = NeonViolet
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PerformanceMode.entries.forEach { mode ->
                            val selected = settings.performanceMode == mode
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPerformanceMode(mode) }
                                    .testTag("perf_mode_${mode.name.lowercase()}"),
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) NeonViolet.copy(alpha = 0.2f) else CarbonSurfaceHigh,
                                border = BorderStroke(1.dp, if (selected) NeonViolet else CarbonBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(mode.label, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                        Text(mode.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    }
                                    if (selected) {
                                        StatusPill(text = "ACTIVE", color = SignalGreen, leadingDot = false)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onApplyRecommended,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                    ) {
                        Text("Apply Auto-Recommended Profile (${capabilities.recommendedResolution.shortLabel} • ${capabilities.recommendedFps.label})")
                    }
                }
            }

            // Hardware Codec Inspection Report
            item {
                GamingCard {
                    SectionHeader(
                        title = "Hardware Encoder Report",
                        subtitle = "Probed live from Android MediaCodecList",
                        icon = Icons.Default.BatteryChargingFull,
                        accent = ElectricBlue
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    capabilities.codecs.values.forEach { codec ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(codec.codec.displayName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text(
                                    codec.encoderName ?: "Not available on chipset",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            StatusPill(
                                text = when {
                                    !codec.isSupported -> "UNAVAILABLE"
                                    codec.isHardwareAccelerated -> "HW ${codec.maxWidth}x${codec.maxHeight}"
                                    else -> "SOFTWARE"
                                },
                                color = if (codec.isHardwareAccelerated) SignalGreen else ThermalAmber,
                                leadingDot = false
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
