package com.scouty.app.assistant.domain

import com.scouty.app.assistant.model.DeviceContextSnapshot
import com.scouty.app.assistant.model.GenerationMode
import com.scouty.app.assistant.model.KnowledgePackStatus
import com.scouty.app.assistant.model.ModelStatus
import com.scouty.app.assistant.model.QueryAnalysis
import com.scouty.app.assistant.model.ReasoningType
import com.scouty.app.assistant.model.SafetyOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiRemoteGenerationEngineTest {
    @Test
    fun onlineWithConfiguredKey_usesGeminiResponse() = runBlocking {
        val client = FakeGeminiContentClient(
            response = geminiResponse(
                """{"summary":"Raspuns din Gemini.","warning":"","guidance":"Ramai pe traseu si verifica marcajul.","sections":[]}"""
            )
        )
        val engine = GeminiRemoteGenerationEngine(
            fallbackEngine = TemplateGenerationEngine(),
            config = GeminiRemoteConfig(apiKey = "test-key", modelName = "gemini-test"),
            client = client
        )

        val result = engine.generate(testInput(isOnline = true))

        assertEquals(1, client.calls)
        assertEquals(GenerationMode.GEMINI_API, result.generationMode)
        assertEquals("Raspuns din Gemini.", result.summary)
        assertEquals("gemini-test", result.modelVersion)
    }

    @Test
    fun offline_doesNotCallGeminiAndFallsBack() = runBlocking {
        val client = FakeGeminiContentClient(
            response = geminiResponse("""{"summary":"unused","warning":"","guidance":"unused"}""")
        )
        val engine = GeminiRemoteGenerationEngine(
            fallbackEngine = TemplateGenerationEngine(),
            config = GeminiRemoteConfig(apiKey = "test-key", modelName = "gemini-test"),
            client = client
        )

        val result = engine.generate(testInput(isOnline = false))

        assertEquals(0, client.calls)
        assertEquals(GenerationMode.FALLBACK_STRUCTURED, result.generationMode)
        assertTrue(result.sections.isNotEmpty())
    }

    @Test
    fun apiError_fallsBackToStructuredOutput() = runBlocking {
        val client = FakeGeminiContentClient(failure = IllegalStateException("boom"))
        val engine = GeminiRemoteGenerationEngine(
            fallbackEngine = TemplateGenerationEngine(),
            config = GeminiRemoteConfig(apiKey = "test-key", modelName = "gemini-test"),
            client = client
        )

        val result = engine.generate(testInput(isOnline = true))

        assertEquals(1, client.calls)
        assertEquals(GenerationMode.FALLBACK_STRUCTURED, result.generationMode)
        assertTrue(result.sections.isNotEmpty())
    }

    private fun testInput(isOnline: Boolean) = GenerationInput(
        query = "Care e marcajul?",
        prompt = AssistantPrompt(
            query = "Care e marcajul?",
            contextSummary = "Traseu activ: Sinaia - Padina, marcaj banda rosie",
            citationsSummary = "Catalog Scouty -> Sinaia - Padina",
            reasoningSummary = "Route context"
        ),
        queryAnalysis = QueryAnalysis(
            preferredLanguage = "ro",
            tokens = listOf("marcaj"),
            domainHints = emptyList(),
            reasoningType = ReasoningType.ROUTE_CONTEXT,
            routeContextQuery = true
        ),
        retrievedChunks = listOf(
            RetrievedChunk(
                topic = "01MN01",
                sourceTitle = "Catalog Scouty",
                sectionTitle = "Sinaia - Padina",
                body = "Marcaj: banda rosie. Durata estimata: 6h.",
                score = 50,
                domain = "route_intelligence_romania",
                language = "ro"
            )
        ),
        context = DeviceContextSnapshot(
            isOnline = isOnline,
            localeTag = "ro"
        ),
        safetyOutcome = SafetyOutcome.NORMAL,
        generationMode = GenerationMode.GEMINI_API,
        modelStatus = ModelStatus(),
        knowledgePackStatus = KnowledgePackStatus(
            available = true,
            packVersion = "pack-1",
            hashValid = true,
            integrityValid = true
        )
    )
}

internal class FakeGeminiContentClient(
    private val response: GeminiGenerateContentResponse? = null,
    private val failure: Throwable? = null
) : GeminiContentClient {
    var calls: Int = 0
        private set

    override suspend fun generateContent(
        apiKey: String,
        modelName: String,
        request: GeminiGenerateContentRequest
    ): GeminiGenerateContentResponse {
        calls += 1
        failure?.let { throw it }
        return response ?: kotlin.error("No fake Gemini response configured")
    }
}

internal fun geminiResponse(text: String): GeminiGenerateContentResponse =
    GeminiGenerateContentResponse(
        candidates = listOf(
            GeminiCandidate(
                content = GeminiContent(
                    parts = listOf(GeminiPart(text = text))
                )
            )
        )
    )
