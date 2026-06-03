package com.scouty.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.Droplet
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Mountain
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.Route
import com.composables.icons.lucide.Send
import com.composables.icons.lucide.ShieldPlus
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.X
import com.scouty.app.assistant.model.AssistantMessageUiModel
import com.scouty.app.assistant.model.AssistantUiState
import com.scouty.app.assistant.model.OfflineChatModelStatus
import com.scouty.app.assistant.model.OfflineChatUiState
import com.scouty.app.ui.components.CategoryIconTile
import com.scouty.app.ui.components.ScoutySectionHeader
import com.scouty.app.ui.theme.AccentGreen
import com.scouty.app.ui.theme.AccentGreenBg
import com.scouty.app.ui.theme.AccentGreenBorder
import com.scouty.app.ui.theme.AccentGreenOnSurface
import com.scouty.app.ui.theme.BgPrimary
import com.scouty.app.ui.theme.BgSurface
import com.scouty.app.ui.theme.BgSurfaceRaised
import com.scouty.app.ui.theme.BorderDefault
import com.scouty.app.ui.theme.Danger
import com.scouty.app.ui.theme.Info as InfoBlue
import com.scouty.app.ui.theme.TextMuted
import com.scouty.app.ui.theme.TextPrimary
import com.scouty.app.ui.theme.TextSecondary
import com.scouty.app.ui.theme.TextTertiary
import com.scouty.app.ui.theme.Warning
import com.scouty.app.ui.theme.Water
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun ChatScreen(
    uiState: AssistantUiState,
    contentPadding: PaddingValues,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onPromptSelected: (String) -> Unit,
    onPhotoClick: () -> Unit,
    onOfflineChatToggle: (Boolean) -> Unit,
    onConfirmOfflineChatMeteredDownload: () -> Unit,
    onDismissOfflineChatMeteredDownload: () -> Unit,
    onDismissOfflineChatLoading: () -> Unit,
    onDismissOfflineChatFinished: () -> Unit,
    onConfirmDisableOfflineChat: () -> Unit,
    onDismissDisableOfflineChat: () -> Unit,
) {
    var showOptions by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(contentPadding),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ChatTopBar(
                isOnline = uiState.isOnline,
                onMenuClick = { showOptions = !showOptions }
            )

            val isEmpty = uiState.messages.size <= 1 && uiState.draft.isEmpty()

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    ChatBubble(message = message, onPromptSelected = onPromptSelected)
                }
                if (uiState.isResponding) {
                    item {
                        val thinkingText = remember(uiState.messages.size) {
                            ThinkingMessages.random()
                        }
                        ThinkingBubble(text = thinkingText)
                    }
                }

                if (isEmpty && uiState.starterPrompts.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        ScoutySectionHeader(title = "INTREBARI SUGERATE")
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            uiState.starterPrompts.forEach { prompt ->
                                SuggestedQuestionRow(
                                    icon = iconForPrompt(prompt),
                                    tint = tintForPrompt(prompt),
                                    text = prompt,
                                    onClick = { onPromptSelected(prompt) },
                                )
                            }
                        }
                    }
                }
            }

            ChatInputBar(
                value = uiState.draft,
                onValueChange = onInputChange,
                onSend = onSend,
                onPhotoClick = onPhotoClick,
                sendEnabled = uiState.draft.isNotBlank() && !uiState.isResponding,
            )
        }

        if (showOptions) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
                    .clickable { showOptions = false }
            )
            AnimatedVisibility(
                visible = showOptions,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 14.dp),
                enter = scaleIn(
                    initialScale = 0.92f,
                    transformOrigin = TransformOrigin(1f, 0f),
                    animationSpec = tween(180, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(180)),
                exit = scaleOut(
                    targetScale = 0.92f,
                    transformOrigin = TransformOrigin(1f, 0f),
                    animationSpec = tween(140, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(140))
            ) {
                ChatOptionsPanel(
                    offlineChat = uiState.offlineChat,
                    onOfflineChatToggle = { enabled ->
                        showOptions = false
                        onOfflineChatToggle(enabled)
                    }
                )
            }
        }
    }

    OfflineChatDialogs(
        offlineChat = uiState.offlineChat,
        onConfirmMeteredDownload = onConfirmOfflineChatMeteredDownload,
        onDismissMeteredDownload = onDismissOfflineChatMeteredDownload,
        onDismissLoading = onDismissOfflineChatLoading,
        onDismissFinished = onDismissOfflineChatFinished,
        onConfirmDisable = onConfirmDisableOfflineChat,
        onDismissDisable = onDismissDisableOfflineChat
    )
}

