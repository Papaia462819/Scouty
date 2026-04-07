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
    val estimatedDuration: String? = null,
    val distanceKm: Double? = null,
    val elevationGain: Int? = null,
    val averageInclinePercent: Double? = null,
    val descriptionRo: String? = null,
    val dailyForecast: List<DailyForecastEntry> = emptyList()
)

data class DailyForecastEntry(
    val date: String,
    val temperatureMax: Double?,
    val temperatureMin: Double?,
    val precipitationProbability: Int?,
    val description: String,
    val sunrise: String?,
    val sunset: String?
)

data class GearContextItem(
    val id: String,
    val name: String,
    val necessity: String,
    val isPacked: Boolean,
    val note: String = ""
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
    val gearItems: List<GearContextItem> = emptyList(),
    val localeTag: String = assistantDefaultLocale().toLanguageTag()
)

data class AssistantCitation(
    val sourceTitle: String,
    val sectionTitle: String,
    val snippet: String,
    val sourceUrl: String? = null,
    val publisher: String? = null
)

data class AssistantOpenQuestion(
    val text: String,
    val targetSlot: String,
    val allowedValues: List<String>,
    val allowedAdditionalSlots: List<String> = emptyList()
)

data class PendingGearAction(
    val packItemIds: List<String> = emptyList(),
    val unpackItemIds: List<String> = emptyList()
)

data class AssistantConversationState(
    val activeTopic: String? = null,
    val lastCardId: String? = null,
    val pendingScenarioCardId: String? = null,
    val facts: Map<String, String> = emptyMap(),
    val askedFollowUps: List<String> = emptyList(),
    val resolvedTerms: List<String> = emptyList(),
    val openQuestion: AssistantOpenQuestion? = null,
    val lastResolvedSlot: String? = null,
    val lastUserMessage: String? = null,
    val lastStandaloneQuery: String? = null,
    val lastRetrievedChunkId: String? = null,
    val lastRetrievedTopic: String? = null,
    val lastRetrievedTitle: String? = null,
    val lastInterpretationConfidence: Double? = null,
    val pendingGearAction: PendingGearAction? = null,
    val lastTrailContextIntent: String? = null
)

data class AssistantQuickReplyUiModel(
    val label: String,
    val query: String
)

data class AssistantMessageUiModel(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val citations: List<AssistantCitation> = emptyList(),
    val safetyOutcome: SafetyOutcome = SafetyOutcome.NORMAL,
    val sections: List<StructuredResponseSection> = emptyList(),
    val followUpReplies: List<AssistantQuickReplyUiModel> = emptyList(),
    val resolvedTopic: String? = null,
    val resolvedFamily: CardFamily? = null,
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

sealed class AssistantAction {
    data class ToggleGearPacked(
        val itemIds: List<String>,
        val packed: Boolean
    ) : AssistantAction()
}

data class AssistantResponse(
    val answerText: String,
    val structuredOutput: StructuredAssistantOutput,
    val citations: List<AssistantCitation>,
    val safetyOutcome: SafetyOutcome,
    val generationMode: GenerationMode,
    val reasoningType: ReasoningType,
    val conversationState: AssistantConversationState = AssistantConversationState(),
    val modelVersion: String? = null,
    val modelRuntimeState: ModelRuntimeState? = null,
    val modelStatusDetails: String? = null,
    val knowledgePackVersion: String? = null,
    val usedFallback: Boolean = false,
    val actions: List<AssistantAction> = emptyList()
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

fun starterPromptsForCurrentLocale(
    locale: Locale = assistantDefaultLocale(),
    hasActiveTrail: Boolean = false
): List<String> =
    if (locale.language == "ro") {
        if (hasActiveTrail) {
            listOf(
                "Spune-mi despre traseul activ",
                "Care e dificultatea traseului?",
                "Ce echipament am nevoie?",
                "Cum va fi vremea?"
            )
        } else {
            listOf(
                "Mi-am sucit glezna",
                "Care e marcajul traseului activ?",
                "Ce echipament sa tin la indemana acum?",
                "Cum fac focul?"
            )
        }
    } else {
        if (hasActiveTrail) {
            listOf(
                "Tell me about the active trail",
                "What is the trail difficulty?",
                "What gear do I need?",
                "What will the weather be like?"
            )
        } else {
            listOf(
                "I twisted my ankle",
                "What is the marker for my active trail?",
                "What gear should I keep ready now?",
                "How do I make a fire?"
            )
        }
    }
