package com.scouty.app.assistant.domain.tools

import com.scouty.app.assistant.domain.CampfireSlotCatalog
import com.scouty.app.assistant.domain.LocalLlmGenerationOptions
import com.scouty.app.assistant.domain.LocalLlmPromptCacheHint
import com.scouty.app.assistant.domain.LocalLlmSamplerParams
import com.scouty.app.assistant.domain.ModelManager
import com.scouty.app.assistant.domain.RetrievalConfidenceAssessment
import com.scouty.app.assistant.domain.RetrievedChunk
import com.scouty.app.assistant.domain.memory.ConversationHistory
import com.scouty.app.assistant.model.AssistantConversationState
import com.scouty.app.assistant.model.DeviceContextSnapshot
import com.scouty.app.assistant.model.QueryAnalysis
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import java.text.Normalizer

enum class AssistantToolName(val wireName: String) {
    LOOKUP_CARD("lookup_card"),
    SET_GEAR_PACKED("set_gear_packed"),
    CHECK_CAPABILITY("check_capability"),
    ASK_CLARIFICATION("ask_clarification"),
    RECALL_PREVIOUS("recall_previous"),
    RESPOND_DIRECTLY("respond_directly");

    companion object {
        fun fromWireName(value: String): AssistantToolName? =
            entries.firstOrNull { it.wireName == value }
    }
}

data class AssistantToolCall(
    val tool: AssistantToolName,
    val domain: String? = null,
    val slotFilters: Map<String, String> = emptyMap(),
    val itemId: String? = null,
    val packed: Boolean? = null,
    val metric: String? = null,
    val slot: String? = null,
    val options: List<String> = emptyList(),
    val topic: String? = null
)

data class ToolPlanningRequest(
    val query: String,
    val queryAnalysis: QueryAnalysis,
    val conversationState: AssistantConversationState,
    val retrievalConfidence: RetrievalConfidenceAssessment,
    val preprocessingSlots: Map<String, String>,
    val retrievedChunks: List<RetrievedChunk>,
    val deviceContext: DeviceContextSnapshot,
    val conversationHistory: ConversationHistory?
)

interface ToolCallModel {
    suspend fun generate(prompt: String, grammar: String, promptCacheHint: LocalLlmPromptCacheHint?): String
}

class ModelManagerToolCallModel(
    private val modelManager: ModelManager
) : ToolCallModel {
    override suspend fun generate(
        prompt: String,
        grammar: String,
        promptCacheHint: LocalLlmPromptCacheHint?
    ): String =
        modelManager.generate(
            prompt = prompt,
            options = LocalLlmGenerationOptions(
                grammar = grammar,
                sampler = LocalLlmSamplerParams(
                    maxTokens = 120,
                    temperature = 0.0f,
                    topK = 1,
                    topP = 0.1f,
                    randomSeed = 19
                ),
                promptCacheHint = promptCacheHint
            )
        ).text
}

