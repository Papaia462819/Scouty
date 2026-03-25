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
import java.io.File
import java.nio.file.Files

class LocalLlmGenerationEngineTest {
    @Test
    fun validJson_usesLocalStructuredOutput() = runBlocking {
        val manager = readyModelManager(
            response = """
                {"summary":"Raspuns local grounded.","sections":[{"title":"Baza offline","body":"Folosește chunk-ul relevant.","style":"GUIDANCE"},{"title":"Context de teren","body":"Baterie 80%.","style":"CONTEXT"}]}
            """.trimIndent()
        )
        val engine = LocalLlmGenerationEngine(
            modelManager = manager,
            fallbackEngine = TemplateGenerationEngine()
        )

        val result = engine.generate(testInput())

        assertEquals(GenerationMode.LOCAL_LLM, result.generationMode)
        assertEquals("Raspuns local grounded.", result.summary)
        assertTrue(result.sections.any { it.title == "Context de teren" })
    }

    @Test
    fun invalidJson_fallsBackToTemplateEngine() = runBlocking {
        val manager = readyModelManager(response = "nu este json valid")
        val engine = LocalLlmGenerationEngine(
            modelManager = manager,
            fallbackEngine = TemplateGenerationEngine()
        )

        val result = engine.generate(testInput())

        assertEquals(GenerationMode.FALLBACK_STRUCTURED, result.generationMode)
        assertTrue(result.summary.contains("chunk-uri offline"))
    }

    @Test
    fun missingModel_fallsBackWithoutBreakingStructuredOutput() = runBlocking {
        val manager = ModelManager(
            modelLocator = FakeLocalModelLocator(
                LocalModelDiscovery(details = "bundle missing")
            ),
            runtimeAdapter = FakeRuntimeAdapter()
        )
        val engine = LocalLlmGenerationEngine(
            modelManager = manager,
            fallbackEngine = TemplateGenerationEngine()
        )

        val result = engine.generate(testInput())

        assertEquals(GenerationMode.FALLBACK_STRUCTURED, result.generationMode)
        assertTrue(result.sections.isNotEmpty())
        assertEquals("gemma-3-1b-it-int4", result.modelVersion)
    }

    private fun readyModelManager(response: String): ModelManager {
        val tempDir = Files.createTempDirectory("scouty-local-llm").toFile()
        val modelFile = File(tempDir, "gemma-3-1b-it-int4.task").apply { writeText("bundle") }
        return ModelManager(
            modelLocator = FakeLocalModelLocator(
                discovery = LocalModelDiscovery(
                    modelVersion = "gemma-3-1b-it-int4",
                    availableOnDisk = true,
                    sourceFile = modelFile,
                    preparedFile = modelFile,
                    details = "ready"
                ),
                preparedArtifact = LocalModelArtifact(
                    modelVersion = "gemma-3-1b-it-int4",
                    sourceFile = modelFile,
                    preparedFile = modelFile
                )
            ),
            runtimeAdapter = FakeRuntimeAdapter(response = response)
        )
    }

    private fun testInput() = GenerationInput(
        query = "Mi-am sucit glezna",
        prompt = AssistantPrompt(
            query = "Mi-am sucit glezna",
            contextSummary = "Traseu activ: Test Trail",
            citationsSummary = "Source -> Sectiune",
            reasoningSummary = "Safety guidance"
        ),
        queryAnalysis = QueryAnalysis(
            preferredLanguage = "ro",
            tokens = listOf("glezna"),
            domainHints = emptyList(),
            reasoningType = ReasoningType.SAFETY_GUIDANCE
        ),
        retrievedChunks = listOf(
            RetrievedChunk(
                topic = "ankle",
                sourceTitle = "Scouty First Aid",
                sectionTitle = "Entorsa",
                body = "Protejează glezna și limitează încărcarea.",
                score = 42
            )
        ),
        context = DeviceContextSnapshot(
            batteryPercent = 80,
            gpsFixed = true,
            batterySafe = false,
            recommendedGear = listOf("Bandaj", "Apa"),
            localeTag = "ro"
        ),
        safetyOutcome = SafetyOutcome.CAUTION,
        generationMode = GenerationMode.FALLBACK_STRUCTURED,
        modelStatus = ModelStatus(),
        knowledgePackStatus = KnowledgePackStatus(
            available = true,
            packVersion = "pack-1",
            hashValid = true,
            integrityValid = true
        )
    )
}
