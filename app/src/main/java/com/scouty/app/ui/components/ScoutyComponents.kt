package com.scouty.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scouty.app.ui.theme.AccentGreen
import com.scouty.app.ui.theme.AccentGreenBg
import com.scouty.app.ui.theme.AccentGreenBorder
import com.scouty.app.ui.theme.AccentGreenOnSurface
import com.scouty.app.ui.theme.BgSurface
import com.scouty.app.ui.theme.BgSurfaceRaised
import com.scouty.app.ui.theme.BorderDefault
import com.scouty.app.ui.theme.BorderSubtle
import com.scouty.app.ui.theme.Danger
import com.scouty.app.ui.theme.TextPrimary
import com.scouty.app.ui.theme.TextSecondary
import com.scouty.app.ui.theme.TextTertiary
import com.scouty.app.ui.theme.Warning

/**
 * Uppercase section header with optional right-aligned trailing slot
 * (typically "View all →" or a small count).
 */
@Composable
fun ScoutySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        if (trailing != null) trailing()
    }
}

/**
 * Convenience overload for "View all →" style trailing link in AccentGreen.
 */
@Composable
fun ScoutySectionHeader(
    title: String,
    trailingText: String,
    trailingColor: Color = AccentGreen,
    onTrailingClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    ScoutySectionHeader(
        title = title,
        modifier = modifier,
        trailing = {
            val clickable = if (onTrailingClick != null) {
                Modifier.clickable(onClick = onTrailingClick)
            } else {
                Modifier
            }
            Text(
                text = trailingText,
                modifier = clickable.padding(horizontal = 2.dp, vertical = 2.dp),
                fontSize = 11.sp,
                color = trailingColor,
                fontWeight = FontWeight.Medium,
            )
        },
    )
}

/**
 * Base card surface: subtle translucent bg, 0.5dp border, 14dp radius.
 * When [semantic] is provided, tints the bg to ~6% of that color and the
 * border to ~15%, producing a gentle accent without dominating.
 */
@Composable
fun ScoutyCard(
    modifier: Modifier = Modifier,
    semantic: Color? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp),
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(14.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bg = semantic?.copy(alpha = 0.06f) ?: BgSurface
    val border = semantic?.copy(alpha = 0.15f) ?: BorderSubtle
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Column(
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .border(0.5.dp, border, shape)
            .then(clickMod)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Compact numeric tile used in profile/stat grids and the map tracking sheet.
 * Layout: tiny uppercase label on top, large value with optional unit on the
 * same baseline, optional trailing accent.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    accent: Color? = null,
) {
    ScoutyCard(
        modifier = modifier,
        semantic = accent,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.5.sp),
            color = TextTertiary,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = accent ?: TextPrimary,
                letterSpacing = (-0.5).sp,
                lineHeight = 22.sp,
            )
            if (unit != null) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = unit,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}

enum class DifficultyLevel { EASY, MEDIUM, HARD }

@Composable
fun DifficultyBadge(level: DifficultyLevel, modifier: Modifier = Modifier) {
    val (color, text) = when (level) {
        DifficultyLevel.EASY -> AccentGreen to "EASY"
        DifficultyLevel.MEDIUM -> Warning to "MEDIUM"
        DifficultyLevel.HARD -> Danger to "HARD"
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp,
            color = color,
        )
    }
}

/**
 * Rounded status pill with a colored leading dot. The dot gently pulses when
 * [pulsing] is true to indicate a live, ongoing state.
 */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
    backdrop: Color = color.copy(alpha = 0.1f),
) {
    val pulseAlpha: Float = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val state = transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseAlpha",
        )
        state.value
    } else {
        1f
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backdrop)
            .border(0.5.dp, color.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .alpha(pulseAlpha)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            letterSpacing = 0.3.sp,
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AccentGreen.copy(alpha = alpha))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentGreenOnSurface,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = AccentGreenOnSurface,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            letterSpacing = 0.2.sp,
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Transparent)
            .border(0.5.dp, BorderDefault, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp)
            .alpha(alpha),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            letterSpacing = 0.2.sp,
        )
    }
}

@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Danger.copy(alpha = 0.1f * alpha))
            .border(0.5.dp, Danger.copy(alpha = 0.3f * alpha), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Danger,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = Danger,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            letterSpacing = 0.2.sp,
        )
    }
}

/**
 * Square rounded icon tile tinted with a semantic color at low alpha,
 * used in quick actions, gear categories, and chat/sos contact rows.
 */
@Composable
fun CategoryIconTile(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: Dp = 18.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = color,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Minus / number / plus stepper used by the plan-trail modal.
 */
@Composable
fun QuantityStepper(
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = AccentGreen,
    minValue: Int = 0,
    maxValue: Int = 99,
    decrementIcon: ImageVector? = null,
    incrementIcon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BgSurfaceRaised),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(
            enabled = value > minValue,
            accent = accent,
            onClick = onDecrement,
            sign = "−",
            icon = decrementIcon,
        )
        Text(
            text = value.toString(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            letterSpacing = (-0.3).sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        StepperButton(
            enabled = value < maxValue,
            accent = accent,
            onClick = onIncrement,
            sign = "+",
            icon = incrementIcon,
        )
    }
}

@Composable
private fun StepperButton(
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit,
    sign: String,
    icon: ImageVector?,
) {
    val tint = if (enabled) accent else TextTertiary
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = if (enabled) 0.1f else 0.03f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = sign,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
        } else {
            Text(
                text = sign,
                color = tint,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
            )
        }
    }
}
