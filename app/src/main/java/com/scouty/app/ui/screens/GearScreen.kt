package com.scouty.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scouty.app.R
import com.scouty.app.ui.components.ScoutyPanel
import com.scouty.app.ui.components.StatusChip
import com.scouty.app.ui.models.GearItem
import com.scouty.app.ui.models.GearNecessity
import com.scouty.app.ui.models.HomeStatus
import java.util.Locale

@Composable
fun GearScreen(
    status: HomeStatus,
    onToggleItem: (String) -> Unit,
    contentPadding: PaddingValues
) {
    val gearList = status.gearList
    val packedCount = gearList.count { it.isPacked }
    val totalCount = gearList.size
    val totalWeightGrams = gearList.sumOf { it.weightGrams ?: 0 }
    val missingMandatoryCount = gearList.count {
        it.necessity == GearNecessity.MANDATORY && !it.isPacked
    }
    val difficultyLabel = status.activeTrail?.difficulty ?: "GENERAL"
    val categories = gearList.map { it.category }.distinct()
    val listKey = gearList.joinToString("|") { "${it.id}:${it.isPacked}" }
    val expandedState = remember(status.activeTrail?.name, status.activeTrail?.difficulty, listKey) {
        mutableStateMapOf<String, Boolean>().apply {
            categories.forEachIndexed { index, category ->
                val hasMissingMandatory = gearList.any {
                    it.category == category &&
                        it.necessity == GearNecessity.MANDATORY &&
                        !it.isPacked
                }
                this[category] = hasMissingMandatory || index == 0
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.gear_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = status.activeTrail?.name ?: "Kit de baza pentru drumetie",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusChip(
                text = difficultyLabel.uppercase(Locale.getDefault()),
                containerColor = difficultyBadgeColor(difficultyLabel).copy(alpha = 0.18f),
                contentColor = difficultyBadgeColor(difficultyLabel)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        GearSummaryCard(
            packedCount = packedCount,
            totalCount = totalCount,
            totalWeightGrams = totalWeightGrams,
            missingMandatoryCount = missingMandatoryCount,
            hasActiveTrail = status.activeTrail != null
        )

        Spacer(modifier = Modifier.height(18.dp))

        categories.forEach { category ->
            val itemsInCategory = gearList.filter { it.category == category }
            val packedInCategory = itemsInCategory.count { it.isPacked }
            val totalInCategory = itemsInCategory.size
            val missingMandatoryInCategory = itemsInCategory.count {
                it.necessity == GearNecessity.MANDATORY && !it.isPacked
            }
            val isExpanded = expandedState[category] == true

            GearCategoryCard(
                title = category,
                icon = categoryIcon(category),
                accentColor = categoryAccentColor(category),
                status = "$packedInCategory/$totalInCategory packed",
                summary = when {
                    missingMandatoryInCategory > 0 -> "$missingMandatoryInCategory left"
                    totalInCategory - packedInCategory > 0 -> "${totalInCategory - packedInCategory} left"
                    else -> "ready"
                },
                summaryContainer = when {
                    missingMandatoryInCategory > 0 -> Color(0xFF3A2A12)
                    totalInCategory - packedInCategory > 0 -> Color.White.copy(alpha = 0.08f)
                    else -> Color(0xFF173A24)
                },
                summaryContent = when {
                    missingMandatoryInCategory > 0 -> Color(0xFFFFB020)
                    totalInCategory - packedInCategory > 0 -> MaterialTheme.colorScheme.onSurface
                    else -> Color(0xFF7BE5A3)
                },
                expanded = isExpanded,
                completed = packedInCategory == totalInCategory,
                onToggle = { expandedState[category] = !isExpanded }
            ) {
                itemsInCategory.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    }
                    GearItemRow(
                        item = item,
                        onToggle = { onToggleItem(item.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun GearSummaryCard(
    packedCount: Int,
    totalCount: Int,
    totalWeightGrams: Int,
    missingMandatoryCount: Int,
    hasActiveTrail: Boolean
) {
    val progress = if (totalCount > 0) packedCount.toFloat() / totalCount else 0f

    ScoutyPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        startColor = MaterialTheme.colorScheme.surface,
        endColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = packedCount.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = " / $totalCount items",
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatWeight(totalWeightGrams),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "total weight",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (hasActiveTrail) {
                "Checklist-ul este adaptat la traseul selectat si starea curenta a echiparii."
            } else {
                "Lista generala; se rafineaza automat cand alegi un traseu."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(18.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (missingMandatoryCount > 0) Color(0xFFFF7A59) else MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.08f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Ready: ${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (missingMandatoryCount > 0) {
                    "$missingMandatoryCount items missing"
                } else {
                    "All mandatory items packed"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (missingMandatoryCount > 0) Color(0xFFFFB020) else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun GearCategoryCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    status: String,
    summary: String,
    summaryContainer: Color,
    summaryContent: Color,
    expanded: Boolean,
    completed: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val rotation = animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "gearCategoryRotation"
    )

    ScoutyPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = accentColor.copy(alpha = 0.14f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = accentColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                StatusChip(
                    text = summary,
                    containerColor = summaryContainer,
                    contentColor = summaryContent
                )

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = if (completed) Icons.Default.Check else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (completed) 0f else rotation.value),
                    tint = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    content()
                }
            }
        }
    }
}

@Composable
private fun GearItemRow(
    item: GearItem,
    onToggle: () -> Unit
) {
    val necessityColors = necessityColors(item.necessity)
    val showBadge = !item.isPacked || item.necessity != GearNecessity.RECOMMENDED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(24.dp),
            shape = CircleShape,
            color = if (item.isPacked) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                Color.Transparent
            },
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (item.isPacked) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                }
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (item.isPacked) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Circle,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = Color.Transparent
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (item.isCritical) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (item.isPacked) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (item.isPacked) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = item.weight,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (showBadge) {
                Spacer(modifier = Modifier.height(8.dp))
                StatusChip(
                    text = necessityColors.label,
                    containerColor = necessityColors.container,
                    contentColor = necessityColors.content
                )
            }

            if (item.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun categoryIcon(category: String): ImageVector =
    when (category) {
        "Navigatie & energie" -> Icons.Default.Explore
        "Siguranta & urgenta" -> Icons.Default.HealthAndSafety
        "Imbracaminte & vreme" -> Icons.Default.Cloud
        "Apa & hrana" -> Icons.Default.Restaurant
        "Echipament tehnic" -> Icons.Default.Bolt
        else -> Icons.Default.Inventory2
    }

private fun categoryAccentColor(category: String): Color =
    when (category) {
        "Navigatie & energie" -> Color(0xFF67A6FF)
        "Siguranta & urgenta" -> Color(0xFFFF7A59)
        "Imbracaminte & vreme" -> Color(0xFFFFC05A)
        "Apa & hrana" -> Color(0xFF7BE5A3)
        "Echipament tehnic" -> Color(0xFF8FE08B)
        else -> Color(0xFF9CAAA0)
    }

private data class NecessityColors(
    val label: String,
    val container: Color,
    val content: Color
)

private fun necessityColors(necessity: GearNecessity): NecessityColors =
    when (necessity) {
        GearNecessity.MANDATORY -> NecessityColors(
            label = "Obligatoriu",
            container = Color(0xFFFDE2E1),
            content = Color(0xFFB42318)
        )
        GearNecessity.RECOMMENDED -> NecessityColors(
            label = "Recomandat",
            container = Color(0xFFDFF4E8),
            content = Color(0xFF166534)
        )
        GearNecessity.CONDITIONAL -> NecessityColors(
            label = "Conditional",
            container = Color(0xFFFDEFD8),
            content = Color(0xFFB45309)
        )
    }

private fun difficultyBadgeColor(difficulty: String): Color =
    when (difficulty.uppercase(Locale.ROOT)) {
        "EXPERT" -> Color(0xFFB42318)
        "HARD" -> Color(0xFFD97706)
        "MEDIUM" -> Color(0xFF1D4ED8)
        "EASY" -> Color(0xFF15803D)
        else -> Color(0xFF0F766E)
    }

private fun formatWeight(totalWeightGrams: Int): String =
    if (totalWeightGrams >= 1000) {
        String.format(Locale.US, "%.1f kg", totalWeightGrams / 1000f)
    } else {
        "$totalWeightGrams g"
    }
