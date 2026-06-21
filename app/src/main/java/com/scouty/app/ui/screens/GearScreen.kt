package com.scouty.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.Compass
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.ShieldPlus
import com.composables.icons.lucide.Utensils
import com.scouty.app.ui.components.CategoryIconTile
import com.scouty.app.ui.components.ScoutyCard
import com.scouty.app.ui.models.GearItem
import com.scouty.app.ui.models.GearNecessity
import com.scouty.app.ui.models.HomeStatus
import com.scouty.app.ui.theme.AccentGreen
import com.scouty.app.ui.theme.AccentGreenBg
import com.scouty.app.ui.theme.AccentGreenOnSurface
import com.scouty.app.ui.theme.BgPrimary
import com.scouty.app.ui.theme.BgSurfaceRaised
import com.scouty.app.ui.theme.BorderDefault
import com.scouty.app.ui.theme.BorderSubtle
import com.scouty.app.ui.theme.Danger
import com.scouty.app.ui.theme.Info as InfoBlue
import com.scouty.app.ui.theme.TextPrimary
import com.scouty.app.ui.theme.TextSecondary
import com.scouty.app.ui.theme.TextTertiary
import com.scouty.app.ui.theme.Warning
import com.scouty.app.ui.theme.Water
import java.util.Locale

