package com.scouty.app.assistant.domain

import android.content.Context
import com.scouty.app.assistant.data.AssistantKnowledgeRepository
import com.scouty.app.assistant.model.AssistantCitation
import com.scouty.app.assistant.model.AssistantResponse
import com.scouty.app.assistant.model.DeviceContextSnapshot
import com.scouty.app.assistant.model.SafetyOutcome
import java.text.Normalizer

data class RetrievedChunk(
    val topic: String,
    val sourceTitle: String,
    val sectionTitle: String,
    val body: String,
    val score: Int
)

data class AssistantPrompt(
    val query: String,
    val contextSummary: String,
    val citationsSummary: String
)

data class GenerationInput(
    val query: String,
    val prompt: AssistantPrompt,
    val retrievedChunks: List<RetrievedChunk>,
    val context: DeviceContextSnapshot,
    val safetyOutcome: SafetyOutcome
)

class RetrievalEngine(
    private val knowledgeRepository: AssistantKnowledgeRepository
) {
    suspend fun retrieve(query: String, limit: Int = 3): List<RetrievedChunk> {
        knowledgeRepository.ensureSeeded()
        val candidates = knowledgeRepository.search(query, limit * 3).ifEmpty {
            knowledgeRepository.allChunks()
        }

        return candidates
            .map { chunk ->
                RetrievedChunk(
                    topic = chunk.topic,
                    sourceTitle = chunk.sourceTitle,
                    sectionTitle = chunk.sectionTitle,
                    body = chunk.body,
                    score = scoreChunk(query, chunk.sectionTitle, chunk.body, chunk.topic)
                )
            }
            .sortedByDescending { it.score }
            .take(limit)
    }

    private fun scoreChunk(query: String, sectionTitle: String, body: String, topic: String): Int {
        val normalizedQuery = normalize(query)
        val tokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
        val normalizedSection = normalize(sectionTitle)
        val normalizedBody = normalize(body)
        val normalizedTopic = normalize(topic)

        return tokens.fold(0) { total, token ->
            total + when {
                token in normalizedSection -> 12
                token in normalizedTopic -> 10
                token in normalizedBody -> 4
                else -> 0
            }
        }
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .trim()
}

class PromptBuilder {
    fun build(
        query: String,
        context: DeviceContextSnapshot,
        retrievedChunks: List<RetrievedChunk>
    ): AssistantPrompt {
        val trailSummary = context.trail?.let { trail ->
            buildString {
                append("Traseu activ: ${trail.name}")
                if (!trail.region.isNullOrBlank()) {
                    append(" (${trail.region})")
                }
                if (!trail.sunsetTime.isNullOrBlank()) {
                    append(", apus la ${trail.sunsetTime}")
                }
            }
        } ?: "Fără traseu activ"

        val batterySummary = "Baterie ${context.batteryPercent}%${if (context.batterySafe) " / Battery Safe" else ""}"
        val gpsSummary = if (context.gpsFixed && context.latitude != null && context.longitude != null) {
            "GPS fix (${String.format("%.4f", context.latitude)}, ${String.format("%.4f", context.longitude)})"
        } else {
            "GPS fără fix stabil"
        }

        return AssistantPrompt(
            query = query,
            contextSummary = listOf(trailSummary, batterySummary, gpsSummary).joinToString(" | "),
            citationsSummary = retrievedChunks.joinToString(" | ") { "${it.sourceTitle} -> ${it.sectionTitle}" }
        )
    }
}

class MedicalSafetyPolicy {
    fun evaluate(query: String, retrievedChunks: List<RetrievedChunk>): SafetyOutcome {
        val haystack = buildString {
            append(query.lowercase())
            append(' ')
            append(retrievedChunks.joinToString(" ") { it.body.lowercase() })
        }

        val emergencyMarkers = listOf(
            "nu pot sa calc",
            "nu pot să calc",
            "deformare",
            "hemorag",
            "sangerare masiva",
            "sângerare masivă",
            "inconstient",
            "inconștient",
            "pierderea sensibilitatii",
            "pierderea sensibilității"
        )
        if (emergencyMarkers.any { it in haystack }) {
            return SafetyOutcome.EMERGENCY_ESCALATION
        }

        val cautionMarkers = listOf(
            "urs",
            "bear",
            "fractur",
            "hipoterm",
            "sangerare",
            "sângerare",
            "entorsa",
            "entorsă",
            "glezna",
            "gleznă",
            "sucit"
        )
        return if (cautionMarkers.any { it in haystack }) {
            SafetyOutcome.CAUTION
        } else {
            SafetyOutcome.NORMAL
        }
    }
}

interface GenerationEngine {
    suspend fun generate(input: GenerationInput): String
}

class TemplateGenerationEngine : GenerationEngine {
    override suspend fun generate(input: GenerationInput): String {
        val answerSteps = input.retrievedChunks
            .flatMap { chunk -> splitIntoSteps(chunk.body) }
            .distinct()
            .take(3)

        val intro = when (input.safetyOutcome) {
            SafetyOutcome.EMERGENCY_ESCALATION ->
                "Situația descrisă are semne de alarmă. Prioritatea este 112 / SOS, apoi măsurile de bază de mai jos."
            SafetyOutcome.CAUTION ->
                "Ai nevoie de un răspuns prudent, orientat pe siguranță și pe pași simpli de teren."
            SafetyOutcome.NORMAL ->
                "Pe baza ghidurilor offline disponibile, urmează pașii de mai jos."
        }

        val contextNotes = mutableListOf<String>()
        if (input.context.batterySafe) {
            contextNotes += "Telefonul este în Battery Safe, deci păstrează energia pentru navigație și apel de urgență."
        }
        input.context.trail?.sunsetTime?.takeIf { it.isNotBlank() }?.let { sunset ->
            contextNotes += "Ține cont de apusul estimat la $sunset când decizi dacă mai continui traseul."
        }

        return buildString {
            appendLine(intro)
            answerSteps.forEachIndexed { index, step ->
                appendLine("${index + 1}. $step")
            }
            if (contextNotes.isNotEmpty()) {
                appendLine()
                appendLine(contextNotes.joinToString(" "))
            }
        }.trim()
    }

    private fun splitIntoSteps(body: String): List<String> =
        body.split('.', '!', '?')
            .map { it.trim() }
            .filter { it.isNotBlank() }
}

class AssistantRepository(
    context: Context,
    private val retrievalEngine: RetrievalEngine = RetrievalEngine(AssistantKnowledgeRepository(context)),
    private val promptBuilder: PromptBuilder = PromptBuilder(),
    private val generationEngine: GenerationEngine = TemplateGenerationEngine(),
    private val medicalSafetyPolicy: MedicalSafetyPolicy = MedicalSafetyPolicy()
) {
    suspend fun answer(query: String, context: DeviceContextSnapshot): AssistantResponse {
        val retrieved = retrievalEngine.retrieve(query)
        val prompt = promptBuilder.build(query, context, retrieved)
        val safetyOutcome = medicalSafetyPolicy.evaluate(query, retrieved)
        val answerText = generationEngine.generate(
            GenerationInput(
                query = query,
                prompt = prompt,
                retrievedChunks = retrieved,
                context = context,
                safetyOutcome = safetyOutcome
            )
        )

        return AssistantResponse(
            answerText = answerText,
            citations = retrieved.map {
                AssistantCitation(
                    sourceTitle = it.sourceTitle,
                    sectionTitle = it.sectionTitle,
                    snippet = it.body.take(140).trimEnd() + if (it.body.length > 140) "..." else ""
                )
            },
            safetyOutcome = safetyOutcome,
            usedFallback = retrieved.isEmpty()
        )
    }
}
