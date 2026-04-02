package com.scouty.app.assistant.domain

import com.scouty.app.assistant.data.buildSearchTokens
import com.scouty.app.assistant.model.AssistantConversationState
import com.scouty.app.assistant.model.AssistantOpenQuestion
import com.scouty.app.assistant.model.ModelRuntimeState
import com.scouty.app.assistant.model.ModelStatus
import com.scouty.app.assistant.model.QueryAnalysis
import com.scouty.app.assistant.model.StructuredAssistantOutput
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer

data class DeterministicPreprocessingResult(
    val normalizedQuery: String,
    val obviousSlotUpdates: Map<String, String> = emptyMap(),
    val topicHints: List<String> = emptyList(),
    val safetyFlags: Set<String> = emptySet(),
    val hasPronounReference: Boolean = false,
    val isFragmented: Boolean = false,
    val isFollowUpShortAnswer: Boolean = false
)

class DeterministicAssistantPreprocessor {
    fun preprocess(
        query: String,
        conversationState: AssistantConversationState,
        queryAnalysis: QueryAnalysis
    ): DeterministicPreprocessingResult {
        val normalizedQuery = normalizeInterpreterText(query)
        val tokens = buildSearchTokens(query, shouldLog = false)
        val openQuestion = conversationState.openQuestion
        val obviousSlotUpdates = buildMap {
            detectOpenQuestionUpdate(normalizedQuery, openQuestion)?.let { putAll(it) }
            detectGeneralCampfireUpdate(normalizedQuery)?.forEach { (slot, value) ->
                putIfAbsent(slot, value)
            }
        }
        val topicHints = buildList {
            if (containsAny(normalizedQuery, "urs", "ursi", "bear", "bears")) {
                add("wildlife")
            }
            if (containsAny(normalizedQuery, "traseu", "trail", "ruta", "route")) {
                add("trail")
            }
            if (containsAny(normalizedQuery, "bocanci", "boots", "aderen", "traction", "slipping", "alunec", "rock", "rocks", "stanca", "stanci")) {
                add("traction")
            }
            conversationState.lastRetrievedTitle
                ?.takeIf { hasPronounReference(normalizedQuery) }
                ?.let(::add)
        }.distinct()

        return DeterministicPreprocessingResult(
            normalizedQuery = normalizedQuery,
            obviousSlotUpdates = obviousSlotUpdates,
            topicHints = topicHints,
            safetyFlags = queryAnalysis.safetyTags,
            hasPronounReference = hasPronounReference(normalizedQuery),
            isFragmented = tokens.size <= 4 && !containsSentenceVerb(normalizedQuery),
            isFollowUpShortAnswer = openQuestion != null && tokens.size <= 5
        )
    }

    private fun detectOpenQuestionUpdate(
        normalizedQuery: String,
        openQuestion: AssistantOpenQuestion?
    ): Map<String, String>? {
        val question = openQuestion ?: return null
        return when (question.targetSlot) {
            "fuel_condition" -> when {
                containsAny(normalizedQuery, "tocmai ce a plouat", "a plouat", "ploua", "ud", "leoarca", "wet") ->
                    mapOf("fuel_condition" to "wet")
                containsAny(normalizedQuery, "umed", "umezeala", "damp") ->
                    mapOf("fuel_condition" to "damp")
                containsAny(normalizedQuery, "uscat", "dry") ->
                    mapOf("fuel_condition" to "dry")
                containsAny(normalizedQuery, "nu stiu", "unknown") ->
                    mapOf("fuel_condition" to "unknown")
                else -> null
            }

            "ignition_source" -> when {
                containsAny(normalizedQuery, "bricheta", "lighter") ->
                    mapOf("ignition_source" to "lighter")
                containsAny(normalizedQuery, "chibrit", "matches", "match") ->
                    mapOf("ignition_source" to "matches")
                containsAny(normalizedQuery, "amnar", "ferro") ->
                    mapOf("ignition_source" to "ferro")
                containsAny(normalizedQuery, "scanteie", "spark") ->
                    mapOf("ignition_source" to "recognized_spark")
                containsAny(normalizedQuery, "nimic", "nu am", "n am", "none") ->
                    mapOf("ignition_source" to "none")
                else -> null
            }

            "goal" -> when {
                containsAny(normalizedQuery, "caldura", "incalz", "warmth") ->
                    mapOf("goal" to "warmth")
                containsAny(normalizedQuery, "gatit", "mancare", "cook", "cooking") ->
                    mapOf("goal" to "cooking")
                containsAny(normalizedQuery, "fiert apa", "fierb apa", "boil water") ->
                    mapOf("goal" to "boil_water")
                else -> null
            }

            "kindling_available" -> when {
                containsAny(normalizedQuery, "am surcele", "am crengute", "am betisoare") ->
                    mapOf("kindling_available" to "yes")
                containsAny(normalizedQuery, "nu am surcele", "nu gasesc crengute", "nu gasesc betisoare") ->
                    mapOf("kindling_available" to "no")
                else -> null
            }

            "wind" -> when {
                containsAny(normalizedQuery, "vant puternic", "bate tare", "foarte tare", "vijelie", "rafale") ->
                    mapOf("wind" to "high")
                containsAny(normalizedQuery, "nu bate", "nu e vant", "liniste", "calm", "deloc") ->
                    mapOf("wind" to "low")
                containsAny(normalizedQuery, "nu tare", "putin", "usor", "vanticel", "bate dar", "ma descurc", "moderat") ->
                    mapOf("wind" to "moderate")
                containsAny(normalizedQuery, "bate", "vant", "da") ->
                    mapOf("wind" to "moderate")
                else -> null
            }

            else -> null
        }
    }

