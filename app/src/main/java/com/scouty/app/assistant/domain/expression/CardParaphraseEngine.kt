package com.scouty.app.assistant.domain.expression

import com.scouty.app.assistant.diagnostics.AssistantDiagnostics
import com.scouty.app.assistant.domain.LocalLlmGenerationOptions
import com.scouty.app.assistant.domain.LocalLlmSamplerParams
import com.scouty.app.assistant.domain.ModelManager
import com.scouty.app.assistant.domain.RetrievalConfidenceTier
import com.scouty.app.assistant.domain.RetrievedChunk
import com.scouty.app.assistant.domain.memory.ConversationHistory
import com.scouty.app.assistant.model.DeviceContextSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.text.Normalizer

data class CardParaphraseRequest(
    val featureEnabled: Boolean,
    val chunk: RetrievedChunk,
    val userQuery: String,
    val confidenceTier: RetrievalConfidenceTier,
    val deviceContext: DeviceContextSnapshot,
    val conversationHistory: ConversationHistory?
)

data class ParaphrasedResponse(
    val text: String,
    val latencyMs: Long
)

interface CardParaphraseModel {
    suspend fun generate(prompt: String, options: LocalLlmGenerationOptions): String
}

class ModelManagerCardParaphraseModel(
    private val modelManager: ModelManager
) : CardParaphraseModel {
    override suspend fun generate(prompt: String, options: LocalLlmGenerationOptions): String =
        modelManager.generate(prompt, options).text
}

