package com.scouty.app.assistant.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scouty.app.assistant.data.DeviceContextProvider
import com.scouty.app.assistant.domain.AssistantRuntimeGraph
import com.scouty.app.assistant.domain.AssistantRepository
import com.scouty.app.assistant.model.AssistantMessageUiModel
import com.scouty.app.assistant.model.AssistantUiState
import com.scouty.app.assistant.model.SafetyOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class AssistantViewModel(
    private val repository: AssistantRepository,
    private val deviceContextProvider: DeviceContextProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

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
                repository.answer(query, deviceContextProvider.deviceContext.value)
            }.onSuccess { response ->
                val assistantMessage = AssistantMessageUiModel(
                    id = UUID.randomUUID().toString(),
                    text = response.answerText,
                    isUser = false,
                    citations = response.citations,
                    safetyOutcome = response.safetyOutcome,
                    sections = response.structuredOutput.sections,
                    generationMode = response.generationMode,
                    reasoningType = response.reasoningType,
                    knowledgePackVersion = response.knowledgePackVersion,
                    modelVersion = response.modelVersion,
                    modelRuntimeState = response.modelRuntimeState,
                    modelStatusDetails = response.modelStatusDetails
                )
                _uiState.update { state ->
                    state.copy(
                        isResponding = false,
                        messages = state.messages + assistantMessage
                    )
                }
            }.onFailure {
                val fallbackMessage = AssistantMessageUiModel(
                    id = UUID.randomUUID().toString(),
                    text = "Assistant-ul local nu a putut răspunde acum. Verifică semnalul GPS, bateria și încearcă din nou.",
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

    class Factory(
        private val application: Application,
        private val deviceContextProvider: DeviceContextProvider
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AssistantViewModel::class.java)) {
                val runtimeGraph = AssistantRuntimeGraph.get(application)
                return AssistantViewModel(
                    repository = runtimeGraph.repository,
                    deviceContextProvider = deviceContextProvider
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
