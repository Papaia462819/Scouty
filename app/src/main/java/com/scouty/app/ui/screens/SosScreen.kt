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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(BgPrimary)
                .border(0.5.dp, BorderSubtle, RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
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
                        text = "Configureaza hold-ul si actiunea directa.",
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(470.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScoutySectionHeader(title = "HOLD DURATION")
                    ScoutyCard(contentPadding = PaddingValues(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CategoryIconTile(icon = Lucide.Clock, color = Warning, size = 34.dp, iconSize = 16.dp)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "${draft.holdSeconds} seconds",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Long enough to avoid accidental activation.",
                                        fontSize = 11.sp,
                                        color = TextTertiary
                                    )
                                }
                            }
                            QuantityStepper(
                                value = draft.holdSeconds,
                                minValue = SosSettings.MinHoldSeconds,
                                maxValue = SosSettings.MaxHoldSeconds,
                                onDecrement = { draft = draft.copy(holdSeconds = (draft.holdSeconds - 1).coerceAtLeast(SosSettings.MinHoldSeconds)) },
                                onIncrement = { draft = draft.copy(holdSeconds = (draft.holdSeconds + 1).coerceAtMost(SosSettings.MaxHoldSeconds)) },
                                accent = Warning
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScoutySectionHeader(title = "ACTION AFTER HOLD")
                    SettingsSwitchRow(
                        title = "Send SOS text",
                        subtitle = "Opens SMS with selected contacts and rescue packet.",
                        iconColor = AccentGreen,
                        icon = Lucide.MessageSquare,
                        checked = draft.action.includesText,
                        onCheckedChange = { checked ->
                            val callTarget = draft.action.callTarget()
                            draft = draft.copy(
                                action = actionFromSwitches(
                                    sendText = checked,
                                    callTarget = if (!checked && callTarget == null) SosCallTarget.Emergency else callTarget
                                )
                            )
                        }
                    )
                    SettingsSwitchRow(
                        title = "Call 112",
                        subtitle = "Mutually exclusive with Salvamont call.",
                        iconColor = Danger,
                        icon = Lucide.Phone,
                        checked = draft.action.callTarget() == SosCallTarget.Emergency,
                        onCheckedChange = { checked ->
                            draft = draft.copy(
                                action = actionFromSwitches(
                                    sendText = draft.action.includesText || (!checked && draft.action.callTarget() == SosCallTarget.Emergency),
                                    callTarget = if (checked) SosCallTarget.Emergency else null
                                )
                            )
                        }
                    )
                    SettingsSwitchRow(
                        title = "Call Salvamont",
                        subtitle = "Uses 0SALVAMONT dial number.",
                        iconColor = Warning,
                        icon = Lucide.ShieldPlus,
                        checked = draft.action.callTarget() == SosCallTarget.Salvamont,
                        onCheckedChange = { checked ->
                            draft = draft.copy(
                                action = actionFromSwitches(
                                    sendText = draft.action.includesText || (!checked && draft.action.callTarget() == SosCallTarget.Salvamont),
                                    callTarget = if (checked) SosCallTarget.Salvamont else null
                                )
                            )
                        }
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScoutySectionHeader(title = "SMS RECIPIENTS")
                    ContactRecipientsCard(
                        contacts = draft.contacts,
                        onAddContact = {
                            contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                        },
                        onToggleContact = { contactId, enabled ->
                            draft = draft.copy(
                                contacts = draft.contacts.map { contact ->
                                    if (contact.id == contactId) contact.copy(enabled = enabled) else contact
                                }
                            )
                        },
                        onRemoveContact = { contactId ->
                            draft = draft.copy(contacts = draft.contacts.filterNot { it.id == contactId })
                        }
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScoutySectionHeader(title = "MESSAGE IDENTITY")
                    SosTextField(
                        label = "Name in SOS message",
                        value = draft.senderName,
                        onValueChange = { draft = draft.copy(senderName = it) },
                        placeholder = profile.displayName.ifBlank { "Your name" },
                        helper = "If empty, Scouty uses your profile name."
                    )
                    SosTextField(
                        label = "Blood type",
                        value = draft.bloodType,
                        onValueChange = { draft = draft.copy(bloodType = it) },
                        placeholder = "O+, A-, unknown",
                        helper = "Optional medical detail."
                    )
                    SosTextField(
                        label = "Medical notes",
                        value = draft.medicalNotes,
                        onValueChange = { draft = draft.copy(medicalNotes = it) },
                        placeholder = "Allergies, medication, conditions",
                        helper = "Keep this short. It will be included only if enabled.",
                        minLines = 2
                    )
                    ScoutyCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
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
                                        text = "Include medical details",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Blood type and notes stay local until SMS/share.",
                                        fontSize = 11.sp,
                                        color = TextTertiary
                                    )
                                }
                            }
                            Switch(
                                checked = draft.includeMedicalDetails,
                                onCheckedChange = { draft = draft.copy(includeMedicalDetails = it) }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                    modifier = Modifier.weight(1f)
                )
            }
        }
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
