package com.scouty.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.scouty.app.assistant.model.AssistantMessageUiModel
import com.scouty.app.assistant.model.AssistantUiState
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

@Composable
fun ChatScreen(
    uiState: AssistantUiState,
    contentPadding: PaddingValues,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onPromptSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(contentPadding),
    ) {
        ChatTopBar()

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
                    ChatBubble(
                        message = AssistantMessageUiModel(
                            id = "loading",
                            text = listOf(
                                "Hmmm...",
                                "Stai sa ma gandesc.",
                                "Imediat!",
                                "Stai putin.",
                            ).random(),
                            isUser = false,
                        ),
                        onPromptSelected = onPromptSelected,
                    )
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
            sendEnabled = uiState.draft.isNotBlank() && !uiState.isResponding,
        )
    }
}

@Composable
private fun ChatTopBar() {
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
                            .background(Warning),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Offline · Knowledge pack v2.1",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                }
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
                imageVector = Lucide.EllipsisVertical,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

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
            Text(
                text = message.text,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = TextPrimary,
            )
        }
        if (!message.isUser) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Ghid Scouty · acum",
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
                .clickable { },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Lucide.Camera,
                contentDescription = "Vision",
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
