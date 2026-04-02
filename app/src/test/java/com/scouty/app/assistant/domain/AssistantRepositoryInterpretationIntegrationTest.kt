package com.scouty.app.assistant.domain

import com.scouty.app.assistant.data.KnowledgeChunkStore
import com.scouty.app.assistant.model.AssistantConversationState
import com.scouty.app.assistant.model.DeviceContextSnapshot
import com.scouty.app.assistant.model.KnowledgeChunkRecord
import com.scouty.app.assistant.model.KnowledgePackStatus
import com.scouty.app.assistant.model.TrailContextSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantRepositoryInterpretationIntegrationTest {
    private val packStatus = KnowledgePackStatus(
        available = true,
        packVersion = "test-pack",
        hashValid = true,
        integrityValid = true
    )

    @Test
    fun noisyRewrite_isAcceptedWhenRetrievalImproves() = runBlocking {
        val tractionChunk = chunk(
            chunkId = "traction",
            domain = "mountain_safety",
            topic = "wet_rock_traction",
            language = "en",
            title = "Hiking footwear and technique for maintaining traction",
            body = "Use footwear with better traction and take short deliberate steps on wet rocky terrain."
        )
        val waterChunk = chunk(
            chunkId = "water",
            domain = "survival_basics",
            topic = "water",
            language = "en",
            title = "Water purification",
            body = "Boil water or use a filter."
        )
        val repository = createRepository(
            chunks = listOf(tractionChunk, waterChunk),
            interpreterEngine = FixedSlmInterpreterEngine(
                """
                    {
                      "standalone_query":"What is the best hiking footwear and technique for maintaining traction on wet rocky terrain?",
                      "topic_hint":"traction",
                      "intent":"traction_help",
                      "slot_updates":{},
                      "resolved_open_question":false,
                      "needs_clarification":false,
                      "clarification_target":null,
                      "confidence":0.93
                    }
                """.trimIndent()
            ),
            knowledgeStore = object : KnowledgeChunkStore {
                override suspend fun packStatus(): KnowledgePackStatus = packStatus

                override suspend fun searchCandidates(
                    query: String,
                    preferredLanguages: List<String>,
                    domainHints: List<String>,
                    limit: Int
                ): List<KnowledgeChunkRecord> {
                    val normalized = normalizeInterpreterText(query)
                    return when {
                        " traction " in normalized && " footwear " in normalized ->
                            listOf(tractionChunk, waterChunk)
                        " boots " in normalized && " wet " in normalized ->
                            listOf(waterChunk)
                        else -> emptyList()
                    }
                }
            }
        )
        val response = repository.answer(
            query = "boots slipping wet rocks",
            context = DeviceContextSnapshot(localeTag = "en")
        )

        assertEquals(
            "What is the best hiking footwear and technique for maintaining traction on wet rocky terrain?",
            response.conversationState.lastStandaloneQuery
        )
        assertEquals("Hiking footwear and technique for maintaining traction", response.conversationState.lastRetrievedTitle)
    }

    @Test
    fun rewriteIsRejectedWhenConfidenceGainIsTooSmall() = runBlocking {
        val repository = createRepository(
            chunks = listOf(
                chunk(
                    chunkId = "rock",
                    domain = "mountain_safety",
                    topic = "wet_rock",
                    language = "en",
                    title = "Walking on wet rock",
                    body = "Move carefully on wet rock and keep your center of gravity low."
                )
            ),
            interpreterEngine = FixedSlmInterpreterEngine(
                """
                    {
                      "standalone_query":"How should I move on wet rock?",
                      "topic_hint":"wet rock",
                      "intent":"movement_help",
                      "slot_updates":{},
                      "resolved_open_question":false,
                      "needs_clarification":false,
                      "clarification_target":null,
                      "confidence":0.91
                    }
                """.trimIndent()
            )
        )
        val response = repository.answer(
            query = "boots slipping wet rocks",
            context = DeviceContextSnapshot(localeTag = "en")
        )

        assertEquals("boots slipping wet rocks", response.conversationState.lastStandaloneQuery)
        assertEquals("Walking on wet rock", response.conversationState.lastRetrievedTitle)
    }

    @Test
    fun invalidInterpreterJson_fallsBackToDeterministicRetrieval() = runBlocking {
        val repository = createRepository(
            chunks = listOf(
                chunk(
                    chunkId = "bear_general",
                    domain = "wildlife_romania",
                    topic = "bear_general",
                    title = "Întâlnire cu ursul",
                    body = "Păstrează distanța și retrage-te lent dacă vezi un urs."
                ),
                chunk(
                    chunkId = "bear_trail",
                    domain = "wildlife_romania",
                    topic = "trail_bear",
                    title = "Urși pe traseele marcate",
                    body = "Pe traseele marcate din zonă pot apărea urși."
                )
            ),
            interpreterEngine = FixedSlmInterpreterEngine("not-json")
        )
        val response = repository.answer(
            query = "Sunt ursi pe el?",
            context = DeviceContextSnapshot(localeTag = "ro")
        )

        assertEquals("Sunt ursi pe el?", response.conversationState.lastStandaloneQuery)
        assertEquals("Urși pe traseele marcate", response.conversationState.lastRetrievedTitle)
    }

    @Test
    fun lowConfidenceInterpreterOutput_isIgnored() = runBlocking {
        val repository = createRepository(
            chunks = listOf(
                chunk(
                    chunkId = "bear_general",
                    domain = "wildlife_romania",
                    topic = "bear_general",
                    title = "Întâlnire cu ursul",
                    body = "Păstrează distanța și retrage-te lent dacă vezi un urs."
                ),
                chunk(
                    chunkId = "bear_trail",
                    domain = "wildlife_romania",
                    topic = "trail_bear",
                    title = "Urși pe traseele marcate",
                    body = "Pe traseele marcate din zonă pot apărea urși."
                )
            ),
            interpreterEngine = FixedSlmInterpreterEngine(
                """
                    {
                      "standalone_query":"Sunt ursi pe traseul activ?",
                      "topic_hint":"trail wildlife",
                      "intent":"follow_up_question",
                      "slot_updates":{},
                      "resolved_open_question":false,
                      "needs_clarification":false,
                      "clarification_target":null,
                      "confidence":0.21
                    }
                """.trimIndent()
            )
        )
        val response = repository.answer(
            query = "Sunt ursi pe el?",
            context = DeviceContextSnapshot(localeTag = "ro")
        )

        assertEquals("Sunt ursi pe el?", response.conversationState.lastStandaloneQuery)
        assertEquals("Urși pe traseele marcate", response.conversationState.lastRetrievedTitle)
    }

    @Test
    fun unavailableInterpreter_fallsBackSafely() = runBlocking {
        val repository = createRepository(
            chunks = listOf(
                chunk(
                    chunkId = "bear_general",
                    domain = "wildlife_romania",
                    topic = "bear_general",
                    title = "Întâlnire cu ursul",
                    body = "Păstrează distanța și retrage-te lent dacă vezi un urs."
                )
            ),
            interpreterEngine = UnavailableSlmInterpreterEngine()
        )
        val response = repository.answer(
            query = "Sunt ursi pe el?",
            context = DeviceContextSnapshot(localeTag = "ro")
        )

        assertEquals("Sunt ursi pe el?", response.conversationState.lastStandaloneQuery)
        assertEquals("Întâlnire cu ursul", response.conversationState.lastRetrievedTitle)
        assertTrue(response.answerText.contains("urs", ignoreCase = true))
    }

    private fun createRepository(
        chunks: List<KnowledgeChunkRecord>,
        interpreterEngine: SlmInterpreterEngine,
        knowledgeStore: KnowledgeChunkStore = TokenAwareKnowledgeStore(chunks, packStatus)
    ): AssistantRepository =
        AssistantRepository(
            context = null,
            knowledgePackManager = FixedPackStatusProvider(packStatus),
            knowledgeStore = knowledgeStore,
            queryAnalyzer = QueryAnalyzer(),
            retrievalEngine = RetrievalEngine(knowledgeStore),
            modelManager = ModelManager(
                modelLocator = FakeLocalModelLocator(LocalModelDiscovery(details = "missing bundle")),
                runtimeAdapter = FakeRuntimeAdapter()
            ),
            slmInterpreterEngine = interpreterEngine,
            groundedWordingEngine = NoopGroundedWordingEngine,
            generationEngine = TemplateGenerationEngine(),
            medicalSafetyPolicy = MedicalSafetyPolicy()
        )

    private fun chunk(
        chunkId: String,
        domain: String,
        topic: String,
        title: String,
        body: String,
        language: String = "ro"
    ) = KnowledgeChunkRecord(
        chunkId = chunkId,
        domain = domain,
        topic = topic,
        language = language,
        title = title,
        body = body,
        sourceTitle = "Test source",
        publisher = "Scouty",
        sourceLanguage = language,
        adaptedLanguage = language,
        sourceTrust = 5,
        packVersion = "test-pack"
    )
}
