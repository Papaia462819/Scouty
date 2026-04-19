package com.scouty.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ScoutyDarkColors = darkColorScheme(
    primary = AccentGreen,
    onPrimary = AccentGreenOnSurface,
    secondary = AccentGreen,
    onSecondary = AccentGreenOnSurface,
    tertiary = Water,
    onTertiary = Color.White,
    background = BgPrimary,
    onBackground = TextPrimary,
    surface = BgPrimary,
    onSurface = TextPrimary,
    surfaceVariant = BgSurface,
    onSurfaceVariant = TextSecondary,
    outline = BorderDefault,
    outlineVariant = BorderSubtle,
    error = Danger,
    onError = Color.White,
    errorContainer = Danger.copy(alpha = 0.1f),
    onErrorContainer = Danger,
)

@Composable
fun ScoutyTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ScoutyDarkColors,
        typography = ScoutyTypography,
        shapes = ScoutyShapes,
        content = content,
    )
}
