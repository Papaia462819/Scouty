package com.scouty.app.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.BatteryFull
import com.composables.icons.lucide.BatteryLow
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Phone
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.ShieldPlus
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.X
import com.scouty.app.profile.UserProfile
import com.scouty.app.sos.SosAction
import com.scouty.app.sos.SosContact
import com.scouty.app.sos.SosMessageBuilder
import com.scouty.app.sos.SosMessageInput
import com.scouty.app.sos.SosSettings
import com.scouty.app.sos.SosSettingsRepository
import com.scouty.app.ui.components.CategoryIconTile
import com.scouty.app.ui.components.PrimaryButton
import com.scouty.app.ui.components.QuantityStepper
import com.scouty.app.ui.components.ScoutyCard
import com.scouty.app.ui.components.ScoutySectionHeader
import com.scouty.app.ui.components.SecondaryButton
import com.scouty.app.ui.components.StatusPill
import com.scouty.app.ui.models.HomeStatus
import com.scouty.app.ui.theme.AccentGreen
import com.scouty.app.ui.theme.AccentGreenBg
import com.scouty.app.ui.theme.BgPrimary
import com.scouty.app.ui.theme.BgSurface
import com.scouty.app.ui.theme.BgSurfaceRaised
import com.scouty.app.ui.theme.BorderDefault
import com.scouty.app.ui.theme.BorderSubtle
import com.scouty.app.ui.theme.Danger
import com.scouty.app.ui.theme.Info
import com.scouty.app.ui.theme.JetBrainsMonoFamily
import com.scouty.app.ui.theme.TextPrimary
import com.scouty.app.ui.theme.TextSecondary
import com.scouty.app.ui.theme.TextTertiary
import com.scouty.app.ui.theme.Warning
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SosScreen(
    contentPadding: PaddingValues,
    status: HomeStatus,
    profile: UserProfile
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val repository = remember(context) { SosSettingsRepository(context.applicationContext) }
    var settings by remember { mutableStateOf(repository.load()) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFollowUpCall by rememberSaveable { mutableStateOf<String?>(null) }
    var waitingForExternalReturn by rememberSaveable { mutableStateOf(false) }
    var externalLaunchAt by rememberSaveable { mutableLongStateOf(0L) }

    val previewMessage = remember(status, profile, settings) {
        SosMessageBuilder.build(
            input = status.toSosMessageInput(profile.displayName),
            settings = settings
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, waitingForExternalReturn, pendingFollowUpCall, externalLaunchAt) {
        val observer = LifecycleEventObserver { _, event ->
            if (
                event == Lifecycle.Event.ON_RESUME &&
                waitingForExternalReturn &&
                pendingFollowUpCall != null &&
                SystemClock.elapsedRealtime() - externalLaunchAt > 700L
            ) {
                val number = pendingFollowUpCall
                pendingFollowUpCall = null
                waitingForExternalReturn = false
                if (number != null && openDialer(context, number)) {
                    statusMessage = "Dialer pregatit pentru ${displayDialNumber(number)}."
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(contentPadding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (showSettings) Modifier.blur(8.dp) else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            SosHeader(
                settings = settings,
                onSettingsClick = { showSettings = true }
            )
            SosHoldButton(
                holdSeconds = settings.holdSeconds,
                action = settings.action,
                onActivated = {
                    val message = SosMessageBuilder.build(
                        input = status.toSosMessageInput(profile.displayName, System.currentTimeMillis()),
                        settings = settings
                    )
                    val launchedText = if (settings.action.includesText) {
                        val recipients = settings.smsRecipients
                        if (recipients.isEmpty()) {
                            statusMessage = "Adauga contacte SMS in setarile SOS."
                            if (settings.action.callNumber == null) {
                                showSettings = true
                            }
                            false
                        } else {
                            openSmsComposer(context, recipients, message).also { launched ->
                                statusMessage = if (launched) {
                                    "Mesaj SOS pregatit pentru ${recipients.size} contact(e)."
                                } else {
                                    "Nu am gasit o aplicatie SMS disponibila."
                                }
                            }
                        }
                    } else {
                        false
                    }

                    val callNumber = settings.action.callNumber
                    when {
                        launchedText && callNumber != null -> {
                            pendingFollowUpCall = callNumber
                            waitingForExternalReturn = true
                            externalLaunchAt = SystemClock.elapsedRealtime()
                        }
                        !settings.action.includesText && callNumber != null -> {
                            if (openDialer(context, callNumber)) {
                                statusMessage = "Dialer pregatit pentru ${displayDialNumber(callNumber)}."
                            } else {
                                statusMessage = "Nu am putut deschide dialer-ul."
                            }
                        }
                        settings.action.includesText && !launchedText && callNumber != null -> {
                            if (openDialer(context, callNumber)) {
                                statusMessage = "SMS indisponibil. Dialer pregatit pentru ${displayDialNumber(callNumber)}."
                            }
                        }
                    }
                }
            )
            statusMessage?.let { message ->
                StatusNotice(message = message, onDismiss = { statusMessage = null })
            }
            YourLocationCard(status = status)
            Spacer(Modifier.height(10.dp))
            MessagePreviewCard(
                message = previewMessage,
                onCopy = {
                    clipboard.setText(AnnotatedString(previewMessage))
                    statusMessage = "Mesaj SOS copiat."
                }
            )
            Spacer(Modifier.height(8.dp))
        }

        if (showSettings) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.34f))
            )
            SosSettingsDialog(
                settings = settings,
                profile = profile,
                onDismiss = { showSettings = false },
                onSave = { updated ->
                    val normalized = updated.normalized()
                    repository.save(normalized)
                    settings = normalized
                    showSettings = false
                    statusMessage = "Setarile SOS au fost salvate."
                }
            )
        }
    }
}

@Composable
private fun SosHeader(
    settings: SosSettings,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Danger.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Lucide.TriangleAlert,
                    contentDescription = null,
                    tint = Danger,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = "EMERGENCY",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Danger,
                    letterSpacing = 0.3.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = topBarActionLabel(settings),
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
                .background(BgSurface)
                .clickable(onClick = onSettingsClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Lucide.Settings,
                contentDescription = "SOS settings",
                tint = TextPrimary,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
private fun SosHoldButton(
    holdSeconds: Int,
    action: SosAction,
    onActivated: () -> Unit
) {
    var isHolding by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var activationToken by remember { mutableLongStateOf(0L) }

    LaunchedEffect(activationToken) {
        if (activationToken > 0) {
            holdProgress = 1f
            delay(180)
            holdProgress = 0f
        }
    }

    val transition = rememberInfiniteTransition(label = "sosPulse")
    val ringAlpha = transition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ringAlpha",
    )
    val ringScale = transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ringScale",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp, bottom = 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .graphicsLayer {
                        scaleX = ringScale.value
                        scaleY = ringScale.value
                    }
                    .clip(CircleShape)
                    .background(Danger.copy(alpha = ringAlpha.value)),
            )
            Box(
                modifier = Modifier
                    .size(144.dp)
                    .clip(CircleShape)
                    .background(Danger.copy(alpha = 0.1f))
                    .border(0.5.dp, Danger.copy(alpha = 0.2f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        spotColor = Danger.copy(alpha = 0.5f)
                    )
                    .clip(CircleShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFC73D3C), Color(0xFF8A2625)),
                        ),
                    )
                    .pointerInput(holdSeconds) {
                        coroutineScope {
                            val gestureScope = this
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                isHolding = true
                                var activated = false
                                val start = SystemClock.elapsedRealtime()
                                val duration = holdSeconds * 1000L
                                val holdJob = gestureScope.launch {
                                    while (holdProgress < 1f) {
                                        val elapsed = SystemClock.elapsedRealtime() - start
                                        holdProgress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                                        if (elapsed >= duration) {
                                            activated = true
                                            activationToken = SystemClock.elapsedRealtime()
                                            onActivated()
                                            break
                                        }
                                        delay(16)
                                    }
                                }
                                waitForUpOrCancellation()
                                holdJob.cancel()
                                isHolding = false
                                if (!activated) {
                                    holdProgress = 0f
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(104.dp)) {
                    drawArc(
                        color = Danger.copy(alpha = 0.25f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                    if (holdProgress > 0f) {
                        drawArc(
                            color = Color.White.copy(alpha = 0.75f),
                            startAngle = -90f,
                            sweepAngle = 360f * holdProgress,
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "SOS",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (isHolding) "KEEP HOLDING" else "HOLD ${holdSeconds}s",
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        letterSpacing = 1.sp,
                    )
                }
            }
        }
        Text(
            text = holdHelperText(action),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 20.dp)
        )
    }
}