    private fun detectGeneralCampfireUpdate(normalizedQuery: String): Map<String, String>? =
        when {
            containsAny(normalizedQuery, "tocmai ce a plouat", "a plouat", "ploua", "ud leoarca", "totul e ud", "tot e ud") ->
                mapOf("fuel_condition" to "wet")
            containsAny(normalizedQuery, "uscat", "dry") ->
                mapOf("fuel_condition" to "dry")
            else -> null
        }

    private fun hasPronounReference(normalizedQuery: String): Boolean =
        PronounPatterns.any { pattern -> pattern in normalizedQuery }

    private fun containsSentenceVerb(normalizedQuery: String): Boolean =
        VerbMarkers.any { marker -> marker in normalizedQuery }

    private fun containsAny(normalized: String, vararg terms: String): Boolean =
        terms.any { normalizeInterpreterText(it) in normalized }

    private companion object {
        private val PronounPatterns = setOf(
            " pe el ",
            " pe ea ",
            " pe asta ",
            " on it ",
            " it ",
            " asta ",
            " acolo ",
            " cum? ",
            " cum ? "
        )

        private val VerbMarkers = setOf(
            " este ",
            " e ",
            " sunt ",
            " fac ",
            " pot ",
            " vreau ",
            " am ",
            " are ",
            " is ",
            " are ",
            " do ",
            " should ",
            " can "
        )
    }
}

enum class RetrievalConfidenceTier(val rank: Int) {
    LOW(0),
    MEDIUM(1),
    HIGH(2)
}

data class RetrievalConfidenceAssessment(
    val score: Double,
    val tier: RetrievalConfidenceTier,
    val top1Strength: Double,
    val margin: Double,
    val channelAgreement: Double,
    val slotCoverage: Double,
    val continuity: Double,
    val contradictionPenalty: Double
)

data class CampfireRetrievalSignals(
    val primaryCardId: String?,
    val top1Score: Double,
    val top2Score: Double,
    val slotCompatibility: Double,
    val conversationCarryOver: Double,
    val semanticSimilarity: Double,
    val lexicalHints: Double,
    val extractedFactCount: Int
)

