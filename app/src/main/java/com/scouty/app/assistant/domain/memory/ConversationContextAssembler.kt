package com.scouty.app.assistant.domain.memory

import com.scouty.app.assistant.data.ConversationRole
import com.scouty.app.assistant.data.ConversationStore
import com.scouty.app.assistant.data.ConversationTurn
import com.scouty.app.assistant.domain.LocalLlmPromptCacheHint
import com.scouty.app.assistant.model.AssistantConversationState
import com.scouty.app.assistant.model.DeviceContextSnapshot
import java.security.MessageDigest
import java.text.Normalizer

data class ConversationHistory(
    val conversationId: String,
    val systemPrompt: String,
    val summary: String?,
    val recentTurns: List<ConversationTurn>,
    val structuredStateBlock: String,
    val currentUserQuery: String,
    val nonCacheableContextBlock: String,
    val contextBlock: String,
    val promptCacheHint: LocalLlmPromptCacheHint,
    val historyTokenEstimate: Int,
    val cacheHitRate: Double
)

class ConversationContextAssembler(
    private val store: ConversationStore,
    private val maxTurns: Int = DefaultRecentTurns,
    private val contextWindowTokens: Int = DefaultContextWindowTokens,
    private val generationReserveTokens: Int = DefaultGenerationReserveTokens
) {
    private val cacheStats = PromptCacheStats()

    suspend fun assemble(
        conversationId: String,
        currentUserQuery: String,
        conversationState: AssistantConversationState,
        deviceContext: DeviceContextSnapshot
    ): ConversationHistory {
        val summary = store.loadSummary(conversationId)
            ?.takeIf { estimateTokens(it) <= MaxSummaryTokens }
        val rawTurns = store.loadRecent(conversationId, maxTurns)
        val structuredStateBlock = buildStructuredStateBlock(conversationState, deviceContext)
        val recentTurns = capTurnsByBudget(
            turns = rawTurns,
            summary = summary,
            structuredStateBlock = structuredStateBlock,
            currentUserQuery = currentUserQuery
        )

        val cacheablePrefix = buildCacheablePrefix(summary)
        val cacheKey = sha256(cacheablePrefix)
        val cacheHitRate = cacheStats.record(conversationId, cacheKey)
        val nonCacheableContextBlock = buildNonCacheableContextBlock(
            turns = recentTurns,
            structuredStateBlock = structuredStateBlock,
            currentUserQuery = currentUserQuery
        )
        val contextBlock = listOf(
            cacheablePrefix.trim(),
            nonCacheableContextBlock
        ).filter { it.isNotBlank() }.joinToString("\n\n")

        return ConversationHistory(
            conversationId = conversationId,
            systemPrompt = SystemPromptRo,
            summary = summary,
            recentTurns = recentTurns,
            structuredStateBlock = structuredStateBlock,
            currentUserQuery = currentUserQuery,
            nonCacheableContextBlock = nonCacheableContextBlock,
            contextBlock = contextBlock,
            promptCacheHint = LocalLlmPromptCacheHint(
                key = cacheKey,
                cacheablePrefix = cacheablePrefix
            ),
            historyTokenEstimate = estimateTokens(contextBlock),
            cacheHitRate = cacheHitRate
        )
    }

    private fun capTurnsByBudget(
        turns: List<ConversationTurn>,
        summary: String?,
        structuredStateBlock: String,
        currentUserQuery: String
    ): List<ConversationTurn> {
        val historyBudget = minOf(MaxHistoryBudgetTokens, contextWindowTokens - generationReserveTokens)
            .coerceAtLeast(0)
        val fixedTokens = estimateTokens(SystemPromptRo) +
            estimateTokens(summary.orEmpty()) +
            estimateTokens(structuredStateBlock) +
            estimateTokens(currentUserQuery)
        val rawTurnBudget = minOf(DefaultRawTurnsBudgetTokens, historyBudget - fixedTokens)
            .coerceAtLeast(0)

        val selected = ArrayDeque<ConversationTurn>()
        var used = 0
        turns.asReversed().forEach { turn ->
            val turnTokens = estimateTokens(formatTurn(turn))
            if (selected.isEmpty() || used + turnTokens <= rawTurnBudget) {
                selected.addFirst(turn)
                used += turnTokens
            }
        }
        return selected.toList()
    }

    private fun buildCacheablePrefix(summary: String?): String =
        buildString {
            appendLine(SystemPromptRo)
            summary?.takeIf { it.isNotBlank() }?.let {
                appendLine("REZUMAT CONVERSATIE:")
                appendLine(it.trim())
            }
        }

    private fun buildNonCacheableContextBlock(
        turns: List<ConversationTurn>,
        structuredStateBlock: String,
        currentUserQuery: String
    ): String =
        buildString {
            if (turns.isNotEmpty()) {
                appendLine("ULTIMELE TURURI:")
                turns.forEach { appendLine(formatTurn(it)) }
            }
            if (structuredStateBlock.isNotBlank()) {
                appendLine()
                appendLine("STARE STRUCTURATA:")
                appendLine(structuredStateBlock)
            }
            appendLine()
            appendLine("INTREBAREA CURENTA:")
            appendLine(currentUserQuery.trim())
        }.trim()

    private fun buildStructuredStateBlock(
        conversationState: AssistantConversationState,
        deviceContext: DeviceContextSnapshot
    ): String {
        val parts = mutableListOf<String>()
        conversationState.activeTopic?.let { parts += "subiect_activ=$it" }
        conversationState.lastRetrievedChunkId?.let { parts += "ultimul_card=$it" }
        conversationState.lastRetrievedTitle?.let { parts += "ultimul_titlu=${sanitize(it, 120)}" }
        conversationState.lastStandaloneQuery?.let { parts += "ultima_intrebare_rezolvata=${sanitize(it, 160)}" }
        if (conversationState.facts.isNotEmpty()) {
            parts += "fapte=${conversationState.facts.entries.joinToString("; ") { "${it.key}=${sanitize(it.value, 80)}" }}"
        }
        conversationState.openQuestion?.let { question ->
            parts += "intrebare_deschisa=${sanitize(question.text, 160)}"
            parts += "slot_deschis=${question.targetSlot}"
        }
        deviceContext.trail?.let { trail ->
            parts += "traseu_activ=${sanitize(trail.name, 120)}"
            trail.localCode?.let { parts += "trail_id=$it" }
        }
        return parts.joinToString("\n")
    }

    private fun formatTurn(turn: ConversationTurn): String {
        val role = when (turn.role) {
            ConversationRole.USER -> "Utilizator"
            ConversationRole.ASSISTANT -> "Asistent"
        }
        val chunk = turn.retrievedChunkId?.let { " [card=$it]" }.orEmpty()
        return "$role$chunk: ${sanitize(turn.text, MaxTurnCharacters)}"
    }

    private fun sanitize(value: String, maxCharacters: Int): String =
        value.replace('\n', ' ')
            .replace("\\s+".toRegex(), " ")
            .trim()
            .take(maxCharacters)

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private class PromptCacheStats {
        private val lastKeyByConversation = mutableMapOf<String, String>()
        private var attempts = 0
        private var hits = 0

        fun record(conversationId: String, key: String): Double {
            attempts += 1
            if (lastKeyByConversation[conversationId] == key) {
                hits += 1
            }
            lastKeyByConversation[conversationId] = key
            return if (attempts == 0) 0.0 else hits.toDouble() / attempts.toDouble()
        }
    }

    companion object {
        const val DefaultRecentTurns = 6
        const val DefaultContextWindowTokens = 8_192
        const val DefaultGenerationReserveTokens = 2_000
        const val DefaultRawTurnsBudgetTokens = 1_500
        const val MaxHistoryBudgetTokens = 8_000
        const val MaxSummaryTokens = 400
        const val MaxTurnCharacters = 600

        fun estimateTokens(value: String): Int {
            val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
            val words = normalized.split(Regex("\\s+")).count { it.isNotBlank() }
            val punctuation = normalized.count { !it.isLetterOrDigit() && !it.isWhitespace() }
            return (words * 1.35 + punctuation * 0.25).toInt().coerceAtLeast(0)
        }
    }
}

private val SystemPromptRo = """
Esti Scouty, asistentul offline pentru drumetii in Romania.
Raspunzi natural, scurt si prudent, in romana. Daca utilizatorul scrie in engleza, poti raspunde in engleza, dar romana ramane limba principala.
Foloseste istoricul doar pentru continuitate conversationala; faptele despre siguranta si traseu trebuie sa ramana ancorate in cardurile recuperate si in contextul dispozitivului.
Nu inventa distante, durate, vreme, reguli legale sau recomandari medicale.
Cand utilizatorul revine la un subiect discutat mai devreme, recunoaste legatura pe scurt si continua raspunsul.
""".trimIndent()