@Composable
private fun StatusNotice(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Info.copy(alpha = 0.08f))
            .border(0.5.dp, Info.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Lucide.Info,
            contentDescription = null,
            tint = Info,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = message,
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Lucide.X,
                contentDescription = "Dismiss",
                tint = TextTertiary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun YourLocationCard(status: HomeStatus) {
    val gpsState = locationPillState(status)
    ScoutyCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 0.dp),
        contentPadding = PaddingValues(14.dp)
    ) {
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
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(gpsState.color.copy(alpha = if (gpsState.color == AccentGreen) 0.08f else 0.1f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(gpsState.color)
                )
                Text(
                    text = gpsState.label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = gpsState.color,
                    letterSpacing = 0.6.sp,
                    lineHeight = 10.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LocationPairCell(
                modifier = Modifier.weight(1f),
                label = "LAT · LNG",
                primary = formatLatitude(status.latitude),
                secondary = formatLongitude(status.longitude),
            )
            LocationPairCell(
                modifier = Modifier.weight(1f),
                label = "ALT · ACC",
                primary = status.altitude?.let { "${it.roundToInt()} m ASL" } ?: "--",
                secondary = status.accuracy?.let { "±${it.roundToInt()} m GPS" } ?: "--",
                secondaryDimmed = true
            )
        }

        Spacer(Modifier.height(8.dp))

        val batteryColor = if (status.batteryPercent < 30) Warning else AccentGreen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(batteryColor.copy(alpha = 0.08f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (status.batteryPercent < 30) Lucide.BatteryLow else Lucide.BatteryFull,
                contentDescription = null,
                tint = batteryColor,
                modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Battery ${status.batteryPercent}% incluse in rescue packet",
                fontSize = 10.sp,
                color = batteryColor,
            )
        }
    }
}

