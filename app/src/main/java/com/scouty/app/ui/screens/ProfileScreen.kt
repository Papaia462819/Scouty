@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.scouty.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.Compass
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mountain
import com.composables.icons.lucide.Route
import com.composables.icons.lucide.ShieldPlus
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scouty.app.profile.ProfileAssessmentEngine
import com.scouty.app.profile.ProfileProgressionEngine
import com.scouty.app.profile.ProfileTrailRecord
import com.scouty.app.profile.ProfileTrailOutcome
import com.scouty.app.profile.TrailStatsSummary
import com.scouty.app.profile.UserProfile
import com.scouty.app.ui.components.DangerButton
import com.scouty.app.ui.components.PrimaryButton
import com.scouty.app.ui.components.RouteRemoteImage
import com.scouty.app.ui.components.ScoutyCard
import com.scouty.app.ui.components.ScoutySectionHeader
import com.scouty.app.ui.components.StatusChip
import com.scouty.app.ui.components.StatusPill
import com.scouty.app.ui.models.HomeStatus
import com.scouty.app.ui.theme.AccentGreen
import com.scouty.app.ui.theme.BgSurface
import com.scouty.app.ui.theme.BgSurfaceRaised
import com.scouty.app.ui.theme.BorderSubtle
import com.scouty.app.ui.theme.Info
import com.scouty.app.ui.theme.TextPrimary
import com.scouty.app.ui.theme.TextSecondary
import com.scouty.app.ui.theme.TextTertiary
import com.scouty.app.ui.theme.Warning
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import com.composables.icons.lucide.Lock
import java.text.SimpleDateFormat
import java.util.Calendar
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
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Text(
                text = "Edit",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = AccentGreen,
                modifier = Modifier
                    .clickable(onClick = onEditProfile)
                    .padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        ScoutyCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarBadge(avatarId = profile.avatarId, size = 64.dp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = profile.homeRegion.ifBlank { "Home region not set" },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Member since $memberSince",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                    }
                }
                StatusPill(
                    text = levelProgress.level.title.uppercase(Locale.ENGLISH),
                    color = AccentGreen
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { levelProgress.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = AccentGreen,
                trackColor = BorderSubtle
            )
            Spacer(modifier = Modifier.height(8.dp))
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
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = levelProgress.nextLevel?.let { "${levelProgress.pointsRemaining} to ${it.title}" }
                        ?: "Top tier reached",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentGreen
                )
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
                valueColor = AccentGreen
            )
            ProfileStatCard(
                modifier = Modifier.weight(1f),
                value = formatDistanceStat(profileStats.totalDistanceKm),
                label = "km",
                valueColor = Info
            )
            ProfileStatCard(
                modifier = Modifier.weight(1f),
                value = formatElevationStat(profileStats.totalElevationGainM),
                label = "m gained",
                valueColor = Warning
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        ScoutySectionHeader(title = "PERFORMANCE")
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

        ScoutySectionHeader(title = "HISTORY")
        Spacer(modifier = Modifier.height(12.dp))
        HistoryCard(
            modifier = Modifier.fillMaxWidth(),
            entries = historyEntries,
            selectedIndex = historyIndex,
            onSelectPrevious = { historyIndex = (historyIndex - 1).coerceAtLeast(0) },
            onSelectNext = { historyIndex = (historyIndex + 1).coerceAtMost(historyEntries.lastIndex) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        ScoutySectionHeader(title = "ACHIEVEMENTS")
        Spacer(modifier = Modifier.height(12.dp))
        MiniTileCarousel(
            modifier = Modifier.fillMaxWidth(),
            tiles = achievements.map { it.toMiniTile() }
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrimaryButton(
                text = "Edit profile",
                onClick = onEditProfile,
                modifier = Modifier.weight(1f),
            )
            DangerButton(
                text = "Sign out",
                onClick = onSignOut,
                modifier = Modifier.weight(1f),
            )
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
    val chartFrame = remember(period, metric, snapshot) {
        PerformanceChartFrame(
            period = period,
            metric = metric,
            snapshot = snapshot
        )
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BgSurface)
            .border(0.5.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp)
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = snapshot.summaryValue,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 30.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = snapshot.summaryCaption,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Text(
                text = snapshot.intervalLabel,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                maxLines = 1
            )
        }

        SegmentedRow(
            options = listOf(
                ProfileChartPeriod.DAY,
                ProfileChartPeriod.WEEK,
                ProfileChartPeriod.MONTH,
                ProfileChartPeriod.YEAR
            ),
            selected = period,
            accentColor = metric.accentColor,
            onSelect = onPeriodChange,
            labelFor = { it.title }
        )

        SegmentedRow(
            options = listOf(
                ProfileChartMetric.KILOMETERS,
                ProfileChartMetric.TRAILS,
                ProfileChartMetric.ELEVATION
            ),
            selected = metric,
            accentColor = metric.accentColor,
            onSelect = onMetricChange,
            labelFor = { it.title }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BgSurfaceRaised)
        ) {
            AnimatedContent(
                targetState = chartFrame,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val sameChartMode = targetState.period == initialState.period &&
                        targetState.metric == initialState.metric
                    val targetStart = targetState.snapshot.intervalStartEpochMillis
                    val initialStart = initialState.snapshot.intervalStartEpochMillis
                    val isIntervalSwipe = sameChartMode && targetStart != initialStart

                    if (isIntervalSwipe) {
                        val slideDistance: (Int) -> Int = { fullWidth -> fullWidth / 3 }
                        val incomingOffset = if (targetStart < initialStart) {
                            { fullWidth: Int -> -slideDistance(fullWidth) }
                        } else {
                            { fullWidth: Int -> slideDistance(fullWidth) }
                        }
                        val outgoingOffset = if (targetStart < initialStart) {
                            { fullWidth: Int -> slideDistance(fullWidth) }
                        } else {
                            { fullWidth: Int -> -slideDistance(fullWidth) }
                        }

                        (
                            slideInHorizontally(
                                animationSpec = tween(
                                    durationMillis = 340,
                                    easing = FastOutSlowInEasing
                                ),
                                initialOffsetX = incomingOffset
                            ) + fadeIn(animationSpec = tween(durationMillis = 160))
                        ) togetherWith (
                            slideOutHorizontally(
                                animationSpec = tween(
                                    durationMillis = 300,
                                    easing = FastOutSlowInEasing
                                ),
                                targetOffsetX = outgoingOffset
                            ) + fadeOut(animationSpec = tween(durationMillis = 140))
                        ) using SizeTransform(clip = false)
                    } else {
                        fadeIn(animationSpec = tween(durationMillis = 140)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 90)) using SizeTransform(clip = false)
                    }
                },
                label = "performance_chart_transition"
            ) { frame ->
                PerformanceChartPlot(
                    modifier = Modifier.fillMaxSize(),
                    series = frame.snapshot.points,
                    metric = frame.metric
                )
            }
        }
    }
}

