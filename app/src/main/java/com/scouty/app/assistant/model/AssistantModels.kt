package com.scouty.app.assistant.model

import java.util.Locale

    private val AssistantDefaultLocale: Locale = Locale.forLanguageTag("ro-RO")

fun assistantDefaultLocale(): Locale = AssistantDefaultLocale

enum class SafetyOutcome {
    NORMAL,
    CAUTION,
    EMERGENCY_ESCALATION
}

data class TrailContextSnapshot(
    val name: String,
    val localCode: String? = null,
    val region: String? = null,
    val fromName: String? = null,
    val toName: String? = null,
    val markingLabel: String? = null,
    val routeSummary: String? = null,
    val sourceUrls: List<String> = emptyList(),
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
    val recommendedGear: List<String> = emptyList(),
    val localeTag: String = assistantDefaultLocale().toLanguageTag()
)

data class AssistantCitation(
    val sourceTitle: String,
    val sectionTitle: String,
    val snippet: String,
    val sourceUrl: String? = null,
    val publisher: String? = null
)

data class AssistantMessageUiModel(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val citations: List<AssistantCitation> = emptyList(),
    val safetyOutcome: SafetyOutcome = SafetyOutcome.NORMAL,
    val sections: List<StructuredResponseSection> = emptyList(),
    val generationMode: GenerationMode? = null,
    val reasoningType: ReasoningType? = null,
    val knowledgePackVersion: String? = null,
    val modelVersion: String? = null,
    val modelRuntimeState: ModelRuntimeState? = null,
    val modelStatusDetails: String? = null
)

data class AssistantUiState(
    val draft: String = "",
    val isResponding: Boolean = false,
    val messages: List<AssistantMessageUiModel> = listOf(buildWelcomeMessage(assistantDefaultLocale())),
    val starterPrompts: List<String> = starterPromptsForCurrentLocale(assistantDefaultLocale())
)

data class AssistantResponse(
    val answerText: String,
    val structuredOutput: StructuredAssistantOutput,
    val citations: List<AssistantCitation>,
    val safetyOutcome: SafetyOutcome,
    val generationMode: GenerationMode,
    val reasoningType: ReasoningType,
    val modelVersion: String? = null,
    val modelRuntimeState: ModelRuntimeState? = null,
    val modelStatusDetails: String? = null,
    val knowledgePackVersion: String? = null,
    val usedFallback: Boolean = false
)

fun buildWelcomeMessage(locale: Locale = assistantDefaultLocale()): AssistantMessageUiModel =
    AssistantMessageUiModel(
        id = "welcome",
        text = if (locale.language == "ro") {
            "Spune-mi ce problema ai pe traseu si iti raspund din knowledge pack-ul offline al aplicatiei."
        } else {
            "Tell me what happened on the trail and I will answer from the app's offline knowledge pack."
        },
        isUser = false
    )

fun starterPromptsForCurrentLocale(locale: Locale = assistantDefaultLocale()): List<String> =
    if (locale.language == "ro") {
        listOf(
            "Mi-am sucit glezna",
            "Care e marcajul traseului activ?",
            "Ce echipament sa tin la indemana acum?"
        )
    } else {
        listOf(
            "I twisted my ankle",
            "What is the marker for my active trail?",
            "What gear should I keep ready now?"
        )
    }