@Composable
fun GearScreen(
    status: HomeStatus,
    onToggleItem: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val gearList = status.gearList
    val hasActiveTrail = status.activeTrail != null
    val packedCount = gearList.count { it.isPacked }
    val totalCount = gearList.size
    val totalWeightGrams = gearList.sumOf { it.weightGrams ?: 0 }
    val missingMandatoryCount = gearList.count {
        it.necessity == GearNecessity.MANDATORY && !it.isPacked
    }
    val activeFilter = remember { mutableStateOf("Toate") }
    val filteredGearList = remember(gearList, activeFilter.value) {
        when (activeFilter.value) {
            "Obligatorii" -> gearList.filter { it.necessity == GearNecessity.MANDATORY }
            "Lipsă" -> gearList.filter { !it.isPacked }
            else -> gearList
        }
    }
    val categories = filteredGearList.map { it.category }.distinct()
    val listKey = filteredGearList.joinToString("|") { "${it.id}:${it.isPacked}" }
    val expandedState = remember(status.activeTrail?.name, status.activeTrail?.difficulty, activeFilter.value, listKey) {
        mutableStateMapOf<String, Boolean>().apply {
            categories.forEachIndexed { index, category ->
                val hasMissingMandatory = filteredGearList.any {
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
            .background(BgPrimary)
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GearTopBar()
        GearSubtitle(status)

        if (!hasActiveTrail) {
            EmptyGearState()
        } else {
            GearSummaryCard(
                packedCount = packedCount,
                totalCount = totalCount,
                totalWeightGrams = totalWeightGrams,
                missingMandatoryCount = missingMandatoryCount,
            )
            GearFilterChips(active = activeFilter.value, onSelect = { activeFilter.value = it })

            if (filteredGearList.isEmpty()) {
                GearEmptyFilterState(activeFilter.value)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { category ->
                        val itemsInCategory = filteredGearList.filter { it.category == category }
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
                            status = "$packedInCategory / $totalInCategory pregătite",
                            countLeft = totalInCategory - packedInCategory,
                            hasMissingMandatory = missingMandatoryInCategory > 0,
                            completed = packedInCategory == totalInCategory,
                            expanded = isExpanded,
                            onToggle = { expandedState[category] = !isExpanded },
                        ) {
                            itemsInCategory.forEach { item ->
                                GearItemRow(item = item, onToggle = { onToggleItem(item.id) })
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun GearEmptyFilterState(activeFilter: String) {
    ScoutyCard(modifier = Modifier.fillMaxWidth(), semantic = AccentGreen) {
        Text(
            text = when (activeFilter) {
                "Obligatorii" -> "Nu sunt elemente obligatorii pentru filtrul curent."
                "Lipsă" -> "Nu ai elemente lipsă în listă."
                else -> "Lista este goală."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}

@Composable
private fun GearTopBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Listă echipament",
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(0.5.dp, BorderDefault, RoundedCornerShape(10.dp))
                .clickable { },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Lucide.EllipsisVertical,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun GearSubtitle(status: HomeStatus) {
    Column {
        Text(
            text = status.activeTrail?.name ?: "Selectează un traseu",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        status.activeTrail?.let { trail ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = trail.partyComposition.summaryRo,
                style = MaterialTheme.typography.bodySmall,
                color = AccentGreen,
            )
        }
    }
}

@Composable
private fun EmptyGearState() {
    ScoutyCard(modifier = Modifier.fillMaxWidth(), semantic = InfoBlue) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryIconTile(icon = Lucide.Package, color = InfoBlue)
            Text(
                text = "Echipamentul apare după ce setezi un traseu.",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Data, vremea și grupul vor genera automat lista potrivită.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun GearSummaryCard(
    packedCount: Int,
    totalCount: Int,
    totalWeightGrams: Int,
    missingMandatoryCount: Int,
) {
    val progress = if (totalCount > 0) packedCount.toFloat() / totalCount else 0f
    val readyPct = (progress * 100).toInt()

    ScoutyCard(modifier = Modifier.fillMaxWidth(), semantic = Warning) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = packedCount.toString(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium,
                        color = AccentGreen,
                        letterSpacing = (-0.8).sp,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "/ $totalCount elemente",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "PREGĂTIT: $readyPct%",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatWeightValue(totalWeightGrams),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        letterSpacing = (-0.3).sp,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "kg",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "GREUTATE TOTALĂ",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = AccentGreen,
            trackColor = BorderSubtle,
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Calculat din traseu, dată și grup",
                fontSize = 10.sp,
                color = TextTertiary,
            )
            if (missingMandatoryCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Warning.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "$missingMandatoryCount obligatorii lipsă",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Warning,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentGreenBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "Pregătit",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = AccentGreen,
                    )
                }
            }
        }
    }
}

@Composable
private fun GearFilterChips(active: String, onSelect: (String) -> Unit) {
    val filters = listOf("Toate", "Obligatorii", "Lipsă")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        filters.forEach { label ->
            val isActive = label == active
            val bg = if (isActive) AccentGreenBg else BgSurfaceRaised
            val fg = if (isActive) AccentGreen else TextSecondary
            val border = if (isActive) Color.Transparent else BorderDefault
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(bg)
                    .border(0.5.dp, border, RoundedCornerShape(20.dp))
                    .clickable { onSelect(label) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = fg,
                )
            }
        }
    }
}

@Composable
private fun GearCategoryCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    status: String,
    countLeft: Int,
    hasMissingMandatory: Boolean,
    completed: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val rotation = animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "gearCategoryRotation",
    )

    ScoutyCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        contentPadding = PaddingValues(0.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIconTile(icon = icon, color = accentColor)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                CountPill(
                    text = if (countLeft > 0) "$countLeft rămase" else "gata",
                    color = when {
                        hasMissingMandatory -> Warning
                        countLeft > 0 -> TextSecondary
                        else -> AccentGreen
                    },
                    background = when {
                        hasMissingMandatory -> Warning.copy(alpha = 0.15f)
                        countLeft > 0 -> BgSurfaceRaised
                        else -> AccentGreenBg
                    },
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = if (completed) Lucide.Check else Lucide.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(if (completed) 0f else rotation.value),
                    tint = if (completed) AccentGreen else TextTertiary,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                    content()
                }
            }
        }
    }
}

@Composable
private fun GearItemRow(item: GearItem, onToggle: () -> Unit) {
    val detailsExpanded = remember(item.id) { mutableStateOf(false) }
    val tagInfo = necessityTag(item.necessity)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ItemCheckbox(checked = item.isPacked)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (item.isPacked) TextSecondary else TextPrimary,
                        textDecoration = if (item.isPacked) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (tagInfo != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(tagInfo.bg)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = tagInfo.label,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                color = tagInfo.fg,
                                letterSpacing = 0.4.sp,
                            )
                        }
                    }
                }
                if (item.note.isNotBlank() && detailsExpanded.value) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = item.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = item.weight,
                fontSize = 10.sp,
                color = TextTertiary,
            )
            if (item.note.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { detailsExpanded.value = !detailsExpanded.value },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Lucide.Info,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemCheckbox(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .then(
                if (checked) {
                    Modifier.background(AccentGreen)
                } else {
                    Modifier.border(1.5.dp, TextTertiary, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Lucide.Check,
                contentDescription = null,
                tint = AccentGreenOnSurface,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun CountPill(text: String, color: Color, background: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

private fun categoryIcon(category: String): ImageVector =
    when (category) {
        "Bază traseu" -> Lucide.Package
        "Siguranță & navigație" -> Lucide.ShieldPlus
        "Straturi & vreme" -> Lucide.Cloud
        "Apă & hrană" -> Lucide.Utensils
        "Copii" -> Lucide.Compass
        else -> Lucide.Package
    }

private fun categoryAccentColor(category: String): Color =
    when (category) {
        "Bază traseu" -> InfoBlue
        "Siguranță & navigație" -> Danger
        "Straturi & vreme" -> Warning
        "Apă & hrană" -> Water
        "Copii" -> AccentGreen
        else -> TextSecondary
    }

private data class TagInfo(val label: String, val bg: Color, val fg: Color)

private fun necessityTag(necessity: GearNecessity): TagInfo? =
    when (necessity) {
        GearNecessity.MANDATORY -> TagInfo("OBLIGATORIU", Danger.copy(alpha = 0.15f), Danger)
        GearNecessity.CONDITIONAL -> TagInfo("OPȚIONAL", Warning.copy(alpha = 0.12f), Warning)
        GearNecessity.RECOMMENDED -> null
    }

private fun formatWeightValue(totalWeightGrams: Int): String =
    if (totalWeightGrams >= 1000) {
        String.format(Locale.US, "%.1f", totalWeightGrams / 1000f)
    } else {
        String.format(Locale.US, "%.2f", totalWeightGrams / 1000f)
    }