class RetrievalConfidencePolicy {
    fun assessStandard(
        query: String,
        queryAnalysis: QueryAnalysis,
        conversationState: AssistantConversationState,
        retrieved: List<RetrievedChunk>,
        preprocessing: DeterministicPreprocessingResult
    ): RetrievalConfidenceAssessment {
        val top1 = retrieved.firstOrNull()
        val top2 = retrieved.getOrNull(1)
        if (top1 == null) {
            return RetrievalConfidenceAssessment(
                score = 0.0,
                tier = RetrievalConfidenceTier.LOW,
                top1Strength = 0.0,
                margin = 0.0,
                channelAgreement = 0.0,
                slotCoverage = 0.0,
                continuity = 0.0,
                contradictionPenalty = 0.4
            )
        }

        val top1Strength = ((top1.score - 16).toDouble() / 42.0).coerceIn(0.0, 1.0)
        val margin = if (top2 == null) {
            1.0
        } else {
            ((top1.score - top2.score).toDouble() / 20.0).coerceIn(0.0, 1.0)
        }
        val lexicalCoverage = lexicalCoverage(query, top1)
        val domainAgreement = if (queryAnalysis.domainHints.any { it.domain == top1.domain }) 1.0 else 0.35
        val languageAgreement = if (top1.language == queryAnalysis.preferredLanguage) 1.0 else 0.25
        val channelAgreement = ((lexicalCoverage * 0.5) + (domainAgreement * 0.3) + (languageAgreement * 0.2))
            .coerceIn(0.0, 1.0)
        val slotCoverage = when {
            conversationState.openQuestion == null -> 0.55
            conversationState.openQuestion.targetSlot in preprocessing.obviousSlotUpdates -> 1.0
            else -> 0.2
        }
        val continuity = when {
            conversationState.lastRetrievedChunkId == top1.chunkId -> 1.0
            !conversationState.lastRetrievedTopic.isNullOrBlank() &&
                conversationState.lastRetrievedTopic.equals(top1.topic, ignoreCase = true) -> 0.9
            !conversationState.lastRetrievedTitle.isNullOrBlank() &&
                normalizeInterpreterText(conversationState.lastRetrievedTitle).contains(normalizeInterpreterText(top1.sectionTitle)) -> 0.75
            conversationState.lastUserMessage == null -> 0.6
            else -> 0.3
        }
        val contradictionPenalty = contradictionPenalty(preprocessing, continuity, top1Strength)
        val score =
            (top1Strength * 0.34) +
                (margin * 0.22) +
                (channelAgreement * 0.2) +
                (slotCoverage * 0.08) +
                (continuity * 0.16) -
                contradictionPenalty

        return RetrievalConfidenceAssessment(
            score = score.coerceIn(0.0, 1.0),
            tier = confidenceTier(score),
            top1Strength = top1Strength,
            margin = margin,
            channelAgreement = channelAgreement,
            slotCoverage = slotCoverage,
            continuity = continuity,
            contradictionPenalty = contradictionPenalty
        )
    }

    fun assessCampfire(
        signals: CampfireRetrievalSignals,
        conversationState: AssistantConversationState,
        preprocessing: DeterministicPreprocessingResult
    ): RetrievalConfidenceAssessment {
        val top1Strength = (signals.top1Score / 85.0).coerceIn(0.0, 1.0)
        val margin = if (signals.top2Score <= 0.0) {
            1.0
        } else {
            ((signals.top1Score - signals.top2Score) / 24.0).coerceIn(0.0, 1.0)
        }
        val channelAgreement =
            ((signals.slotCompatibility * 0.35) +
                (signals.conversationCarryOver * 0.2) +
                (signals.semanticSimilarity * 0.25) +
                (signals.lexicalHints * 0.2))
                .coerceIn(0.0, 1.0)
        val slotCoverage = when {
            conversationState.openQuestion == null && signals.extractedFactCount > 0 -> 0.75
            conversationState.openQuestion == null -> 0.45
            conversationState.openQuestion.targetSlot in preprocessing.obviousSlotUpdates -> 1.0
            signals.extractedFactCount > conversationState.facts.size -> 0.7
            else -> 0.25
        }
        val continuity = if (conversationState.activeTopic == "campfire") 0.9 else 0.6
        val contradictionPenalty = contradictionPenalty(preprocessing, continuity, top1Strength) / 2.0
        val score =
            (top1Strength * 0.3) +
                (margin * 0.24) +
                (channelAgreement * 0.24) +
                (slotCoverage * 0.12) +
                (continuity * 0.1) -
                contradictionPenalty

        return RetrievalConfidenceAssessment(
            score = score.coerceIn(0.0, 1.0),
            tier = confidenceTier(score),
            top1Strength = top1Strength,
            margin = margin,
            channelAgreement = channelAgreement,
            slotCoverage = slotCoverage,
            continuity = continuity,
            contradictionPenalty = contradictionPenalty
        )
    }

