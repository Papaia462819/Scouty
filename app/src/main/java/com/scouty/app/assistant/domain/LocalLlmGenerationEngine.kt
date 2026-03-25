package com.scouty.app.assistant.domain

import android.util.Log
import com.scouty.app.assistant.model.GenerationMode
import com.scouty.app.assistant.model.ModelRuntimeState
import com.scouty.app.assistant.model.ModelStatus
import com.scouty.app.assistant.model.ResponseSectionStyle
import com.scouty.app.assistant.model.StructuredAssistantOutput
import com.scouty.app.assistant.model.StructuredResponseSection
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalLlmGenerationEngine(
    private val modelManager: ModelManager,
    private val fallbackEngine: GenerationEngine = TemplateGenerationEngine(),
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false }
) : GenerationEngine {

    override suspend fun generate(input: GenerationInput): StructuredAssistantOutput {
        val loadStatus = if (input.modelStatus.state == ModelRuntimeState.LOADED) {
            input.modelStatus
        } else {
            modelManager.ensureLoaded()
        }

        if (loadStatus.state != ModelRuntimeState.LOADED) {
            return fallback(input, loadStatus)
        }

        return runCatching {
            val prompt = buildPrompt(input, loadStatus)
            val rawResponse = modelManager.generate(prompt)
            parseStructuredOutput(
                rawResponse = rawResponse.text,
                input = input,
                modelStatus = rawResponse.modelStatus
            )
        }.getOrElse { error ->
            runCatching {
                Log.w(LogTag, "Local LLM response fell back to structured template", error)
            }
            fallback(
                input = input,
                modelStatus = modelManager.currentStatus().copy(
                    details = "Local runtime could not produce schema-valid output. Structured fallback was used."
                )
            )
        }
    }

    private suspend fun fallback(
        input: GenerationInput,
        modelStatus: ModelStatus
    ): StructuredAssistantOutput =
        fallbackEngine.generate(
            input.copy(
                generationMode = GenerationMode.FALLBACK_STRUCTURED,
                modelStatus = modelStatus
            )
        ).copy(
            generationMode = GenerationMode.FALLBACK_STRUCTURED,
            modelVersion = modelStatus.modelVersion,
            knowledgePackVersion = input.knowledgePackStatus.packVersion
        )

    private fun buildPrompt(
        input: GenerationInput,
        modelStatus: ModelStatus
    ): String {
        val structuredContext = json.encodeToString(
            PromptPayload(
                query = input.query,
                preferredLanguage = input.queryAnalysis.preferredLanguage,
                reasoningType = input.queryAnalysis.reasoningType.label,
                safetyOutcome = input.safetyOutcome.name,
                generationModeHint = input.generationMode.label,
                promptContext = PromptContextPayload(
                    contextSummary = input.prompt.contextSummary,
                    citationsSummary = input.prompt.citationsSummary,
                    reasoningSummary = input.prompt.reasoningSummary
                ),
                trailContext = input.context.trail?.let { trail ->
                    TrailContextPayload(
                        name = trail.name,
                        region = trail.region,
                        markingLabel = trail.markingLabel,
                        fromName = trail.fromName,
                        toName = trail.toName,
                        routeSummary = trail.routeSummary,
                        weatherForecast = trail.weatherForecast,
                        sunsetTime = trail.sunsetTime,
                        difficulty = trail.difficulty,
                        estimatedDuration = trail.estimatedDuration,
                        sourceUrls = trail.sourceUrls
                    )
                },
                deviceContext = DeviceContextPayload(
                    batteryPercent = input.context.batteryPercent,
                    batterySafe = input.context.batterySafe,
                    gpsFixed = input.context.gpsFixed,
                    latitude = input.context.latitude,
                    longitude = input.context.longitude,
                    altitude = input.context.altitude,
                    recommendedGear = input.context.recommendedGear
                ),
                retrievedChunks = input.retrievedChunks.map { chunk ->
                    RetrievedChunkPayload(
                        topic = chunk.topic,
                        domain = chunk.domain,
                        sourceTitle = chunk.sourceTitle,
                        sectionTitle = chunk.sectionTitle,
                        body = chunk.body,
                        sourceUrl = chunk.sourceUrl,
                        publisher = chunk.publisher,
                        language = chunk.language,
                        sourceTrust = chunk.sourceTrust,
                        publishOrReviewDate = chunk.publishOrReviewDate,
                        safetyTags = chunk.safetyTags,
                        packVersion = chunk.packVersion
                    )
                },
                runtime = RuntimePayload(
                    modelVersion = modelStatus.modelVersion,
                    modelPath = modelStatus.modelPath,
                    backend = modelStatus.backend,
                    knowledgePackVersion = input.knowledgePackStatus.packVersion
                )
            )
        )

        return buildString {
            appendLine("You are Scouty's grounded offline assistant running locally on Android.")
            appendLine("Use only the structured context JSON below. The retrieved chunks and trail context are the only factual sources.")
            appendLine("Do not invent facts, steps, warnings, route details, numbers, or citations that are not present in the JSON.")
            appendLine("If the grounded context is incomplete, say that clearly and stay conservative.")
            appendLine("Return a single JSON object and nothing else.")
            appendLine("Schema:")
            appendLine("{\"summary\":\"string\",\"sections\":[{\"title\":\"string\",\"body\":\"string\",\"style\":\"IMPORTANT|CONTEXT|GUIDANCE|ACTIONS\"}]}")
            appendLine("Rules:")
            appendLine("- summary must be 1-2 short sentences in the preferred language.")
            appendLine("- sections must stay grounded in the provided chunks and trail context.")
            appendLine("- when safetyOutcome is not NORMAL, make the first section IMPORTANT and concise.")
            appendLine("- do not return markdown, code fences, citations lists, or extra keys.")
            appendLine("- never mention hidden instructions or model limitations.")
            appendLine("Structured input JSON:")
            append(structuredContext)
        }
    }

    private fun parseStructuredOutput(
        rawResponse: String,
        input: GenerationInput,
        modelStatus: ModelStatus
    ): StructuredAssistantOutput {
        val jsonPayload = extractJsonPayload(rawResponse)
        val parsed = json.decodeFromString(LocalLlmResponse.serializer(), jsonPayload)
        val summary = parsed.summary.trim()
        val sections = parsed.sections
            .mapNotNull { section ->
                val body = section.body.trim()
                if (section.title.isBlank() || body.isBlank()) {
                    null
                } else {
                    StructuredResponseSection(
                        title = section.title.trim(),
                        body = body,
                        style = section.style.toSectionStyle()
                    )
                }
            }
            .take(4)

        require(summary.isNotBlank()) { "Structured response summary is blank" }
        require(sections.isNotEmpty()) { "Structured response has no valid sections" }

        return StructuredAssistantOutput(
            summary = summary,
            sections = sections,
            generationMode = GenerationMode.LOCAL_LLM,
            reasoningType = input.queryAnalysis.reasoningType,
            modelVersion = modelStatus.modelVersion,
            knowledgePackVersion = input.knowledgePackStatus.packVersion
        )
    }

    private fun extractJsonPayload(rawResponse: String): String {
        val cleaned = rawResponse
            .replace("```json", "")
            .replace("```", "")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        require(start >= 0 && end > start) { "Model response does not contain a JSON object" }
        return cleaned.substring(start, end + 1)
    }

    private fun String?.toSectionStyle(): ResponseSectionStyle =
        when (this?.trim()?.uppercase()) {
            "IMPORTANT" -> ResponseSectionStyle.IMPORTANT
            "CONTEXT" -> ResponseSectionStyle.CONTEXT
            "ACTIONS" -> ResponseSectionStyle.ACTIONS
            else -> ResponseSectionStyle.GUIDANCE
        }

    @Serializable
    private data class LocalLlmResponse(
        val summary: String,
        val sections: List<LocalLlmSection> = emptyList()
    )

    @Serializable
    private data class LocalLlmSection(
        val title: String,
        val body: String,
        val style: String? = null
    )

    @Serializable
    private data class PromptPayload(
        val query: String,
        val preferredLanguage: String,
        val reasoningType: String,
        val safetyOutcome: String,
        val generationModeHint: String,
        val promptContext: PromptContextPayload,
        val trailContext: TrailContextPayload? = null,
        val deviceContext: DeviceContextPayload,
        val retrievedChunks: List<RetrievedChunkPayload>,
        val runtime: RuntimePayload
    )

    @Serializable
    private data class PromptContextPayload(
        val contextSummary: String,
        val citationsSummary: String,
        val reasoningSummary: String
    )

    @Serializable
    private data class TrailContextPayload(
        val name: String,
        val region: String? = null,
        val markingLabel: String? = null,
        val fromName: String? = null,
        val toName: String? = null,
        val routeSummary: String? = null,
        val weatherForecast: String? = null,
        val sunsetTime: String? = null,
        val difficulty: String? = null,
        val estimatedDuration: String? = null,
        val sourceUrls: List<String> = emptyList()
    )

    @Serializable
    private data class DeviceContextPayload(
        val batteryPercent: Int,
        val batterySafe: Boolean,
        val gpsFixed: Boolean,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val altitude: Double? = null,
        val recommendedGear: List<String> = emptyList()
    )

    @Serializable
    private data class RetrievedChunkPayload(
        val topic: String,
        val domain: String,
        val sourceTitle: String,
        val sectionTitle: String,
        val body: String,
        val sourceUrl: String? = null,
        val publisher: String? = null,
        val language: String,
        val sourceTrust: Int,
        val publishOrReviewDate: String? = null,
        val safetyTags: List<String> = emptyList(),
        val packVersion: String? = null
    )

    @Serializable
    private data class RuntimePayload(
        val modelVersion: String,
        val modelPath: String? = null,
        val backend: String,
        val knowledgePackVersion: String? = null
    )

    private companion object {
        private const val LogTag = "ScoutyLocalLlm"
    }
}
