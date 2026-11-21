package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.system.DeviceCapabilities
import com.example.ui.components.GamingCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThermalAmber
import com.example.viewmodel.PermissionSnapshot

@Composable
fun PermissionsPrivacyScreen(
    permissions: PermissionSnapshot,
    capabilities: DeviceCapabilities,
    onBack: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAppSettings: () -> Unit
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
                    IconButton(onClick = onBack, modifier = Modifier.testTag("perm_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Column {
                        Text("Permissions & Privacy", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                        Text("100% Local Offline Recording • Zero Login or Tracking", style = MaterialTheme.typography.bodySmall, color = SignalGreen)
                    }
                }
            }

            // Privacy Guarantees Card
            item {
                GamingCard(accentColor = SignalGreen) {
                    SectionHeader(
                        title = "Zero-Cloud Privacy Architecture",
                        subtitle = "Everything runs strictly on your Android device",
                        icon = Icons.Default.VerifiedUser,
                        accent = SignalGreen
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    PrivacyBullet(Icons.Default.CloudOff, "No Login, No Account, No Cloud Uploads — opens straight to dashboard and works completely offline.")
                    PrivacyBullet(Icons.Default.Lock, "Local Gallery Only — videos save directly to Movies/ZeroRecorder/ and screenshots to Pictures/ZeroRecorder/.")
                    PrivacyBullet(Icons.Default.ScreenShare, "Transparent Capture — uses official Android MediaProjection consent and shows an active foreground notification at all times.")
                    PrivacyBullet(Icons.Default.CheckCircle, "Protected App Respect — DRM and FLAG_SECURE sensitive apps (banking, passwords) are automatically shielded by Android.")
                }
            }

            // Runtime Permissions Checklist
            item {
                GamingCard(accentColor = ElectricBlue) {
                    SectionHeader(
                        title = "Android Permissions",
                        subtitle = "Requested only when needed — zero broad storage permissions used",
                        icon = Icons.Default.Lock,
                        accent = ElectricBlue
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    PermissionActionRow(
                        icon = Icons.Default.Mic,
                        title = "Microphone (RECORD_AUDIO)",
                        description = "Required for your voice recording, live mic test, and Android AudioPlaybackCapture internal game/team audio.",
                        granted = permissions.hasMicrophone,
                        actionLabel = "Grant",
                        onAction = onRequestMicrophone,
                        testTag = "perm_grant_mic_button"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    PermissionActionRow(
                        icon = Icons.Default.Notifications,
                        title = "Recording Notification",
                        description = "Displays live timer and Pause / Mute / Stop controls while recording in background.",
                        granted = permissions.hasNotifications,
                        actionLabel = "Grant",
                        onAction = onRequestNotifications,
                        testTag = "perm_grant_notif_button"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    PermissionActionRow(
                        icon = Icons.Default.PictureInPictureAlt,
                        title = "Floating Overlay (Optional)",
                        description = "Allows movable one-hand gameplay controls over your game. Optional—recording works via notification too.",
                        granted = permissions.hasOverlay,
                        actionLabel = "Configure",
                        onAction = onOpenOverlaySettings,
                        testTag = "perm_grant_overlay_button"
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onOpenAppSettings,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, CarbonBorder)
                    ) {
                        Text("Open Android System App Settings")
                    }
                }
            }

            // Honest Android Capture Capabilities Disclosure
            item {
                GamingCard(accentColor = NeonViolet) {
                    SectionHeader(
                        title = "Platform Capture Capabilities",
                        subtitle = "Honest technical status for your Android version",
                        icon = Icons.Default.ScreenShare,
                        accent = NeonViolet
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (capabilities.supportsAppWindowCaptureChoice) {
                            "• Single-App / Full-Screen Choice: Supported (Android 14+). When starting a recording, Android's system dialog lets you choose either a single game app or the entire display."
                        } else {
                            "• Capture Scope: Full-Display Capture. Android versions below 14 require full-screen capture via MediaProjection; Zero Recorder never falsely claims single-app capture on this OS version."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (capabilities.supportsInternalAudioCapture) {
                            "• Game & Teammate Voice Capture: Supported via Android AudioPlaybackCapture (Android 10+). Apps that explicitly set ALLOW_CAPTURE_BY_NONE or route voice chat over Bluetooth SCO telephony cannot be captured by any screen recorder without root; Zero Recorder detects and reports this transparently."
                        } else {
                            "• Internal Audio Capture: Requires Android 10+. On this OS version only Microphone capture is available."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun PrivacyBullet(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = SignalGreen,
            modifier = Modifier.size(18.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
    }
}

@Composable
private fun PermissionActionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (granted) SignalGreen else CyberCyan,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        if (granted) {
            StatusPill(text = "GRANTED", color = SignalGreen, leadingDot = false)
        } else {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag(testTag)
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
