@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.scouty.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scouty.app.profile.ProfileAssessmentEngine
import com.scouty.app.profile.ProfileProgressionEngine
import com.scouty.app.profile.ProfileTrailRecord
import com.scouty.app.profile.ProfileTrailOutcome
import com.scouty.app.profile.TrailStatsSummary
import com.scouty.app.profile.UserProfile
import com.scouty.app.ui.components.RouteRemoteImage
import com.scouty.app.ui.components.ScoutyPanel
import com.scouty.app.ui.components.SectionHeader
import com.scouty.app.ui.components.StatusChip
import com.scouty.app.ui.models.HomeStatus
import com.scouty.app.ui.theme.PrimaryGreen
import com.scouty.app.ui.theme.StatusAmber
import com.scouty.app.ui.theme.StatusBlue
import com.scouty.app.ui.theme.StatusOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ProfileScreen(
    contentPadding: PaddingValues,
    profile: UserProfile,
    status: HomeStatus,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit
) {
    val estimatedStats = ProfileAssessmentEngine.estimateTrailStats(profile.answers)
    val profileStats = resolveProfileStats(profile, estimatedStats)
    val levelProgress = ProfileProgressionEngine.progress(profile)
    val memberSince = formatMonthYear(profile.createdAtEpochMillis)
    val historyEntries = profile.trailHistory.sortedByDescending(ProfileTrailRecord::completedAtEpochMillis)
    val skills = buildProfileSkills(profile)
    val achievements = buildAchievements(profile, profileStats)

    var historyIndex by rememberSaveable(historyEntries.size) { mutableIntStateOf(0) }
    LaunchedEffect(historyEntries.size) {
        historyIndex = historyIndex.coerceIn(0, (historyEntries.lastIndex).coerceAtLeast(0))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Profile",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onEditProfile) {
                Text(text = "Edit", color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ScoutyPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            startColor = MaterialTheme.colorScheme.surface,
            endColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarBadge(avatarId = profile.avatarId, size = 72.dp)
                Spacer(modifier = Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = levelProgress.level.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${profile.homeRegion.ifBlank { "Home region not set" }} · Member since $memberSince",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { levelProgress.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (levelProgress.pointsRequired > 0) {
                                "${levelProgress.currentPoints} / ${levelProgress.pointsRequired} pts"
                            } else {
                                "${levelProgress.totalPoints} pts"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = levelProgress.nextLevel?.let { "${levelProgress.pointsRemaining} to ${it.title}" }
                                ?: "Top tier reached",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileStatCard(
                modifier = Modifier.weight(1f),
                value = profileStats.completedHikes.toString(),
                label = "Trails",
                valueColor = PrimaryGreen
            )
            ProfileStatCard(
                modifier = Modifier.weight(1f),
                value = formatDistanceStat(profileStats.totalDistanceKm),
                label = "km",
                valueColor = StatusBlue
            )
            ProfileStatCard(
                modifier = Modifier.weight(1f),
                value = formatElevationStat(profileStats.totalElevationGainM),
                label = "m gained",
                valueColor = StatusOrange
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(title = "Skills")
        Spacer(modifier = Modifier.height(12.dp))
        MiniTileCarousel(
            modifier = Modifier.fillMaxWidth(),
            tiles = skills
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(title = "History")
        Spacer(modifier = Modifier.height(12.dp))
        HistoryCard(
            modifier = Modifier.fillMaxWidth(),
            entries = historyEntries,
            selectedIndex = historyIndex,
            onSelectPrevious = { historyIndex = (historyIndex - 1).coerceAtLeast(0) },
            onSelectNext = { historyIndex = (historyIndex + 1).coerceAtMost(historyEntries.lastIndex) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(title = "Achievements")
        Spacer(modifier = Modifier.height(12.dp))
        MiniTileCarousel(
            modifier = Modifier.fillMaxWidth(),
            tiles = achievements.map { it.toMiniTile() }
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onEditProfile,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(text = "Edit Profile", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onSignOut,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1716))
            ) {
                Text(text = "Sign Out", fontWeight = FontWeight.Bold, color = Color(0xFFFFC8C0))
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun HistoryCard(
    modifier: Modifier,
    entries: List<ProfileTrailRecord>,
    selectedIndex: Int,
    onSelectPrevious: () -> Unit,
    onSelectNext: () -> Unit
) {
    val selectedEntry = entries.getOrNull(selectedIndex)
    val canGoPrevious = selectedIndex > 0
    val canGoNext = selectedIndex < entries.lastIndex

    Column(modifier = modifier) {
        if (selectedEntry != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedIndex + 1} / ${entries.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubtleArrowButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        enabled = canGoPrevious,
                        onClick = onSelectPrevious
                    )
                    SubtleArrowButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        enabled = canGoNext,
                        onClick = onSelectNext
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(248.dp)
                .pointerInput(entries.size, selectedIndex) {
                    if (entries.size <= 1) return@pointerInput
                    var dragDelta = 0f
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount ->
                            dragDelta += dragAmount
                            when {
                                dragDelta <= -52f && canGoNext -> {
                                    dragDelta = 0f
                                    onSelectNext()
                                }

                                dragDelta >= 52f && canGoPrevious -> {
                                    dragDelta = 0f
                                    onSelectPrevious()
                                }
                            }
                        },
                        onDragEnd = { dragDelta = 0f },
                        onDragCancel = { dragDelta = 0f }
                    )
                }
                .animateContentSize(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
            )
        ) {
            AnimatedContent(
                targetState = selectedIndex,
                transitionSpec = {
                    if (targetState >= initialState) {
                        (slideInHorizontally { fullWidth -> fullWidth / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { fullWidth -> -fullWidth / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally { fullWidth -> -fullWidth / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { fullWidth -> fullWidth / 3 } + fadeOut())
                    }.using(SizeTransform(clip = false))
                },
                label = "history_card_transition"
            ) { targetIndex ->
                val entry = entries.getOrNull(targetIndex)
                if (entry == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surface,
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            )
                            .padding(20.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Text(
                            text = "Trails finished from the map will land here.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp
                        )
                    }
                } else {
                    HistoryEntryContent(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun SubtleArrowButton(
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(28.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.72f else 0.24f)
        )
    }
}

@Composable
private fun ProfileStatCard(
    modifier: Modifier,
    value: String,
    label: String,
    valueColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistoryEntryContent(entry: ProfileTrailRecord) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val imageUrl = entry.imageUrl
        if (!imageUrl.isNullOrBlank()) {
            RouteRemoteImage(
                imageUrl = imageUrl,
                contentDescription = entry.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholderColor = MaterialTheme.colorScheme.surfaceVariant
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF23361F), Color(0xFF162414), Color(0xFF1B2A1A))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Route,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x22071008),
                            Color(0x55071008),
                            Color(0xD9071008)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusChip(
                    text = entry.difficulty.uppercase(Locale.getDefault()),
                    containerColor = difficultyColor(entry.difficulty).copy(alpha = 0.22f),
                    contentColor = Color.White
                )
                StatusChip(
                    text = if (entry.outcome == ProfileTrailOutcome.COMPLETED) "Finished" else "Ended early",
                    containerColor = if (entry.outcome == ProfileTrailOutcome.COMPLETED) {
                        PrimaryGreen.copy(alpha = 0.2f)
                    } else {
                        Color.White.copy(alpha = 0.14f)
                    },
                    contentColor = Color.White
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${entry.region} · ${formatHistoryDate(entry.completedAtEpochMillis)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatusChip(
                    text = "${entry.distanceKm.formatOneDecimal()} km",
                    containerColor = Color.White.copy(alpha = 0.12f),
                    contentColor = Color.White
                )
                StatusChip(
                    text = "+${entry.elevationGainM} m",
                    containerColor = Color.White.copy(alpha = 0.12f),
                    contentColor = Color.White
                )
                StatusChip(
                    text = entry.durationText,
                    containerColor = Color.White.copy(alpha = 0.12f),
                    contentColor = Color.White
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (entry.earnedPoints > 0) "+${entry.earnedPoints} pts" else "No XP",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (entry.earnedPoints > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White.copy(alpha = 0.78f)
                }
            )
        }
    }
}

@Composable
private fun MiniTileCarousel(
    modifier: Modifier = Modifier,
    tiles: List<ProfileMiniTileUi>
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tiles) { tile ->
            CompactProfileTile(tile = tile)
        }
    }
}

@Composable
private fun CompactProfileTile(tile: ProfileMiniTileUi) {
    Surface(
        modifier = Modifier
            .width(108.dp)
            .height(132.dp),
        shape = RoundedCornerShape(22.dp),
        color = if (tile.isHighlighted) {
            tile.accentColor.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (tile.isHighlighted) {
                tile.accentColor.copy(alpha = 0.26f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = tile.accentColor.copy(alpha = if (tile.isHighlighted) 0.18f else 0.1f)
            ) {
                Icon(
                    imageVector = tile.icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = tile.accentColor
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = tile.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tile.subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (tile.isHighlighted) tile.accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class ProfileMiniTileUi(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val isHighlighted: Boolean = true
)

private data class ProfileSkillUi(
    val title: String,
    val category: String,
    val icon: ImageVector,
    val accentColor: Color
)

private data class ProfileAchievementUi(
    val title: String,
    val subtitle: String,
    val unlocked: Boolean,
    val icon: ImageVector,
    val accentColor: Color
)

private fun ProfileSkillUi.toMiniTile(): ProfileMiniTileUi =
    ProfileMiniTileUi(
        title = title,
        subtitle = category,
        icon = icon,
        accentColor = accentColor
    )

private fun ProfileAchievementUi.toMiniTile(): ProfileMiniTileUi =
    ProfileMiniTileUi(
        title = title,
        subtitle = subtitle,
        icon = icon,
        accentColor = accentColor,
        isHighlighted = unlocked
    )

private fun buildProfileSkills(profile: UserProfile): List<ProfileMiniTileUi> =
    listOf(
        ProfileSkillUi(
            title = answerLabel(profile, "hike_frequency"),
            category = "Frequency",
            icon = Icons.Default.Route,
            accentColor = PrimaryGreen
        ),
        ProfileSkillUi(
            title = answerLabel(profile, "max_distance"),
            category = "Distance",
            icon = Icons.Default.Route,
            accentColor = StatusBlue
        ),
        ProfileSkillUi(
            title = answerLabel(profile, "physical_condition"),
            category = "Fitness",
            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
            accentColor = PrimaryGreen
        ),
        ProfileSkillUi(
            title = answerLabel(profile, "navigation"),
            category = "Navigation",
            icon = Icons.Default.Explore,
            accentColor = StatusBlue
        ),
        ProfileSkillUi(
            title = answerLabel(profile, "terrain"),
            category = "Terrain",
            icon = Icons.Default.Terrain,
            accentColor = StatusAmber
        ),
        ProfileSkillUi(
            title = answerLabel(profile, "conditions"),
            category = "Weather",
            icon = Icons.Default.Cloud,
            accentColor = StatusAmber
        ),
        ProfileSkillUi(
            title = answerLabel(profile, "gear_setup"),
            category = "Gear",
            icon = Icons.Default.Checklist,
            accentColor = StatusOrange
        ),
        ProfileSkillUi(
            title = answerLabel(profile, "first_aid"),
            category = "First Aid",
            icon = Icons.Default.HealthAndSafety,
            accentColor = PrimaryGreen
        )
    ).map(ProfileSkillUi::toMiniTile)

private fun buildAchievements(
    profile: UserProfile,
    stats: TrailStatsSummary
): List<ProfileAchievementUi> {
    val firstAidAnswer = profile.answers["first_aid"]
    val navigationAnswer = profile.answers["navigation"]
    val conditionsAnswer = profile.answers["conditions"]
    val gearAnswer = profile.answers["gear_setup"]
    val terrainAnswer = profile.answers["terrain"]

    return listOf(
        ProfileAchievementUi(
            title = "Route Brain",
            subtitle = "Unlocked",
            unlocked = navigationAnswer == "map_gps" || navigationAnswer == "independent",
            icon = Icons.Default.Explore,
            accentColor = StatusBlue
        ),
        ProfileAchievementUi(
            title = "First Aid Ready",
            subtitle = "Unlocked",
            unlocked = firstAidAnswer == "common_issues" || firstAidAnswer == "confident",
            icon = Icons.Default.HealthAndSafety,
            accentColor = PrimaryGreen
        ),
        ProfileAchievementUi(
            title = "Weatherproof",
            subtitle = "Unlocked",
            unlocked = conditionsAnswer == "three_season" || conditionsAnswer == "winter",
            icon = Icons.Default.Cloud,
            accentColor = StatusAmber
        ),
        ProfileAchievementUi(
            title = "Gear Ritual",
            subtitle = "Unlocked",
            unlocked = gearAnswer == "checklist" || gearAnswer == "route_tuned" || gearAnswer == "locked_in",
            icon = Icons.Default.Checklist,
            accentColor = StatusOrange
        ),
        ProfileAchievementUi(
            title = "100 km Club",
            subtitle = if (stats.totalDistanceKm >= 100.0) "Unlocked" else "In progress",
            unlocked = stats.totalDistanceKm >= 100.0,
            icon = Icons.Default.Route,
            accentColor = StatusBlue
        ),
        ProfileAchievementUi(
            title = "5K Climber",
            subtitle = if (stats.totalElevationGainM >= 5_000) "Unlocked" else "In progress",
            unlocked = stats.totalElevationGainM >= 5_000,
            icon = Icons.Default.Terrain,
            accentColor = StatusOrange
        ),
        ProfileAchievementUi(
            title = "Trail Ledger",
            subtitle = if (profile.trailHistory.isNotEmpty()) "Unlocked" else "Locked",
            unlocked = profile.trailHistory.isNotEmpty(),
            icon = Icons.Default.Route,
            accentColor = PrimaryGreen
        ),
        ProfileAchievementUi(
            title = "Ridge Taste",
            subtitle = if (terrainAnswer == "ridge") "Unlocked" else "Locked",
            unlocked = terrainAnswer == "ridge",
            icon = Icons.Default.Terrain,
            accentColor = StatusAmber
        )
    )
}

private fun resolveProfileStats(
    profile: UserProfile,
    estimatedStats: TrailStatsSummary
): TrailStatsSummary =
    TrailStatsSummary(
        completedHikes = profile.completedHikes.takeIf { it > 0 } ?: estimatedStats.completedHikes,
        totalDistanceKm = profile.totalDistanceKm.takeIf { it > 0.0 } ?: estimatedStats.totalDistanceKm,
        totalElevationGainM = profile.totalElevationGainM.takeIf { it > 0 } ?: estimatedStats.totalElevationGainM
    )

private fun answerLabel(profile: UserProfile, questionId: String): String =
    ProfileAssessmentEngine.answerLabel(questionId, profile.answers[questionId]) ?: "Not set"

private fun formatDistanceStat(distanceKm: Double): String =
    distanceKm.roundToInt().toString()

private fun formatElevationStat(elevationGainM: Int): String =
    "%,d".format(Locale.ENGLISH, elevationGainM)

private fun formatMonthYear(epochMillis: Long): String =
    SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(Date(epochMillis))

private fun formatHistoryDate(epochMillis: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(epochMillis))

private fun difficultyColor(difficulty: String): Color =
    when (difficulty.uppercase(Locale.getDefault())) {
        "EASY" -> PrimaryGreen
        "HARD" -> StatusAmber
        "EXPERT" -> StatusOrange
        else -> StatusBlue
    }

private fun Double.formatOneDecimal(): String =
    String.format(Locale.ENGLISH, "%.1f", this)
