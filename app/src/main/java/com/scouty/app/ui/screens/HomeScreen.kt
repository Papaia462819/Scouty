package com.scouty.app.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.House
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.scouty.app.R
import com.scouty.app.ui.components.QuickActionButton
import com.scouty.app.ui.components.RouteRemoteImage
import com.scouty.app.ui.components.ScoutyPanel
import com.scouty.app.ui.components.SectionHeader
import com.scouty.app.ui.components.StatusChip
import com.scouty.app.ui.models.ActiveTrail
import com.scouty.app.ui.models.ActiveTrailState
import com.scouty.app.ui.models.HomeStatus
import com.scouty.app.ui.models.RouteRecommendation
import com.scouty.app.ui.models.TrailMetadataFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun HomeScreen(
    status: HomeStatus,
    contentPadding: PaddingValues,
    onActiveTrailClick: () -> Unit = {},
    onShelterClick: () -> Unit = {},
    onWaterClick: () -> Unit = {}
) {
    val weatherSnapshot = buildWeatherSnapshot(status)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(14.dp))
        HomeHeader()

        Spacer(modifier = Modifier.height(18.dp))

        LocationPanel(status = status)

        Spacer(modifier = Modifier.height(14.dp))

        WeatherPanel(status = status, weatherSnapshot = weatherSnapshot)

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(title = stringResource(R.string.home_active_trail), onViewAllClick = {})

        Spacer(modifier = Modifier.height(12.dp))

        if (status.activeTrail != null) {
            ActiveTrailCard(
                trail = status.activeTrail,
                onClick = onActiveTrailClick
            )
        } else {
            EmptyTrailCard()
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(title = stringResource(R.string.home_quick_actions))

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Warning,
                label = stringResource(R.string.nav_sos),
                containerColor = Color(0xFFFF7A59),
                onClick = {}
            )
            QuickActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.House,
                label = "Shelter",
                containerColor = Color(0xFFFFB020),
                onClick = onShelterClick
            )
            QuickActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.WaterDrop,
                label = "Water",
                containerColor = Color(0xFF2ED3A6),
                onClick = onWaterClick
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(title = "Recomandate pentru tine")

        Spacer(modifier = Modifier.height(12.dp))

        if (status.routeRecommendations.isEmpty()) {
            EmptyTrailCard(message = "Alege cateva preferinte in profil sau asteapta un GPS fix pentru recomandari.")
        } else {
            status.routeRecommendations.forEach { recommendation ->
                RecommendedTrailCard(recommendation = recommendation)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Terrain,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Scouty",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp),
                fontWeight = FontWeight.Bold
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeaderActionButton(icon = Icons.Outlined.Notifications)
            HeaderActionButton(icon = Icons.Outlined.Settings)
        }
    }
}

