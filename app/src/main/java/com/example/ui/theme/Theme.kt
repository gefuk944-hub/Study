package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ColorTokenOutlineVariant = androidx.compose.ui.graphics.Color(0xFF1C2436)
private val ColorTokenErrorLight = androidx.compose.ui.graphics.Color(0xFFFF8A9B)

private val ZeroRecorderDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = OnElectricBlue,
    primaryContainer = ElectricBlueContainer,
    onPrimaryContainer = ElectricBlueBright,
    secondary = NeonViolet,
    onSecondary = OnElectricBlue,
    secondaryContainer = NeonVioletContainer,
    onSecondaryContainer = NeonVioletBright,
    tertiary = CyberCyan,
    onTertiary = ObsidianVoid,
    tertiaryContainer = CyberCyanDim,
    onTertiaryContainer = CyberCyan,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = GraphiteBase,
    onSurface = TextPrimary,
    surfaceVariant = CarbonSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = GraphiteBase,
    surfaceContainer = CarbonSurface,
    surfaceContainerHigh = CarbonSurfaceHigh,
    surfaceContainerHighest = CarbonSurfaceHighest,
    outline = CarbonBorder,
    outlineVariant = ColorTokenOutlineVariant,
    error = RecordCrimson,
    onError = OnElectricBlue,
    errorContainer = RecordCrimsonDark,
    onErrorContainer = ColorTokenErrorLight
)

val ZeroRecorderShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ZeroRecorderTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ZeroRecorderDarkColorScheme,
        typography = ZeroRecorderTypography,
        shapes = ZeroRecorderShapes,
        content = content
    )
}