@Composable
private fun ThinkingBubble(text: String) {
    var visibleCharacters by remember(text) { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 4.dp)

    LaunchedEffect(text) {
        while (true) {
            visibleCharacters = 0
            text.indices.forEach { index ->
                visibleCharacters = index + 1
                delay(42)
            }
            delay(500)
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(AccentGreenBg)
                .border(0.5.dp, AccentGreenBorder, shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = text,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = Color.Transparent,
            )
            Text(
                text = text.take(visibleCharacters),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = TextPrimary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Ghid Scouty · acum",
            fontSize = 10.sp,
            color = TextMuted,
        )
    }
}

@Composable
private fun ChatTopBar(
    isOnline: Boolean,
    onMenuClick: () -> Unit
) {
    val statusText = if (isOnline) "online" else "offline"
    val statusTextColor = if (isOnline) AccentGreen else TextTertiary
    val statusDotColor = if (isOnline) AccentGreen else Warning

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIconTile(icon = Lucide.MessageSquare, color = AccentGreen)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Ghid Scouty",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(statusDotColor),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusTextColor,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(0.5.dp, BorderDefault, RoundedCornerShape(10.dp))
                .clickable(onClick = onMenuClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Lucide.EllipsisVertical,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
private fun ChatOptionsPanel(
    offlineChat: OfflineChatUiState,
    onOfflineChatToggle: (Boolean) -> Unit
) {
    val checked = offlineChat.enabled
    Column(
        modifier = Modifier
            .width(240.dp)
            .shadow(16.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.4f))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141A14))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Text(
                text = "SETARI CHAT",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onOfflineChatToggle(!checked) }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Chat offline",
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = offlineChatStatusLabel(offlineChat),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ChatToggleSwitch(
                checked = checked,
                onClick = { onOfflineChatToggle(!checked) }
            )
        }
    }
}

@Composable
private fun ChatToggleSwitch(
    checked: Boolean,
    onClick: () -> Unit
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 16.dp else 0.dp,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "offlineChatThumb"
    )
    Box(
        modifier = Modifier
            .size(width = 36.dp, height = 20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (checked) AccentGreen else Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(16.dp)
                .clip(CircleShape)
                .background(if (checked) Color.White else TextTertiary)
        )
    }
}

@Composable
private fun OfflineChatDialogs(
    offlineChat: OfflineChatUiState,
    onConfirmMeteredDownload: () -> Unit,
    onDismissMeteredDownload: () -> Unit,
    onDismissLoading: () -> Unit,
    onDismissFinished: () -> Unit,
    onConfirmDisable: () -> Unit,
    onDismissDisable: () -> Unit
) {
    if (offlineChat.showMeteredConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissMeteredDownload,
            title = {
                Text(text = "Descarci chat offline?")
            },
            text = {
                Text(
                    text = "Modelul Qwen are aproximativ ${offlineModelSizeLabel(offlineChat.modelSizeBytes)}. Ești pe date mobile; confirmă descărcarea doar dacă vrei să consumi traficul acum."
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmMeteredDownload) {
                    Text("Descarcă")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissMeteredDownload) {
                    Text("Anulează")
                }
            }
        )
    }

    if (offlineChat.showLoadingDialog) {
        OfflineChatLoadingDialog(
            offlineChat = offlineChat,
            onDismiss = onDismissLoading
        )
    }

    if (offlineChat.showFinishedDialog) {
        AlertDialog(
            onDismissRequest = onDismissFinished,
            title = {
                Text(text = "Chat offline este gata")
            },
            text = {
                Text(text = "Modelul Qwen a fost instalat și încărcat. Îl păstrez activ până îl oprești din meniul de chat.")
            },
            confirmButton = {
                TextButton(onClick = onDismissFinished) {
                    Text("OK")
                }
            }
        )
    }

    if (offlineChat.showDisableConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissDisable,
            title = {
                Text(text = "Vrei să pierzi asistentul offline?")
            },
            text = {
                Text(text = "Modelul local va fi șters de pe telefon. Chatul va continua cu răspuns online când ai internet sau cu răspuns structurat când nu ai.")
            },
            confirmButton = {
                TextButton(onClick = onConfirmDisable) {
                    Text("Dezinstalează")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDisable) {
                    Text("Păstrează")
                }
            }
        )
    }
}