class GrammarToolCallPlanner(
    private val model: ToolCallModel,
    private val parser: ToolCallParser = ToolCallParser()
) {
    suspend fun plan(request: ToolPlanningRequest): AssistantToolCall? {
        val raw = runCatching {
            model.generate(
                prompt = buildPrompt(request),
                grammar = ToolCallGrammar.Text,
                promptCacheHint = request.conversationHistory?.promptCacheHint
            )
        }.getOrNull() ?: return null
        return parser.parse(raw)
    }

    private fun buildPrompt(request: ToolPlanningRequest): String =
        buildString {
            appendLine("Ești stratul local de tool-calling pentru Scouty.")
            appendLine("Nu răspunzi utilizatorului direct. Alegi exact un tool din catalog.")
            appendLine("Returnează doar JSON valid conform gramaticii GBNF, fără markdown.")
            appendLine("Reguli:")
            appendLine("- Dacă utilizatorul cere explicit ceva discutat anterior, folosește recall_previous.")
            appendLine("- Dacă lipsește un slot concret care decide între carduri, folosește ask_clarification.")
            appendLine("- Dacă un card mai specific poate fi găsit fără întrebare, folosește lookup_card.")
            appendLine("- Dacă mesajul actualizează echipament, folosește set_gear_packed.")
            appendLine("- Dacă întreabă despre durată, diferență de nivel sau vreme pe traseu, folosește check_capability.")
            appendLine("- Dacă top-1 este suficient și nu lipsește nimic critic, folosește respond_directly.")
            appendLine()
            appendLine("CATALOG_TOOLURI:")
            appendLine("- lookup_card(domain, slot_filters)")
            appendLine("- set_gear_packed(item_id, packed)")
            appendLine("- check_capability(metric)")
            appendLine("- ask_clarification(slot, options)")
            appendLine("- recall_previous(topic)")
            appendLine("- respond_directly()")
            appendLine()
            appendLine("SLOTURI_PERMISE:")
            appendLine(CampfireSlotCatalog.allowedValues.keys.joinToString(", "))
            appendLine()
            appendLine("CONTEXT:")
            appendLine("query=${sanitizeLine(request.query, 220)}")
            appendLine("language=${request.queryAnalysis.preferredLanguage}")
            appendLine("confidence=${"%.3f".format(request.retrievalConfidence.score)}")
            appendLine("confidence_tier=${request.retrievalConfidence.tier.name}")
            appendLine("detected_slots=${request.preprocessingSlots}")
            appendLine("open_question=${request.conversationState.openQuestion?.targetSlot.orEmpty()}")
            appendLine("domain_hints=${request.queryAnalysis.domainHints.joinToString(",") { it.domain }}")
            appendLine("trail=${request.deviceContext.trail?.name.orEmpty()}")
            appendLine("gear_items=${request.deviceContext.gearItems.take(12).joinToString(";") { "${it.id}:${it.name}:${it.isPacked}" }}")
            appendLine("top_candidates=")
            request.retrievedChunks.take(4).forEachIndexed { index, chunk ->
                appendLine(
                    "${index + 1}. id=${chunk.chunkId} domain=${chunk.domain} title=${sanitizeLine(chunk.sectionTitle, 90)} " +
                        "score=${chunk.score} slots=${slotConstraints(chunk)}"
                )
            }
            request.conversationHistory?.recentTurns?.takeLast(4)?.takeIf { it.isNotEmpty() }?.let { turns ->
                appendLine("recent_turns=")
                turns.forEach { turn ->
                    appendLine("- ${turn.role}: ${sanitizeLine(turn.text, 160)}")
                }
            }
            appendLine()
            appendLine("JSON acum:")
        }

    private fun slotConstraints(chunk: RetrievedChunk): String {
        val metadata = chunk.metadataJson?.let { raw ->
            runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
        } ?: return "{}"
        return metadata["slot_constraints"]?.toString() ?: "{}"
    }
}

