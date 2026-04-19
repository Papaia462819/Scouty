package com.scouty.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.CloudLightning
import com.composables.icons.lucide.Crosshair
import com.composables.icons.lucide.Droplet
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mountain
import com.composables.icons.lucide.Route
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Sun
import com.composables.icons.lucide.Sunset
import com.composables.icons.lucide.TrendingUp
import com.composables.icons.lucide.TriangleAlert
import com.scouty.app.R
import com.scouty.app.ui.components.CategoryIconTile
import com.scouty.app.ui.components.DifficultyBadge
import com.scouty.app.ui.components.DifficultyLevel
import com.scouty.app.ui.components.RouteRemoteImage
import com.scouty.app.ui.components.ScoutyCard
import com.scouty.app.ui.components.ScoutySectionHeader
import com.scouty.app.ui.components.StatusPill
import com.scouty.app.ui.models.ActiveTrail
import com.scouty.app.ui.models.ActiveTrailState
import com.scouty.app.ui.models.HomeStatus
import com.scouty.app.ui.models.RouteRecommendation
import com.scouty.app.ui.models.TrailMetadataFormatter
import com.scouty.app.ui.theme.AccentGreen
import com.scouty.app.ui.theme.AccentGreenBg
import com.scouty.app.ui.theme.BgSurface
import com.scouty.app.ui.theme.BgSurfaceRaised
import com.scouty.app.ui.theme.BorderDefault
import com.scouty.app.ui.theme.BorderSubtle
import com.scouty.app.ui.theme.Danger
import com.scouty.app.ui.theme.Info
import com.scouty.app.ui.theme.JetBrainsMonoFamily
import com.scouty.app.ui.theme.TextMuted
import com.scouty.app.ui.theme.TextPrimary
import com.scouty.app.ui.theme.TextSecondary
import com.scouty.app.ui.theme.TextTertiary
import com.scouty.app.ui.theme.Warning
import com.scouty.app.ui.theme.Water
import java.util.Locale
import kotlin.math.abs

@Composable
fun HomeScreen(
    status: HomeStatus,
    contentPadding: PaddingValues,
    onActiveTrailClick: () -> Unit = {},
    onShelterClick: () -> Unit = {},
    onWaterClick: () -> Unit = {},
) {
    val weatherSnapshot = buildWeatherSnapshot(status)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(14.dp))
        HomeHeader()

        Spacer(Modifier.height(16.dp))
        LocationPanel(status = status)

        Spacer(Modifier.height(10.dp))
        WeatherRow(status = status, snapshot = weatherSnapshot)

        Spacer(Modifier.height(20.dp))
        ScoutySectionHeader(
            title = stringResource(R.string.home_active_trail),
            trailingText = stringResource(R.string.home_view_all),
            trailingColor = AccentGreen,
        )
        Spacer(Modifier.height(10.dp))
        if (status.activeTrail != null) {
            ActiveTrailCard(trail = status.activeTrail, onClick = onActiveTrailClick)
        } else {
            EmptyTrailCard()
        }

        Spacer(Modifier.height(20.dp))
        ScoutySectionHeader(title = stringResource(R.string.home_quick_actions))
        Spacer(Modifier.height(10.dp))
        QuickActionsRow(onShelter = onShelterClick, onWater = onWaterClick)

        Spacer(Modifier.height(20.dp))
        ScoutySectionHeader(
            title = "RECOMANDATE PENTRU TINE",
            trailing = {
                Text(
                    text = if (status.routeRecommendations.isEmpty()) "—" else "${status.routeRecommendations.size} trasee",
                    fontSize = 11.sp,
                    color = TextTertiary,
                )
            },
        )
        Spacer(Modifier.height(10.dp))
        if (status.routeRecommendations.isEmpty()) {
            EmptyTrailCard(message = "Alege cateva preferinte in profil sau asteapta un GPS fix pentru recomandari.")
        } else {
            status.routeRecommendations.forEachIndexed { index, recommendation ->
                RecommendedTrailCard(recommendation = recommendation)
                if (index != status.routeRecommendations.lastIndex) {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIconTile(icon = Lucide.Mountain, color = AccentGreen)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Scouty",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeaderIconButton(icon = Lucide.Bell, showDot = false)
            HeaderIconButton(icon = Lucide.Settings, showDot = false)
        }
    }
}

