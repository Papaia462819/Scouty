package com.scouty.app.assistant.domain.tools

import com.scouty.app.assistant.domain.DeterministicPreprocessingResult
import com.scouty.app.assistant.domain.PromptBuilder
import com.scouty.app.assistant.domain.QueryAnalyzer
import com.scouty.app.assistant.domain.RetrievalConfidenceAssessment
import com.scouty.app.assistant.domain.RetrievalConfidencePolicy
import com.scouty.app.assistant.domain.RetrievalEngine
import com.scouty.app.assistant.domain.RetrievedChunk
import com.scouty.app.assistant.domain.memory.ConversationHistory
import com.scouty.app.assistant.model.AssistantAction
import com.scouty.app.assistant.model.AssistantConversationState
import com.scouty.app.assistant.model.AssistantOpenQuestion
import com.scouty.app.assistant.model.DeviceContextSnapshot
import com.scouty.app.assistant.model.DomainHint
import com.scouty.app.assistant.model.GenerationMode
import com.scouty.app.assistant.model.KnowledgePackStatus
import com.scouty.app.assistant.model.PendingGearAction
import com.scouty.app.assistant.model.QueryAnalysis
import com.scouty.app.assistant.model.ReasoningType
import com.scouty.app.assistant.model.ResponseSectionStyle
import com.scouty.app.assistant.model.StructuredAssistantOutput
import com.scouty.app.assistant.model.StructuredResponseSection

data class ToolDispatchRequest(
    val query: String,
    val context: DeviceContextSnapshot,
    val queryAnalysis: QueryAnalysis,
    val conversationState: AssistantConversationState,
    val preprocessing: DeterministicPreprocessingResult,
    val retrievedChunks: List<RetrievedChunk>,
    val retrievalConfidence: RetrievalConfidenceAssessment,
    val knowledgePackStatus: KnowledgePackStatus,
    val conversationHistory: ConversationHistory?
)

data class ToolDispatchResult(
    val toolCall: AssistantToolCall,
    val output: StructuredAssistantOutput? = null,
    val retrievedChunks: List<RetrievedChunk> = emptyList(),
    val queryAnalysis: QueryAnalysis? = null,
    val retrievalConfidence: RetrievalConfidenceAssessment? = null,
    val conversationState: AssistantConversationState? = null,
    val actions: List<AssistantAction> = emptyList(),
    val continueExistingPath: Boolean = false
) {
    val isTerminal: Boolean
        get() = output != null
}

