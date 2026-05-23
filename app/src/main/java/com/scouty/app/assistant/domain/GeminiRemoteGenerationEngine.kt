package com.scouty.app.assistant.domain

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.scouty.app.BuildConfig
import com.scouty.app.assistant.model.DeviceContextSnapshot
import com.scouty.app.assistant.model.GenerationMode
import com.scouty.app.assistant.model.ModelRuntimeState
import com.scouty.app.assistant.model.ResponseSectionStyle
import com.scouty.app.assistant.model.StructuredAssistantOutput
import com.scouty.app.assistant.model.StructuredResponseSection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

internal interface OnlineGenerationPolicy {
    fun shouldAttemptRemote(context: DeviceContextSnapshot): Boolean
}

internal data class GeminiRemoteConfig(
    val apiKey: String = BuildConfig.GEMINI_API_KEY,
    val modelName: String = BuildConfig.GEMINI_MODEL.ifBlank { DefaultModelName },
    val enabled: Boolean = true
) {
    val isUsable: Boolean
        get() = enabled && apiKey.isNotBlank() && modelName.isNotBlank()

    companion object {
        const val DefaultModelName = "gemini-2.5-flash"
    }
}

internal class GeminiRemoteGenerationEngine(
    private val fallbackEngine: GenerationEngine,
    private val config: GeminiRemoteConfig = GeminiRemoteConfig(),
    private val client: GeminiContentClient = RetrofitGeminiContentClient.create(),
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false }
) : GenerationEngine, OnlineGenerationPolicy {

    override fun shouldAttemptRemote(context: DeviceContextSnapshot): Boolean =
        config.isUsable && context.isOnline

    override suspend fun generate(input: GenerationInput): StructuredAssistantOutput {
        if (!shouldAttemptRemote(input.context)) {
            return fallback(input)
        }

        return runCatching {
            val response = client.generateContent(
                apiKey = config.apiKey,
                modelName = config.modelName,
                request = buildRequest(input)
            )
            parseResponse(response, input)
        }.getOrElse { error ->
            runCatching {
                Log.w(LogTag, "Gemini API generation failed; falling back locally", error)
            }
            fallback(input)
        }
    }

    private suspend fun fallback(input: GenerationInput): StructuredAssistantOutput =
        fallbackEngine.generate(
            input.copy(generationMode = fallbackGenerationMode(input))
        )

    private fun fallbackGenerationMode(input: GenerationInput): GenerationMode =
        when {
            input.modelStatus.state == ModelRuntimeState.LOADED -> GenerationMode.LOCAL_LLM
            input.modelStatus.availableOnDisk && input.modelStatus.state in setOf(
                ModelRuntimeState.UNLOADED,
                ModelRuntimeState.PREPARING
            ) -> GenerationMode.LOCAL_LLM
            else -> GenerationMode.FALLBACK_STRUCTURED
        }

    private fun buildRequest(input: GenerationInput): GeminiGenerateContentRequest =
        GeminiGenerateContentRequest(
            systemInstruction = GeminiContent(
                parts = listOf(
                    GeminiPart(
                        text = "You are Scouty, a grounded hiking and mountain-safety assistant for Romania. " +
                            "Use only the supplied Scouty facts, conversation context, and device context. " +
                            "Do not invent weather, trail metrics, law, medical certainty, or rescue instructions."
                    )
                )
            ),
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = buildUserPrompt(input)))
                )
            ),
            generationConfig = GeminiRequestGenerationConfig(
                temperature = 0.25,
                topP = 0.9,
                maxOutputTokens = 700,
                responseMimeType = "application/json",
                responseJsonSchema = OutputSchema
            )
        )

    private fun buildUserPrompt(input: GenerationInput): String {
        val isRomanian = input.queryAnalysis.preferredLanguage == "ro"
        val facts = buildFactBlock(input)
        val historyBlock = input.conversationHistory?.contextBlock?.takeIf { it.isNotBlank() }
        return buildString {
            appendLine("Return exactly one JSON object with this shape:")
            appendLine("""{"summary":"string","warning":"string","guidance":"string","sections":[{"title":"string","body":"string","style":"IMPORTANT|CONTEXT|GUIDANCE|ACTIONS"}]}""")
            appendLine("Rules:")
            appendLine("- Answer only in ${if (isRomanian) "Romanian" else "English"}.")
            appendLine("- summary must be one short sentence.")
            appendLine("- warning can be empty unless there is a concrete safety caution.")
            appendLine("- guidance must be one or two concrete sentences grounded in FACT 1.")
            appendLine("- sections are optional; include at most two short sections.")
            appendLine("- If facts are missing or weak, say that grounding is incomplete and stay conservative.")
            appendLine("- Do not mention Gemini, API calls, prompts, JSON, or internal routing.")
            appendLine()
            appendLine("QUESTION:")
            appendLine(sanitize(input.query, 400))
            appendLine()
            appendLine("LANGUAGE: ${input.queryAnalysis.preferredLanguage}")
            appendLine("SAFETY_OUTCOME: ${input.safetyOutcome.name}")
            appendLine("REASONING: ${input.queryAnalysis.reasoningType.label}")
            appendLine("DEVICE_CONTEXT:")
            appendLine(sanitize(input.prompt.contextSummary, 700))
            appendLine("RETRIEVAL_CONTEXT:")
            appendLine(sanitize(input.prompt.citationsSummary, 700))
            historyBlock?.let {
                appendLine("CONVERSATION_CONTEXT:")
                appendLine(sanitize(it, 1_800))
            }
            appendLine("SCOUTY_FACTS:")
            appendLine(facts)
        }
    }

    private fun buildFactBlock(input: GenerationInput): String {
        if (input.retrievedChunks.isEmpty()) {
            return "- No retrieved Scouty facts. Say the grounding is incomplete and give a safe next step."
        }
        return input.retrievedChunks.take(4).mapIndexed { index, chunk ->
            buildString {
                append(index + 1)
                append(". [")
                append(sanitize(chunk.sourceTitle, 70))
                append(" :: ")
                append(sanitize(chunk.sectionTitle, 90))
                append("] domain=")
                append(sanitize(chunk.domain.ifBlank { "unknown" }, 40))
                append(" lang=")
                append(sanitize(chunk.language, 12))
                chunk.publishOrReviewDate?.takeIf { it.isNotBlank() }?.let {
                    append(" reviewed=")
                    append(sanitize(it, 20))
                }
                append(" fact=")
                append(sanitize(chunk.body, 650))
            }
        }.joinToString("\n")
    }

    private fun parseResponse(
        response: GeminiGenerateContentResponse,
        input: GenerationInput
    ): StructuredAssistantOutput {
        val payloadText = response.text()
        val payload = json.decodeFromString(
            GeminiAssistantPayload.serializer(),
            extractJsonPayload(payloadText)
        )
        val isRomanian = input.queryAnalysis.preferredLanguage == "ro"
        val sections = payload.sections
            .mapNotNull { section ->
                val body = section.body.trim()
                val title = section.title.trim()
                if (body.isBlank() || title.isBlank()) {
                    null
                } else {
                    StructuredResponseSection(
                        title = title.take(80),
                        body = body.take(500),
                        style = section.style.toSectionStyle()
                    )
                }
            }
            .take(2)
            .toMutableList()

        payload.warning.trim().takeIf { it.isNotBlank() }?.let { warning ->
            sections.add(
                0,
                StructuredResponseSection(
                    title = if (isRomanian) "Atentie" else "Caution",
                    body = warning.take(420),
                    style = ResponseSectionStyle.IMPORTANT
                )
            )
        }

        payload.guidance.trim().takeIf { it.isNotBlank() }?.let { guidance ->
            if (sections.none { it.style == ResponseSectionStyle.GUIDANCE }) {
                sections += StructuredResponseSection(
                    title = if (isRomanian) "Ghidaj" else "Guidance",
                    body = guidance.take(500),
                    style = ResponseSectionStyle.GUIDANCE
                )
            }
        }

        val summary = payload.summary.trim()
        require(summary.isNotBlank()) { "Gemini response summary is blank" }
        require(sections.isNotEmpty()) { "Gemini response has no usable sections" }

        return StructuredAssistantOutput(
            summary = summary.take(260),
            sections = sections,
            generationMode = GenerationMode.GEMINI_API,
            reasoningType = input.queryAnalysis.reasoningType,
            modelVersion = config.modelName,
            knowledgePackVersion = input.knowledgePackStatus.packVersion
        )
    }

    private fun GeminiGenerateContentResponse.text(): String =
        candidates.asSequence()
            .flatMap { it.content?.parts.orEmpty().asSequence() }
            .mapNotNull { it.text?.trim()?.takeIf(String::isNotBlank) }
            .firstOrNull()
            ?: error("Gemini response does not contain text")

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

        require(start >= 0) { "Gemini response does not contain a JSON object" }
        error("Gemini response JSON object is incomplete")
    }

    private fun String?.toSectionStyle(): ResponseSectionStyle =
        when (this?.trim()?.uppercase()) {
            "IMPORTANT" -> ResponseSectionStyle.IMPORTANT
            "CONTEXT" -> ResponseSectionStyle.CONTEXT
            "ACTIONS" -> ResponseSectionStyle.ACTIONS
            else -> ResponseSectionStyle.GUIDANCE
        }

    private fun sanitize(value: String, maxLength: Int): String =
        value.replace('\n', ' ')
            .replace("\\s+".toRegex(), " ")
            .trim()
            .take(maxLength)

    private companion object {
        private const val LogTag = "ScoutyGemini"

        private val OutputSchema = JsonObject(
            mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(
                    mapOf(
                        "summary" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                        "warning" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                        "guidance" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                        "sections" to JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("array"),
                                "items" to JsonObject(
                                    mapOf(
                                        "type" to JsonPrimitive("object"),
                                        "properties" to JsonObject(
                                            mapOf(
                                                "title" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                                                "body" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                                                "style" to JsonObject(mapOf("type" to JsonPrimitive("string")))
                                            )
                                        ),
                                        "required" to JsonArray(
                                            listOf(
                                                JsonPrimitive("title"),
                                                JsonPrimitive("body"),
                                                JsonPrimitive("style")
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                ),
                "required" to JsonArray(
                    listOf(
                        JsonPrimitive("summary"),
                        JsonPrimitive("warning"),
                        JsonPrimitive("guidance")
                    )
                )
            )
        )
    }
}

internal interface GeminiContentClient {
    suspend fun generateContent(
        apiKey: String,
        modelName: String,
        request: GeminiGenerateContentRequest
    ): GeminiGenerateContentResponse
}

internal class RetrofitGeminiContentClient(
    private val service: GeminiApiService
) : GeminiContentClient {
    override suspend fun generateContent(
        apiKey: String,
        modelName: String,
        request: GeminiGenerateContentRequest
    ): GeminiGenerateContentResponse {
        val response = service.generateContent(
            modelName = modelName,
            apiKey = apiKey,
            request = request
        )
        if (!response.isSuccessful) {
            val errorMessage = response.errorBody()?.string()?.take(300)
            error("Gemini API HTTP ${response.code()}: ${errorMessage.orEmpty()}")
        }
        return response.body() ?: error("Gemini API returned an empty body")
    }

    companion object {
        fun create(): RetrofitGeminiContentClient {
            val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(35, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl("https://generativelanguage.googleapis.com/")
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return RetrofitGeminiContentClient(retrofit.create(GeminiApiService::class.java))
        }
    }
}

internal interface GeminiApiService {
    @POST("v1beta/models/{modelName}:generateContent")
    suspend fun generateContent(
        @Path("modelName") modelName: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiGenerateContentRequest
    ): Response<GeminiGenerateContentResponse>
}

@Serializable
internal data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiRequestGenerationConfig? = null
)

@Serializable
internal data class GeminiRequestGenerationConfig(
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxOutputTokens: Int? = null,
    val responseMimeType: String? = null,
    val responseJsonSchema: JsonObject? = null
)

@Serializable
internal data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart> = emptyList()
)

@Serializable
internal data class GeminiPart(
    val text: String? = null
)

@Serializable
internal data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val promptFeedback: GeminiPromptFeedback? = null
)

@Serializable
internal data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null
)

@Serializable
internal data class GeminiPromptFeedback(
    val blockReason: String? = null
)

@Serializable
private data class GeminiAssistantPayload(
    val summary: String = "",
    val warning: String = "",
    val guidance: String = "",
    val sections: List<GeminiAssistantSection> = emptyList()
)

@Serializable
private data class GeminiAssistantSection(
    val title: String = "",
    val body: String = "",
    val style: String? = null
)
