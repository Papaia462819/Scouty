@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.scouty.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
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
    val achievements = buildAchievements(profile, profileStats)
    var performancePeriod by rememberSaveable { mutableStateOf(ProfileChartPeriod.DAY) }
    var performanceMetric by rememberSaveable { mutableStateOf(ProfileChartMetric.KILOMETERS) }
    var performanceOffset by rememberSaveable { mutableIntStateOf(0) }
    val performanceSnapshot = remember(historyEntries, performancePeriod, performanceMetric, performanceOffset) {
        buildPerformanceSnapshot(
            entries = historyEntries,
            period = performancePeriod,
            metric = performanceMetric,
            intervalOffset = performanceOffset
        )
    }

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

        SectionHeader(title = "Performance")
        Spacer(modifier = Modifier.height(12.dp))
        PerformanceChartCard(
            modifier = Modifier.fillMaxWidth(),
            period = performancePeriod,
            metric = performanceMetric,
            snapshot = performanceSnapshot,
            onPeriodChange = {
                performancePeriod = it
                performanceOffset = 0
            },
            onMetricChange = { performanceMetric = it },
            onPreviousInterval = { performanceOffset += 1 },
            onNextInterval = {
                if (performanceOffset > 0) {
                    performanceOffset -= 1
                }
            }
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
private fun PerformanceChartCard(
    modifier: Modifier = Modifier,
    period: ProfileChartPeriod,
    metric: ProfileChartMetric,
    snapshot: ProfilePerformanceSnapshot,
    onPeriodChange: (ProfileChartPeriod) -> Unit,
    onMetricChange: (ProfileChartMetric) -> Unit,
    onPreviousInterval: () -> Unit,
    onNextInterval: () -> Unit
) {
    val series = snapshot.points
    val maxValue = series.maxOfOrNull { it.value }?.takeIf { it > 0.0 } ?: 1.0

    Surface(
        modifier = modifier
            .pointerInput(period, snapshot.intervalKey) {
                var dragDelta = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        dragDelta += dragAmount
                        when {
                            dragDelta >= 56f -> {
                                dragDelta = 0f
                                onPreviousInterval()
                            }

                            dragDelta <= -56f -> {
                                dragDelta = 0f
                                onNextInterval()
                            }
                        }
                    },
                    onDragEnd = { dragDelta = 0f },
                    onDragCancel = { dragDelta = 0f }
                )
            },
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = snapshot.summaryLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = snapshot.summaryValue,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = snapshot.summaryCaption,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = snapshot.intervalLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    ProfileChartPeriod.DAY,
                    ProfileChartPeriod.WEEK,
                    ProfileChartPeriod.MONTH,
                    ProfileChartPeriod.YEAR
                ).forEach { option ->
                    ProfileChartPill(
                        modifier = Modifier.weight(1f),
                        label = option.title,
                        selected = option == period,
                        accentColor = metric.accentColor,
                        onClick = { onPeriodChange(option) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    ProfileChartMetric.KILOMETERS,
                    ProfileChartMetric.TRAILS,
                    ProfileChartMetric.ELEVATION
                ).forEach { option ->
                    ProfileChartPill(
                        modifier = Modifier.weight(1f),
                        label = option.title,
                        selected = option == metric,
                        accentColor = option.accentColor,
                        onClick = { onMetricChange(option) }
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.54f)
            ) {
                if (series.all { it.value <= 0.0 }) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No activity in this interval.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        series.forEachIndexed { index, point ->
                            AnimatedPerformanceBar(
                                modifier = Modifier.weight(1f),
                                point = point,
                                metric = metric,
                                maxValue = maxValue,
                                animationKey = "${period.name}:${metric.name}:${snapshot.intervalKey}:${series.joinToString { it.value.toString() }}",
                                index = index
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileChartPill(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(38.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) accentColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) accentColor.copy(alpha = 0.42f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AnimatedPerformanceBar(
    modifier: Modifier = Modifier,
    point: ProfilePerformancePoint,
    metric: ProfileChartMetric,
    maxValue: Double,
    animationKey: String,
    index: Int
) {
    val animatedFraction = remember(animationKey, point.label) { Animatable(0f) }
    val targetFraction = (point.value / maxValue).toFloat().coerceIn(0.04f, 1f)

    LaunchedEffect(animationKey, point.value) {
        animatedFraction.snapTo(0f)
        delay(index * 42L)
        animatedFraction.animateTo(
            targetValue = if (point.value > 0.0) targetFraction else 0f,
            animationSpec = tween(
                durationMillis = 680,
                easing = FastOutSlowInEasing
            )
        )
    }

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            text = if (point.value > 0.0) metric.compactValueLabel(point.value) else "",
            style = MaterialTheme.typography.labelSmall,
            color = metric.accentColor,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .fillMaxHeight(animatedFraction.value)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                metric.accentColor,
                                metric.accentColor.copy(alpha = 0.48f)
                            )
                        )
                    )
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = point.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
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

private data class ProfileAchievementUi(
    val title: String,
    val subtitle: String,
    val unlocked: Boolean,
    val icon: ImageVector,
    val accentColor: Color
)

private data class ProfilePerformancePoint(
    val label: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val value: Double
)

private data class ProfilePerformanceSnapshot(
    val points: List<ProfilePerformancePoint>,
    val intervalKey: String,
    val intervalLabel: String,
    val summaryLabel: String,
    val summaryValue: String,
    val summaryCaption: String
)

private enum class ProfileChartPeriod(
    val title: String,
    val averageUnitName: String
) {
    DAY("D", "hour"),
    WEEK("W", "day"),
    MONTH("M", "week"),
    YEAR("Y", "month")
}

private enum class ProfileChartMetric(
    val title: String,
    val accentColor: Color
) {
    KILOMETERS("Km", StatusBlue),
    TRAILS("Trails", PrimaryGreen),
    ELEVATION("Elev", StatusOrange);

    fun valueFor(entries: List<ProfileTrailRecord>): Double =
        when (this) {
            KILOMETERS -> entries.sumOf(ProfileTrailRecord::distanceKm)
            TRAILS -> entries.size.toDouble()
            ELEVATION -> entries.sumOf { it.elevationGainM }.toDouble()
        }

    fun valueLabel(value: Double): String =
        when (this) {
            KILOMETERS -> "${value.formatOneDecimal()} km"
            TRAILS -> "${value.roundToInt()} trails"
            ELEVATION -> "+${value.roundToInt()} m"
        }

    fun compactValueLabel(value: Double): String =
        when (this) {
            KILOMETERS -> value.formatOneDecimal()
            TRAILS -> value.roundToInt().toString()
            ELEVATION -> if (value >= 1000.0) {
                "${(value / 1000.0).formatOneDecimal()}k"
            } else {
                value.roundToInt().toString()
            }
        }

    fun totalLabel(value: Double): String =
        when (this) {
            KILOMETERS -> "${value.formatOneDecimal()} km"
            TRAILS -> "${value.roundToInt()} trails"
            ELEVATION -> "+${value.roundToInt()} m"
        }

    fun averageLabel(value: Double): String =
        when (this) {
            KILOMETERS -> "${value.formatOneDecimal()} km"
            TRAILS -> String.format(Locale.ENGLISH, "%.1f trails", value)
            ELEVATION -> "+${value.roundToInt()} m"
        }
}

private fun ProfileAchievementUi.toMiniTile(): ProfileMiniTileUi =
    ProfileMiniTileUi(
        title = title,
        subtitle = subtitle,
        icon = icon,
        accentColor = accentColor,
        isHighlighted = unlocked
    )

private fun buildPerformanceSnapshot(
    entries: List<ProfileTrailRecord>,
    period: ProfileChartPeriod,
    metric: ProfileChartMetric,
    intervalOffset: Int
): ProfilePerformanceSnapshot {
    val interval = performanceInterval(period, intervalOffset)
    val points = buildPerformanceBuckets(period, interval.start, interval.end).map { bucket ->
        val bucketEntries = entries.filter { entry ->
            entry.completedAtEpochMillis in bucket.startEpochMillis until bucket.endEpochMillis
        }
        bucket.copy(value = metric.valueFor(bucketEntries))
    }
    val total = points.sumOf { it.value }
    val average = total / points.size.coerceAtLeast(1)
    val isCurrentDay = period == ProfileChartPeriod.DAY && intervalOffset == 0
    val summaryValue = if (isCurrentDay) {
        metric.totalLabel(total)
    } else {
        metric.averageLabel(average)
    }
    val summaryCaption = if (isCurrentDay) {
        "Today total"
    } else {
        "${period.averageUnitName.replaceFirstChar { it.uppercase(Locale.ENGLISH) }} average"
    }

    return ProfilePerformanceSnapshot(
        points = points,
        intervalKey = "${period.name}:${interval.start.timeInMillis}:${interval.end.timeInMillis}",
        intervalLabel = performanceIntervalLabel(interval.start, interval.end, period, intervalOffset),
        summaryLabel = if (isCurrentDay) "TOTAL" else "AVG",
        summaryValue = summaryValue,
        summaryCaption = summaryCaption
    )
}

private data class PerformanceInterval(
    val start: Calendar,
    val end: Calendar
)

private fun performanceInterval(period: ProfileChartPeriod, intervalOffset: Int): PerformanceInterval {
    val now = Calendar.getInstance(Locale.ENGLISH)
    val start = when (period) {
        ProfileChartPeriod.DAY -> startOfDay(now).also { it.add(Calendar.DAY_OF_MONTH, -intervalOffset) }
        ProfileChartPeriod.WEEK -> startOfWeek(now).also { it.add(Calendar.WEEK_OF_YEAR, -intervalOffset) }
        ProfileChartPeriod.MONTH -> startOfMonth(now).also { it.add(Calendar.MONTH, -intervalOffset) }
        ProfileChartPeriod.YEAR -> startOfYear(now).also { it.add(Calendar.YEAR, -intervalOffset) }
    }
    val end = start.cloneCalendar().apply {
        when (period) {
            ProfileChartPeriod.DAY -> add(Calendar.DAY_OF_MONTH, 1)
            ProfileChartPeriod.WEEK -> add(Calendar.WEEK_OF_YEAR, 1)
            ProfileChartPeriod.MONTH -> add(Calendar.MONTH, 1)
            ProfileChartPeriod.YEAR -> add(Calendar.YEAR, 1)
        }
    }
    return PerformanceInterval(start = start, end = end)
}

private fun buildPerformanceBuckets(
    period: ProfileChartPeriod,
    intervalStart: Calendar,
    intervalEnd: Calendar
): List<ProfilePerformancePoint> =
    when (period) {
        ProfileChartPeriod.DAY -> (0 until 12).map { index ->
            val start = intervalStart.cloneCalendar().apply { add(Calendar.HOUR_OF_DAY, index * 2) }
            val end = start.cloneCalendar().apply { add(Calendar.HOUR_OF_DAY, 2) }
            ProfilePerformancePoint(
                label = if (index % 3 == 0) "${index * 2}" else "",
                startEpochMillis = start.timeInMillis,
                endEpochMillis = end.timeInMillis,
                value = 0.0
            )
        }

        ProfileChartPeriod.WEEK -> (0 until 7).map { index ->
            val start = intervalStart.cloneCalendar().apply { add(Calendar.DAY_OF_MONTH, index) }
            val end = start.cloneCalendar().apply { add(Calendar.DAY_OF_MONTH, 1) }
            ProfilePerformancePoint(
                label = SimpleDateFormat("E", Locale.ENGLISH).format(start.time),
                startEpochMillis = start.timeInMillis,
                endEpochMillis = end.timeInMillis,
                value = 0.0
            )
        }

        ProfileChartPeriod.MONTH -> buildList {
            var cursor = intervalStart.cloneCalendar()
            while (cursor.before(intervalEnd)) {
                val start = cursor.cloneCalendar()
                val end = cursor.cloneCalendar().apply {
                    add(Calendar.DAY_OF_MONTH, 7)
                    if (after(intervalEnd)) {
                        timeInMillis = intervalEnd.timeInMillis
                    }
                }
                add(
                    ProfilePerformancePoint(
                        label = start.get(Calendar.DAY_OF_MONTH).toString(),
                        startEpochMillis = start.timeInMillis,
                        endEpochMillis = end.timeInMillis,
                        value = 0.0
                    )
                )
                cursor = end
            }
        }

        ProfileChartPeriod.YEAR -> (0 until 12).map { index ->
            val start = intervalStart.cloneCalendar().apply { add(Calendar.MONTH, index) }
            val end = start.cloneCalendar().apply { add(Calendar.MONTH, 1) }
            ProfilePerformancePoint(
                label = SimpleDateFormat("MMM", Locale.ENGLISH).format(start.time).take(1),
                startEpochMillis = start.timeInMillis,
                endEpochMillis = end.timeInMillis,
                value = 0.0
            )
        }
    }

private fun startOfDay(calendar: Calendar): Calendar =
    calendar.cloneCalendar().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

private fun startOfWeek(calendar: Calendar): Calendar =
    startOfDay(calendar).apply {
        firstDayOfWeek = Calendar.MONDAY
        while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            add(Calendar.DAY_OF_MONTH, -1)
        }
    }

private fun startOfMonth(calendar: Calendar): Calendar =
    startOfDay(calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }

private fun startOfYear(calendar: Calendar): Calendar =
    startOfDay(calendar).apply {
        set(Calendar.DAY_OF_YEAR, 1)
    }

private fun Calendar.cloneCalendar(): Calendar =
    (clone() as Calendar).apply {
        firstDayOfWeek = Calendar.MONDAY
    }

private fun performanceIntervalLabel(
    start: Calendar,
    end: Calendar,
    period: ProfileChartPeriod,
    intervalOffset: Int
): String =
    when (period) {
        ProfileChartPeriod.DAY -> if (intervalOffset == 0) {
            "Today"
        } else {
            SimpleDateFormat("d MMM", Locale.ENGLISH).format(start.time)
        }

        ProfileChartPeriod.WEEK -> {
            val endInclusive = end.cloneCalendar().apply { add(Calendar.DAY_OF_MONTH, -1) }
            "${SimpleDateFormat("d MMM", Locale.ENGLISH).format(start.time)} - ${
                SimpleDateFormat("d MMM", Locale.ENGLISH).format(endInclusive.time)
            }"
        }

        ProfileChartPeriod.MONTH -> SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(start.time)
        ProfileChartPeriod.YEAR -> SimpleDateFormat("yyyy", Locale.ENGLISH).format(start.time)
    }

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