@Composable
private fun HeaderActionButton(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(34.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LocationPanel(status: HomeStatus) {
    val statusColors = when {
        status.latitude == null -> Color(0xFF3A3F38) to Color(0xFFBBC3B6)
        status.isOnline -> Color(0xFF1F5F37) to Color(0xFF51E58A)
        else -> Color(0xFF3A2A12) to Color(0xFFFFB020)
    }

    ScoutyPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        startColor = MaterialTheme.colorScheme.surface,
        endColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusChip(
                text = when {
                    status.latitude == null -> "WAITING"
                    status.isOnline -> "ONLINE"
                    else -> stringResource(R.string.state_offline)
                },
                containerColor = statusColors.first,
                contentColor = statusColors.second
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (status.gpsFixed) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (status.gpsFixed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (status.gpsFixed) stringResource(R.string.state_gps_lock) else "Searching GPS",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (status.gpsFixed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatCoordinate(status.latitude, "N", "S"),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatCoordinate(status.longitude, "E", "W"),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        status.accuracy != null -> "${status.locationName} \u00B7 ±${status.accuracy.toInt()}m accuracy"
                        else -> "Waiting for signal..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = status.altitude?.let { String.format(Locale.US, "%,.0fm", it) } ?: "---",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "altitude ASL",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WeatherPanel(
    status: HomeStatus,
    weatherSnapshot: WeatherSnapshot
) {
    ScoutyPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        startColor = MaterialTheme.colorScheme.surfaceVariant,
        endColor = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.06f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = weatherSnapshot.icon,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = weatherSnapshot.iconTint
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = weatherSnapshot.temperature,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = weatherSnapshot.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                status.activeTrail?.name?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WeatherMetaRow(
                    icon = Icons.Default.Schedule,
                    text = status.activeTrail?.sunsetTime?.let { "Sunset $it" } ?: "Forecast pending",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                WeatherMetaRow(
                    icon = if (status.gpsFixed) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                    text = if (status.gpsFixed) "GPS ready" else "No fix yet",
                    tint = if (status.gpsFixed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WeatherMetaRow(
    icon: ImageVector,
    text: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = tint
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = tint
        )
    }
}

@Composable
private fun ActiveTrailCard(
    trail: ActiveTrail,
    onClick: () -> Unit
) {
    val remainingDistanceKm = (trail.distanceKm * (1f - trail.progress.coerceIn(0f, 1f))).coerceAtLeast(0.0)
    val completionPercent = (trail.progress.coerceIn(0f, 1f) * 100).toInt()
    val isStarted = trail.trackingState == ActiveTrailState.ACTIVE
    val markerLabel = TrailMetadataFormatter.formatTrailMarkers(trail.markingSymbols)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(242.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.82f)
        )
    ) {
        Box {
            if (!trail.imageUrl.isNullOrBlank()) {
                RouteRemoteImage(
                    imageUrl = trail.imageUrl,
                    contentDescription = trail.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF23361F), Color(0xFF162414), Color(0xFF1B2A1A))
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0x33071008),
                                Color(0xCC071008)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                StatusChip(
                    text = trail.difficulty.uppercase(Locale.getDefault()),
                    containerColor = difficultyChipColors(trail.difficulty).first,
                    contentColor = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = trail.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isStarted) {
                        "${formatDistance(remainingDistanceKm)} remaining \u00B7 ${trail.estimatedDuration}"
                    } else {
                        "Ready to start \u00B7 ${trail.estimatedDuration}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.76f)
                )
                trail.routeSummary?.takeIf { it.isNotBlank() }?.let { summary ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.84f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TrailMetric(icon = Icons.Default.Route, text = formatDistance(trail.distanceKm))
                    TrailMetric(icon = Icons.Default.NorthEast, text = "+${trail.elevationGain} m")
                    TrailMetric(icon = Icons.Default.Schedule, text = trail.estimatedDuration)
                }
                markerLabel?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Marcaj: $it",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.86f)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { trail.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.18f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isStarted) "$completionPercent% completed" else "Planned trail",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.76f)
                )
            }
        }
    }
}

@Composable
private fun TrailMetric(
    icon: ImageVector,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = Color.White.copy(alpha = 0.86f)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

@Composable
private fun EmptyTrailCard(message: String = "No active trail. Search and set one!") {
    ScoutyPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        startColor = MaterialTheme.colorScheme.surface,
        endColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecommendedTrailCard(
    recommendation: RouteRecommendation
) {
    val difficulty = recommendation.difficulty.name
    val info = buildString {
        append(recommendation.secondarySummary)
        recommendation.proximityKm?.let {
            if (length > 0) append(" · ")
            append(String.format(Locale.US, "%.0f km distanta", it))
        }
    }

    ScoutyPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Terrain,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recommendation.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = info,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (recommendation.whyItFits.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = recommendation.whyItFits,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            StatusChip(
                text = difficulty,
                containerColor = difficultyChipColors(difficulty).first,
                contentColor = difficultyChipColors(difficulty).second
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
}

private data class WeatherSnapshot(
    val temperature: String,
    val summary: String,
    val icon: ImageVector,
    val iconTint: Color
)

private fun buildWeatherSnapshot(status: HomeStatus): WeatherSnapshot {
    val parts = status.activeTrail?.weatherForecast?.split(",", limit = 2).orEmpty()
    val temperature = parts.getOrNull(0)?.trim().takeUnless { it.isNullOrBlank() } ?: "12°C"
    val summary = parts.getOrNull(1)?.trim().takeUnless { it.isNullOrBlank() } ?: "Partly cloudy"
    val lowercaseSummary = summary.lowercase(Locale.getDefault())

    val icon = when {
        "storm" in lowercaseSummary || "thunder" in lowercaseSummary -> Icons.Default.Thunderstorm
        "clear" in lowercaseSummary || "sun" in lowercaseSummary -> Icons.Default.WbSunny
        else -> Icons.Default.Cloud
    }
    val tint = when (icon) {
        Icons.Default.Thunderstorm -> Color(0xFFFFB020)
        Icons.Default.WbSunny -> Color(0xFFFFD166)
        else -> Color(0xFFE7EAFF)
    }

    return WeatherSnapshot(
        temperature = temperature,
        summary = summary,
        icon = icon,
        iconTint = tint
    )
}

private fun formatDistance(distanceKm: Double): String =
    String.format(Locale.getDefault(), "%.1f km", distanceKm)

private fun formatCoordinate(value: Double?, positiveDirection: String, negativeDirection: String): String =
    value?.let {
        val direction = if (it >= 0.0) positiveDirection else negativeDirection
        String.format(Locale.US, "%.4f° %s", abs(it), direction)
    } ?: "---"

private fun difficultyChipColors(difficulty: String): Pair<Color, Color> =
    when (difficulty.uppercase(Locale.ROOT)) {
        "EXPERT" -> Color(0xFF8E1C2B).copy(alpha = 0.88f) to Color(0xFFFFD5D5)
        "HARD" -> Color(0xFF3E6B2D).copy(alpha = 0.9f) to Color.White
        "MEDIUM" -> Color(0xFF7D4C12).copy(alpha = 0.88f) to Color(0xFFFFCC80)
        else -> Color(0xFF195B3B).copy(alpha = 0.9f) to Color(0xFFCFF7DE)
    }