@Composable
private fun LocationPairCell(
    modifier: Modifier = Modifier,
    label: String,
    primary: String,
    secondary: String,
    secondaryDimmed: Boolean = false
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.5.sp),
            color = TextTertiary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            fontFamily = JetBrainsMonoFamily,
            letterSpacing = (-0.2).sp,
            lineHeight = 18.sp
        )
        Text(
            text = secondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (secondaryDimmed) TextSecondary else TextPrimary,
            fontFamily = JetBrainsMonoFamily,
            letterSpacing = (-0.2).sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun MessagePreviewCard(message: String, onCopy: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1200)
            copied = false
        }
    }

    ScoutyCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentGreen.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Lucide.MessageSquare,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "RESCUE PACKET",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            letterSpacing = 0.2.sp
                        ),
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Text(
                        text = "SMS pregatit · ${message.length} char",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                PacketIconButton(
                    icon = if (copied) Lucide.Check else Lucide.Copy,
                    tint = if (copied) AccentGreen else TextPrimary,
                    contentDescription = "Copy SOS message",
                    onClick = {
                        onCopy()
                        copied = true
                    }
                )
                Row(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(BgSurfaceRaised)
                        .border(0.5.dp, BorderDefault, RoundedCornerShape(9.dp))
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "Preview",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Icon(
                        imageVector = if (expanded) Lucide.ChevronUp else Lucide.ChevronDown,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !expanded,
            enter = expandVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PacketSummaryRow("Nume · Locatie · Altitudine · GPS accuracy")
                PacketSummaryRow("Maps link · Battery · Timestamp")
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeOut()
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(BorderSubtle)
                )
                Text(
                    text = message,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    fontFamily = JetBrainsMonoFamily,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgSurface)
                        .padding(14.dp)
                )
            }
        }
    }
}

@Composable
private fun PacketIconButton(
    icon: ImageVector,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(BgSurfaceRaised)
            .border(0.5.dp, BorderDefault, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(13.dp)
        )
    }
}

