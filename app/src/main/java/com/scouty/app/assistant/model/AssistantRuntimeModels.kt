package com.scouty.app.assistant.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class GenerationMode(val label: String) {
    FALLBACK_STRUCTURED("Fallback structured"),
    LOCAL_LLM("Local LLM")
}

enum class ReasoningType(val label: String) {
    SAFETY_GUIDANCE("Safety guidance"),
    ROUTE_CONTEXT("Route context"),
    GEAR_ADVICE("Gear advice"),
    WEATHER_CONTEXT("Weather context"),
    GENERAL_RETRIEVAL("General retrieval")
}

enum class ResponseSectionStyle {
    IMPORTANT,
    CONTEXT,
    GUIDANCE,
    ACTIONS
}

data class StructuredResponseSection(
    val title: String,
    val body: String,
    val style: ResponseSectionStyle = ResponseSectionStyle.GUIDANCE
)

data class StructuredAssistantOutput(
    val summary: String,
    val sections: List<StructuredResponseSection>,
    val generationMode: GenerationMode,
    val reasoningType: ReasoningType,
    val modelVersion: String? = null,
    val knowledgePackVersion: String? = null
) {
    fun renderText(): String =
        buildString {
            append(summary.trim())
            sections.forEach { section ->
                if (section.body.isBlank()) {
                    return@forEach
                }
                appendLine()
                appendLine()
                append(section.title.trim())
                appendLine()
                append(section.body.trim())
            }
        }.trim()
}

enum class ModelRuntimeState(val label: String) {
    MISSING("Missing"),
    PREPARING("Preparing"),
    LOADED("Loaded"),
    FAILED("Failed"),
    UNLOADED("Unloaded")
}

data class ModelStatus(
    val runtimeLabel: String = "Google AI Edge",
    val modelVersion: String = "gemma-3-1b-it-int4",
    val state: ModelRuntimeState = ModelRuntimeState.MISSING,
    val details: String = "Local Gemma bundle is not available. Structured fallback remains active.",
    val modelPath: String? = null,
    val sourcePath: String? = null,
    val availableOnDisk: Boolean = false,
    val backend: String = "CPU",
    val fileSizeBytes: Long? = null,
    val lastCheckedEpochMs: Long? = null,
    val loadedAtEpochMs: Long? = null,
    val lastError: String? = null
) {
    val canGenerateLocally: Boolean
        get() = state == ModelRuntimeState.LOADED
}

@Serializable
data class KnowledgePackManifest(
    @SerialName("pack_version")
    val packVersion: String,
    @SerialName("generated_at")
    val generatedAt: String,
    @SerialName("db_file_name")
    val dbFileName: String,
    @SerialName("db_sha256")
    val dbSha256: String,
    @SerialName("chunk_count")
    val chunkCount: Int,
    @SerialName("source_count")
    val sourceCount: Int,
    val languages: List<String> = emptyList(),
    val domains: List<String> = emptyList()
)

data class KnowledgePackStatus(
    val available: Boolean = false,
    val packVersion: String? = null,
    val generatedAt: String? = null,
    val expectedChunkCount: Int = 0,
    val sourceCount: Int = 0,
    val hashValid: Boolean = false,
    val integrityValid: Boolean = false,
    val installedAtEpochMs: Long? = null,
    val databasePath: String? = null,
    val errorMessage: String? = null
) {
    val isReady: Boolean
        get() = available && hashValid && integrityValid && !packVersion.isNullOrBlank()
}

data class AssistantRuntimeDebugInfo(
    val knowledgePackStatus: KnowledgePackStatus = KnowledgePackStatus(),
    val modelStatus: ModelStatus = ModelStatus(),
    val generationMode: GenerationMode = GenerationMode.FALLBACK_STRUCTURED
)

data class KnowledgeChunkRecord(
    val chunkId: String,
    val domain: String,
    val topic: String,
    val language: String,
    val title: String,
    val body: String,
    val sourceTitle: String,
    val sourceUrl: String? = null,
    val publisher: String,
    val sourceLanguage: String,
    val adaptedLanguage: String,
    val publishOrReviewDate: String? = null,
    val sourceTrust: Int = 0,
    val safetyTags: List<String> = emptyList(),
    val countryScope: String = "global",
    val packVersion: String,
    val keywords: String = ""
)

data class DomainHint(
    val domain: String,
    val weight: Double
)

data class QueryAnalysis(
    val preferredLanguage: String,
    val tokens: List<String>,
    val domainHints: List<DomainHint>,
    val reasoningType: ReasoningType,
    val routeContextQuery: Boolean = false,
    val gearQuery: Boolean = false,
    val safetyTags: Set<String> = emptySet()
)
