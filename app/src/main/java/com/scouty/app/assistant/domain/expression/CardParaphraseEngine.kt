package com.scouty.app.assistant.domain.expression

import com.scouty.app.assistant.diagnostics.AssistantDiagnostics
import com.scouty.app.assistant.domain.LocalLlmGenerationOptions
import com.scouty.app.assistant.domain.LocalLlmSamplerParams
import com.scouty.app.assistant.domain.ModelManager
import com.scouty.app.assistant.domain.RetrievalConfidenceTier
import com.scouty.app.assistant.domain.RetrievedChunk
import com.scouty.app.assistant.domain.memory.ConversationHistory
import com.scouty.app.assistant.model.DeviceContextSnapshot
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
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
    val conversationHistory: ConversationHistory?,
    val preferredLanguage: String? = null
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
    private val cache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean =
            size > MaxCacheEntries
    }
    private val cacheLock = Any()

    fun isEligibleForParaphrase(chunk: RetrievedChunk): Boolean =
        metadataText(chunk, "tier")?.equals("B", ignoreCase = true) == true

    suspend fun maybeParaphrase(request: CardParaphraseRequest): ParaphrasedResponse? {
        if (!request.featureEnabled) {
            log(
                chunkId = request.chunk.chunkId,
                invoked = false,
                rejected = false,
                reason = "feature_disabled"
            )
            return null
        }

        val tier = metadataText(request.chunk, "tier")
        if (tier == null) {
            log(
                chunkId = request.chunk.chunkId,
                invoked = false,
                rejected = false,
                reason = "missing_tier"
            )
            return null
        }
        if (!tier.equals("B", ignoreCase = true)) {
            log(
                chunkId = request.chunk.chunkId,
                invoked = false,
                rejected = false,
                reason = "tier_not_b"
            )
            return null
        }
        if (request.confidenceTier != RetrievalConfidenceTier.HIGH) {
            log(
                chunkId = request.chunk.chunkId,
                invoked = false,
                rejected = false,
                reason = "not_high_confidence"
            )
            return null
        }

        val cacheKey = cacheKey(request)
        cachedText(cacheKey)?.let { cached ->
            log(
                chunkId = request.chunk.chunkId,
                invoked = false,
                rejected = false,
                reason = "cache_hit",
                rawLength = cached.length,
                rawPrefix = cached
            )
            return ParaphrasedResponse(text = cached, latencyMs = 0)
        }

        val prompt = buildPrompt(request)
        val startedAt = System.nanoTime()
        val raw = runCatching {
            withTimeout(QwenParaphraseTimeoutMs) {
                model.generate(
                    prompt = prompt,
                    options = LocalLlmGenerationOptions(
                        sampler = LocalLlmSamplerParams(
                            maxTokens = QwenParaphraseMaxTokens,
                            temperature = 0.25f,
                            topK = 40,
                            topP = 0.9f,
                            randomSeed = 13
                        ),
                        promptCacheHint = null,
                        stopSequences = listOf("\n\n")
                    )
                )
            }
        }.getOrElse {
            val reason = if (it is TimeoutCancellationException) {
                "timeout"
            } else {
                "generation_error"
            }
            log(
                chunkId = request.chunk.chunkId,
                invoked = true,
                rejected = true,
                reason = reason,
                latencyMs = elapsedMsSince(startedAt)
            )
            return null
        }

        val elapsedMs = elapsedMsSince(startedAt)
        val sanitized = sanitize(raw, request.chunk.body)
        if (sanitized == null) {
            log(
                chunkId = request.chunk.chunkId,
                invoked = true,
                rejected = true,
                reason = "sanitization_rejected",
                latencyMs = elapsedMs,
                rawLength = raw.length,
                rawPrefix = raw
            )
            return null
        }

        val allowedSources = listOf(
            request.chunk.body,
            metadataText(request.chunk, "lead").orEmpty(),
            request.userQuery,
            buildDeviceContext(request.deviceContext)
        )
        if (introducesUnknownFacts(allowedSources, sanitized)) {
            log(
                chunkId = request.chunk.chunkId,
                invoked = true,
                rejected = true,
                reason = "faithfulness_rejected",
                latencyMs = elapsedMs,
                rawLength = raw.length,
                rawPrefix = raw
            )
            return null
        }

        cachePut(cacheKey, sanitized)
        log(
            chunkId = request.chunk.chunkId,
            invoked = true,
            rejected = false,
            reason = "ok",
            latencyMs = elapsedMs,
            rawLength = sanitized.length,
            rawPrefix = sanitized
        )
        return ParaphrasedResponse(text = sanitized, latencyMs = elapsedMs)
    }

    private fun buildPrompt(request: CardParaphraseRequest): String =
        buildString {
            appendLine("Reformulează cardul de mai jos într-un singur paragraf scurt, natural, în română, fidel faptelor din card.")
            appendLine("Nu inventa numere, locuri sau sfaturi noi. Nu folosi markdown, JSON sau explicații despre prompt.")
            appendLine()
            appendLine("CARD:")
            appendLine(request.chunk.body.trim())
            appendLine()
            appendLine("ÎNTREBARE UTILIZATOR:")
            appendLine(request.userQuery.trim())
            appendLine()
            appendLine("CONTEXT DISPOZITIV (folosește doar dacă e relevant):")
            appendLine(buildDeviceContext(request.deviceContext))
            appendLine()
            append("Răspunde acum cu o singură formulare conversațională a cardului, fără să repeți întrebarea.")
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

    internal fun introducesUnknownFacts(allowedSources: List<String>, response: String): Boolean {
        val allowedNumbers = linkedSetOf<String>()
        val allowedNouns = linkedSetOf<String>()
        for (source in allowedSources) {
            if (source.isBlank()) continue
            NumberRegex.findAll(source).mapTo(allowedNumbers) { normalize(it.value) }
            collectProperNouns(source).mapTo(allowedNouns) { normalize(it) }
        }
        val responseNumbers = NumberRegex.findAll(response).map { normalize(it.value) }
        if (responseNumbers.any { it !in allowedNumbers }) {
            return true
        }
        val responseNouns = collectProperNouns(response).map { normalize(it) }
        if (responseNouns.any { token -> token !in allowedNouns && token !in StopWords && token.length >= 3 }) {
            return true
        }
        return false
    }

    private fun collectProperNouns(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        for (match in CapitalizedWordRegex.findAll(text)) {
            val start = match.range.first
            if (isSentenceInitial(text, start)) continue
            result += match.value
        }
        return result
    }

    private fun isSentenceInitial(text: String, index: Int): Boolean {
        var i = index - 1
        while (i >= 0 && text[i].isWhitespace()) {
            i--
        }
        if (i < 0) return true
        val prev = text[i]
        return prev == '.' || prev == '!' || prev == '?' || prev == '\n' || prev == ':'
    }

    private fun cacheKey(request: CardParaphraseRequest): String {
        val language = request.preferredLanguage?.takeIf { it.isNotBlank() } ?: "ro"
        return request.chunk.chunkId + "|" + normalize(request.userQuery) + "|" + language
    }

    private fun cachedText(key: String): String? = synchronized(cacheLock) {
        cache[key]
    }

    private fun cachePut(key: String, value: String) {
        synchronized(cacheLock) {
            cache[key] = value
        }
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

    private fun log(
        chunkId: String,
        invoked: Boolean,
        rejected: Boolean,
        reason: String,
        latencyMs: Long = 0,
        rawLength: Int = 0,
        rawPrefix: String = ""
    ) {
        AssistantDiagnostics.logExpressionLayer(
            chunkId = chunkId,
            invocationCount = if (invoked) 1 else 0,
            fallbackCount = if (rejected) 1 else 0,
            tokenLatencyMs = latencyMs,
            reason = reason,
            rawLength = rawLength,
            rawPrefix = rawPrefix
        )
    }

    private companion object {
        private const val MaxResponseToCardRatio = 2.5
        private const val MinResponseCharacters = 120
        private const val MaxCacheEntries = 64
        private const val QwenParaphraseTimeoutMs = 2_000L
        private const val QwenParaphraseMaxTokens = 72
        private val NumberRegex = Regex("\\b\\d+(?:[.,:/-]\\d+)*\\b")
        private val CapitalizedWordRegex = Regex("\\b\\p{Lu}[\\p{L}0-9-]{2,}\\b")
        private val StopWords = setOf(
            "acest", "aceasta", "aceste", "acolo", "adica", "apoi", "asa", "asadar",
            "cand", "care", "catre", "daca", "deci", "despre", "este", "fara", "foarte",
            "iata", "intr", "intre", "mai", "mult", "nici", "pentru", "peste", "prin",
            "sau", "sunt", "trebuie", "unui", "unei", "unde", "scouty"
        )
    }
}