@Composable
private fun PacketSummaryRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Lucide.Check,
            contentDescription = null,
            tint = AccentGreen,
            modifier = Modifier.size(10.dp)
        )
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun SosSettingsDialog(
    settings: SosSettings,
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (SosSettings) -> Unit
) {
    val context = LocalContext.current
    var draft by remember(settings) { mutableStateOf(settings) }
    var selectedTab by rememberSaveable { mutableStateOf(SosSettingsTab.Trigger) }
    val contactPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contact = result.data?.data?.let { readPickedPhoneContact(context, it) }
            if (contact != null) {
                draft = draft.copy(
                    contacts = upsertSosContact(draft.contacts, contact),
                    smsRecipientsRaw = ""
                )
            }
        }
    }
    val canSave = !draft.action.includesText || draft.smsRecipients.isNotEmpty()

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 340.dp)
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF141A14))
                .border(0.5.dp, BorderDefault, RoundedCornerShape(20.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, Color.Transparent, RoundedCornerShape(0.dp))
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SOS settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = selectedTab.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(BgSurface)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Lucide.X,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(BorderSubtle)
            )

            SettingsTabs(
                selectedTab = selectedTab,
                onSelect = { selectedTab = it }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (androidx.compose.animation.slideInHorizontally(
                            animationSpec = tween(240, easing = FastOutSlowInEasing),
                            initialOffsetX = { it * direction }
                        ) + fadeIn(animationSpec = tween(180))) togetherWith
                            (androidx.compose.animation.slideOutHorizontally(
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                                targetOffsetX = { -it * direction }
                            ) + fadeOut(animationSpec = tween(160)))
                    },
                    label = "sosSettingsTab"
                ) { tab ->
                    when (tab) {
                        SosSettingsTab.Trigger -> TriggerSettingsTab(
                            draft = draft,
                            onDraftChange = { draft = it },
                            onNeedContacts = { selectedTab = SosSettingsTab.Contacts }
                        )
                        SosSettingsTab.Contacts -> ContactsSettingsTab(
                            draft = draft,
                            onDraftChange = { draft = it },
                            onAddContact = {
                                contactPicker.launch(
                                    Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                                )
                            }
                        )
                        SosSettingsTab.Identity -> IdentitySettingsTab(
                            draft = draft,
                            profile = profile,
                            onDraftChange = { draft = it }
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(BorderSubtle)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SecondaryButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton(
                    text = "Save",
                    onClick = { onSave(draft) },
                    icon = Lucide.Check,
                    enabled = canSave,
                    modifier = Modifier.weight(1.5f)
                )
            }
        }
    }
}

private enum class SosSettingsTab(val label: String, val subtitle: String) {
    Trigger("Trigger", "Configureaza hold-ul si actiunea"),
    Contacts("Contacts", "Cui i se trimite rescue packet-ul"),
    Identity("Identity", "Datele incluse in rescue packet")
}

@Composable
private fun SettingsTabs(
    selectedTab: SosSettingsTab,
    onSelect: (SosSettingsTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SosSettingsTab.entries.forEach { tab ->
            val active = tab == selectedTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) AccentGreen.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.label,
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    color = if (active) AccentGreen else TextSecondary
                )
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(BorderSubtle)
    )
}

@Composable
private fun TriggerSettingsTab(
    draft: SosSettings,
    onDraftChange: (SosSettings) -> Unit,
    onNeedContacts: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    ) {
        SettingsSectionLabel("HOLD DURATION")
        ScoutyCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryIconTile(icon = Lucide.Clock, color = Warning, size = 28.dp, iconSize = 14.dp)
                Column {
                    Text(
                        text = "${draft.holdSeconds} seconds",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.2).sp
                    )
                    Text(
                        text = "Evita activarea accidentala",
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Slider(
                value = draft.holdSeconds.toFloat(),
                onValueChange = { value ->
                    onDraftChange(draft.copy(holdSeconds = value.roundToInt().coerceIn(SosSettings.MinHoldSeconds, SosSettings.MaxHoldSeconds)))
                },
                valueRange = SosSettings.MinHoldSeconds.toFloat()..SosSettings.MaxHoldSeconds.toFloat(),
                steps = SosSettings.MaxHoldSeconds - SosSettings.MinHoldSeconds - 1,
                colors = SliderDefaults.colors(
                    thumbColor = Warning,
                    activeTrackColor = Warning,
                    inactiveTrackColor = BorderSubtle
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("2s", color = TextTertiary, fontSize = 9.sp)
                Text("10s", color = TextTertiary, fontSize = 9.sp)
            }
        }

        Spacer(Modifier.height(18.dp))
        SettingsSectionLabel("ACTION AFTER HOLD")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val hasContacts = draft.smsRecipients.isNotEmpty()
            ActionChoiceCard(
                title = "SMS to contacts",
                subtitle = "Trimite rescue packet",
                semantic = Info,
                icon = Lucide.MessageSquare,
                selected = draft.action == SosAction.TEXT_ONLY,
                enabled = hasContacts,
                disabledPill = "0 contacts",
                onDisabledClick = onNeedContacts,
                onSelect = { onDraftChange(draft.copy(action = SosAction.TEXT_ONLY)) }
            )
            ActionChoiceCard(
                title = "Call 112",
                subtitle = "Apel direct, fara meniu",
                semantic = Danger,
                icon = Lucide.Phone,
                selected = draft.action == SosAction.CALL_112 || draft.action == SosAction.TEXT_THEN_CALL_112,
                onSelect = { onDraftChange(draft.copy(action = SosAction.CALL_112)) }
            )
            ActionChoiceCard(
                title = "Call Salvamont",
                subtitle = "0SALVAMONT",
                semantic = Warning,
                icon = Lucide.ShieldPlus,
                selected = draft.action == SosAction.CALL_SALVAMONT || draft.action == SosAction.TEXT_THEN_CALL_SALVAMONT,
                onSelect = { onDraftChange(draft.copy(action = SosAction.CALL_SALVAMONT)) }
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Warning.copy(alpha = 0.06f))
                .border(0.5.dp, Warning.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Lucide.Info, contentDescription = null, tint = Warning, modifier = Modifier.size(11.dp))
            Text(
                text = "Doar o singura actiune principala poate fi activa",
                color = TextPrimary.copy(alpha = 0.75f),
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun ActionChoiceCard(
    title: String,
    subtitle: String,
    semantic: Color,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean = true,
    disabledPill: String? = null,
    onDisabledClick: (() -> Unit)? = null,
    onSelect: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.border(3.dp, semantic.copy(alpha = 0.08f), shape) else Modifier)
            .clip(shape)
            .background(if (selected) semantic.copy(alpha = 0.06f) else BgSurface)
            .border(if (selected) 1.dp else 0.5.dp, if (selected) semantic else BorderSubtle, shape)
            .clickable { if (enabled) onSelect() else onDisabledClick?.invoke() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CategoryIconTile(icon = icon, color = semantic, size = 28.dp, iconSize = 14.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (selected) semantic else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextSecondary, fontSize = 10.sp, lineHeight = 13.sp)
        }
        if (!enabled && disabledPill != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Warning.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(disabledPill, color = Warning, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        } else {
            MiniSwitch(checked = selected, color = semantic, onClick = onSelect)
        }
    }
}

@Composable
private fun MiniSwitch(
    checked: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 32.dp, height = 18.dp)
            .clip(CircleShape)
            .background(if (checked) color else TextSecondary.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (checked) TextPrimary else TextTertiary)
        )
    }
}

