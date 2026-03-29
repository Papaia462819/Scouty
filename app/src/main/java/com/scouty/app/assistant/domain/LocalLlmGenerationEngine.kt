package com.scouty.app.assistant.domain

import android.util.Log
import com.scouty.app.assistant.model.GenerationMode
import com.scouty.app.assistant.model.ModelRuntimeState
import com.scouty.app.assistant.model.ModelStatus
import com.scouty.app.assistant.model.ResponseSectionStyle
import com.scouty.app.assistant.model.StructuredAssistantOutput
import com.scouty.app.assistant.model.StructuredResponseSection
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
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
            runCatching {
                Log.d(
                    LogTag,
                    "Local LLM raw response=${rawResponse.text.replace("\n", "\\n").take(2000)}"
                )
            }
            runCatching {
                parseStructuredOutput(
                    rawResponse = rawResponse.text,
                    input = input,
                    modelStatus = rawResponse.modelStatus
                )
            }.recoverCatching { error ->
                repairStructuredOutput(
                    rawResponse = rawResponse.text,
                    input = input,
                    modelStatus = rawResponse.modelStatus
                ) ?: throw error
            }.getOrThrow()
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
        val isRomanian = input.queryAnalysis.preferredLanguage == "ro"
        val factsBlock = buildFactBlock(input)
        val fieldContext = buildFieldContext(input)
        val exampleOutput = if (isRomanian) {
            """{"summary":"Pastreaza focul mic si stinge-l complet inainte sa pleci.","warning":"Verifica daca focul este permis si tine-l departe de vegetatie uscata sau vant puternic.","guidance":"Nu pleca de langa foc pana cand cenusa si resturile sunt reci la atingere."}"""
        } else {
            """{"summary":"Keep the fire small and fully extinguish it before leaving.","warning":"Check that campfires are allowed and keep the fire away from dry vegetation or strong wind.","guidance":"Do not leave the fire until the ashes and remains are cool to the touch."}"""
        }

        return buildString {
            appendLine("You are Scouty's grounded offline assistant running locally on Android.")
            appendLine("Answer exclusively in ${if (isRomanian) "Romanian" else "English"}.")
            appendLine(
                if (isRomanian) {
                    "Every non-empty field must stay in Romanian. Do not switch to English."
                } else {
                    "Every non-empty field must stay in English. Do not switch to Romanian."
                }
            )
            appendLine("Use only the FACTS below.")
            appendLine("Do not copy the prompt, the field context, or the facts.")
            appendLine("Do not output markdown, code fences, explanations, or extra keys.")
            appendLine("Return exactly one compact JSON object with this schema:")
            appendLine("{\"summary\":\"string\",\"warning\":\"string\",\"guidance\":\"string\"}")
            appendLine("Requirements:")
            appendLine("- summary must be 1 short sentence.")
            appendLine("- warning is optional; use an empty string if none.")
            appendLine("- guidance must be 1 or 2 short sentences grounded in FACT 1.")
            appendLine("- FACT 1 is the primary grounding source. Ignore tangential facts.")
            appendLine("- ignore trail metadata unless the question explicitly asks about route, navigation, marker, distance, duration, or endpoints.")
            appendLine("- keep warning and guidance short and concrete.")
            appendLine("- never echo FACTS as nested JSON.")
            appendLine("Valid example:")
            appendLine(exampleOutput)
            appendLine("QUESTION: ${sanitizeSingleLine(input.query, 160)}")
            appendLine("SAFETY: ${input.safetyOutcome.name}")
            appendLine("REASONING: ${input.queryAnalysis.reasoningType.label}")
            appendLine("FIELD_CONTEXT: $fieldContext")
            appendLine("RUNTIME: model=${sanitizeSingleLine(modelStatus.modelVersion, 80)} backend=${sanitizeSingleLine(modelStatus.backend, 32)} pack=${sanitizeSingleLine(input.knowledgePackStatus.packVersion ?: "unknown", 48)}")
            appendLine("FACTS:")
            appendLine(factsBlock)
            appendLine("Return the JSON object now.")
        }
    }

    private fun parseStructuredOutput(
        rawResponse: String,
        input: GenerationInput,
        modelStatus: ModelStatus
    ): StructuredAssistantOutput {
        val isRomanian = input.queryAnalysis.preferredLanguage == "ro"
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
            .toMutableList()

        val warning = parsed.warning.orEmpty().trim()
        if (warning.isNotBlank()) {
            sections.add(
                0,
                StructuredResponseSection(
                    title = if (isRomanian) "Atentie" else "Caution",
                    body = warning,
                    style = ResponseSectionStyle.IMPORTANT
                )
            )
        }

        val groundedSections = fallbackSectionsFromRetrievedChunks(input, isRomanian)
        if (groundedSections.isNotEmpty()) {
            sections += groundedSections
        } else {
            val guidance = parsed.guidance.orEmpty().trim()
            if (guidance.isNotBlank()) {
                sections += StructuredResponseSection(
                    title = if (isRomanian) "Baza offline" else "Grounded guidance",
                    body = guidance,
                    style = ResponseSectionStyle.GUIDANCE
                )
            }
        }

        runCatching {
            Log.d(
                LogTag,
                "Parsed local response summary=\"${summary.take(240)}\" sections=${sections.map { "${it.style}:${it.title}" }}"
            )
        }

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
        var start = -1
        var depth = 0
        var inString = false
        var escaped = false

        cleaned.forEachIndexed { index, character ->
            if (start < 0) {
                if (character == '{') {
                    start = index
                    depth = 1
                }
                return@forEachIndexed
            }

            if (escaped) {
                escaped = false
                return@forEachIndexed
            }

            when (character) {
                '\\' -> if (inString) {
                    escaped = true
                }

                '"' -> inString = !inString
                '{' -> if (!inString) {
                    depth += 1
                }

                '}' -> if (!inString) {
                    depth -= 1
                    if (depth == 0) {
                        return cleaned.substring(start, index + 1)
                    }
                }
            }
        }

        require(start >= 0) { "Model response does not contain a JSON object" }
        error("Model response JSON object is incomplete")
    }

    private fun repairStructuredOutput(
        rawResponse: String,
        input: GenerationInput,
        modelStatus: ModelStatus
    ): StructuredAssistantOutput? {
        val summary = extractJsonStringField(rawResponse, "summary")
            ?.let(::cleanGeneratedText)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val isRomanian = input.queryAnalysis.preferredLanguage == "ro"
        val warning = extractJsonStringField(rawResponse, "warning")
            ?.let(::cleanGeneratedText)
            .orEmpty()
        val sections = mutableListOf<StructuredResponseSection>()
        if (warning.isNotBlank()) {
            sections += StructuredResponseSection(
                title = if (isRomanian) "Atentie" else "Caution",
                body = warning,
                style = ResponseSectionStyle.IMPORTANT
            )
        }
        sections += fallbackSectionsFromRetrievedChunks(input, isRomanian)
        if (sections.isEmpty()) {
            return null
        }

        runCatching {
            Log.w(
                LogTag,
                "Recovered local response from partial JSON summary=\"${summary.take(160)}\" sections=${sections.map { "${it.style}:${it.title}" }}"
            )
        }

        return StructuredAssistantOutput(
            summary = summary,
            sections = sections.take(4),
            generationMode = GenerationMode.LOCAL_LLM,
            reasoningType = input.queryAnalysis.reasoningType,
            modelVersion = modelStatus.modelVersion,
            knowledgePackVersion = input.knowledgePackStatus.packVersion
        )
    }

    private fun extractJsonStringField(rawResponse: String, key: String): String? {
        val pattern = Regex(
            """\"$key\"\s*:\s*\"((?:\\.|[^\"\\])*)\"""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        val match = pattern.find(rawResponse) ?: return null
        val encoded = "\"${match.groupValues[1]}\""
        return runCatching { json.decodeFromString(String.serializer(), encoded) }.getOrNull()
    }

    private fun buildFieldContext(input: GenerationInput): String {
        val parts = mutableListOf<String>()
        parts += "Battery=${input.context.batteryPercent}%${if (input.context.batterySafe) " BatterySafe" else ""}"
        parts += "GPS=${if (input.context.gpsFixed) "available" else "unstable"}"
        if (input.queryAnalysis.gearQuery && input.context.recommendedGear.isNotEmpty()) {
            parts += "Gear=${sanitizeSingleLine(input.context.recommendedGear.joinToString(", "), 120)}"
        }
        if (input.queryAnalysis.routeContextQuery) {
            input.context.trail?.let { trail ->
                parts += "Trail=${sanitizeSingleLine(trail.name, 80)}"
                trail.markingLabel?.takeIf { it.isNotBlank() }?.let {
                    parts += "Marker=${sanitizeSingleLine(it, 32)}"
                }
                trail.region?.takeIf { it.isNotBlank() }?.let {
                    parts += "Region=${sanitizeSingleLine(it, 48)}"
                }
            }
        }
        return parts.joinToString(" | ").take(320)
    }

    private fun buildFactBlock(input: GenerationInput): String {
        val promptFacts = selectPromptFacts(input)
        if (promptFacts.isEmpty()) {
            return "- No retrieved chunks. Say that the grounding is incomplete and stay conservative."
        }

        return promptFacts
            .mapIndexed { index, chunk ->
                buildString {
                    append(index + 1)
                    append(". [")
                    append(sanitizeSingleLine(chunk.sourceTitle, 48))
                    append(" :: ")
                    append(sanitizeSingleLine(chunk.sectionTitle, 64))
                    append("]")
                    append(" domain=")
                    append(sanitizeSingleLine(chunk.domain.ifBlank { "unknown" }, 32))
                    append(" lang=")
                    append(sanitizeSingleLine(chunk.language, 8))
                    append(" trust=")
                    append(chunk.sourceTrust)
                    chunk.publishOrReviewDate?.takeIf { it.isNotBlank() }?.let {
                        append(" reviewed=")
                        append(sanitizeSingleLine(it, 16))
                    }
                    append(" fact=")
                    append(sanitizeParagraph(chunk.body, 420))
                }
            }
            .joinToString("\n")
    }

    private fun selectPromptFacts(input: GenerationInput): List<RetrievedChunk> {
        val primary = input.retrievedChunks.firstOrNull() ?: return emptyList()
        val scoreFloor = when {
            input.queryAnalysis.routeContextQuery -> primary.score - 24
            else -> primary.score - 18
        }

        return input.retrievedChunks.filter { chunk ->
            when {
                chunk == primary -> true
                input.queryAnalysis.routeContextQuery -> {
                    chunk.language == primary.language &&
                        chunk.score >= scoreFloor &&
                        (
                            chunk.domain == primary.domain ||
                                chunk.domain == "route_intelligence_romania"
                            )
                }

                else -> {
                    chunk.language == primary.language &&
                        chunk.domain == primary.domain &&
                        chunk.score >= scoreFloor
                }
            }
        }.take(2)
    }

    private fun fallbackSectionsFromRetrievedChunks(
        input: GenerationInput,
        isRomanian: Boolean
    ): List<StructuredResponseSection> {
        val primary = input.retrievedChunks.firstOrNull() ?: return emptyList()
        val sections = mutableListOf<StructuredResponseSection>()
        sections += StructuredResponseSection(
            title = if (isRomanian) "Baza offline" else "Grounded guidance",
            body = sanitizeParagraph(primary.body, 320),
            style = ResponseSectionStyle.GUIDANCE
        )
        input.retrievedChunks
            .drop(1)
            .firstOrNull()
            ?.takeIf { it.domain == primary.domain && it.language == primary.language }
            ?.let { chunk ->
                sections += StructuredResponseSection(
                    title = if (isRomanian) "Detalii utile" else "Useful detail",
                    body = sanitizeParagraph(chunk.body, 220),
                    style = ResponseSectionStyle.CONTEXT
                )
            }
        return sections
    }

    private fun sanitizeSingleLine(value: String, maxLength: Int): String =
        value.replace("\\s+".toRegex(), " ").trim().take(maxLength)

    private fun sanitizeParagraph(value: String, maxLength: Int): String =
        value
            .replace('\n', ' ')
            .replace("\\s+".toRegex(), " ")
            .trim()
            .take(maxLength)

    private fun cleanGeneratedText(value: String): String {
        val tokens = value
            .replace('\n', ' ')
            .replace("\\s+".toRegex(), " ")
            .trim()
            .split(' ')
            .filter { it.isNotBlank() }

        val cleanedTokens = mutableListOf<String>()
        var previous = ""
        var repetition = 0
        for (token in tokens) {
            val normalized = token.lowercase()
            if (normalized == previous) {
                repetition += 1
                if (repetition >= 6) {
                    break
                }
            } else {
                previous = normalized
                repetition = 0
            }
            cleanedTokens += token
        }

        return cleanedTokens
            .joinToString(" ")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .take(220)
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
        val warning: String? = null,
        val guidance: String? = null,
        val sections: List<LocalLlmSection> = emptyList()
    )

    @Serializable
    private data class LocalLlmSection(
        val title: String,
        val body: String,
        val style: String? = null
    )

    private companion object {
        private const val LogTag = "ScoutyLocalLlm"
    }
}
