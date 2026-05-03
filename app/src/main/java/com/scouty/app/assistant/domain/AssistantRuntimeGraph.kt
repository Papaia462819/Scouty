package com.scouty.app.assistant.domain

import android.content.Context
import com.scouty.app.assistant.data.KnowledgePackManager
import com.scouty.app.assistant.data.SqliteKnowledgeChunkStore
import com.scouty.app.assistant.domain.retrieval.CrossEncoderReranker

data class RuntimeFeatureFlags(
    val useCrossEncoderReranker: Boolean = false,
    val useLlamaCpp: Boolean = false,
    val useConversationMemory: Boolean = false,
    val useLlmSummarizer: Boolean = false,
    val useQwenDefault: Boolean = false,
    val useCardParaphraseExpression: Boolean = false,
    val useGrammarToolCalling: Boolean = false,
    val useLegacyInterpreter: Boolean = true
)

class AssistantRuntimeGraph private constructor(
    context: Context,
    val featureFlags: RuntimeFeatureFlags = RuntimeFeatureFlags()
) {
    val knowledgePackManager = KnowledgePackManager(context)
    val modelManager = ModelManager(context, featureFlags)

    private val queryAnalyzer = QueryAnalyzer()
    private val knowledgeStore = SqliteKnowledgeChunkStore(knowledgePackManager)
    private val crossEncoderReranker = if (featureFlags.useCrossEncoderReranker) {
        CrossEncoderReranker(context)
    } else {
        null
    }
    private val retrievalEngine = RetrievalEngine(knowledgeStore, queryAnalyzer)
    private val promptBuilder = PromptBuilder()
    private val safetyPolicy = MedicalSafetyPolicy()
    private val fallbackEngine = TemplateGenerationEngine()
    private val generationEngine = LocalLlmGenerationEngine(
        modelManager = modelManager,
        fallbackEngine = fallbackEngine
    )
    private val trailContextEngine = TrailContextEngine()

    val repository = AssistantRepository(
        knowledgePackManager = knowledgePackManager,
        knowledgeStore = knowledgeStore,
        queryAnalyzer = queryAnalyzer,
        retrievalEngine = retrievalEngine,
        promptBuilder = promptBuilder,
        modelManager = modelManager,
        generationEngine = generationEngine,
        medicalSafetyPolicy = safetyPolicy,
        trailContextEngine = trailContextEngine,
        crossEncoderReranker = crossEncoderReranker
    )

    companion object {
        @Volatile
        private var instance: AssistantRuntimeGraph? = null

        fun get(
            context: Context,
            featureFlags: RuntimeFeatureFlags = RuntimeFeatureFlags()
        ): AssistantRuntimeGraph =
            instance ?: synchronized(this) {
                instance ?: AssistantRuntimeGraph(context.applicationContext, featureFlags).also { instance = it }
            }
    }
}