    fun shouldAcceptRewrite(
        before: RetrievalConfidenceAssessment,
        after: RetrievalConfidenceAssessment,
        interpretation: ValidatedInterpretation
    ): Boolean {
        if (interpretation.slotUpdates.isNotEmpty() && interpretation.resolvedOpenQuestion) {
            return after.score >= before.score - 0.05
        }
        if (interpretation.standaloneQuery.isNullOrBlank()) {
            return false
        }
        if (after.tier.rank > before.tier.rank) {
            return true
        }
        return after.score >= before.score + 0.04 ||
            (after.top1Strength > before.top1Strength + 0.04 && after.margin >= before.margin)
    }

    private fun lexicalCoverage(
        query: String,
        top1: RetrievedChunk
    ): Double {
        val tokens = buildSearchTokens(query, shouldLog = false)
        if (tokens.isEmpty()) {
            return 0.0
        }
        val haystack = normalizeInterpreterText(
            listOf(top1.sectionTitle, top1.body, top1.topic, top1.sourceTitle).joinToString(" ")
        )
        val matched = tokens.count { token -> token in haystack }
        return (matched.toDouble() / tokens.size.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun contradictionPenalty(
        preprocessing: DeterministicPreprocessingResult,
        continuity: Double,
        top1Strength: Double
    ): Double =
        when {
            preprocessing.hasPronounReference && continuity < 0.5 -> 0.34
            preprocessing.isFollowUpShortAnswer && top1Strength < 0.6 -> 0.24
            preprocessing.isFragmented && top1Strength < 0.55 -> 0.18
            else -> 0.0
        }

    private fun confidenceTier(score: Double): RetrievalConfidenceTier =
        when {
            score >= 0.72 -> RetrievalConfidenceTier.HIGH
            score >= 0.45 -> RetrievalConfidenceTier.MEDIUM
            else -> RetrievalConfidenceTier.LOW
        }
}

data class InterpreterGateDecision(
    val shouldInvoke: Boolean,
    val reason: String,
    val requiresRewriteComparison: Boolean
)

class InterpreterGate(
    private val confidencePolicy: RetrievalConfidencePolicy = RetrievalConfidencePolicy()
) {
    fun decide(
        assessment: RetrievalConfidenceAssessment,
        preprocessing: DeterministicPreprocessingResult,
        conversationState: AssistantConversationState
    ): InterpreterGateDecision {
        val ambiguous = preprocessing.hasPronounReference ||
            preprocessing.isFragmented ||
            preprocessing.isFollowUpShortAnswer
        val unresolvedOpenQuestion = conversationState.openQuestion != null &&
            conversationState.openQuestion.targetSlot !in preprocessing.obviousSlotUpdates
        return when {
            assessment.tier == RetrievalConfidenceTier.LOW ->
                InterpreterGateDecision(
                    shouldInvoke = true,
                    reason = "low_confidence",
                    requiresRewriteComparison = true
                )

            assessment.tier == RetrievalConfidenceTier.MEDIUM && (ambiguous || unresolvedOpenQuestion) ->
                InterpreterGateDecision(
                    shouldInvoke = true,
                    reason = if (unresolvedOpenQuestion) "open_question_resolution" else "ambiguous_medium_confidence",
                    requiresRewriteComparison = true
                )

            unresolvedOpenQuestion && assessment.score < 0.86 ->
                InterpreterGateDecision(
                    shouldInvoke = true,
                    reason = "structured_follow_up_resolution",
                    requiresRewriteComparison = false
                )

            else ->
                InterpreterGateDecision(
                    shouldInvoke = false,
                    reason = "deterministic_path_sufficient",
                    requiresRewriteComparison = false
                )
        }
    }
}

object CampfireSlotCatalog {
    val allowedValues: Map<String, Set<String>> = mapOf(
        "goal" to setOf("warmth", "cooking", "boil_water"),
        "ignition_source" to setOf("lighter", "matches", "ferro", "recognized_spark", "none"),
        "tinder_available" to setOf("yes", "no"),
        "tinder_material" to setOf("paper", "tissue", "cotton", "lint"),
        "tinder_condition" to setOf("dry", "damp", "wet", "unavailable"),
        "kindling_available" to setOf("yes", "no"),
        "fuel_condition" to setOf("dry", "damp", "wet", "scarce", "unknown"),
        "wind" to setOf("high", "moderate", "low"),
        "permission" to setOf("forbidden", "unknown"),
        "ground_risk" to setOf("roots_or_peat", "dry_vegetation", "indoor_or_tent", "safe"),
        "tinder_strategy" to setOf("improvise"),
        "need_level" to setOf("necessary", "optional"),
        "daylight" to setOf("low", "dark", "enough"),
        "fatigue" to setOf("high", "moderate"),
        "compromised_item" to setOf("lighter", "matches", "ferro", "recognized_spark"),
        "compromised_reason" to setOf("lost", "broken", "unusable")
    )

    fun isKnownSlot(slot: String): Boolean = slot in allowedValues

    fun isAllowedValue(slot: String, value: String): Boolean =
        allowedValues[slot]?.contains(value) == true
}

data class InterpreterRequest(
    val query: String,
    val preferredLanguage: String,
    val queryAnalysis: QueryAnalysis,
    val conversationState: AssistantConversationState,
    val retrievalConfidence: RetrievalConfidenceAssessment,
    val preprocessing: DeterministicPreprocessingResult,
    val activeTrailLabel: String? = null
)

data class InterpreterExecutionResult(
    val rawOutput: String? = null,
    val modelStatus: ModelStatus,
    val error: String? = null
) {
    val isAvailable: Boolean
        get() = error == null && !rawOutput.isNullOrBlank()
}

interface SlmInterpreterEngine {
    suspend fun interpret(request: InterpreterRequest): InterpreterExecutionResult
}

class OnDeviceTranslationEngine {
    fun toEnglishControlText(text: String?): String? {
        val value = text?.trim()?.takeIf { it.isNotBlank() } ?: return null
        var translated = value.lowercase()
        TranslationMap.forEach { (source, target) ->
            translated = translated.replace(source, target)
        }
        return translated.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private companion object {
        private val TranslationMap = linkedMapOf(
            "tocmai ce a plouat" to "it just rained",
            "a plouat" to "it rained",
            "ploua" to "it is raining",
            "e ud" to "it is wet",
            "este ud" to "it is wet",
            "uscat" to "dry",
            "umed" to "damp",
            "bricheta" to "lighter",
            "chibrite" to "matches",
            "chibrit" to "matches",
            "amnar" to "ferro rod",
            "foc" to "campfire",
            "caldura" to "warmth",
            "gatit" to "cooking",
            "fiert apa" to "boil water",
            "ursi" to "bears",
            "urs" to "bear",
            "traseu" to "trail",
            "bocanci" to "boots",
            "alunec" to "slipping",
            "pietre ude" to "wet rocks",
            "stanci ude" to "wet rocks"
        )
    }
}

class InterpreterPromptBuilder(
    private val translationEngine: OnDeviceTranslationEngine = OnDeviceTranslationEngine(),
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false }
) {
    fun build(request: InterpreterRequest): String {
        val context = InterpreterPromptContext(
            userLanguage = request.preferredLanguage,
            userMessage = sanitizePromptLine(request.query, 240),
            userMessageEnglishGloss = translationEngine.toEnglishControlText(request.query),
            lastUserMessage = request.conversationState.lastUserMessage?.let { sanitizePromptLine(it, 180) },
            lastStandaloneQuery = request.conversationState.lastStandaloneQuery?.let { sanitizePromptLine(it, 180) },
            lastRetrievedTitle = request.conversationState.lastRetrievedTitle?.let { sanitizePromptLine(it, 180) },
            lastRetrievedTopic = request.conversationState.lastRetrievedTopic,
            activeTopic = request.conversationState.activeTopic,
            activeTrailLabel = request.activeTrailLabel?.let { sanitizePromptLine(it, 180) },
            openQuestion = request.conversationState.openQuestion?.let { openQuestion ->
                InterpreterPromptOpenQuestion(
                    text = sanitizePromptLine(openQuestion.text, 180),
                    textEnglishGloss = translationEngine.toEnglishControlText(openQuestion.text),
                    targetSlot = openQuestion.targetSlot,
                    allowedValues = openQuestion.allowedValues,
                    allowedAdditionalSlots = openQuestion.allowedAdditionalSlots
                )
            },
            confirmedFacts = request.conversationState.facts,
            requestedTask = when {
                request.conversationState.openQuestion != null -> "follow_up_resolution_and_standalone_rewrite"
                request.preprocessing.hasPronounReference || request.preprocessing.isFragmented ->
                    "standalone_rewrite_and_topic_hint"
                else -> "standalone_rewrite"
            },
            confidence = InterpreterPromptConfidence(
                score = request.retrievalConfidence.score,
                tier = request.retrievalConfidence.tier.name,
                top1Strength = request.retrievalConfidence.top1Strength,
                margin = request.retrievalConfidence.margin,
                continuity = request.retrievalConfidence.continuity
            )
        )
        val contextJson = json.encodeToString(InterpreterPromptContext.serializer(), context)
        return buildString {
            appendLine("You are Scouty's local interpretation layer.")
            appendLine("You are not the final answering assistant and you do not decide retrieval ranking.")
            appendLine("Return exactly one compact JSON object with this schema:")
            appendLine("{\"standalone_query\":\"string or null\",\"topic_hint\":\"string or null\",\"intent\":\"string or null\",\"slot_updates\":{\"slot_name\":\"canonical_value\"},\"resolved_open_question\":true|false,\"needs_clarification\":true|false,\"clarification_target\":\"string or null\",\"confidence\":0.0}")
            appendLine("Rules:")
            appendLine("- Do not answer the user.")
            appendLine("- Do not invent facts not grounded in the current user message or the confirmed facts.")
            appendLine("- When open_question is present, prefer resolving only that target slot.")
            appendLine("- Use only canonical slot names and canonical English slot values.")
            appendLine("- If user_language is ro, standalone_query must stay in Romanian for retrieval compatibility.")
            appendLine("- If the message is too ambiguous, set needs_clarification=true and keep slot_updates empty.")
            appendLine("- Confidence must be between 0.0 and 1.0.")
            appendLine("CONTEXT_JSON:")
            appendLine(contextJson)
            appendLine("Return the JSON object now.")
        }
    }
}

class OnDeviceSlmInterpreterEngine(
    private val modelManager: ModelManager,
    private val promptBuilder: InterpreterPromptBuilder = InterpreterPromptBuilder()
) : SlmInterpreterEngine {
    override suspend fun interpret(request: InterpreterRequest): InterpreterExecutionResult {
        val status = if (modelManager.currentStatus().state == ModelRuntimeState.LOADED) {
            modelManager.currentStatus()
        } else {
            modelManager.ensureLoaded()
        }
        if (status.state != ModelRuntimeState.LOADED) {
            return InterpreterExecutionResult(
                modelStatus = status,
                error = status.details
            )
        }
        return runCatching {
            val raw = modelManager.generate(promptBuilder.build(request))
            InterpreterExecutionResult(
                rawOutput = raw.text,
                modelStatus = raw.modelStatus
            )
        }.getOrElse { error ->
            InterpreterExecutionResult(
                modelStatus = modelManager.currentStatus(),
                error = error.message ?: error::class.java.simpleName
            )
        }
    }
}

data class ValidatedInterpretation(
    val standaloneQuery: String? = null,
    val topicHint: String? = null,
    val intent: String? = null,
    val slotUpdates: Map<String, String> = emptyMap(),
    val resolvedOpenQuestion: Boolean = false,
    val needsClarification: Boolean = false,
    val clarificationTarget: String? = null,
    val confidence: Double = 0.0
)

class InterpreterOutputValidator(
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false }
) {
    fun validate(
        request: InterpreterRequest,
        execution: InterpreterExecutionResult
    ): ValidatedInterpretation? {
        if (!execution.isAvailable) {
            return null
        }
        val payload = runCatching {
            val rawPayload = extractFirstJsonObject(execution.rawOutput.orEmpty())
            json.decodeFromString(InterpreterModelPayload.serializer(), rawPayload)
        }.getOrNull() ?: return null

        val minimumConfidence = if (request.conversationState.openQuestion != null) 0.74 else 0.62
        if (payload.confidence < minimumConfidence) {
            return null
        }

        val allowedSlots = request.conversationState.openQuestion?.let { question ->
            setOf(question.targetSlot) + question.allowedAdditionalSlots
        }
        val filteredUpdates = payload.slotUpdates
            .mapNotNull { (slot, value) ->
                val canonicalSlot = slot.trim()
                val canonicalValue = value.trim()
                when {
                    !CampfireSlotCatalog.isKnownSlot(canonicalSlot) -> null
                    allowedSlots != null && canonicalSlot !in allowedSlots -> null
                    !CampfireSlotCatalog.isAllowedValue(canonicalSlot, canonicalValue) -> null
                    conflictsWithConfirmedFact(
                        slot = canonicalSlot,
                        value = canonicalValue,
                        conversationState = request.conversationState
                    ) -> null
                    else -> canonicalSlot to canonicalValue
                }
            }.toMap()

        val standaloneQuery = payload.standaloneQuery
            ?.trim()
            ?.takeIf {
                it.isNotBlank() &&
                    !normalizeInterpreterText(it).equals(normalizeInterpreterText(request.query), ignoreCase = true)
            }
            ?.take(220)

        return ValidatedInterpretation(
            standaloneQuery = standaloneQuery,
            topicHint = payload.topicHint?.trim()?.takeIf { it.isNotBlank() }?.take(80),
            intent = payload.intent?.trim()?.takeIf { it.isNotBlank() }?.take(80),
            slotUpdates = filteredUpdates,
            resolvedOpenQuestion = payload.resolvedOpenQuestion && filteredUpdates.isNotEmpty(),
            needsClarification = payload.needsClarification && filteredUpdates.isEmpty(),
            clarificationTarget = payload.clarificationTarget?.trim()?.takeIf { it.isNotBlank() }?.take(80),
            confidence = payload.confidence
        )
    }

    private fun conflictsWithConfirmedFact(
        slot: String,
        value: String,
        conversationState: AssistantConversationState
    ): Boolean {
        val existing = conversationState.facts[slot] ?: return false
        val openQuestionTarget = conversationState.openQuestion?.targetSlot
        return existing != value && slot != openQuestionTarget
    }
}

data class GroundedRetrievalPlan(
    val retrievalQuery: String,
    val topicHint: String? = null,
    val slotUpdates: Map<String, String> = emptyMap()
)

class GroundedQueryBuilder {
    fun build(
        originalQuery: String,
        interpretation: ValidatedInterpretation?,
        preprocessing: DeterministicPreprocessingResult
    ): GroundedRetrievalPlan {
        val rewritten = interpretation?.standaloneQuery?.takeIf { it.isNotBlank() }
        val topicHint = interpretation?.topicHint ?: preprocessing.topicHints.firstOrNull()
        return GroundedRetrievalPlan(
            retrievalQuery = rewritten ?: originalQuery,
            topicHint = topicHint,
            slotUpdates = preprocessing.obviousSlotUpdates + (interpretation?.slotUpdates.orEmpty())
        )
    }
}

data class GroundedWordingRequest(
    val query: String,
    val preferredLanguage: String,
    val deterministicOutput: StructuredAssistantOutput,
    val retrievedChunks: List<RetrievedChunk>
)

data class GroundedWordingResult(
    val summary: String,
    val context: String? = null
)

interface GroundedWordingEngine {
    suspend fun rephrase(request: GroundedWordingRequest): GroundedWordingResult?
}

class OnDeviceGroundedWordingEngine(
    private val modelManager: ModelManager,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false }
) : GroundedWordingEngine {
    override suspend fun rephrase(request: GroundedWordingRequest): GroundedWordingResult? {
        val status = if (modelManager.currentStatus().state == ModelRuntimeState.LOADED) {
            modelManager.currentStatus()
        } else {
            modelManager.ensureLoaded()
        }
        if (status.state != ModelRuntimeState.LOADED) {
            return null
        }

        val prompt = buildPrompt(request)
        return runCatching {
            val raw = modelManager.generate(prompt)
            val payload = json.decodeFromString(
                GroundedWordingPayload.serializer(),
                extractFirstJsonObject(raw.text)
            )
            val summary = payload.summary.trim().takeIf { it.isNotBlank() } ?: return null
            GroundedWordingResult(
                summary = summary.take(180),
                context = payload.context?.trim()?.takeIf { it.isNotBlank() }?.take(180)
            )
        }.getOrNull()
    }

    private fun buildPrompt(request: GroundedWordingRequest): String {
        val isRomanian = request.preferredLanguage == "ro"
        val grounding = request.retrievedChunks.take(2).joinToString("\n") { chunk ->
            "- ${sanitizePromptLine(chunk.sectionTitle, 100)}: ${sanitizePromptLine(chunk.body, 280)}"
        }
        val sections = request.deterministicOutput.sections.take(2).joinToString("\n") { section ->
            "- ${sanitizePromptLine(section.title, 60)}: ${sanitizePromptLine(section.body, 220)}"
        }
        return buildString {
            appendLine("You are Scouty's wording layer.")
            appendLine("Do not change retrieval, card selection, or follow-up planning.")
            appendLine("Use only the grounded content below.")
            appendLine("Return exactly one JSON object with schema:")
            appendLine("{\"summary\":\"string\",\"context\":\"string or empty\"}")
            appendLine("Rules:")
            appendLine("- Do not add unsupported facts.")
            appendLine("- Do not add new steps.")
            appendLine("- Keep the language exactly ${if (isRomanian) "Romanian" else "English"}.")
            appendLine("- summary must be one short sentence.")
            appendLine("- context is optional and must also stay grounded.")
            appendLine("QUESTION: ${sanitizePromptLine(request.query, 180)}")
            appendLine("GROUNDING:")
            appendLine(grounding)
            appendLine("DETERMINISTIC_OUTPUT:")
            appendLine("- summary: ${sanitizePromptLine(request.deterministicOutput.summary, 180)}")
            appendLine(sections)
            appendLine("Return the JSON object now.")
        }
    }
}

