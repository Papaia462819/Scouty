package com.scouty.app.assistant.domain.memory

import com.scouty.app.assistant.data.ConversationRole
import com.scouty.app.assistant.data.ConversationStore
import com.scouty.app.assistant.data.ConversationTurn

data class SummaryCompactionResult(
    val compacted: Boolean,
    val compactedTurnCount: Int,
    val summaryTokenCount: Int,
    val historyTokenCount: Int
)

class SummaryCompactor(
    private val store: ConversationStore,
    useLlmSummarizer: Boolean = false,
    private val budgetTokens: Int = DefaultCompactionBudgetTokens,
    private val triggerTurnCount: Int = DefaultTriggerTurnCount,
    private val keepLastTurns: Int = DefaultKeepLastTurns
) {
    suspend fun compactIfNeeded(conversationId: String): SummaryCompactionResult {
        val turns = store.loadAllTurns(conversationId)
        val existingSummary = store.loadSummary(conversationId)
        val historyTokens = turns.sumOf { ConversationContextAssembler.estimateTokens(it.text) }
        val summaryTokens = ConversationContextAssembler.estimateTokens(existingSummary.orEmpty())

        if (turns.size <= triggerTurnCount || summaryTokens + historyTokens <= budgetTokens) {
            return SummaryCompactionResult(
                compacted = false,
                compactedTurnCount = 0,
                summaryTokenCount = summaryTokens,
                historyTokenCount = historyTokens
            )
        }

        val turnsToCompact = turns.dropLast(keepLastTurns)
        if (turnsToCompact.isEmpty()) {
            return SummaryCompactionResult(
                compacted = false,
                compactedTurnCount = 0,
                summaryTokenCount = summaryTokens,
                historyTokenCount = historyTokens
            )
        }

        val deterministicSummary = summarizeDeterministically(turnsToCompact)
        val updatedSummary = mergeSummaries(existingSummary, deterministicSummary)
            .let { enforceSummaryBudget(it) }
        store.updateSummary(conversationId, updatedSummary)
        store.pruneToRecent(conversationId, keepLastTurns)

        return SummaryCompactionResult(
            compacted = true,
            compactedTurnCount = turnsToCompact.size,
            summaryTokenCount = ConversationContextAssembler.estimateTokens(updatedSummary),
            historyTokenCount = turns.takeLast(keepLastTurns).sumOf { ConversationContextAssembler.estimateTokens(it.text) }
        )
    }

    private fun summarizeDeterministically(turns: List<ConversationTurn>): String {
        val bullets = mutableListOf<String>()
        var index = 0
        while (index < turns.size) {
            val userTurn = turns.drop(index).firstOrNull { it.role == ConversationRole.USER }
            if (userTurn == null) {
                break
            }
            val assistantTurn = turns.dropWhile { it.turnIdx <= userTurn.turnIdx }
                .firstOrNull { it.role == ConversationRole.ASSISTANT }
            bullets += buildString {
                append("- Utilizatorul a întrebat: ")
                append(quote(userTurn.text, 180))
                if (assistantTurn != null) {
                    append("; asistentul a răspuns: ")
                    append(quote(assistantTurn.text, 220))
                    assistantTurn.retrievedChunkId?.let { append(" [card=$it]") }
                }
                append(".")
            }
            index = turns.indexOfFirst { it.turnIdx > (assistantTurn?.turnIdx ?: userTurn.turnIdx) }
                .takeIf { it >= 0 } ?: turns.size
        }
        return buildString {
            appendLine("Rezumat determinist.")
            bullets.take(MaxSummaryBullets).forEach { appendLine(it) }
        }.trim()
    }

    private fun mergeSummaries(existingSummary: String?, newSummary: String): String =
        listOfNotNull(
            existingSummary?.takeIf { it.isNotBlank() },
            newSummary.takeIf { it.isNotBlank() }
        ).joinToString("\n")

    private fun enforceSummaryBudget(summary: String): String {
        val lines = summary.lineSequence()
            .filter { it.isNotBlank() }
            .toList()
        val selected = ArrayDeque<String>()
        var tokens = 0
        lines.asReversed().forEach { line ->
            val lineTokens = ConversationContextAssembler.estimateTokens(line)
            if (selected.isEmpty() || tokens + lineTokens <= ConversationContextAssembler.MaxSummaryTokens) {
                selected.addFirst(line)
                tokens += lineTokens
            }
        }
        return selected.joinToString("\n")
    }

    private fun quote(value: String, maxCharacters: Int): String =
        "\"${value.replace('\n', ' ').replace("\\s+".toRegex(), " ").trim().take(maxCharacters)}\""

    companion object {
        const val DefaultCompactionBudgetTokens = 1_500
        const val DefaultTriggerTurnCount = 12
        const val DefaultKeepLastTurns = 4
        const val MaxSummaryBullets = 16
    }
}
