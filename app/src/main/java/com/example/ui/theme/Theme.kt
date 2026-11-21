package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ColorTokenOutlineVariant = Color(0xFF222834)
private val ColorTokenErrorLight = Color(0xFFFCA5A5)

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
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
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