@Serializable
private data class InterpreterPromptContext(
    @SerialName("user_language")
    val userLanguage: String,
    @SerialName("user_message")
    val userMessage: String,
    @SerialName("user_message_gloss_en")
    val userMessageEnglishGloss: String? = null,
    @SerialName("last_user_message")
    val lastUserMessage: String? = null,
    @SerialName("last_standalone_query")
    val lastStandaloneQuery: String? = null,
    @SerialName("last_retrieved_title")
    val lastRetrievedTitle: String? = null,
    @SerialName("last_retrieved_topic")
    val lastRetrievedTopic: String? = null,
    @SerialName("active_topic")
    val activeTopic: String? = null,
    @SerialName("active_trail_label")
    val activeTrailLabel: String? = null,
    @SerialName("open_question")
    val openQuestion: InterpreterPromptOpenQuestion? = null,
    @SerialName("confirmed_facts")
    val confirmedFacts: Map<String, String> = emptyMap(),
    @SerialName("requested_task")
    val requestedTask: String,
    val confidence: InterpreterPromptConfidence
)

@Serializable
private data class InterpreterPromptOpenQuestion(
    val text: String,
    @SerialName("text_gloss_en")
    val textEnglishGloss: String? = null,
    @SerialName("target_slot")
    val targetSlot: String,
    @SerialName("allowed_values")
    val allowedValues: List<String>,
    @SerialName("allowed_additional_slots")
    val allowedAdditionalSlots: List<String> = emptyList()
)

