package com.scouty.app.assistant.domain

import com.scouty.app.assistant.data.ChatActionHandler
import com.scouty.app.assistant.data.KnowledgeChunkStore
import com.scouty.app.assistant.data.KnowledgePackStatusProvider
import com.scouty.app.assistant.domain.expression.CardParaphraseEngine
import com.scouty.app.assistant.domain.expression.CardParaphraseModel
import com.scouty.app.assistant.domain.tools.GrammarToolCallPlanner
import com.scouty.app.assistant.domain.tools.ToolCallModel
import com.scouty.app.assistant.domain.tools.ToolDispatcher
import com.scouty.app.assistant.model.AssistantAction
import com.scouty.app.assistant.model.AssistantWeatherRequest
import com.scouty.app.assistant.model.AssistantWeatherResult
import com.scouty.app.assistant.model.DeviceContextSnapshot
import com.scouty.app.assistant.model.GearContextItem
import com.scouty.app.assistant.model.GearItemDraft
import com.scouty.app.assistant.model.GearItemUpdate
import com.scouty.app.assistant.model.AssistantHourlyWeather
import com.scouty.app.assistant.model.GenerationMode
import com.scouty.app.assistant.model.KnowledgeChunkRecord
import com.scouty.app.assistant.model.KnowledgePackStatus
import com.scouty.app.assistant.model.ModelRuntimeState
import com.scouty.app.assistant.model.QueryAnalysis
import com.scouty.app.assistant.model.ResponseSectionStyle
import com.scouty.app.assistant.model.SafetyOutcome
import com.scouty.app.assistant.model.TrailHistoryEntry
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

    @Test
    fun tierBExpressionFlag_usesParaphraseBeforeTemplate() = runBlocking {
        val knowledgePackStatus = KnowledgePackStatus(
            available = true,
            packVersion = "pack-1",
            hashValid = true,
            integrityValid = true
        )
        val body = "Frontala cu baterii de rezervă este critică pe traseu; păstreaz-o la îndemână când plouă sau se întunecă."
        val paraphrase = "Ține frontala cu baterii de rezervă la îndemână, mai ales dacă plouă sau se întunecă pe traseu."
        val chunks = listOf(
            KnowledgeChunkRecord(
                chunkId = "cg_gear_frontala",
                domain = "gear_and_preparation",
                topic = "headlamp_rain_dark",
                language = "ro",
                title = "Frontala pe ploaie și întuneric",
                body = body,
                sourceTitle = "Scouty",
                publisher = "Scouty",
                sourceLanguage = "ro",
                adaptedLanguage = "ro",
                sourceTrust = 5,
                packVersion = "pack-1",
                keywords = "frontală baterii ploaie întuneric traseu",
                metadataJson = """{"tier":"B","tone":"conversational","lead":"Frontala cu baterii de rezervă este critică pe traseu."}"""
            )
        )
        val store = FakeSearchKnowledgeStore(chunks, knowledgePackStatus)
        val repository = AssistantRepository(
            context = null,
            knowledgePackManager = FakeKnowledgePackStatusProvider(knowledgePackStatus),
            knowledgeStore = store,
            queryAnalyzer = QueryAnalyzer(),
            retrievalEngine = RetrievalEngine(store),
            promptBuilder = PromptBuilder(),
            modelManager = ModelManager(
                modelLocator = FakeLocalModelLocator(LocalModelDiscovery(details = "missing bundle")),
                runtimeAdapter = FakeRuntimeAdapter()
            ),
            generationEngine = TemplateGenerationEngine(),
            medicalSafetyPolicy = MedicalSafetyPolicy(),
            cardParaphraseEngine = CardParaphraseEngine(FakeCardParaphraseModel(paraphrase)),
            useCardParaphraseExpression = true
        )

        val response = repository.answer(
            query = "Ce fac cu frontala dacă plouă și se întunecă?",
            context = DeviceContextSnapshot(
                batteryPercent = 73,
                gpsFixed = true,
                localeTag = "ro"
            )
        )

        assertEquals(GenerationMode.LOCAL_LLM, response.generationMode)
        assertEquals(paraphrase, response.answerText)
        assertFalse(response.answerText == body)
        assertTrue(response.answerText.contains("frontala", ignoreCase = true))
        assertTrue(response.answerText.contains("baterii", ignoreCase = true))
    }

    @Test
    fun onlineGemini_skipsLocalParaphraseAndUsesRemoteAnswer() = runBlocking {
        val knowledgePackStatus = KnowledgePackStatus(
            available = true,
            packVersion = "pack-1",
            hashValid = true,
            integrityValid = true
        )
        val body = "Frontala cu baterii de rezerva este critica pe traseu; pastreaz-o la indemana cand se intuneca."
        val chunks = listOf(
            KnowledgeChunkRecord(
                chunkId = "cg_gear_frontala",
                domain = "gear_and_preparation",
                topic = "headlamp_dark",
                language = "ro",
                title = "Frontala pe intuneric",
                body = body,
                sourceTitle = "Scouty",
                publisher = "Scouty",
                sourceLanguage = "ro",
                adaptedLanguage = "ro",
                sourceTrust = 5,
                packVersion = "pack-1",
                keywords = "frontala baterii intuneric traseu",
                metadataJson = """{"tier":"B","tone":"conversational"}"""
            )
        )
        val store = FakeSearchKnowledgeStore(chunks, knowledgePackStatus)
        val client = FakeGeminiContentClient(
            response = geminiResponse(
                """{"summary":"Raspuns remote Gemini.","warning":"","guidance":"Tine frontala la indemana si verifica bateriile inainte de plecare.","sections":[]}"""
            )
        )
        val modelManager = ModelManager(
            modelLocator = FakeLocalModelLocator(LocalModelDiscovery(details = "missing bundle")),
            runtimeAdapter = FakeRuntimeAdapter()
        )
        val repository = AssistantRepository(
            context = null,
            knowledgePackManager = FakeKnowledgePackStatusProvider(knowledgePackStatus),
            knowledgeStore = store,
            queryAnalyzer = QueryAnalyzer(),
            retrievalEngine = RetrievalEngine(store),
            promptBuilder = PromptBuilder(),
            modelManager = modelManager,
            generationEngine = GeminiRemoteGenerationEngine(
                fallbackEngine = TemplateGenerationEngine(),
                config = GeminiRemoteConfig(apiKey = "test-key", modelName = "gemini-test"),
                client = client
            ),
            medicalSafetyPolicy = MedicalSafetyPolicy(),
            cardParaphraseEngine = CardParaphraseEngine(FakeCardParaphraseModel("Paraphrase local care nu trebuie folosit.")),
            useCardParaphraseExpression = true
        )

        val response = repository.answer(
            query = "Ce fac cu frontala daca se intuneca?",
            context = DeviceContextSnapshot(
                batteryPercent = 73,
                gpsFixed = true,
                isOnline = true,
                localeTag = "ro"
            )
        )

        assertEquals(1, client.calls)
        assertEquals(GenerationMode.GEMINI_API, response.generationMode)
        assertEquals("gemini-test", response.modelVersion)
        assertTrue(response.answerText.contains("remote Gemini", ignoreCase = true))
        assertFalse(response.answerText.contains("Paraphrase local", ignoreCase = true))
    }

    @Test
    fun performanceHistory_answersWithoutActiveTrail() = runBlocking {
        val repository = createRepository(
            modelManager = ModelManager(
                modelLocator = FakeLocalModelLocator(LocalModelDiscovery(details = "missing bundle")),
                runtimeAdapter = FakeRuntimeAdapter()
            )
        )

        val response = repository.answer(
            query = "Cât mi-a luat să fac traseul Sinaia Padina?",
            context = DeviceContextSnapshot(
                localeTag = "ro",
                trailHistory = listOf(
                    TrailHistoryEntry(
                        name = "Sinaia - Cabana Padina",
                        region = "Bucegi",
                        completedAtEpochMillis = 1_768_780_800_000L,
                        distanceKm = 12.4,
                        elevationGainM = 980,
                        durationText = "5h 20min",
                        difficulty = "MEDIUM",
                        outcome = "COMPLETED"
                    )
                )
            )
        )

        assertEquals(GenerationMode.CARD_DIRECT, response.generationMode)
        assertTrue(response.answerText.contains("5h 20min"))
        assertTrue(response.answerText.contains("12.4 km"))
        assertTrue(response.citations.isEmpty())
    }

    @Test
    fun grammarToolCalling_lowConfidenceCanAskClarification() = runBlocking {
        val knowledgePackStatus = KnowledgePackStatus(
            available = true,
            packVersion = "pack-1",
            hashValid = true,
            integrityValid = true
        )
        val store = FakeSearchKnowledgeStore(emptyList(), knowledgePackStatus)
        val queryAnalyzer = QueryAnalyzer()
        val retrievalEngine = RetrievalEngine(store, queryAnalyzer)
        val repository = AssistantRepository(
            context = null,
            knowledgePackManager = FakeKnowledgePackStatusProvider(knowledgePackStatus),
            knowledgeStore = store,
            queryAnalyzer = queryAnalyzer,
            retrievalEngine = retrievalEngine,
            promptBuilder = PromptBuilder(),
            modelManager = ModelManager(
                modelLocator = FakeLocalModelLocator(LocalModelDiscovery(details = "missing bundle")),
                runtimeAdapter = FakeRuntimeAdapter()
            ),
            generationEngine = TemplateGenerationEngine(),
            medicalSafetyPolicy = MedicalSafetyPolicy(),
            toolCallPlanner = GrammarToolCallPlanner(
                FakeToolCallModel(
                    """{"tool":"ask_clarification","slot":"ignition_source","options":["lighter","matches","ferro"]}"""
                )
            ),
            toolDispatcher = ToolDispatcher(
                retrievalEngine = retrievalEngine,
                queryAnalyzer = queryAnalyzer
            ),
            useGrammarToolCalling = true,
            useLegacyInterpreter = false
        )

        val response = repository.answer(
            query = "Nu se aprinde focul",
            context = DeviceContextSnapshot(localeTag = "ro")
        )

        assertTrue(response.answerText.contains("Cu ce încerci"))
        assertEquals("ignition_source", response.conversationState.openQuestion?.targetSlot)
    }

    @Test
    fun gearInteraction_addsCustomItem() = runBlocking {
        val repository = createRepository(
            modelManager = ModelManager(
                modelLocator = FakeLocalModelLocator(LocalModelDiscovery(details = "missing bundle")),
                runtimeAdapter = FakeRuntimeAdapter()
            )
        )

        val response = repository.answer(
            query = "adauga manusi",
            context = DeviceContextSnapshot(localeTag = "ro")
        )

        val action = response.actions.single() as AssistantAction.AddGearItems
        assertEquals("Manusi", action.items.single().name)
        assertFalse(action.items.single().packed)
        assertTrue(response.answerText.contains("adaugat", ignoreCase = true))
    }

    @Test
    fun gearInteraction_marksExistingItemPacked() = runBlocking {
        val repository = createRepository(
            modelManager = ModelManager(
                modelLocator = FakeLocalModelLocator(LocalModelDiscovery(details = "missing bundle")),
                runtimeAdapter = FakeRuntimeAdapter()
            )
        )

        val response = repository.answer(
            query = "am pus apa",
            context = DeviceContextSnapshot(
                localeTag = "ro",
                gearItems = listOf(
                    GearContextItem(id = "water", name = "Apa", necessity = "MANDATORY", isPacked = false)
                )
            )
        )

        val action = response.actions.single() as AssistantAction.ToggleGearPacked
        assertEquals(listOf("water"), action.itemIds)
        assertTrue(action.packed)
    }

    @Test
    fun gearInteraction_ambiguousItemAsksClarification() = runBlocking {
        val repository = createRepository(
            modelManager = ModelManager(
                modelLocator = FakeLocalModelLocator(LocalModelDiscovery(details = "missing bundle")),
                runtimeAdapter = FakeRuntimeAdapter()
            )
        )

        val response = repository.answer(
            query = "bifeaza apa",
            context = DeviceContextSnapshot(
                localeTag = "ro",
                gearItems = listOf(
                    GearContextItem(id = "water-bottle", name = "Apa", necessity = "MANDATORY", isPacked = false),
                    GearContextItem(id = "waterproof-cover", name = "Husa de apa", necessity = "RECOMMENDED", isPacked = false)
                )
            )
        )

        assertTrue(response.actions.isEmpty())
        assertTrue(response.structuredOutput.followUpQuestions.size >= 2)
        assertTrue(response.answerText.contains("mai multe", ignoreCase = true))
    }

    @Test
    fun gearInteraction_removingMandatoryItemWarns() = runBlocking {
        val repository = createRepository(
            modelManager = ModelManager(
                modelLocator = FakeLocalModelLocator(LocalModelDiscovery(details = "missing bundle")),
                runtimeAdapter = FakeRuntimeAdapter()
            )
        )

        val response = repository.answer(
            query = "scoate frontala",
            context = DeviceContextSnapshot(
                localeTag = "ro",
                gearItems = listOf(
                    GearContextItem(id = "headlamp", name = "Frontala", necessity = "MANDATORY", isPacked = false)
                )
            )
        )

        val action = response.actions.single() as AssistantAction.RemoveGearItems
        assertEquals(listOf("headlamp"), action.itemIds)
        assertTrue(response.answerText.contains("obligatoriu", ignoreCase = true))
    }

    @Test
    fun weatherInteraction_usesLiveWeatherHandlerForHourlyRequest() = runBlocking {
        val repository = createRepository(
            modelManager = ModelManager(
                modelLocator = FakeLocalModelLocator(LocalModelDiscovery(details = "missing bundle")),
                runtimeAdapter = FakeRuntimeAdapter()
            )
        )
        val handler = FakeChatActionHandler(
            weatherResult = AssistantWeatherResult(
                available = true,
                isLive = true,
                locationLabel = "Sinaia",
                summary = "12.0°C, ploaie 60%",
                hourly = AssistantHourlyWeather(
                    time = "2026-05-10 13:00",
                    temperatureC = 12.0,
                    precipitationProbability = 60
                )
            )
        )

        val response = repository.answer(
            query = "cum va fi vremea in 2 ore",
            context = DeviceContextSnapshot(
                localeTag = "ro",
                isOnline = true,
                trail = TrailContextSnapshot(
                    name = "Sinaia",
                    latitude = 45.35,
                    longitude = 25.55
                )
            ),
            interactionHandler = handler
        )

        assertEquals(2, handler.weatherRequests.single().offsetHours)
        assertTrue(response.answerText.contains("12.0"))
        assertTrue(response.answerText.contains("Sinaia"))
    }

    @Test
    fun weatherInteraction_unavailableLiveWeatherDoesNotHallucinate() = runBlocking {
        val repository = createRepository(
            modelManager = ModelManager(
                modelLocator = FakeLocalModelLocator(LocalModelDiscovery(details = "missing bundle")),
                runtimeAdapter = FakeRuntimeAdapter()
            )
        )
        val handler = FakeChatActionHandler(
            weatherResult = AssistantWeatherResult(
                available = false,
                isLive = false,
                locationLabel = "Sinaia",
                summary = "Nu pot verifica vremea live fara conexiune.",
                errorMessage = "offline"
            )
        )

        val response = repository.answer(
            query = "ploua peste 3 ore?",
            context = DeviceContextSnapshot(
                localeTag = "ro",
                trail = TrailContextSnapshot(
                    name = "Sinaia",
                    latitude = 45.35,
                    longitude = 25.55
                )
            ),
            interactionHandler = handler
        )

        assertEquals(SafetyOutcome.CAUTION, response.safetyOutcome)
        assertTrue(response.answerText.contains("Nu pot verifica", ignoreCase = true))
        assertFalse(response.answerText.contains("va ploua sigur", ignoreCase = true))
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

private class FakeCardParaphraseModel(
    private val response: String
) : CardParaphraseModel {
    override suspend fun generate(prompt: String, options: LocalLlmGenerationOptions): String = response
}

private class FakeToolCallModel(
    private val response: String
) : ToolCallModel {
    override suspend fun generate(prompt: String, grammar: String, promptCacheHint: LocalLlmPromptCacheHint?): String =
        response
}

private class FakeChatActionHandler(
    private val weatherResult: AssistantWeatherResult = AssistantWeatherResult(
        available = false,
        isLive = false,
        summary = "weather unavailable"
    )
) : ChatActionHandler {
    val weatherRequests = mutableListOf<AssistantWeatherRequest>()
    val addedGear = mutableListOf<GearItemDraft>()
    val removedGearIds = mutableListOf<String>()
    val updatedGear = mutableListOf<GearItemUpdate>()
    val toggledGear = mutableListOf<Pair<List<String>, Boolean>>()

    override fun toggleGearPacked(itemIds: List<String>, packed: Boolean) {
        toggledGear += itemIds to packed
    }

    override fun addGearItems(items: List<GearItemDraft>) {
        addedGear += items
    }

    override fun removeGearItems(itemIds: List<String>) {
        removedGearIds += itemIds
    }

    override fun updateGearItems(updates: List<GearItemUpdate>) {
        updatedGear += updates
    }

    override suspend fun queryWeather(request: AssistantWeatherRequest): AssistantWeatherResult {
        weatherRequests += request
        return weatherResult
    }
}