class CardParaphraseEngine(
    private val model: CardParaphraseModel,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false }
) {
    suspend fun maybeParaphrase(request: CardParaphraseRequest): ParaphrasedResponse? {
        if (!request.featureEnabled) {
            return null
        }

        val tier = metadataText(request.chunk, "tier")
        if (tier == "A") {
            AssistantDiagnostics.logExpressionLayer(
                chunkId = request.chunk.chunkId,
                invocationCount = 0,
                fallbackCount = 0,
                tokenLatencyMs = 0,
                skippedTierACount = 1,
                reason = "tier_a_skip"
            )
            return null
        }
        if (tier != "B") {
            return null
        }
        if (request.confidenceTier == RetrievalConfidenceTier.LOW) {
            return null
        }

        val prompt = buildPrompt(request)
        val startedAt = System.nanoTime()
        val raw = runCatching {
            model.generate(
                prompt = prompt,
                options = LocalLlmGenerationOptions(
                    sampler = LocalLlmSamplerParams(
                        maxTokens = 180,
                        temperature = 0.4f,
                        topK = 40,
                        topP = 0.9f,
                        randomSeed = 13
                    ),
                    promptCacheHint = request.conversationHistory?.promptCacheHint
                )
            )
        }.getOrElse {
            val elapsedMs = elapsedMsSince(startedAt)
            AssistantDiagnostics.logExpressionLayer(
                chunkId = request.chunk.chunkId,
                invocationCount = 1,
                fallbackCount = 1,
                tokenLatencyMs = elapsedMs,
                skippedTierACount = 0,
                reason = "generation_error"
            )
            return null
        }

        val elapsedMs = elapsedMsSince(startedAt)
        val sanitized = sanitize(raw, request.chunk.body)
        if (sanitized == null) {
            AssistantDiagnostics.logExpressionLayer(
                chunkId = request.chunk.chunkId,
                invocationCount = 1,
                fallbackCount = 1,
                tokenLatencyMs = elapsedMs,
                skippedTierACount = 0,
                reason = "sanitization_rejected"
            )
            return null
        }

        if (!isFaithful(source = request.chunk.body, lead = metadataText(request.chunk, "lead"), response = sanitized)) {
            AssistantDiagnostics.logExpressionLayer(
                chunkId = request.chunk.chunkId,
                invocationCount = 1,
                fallbackCount = 1,
                tokenLatencyMs = elapsedMs,
                skippedTierACount = 0,
                reason = "faithfulness_rejected"
            )
            return null
        }

        AssistantDiagnostics.logExpressionLayer(
            chunkId = request.chunk.chunkId,
            invocationCount = 1,
            fallbackCount = 0,
            tokenLatencyMs = elapsedMs,
            skippedTierACount = 0,
            reason = "ok"
        )
        return ParaphrasedResponse(text = sanitized, latencyMs = elapsedMs)
    }

    private fun buildPrompt(request: CardParaphraseRequest): String =
        buildString {
            request.conversationHistory?.contextBlock?.takeIf { it.isNotBlank() }?.let { history ->
                appendLine("CONTEXT CONVERSAȚIE:")
                appendLine("```text")
                appendLine(history.take(MaxHistoryCharacters))
                appendLine("```")
                appendLine()
            } ?: run {
                appendLine("Ești Scouty, asistentul offline pentru drumeții în România.")
                appendLine("Răspunzi scurt, natural și prudent, în română.")
                appendLine()
            }
            appendLine("CONTEXT DISPOZITIV:")
            appendLine("```text")
            appendLine(buildDeviceContext(request.deviceContext))
            appendLine("```")
            appendLine()
            appendLine("CARD SURSĂ DE ADEVĂR:")
            appendLine("```text")
            appendLine(request.chunk.body.trim())
            appendLine("```")
            appendLine()
            appendLine("ÎNTREBAREA UTILIZATORULUI:")
            appendLine("```text")
            appendLine(request.userQuery.trim())
            appendLine("```")
            appendLine()
            appendLine("Reformulează cardul de mai sus într-un răspuns conversațional, fidel faptelor, fără să inventezi informații noi.")
            appendLine("Răspunsul trebuie să fie doar textul final pentru utilizator, fără markdown, fără JSON și fără explicații despre prompt.")
        }

    private fun buildDeviceContext(context: DeviceContextSnapshot): String {
        val parts = mutableListOf<String>()
        parts += "baterie=${context.batteryPercent}%"
        parts += "battery_safe=${context.batterySafe}"
        parts += "gps_fix=${context.gpsFixed}"
        context.trail?.let { trail ->
            parts += "traseu=${trail.name}"
            trail.localCode?.let { parts += "trail_id=$it" }
            trail.weatherForecast?.let { parts += "vreme=$it" }
        }
        return parts.joinToString("\n")
    }

    private fun sanitize(raw: String, cardBody: String): String? {
        val cleaned = raw
            .replace("```", "")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .trim('"', '\'', '“', '”')
            .trim()
        if (cleaned.isBlank()) {
            return null
        }
        val maxLength = (cardBody.length * MaxResponseToCardRatio).toInt().coerceAtLeast(MinResponseCharacters)
        return cleaned.takeIf { it.length <= maxLength }
    }

    internal fun isFaithful(source: String, lead: String?, response: String): Boolean {
        val facts = keyFactTokens(source, lead)
        if (facts.isEmpty()) {
            return true
        }
        val responseTokens = normalizedTokens(response).toSet()
        val normalizedResponse = normalize(response)
        val covered = facts.count { fact ->
            fact in responseTokens || normalizedResponse.contains(fact)
        }
        return covered.toDouble() / facts.size.toDouble() >= MinKeyFactCoverage
    }

    internal fun keyFactTokens(source: String, lead: String?): Set<String> {
        val facts = linkedSetOf<String>()
        NumberRegex.findAll(source).mapTo(facts) { normalize(it.value) }
        CapitalizedWordRegex.findAll(source).mapTo(facts) { normalize(it.value) }
        normalizedTokens(lead.orEmpty()).forEach { token ->
            if (token.length >= 4 && token !in StopWords) {
                facts += token
            }
        }
        return facts.filterTo(linkedSetOf()) { it.length >= 2 && it !in StopWords }
    }

    private fun normalizedTokens(value: String): List<String> =
        normalize(value)
            .split(" ")
            .filter { it.isNotBlank() }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^\\p{L}\\p{N}]+".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    private fun metadataText(chunk: RetrievedChunk, key: String): String? =
        metadata(chunk)?.get(key)
            ?.let { value ->
                when (value) {
                    is JsonPrimitive -> value.toString().trim('"')
                    else -> value.toString().trim('"')
                }
            }
            ?.takeIf { it.isNotBlank() }

    private fun metadata(chunk: RetrievedChunk): JsonObject? =
        chunk.metadataJson
            ?.takeIf { it.isNotBlank() }
            ?.let { raw ->
                runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            }

    private fun elapsedMsSince(startedAtNanos: Long): Long =
        (System.nanoTime() - startedAtNanos) / 1_000_000

    private companion object {
        private const val MaxHistoryCharacters = 6_000
        private const val MaxResponseToCardRatio = 2.5
        private const val MinResponseCharacters = 120
        private const val MinKeyFactCoverage = 0.70
        private val NumberRegex = Regex("\\b\\d+(?:[.,:/-]\\d+)*\\b")
        private val CapitalizedWordRegex = Regex("\\b\\p{Lu}[\\p{L}0-9-]{2,}\\b")
        private val StopWords = setOf(
            "acest", "aceasta", "aceste", "acolo", "adica", "apoi", "asa", "asadar",
            "cand", "care", "catre", "daca", "deci", "despre", "este", "fara", "foarte",
            "iata", "intr", "intre", "mai", "mult", "nici", "pentru", "peste", "prin",
            "sau", "sunt", "trebuie", "unui", "unei", "unde", "este", "scouty"
        )
    }
}