@Composable
private fun HeaderIconButton(icon: ImageVector, showDot: Boolean) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Transparent)
            .border(0.5.dp, BorderDefault, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(14.dp),
        )
        if (showDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(5.dp)
                    .background(AccentGreen, RoundedCornerShape(50)),
            )
        }
    }
}

@Composable
private fun LocationPanel(status: HomeStatus) {
    val isOnline = status.isOnline && status.latitude != null
    val statusText = when {
        status.latitude == null -> "WAITING"
        isOnline -> "ONLINE"
        else -> stringResource(R.string.state_offline)
    }
    val statusColor = when {
        status.latitude == null -> TextTertiary
        isOnline -> AccentGreen
        else -> Warning
    }

    ScoutyCard(
        modifier = Modifier.fillMaxWidth(),
        semantic = AccentGreen,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusPill(text = statusText, color = statusColor, pulsing = isOnline)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Lucide.Crosshair,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(11.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = when {
                        status.accuracy != null -> "GPS lock · ±${status.accuracy.toInt()}m"
                        status.gpsFixed -> stringResource(R.string.state_gps_lock)
                        else -> "Searching GPS"
                    },
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatCoordinate(status.latitude, "N", "S"),
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.5).sp,
                    color = TextPrimary,
                )
                Text(
                    text = formatCoordinate(status.longitude, "E", "W"),
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.5).sp,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (status.locationName.isNotBlank()) {
                        "CURRENT LOCATION · ${status.locationName}"
                    } else {
                        "CURRENT LOCATION"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = status.altitude?.let { String.format(Locale.US, "%,.0f", it) } ?: "---",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.8).sp,
                        color = AccentGreen,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "m",
                        fontSize = 16.sp,
                        color = AccentGreen,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                Text(
                    text = "ALTITUDE ASL",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun WeatherRow(status: HomeStatus, snapshot: WeatherSnapshot) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScoutyCard(
            modifier = Modifier.weight(1f),
            semantic = Info,
            shape = RoundedCornerShape(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = snapshot.icon,
                    contentDescription = null,
                    tint = Info,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = snapshot.temperature,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = snapshot.summary,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }

        ScoutyCard(
            modifier = Modifier.weight(1f),
            semantic = Warning,
            shape = RoundedCornerShape(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Lucide.Sunset,
                    contentDescription = null,
                    tint = Warning,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = status.activeTrail?.sunsetTime ?: "—",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Sunset today",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun ActiveTrailCard(trail: ActiveTrail, onClick: () -> Unit) {
    val remainingDistanceKm = (trail.distanceKm * (1f - trail.progress.coerceIn(0f, 1f))).coerceAtLeast(0.0)
    val completionPercent = (trail.progress.coerceIn(0f, 1f) * 100).toInt()
    val isStarted = trail.trackingState == ActiveTrailState.ACTIVE
    val markerLabel = TrailMetadataFormatter.formatTrailMarkers(trail.markingSymbols)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        if (!trail.imageUrl.isNullOrBlank()) {
            RouteRemoteImage(
                imageUrl = trail.imageUrl,
                contentDescription = trail.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF4A6580), Color(0xFF2D3A4A)),
                        ),
                    ),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DifficultyBadge(level = difficultyLevel(trail.difficulty))
                StatusPill(
                    text = if (isStarted) "In progress" else "Planned",
                    color = if (isStarted) AccentGreen else TextSecondary,
                    pulsing = isStarted,
                    backdrop = Color.Black.copy(alpha = 0.5f),
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = trail.name,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (isStarted) {
                    "${formatDistance(remainingDistanceKm)} remaining · ${trail.estimatedDuration} · +${trail.elevationGain} m"
                } else {
                    "Ready to start · ${trail.estimatedDuration} · +${trail.elevationGain} m"
                },
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TranslucentChip(icon = Lucide.Route, text = formatDistance(trail.distanceKm))
                TranslucentChip(icon = Lucide.TrendingUp, text = "+${trail.elevationGain} m")
                TranslucentChip(icon = Lucide.Clock, text = trail.estimatedDuration)
            }

            markerLabel?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Marcaj: $it",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }

            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { trail.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AccentGreen,
                trackColor = Color.White.copy(alpha = 0.15f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isStarted) "$completionPercent% completed" else "Planned trail",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun TranslucentChip(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(10.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 10.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun EmptyTrailCard(message: String = "No active trail. Search and set one!") {
    ScoutyCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun QuickActionsRow(onShelter: () -> Unit, onWater: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickActionTile(
            modifier = Modifier.weight(1f),
            icon = Lucide.TriangleAlert,
            label = "SOS",
            color = Danger,
            onClick = {},
        )
        QuickActionTile(
            modifier = Modifier.weight(1f),
            icon = Lucide.House,
            label = "Shelter",
            color = Warning,
            onClick = onShelter,
        )
        QuickActionTile(
            modifier = Modifier.weight(1f),
            icon = Lucide.Droplet,
            label = "Water",
            color = Water,
            onClick = onWater,
        )
    }
}

@Composable
private fun QuickActionTile(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.06f))
            .border(0.5.dp, color.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RecommendedTrailCard(recommendation: RouteRecommendation) {
    val info = buildString {
        append(recommendation.secondarySummary)
        recommendation.proximityKm?.let {
            if (length > 0) append(" · ")
            append(String.format(Locale.US, "%.0f km distanta", it))
        }
    }

    ScoutyCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF2D3A4A), Color(0xFF4A6580)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Lucide.Mountain,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = recommendation.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    DifficultyBadge(level = difficultyLevel(recommendation.difficulty.name))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = info,
                    fontSize = 10.sp,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (recommendation.whyItFits.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = recommendation.whyItFits,
                        fontSize = 10.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private data class WeatherSnapshot(
    val temperature: String,
    val summary: String,
    val icon: ImageVector,
)

private fun buildWeatherSnapshot(status: HomeStatus): WeatherSnapshot {
    val parts = status.activeTrail?.weatherForecast?.split(",", limit = 2).orEmpty()
    val temperature = parts.getOrNull(0)?.trim().takeUnless { it.isNullOrBlank() } ?: "12°C"
    val summary = parts.getOrNull(1)?.trim().takeUnless { it.isNullOrBlank() } ?: "Partly cloudy"
    val lowercaseSummary = summary.lowercase(Locale.getDefault())

    val icon = when {
        "storm" in lowercaseSummary || "thunder" in lowercaseSummary -> Lucide.CloudLightning
        "clear" in lowercaseSummary || "sun" in lowercaseSummary -> Lucide.Sun
        else -> Lucide.Cloud
    }
    return WeatherSnapshot(temperature = temperature, summary = summary, icon = icon)
}

private fun formatDistance(distanceKm: Double): String =
    String.format(Locale.getDefault(), "%.1f km", distanceKm)

private fun formatCoordinate(value: Double?, positiveDirection: String, negativeDirection: String): String =
    value?.let {
        val direction = if (it >= 0.0) positiveDirection else negativeDirection
        String.format(Locale.US, "%.4f° %s", abs(it), direction)
    } ?: "---"

private fun difficultyLevel(difficulty: String): DifficultyLevel =
    when (difficulty.uppercase(Locale.ROOT)) {
        "HARD", "EXPERT" -> DifficultyLevel.HARD
        "MEDIUM" -> DifficultyLevel.MEDIUM
        else -> DifficultyLevel.EASY
    }
