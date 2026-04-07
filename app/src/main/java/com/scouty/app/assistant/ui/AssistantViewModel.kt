package com.scouty.app.assistant.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scouty.app.assistant.data.ChatActionHandler
import com.scouty.app.assistant.data.DeviceContextProvider
import com.scouty.app.assistant.domain.AssistantRuntimeGraph
import com.scouty.app.assistant.domain.AssistantRepository
import com.scouty.app.assistant.model.AssistantAction
import com.scouty.app.assistant.model.AssistantConversationState
import com.scouty.app.assistant.model.AssistantMessageUiModel
import com.scouty.app.assistant.model.AssistantUiState
import com.scouty.app.assistant.model.SafetyOutcome
import com.scouty.app.assistant.model.assistantDefaultLocale
import com.scouty.app.assistant.model.starterPromptsForCurrentLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class AssistantViewModel(
    private val repository: AssistantRepository,
    private val deviceContextProvider: DeviceContextProvider,
    private val chatActionHandler: ChatActionHandler? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()
    private var conversationState = AssistantConversationState()
    private var lastTrailPresent: Boolean = false

    init {
        viewModelScope.launch {
            deviceContextProvider.deviceContext.collect { context ->
                val hasTrail = context.trail != null
                if (hasTrail != lastTrailPresent) {
                    lastTrailPresent = hasTrail
                    val locale = assistantDefaultLocale()
                    _uiState.update { state ->
                        state.copy(
                            starterPrompts = starterPromptsForCurrentLocale(
                                locale = locale,
                                hasActiveTrail = hasTrail
                            )
                        )
                    }
                }
            }
        }
    }

    fun updateDraft(value: String) {
        _uiState.update { it.copy(draft = value) }
    }

    fun sendPrompt(prompt: String) {
        updateDraft(prompt)
        sendCurrentDraft()
    }

    fun sendCurrentDraft() {
        val query = _uiState.value.draft.trim()
        if (query.isBlank() || _uiState.value.isResponding) {
            return
        }

        if (isEchoedAssistantFollowUp(query)) {
            appendAssistantClarificationForEchoedFollowUp(query)
            return
        }

        val userMessage = AssistantMessageUiModel(
            id = UUID.randomUUID().toString(),
            text = query,
            isUser = true
        )
        _uiState.update {
            it.copy(
                draft = "",
                isResponding = true,
                messages = it.messages + userMessage
            )
        }

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    repository.answer(
                        query = query,
                        context = deviceContextProvider.deviceContext.value,
                        conversationState = conversationState
                    )
                }
            }.onSuccess { response ->
                conversationState = response.conversationState
                processActions(response.actions)
                val assistantMessage = AssistantMessageUiModel(
                    id = UUID.randomUUID().toString(),
                    text = response.answerText,
                    isUser = false,
                    citations = response.citations,
                    safetyOutcome = response.safetyOutcome,
                    sections = response.structuredOutput.sections,
                    resolvedTopic = response.structuredOutput.resolvedTopic,
                    resolvedFamily = response.structuredOutput.resolvedFamily,
                    generationMode = response.generationMode,
                    reasoningType = response.reasoningType,
                    knowledgePackVersion = response.knowledgePackVersion,
                    modelVersion = response.modelVersion,
                    modelRuntimeState = response.modelRuntimeState,
                    modelStatusDetails = response.modelStatusDetails
                )
                val followUpMessage = buildSequentialFollowUpPrompt(response.structuredOutput.followUpQuestions)
                    ?.let { prompt ->
                        AssistantMessageUiModel(
                            id = UUID.randomUUID().toString(),
                            text = prompt.question,
                            isUser = false,
                            followUpReplies = prompt.suggestedReplies,
                            resolvedTopic = response.structuredOutput.resolvedTopic,
                            resolvedFamily = response.structuredOutput.resolvedFamily,
                            generationMode = response.generationMode,
                            reasoningType = response.reasoningType,
                            knowledgePackVersion = response.knowledgePackVersion
                        )
                    }
                _uiState.update { state ->
                    state.copy(
                        isResponding = false,
                        messages = state.messages + listOfNotNull(assistantMessage, followUpMessage)
                    )
                }
            }.onFailure {
                val fallbackMessage = AssistantMessageUiModel(
                    id = UUID.randomUUID().toString(),
                    text = "Nu am putut procesa mesajul acesta. Scrie-mi mai simplu ce vrei sau răspunde pe scurt cu situația ta, de exemplu: Căldură, Gătit, Am amnar, Totul e ud.",
                    isUser = false,
                    safetyOutcome = SafetyOutcome.CAUTION
                )
                _uiState.update { state ->
                    state.copy(
                        isResponding = false,
                        messages = state.messages + fallbackMessage
                    )
                }
            }
        }
    }

    private fun isEchoedAssistantFollowUp(query: String): Boolean =
        conversationState.askedFollowUps.any { normalizePrompt(it) == normalizePrompt(query) }

    private fun appendAssistantClarificationForEchoedFollowUp(query: String) {
        val prompt = buildSequentialFollowUpPrompt(listOf(query))
        val userMessage = AssistantMessageUiModel(
            id = UUID.randomUUID().toString(),
            text = query,
            isUser = true
        )
        val clarification = AssistantMessageUiModel(
            id = UUID.randomUUID().toString(),
            text = "Asta era întrebarea mea pentru tine. Răspunde-mi cu varianta care se potrivește sau scrie pe scurt situația ta.",
            isUser = false,
            followUpReplies = prompt?.suggestedReplies.orEmpty(),
            safetyOutcome = SafetyOutcome.NORMAL
        )
        _uiState.update { state ->
            state.copy(
                draft = "",
                isResponding = false,
                messages = state.messages + userMessage + clarification
            )
        }
    }

    private fun normalizePrompt(value: String): String =
        value.lowercase()
            .replace("[^\\p{L}0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    private fun processActions(actions: List<AssistantAction>) {
        actions.forEach { action ->
            when (action) {
                is AssistantAction.ToggleGearPacked -> {
                    chatActionHandler?.toggleGearPacked(action.itemIds, action.packed)
                }
            }
        }
    }

    class Factory(
        private val application: Application,
        private val deviceContextProvider: DeviceContextProvider,
        private val chatActionHandler: ChatActionHandler? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AssistantViewModel::class.java)) {
                val runtimeGraph = AssistantRuntimeGraph.get(application)
                return AssistantViewModel(
                    repository = runtimeGraph.repository,
                    deviceContextProvider = deviceContextProvider,
                    chatActionHandler = chatActionHandler
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
