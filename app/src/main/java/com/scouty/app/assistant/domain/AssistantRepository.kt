package com.scouty.app.assistant.domain

import android.content.Context
import com.scouty.app.assistant.data.AssistantKnowledgeRepository
import com.scouty.app.assistant.model.AssistantCitation
import com.scouty.app.assistant.model.AssistantResponse
import com.scouty.app.assistant.model.DeviceContextSnapshot
import com.scouty.app.assistant.model.SafetyOutcome
import java.text.Normalizer
import kotlin.math.min

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
    suspend fun retrieve(query: String, language: String, limit: Int = 3): List<RetrievedChunk> {
        knowledgeRepository.ensureSeeded()
        val candidates = knowledgeRepository.searchByLanguage(query, limit * 3, language).ifEmpty {
            knowledgeRepository.allChunksByLanguage(language).ifEmpty {
                knowledgeRepository.allChunks()
            }
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

        var totalScore = 0
        var matchedTokens = 0

        for (token in tokens) {
            val tokenScore = when {
                token in normalizedSection -> 12
                token in normalizedTopic -> 10
                else -> {
                    val count = countOccurrences(normalizedBody, token)
                    if (count > 0) 4 * min(count, 3) else 0
                }
            }
            if (tokenScore > 0) matchedTokens++
            totalScore += tokenScore
        }

        // Query coverage bonus: reward chunks matching multiple query tokens
        if (matchedTokens > 1) {
            totalScore += (matchedTokens - 1) * 8
        }

        return totalScore
    }

    private fun countOccurrences(text: String, token: String): Int {
        var count = 0
        var startIndex = 0
        while (true) {
            val index = text.indexOf(token, startIndex)
            if (index < 0) break
            count++
            startIndex = index + token.length
        }
        return count
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
            append(normalize(query))
            append(' ')
            append(retrievedChunks.joinToString(" ") { normalize(it.body) })
        }

        if (emergencyMarkers.any { it in haystack }) {
            return SafetyOutcome.EMERGENCY_ESCALATION
        }

        return if (cautionMarkers.any { it in haystack }) {
            SafetyOutcome.CAUTION
        } else {
            SafetyOutcome.NORMAL
        }
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")

    companion object {
        private val emergencyMarkers = listOf(
            // Romanian
            "nu pot sa calc",
            "nu pot calca",
            "deformare",
            "hemorag",
            "sangerare masiva",
            "inconstient",
            "pierderea sensibilitatii",
            "nu respira",
            "durere in piept",
            "convulsii",
            "lesin",
            "stop cardiac",
            "nu mai respira",
            "pierderea cunostintei",
            // English
            "can't walk",
            "cannot walk",
            "deformity",
            "deformed",
            "hemorrhag",
            "massive bleeding",
            "unconscious",
            "loss of sensation",
            "not breathing",
            "chest pain",
            "seizure",
            "cardiac arrest",
            "stopped breathing",
            "loss of consciousness",
            "collapsed"
        )

        private val cautionMarkers = listOf(
            // Romanian
            "urs",
            "fractur",
            "hipoterm",
            "sangerare",
            "entorsa",
            "glezna",
            "sucit",
            "sarpe",
            "muscatura",
            "fulger",
            "pierdut",
            "ratacit",
            "ameteli",
            "ameteala",
            "deshidratare",
            "arsura",
            "inec",
            "rana",
            "taietura",
            "altitudine",
            "bataturi",
            "tremur",
            "frison",
            "confuzie",
            // English
            "bear",
            "fractur",
            "hypotherm",
            "bleeding",
            "sprain",
            "ankle",
            "twisted",
            "snake",
            "bite",
            "bitten",
            "lightning",
            "lost",
            "dizzy",
            "dizziness",
            "dehydrat",
            "burn",
            "wound",
            "cut",
            "altitude sick",
            "blister",
            "heat exhaust",
            "heat stroke",
            "heatstroke"
        )
    }
}

interface GenerationEngine {
    suspend fun generate(input: GenerationInput): String
}

