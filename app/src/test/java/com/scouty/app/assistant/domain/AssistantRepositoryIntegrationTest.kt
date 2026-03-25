package com.scouty.app.assistant.domain

import com.scouty.app.assistant.data.KnowledgeChunkStore
import com.scouty.app.assistant.data.KnowledgePackStatusProvider
import com.scouty.app.assistant.model.DeviceContextSnapshot
import com.scouty.app.assistant.model.GenerationMode
import com.scouty.app.assistant.model.KnowledgeChunkRecord
import com.scouty.app.assistant.model.KnowledgePackStatus
import com.scouty.app.assistant.model.ModelRuntimeState
import com.scouty.app.assistant.model.ResponseSectionStyle
import com.scouty.app.assistant.model.SafetyOutcome
import com.scouty.app.assistant.model.TrailContextSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AssistantRepositoryIntegrationTest {
    @Test
    fun missingModel_usesFallbackButKeepsStructuredOutput() = runBlocking {
        val repository = createRepository(
            modelManager = ModelManager(
                modelLocator = FakeLocalModelLocator(
                    LocalModelDiscovery(details = "missing bundle")
                ),
                runtimeAdapter = FakeRuntimeAdapter()
            )
        )

        val response = repository.answer(
            query = "Care e marcajul traseului activ?",
            context = DeviceContextSnapshot(
                batteryPercent = 63,
                gpsFixed = true,
                localeTag = "ro",
                trail = TrailContextSnapshot(
                    name = "Sinaia - Cabana Padina",
                    localCode = "01MN01",
                    markingLabel = "banda rosie",
                    sourceUrls = listOf("https://example.com/trail")
                )
            )
        )

        assertTrue(response.usedFallback)
        assertEquals(GenerationMode.FALLBACK_STRUCTURED, response.generationMode)
        assertTrue(response.structuredOutput.sections.isNotEmpty())
        assertTrue(response.citations.isNotEmpty())
    }

    @Test
    fun safetyOverride_staysAuthoritativeOverLocalModelOutput() = runBlocking {
        val manager = readyModelManager(
            response = """
                {"summary":"Poți continua puțin mai lent.","sections":[{"title":"Ghid local","body":"Reduci ritmul și urmărești starea.","style":"GUIDANCE"}]}
            """.trimIndent()
        )
        val repository = createRepository(modelManager = manager)

        val response = repository.answer(
            query = "Nu pot sa calc deloc dupa ce am cazut",
            context = DeviceContextSnapshot(
                batteryPercent = 41,
                gpsFixed = true,
                localeTag = "ro"
            )
        )

        assertFalse(response.usedFallback)
        assertEquals(GenerationMode.LOCAL_LLM, response.generationMode)
        assertEquals(SafetyOutcome.EMERGENCY_ESCALATION, response.safetyOutcome)
        assertEquals(ResponseSectionStyle.IMPORTANT, response.structuredOutput.sections.first().style)
        assertTrue(response.answerText.contains("112") || response.answerText.contains("SOS"))
    }

    private fun createRepository(modelManager: ModelManager): AssistantRepository {
        val knowledgePackStatus = KnowledgePackStatus(
            available = true,
            packVersion = "pack-1",
            hashValid = true,
            integrityValid = true
        )
        val chunks = listOf(
            KnowledgeChunkRecord(
                chunkId = "route-1",
                domain = "route_intelligence_romania",
                topic = "01MN01",
                language = "ro",
                title = "Sinaia - Cabana Padina",
                body = "Marcaj: banda rosie. Date cheie: durata 6h.",
                sourceTitle = "Catalog Scouty",
                publisher = "Scouty",
                sourceLanguage = "ro",
                adaptedLanguage = "ro",
                sourceTrust = 5,
                packVersion = "pack-1"
            ),
            KnowledgeChunkRecord(
                chunkId = "medical-1",
                domain = "medical_emergency",
                topic = "lower_limb_trauma",
                language = "ro",
                title = "Trauma membru inferior",
                body = "Dacă nu poți călca deloc, prioritizează 112 și evită deplasarea inutilă.",
                sourceTitle = "Scouty First Aid",
                publisher = "Scouty",
                sourceLanguage = "ro",
                adaptedLanguage = "ro",
                sourceTrust = 5,
                packVersion = "pack-1"
            )
        )

        return AssistantRepository(
            context = null,
            knowledgePackManager = FakeKnowledgePackStatusProvider(knowledgePackStatus),
            knowledgeStore = FakeSearchKnowledgeStore(chunks, knowledgePackStatus),
            queryAnalyzer = QueryAnalyzer(),
            retrievalEngine = RetrievalEngine(FakeSearchKnowledgeStore(chunks, knowledgePackStatus)),
            promptBuilder = PromptBuilder(),
            modelManager = modelManager,
            generationEngine = LocalLlmGenerationEngine(modelManager, TemplateGenerationEngine()),
            medicalSafetyPolicy = MedicalSafetyPolicy()
        )
    }

    private fun readyModelManager(response: String): ModelManager {
        val tempDir = Files.createTempDirectory("scouty-repo-llm").toFile()
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
}

private class FakeKnowledgePackStatusProvider(
    initialStatus: KnowledgePackStatus
) : KnowledgePackStatusProvider {
    private val internalStatus = MutableStateFlow(initialStatus)

    override val status: StateFlow<KnowledgePackStatus> = internalStatus

    override suspend fun ensureReady(): KnowledgePackStatus = internalStatus.value
}

private class FakeSearchKnowledgeStore(
    private val chunks: List<KnowledgeChunkRecord>,
    private val packStatus: KnowledgePackStatus
) : KnowledgeChunkStore {
    override suspend fun packStatus(): KnowledgePackStatus = packStatus

    override suspend fun searchCandidates(
        query: String,
        preferredLanguages: List<String>,
        domainHints: List<String>,
        limit: Int
    ): List<KnowledgeChunkRecord> =
        chunks
            .filter { chunk ->
                domainHints.isEmpty() || chunk.domain in domainHints || chunk.topic in domainHints
            }
            .sortedByDescending { if (it.language in preferredLanguages) 1 else 0 }
            .take(limit)
}