@Composable
private fun ContactsSettingsTab(
    draft: SosSettings,
    onDraftChange: (SosSettings) -> Unit,
    onAddContact: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    ) {
        SettingsSectionLabel("EMERGENCY CONTACTS")
        ScoutyCard(semantic = AccentGreen, contentPadding = PaddingValues(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    CategoryIconTile(icon = Lucide.MessageSquare, color = AccentGreen, size = 32.dp, iconSize = 15.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Emergency contacts", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = if (draft.contacts.isEmpty()) "Pick from your contacts" else "${draft.contacts.size} contacts picked",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(0.5.dp, BorderDefault, RoundedCornerShape(10.dp))
                        .clickable(onClick = onAddContact)
                        .padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Lucide.MessageSquare, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(11.dp))
                    Text("Add", color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(BorderSubtle))
        Spacer(Modifier.height(12.dp))
        if (draft.contacts.isEmpty()) {
            Text(
                text = "No contact is stored until you pick one from the Android contact picker.",
                color = TextTertiary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                draft.contacts.forEach { contact ->
                    ContactListRow(
                        contact = contact,
                        onToggle = {
                            onDraftChange(
                                draft.copy(
                                    contacts = draft.contacts.map {
                                        if (it.id == contact.id) it.copy(enabled = !it.enabled) else it
                                    }
                                )
                            )
                        },
                        onRemove = {
                            onDraftChange(draft.copy(contacts = draft.contacts.filterNot { it.id == contact.id }))
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        ScoutyCard(contentPadding = PaddingValues(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryIconTile(icon = Lucide.ShieldPlus, color = Warning, size = 32.dp, iconSize = 15.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Send to Salvamont too", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Notifies the Romanian rescue dispatch", color = TextSecondary, fontSize = 10.sp)
                }
                MiniSwitch(
                    checked = draft.action == SosAction.TEXT_THEN_CALL_SALVAMONT,
                    color = Warning,
                    onClick = {
                        onDraftChange(
                            draft.copy(
                                action = if (draft.action == SosAction.TEXT_THEN_CALL_SALVAMONT) {
                                    SosAction.TEXT_ONLY
                                } else {
                                    SosAction.TEXT_THEN_CALL_SALVAMONT
                                }
                            )
                        )
                    },
                    modifier = Modifier.size(width = 36.dp, height = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun ContactListRow(
    contact: SosContact,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgSurfaceRaised)
            .alpha(if (contact.enabled) 1f else 0.55f)
            .clickable(onClick = onToggle)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(AccentGreenBg),
            contentAlignment = Alignment.Center
        ) {
            Text(contact.initials(), color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(contact.name.ifBlank { "Emergency contact" }, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(contact.phone, color = TextTertiary, fontSize = 10.sp, fontFamily = JetBrainsMonoFamily)
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Danger.copy(alpha = 0.1f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(Lucide.X, contentDescription = "Remove contact", tint = Danger, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun IdentitySettingsTab(
    draft: SosSettings,
    profile: UserProfile,
    onDraftChange: (SosSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    ) {
        SettingsSectionLabel("NAME IN SOS MESSAGE")
        SosTextField(
            label = "",
            value = draft.senderName,
            onValueChange = { onDraftChange(draft.copy(senderName = it)) },
            placeholder = profile.displayName.ifBlank { "Your name here" },
            helper = "Lasa gol pentru a folosi numele din profil"
        )
        Spacer(Modifier.height(14.dp))
        ScoutyCard(semantic = AccentGreen, contentPadding = PaddingValues(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    CategoryIconTile(icon = Lucide.ShieldPlus, color = AccentGreen, size = 32.dp, iconSize = 15.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Include detalii medicale",
                            color = if (draft.includeMedicalDetails) AccentGreen else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text("Stocate local, trimise doar la SOS", color = TextSecondary, fontSize = 10.sp)
                    }
                }
                MiniSwitch(
                    checked = draft.includeMedicalDetails,
                    color = AccentGreen,
                    onClick = { onDraftChange(draft.copy(includeMedicalDetails = !draft.includeMedicalDetails)) },
                    modifier = Modifier.size(width = 36.dp, height = 20.dp)
                )
            }
        }
        AnimatedVisibility(
            visible = draft.includeMedicalDetails,
            enter = expandVertically(animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeOut()
        ) {
            Column {
                Spacer(Modifier.height(16.dp))
                SettingsSectionLabel("BLOOD TYPE")
                BloodTypeGrid(
                    selected = draft.bloodType,
                    onSelect = { onDraftChange(draft.copy(bloodType = it)) }
                )
                Spacer(Modifier.height(16.dp))
                MedicalNotesField(
                    value = draft.medicalNotes,
                    onValueChange = { onDraftChange(draft.copy(medicalNotes = it)) }
                )
            }
        }
    }
}

@Composable
private fun BloodTypeGrid(selected: String, onSelect: (String) -> Unit) {
    val items = listOf("O+", "A-", "B+", "AB+", "O-", "A+", "B-", "?")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    val active = selected == item
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) AccentGreen.copy(alpha = 0.15f) else BgSurfaceRaised)
                            .border(if (active) 1.dp else 0.5.dp, if (active) AccentGreen else BorderDefault, RoundedCornerShape(10.dp))
                            .clickable { onSelect(item) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item,
                            color = if (active) AccentGreen else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicalNotesField(value: String, onValueChange: (String) -> Unit) {
    val overLimit = value.length > 120
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsSectionLabel("MEDICAL NOTES", bottom = 0.dp)
        Text(
            text = "${value.length} / 120",
            color = if (overLimit) Danger else TextTertiary,
            fontSize = 10.sp,
            lineHeight = 12.sp
        )
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .background(BgSurfaceRaised, RoundedCornerShape(12.dp)),
        minLines = 3,
        maxLines = 4,
        placeholder = { Text("Alergii, medicatie, conditii…", color = TextTertiary, fontSize = 12.sp) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = TextPrimary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        ),
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = BgSurfaceRaised,
            unfocusedContainerColor = BgSurfaceRaised,
            disabledContainerColor = BgSurfaceRaised,
            focusedIndicatorColor = if (overLimit) Danger else AccentGreen,
            unfocusedIndicatorColor = if (overLimit) Danger else BorderDefault,
            cursorColor = AccentGreen,
        )
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Scurt si concret. Inclus doar daca toggle-ul e activ.",
        color = TextTertiary,
        fontSize = 9.sp
    )
}

@Composable
private fun SettingsSectionLabel(text: String, bottom: Dp = 8.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.5.sp),
        color = TextSecondary
    )
    if (bottom > 0.dp) {
        Spacer(Modifier.height(bottom))
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    iconColor: Color,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ScoutyCard(
        modifier = Modifier.fillMaxWidth(),
        semantic = if (checked) iconColor else null,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CategoryIconTile(
                icon = icon,
                color = if (checked) iconColor else TextSecondary,
                size = 32.dp,
                iconSize = 15.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextTertiary,
                    lineHeight = 14.sp
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun ContactRecipientsCard(
    contacts: List<SosContact>,
    onAddContact: () -> Unit,
    onToggleContact: (String, Boolean) -> Unit,
    onRemoveContact: (String) -> Unit
) {
    ScoutyCard(contentPadding = PaddingValues(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                CategoryIconTile(
                    icon = Lucide.MessageSquare,
                    color = AccentGreen,
                    size = 34.dp,
                    iconSize = 16.dp
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Emergency contacts",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = if (contacts.isEmpty()) {
                            "Pick phone numbers from your contacts."
                        } else {
                            "${contacts.count { it.enabled }} of ${contacts.size} selected for SMS."
                        },
                        fontSize = 11.sp,
                        color = TextTertiary
                    )
                }
            }
            SecondaryButton(
                text = "Add",
                onClick = onAddContact,
                modifier = Modifier.width(86.dp)
            )
        }

        if (contacts.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                contacts.forEach { contact ->
                    ContactRecipientRow(
                        contact = contact,
                        onToggle = { enabled -> onToggleContact(contact.id, enabled) },
                        onRemove = { onRemoveContact(contact.id) }
                    )
                }
            }
        } else {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "No contact is stored until you pick one from the Android contact picker.",
                color = TextTertiary,
                fontSize = 10.sp,
                lineHeight = 13.sp
            )
        }
    }
}

@Composable
private fun ContactRecipientRow(
    contact: SosContact,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BgSurfaceRaised)
            .clickable { onToggle(!contact.enabled) }
            .padding(start = 6.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = contact.enabled,
            onCheckedChange = onToggle
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.name.ifBlank { "Emergency contact" },
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = contact.phone,
                color = TextTertiary,
                fontSize = 11.sp,
                fontFamily = JetBrainsMonoFamily
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Lucide.X,
                contentDescription = "Remove contact",
                tint = TextTertiary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun SosTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    helper: String,
    minLines: Int = 1
) {
    Column {
        Text(
            text = label.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.3.sp),
            color = TextTertiary
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .background(BgSurfaceRaised, RoundedCornerShape(12.dp)),
            singleLine = minLines == 1,
            minLines = minLines,
            placeholder = {
                Text(
                    text = placeholder,
                    color = TextTertiary,
                    fontSize = 13.sp,
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = TextPrimary,
                fontSize = 13.sp,
            ),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = BgSurfaceRaised,
                unfocusedContainerColor = BgSurfaceRaised,
                disabledContainerColor = BgSurfaceRaised,
                focusedIndicatorColor = AccentGreen,
                unfocusedIndicatorColor = BorderDefault,
                cursorColor = AccentGreen,
            )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = helper,
            color = TextTertiary,
            fontSize = 10.sp,
            lineHeight = 13.sp,
        )
    }
}

private fun HomeStatus.toSosMessageInput(
    displayName: String,
    timestampEpochMillis: Long = System.currentTimeMillis()
): SosMessageInput =
    SosMessageInput(
        displayName = displayName,
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = altitude,
        accuracyMeters = accuracy,
        gpsFixed = gpsFixed,
        locationName = locationName,
        batteryPercent = batteryPercent,
        batterySafe = batterySafe,
        activeTrailName = activeTrail?.name,
        activeTrailRegion = activeTrail?.region,
        activeTrailProgressPercent = activeTrail?.let { (it.progress * 100f).roundToInt().coerceIn(0, 100) },
        activeTrailRemainingKm = activeTrail?.remainingDistanceKm,
        timestampEpochMillis = timestampEpochMillis
    )

private enum class SosCallTarget {
    Emergency,
    Salvamont
}

private fun SosAction.callTarget(): SosCallTarget? =
    when (this) {
        SosAction.CALL_112, SosAction.TEXT_THEN_CALL_112 -> SosCallTarget.Emergency
        SosAction.CALL_SALVAMONT, SosAction.TEXT_THEN_CALL_SALVAMONT -> SosCallTarget.Salvamont
        SosAction.TEXT_ONLY -> null
    }

private fun actionFromSwitches(sendText: Boolean, callTarget: SosCallTarget?): SosAction =
    when (callTarget) {
        SosCallTarget.Emergency -> if (sendText) SosAction.TEXT_THEN_CALL_112 else SosAction.CALL_112
        SosCallTarget.Salvamont -> if (sendText) SosAction.TEXT_THEN_CALL_SALVAMONT else SosAction.CALL_SALVAMONT
        null -> if (sendText) SosAction.TEXT_ONLY else SosAction.CALL_112
    }

private fun actionSummary(settings: SosSettings): String {
    val recipientText = if (settings.action.includesText) {
        val count = settings.smsRecipients.size
        if (count == 0) "No SMS contacts configured" else "$count SMS contact(s)"
    } else {
        "No SMS step"
    }
    val callText = settings.action.callNumber?.let { "dial ${displayDialNumber(it)}" } ?: "no call"
    return "$recipientText · $callText"
}

private data class LocationPillState(
    val label: String,
    val color: Color
)

private fun locationPillState(status: HomeStatus): LocationPillState =
    when {
        !status.gpsFixed -> LocationPillState("ACQUIRING…", Warning)
        !status.isOnline -> LocationPillState("LAST KNOWN", Warning)
        else -> LocationPillState("GPS FIX", AccentGreen)
    }

private fun topBarActionLabel(settings: SosSettings): String =
    "Hold ${settings.holdSeconds}s · ${shortActionLabel(settings.action)}"

private fun shortActionLabel(action: SosAction): String =
    when (action) {
        SosAction.CALL_112 -> "Call 112"
        SosAction.CALL_SALVAMONT -> "Call Salvamont"
        SosAction.TEXT_ONLY -> "SMS contacts"
        SosAction.TEXT_THEN_CALL_112 -> "SMS + Call 112"
        SosAction.TEXT_THEN_CALL_SALVAMONT -> "SMS + Salvamont"
    }

private fun holdHelperText(action: SosAction): String =
    when (action) {
        SosAction.CALL_112 -> "Hold pentru call 112. Fara meniu intermediar."
        SosAction.CALL_SALVAMONT -> "Hold pentru call Salvamont. Fara meniu intermediar."
        SosAction.TEXT_ONLY -> "Hold pentru SMS catre contacte. Fara meniu intermediar."
        SosAction.TEXT_THEN_CALL_112 -> "Hold pentru SMS, apoi call 112. Fara meniu intermediar."
        SosAction.TEXT_THEN_CALL_SALVAMONT -> "Hold pentru SMS, apoi call Salvamont. Fara meniu intermediar."
    }

private fun openSmsComposer(context: Context, recipients: List<String>, message: String): Boolean {
    if (recipients.isEmpty()) return false
    val joinedRecipients = recipients.joinToString(";")
    val uri = Uri.parse("smsto:" + recipients.joinToString(";") { Uri.encode(it) })
    val intent = Intent(Intent.ACTION_SENDTO, uri)
        .putExtra("sms_body", message)
        .putExtra("address", joinedRecipients)
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrElse { error ->
        if (error is ActivityNotFoundException) false else false
    }
}

private fun readPickedPhoneContact(context: Context, uri: Uri): SosContact? =
    runCatching {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone._ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val id = cursor.getStringOrNull(ContactsContract.CommonDataKinds.Phone._ID).orEmpty()
            val name = cursor.getStringOrNull(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME).orEmpty()
            val phone = cursor.getStringOrNull(ContactsContract.CommonDataKinds.Phone.NUMBER)
                .orEmpty()
                .replace(Regex("\\s+"), " ")
                .trim()
            if (phone.isBlank()) {
                null
            } else {
                SosContact(
                    id = id.ifBlank { "phone_${phone.filter(Char::isDigit)}" },
                    name = name.ifBlank { phone },
                    phone = phone,
                    enabled = true
                )
            }
        }
    }.getOrNull()

private fun android.database.Cursor.getStringOrNull(columnName: String): String? {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getString(index) else null
}

private fun upsertSosContact(existing: List<SosContact>, contact: SosContact): List<SosContact> {
    val key = contact.dedupeKey
    val withoutDuplicate = existing.filterNot { it.dedupeKey == key || it.id == contact.id }
    return withoutDuplicate + contact.copy(enabled = true)
}

private fun SosContact.initials(): String {
    val source = name.ifBlank { phone }
    return source
        .split(' ', '-', '_')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "?" }
}

private fun openDialer(context: Context, number: String): Boolean {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrElse { false }
}

private fun displayDialNumber(number: String): String =
    if (number == SosAction.SalvamontDialNumber) "0SALVAMONT" else number

private fun formatLatitude(value: Double?): String =
    value?.let { String.format(Locale.US, "%.6f° %s", abs(it), if (it >= 0) "N" else "S") } ?: "--"

private fun formatLongitude(value: Double?): String =
    value?.let { String.format(Locale.US, "%.6f° %s", abs(it), if (it >= 0) "E" else "W") } ?: "--"
