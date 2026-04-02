package com.scouty.app.assistant.domain

import com.scouty.app.assistant.data.KnowledgeChunkStore
import com.scouty.app.assistant.data.KnowledgePackStatusProvider
import com.scouty.app.assistant.data.buildSearchTokens
import com.scouty.app.assistant.model.CardFamily
import com.scouty.app.assistant.model.KnowledgeChunkRecord
import com.scouty.app.assistant.model.KnowledgePackStatus
import com.scouty.app.assistant.model.ModelRuntimeState
import com.scouty.app.assistant.model.ModelStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FixedSlmInterpreterEngine(
    private val rawOutput: String
) : SlmInterpreterEngine {
    override suspend fun interpret(request: InterpreterRequest): InterpreterExecutionResult =
        InterpreterExecutionResult(
            rawOutput = rawOutput,
            modelStatus = ModelStatus(
                state = ModelRuntimeState.LOADED,
                availableOnDisk = true,
                details = "ready"
            )
        )
}

class UnavailableSlmInterpreterEngine(
    private val message: String = "interpreter unavailable"
) : SlmInterpreterEngine {
    override suspend fun interpret(request: InterpreterRequest): InterpreterExecutionResult =
        InterpreterExecutionResult(
            modelStatus = ModelStatus(details = message),
            error = message
        )
}

object NoopGroundedWordingEngine : GroundedWordingEngine {
    override suspend fun rephrase(request: GroundedWordingRequest): GroundedWordingResult? = null
}

class FixedGroundedWordingEngine(
    private val result: GroundedWordingResult
) : GroundedWordingEngine {
    override suspend fun rephrase(request: GroundedWordingRequest): GroundedWordingResult = result
}

class FixedPackStatusProvider(
    initialStatus: KnowledgePackStatus
) : KnowledgePackStatusProvider {
    private val internalStatus = MutableStateFlow(initialStatus)

    override val status: StateFlow<KnowledgePackStatus> = internalStatus

    override suspend fun ensureReady(): KnowledgePackStatus = internalStatus.value
}

class TokenAwareKnowledgeStore(
    private val chunks: List<KnowledgeChunkRecord>,
    private val packStatus: KnowledgePackStatus
) : KnowledgeChunkStore {
    override suspend fun packStatus(): KnowledgePackStatus = packStatus

    override suspend fun searchCandidates(
        query: String,
        preferredLanguages: List<String>,
        domainHints: List<String>,
        limit: Int
    ): List<KnowledgeChunkRecord> {
        val tokens = buildSearchTokens(query, shouldLog = false)
        return chunks
            .mapNotNull { chunk ->
                val score = tokenScore(chunk, tokens, domainHints, preferredLanguages)
                chunk.takeIf { score > 0 || tokens.isEmpty() }?.let { it to score }
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
    }

    override suspend fun searchStructuredCards(
        query: String,
        preferredLanguage: String,
        domain: String,
        topic: String,
        family: CardFamily?,
        limit: Int
    ): List<KnowledgeChunkRecord> =
        chunks
            .filter { it.language == preferredLanguage && it.domain == domain && it.topic == topic }
            .filter { family == null || it.cardFamily == family }
            .sortedByDescending { it.priority }
            .take(limit)

    private fun tokenScore(
        chunk: KnowledgeChunkRecord,
        tokens: List<String>,
        domainHints: List<String>,
        preferredLanguages: List<String>
    ): Int {
        val haystackTitle = normalizeForStore("${chunk.title} ${chunk.topic}")
        val haystackBody = normalizeForStore("${chunk.body} ${chunk.keywords}")
        val lexical = tokens.fold(0) { total, token ->
            when {
                token in haystackTitle -> total + 6
                token in haystackBody -> total + 3
                else -> total
            }
        }
        val language = if (chunk.language in preferredLanguages) 3 else 0
        val domain = if (domainHints.isEmpty() || chunk.domain in domainHints || chunk.topic in domainHints) 2 else 0
        return lexical + language + domain
    }

    private fun normalizeForStore(value: String): String =
        normalizeInterpreterText(value)
}
