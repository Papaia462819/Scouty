package com.scouty.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mountain
import com.composables.icons.lucide.Route
import com.composables.icons.lucide.TrendingUp
import com.scouty.app.ui.theme.AccentGreen
import com.scouty.app.ui.theme.BgSurface
import com.scouty.app.ui.theme.BgSurfaceRaised
import com.scouty.app.ui.theme.BorderSubtle
import com.scouty.app.ui.theme.TextMuted
import com.scouty.app.ui.theme.TextPrimary
import com.scouty.app.ui.theme.TextSecondary
import com.scouty.app.ui.theme.TextTertiary
import java.util.Locale

typealias Difficulty = DifficultyLevel

enum class TrailThumbnail { Mountain, Forest, Sunset, Snow }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrailListItem(
    title: String,
    region: String?,
    durationLabel: String?,
    distanceLabel: String?,
    elevationLabel: String? = null,
    distanceAwayLabel: String? = null,
    difficulty: Difficulty?,
    thumbnail: TrailThumbnail = TrailThumbnail.Mountain,
    timeAgoLabel: String? = null,
    highlightQuery: String? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val containerColor by animateColorAsState(
        targetValue = if (isPressed) BgSurfaceRaised else BgSurface,
        label = "trailItemContainer"
    )
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor, shape)
            .border(0.5.dp, BorderSubtle, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            TrailThumbnailTile(thumbnail = thumbnail)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = highlightedTrailTitle(title, highlightQuery),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 17.sp
                        ),
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    when {
                        !timeAgoLabel.isNullOrBlank() -> TimeAgoLabel(timeAgoLabel)
                        difficulty != null -> DifficultyBadge(level = difficulty)
                    }
                }

                val metaItems = listOfNotNull(
                    durationLabel?.takeIf { it.isNotBlank() }?.let {
                        TrailMetaItem.IconMeta(Lucide.Clock, it)
                    },
                    distanceLabel?.takeIf { it.isNotBlank() }?.let {
                        TrailMetaItem.IconMeta(Lucide.Route, it)
                    },
                    elevationLabel?.takeIf { it.isNotBlank() }?.let {
                        TrailMetaItem.IconMeta(Lucide.TrendingUp, it)
                    },
                    distanceAwayLabel?.takeIf { it.isNotBlank() }?.let {
                        TrailMetaItem.DistanceAway(it)
                    }
                )

                if (metaItems.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        metaItems.forEachIndexed { index, item ->
                            TrailMetaChip(item)
                            if (index != metaItems.lastIndex) {
                                Text(
                                    text = "·",
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }

                if (!region.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = region,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TrailThumbnailTile(thumbnail: TrailThumbnail) {
    val colors = when (thumbnail) {
        TrailThumbnail.Mountain -> listOf(Color(0xFF2D3A4A), Color(0xFF4A6580))
        TrailThumbnail.Forest -> listOf(Color(0xFF1F3A2A), Color(0xFF3D6B4D))
        TrailThumbnail.Sunset -> listOf(Color(0xFF3A2A1A), Color(0xFF6B4D2D))
        TrailThumbnail.Snow -> listOf(Color(0xFF3A4658), Color(0xFF6F8398))
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Lucide.Mountain,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun TimeAgoLabel(label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Lucide.Clock,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(10.dp)
        )
        Text(text = label, fontSize = 10.sp, color = TextTertiary)
    }
}

@Composable
private fun TrailMetaChip(item: TrailMetaItem) {
    when (item) {
        is TrailMetaItem.DistanceAway -> Text(
            text = item.label,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = AccentGreen.copy(alpha = 0.75f)
        )

        is TrailMetaItem.IconMeta -> Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = TextSecondary.copy(alpha = 0.65f),
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = item.label,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                color = TextSecondary.copy(alpha = 0.65f)
            )
        }
    }
}

private fun highlightedTrailTitle(title: String, query: String?) = buildAnnotatedString {
    val needle = query?.trim().orEmpty()
    if (needle.isBlank()) {
        append(title)
        return@buildAnnotatedString
    }
    val start = title.lowercase(Locale.getDefault()).indexOf(needle.lowercase(Locale.getDefault()))
    if (start < 0) {
        append(title)
        return@buildAnnotatedString
    }
    append(title.substring(0, start))
    withStyle(SpanStyle(background = AccentGreen.copy(alpha = 0.2f), color = AccentGreen)) {
        append(title.substring(start, start + needle.length))
    }
    append(title.substring(start + needle.length))
}

private sealed interface TrailMetaItem {
    data class IconMeta(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String) : TrailMetaItem
    data class DistanceAway(val label: String) : TrailMetaItem
}
