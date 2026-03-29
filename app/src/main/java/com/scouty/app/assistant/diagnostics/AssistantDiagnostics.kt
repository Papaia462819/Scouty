package com.scouty.app.assistant.diagnostics

import android.util.Log
import com.scouty.app.BuildConfig
import com.scouty.app.assistant.domain.RetrievedChunk
import com.scouty.app.assistant.model.GenerationMode
import com.scouty.app.assistant.model.KnowledgeChunkRecord
import com.scouty.app.assistant.model.KnowledgePackStatus
import com.scouty.app.assistant.model.ModelStatus
import com.scouty.app.assistant.model.QueryAnalysis
import com.scouty.app.assistant.model.SafetyOutcome

object AssistantDiagnostics {
    private const val LogTag = "ScoutyAssistant"

    fun logBuildSearchTokens(rawQuery: String, tokens: List<String>) {
        debug("buildSearchTokens query=\"$rawQuery\" tokens=$tokens")
    }

    fun logQueryAnalysis(query: String, analysis: QueryAnalysis) {
        debug(
            "QueryAnalyzer.analyze query=\"$query\" preferredLanguage=${analysis.preferredLanguage} " +
                "tokens=${analysis.tokens} domainHints=${analysis.domainHints.map { "${it.domain}:${"%.2f".format(it.weight)}" }} " +
                "reasoningType=${analysis.reasoningType.name} routeContext=${analysis.routeContextQuery} " +
                "gearQuery=${analysis.gearQuery} safetyTags=${analysis.safetyTags}"
        )
    }

    fun logSqliteSearch(
        query: String,
        preferredLanguages: List<String>,
        domainHints: List<String>,
        tokens: List<String>,
        ftsQuery: String?,
        packStatus: KnowledgePackStatus,
        candidates: List<KnowledgeChunkRecord>
    ) {
        debug(
            "SqliteKnowledgeChunkStore.searchCandidates query=\"$query\" tokens=$tokens " +
                "preferredLanguages=$preferredLanguages domainHints=$domainHints ftsQuery=$ftsQuery " +
                "packStatus.isReady=${packStatus.isReady} dbPath=${packStatus.databasePath} " +
                "candidates=${formatChunkRecords(candidates)}"
        )
    }

    fun logRetrieval(
        query: String,
        selected: List<RetrievedChunk>,
        scoredCandidates: List<RetrievedChunk>
    ) {
        debug(
            "RetrievalEngine.retrieve query=\"$query\" scoredCandidates=${formatRetrievedChunks(scoredCandidates)} " +
                "selected=${formatRetrievedChunks(selected)}"
        )
    }

    fun logAnswerStart(
        query: String,
        packStatus: KnowledgePackStatus,
        modelStatus: ModelStatus,
        generationMode: GenerationMode
    ) {
        debug(
            "AssistantRepository.answer:start query=\"$query\" generationMode=${generationMode.name} " +
                "packStatus.isReady=${packStatus.isReady} dbPath=${packStatus.databasePath} " +
                "modelState=${modelStatus.state.name} modelVersion=${modelStatus.modelVersion} " +
                "modelPath=${modelStatus.modelPath} details=${modelStatus.details}"
        )
    }

    fun logAnswerEnd(
        query: String,
        packStatus: KnowledgePackStatus,
        modelStatus: ModelStatus,
        generationMode: GenerationMode,
        safetyOutcome: SafetyOutcome,
        retrievedChunks: List<RetrievedChunk>
    ) {
        debug(
            "AssistantRepository.answer:end query=\"$query\" generationMode=${generationMode.name} " +
                "safetyOutcome=${safetyOutcome.name} packStatus.isReady=${packStatus.isReady} " +
                "modelState=${modelStatus.state.name} modelVersion=${modelStatus.modelVersion} " +
                "retrieved=${formatRetrievedChunks(retrievedChunks)}"
        )
    }

    private fun debug(message: String) {
        if (BuildConfig.DEBUG) {
            runCatching {
                Log.d(LogTag, message)
            }
        }
    }

    private fun formatChunkRecords(chunks: List<KnowledgeChunkRecord>): String =
        if (chunks.isEmpty()) {
            "[]"
        } else {
            chunks.joinToString(
                prefix = "[",
                postfix = "]"
            ) { chunk ->
                "${chunk.chunkId}|${chunk.domain}|${chunk.language}|${chunk.title}"
            }
        }

    private fun formatRetrievedChunks(chunks: List<RetrievedChunk>): String =
        if (chunks.isEmpty()) {
            "[]"
        } else {
            chunks.joinToString(
                prefix = "[",
                postfix = "]"
            ) { chunk ->
                "${chunk.chunkId}|${chunk.domain}|${chunk.language}|score=${chunk.score}|${chunk.sectionTitle}"
            }
        }
}
