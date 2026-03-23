package com.scouty.app.assistant.model

import java.util.Locale

enum class SafetyOutcome {
    NORMAL,
    CAUTION,
    EMERGENCY_ESCALATION
}

data class TrailContextSnapshot(
    val name: String,
    val region: String? = null,
    val sunsetTime: String? = null,
    val weatherForecast: String? = null,
    val difficulty: String? = null,
    val estimatedDuration: String? = null
)

data class DeviceContextSnapshot(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val accuracyMeters: Float? = null,
    val batteryPercent: Int = 0,
    val batterySafe: Boolean = false,
    val isOnline: Boolean = false,
    val gpsFixed: Boolean = false,
    val trail: TrailContextSnapshot? = null,
    val localeTag: String = Locale.getDefault().toLanguageTag()
)

data class AssistantCitation(
    val sourceTitle: String,
    val sectionTitle: String,
    val snippet: String
)

data class AssistantMessageUiModel(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val citations: List<AssistantCitation> = emptyList(),
    val safetyOutcome: SafetyOutcome = SafetyOutcome.NORMAL
)

data class AssistantUiState(
    val draft: String = "",
    val isResponding: Boolean = false,
    val messages: List<AssistantMessageUiModel> = listOf(buildWelcomeMessage()),
    val starterPrompts: List<String> = starterPromptsForCurrentLocale()
)

data class AssistantResponse(
    val answerText: String,
    val citations: List<AssistantCitation>,
    val safetyOutcome: SafetyOutcome,
    val usedFallback: Boolean = false
)

fun buildWelcomeMessage(locale: Locale = Locale.getDefault()): AssistantMessageUiModel =
    AssistantMessageUiModel(
        id = "welcome",
        text = if (locale.language == "ro") {
            "Spune-mi ce problemă ai pe traseu și îți răspund pe baza ghidurilor locale offline."
        } else {
            "Tell me what happened on the trail and I will answer using the local offline field guides."
        },
        isUser = false
    )

fun starterPromptsForCurrentLocale(locale: Locale = Locale.getDefault()): List<String> =
    if (locale.language == "ro") {
        listOf(
            "Mi-am sucit glezna",
            "Cum pot purifica apa?",
            "Urme de urs în apropiere, ce fac?"
        )
    } else {
        listOf(
            "I twisted my ankle",
            "How can I purify water?",
            "Bear signs nearby, what do I do?"
        )
    }
