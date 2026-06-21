package com.scouty.app.assistant.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scouty.app.assistant.data.ChatActionHandler
import com.scouty.app.assistant.data.DeviceContextProvider
import com.scouty.app.assistant.domain.AssistantAnswerEvent
import com.scouty.app.assistant.domain.AssistantRuntimeGraph
import com.scouty.app.assistant.domain.AssistantRepository
import com.scouty.app.assistant.domain.OfflineChatModelController
import com.scouty.app.assistant.model.AssistantAction
import com.scouty.app.assistant.model.AssistantConversationState
import com.scouty.app.assistant.model.AssistantMessageUiModel
import com.scouty.app.assistant.model.AssistantResponse
import com.scouty.app.assistant.model.AssistantUiState
import com.scouty.app.assistant.model.GenerationMode
import com.scouty.app.assistant.model.OfflineChatModelState
import com.scouty.app.assistant.model.OfflineChatModelStatus
import com.scouty.app.assistant.model.OfflineChatUiState
import com.scouty.app.assistant.model.SafetyOutcome
import com.scouty.app.assistant.model.assistantDefaultLocale
import com.scouty.app.assistant.model.buildWelcomeMessage
import com.scouty.app.assistant.model.starterPromptsForCurrentLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Temporary kill-switch for the on-device Qwen generation/interpretation path.
 *
 * While we rebuild the knowledge pack around base-case "hub" cards, the local
 * model produces low-quality Romanian and is not used for answering. Retrieval,
 * rerank, the structured tile fallback and all deterministic app-context paths
 * (weather API, gear inspection, trail context) are unaffected. Flip back to
 * `true` to re-enable Qwen once the knowledge-pack work lands.
 */
private const val LOCAL_MODEL_GENERATION_ENABLED = false