@Composable
private fun OfflineChatLoadingDialog(
    offlineChat: OfflineChatUiState,
    onDismiss: () -> Unit
) {
    val progress = offlineChat.progressPercent?.let { (it / 100f).coerceIn(0f, 1f) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 340.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(BgSurfaceRaised)
                .border(0.5.dp, BorderDefault, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Se activează chat offline",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Lucide.X,
                        contentDescription = "Închide",
                        tint = TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = offlineChat.message ?: "Se pregătește modelul offline.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(14.dp))
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = AccentGreen,
                    trackColor = BorderDefault
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = AccentGreen,
                    trackColor = BorderDefault
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${offlineChat.progressPercent ?: 0}% · ${offlineModelSizeLabel(offlineChat.modelSizeBytes)}",
                fontSize = 10.sp,
                color = TextTertiary
            )
        }
    }
}

private fun offlineChatStatusLabel(offlineChat: OfflineChatUiState): String =
    when (offlineChat.status) {
        OfflineChatModelStatus.DISABLED -> "Oprit"
        OfflineChatModelStatus.WAITING_METERED_CONFIRMATION -> "Așteaptă confirmare"
        OfflineChatModelStatus.DOWNLOADING -> "Descarcă ${offlineChat.progressPercent ?: 0}%"
        OfflineChatModelStatus.INSTALLING -> "Se instalează"
        OfflineChatModelStatus.LOADING -> "Se încarcă"
        OfflineChatModelStatus.READY -> "Activ"
        OfflineChatModelStatus.FAILED -> "Eroare"
    }

private fun offlineModelSizeLabel(bytes: Long): String =
    String.format(Locale.getDefault(), "%.1f GB", bytes / 1_000_000_000.0)

