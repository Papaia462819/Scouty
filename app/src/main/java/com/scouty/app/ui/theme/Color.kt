package com.scouty.app.ui.theme

import androidx.compose.ui.graphics.Color

// Backgrounds
val BgPrimary = Color(0xFF0A0F0A)
val BgSurface = Color(0xFFFFFFFF).copy(alpha = 0.02f)
val BgSurfaceRaised = Color(0xFFFFFFFF).copy(alpha = 0.04f)

// Borders
val BorderSubtle = Color(0xFFFFFFFF).copy(alpha = 0.06f)
val BorderDefault = Color(0xFFFFFFFF).copy(alpha = 0.08f)
val BorderStrong = Color(0xFFFFFFFF).copy(alpha = 0.12f)

// Text
val TextPrimary = Color(0xFFE8EDE8)
val TextSecondary = Color(0xFFE8EDE8).copy(alpha = 0.6f)
val TextTertiary = Color(0xFFE8EDE8).copy(alpha = 0.45f)
val TextMuted = Color(0xFFE8EDE8).copy(alpha = 0.3f)

// Brand accent (primary mint-green)
val AccentGreen = Color(0xFF9BE063)
val AccentGreenOnSurface = Color(0xFF0A1F05)
val AccentGreenBg = Color(0xFF9BE063).copy(alpha = 0.08f)
val AccentGreenBorder = Color(0xFF9BE063).copy(alpha = 0.2f)

// Semantic colors
val Danger = Color(0xFFE24B4A)
val DangerSoft = Color(0xFFF09595)
val Warning = Color(0xFFEF9F27)
val Info = Color(0xFF6BB3F0)
val Water = Color(0xFF5DCAA5)

// Legacy aliases retained to avoid breaking non-UI references.
// Mapped to the closest semantic token above.
val BackgroundDark = BgPrimary
val SurfaceDark = BgPrimary
val CardDark = BgSurface
val CardDarkAlt = BgSurfaceRaised
val CardStroke = BorderDefault
val NavSurface = BgPrimary

val PrimaryGreen = AccentGreen
val SecondaryGreen = AccentGreen.copy(alpha = 0.6f)
val AccentTeal = Water

val StatusOrange = Warning
val StatusRed = Danger
val StatusBlue = Info
val StatusAmber = Warning
val StatusAmberSoft = Warning.copy(alpha = 0.15f)
val StatusRedSoft = Danger.copy(alpha = 0.15f)
val StatusBlueSoft = Info.copy(alpha = 0.15f)
val StatusTealSoft = Water.copy(alpha = 0.15f)