@Composable
private fun <T> SegmentedRow(
    options: List<T>,
    selected: T,
    accentColor: Color,
    onSelect: (T) -> Unit,
    labelFor: (T) -> String,
    modifier: Modifier = Modifier
) {
    val itemSpacing = 3.dp
    val optionCount = options.size.coerceAtLeast(1)
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val density = LocalDensity.current
    val itemSpacingPx = with(density) { itemSpacing.roundToPx() }
    var contentWidthPx by remember { mutableIntStateOf(0) }
    val segmentWidthPx = ((contentWidthPx - itemSpacingPx * (optionCount - 1)) / optionCount.toFloat())
        .coerceAtLeast(0f)
    val indicatorOffsetPx by animateFloatAsState(
        targetValue = (segmentWidthPx + itemSpacingPx) * selectedIndex,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "segmented_indicator_offset"
    )
    val indicatorColor by animateColorAsState(
        targetValue = accentColor.copy(alpha = 0.14f),
        animationSpec = tween(durationMillis = 180),
        label = "segmented_indicator_color"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(BgSurfaceRaised)
            .padding(3.dp)
            .onSizeChanged { contentWidthPx = it.width }
    ) {
        if (segmentWidthPx > 0f) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(indicatorOffsetPx.roundToInt(), 0) }
                    .width(with(density) { segmentWidthPx.toDp() })
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(indicatorColor)
            )
        }
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                val labelColor by animateColorAsState(
                    targetValue = if (isSelected) accentColor else TextSecondary,
                    animationSpec = tween(durationMillis = 180),
                    label = "segmented_label_color"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (option != selected) {
                                onSelect(option)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelFor(option),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = labelColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun PerformanceChartPlot(
    modifier: Modifier = Modifier,
    series: List<ProfilePerformancePoint>,
    metric: ProfileChartMetric
) {
    val maxValue = series.maxOfOrNull { it.value }?.takeIf { it > 0.0 } ?: 1.0

    if (series.all { it.value <= 0.0 }) {
        Box(
            modifier = modifier.padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No activity in this interval.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 18.sp
            )
        }
    } else {
        Row(
            modifier = modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            series.forEachIndexed { index, point ->
                AnimatedPerformanceBar(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    point = point,
                    metric = metric,
                    maxValue = maxValue,
                    index = index
                )
            }
        }
    }
}

@Composable
private fun AnimatedPerformanceBar(
    modifier: Modifier = Modifier,
    point: ProfilePerformancePoint,
    metric: ProfileChartMetric,
    maxValue: Double,
    index: Int
) {
    val targetFraction = (point.value / maxValue).toFloat().coerceIn(0.04f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = if (point.value > 0.0) targetFraction else 0f,
        animationSpec = tween(
            durationMillis = 420,
            delayMillis = index * 18,
            easing = FastOutSlowInEasing
        ),
        label = "performance_bar_height"
    )

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
                    .fillMaxHeight(animatedFraction)
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BgSurface)
                .border(0.5.dp, BorderSubtle, RoundedCornerShape(14.dp))
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
                .animateContentSize()
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
                            .padding(20.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Text(
                            text = "Trails finished from the map will land here."           ,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            lineHeight = 20.sp
                        )
                    }
                } else {
                    HistoryEntryContent(entry = entry)
                }
            }
        }

        if (selectedEntry != null && entries.size > 1) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedIndex + 1} / ${entries.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SubtleArrowButton(
                        icon = Lucide.ChevronLeft,
                        enabled = canGoPrevious,
                        onClick = onSelectPrevious
                    )
                    SubtleArrowButton(
                        icon = Lucide.ChevronRight,
                        enabled = canGoNext,
                        onClick = onSelectNext
                    )
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
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(BgSurface)
            .border(0.5.dp, BorderSubtle, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) TextSecondary else TextTertiary.copy(alpha = 0.4f),
            modifier = Modifier.size(14.dp)
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
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(valueColor.copy(alpha = 0.08f))
            .border(0.5.dp, valueColor.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            letterSpacing = (-0.5).sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label.uppercase(Locale.ENGLISH),
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
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
                    imageVector = Lucide.Route,
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
                        AccentGreen.copy(alpha = 0.2f)
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
                fontWeight = FontWeight.Medium,
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
                fontWeight = FontWeight.Medium,
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(tiles) { tile ->
            CompactProfileTile(tile = tile)
        }
        item {
            LockedAchievementTile()
        }
    }
}