@Composable
private fun SuggestedQuestionRow(
    icon: ImageVector,
    tint: Color,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgSurface)
            .border(0.5.dp, BorderDefault, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            color = TextPrimary,
        )
        Icon(
            imageVector = Lucide.ChevronRight,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun ChatBubble(
    message: AssistantMessageUiModel,
    onPromptSelected: (String) -> Unit,
) {
    var sourcesExpanded by rememberSaveable(message.id) { mutableStateOf(false) }
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val containerColor = if (message.isUser) BgSurfaceRaised else AccentGreenBg
    val borderColor = if (message.isUser) BorderDefault else AccentGreenBorder
    val shape = if (message.isUser) {
        RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 4.dp, bottomStart = 14.dp)
    } else {
        RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 4.dp)
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(containerColor)
                .border(0.5.dp, borderColor, shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            val messageText = if (message.isUser) {
                AnnotatedString(message.text)
            } else if (message.isProvisional) {
                renderAssistantMarkdown("${message.text} ...")
            } else {
                renderAssistantMarkdown(message.text)
            }
            Text(
                text = messageText,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = TextPrimary,
            )
        }
        if (!message.isUser) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (message.isProvisional) "Ghid Scouty · redactez" else "Ghid Scouty · acum",
                fontSize = 10.sp,
                color = TextMuted,
            )
        }
        if (!message.isUser && message.followUpReplies.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                message.followUpReplies.forEach { followUp ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(BgSurfaceRaised)
                            .border(0.5.dp, BorderDefault, RoundedCornerShape(20.dp))
                            .clickable { onPromptSelected(followUp.query) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = followUp.label,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
        if (!message.isUser && message.citations.isNotEmpty()) {
            TextButton(
                onClick = { sourcesExpanded = !sourcesExpanded },
                modifier = Modifier.padding(top = 2.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) {
                Icon(
                    imageVector = if (sourcesExpanded) Lucide.ChevronUp else Lucide.ChevronDown,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (sourcesExpanded) "Ascunde sursele" else "Surse",
                    fontSize = 11.sp,
                    color = AccentGreen,
                    fontWeight = FontWeight.Medium,
                )
            }
            AnimatedVisibility(visible = sourcesExpanded) {
                Column(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    message.citations.forEach { citation ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(BgSurface)
                                .border(0.5.dp, BorderDefault, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Column {
                                Text(
                                    text = "${citation.sourceTitle} · ${citation.sectionTitle}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AccentGreen,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = citation.snippet,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onPhotoClick: () -> Unit,
    sendEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .navigationBarsPadding()
            .imePadding()
            .clip(RoundedCornerShape(24.dp))
            .background(BgSurfaceRaised)
            .border(0.5.dp, BorderDefault, RoundedCornerShape(24.dp))
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(AccentGreenBg)
                .clickable(onClick = onPhotoClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Lucide.Camera,
                contentDescription = "Deschide TrackScanner",
                tint = AccentGreen,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = "Ask anything...",
                    fontSize = 13.sp,
                    color = TextTertiary,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(
                    color = TextPrimary,
                    fontSize = 13.sp,
                ),
                cursorBrush = SolidColor(AccentGreen),
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (sendEnabled) onSend() }),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(AccentGreen.copy(alpha = if (sendEnabled) 1f else 0.4f))
                .clickable(enabled = sendEnabled, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Lucide.Send,
                contentDescription = "Send",
                tint = AccentGreenOnSurface,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun iconForPrompt(prompt: String): ImageVector {
    val lower = prompt.lowercase()
    return when {
        "vreme" in lower || "ploaie" in lower || "nor" in lower || "furtuna" in lower -> Lucide.Cloud
        "urgenta" in lower || "ranit" in lower || "sos" in lower ||
            "pierd" in lower || "ratac" in lower || "pericol" in lower -> Lucide.TriangleAlert
        "dificultate" in lower || "greu" in lower || "nivel" in lower ||
            "siguranta" in lower || "salvamont" in lower || "kit" in lower -> Lucide.ShieldPlus
        "echipament" in lower || "gear" in lower || "bocanci" in lower || "rucsac" in lower -> Lucide.Package
        "apa" in lower || "izvor" in lower || "hidrat" in lower -> Lucide.Droplet
        "refugi" in lower || "caban" in lower || "adapost" in lower -> Lucide.House
        "traseu" in lower || "marca" in lower || "poteca" in lower -> Lucide.Route
        else -> Lucide.Mountain
    }
}

private fun tintForPrompt(prompt: String): Color {
    val lower = prompt.lowercase()
    return when {
        "vreme" in lower || "ploaie" in lower || "nor" in lower || "furtuna" in lower -> InfoBlue
        "urgenta" in lower || "ranit" in lower || "sos" in lower ||
            "pierd" in lower || "ratac" in lower || "pericol" in lower -> Danger
        "dificultate" in lower || "greu" in lower || "nivel" in lower ||
            "siguranta" in lower || "salvamont" in lower || "kit" in lower -> Warning
        "echipament" in lower || "gear" in lower || "bocanci" in lower || "rucsac" in lower -> Warning
        "apa" in lower || "izvor" in lower || "hidrat" in lower -> Water
        "refugi" in lower || "caban" in lower || "adapost" in lower -> InfoBlue
        else -> AccentGreen
    }
}

private fun renderAssistantMarkdown(text: String): AnnotatedString {
    val normalizedText = normalizeMarkdownBullets(text)
    return buildAnnotatedString {
        var cursor = 0
        while (cursor < normalizedText.length) {
            val boldStart = normalizedText.indexOf("**", startIndex = cursor)
            if (boldStart < 0) {
                append(normalizedText.substring(cursor))
                break
            }

            val boldEnd = normalizedText.indexOf("**", startIndex = boldStart + 2)
            if (boldEnd < 0) {
                append(normalizedText.substring(cursor))
                break
            }

            append(normalizedText.substring(cursor, boldStart))
            pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
            append(normalizedText.substring(boldStart + 2, boldEnd))
            pop()
            cursor = boldEnd + 2
        }
    }
}

private fun normalizeMarkdownBullets(text: String): String =
    text.lineSequence()
        .joinToString("\n") { line ->
            val contentStart = line.indexOfFirst { it != ' ' && it != '\t' }
            if (contentStart < 0) {
                line
            } else {
                val indent = line.substring(0, contentStart)
                val content = line.substring(contentStart)
                when {
                    content.startsWith("* ") -> "$indent• ${content.drop(2)}"
                    content.startsWith("- ") -> "$indent• ${content.drop(2)}"
                    else -> line
                }
            }
        }

private val ThinkingMessages = listOf(
    "Verific informatiile...",
    "Caut in context...",
    "Ma uit peste datele traseului...",
    "Pun cap la cap detaliile...",
    "Consult busola Scouty...",
)