@Serializable
private data class InterpreterPromptConfidence(
    val score: Double,
    val tier: String,
    @SerialName("top1_strength")
    val top1Strength: Double,
    val margin: Double,
    val continuity: Double
)

@Serializable
private data class InterpreterModelPayload(
    @SerialName("standalone_query")
    val standaloneQuery: String? = null,
    @SerialName("topic_hint")
    val topicHint: String? = null,
    val intent: String? = null,
    @SerialName("slot_updates")
    val slotUpdates: Map<String, String> = emptyMap(),
    @SerialName("resolved_open_question")
    val resolvedOpenQuestion: Boolean = false,
    @SerialName("needs_clarification")
    val needsClarification: Boolean = false,
    @SerialName("clarification_target")
    val clarificationTarget: String? = null,
    val confidence: Double = 0.0
)

@Serializable
private data class GroundedWordingPayload(
    val summary: String,
    val context: String? = null
)

private fun sanitizePromptLine(value: String, maxLength: Int): String =
    value.replace("\\s+".toRegex(), " ").trim().take(maxLength)

fun normalizeInterpreterText(value: String): String =
    " ${Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .replace("[^a-z0-9 ]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()} "

private fun extractFirstJsonObject(rawResponse: String): String {
    val cleaned = rawResponse
        .replace("```json", "")
        .replace("```", "")
        .trim()
    var start = -1
    var depth = 0
    var inString = false
    var escaped = false

    cleaned.forEachIndexed { index, character ->
        if (start < 0) {
            if (character == '{') {
                start = index
                depth = 1
            }
            return@forEachIndexed
        }

        if (escaped) {
            escaped = false
            return@forEachIndexed
        }

        when (character) {
            '\\' -> if (inString) {
                escaped = true
            }

            '"' -> inString = !inString
            '{' -> if (!inString) {
                depth += 1
            }

            '}' -> if (!inString) {
                depth -= 1
                if (depth == 0) {
                    return cleaned.substring(start, index + 1)
                }
            }
        }
    }

    error("No complete JSON object found")
}
