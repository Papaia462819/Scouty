package com.scouty.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scouty.app.R
import com.scouty.app.assistant.model.AssistantMessageUiModel
import com.scouty.app.assistant.model.AssistantUiState
import com.scouty.app.assistant.model.ResponseSectionStyle
import com.scouty.app.assistant.model.SafetyOutcome

@Composable
fun ChatScreen(
    uiState: AssistantUiState,
    contentPadding: PaddingValues,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onPromptSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(uiState.messages, key = { it.id }) { message ->
                ChatBubble(message)
            }
            if (uiState.isResponding) {
                item {
                    val loadingText = if (java.util.Locale.getDefault().language == "ro") {
                        "Scouty AI procesează răspunsul offline..."
                    } else {
                        "Scouty AI is processing your offline response..."
                    }
                    ChatBubble(
                        AssistantMessageUiModel(
                            id = "loading",
                            text = loadingText,
                            isUser = false
                        )
                    )
                }
            }
        }

        if (uiState.messages.size == 1 && uiState.draft.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.starterPrompts.forEach { prompt ->
                    SuggestionChip(
                        onClick = { onPromptSelected(prompt) },
                        label = { Text(prompt) }
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { }) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Vision",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = uiState.draft,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.chat_hint)) },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onSend,
                    enabled = uiState.draft.isNotBlank() && !uiState.isResponding,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: AssistantMessageUiModel) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val containerColor = if (message.isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (message.isUser) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = if (message.isUser) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = shape
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (!message.isUser) {
            Text(
                text = "Scouty AI",
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            if (message.safetyOutcome != SafetyOutcome.NORMAL) {
                val isRo = java.util.Locale.getDefault().language == "ro"
                val safetyLabel = if (message.safetyOutcome == SafetyOutcome.EMERGENCY_ESCALATION) {
                    if (isRo) "Escalare: prioritizează SOS / 112" else "Escalation: prioritize SOS / 112"
                } else {
                    if (isRo) "Atenție: verifică starea înainte de a continua" else "Caution: verify condition before continuing"
                }
                AssistChip(
                    onClick = { },
                    enabled = false,
                    label = { Text(safetyLabel) },
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (message.citations.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    message.citations.forEach { citation ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    text = "${citation.sourceTitle} · ${citation.sectionTitle}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = citation.snippet,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            if (message.sections.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    message.sections.forEach { section ->
                        val sectionColor = when (section.style) {
                            ResponseSectionStyle.IMPORTANT -> MaterialTheme.colorScheme.errorContainer
                            ResponseSectionStyle.CONTEXT -> MaterialTheme.colorScheme.secondaryContainer
                            ResponseSectionStyle.ACTIONS -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ResponseSectionStyle.GUIDANCE -> MaterialTheme.colorScheme.surface
                        }
                        Surface(
                            color = sectionColor,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = section.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
            if (!message.generationMode?.label.isNullOrBlank() || !message.knowledgePackVersion.isNullOrBlank()) {
                Text(
                    text = listOfNotNull(
                        message.generationMode?.label,
                        message.reasoningType?.label,
                        message.knowledgePackVersion?.let { "pack $it" },
                        message.modelVersion
                    ).joinToString(" • "),
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
