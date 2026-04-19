package com.scouty.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Phone
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.X
import com.scouty.app.ui.components.ScoutyCard
import com.scouty.app.ui.components.ScoutySectionHeader
import com.scouty.app.ui.theme.AccentGreen
import com.scouty.app.ui.theme.AccentGreenBg
import com.scouty.app.ui.theme.BgPrimary
import com.scouty.app.ui.theme.BorderDefault
import com.scouty.app.ui.theme.Danger
import com.scouty.app.ui.theme.JetBrainsMonoFamily
import com.scouty.app.ui.theme.TextPrimary
import com.scouty.app.ui.theme.TextSecondary
import com.scouty.app.ui.theme.TextTertiary
import com.scouty.app.ui.theme.Warning

@Composable
fun SosScreen(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SosHeader()
        Text(
            text = "Tine apasat 3 secunde pentru a trimite locatia ta GPS la Salvamont",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        SosButton()
        YourLocationCard()
        ScoutySectionHeader(title = "EMERGENCY CONTACTS")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EmergencyContactRow(
                name = "Salvamont Romania",
                subtitle = "Mountain rescue · 24/7",
                action = "0SALVAMONT",
                semantic = Danger,
                isPersonal = false,
            )
            EmergencyContactRow(
                name = "Emergency (Urgente)",
                subtitle = "Police · Fire · Ambulance",
                action = "112",
                semantic = Danger,
                isPersonal = false,
            )
            EmergencyContactRow(
                name = "Ion (Personal contact)",
                subtitle = "Last sent: Never",
                action = "Offline SMS",
                semantic = AccentGreen,
                isPersonal = true,
                initials = "Io",
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SosHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Danger.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Lucide.TriangleAlert,
                    contentDescription = null,
                    tint = Danger,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "EMERGENCY",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Danger,
                    letterSpacing = 0.3.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Salvamont · Romania",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(0.5.dp, BorderDefault, RoundedCornerShape(10.dp))
                .clickable { },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Lucide.X,
                contentDescription = "Close",
                tint = TextSecondary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun SosButton() {
    val transition = rememberInfiniteTransition(label = "sosPulse")
    val ringAlpha = transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ringAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .alpha(ringAlpha.value)
                .clip(CircleShape)
                .background(Danger.copy(alpha = 0.06f)),
        )
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(Danger.copy(alpha = 0.1f))
                .border(0.5.dp, Danger.copy(alpha = 0.2f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFA32D2D), Color(0xFF791F1F)),
                    ),
                )
                .clickable { },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "SOS",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "HOLD 3s",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

@Composable
private fun YourLocationCard() {
    ScoutyCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Lucide.MapPin,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "YOUR LOCATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentGreenBg)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "GPS FIX",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = AccentGreen,
                    letterSpacing = 0.6.sp,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LocationCell(
                modifier = Modifier.weight(1f),
                label = "LATITUDE",
                value = "45.4523° N",
            )
            LocationCell(
                modifier = Modifier.weight(1f),
                label = "LONGITUDE",
                value = "25.5432° E",
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LocationCell(
                modifier = Modifier.weight(1f),
                label = "ALTITUDE",
                value = "2,341 m",
                trailing = "±15m",
            )
            LocationCell(
                modifier = Modifier.weight(1f),
                label = "ACCURACY",
                value = "±8 m",
                trailing = "GPS",
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Warning.copy(alpha = 0.08f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Lucide.Info,
                contentDescription = null,
                tint = Warning,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Offline · Last signal 2h 14m ago",
                fontSize = 11.sp,
                color = Warning,
            )
        }
    }
}

@Composable
private fun LocationCell(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    trailing: String? = null,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.5.sp),
            color = TextTertiary,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                fontFamily = JetBrainsMonoFamily,
            )
            if (trailing != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = trailing,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = JetBrainsMonoFamily,
                    modifier = Modifier.padding(bottom = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun EmergencyContactRow(
    name: String,
    subtitle: String,
    action: String,
    semantic: Color,
    isPersonal: Boolean,
    initials: String? = null,
) {
    ScoutyCard(
        modifier = Modifier.fillMaxWidth(),
        semantic = semantic,
        contentPadding = PaddingValues(12.dp),
        onClick = { },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(semantic.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                if (isPersonal && initials != null) {
                    Text(
                        text = initials,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = semantic,
                    )
                } else {
                    Icon(
                        imageVector = Lucide.Phone,
                        contentDescription = null,
                        tint = semantic,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = TextTertiary,
                )
            }
            Spacer(Modifier.width(8.dp))
            ContactActionPill(action = action, isPersonal = isPersonal)
        }
    }
}

@Composable
private fun ContactActionPill(action: String, isPersonal: Boolean) {
    val (bg, fg) = if (isPersonal) {
        AccentGreenBg to AccentGreen
    } else {
        Danger to Color.White
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = action,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = fg,
            letterSpacing = 0.4.sp,
        )
    }
}