class TemplateGenerationEngine : GenerationEngine {
    override suspend fun generate(input: GenerationInput): String {
        val isRomanian = input.context.localeTag.startsWith("ro")
        val chunks = input.retrievedChunks

        return buildString {
            // Safety intro
            appendLine(safetyIntro(input.safetyOutcome, isRomanian))
            appendLine()

            // Primary chunk as coherent paragraph
            if (chunks.isNotEmpty()) {
                appendLine(chunks[0].body)
            }

            // Secondary chunk if available
            if (chunks.size >= 2) {
                appendLine()
                val prefix = if (isRomanian) "De asemenea:" else "Additionally:"
                appendLine(prefix)
                appendLine(truncateBody(chunks[1].body, 300))
            }

            // Tertiary chunk as brief note
            if (chunks.size >= 3) {
                appendLine()
                val prefix = if (isRomanian) "Notă:" else "Note:"
                appendLine("$prefix ${truncateBody(chunks[2].body, 150)}")
            }

            // Device context notes
            val contextNotes = buildContextNotes(input.context, isRomanian)
            if (contextNotes.isNotBlank()) {
                appendLine()
                appendLine(contextNotes)
            }

            // Emergency CTA as last line
            if (input.safetyOutcome == SafetyOutcome.EMERGENCY_ESCALATION) {
                appendLine()
                appendLine(if (isRomanian) "Sună 112 imediat." else "Call 112 immediately.")
            }
        }.trim()
    }

    private fun safetyIntro(outcome: SafetyOutcome, isRomanian: Boolean): String = when (outcome) {
        SafetyOutcome.EMERGENCY_ESCALATION -> if (isRomanian) {
            "Situația descrisă are semne de alarmă. Prioritatea este 112 / SOS, apoi măsurile de bază de mai jos."
        } else {
            "The situation described shows warning signs. Priority is 112 / SOS, then the basic measures below."
        }
        SafetyOutcome.CAUTION -> if (isRomanian) {
            "Ai nevoie de un răspuns prudent, orientat pe siguranță și pe pași simpli de teren."
        } else {
            "You need a cautious response focused on safety and simple field steps."
        }
        SafetyOutcome.NORMAL -> if (isRomanian) {
            "Pe baza ghidurilor offline disponibile:"
        } else {
            "Based on available offline guidelines:"
        }
    }

    private fun buildContextNotes(context: DeviceContextSnapshot, isRomanian: Boolean): String {
        val notes = mutableListOf<String>()
        if (context.batterySafe) {
            notes += if (isRomanian) {
                "Telefonul este în Battery Safe — păstrează energia pentru navigație și apel de urgență."
            } else {
                "Phone is in Battery Safe — conserve energy for navigation and emergency calls."
            }
        }
        context.trail?.sunsetTime?.takeIf { it.isNotBlank() }?.let { sunset ->
            notes += if (isRomanian) {
                "Ține cont de apusul estimat la $sunset când decizi dacă mai continui traseul."
            } else {
                "Consider the estimated sunset at $sunset when deciding whether to continue the trail."
            }
        }
        return notes.joinToString(" ")
    }

    private fun truncateBody(body: String, maxChars: Int): String {
        if (body.length <= maxChars) return body
        val truncated = body.take(maxChars)
        val lastSentenceEnd = truncated.lastIndexOfAny(charArrayOf('.', '!', '?'))
        return if (lastSentenceEnd > maxChars / 2) {
            truncated.substring(0, lastSentenceEnd + 1)
        } else {
            "$truncated..."
        }
    }
}

class  AssistantRepository(
    context: Context,
    private val retrievalEngine: RetrievalEngine = RetrievalEngine(AssistantKnowledgeRepository(context)),
    private val promptBuilder: PromptBuilder = PromptBuilder(),
    private val generationEngine: GenerationEngine = TemplateGenerationEngine(),
    private val medicalSafetyPolicy: MedicalSafetyPolicy = MedicalSafetyPolicy()
) {
    suspend fun answer(query: String, context: DeviceContextSnapshot): AssistantResponse {
        val language = if (context.localeTag.startsWith("ro")) "ro" else "en"
        val retrieved = retrievalEngine.retrieve(query, language)
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
