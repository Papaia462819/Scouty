package com.scouty.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.Black,
    secondary = SecondaryGreen,
    onSecondary = Color.White,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextSecondary,
    tertiary = AccentTeal,
    onTertiary = Color.White,
    outline = CardStroke,
    outlineVariant = CardStroke.copy(alpha = 0.72f),
    error = StatusRed,
    onError = Color.White,
)

@Composable
fun ScoutyTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography,
        content = content,
    )
}