class ToolDispatcher(
    private val retrievalEngine: RetrievalEngine,
    private val queryAnalyzer: QueryAnalyzer = QueryAnalyzer(),
    private val confidencePolicy: RetrievalConfidencePolicy = RetrievalConfidencePolicy(),
    @Suppress("unused") private val promptBuilder: PromptBuilder = PromptBuilder()
) {
    suspend fun dispatch(
        call: AssistantToolCall,
        request: ToolDispatchRequest
    ): ToolDispatchResult =
        when (call.tool) {
            AssistantToolName.LOOKUP_CARD -> lookupCard(call, request)
            AssistantToolName.SET_GEAR_PACKED -> setGearPacked(call, request)
            AssistantToolName.CHECK_CAPABILITY -> checkCapability(call, request)
            AssistantToolName.ASK_CLARIFICATION -> askClarification(call, request)
            AssistantToolName.RECALL_PREVIOUS -> recallPrevious(call, request)
            AssistantToolName.RESPOND_DIRECTLY -> ToolDispatchResult(
                toolCall = call,
                continueExistingPath = true
            )
        }

    private suspend fun lookupCard(
        call: AssistantToolCall,
        request: ToolDispatchRequest
    ): ToolDispatchResult {
        val domain = call.domain ?: request.queryAnalysis.domainHints.firstOrNull()?.domain ?: "mountain_safety"
        val querySuffix = buildList {
            add(request.query)
            call.slotFilters.entries.forEach { (slot, value) -> add("$slot $value") }
        }.joinToString(" ")
        val analysis = queryAnalyzer.analyze(querySuffix, request.context, request.conversationState).copy(
            domainHints = (
                listOf(DomainHint(domain, 5.0)) +
                    request.queryAnalysis.domainHints.filterNot { it.domain == domain }
                ).take(3),
            knowledgeLane = request.queryAnalysis.knowledgeLane,
            resolvedTopic = request.queryAnalysis.resolvedTopic,
            targetFamily = request.queryAnalysis.targetFamily
        )
        val retrieved = retrievalEngine.retrieve(
            query = querySuffix,
            context = request.context,
            queryAnalysis = analysis,
            limit = 4
        )
        val filtered = filterBySlotConstraints(retrieved, call.slotFilters).ifEmpty { retrieved }
        val assessment = confidencePolicy.assessStandard(
            query = querySuffix,
            queryAnalysis = analysis,
            conversationState = request.conversationState,
            retrieved = filtered,
            preprocessing = request.preprocessing
        )
        return ToolDispatchResult(
            toolCall = call,
            retrievedChunks = filtered,
            queryAnalysis = analysis,
            retrievalConfidence = assessment,
            continueExistingPath = true
        )
    }

    private fun askClarification(
        call: AssistantToolCall,
        request: ToolDispatchRequest
    ): ToolDispatchResult {
        val slot = call.slot ?: request.conversationState.openQuestion?.targetSlot ?: "domain"
        val options = call.options.ifEmpty { defaultOptions(slot) }
        val question = buildClarificationQuestion(slot, options)
        val output = StructuredAssistantOutput(
            summary = question,
            sections = emptyList(),
            generationMode = GenerationMode.FALLBACK_STRUCTURED,
            reasoningType = request.queryAnalysis.reasoningType,
            followUpQuestions = options.map { labelForOption(slot, it) },
            knowledgePackVersion = request.knowledgePackStatus.packVersion
        )
        return ToolDispatchResult(
            toolCall = call,
            output = output,
            conversationState = request.conversationState.copy(
                openQuestion = AssistantOpenQuestion(
                    text = question,
                    targetSlot = slot,
                    allowedValues = options,
                    allowedAdditionalSlots = emptyList()
                )
            )
        )
    }

    private fun recallPrevious(
        call: AssistantToolCall,
        request: ToolDispatchRequest
    ): ToolDispatchResult {
        val topic = call.topic.orEmpty().ifBlank { request.query }
        val topicTokens = normalizeToolText(topic).split(" ").filter { it.length >= 3 }.toSet()
        val history = request.conversationHistory
        val matches = history?.recentTurns.orEmpty()
            .filter { turn ->
                val normalized = normalizeToolText(turn.text)
                topicTokens.isEmpty() || topicTokens.any { it in normalized }
            }
            .takeLast(4)
        val summaryMatch = history?.summary
            ?.takeIf { summary -> topicTokens.any { it in normalizeToolText(summary) } }
        val body = when {
            matches.isNotEmpty() -> matches.joinToString(" ") { turn ->
                val role = if (turn.role.name == "USER") "tu ai întrebat" else "eu am răspuns"
                "$role: ${sanitizeLine(turn.text, 180)}"
            }
            !summaryMatch.isNullOrBlank() -> summaryMatch
            else -> "Nu găsesc în istoricul local un detaliu suficient de clar despre ${sanitizeLine(topic, 80)}."
        }
        val output = StructuredAssistantOutput(
            summary = if (matches.isNotEmpty() || !summaryMatch.isNullOrBlank()) {
                "Din conversația de mai devreme: $body"
            } else {
                body
            },
            sections = emptyList(),
            generationMode = GenerationMode.FALLBACK_STRUCTURED,
            reasoningType = ReasoningType.GENERAL_RETRIEVAL,
            knowledgePackVersion = request.knowledgePackStatus.packVersion
        )
        return ToolDispatchResult(
            toolCall = call,
            output = output,
            conversationState = request.conversationState
        )
    }

    private fun setGearPacked(
        call: AssistantToolCall,
        request: ToolDispatchRequest
    ): ToolDispatchResult {
        val itemId = call.itemId?.let { requested ->
            val normalizedRequested = normalizeToolText(requested)
            request.context.gearItems.firstOrNull { item ->
                item.id == requested ||
                    normalizeToolText(item.id) == normalizedRequested ||
                    normalizeToolText(item.name).contains(normalizedRequested)
            }?.id
        }
        if (itemId.isNullOrBlank()) {
            return askClarification(
                call.copy(
                    tool = AssistantToolName.ASK_CLARIFICATION,
                    slot = "domain",
                    options = request.context.gearItems.take(4).map { it.name }
                ),
                request
            )
        }
        val packed = call.packed ?: true
        val itemName = request.context.gearItems.firstOrNull { it.id == itemId }?.name ?: itemId
        val output = StructuredAssistantOutput(
            summary = if (packed) {
                "Am marcat „$itemName” ca împachetat."
            } else {
                "Am scos marcajul de împachetat pentru „$itemName”."
            },
            sections = emptyList(),
            generationMode = GenerationMode.FALLBACK_STRUCTURED,
            reasoningType = ReasoningType.GEAR_ADVICE,
            knowledgePackVersion = request.knowledgePackStatus.packVersion
        )
        return ToolDispatchResult(
            toolCall = call,
            output = output,
            conversationState = request.conversationState.copy(
                pendingGearAction = PendingGearAction()
            ),
            actions = listOf(AssistantAction.ToggleGearPacked(listOf(itemId), packed))
        )
    }

    private fun checkCapability(
        call: AssistantToolCall,
        request: ToolDispatchRequest
    ): ToolDispatchResult {
        val trail = request.context.trail
        val metric = call.metric ?: "duration"
        val body = when (metric) {
            "duration" -> trail?.estimatedDuration?.let { "Durata estimată pentru ${trail.name}: $it." }
                ?: "Nu am o durată estimată suficient de clară pentru traseul activ."
            "elevation" -> trail?.elevationGain?.let { gain ->
                "Diferența pozitivă de nivel pentru ${trail.name}: aproximativ ${gain} m."
            } ?: "Nu am diferența de nivel disponibilă pentru traseul activ."
            "weather" -> trail?.weatherForecast?.let { "Vreme pentru ${trail.name}: $it." }
                ?: "Nu am prognoză locală disponibilă pentru traseul activ."
            else -> "Nu am acest tip de verificare disponibil local."
        }
        val output = StructuredAssistantOutput(
            summary = body,
            sections = buildCapabilitySections(metric, trail != null, request),
            generationMode = GenerationMode.FALLBACK_STRUCTURED,
            reasoningType = ReasoningType.ROUTE_CONTEXT,
            knowledgePackVersion = request.knowledgePackStatus.packVersion
        )
        return ToolDispatchResult(
            toolCall = call,
            output = output,
            conversationState = request.conversationState.copy(lastTrailContextIntent = metric.uppercase())
        )
    }

    private fun buildCapabilitySections(
        metric: String,
        hasTrail: Boolean,
        request: ToolDispatchRequest
    ): List<StructuredResponseSection> =
        if (!hasTrail) {
            listOf(
                StructuredResponseSection(
                    title = "Context lipsă",
                    body = "Încarcă un traseu activ ca să pot verifica ${metricLabel(metric)} din datele locale.",
                    style = ResponseSectionStyle.CONTEXT
                )
            )
        } else if (request.context.batterySafe) {
            listOf(
                StructuredResponseSection(
                    title = "Baterie",
                    body = "Economisirea bateriei este activă; păstrează bateria pentru navigație și apeluri.",
                    style = ResponseSectionStyle.ACTIONS
                )
            )
        } else {
            emptyList()
        }

    private fun filterBySlotConstraints(
        chunks: List<RetrievedChunk>,
        filters: Map<String, String>
    ): List<RetrievedChunk> {
        if (filters.isEmpty()) {
            return chunks
        }
        return chunks.filter { chunk ->
            val metadata = chunk.metadataJson ?: return@filter false
            filters.all { (slot, value) ->
                "\"$slot\"" in metadata && "\"$value\"" in metadata
            }
        }
    }

    private fun buildClarificationQuestion(slot: String, options: List<String>): String {
        val labels = options.map { labelForOption(slot, it) }.distinct()
        return when (slot) {
            "ignition_source" -> "Cu ce încerci să aprinzi focul: ${labels.joinToString(", ")}?"
            "fuel_condition" -> "Cum sunt lemnele sau materialul de foc: ${labels.joinToString(", ")}?"
            "wind" -> "Cât de tare bate vântul: ${labels.joinToString(", ")}?"
            "domain" -> "Te referi la ${labels.joinToString(", ")}?"
            "problem_cause" -> "Care pare problema principală: ${labels.joinToString(", ")}?"
            else -> "Am nevoie de un detaliu: ${labels.joinToString(", ")}?"
        }
    }

    private fun defaultOptions(slot: String): List<String> =
        when (slot) {
            "ignition_source" -> listOf("lighter", "matches", "ferro")
            "fuel_condition" -> listOf("dry", "damp", "wet")
            "wind" -> listOf("low", "moderate", "high")
            "domain" -> listOf("foc", "echipament", "traseu")
            "problem_cause" -> listOf("lemne ude", "iască lipsă", "vânt puternic")
            else -> listOf("da", "nu")
        }

    private fun labelForOption(slot: String, option: String): String =
        when (option) {
            "lighter" -> "brichetă"
            "matches" -> "chibrituri"
            "ferro" -> "amnar"
            "dry" -> "uscate"
            "damp" -> "umede"
            "wet" -> "ude"
            "low" -> "slab"
            "moderate" -> "moderat"
            "high" -> "puternic"
            "foc" -> "foc"
            "echipament" -> "echipament"
            "traseu" -> "traseu"
            else -> option
        }

    private fun metricLabel(metric: String): String =
        when (metric) {
            "duration" -> "durata"
            "elevation" -> "diferența de nivel"
            "weather" -> "vremea"
            else -> metric
        }
}
