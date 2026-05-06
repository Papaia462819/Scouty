package com.scouty.app.assistant.domain

import android.content.Context
import com.scouty.app.assistant.data.ConversationStore
import com.scouty.app.assistant.data.KnowledgePackManager
import com.scouty.app.assistant.data.SqliteKnowledgeChunkStore
import com.scouty.app.assistant.domain.expression.CardParaphraseEngine
import com.scouty.app.assistant.domain.expression.ModelManagerCardParaphraseModel
import com.scouty.app.assistant.domain.memory.ConversationContextAssembler
import com.scouty.app.assistant.domain.memory.SummaryCompactor
import com.scouty.app.assistant.domain.retrieval.CrossEncoderReranker
import com.scouty.app.assistant.domain.tools.GrammarToolCallPlanner
import com.scouty.app.assistant.domain.tools.ModelManagerToolCallModel
import com.scouty.app.assistant.domain.tools.ToolDispatcher

data class RuntimeFeatureFlags(
    val useCrossEncoderReranker: Boolean = true,
    val useLlamaCpp: Boolean = true,
    val useConversationMemory: Boolean = true,
    val useLlmSummarizer: Boolean = true,
    val useQwenDefault: Boolean = true,
    val useCardParaphraseExpression: Boolean = true,
    val useGrammarToolCalling: Boolean = true,
    val useLegacyInterpreter: Boolean = false
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
    private val conversationStore = if (featureFlags.useConversationMemory) {
        ConversationStore(context)
    } else {
        null
    }
    private val conversationContextAssembler = conversationStore?.let(::ConversationContextAssembler)
    private val summaryCompactor = conversationStore?.let {
        SummaryCompactor(
            store = it,
            useLlmSummarizer = featureFlags.useLlmSummarizer
        )
    }
    private val retrievalEngine = RetrievalEngine(knowledgeStore, queryAnalyzer)
    private val promptBuilder = PromptBuilder()
    private val safetyPolicy = MedicalSafetyPolicy()
    private val fallbackEngine = TemplateGenerationEngine()
    private val generationEngine = LocalLlmGenerationEngine(
        modelManager = modelManager,
        fallbackEngine = fallbackEngine
    )
    private val cardParaphraseEngine = if (featureFlags.useCardParaphraseExpression) {
        CardParaphraseEngine(ModelManagerCardParaphraseModel(modelManager))
    } else {
        null
    }
    private val toolCallPlanner = if (featureFlags.useGrammarToolCalling) {
        GrammarToolCallPlanner(ModelManagerToolCallModel(modelManager))
    } else {
        null
    }
    private val toolDispatcher = if (featureFlags.useGrammarToolCalling) {
        ToolDispatcher(
            retrievalEngine = retrievalEngine,
            queryAnalyzer = queryAnalyzer
        )
    } else {
        null
    }
    private val trailContextEngine = TrailContextEngine()

    val repository = AssistantRepository(
        featureFlags = featureFlags,
        knowledgePackManager = knowledgePackManager,
        knowledgeStore = knowledgeStore,
        queryAnalyzer = queryAnalyzer,
        retrievalEngine = retrievalEngine,
        promptBuilder = promptBuilder,
        modelManager = modelManager,
        generationEngine = generationEngine,
        medicalSafetyPolicy = safetyPolicy,
        trailContextEngine = trailContextEngine,
        crossEncoderReranker = crossEncoderReranker,
        conversationStore = conversationStore,
        conversationContextAssembler = conversationContextAssembler,
        summaryCompactor = summaryCompactor,
        cardParaphraseEngine = cardParaphraseEngine,
        useCardParaphraseExpression = featureFlags.useCardParaphraseExpression,
        toolCallPlanner = toolCallPlanner,
        toolDispatcher = toolDispatcher,
        useGrammarToolCalling = featureFlags.useGrammarToolCalling,
        useLegacyInterpreter = featureFlags.useLegacyInterpreter
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
