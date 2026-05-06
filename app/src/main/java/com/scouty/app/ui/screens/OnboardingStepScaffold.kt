package com.scouty.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.scouty.app.ui.components.PrimaryButton
import com.scouty.app.ui.components.SecondaryButton
import com.scouty.app.ui.theme.AccentGreen
import com.scouty.app.ui.theme.BgPrimary
import com.scouty.app.ui.theme.BorderDefault
import com.scouty.app.ui.theme.TextMuted
import com.scouty.app.ui.theme.TextPrimary
import com.scouty.app.ui.theme.TextSecondary
import com.scouty.app.ui.theme.TextTertiary

@Composable
fun OnboardingStepScaffold(
    stepNumber: Int,
    totalSteps: Int = 11,
    flowSubtitle: String = "Ajusteaza profilul Scouty",
    stepTitle: String,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    isContinueEnabled: Boolean,
    continueLabel: String = "Continua",
    backLabel: String = "Inapoi",
    showBack: Boolean = true,
    estimatedTimeRemaining: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val progressTarget = (stepNumber.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "onboardingProgress",
    )
    val continueIcon = if (continueLabel.contains("salveaza", ignoreCase = true)) {
        Lucide.Check
    } else {
        Lucide.ChevronRight
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .windowInsetsPadding(WindowInsets.statusBars)
            .navigationBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(top = 4.dp, bottom = 18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showBack) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(0.5.dp, BorderDefault, RoundedCornerShape(10.dp))
                        .background(Color.Transparent)
                        .clickable(onClick = onBack)
                        .padding(9.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Lucide.ChevronLeft,
                        contentDescription = backLabel,
                        tint = TextPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "PAS $stepNumber DIN $totalSteps",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentGreen,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "·",
                        color = TextMuted,
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = flowSubtitle,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stepTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (estimatedTimeRemaining != null) {
                Text(
                    text = estimatedTimeRemaining,
                    color = TextTertiary,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFFFFFFF).copy(alpha = 0.06f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .shadow(5.dp, RoundedCornerShape(2.dp))
                    .clip(RoundedCornerShape(2.dp))
                    .background(AccentGreen),
            )
        }

        Spacer(Modifier.height(18.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            content = content,
        )

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (showBack) {
                SecondaryButton(
                    text = backLabel,
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                )
            }
            PrimaryButton(
                text = continueLabel,
                icon = continueIcon,
                enabled = isContinueEnabled,
                onClick = onContinue,
                modifier = Modifier.weight(2f),
            )
        }
    }
}