@Composable
private fun CompactProfileTile(tile: ProfileMiniTileUi) {
    val bg = if (tile.isHighlighted) tile.accentColor.copy(alpha = 0.08f) else BgSurface
    val border = if (tile.isHighlighted) tile.accentColor.copy(alpha = 0.22f) else BorderSubtle
    Column(
        modifier = Modifier
            .width(112.dp)
            .height(128.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(0.5.dp, border, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tile.accentColor.copy(alpha = if (tile.isHighlighted) 0.18f else 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tile.icon,
                contentDescription = null,
                tint = if (tile.isHighlighted) tile.accentColor else tile.accentColor.copy(alpha = 0.45f),
                modifier = Modifier.size(16.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = tile.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = if (tile.isHighlighted) TextPrimary else TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = tile.subtitle.uppercase(Locale.ENGLISH),
                style = MaterialTheme.typography.labelSmall,
                color = if (tile.isHighlighted) tile.accentColor else TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LockedAchievementTile() {
    Column(
        modifier = Modifier
            .width(112.dp)
            .height(128.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BgSurfaceRaised)
            .border(
                BorderStroke(0.5.dp, BorderSubtle),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(TextTertiary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Lucide.Lock,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "More soon",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
            Text(
                text = "LOCKED",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
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

private data class PerformanceChartFrame(
    val period: ProfileChartPeriod,
    val metric: ProfileChartMetric,
    val snapshot: ProfilePerformanceSnapshot
)

private data class ProfilePerformanceSnapshot(
    val points: List<ProfilePerformancePoint>,
    val intervalKey: String,
    val intervalStartEpochMillis: Long,
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
    KILOMETERS("Km", Info),
    TRAILS("Trails", AccentGreen),
    ELEVATION("Elev", Warning);

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
        intervalStartEpochMillis = interval.start.timeInMillis,
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
            icon = Lucide.Compass,
            accentColor = Info
        ),
        ProfileAchievementUi(
            title = "First Aid Ready",
            subtitle = "Unlocked",
            unlocked = firstAidAnswer == "common_issues" || firstAidAnswer == "confident",
            icon = Lucide.ShieldPlus,
            accentColor = AccentGreen
        ),
        ProfileAchievementUi(
            title = "Weatherproof",
            subtitle = "Unlocked",
            unlocked = conditionsAnswer == "three_season" || conditionsAnswer == "winter",
            icon = Lucide.Cloud,
            accentColor = Warning
        ),
        ProfileAchievementUi(
            title = "Gear Ritual",
            subtitle = "Unlocked",
            unlocked = gearAnswer == "checklist" || gearAnswer == "route_tuned" || gearAnswer == "locked_in",
            icon = Lucide.ListChecks,
            accentColor = Warning
        ),
        ProfileAchievementUi(
            title = "100 km Club",
            subtitle = if (stats.totalDistanceKm >= 100.0) "Unlocked" else "In progress",
            unlocked = stats.totalDistanceKm >= 100.0,
            icon = Lucide.Route,
            accentColor = Info
        ),
        ProfileAchievementUi(
            title = "5K Climber",
            subtitle = if (stats.totalElevationGainM >= 5_000) "Unlocked" else "In progress",
            unlocked = stats.totalElevationGainM >= 5_000,
            icon = Lucide.Mountain,
            accentColor = Warning
        ),
        ProfileAchievementUi(
            title = "Trail Ledger",
            subtitle = if (profile.trailHistory.isNotEmpty()) "Unlocked" else "Locked",
            unlocked = profile.trailHistory.isNotEmpty(),
            icon = Lucide.Route,
            accentColor = AccentGreen
        ),
        ProfileAchievementUi(
            title = "Ridge Taste",
            subtitle = if (terrainAnswer == "ridge") "Unlocked" else "Locked",
            unlocked = terrainAnswer == "ridge",
            icon = Lucide.Mountain,
            accentColor = Warning
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
        "EASY" -> AccentGreen
        "HARD" -> Warning
        "EXPERT" -> Warning
        else -> Info
    }

private fun Double.formatOneDecimal(): String =
    String.format(Locale.ENGLISH, "%.1f", this)
