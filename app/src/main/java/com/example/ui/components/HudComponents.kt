package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CarbonSurfaceHigh
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.RecordCrimson
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThermalAmber

/**
 * Clean native matte container card with subtle hairline border (no neon glow borders).
 */
@Composable
fun GamingCard(
    modifier: Modifier = Modifier,
    accentColor: Color = CarbonBorder,
    containerColor: Color = CarbonSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    // Only tint the hairline border for true warning/error states; keep all normal cards clean & neutral.
    val effectiveBorder = if (accentColor == RecordCrimson || accentColor == ThermalAmber) {
        accentColor.copy(alpha = 0.55f)
    } else {
        CarbonBorder.copy(alpha = 0.72f)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, effectiveBorder),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

/**
 * Clean native Android uppercase section group label above cards/lists.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            letterSpacing = 0.9.sp,
            fontWeight = FontWeight.SemiBold
        ),
        color = TextMuted,
        modifier = modifier.padding(start = 4.dp, bottom = 6.dp, top = 4.dp)
    )
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    accent: Color = ElectricBlue,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
        trailing?.invoke()
    }
}

/**
 * Native Android grouped preference row with optional leading icon, subtitle, value, and chevron.
 */
@Composable
fun SettingsNavRow(
    title: String,
    subtitle: String? = null,
    trailingValue: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = TextSecondary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!trailingValue.isNullOrBlank()) {
            Text(
                text = trailingValue,
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                modifier = Modifier.padding(end = 6.dp)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    leadingDot: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingDot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Clean real-time studio VU level meter driven directly by PCM RMS levels (0f..1f).
 */
@Composable
fun SegmentedAudioMeter(
    level: Float,
    modifier: Modifier = Modifier,
    segments: Int = 28,
    activeColor: Color = SignalGreen,
    muted: Boolean = false
) {
    val animatedLevel by animateFloatAsState(
        targetValue = if (muted) 0f else level.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 700f),
        label = "audio_meter_level"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
    ) {
        val gap = 2.dp.toPx()
        val totalGap = gap * (segments - 1)
        val segWidth = ((size.width - totalGap) / segments).coerceAtLeast(2f)
        val activeSegments = (animatedLevel * segments).toInt()

        for (i in 0 until segments) {
            val fraction = i.toFloat() / segments.toFloat()
            val lit = i < activeSegments
            val segColor = when {
                !lit -> CarbonSurfaceHigh
                fraction > 0.86f -> RecordCrimson
                fraction > 0.68f -> ThermalAmber
                else -> SignalGreen
            }
            drawRoundRect(
                color = segColor,
                topLeft = Offset(i * (segWidth + gap), 0f),
                size = Size(segWidth, size.height),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = ElectricBlue,
    testTag: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (enabled) TextPrimary else TextMuted
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) TextSecondary else TextMuted
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
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
fun TelemetryStatTile(
    label: String,
    value: String,
    subvalue: String? = null,
    accent: Color = TextPrimary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = CarbonSurfaceHigh
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (accent == RecordCrimson || accent == ThermalAmber || accent == SignalGreen) accent else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subvalue.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subvalue,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun GradientAccentDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = CarbonBorder.copy(alpha = 0.65f)
    )
}

@Composable
fun RenameMediaDialog(
    currentName: String,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var input by remember(currentName) {
        mutableStateOf(currentName.substringBeforeLast("."))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CarbonSurface,
        title = { Text(title, color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                label = { Text("File name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_media_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(input) },
                enabled = input.isNotBlank(),
                modifier = Modifier.testTag("confirm_rename_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ConfirmDeleteDialog(
    itemName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CarbonSurface,
        title = { Text("Delete file?", color = TextPrimary) },
        text = {
            Text(
                "Permanently delete \"$itemName\" from your device Gallery? This cannot be undone.",
                color = TextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = RecordCrimson),
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun formatBytesHuman(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024f * 1024f)
    return if (mb >= 1024f) "%.2f GB".format(mb / 1024f) else "%.1f MB".format(mb)
}

fun formatThermalColor(isWarning: Boolean): Color = if (isWarning) ThermalAmber else SignalGreen