class AssistantViewModel(
    private val repository: AssistantRepository,
    private val deviceContextProvider: DeviceContextProvider,
    private val offlineChatModelController: OfflineChatModelController,
    private val chatActionHandler: ChatActionHandler? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()
    private var conversationState = AssistantConversationState()
    private var lastTrailPresent: Boolean = false
    private var remoteFallbackActive: Boolean = false
    private var offlineLoadingDialogDismissed: Boolean = false
    private var dismissedOfflineFinishedEventId: Long = 0L
    private var showOfflineDisableConfirmation: Boolean = false

    init {
        viewModelScope.launch {
            deviceContextProvider.deviceContext.collect { context ->
                val canAttemptOnlineGeneration = repository.canAttemptOnlineGeneration(context)
                if (!canAttemptOnlineGeneration) {
                    remoteFallbackActive = false
                }
                val hasTrail = context.trail != null
                val updatedStarterPrompts = if (hasTrail != lastTrailPresent) {
                    lastTrailPresent = hasTrail
                    val locale = assistantDefaultLocale()
                    starterPromptsForCurrentLocale(
                        locale = locale,
                        hasActiveTrail = hasTrail
                    )
                } else {
                    null
                }
                _uiState.update { state ->
                    state.copy(
                        isOnline = canAttemptOnlineGeneration && !remoteFallbackActive,
                        starterPrompts = updatedStarterPrompts ?: state.starterPrompts
                    )
                }
            }
        }
        viewModelScope.launch {
            offlineChatModelController.state.collect { modelState ->
                if (!modelState.isBusy) {
                    offlineLoadingDialogDismissed = false
                }
                _uiState.update { state ->
                    val canAttemptOnlineGeneration =
                        repository.canAttemptOnlineGeneration(deviceContextProvider.deviceContext.value)
                    state.copy(
                        offlineChat = modelState.toOfflineChatUiState(),
                        isOnline = canAttemptOnlineGeneration && !remoteFallbackActive
                    )
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

    fun resetConversation() {
        conversationState = AssistantConversationState()
        val locale = assistantDefaultLocale()
        _uiState.update {
            it.copy(
                draft = "",
                isResponding = false,
                messages = listOf(buildWelcomeMessage(locale)),
                starterPrompts = starterPromptsForCurrentLocale(
                    locale = locale,
                    hasActiveTrail = lastTrailPresent
                )
            )
        }
        viewModelScope.launch {
            repository.resetConversation(deviceContextProvider.deviceContext.value)
        }
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
            val provisionalId = UUID.randomUUID().toString()
            var provisionalShown = false
            runCatching {
                repository.answerEvents(
                    query = query,
                    context = deviceContextProvider.deviceContext.value,
                    conversationState = conversationState,
                    interactionHandler = chatActionHandler,
                    allowLocalModel = LOCAL_MODEL_GENERATION_ENABLED &&
                        _uiState.value.offlineChat.canUseLocalModel
                ).collect { event ->
                    when (event) {
                        is AssistantAnswerEvent.DraftVisible -> {
                            provisionalShown = true
                            val draftMessage = AssistantMessageUiModel(
                                id = provisionalId,
                                text = event.text,
                                isUser = false,
                                isProvisional = true,
                                citations = event.citations,
                                safetyOutcome = event.safetyOutcome
                            )
                            _uiState.update { state ->
                                state.copy(messages = state.messages + draftMessage)
                            }
                        }

                        is AssistantAnswerEvent.Final -> applyAssistantResponse(
                            response = event.response,
                            messageId = provisionalId,
                            replaceExisting = provisionalShown
                        )

                        is AssistantAnswerEvent.ErrorFallback -> applyAssistantResponse(
                            response = event.response,
                            messageId = provisionalId,
                            replaceExisting = provisionalShown
                        )
                    }
                }
            }.onFailure {
                remoteFallbackActive = repository.canAttemptOnlineGeneration(deviceContextProvider.deviceContext.value)
                val fallbackMessage = AssistantMessageUiModel(
                    id = UUID.randomUUID().toString(),
                    text = "Nu am putut procesa mesajul acesta. Scrie-mi mai simplu ce vrei sau răspunde pe scurt cu situația ta, de exemplu: Căldură, Gătit, Am amnar, Totul e ud.",
                    isUser = false,
                    safetyOutcome = SafetyOutcome.CAUTION
                )
                _uiState.update { state ->
                    state.copy(
                        isOnline = false,
                        isResponding = false,
                        messages = state.messages + fallbackMessage
                    )
                }
            }
        }
    }

    private fun applyAssistantResponse(
        response: AssistantResponse,
        messageId: String,
        replaceExisting: Boolean
    ) {
        conversationState = response.conversationState
        processActions(response.actions)
        val assistantOnline = response.generationMode == GenerationMode.GEMINI_API
        remoteFallbackActive = !assistantOnline &&
            repository.canAttemptOnlineGeneration(deviceContextProvider.deviceContext.value)
        val assistantMessage = AssistantMessageUiModel(
            id = messageId,
            text = response.answerText,
            isUser = false,
            isProvisional = false,
            citations = response.citations,
            safetyOutcome = response.safetyOutcome,
            sections = response.structuredOutput.sections,
            followUpReplies = buildInlineFollowUpReplies(response.structuredOutput.followUpQuestions),
            resolvedTopic = response.structuredOutput.resolvedTopic,
            resolvedFamily = response.structuredOutput.resolvedFamily,
            generationMode = response.generationMode,
            reasoningType = response.reasoningType,
            knowledgePackVersion = response.knowledgePackVersion,
            modelVersion = response.modelVersion,
            modelRuntimeState = response.modelRuntimeState,
            modelStatusDetails = response.modelStatusDetails
        )
        _uiState.update { state ->
            val messages = if (replaceExisting) {
                state.messages.map { message ->
                    if (message.id == messageId) assistantMessage else message
                }
            } else {
                state.messages + assistantMessage
            }
            state.copy(
                isOnline = assistantOnline,
                isResponding = false,
                messages = messages
            )
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
                is AssistantAction.AddGearItems -> {
                    chatActionHandler?.addGearItems(action.items)
                }
                is AssistantAction.RemoveGearItems -> {
                    chatActionHandler?.removeGearItems(action.itemIds)
                }
                is AssistantAction.UpdateGearItems -> {
                    chatActionHandler?.updateGearItems(action.updates)
                }
            }
        }
    }

    fun setOfflineChatEnabled(enabled: Boolean) {
        if (enabled) {
            showOfflineDisableConfirmation = false
            offlineLoadingDialogDismissed = false
            offlineChatModelController.requestEnable()
        } else {
            showOfflineDisableConfirmation = true
            refreshOfflineChatUiState()
        }
    }

    fun confirmOfflineChatMeteredDownload() {
        offlineLoadingDialogDismissed = false
        offlineChatModelController.confirmMeteredDownload()
    }

    fun dismissOfflineChatMeteredDownload() {
        offlineChatModelController.cancelMeteredConfirmation()
    }

    fun dismissOfflineChatLoadingDialog() {
        offlineLoadingDialogDismissed = true
        refreshOfflineChatUiState()
    }

    fun dismissOfflineChatFinishedDialog() {
        dismissedOfflineFinishedEventId = offlineChatModelController.state.value.completedEventId
        refreshOfflineChatUiState()
    }

    fun confirmDisableOfflineChat() {
        showOfflineDisableConfirmation = false
        offlineChatModelController.requestDisable()
        refreshOfflineChatUiState()
    }

    fun dismissDisableOfflineChat() {
        showOfflineDisableConfirmation = false
        refreshOfflineChatUiState()
    }

    private fun refreshOfflineChatUiState() {
        val modelState = offlineChatModelController.state.value
        val canAttemptOnlineGeneration = repository.canAttemptOnlineGeneration(deviceContextProvider.deviceContext.value)
        _uiState.update { state ->
            state.copy(
                offlineChat = modelState.toOfflineChatUiState(),
                isOnline = canAttemptOnlineGeneration && !remoteFallbackActive
            )
        }
    }

    private fun OfflineChatModelState.toOfflineChatUiState(): OfflineChatUiState =
        OfflineChatUiState(
            enabled = enabled,
            status = status,
            progressPercent = progressPercent,
            message = message,
            errorMessage = errorMessage,
            modelSizeBytes = modelSizeBytes,
            completedEventId = completedEventId,
            showMeteredConfirmation = status == OfflineChatModelStatus.WAITING_METERED_CONFIRMATION,
            showLoadingDialog = isBusy && !offlineLoadingDialogDismissed,
            showFinishedDialog = completedEventId > 0L && completedEventId != dismissedOfflineFinishedEventId,
            showDisableConfirmation = showOfflineDisableConfirmation
        )

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
                    offlineChatModelController = runtimeGraph.offlineChatModelController,
                    chatActionHandler = chatActionHandler
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