class ToolCallParser(
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false }
) {
    fun parse(raw: String): AssistantToolCall? {
        val element = runCatching { json.parseToJsonElement(raw.trim()) }.getOrNull() ?: return null
        val obj = element as? JsonObject ?: return null
        val tool = AssistantToolName.fromWireName(obj.string("tool") ?: return null) ?: return null
        val slotFilters = obj["slot_filters"]
            ?.jsonObjectOrNull()
            ?.mapNotNull { (key, value) ->
                val normalizedKey = key.trim()
                val normalizedValue = value.stringValue()?.trim().orEmpty()
                if (
                    CampfireSlotCatalog.isKnownSlot(normalizedKey) &&
                    CampfireSlotCatalog.isAllowedValue(normalizedKey, normalizedValue)
                ) {
                    normalizedKey to normalizedValue
                } else {
                    null
                }
            }
            ?.toMap()
            .orEmpty()
        return AssistantToolCall(
            tool = tool,
            domain = obj.string("domain")?.takeIf { it in AllowedDomains },
            slotFilters = slotFilters,
            itemId = obj.string("item_id")?.take(80),
            packed = obj.boolean("packed"),
            metric = obj.string("metric")?.takeIf { it in AllowedMetrics },
            slot = obj.string("slot")?.takeIf { CampfireSlotCatalog.isKnownSlot(it) || it == "domain" || it == "problem_cause" },
            options = obj.stringList("options").take(4),
            topic = obj.string("topic")?.take(120)
        )
    }

    private fun JsonObject.string(key: String): String? = this[key].stringValue()?.trim()?.takeIf { it.isNotBlank() }

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.stringList(key: String): List<String> {
        val raw = this[key] ?: return emptyList()
        return runCatching {
            json.decodeFromJsonElement(ListSerializer(String.serializer()), raw)
        }.getOrDefault(emptyList())
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun JsonElement?.stringValue(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        return runCatching { json.decodeFromJsonElement(String.serializer(), primitive) }
            .getOrElse { primitive.toString().trim('"') }
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? =
        runCatching { jsonObject }.getOrNull()

    private companion object {
        private val AllowedDomains = setOf(
            "campfire_basics",
            "gear_and_preparation",
            "tips_and_tricks",
            "survival_basics",
            "weather_and_season",
            "wildlife_romania",
            "route_intelligence_romania",
            "medical_emergency",
            "mountain_safety"
        )
        private val AllowedMetrics = setOf("duration", "elevation", "weather")
    }
}

object ToolCallGrammar {
    val Text: String = """
root ::= lookup | set_gear | check | ask | recall | direct
lookup ::= "{" ws "\"tool\"" ws ":" ws "\"lookup_card\"" ws "," ws "\"domain\"" ws ":" ws domain ws "," ws "\"slot_filters\"" ws ":" ws filters ws "}"
set_gear ::= "{" ws "\"tool\"" ws ":" ws "\"set_gear_packed\"" ws "," ws "\"item_id\"" ws ":" ws string ws "," ws "\"packed\"" ws ":" ws boolean ws "}"
check ::= "{" ws "\"tool\"" ws ":" ws "\"check_capability\"" ws "," ws "\"metric\"" ws ":" ws metric ws "}"
ask ::= "{" ws "\"tool\"" ws ":" ws "\"ask_clarification\"" ws "," ws "\"slot\"" ws ":" ws slot ws "," ws "\"options\"" ws ":" ws string_array ws "}"
recall ::= "{" ws "\"tool\"" ws ":" ws "\"recall_previous\"" ws "," ws "\"topic\"" ws ":" ws string ws "}"
direct ::= "{" ws "\"tool\"" ws ":" ws "\"respond_directly\"" ws "}"
filters ::= "{" ws (filter_pair (ws "," ws filter_pair)*)? ws "}"
filter_pair ::= slot ws ":" ws slot_value
string_array ::= "[" ws string (ws "," ws string)* ws "]"
domain ::= "\"campfire_basics\"" | "\"gear_and_preparation\"" | "\"tips_and_tricks\"" | "\"survival_basics\"" | "\"weather_and_season\"" | "\"wildlife_romania\"" | "\"route_intelligence_romania\"" | "\"medical_emergency\"" | "\"mountain_safety\""
metric ::= "\"duration\"" | "\"elevation\"" | "\"weather\""
slot ::= "\"goal\"" | "\"ignition_source\"" | "\"tinder_available\"" | "\"tinder_material\"" | "\"tinder_condition\"" | "\"kindling_available\"" | "\"fuel_condition\"" | "\"wind\"" | "\"permission\"" | "\"ground_risk\"" | "\"tinder_strategy\"" | "\"need_level\"" | "\"daylight\"" | "\"fatigue\"" | "\"compromised_item\"" | "\"compromised_reason\"" | "\"domain\"" | "\"problem_cause\""
slot_value ::= "\"warmth\"" | "\"cooking\"" | "\"boil_water\"" | "\"lighter\"" | "\"matches\"" | "\"ferro\"" | "\"recognized_spark\"" | "\"none\"" | "\"yes\"" | "\"no\"" | "\"paper\"" | "\"tissue\"" | "\"cotton\"" | "\"lint\"" | "\"dry\"" | "\"damp\"" | "\"wet\"" | "\"unavailable\"" | "\"scarce\"" | "\"unknown\"" | "\"high\"" | "\"moderate\"" | "\"low\"" | "\"forbidden\"" | "\"roots_or_peat\"" | "\"dry_vegetation\"" | "\"indoor_or_tent\"" | "\"safe\"" | "\"improvise\"" | "\"necessary\"" | "\"optional\"" | "\"dark\"" | "\"enough\"" | "\"lost\"" | "\"broken\"" | "\"unusable\""
boolean ::= "true" | "false"
string ::= "\"" chars "\""
chars ::= ([^"\\] | "\\" ["\\/bfnrt])*
ws ::= [ \t\n\r]*
""".trimIndent()
}

internal fun sanitizeLine(value: String, max: Int): String =
    value.replace('\n', ' ')
        .replace("\\s+".toRegex(), " ")
        .trim()
        .take(max)

internal fun normalizeToolText(value: String): String =
    Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .replace("[^a-z0-9 ]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
