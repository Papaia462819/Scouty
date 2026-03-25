package com.scouty.app.assistant.domain

import android.content.Context
import com.scouty.app.assistant.data.KnowledgePackManager
import com.scouty.app.assistant.data.SqliteKnowledgeChunkStore

class AssistantRuntimeGraph private constructor(
    context: Context
) {
    val knowledgePackManager = KnowledgePackManager(context)
    val modelManager = ModelManager(context)

    private val queryAnalyzer = QueryAnalyzer()
    private val knowledgeStore = SqliteKnowledgeChunkStore(knowledgePackManager)
    private val retrievalEngine = RetrievalEngine(knowledgeStore, queryAnalyzer)
    private val promptBuilder = PromptBuilder()
    private val safetyPolicy = MedicalSafetyPolicy()
    private val fallbackEngine = TemplateGenerationEngine()
    private val generationEngine = LocalLlmGenerationEngine(
        modelManager = modelManager,
        fallbackEngine = fallbackEngine
    )

    val repository = AssistantRepository(
        knowledgePackManager = knowledgePackManager,
        knowledgeStore = knowledgeStore,
        queryAnalyzer = queryAnalyzer,
        retrievalEngine = retrievalEngine,
        promptBuilder = promptBuilder,
        modelManager = modelManager,
        generationEngine = generationEngine,
        medicalSafetyPolicy = safetyPolicy
    )

    companion object {
        @Volatile
        private var instance: AssistantRuntimeGraph? = null

        fun get(context: Context): AssistantRuntimeGraph =
            instance ?: synchronized(this) {
                instance ?: AssistantRuntimeGraph(context.applicationContext).also { instance = it }
            }
    }
}
