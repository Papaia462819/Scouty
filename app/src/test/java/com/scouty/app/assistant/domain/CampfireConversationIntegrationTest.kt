package com.scouty.app.assistant.domain

import com.scouty.app.assistant.data.CampfireCardEmbedding
import com.scouty.app.assistant.data.CampfireEmbeddingStore
import com.scouty.app.assistant.data.CampfirePhrasingEmbedding
import com.scouty.app.assistant.data.KnowledgeChunkStore
import com.scouty.app.assistant.data.KnowledgePackStatusProvider
import com.scouty.app.assistant.model.AssistantConversationState
import com.scouty.app.assistant.model.CardFamily
import com.scouty.app.assistant.model.DeviceContextSnapshot
import com.scouty.app.assistant.model.GenerationMode
import com.scouty.app.assistant.model.KnowledgeChunkRecord
import com.scouty.app.assistant.model.KnowledgePackStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampfireConversationIntegrationTest {
    @Test
    fun campfireConversation_keepsTopicAcrossDefinitionAndFollowUp() = runBlocking {
        val repository = createRepository()
        val context = DeviceContextSnapshot(localeTag = "ro")

        val first = repository.answer("Cum fac focul?", context)
        assertEquals(GenerationMode.CARD_DIRECT, first.generationMode)
        assertEquals("campfire", first.structuredOutput.resolvedTopic)
        assertEquals(CardFamily.SCENARIO, first.structuredOutput.resolvedFamily)
        assertFalse(first.structuredOutput.followUpQuestions.isEmpty())

        val definition = repository.answer(
            query = "Ce e tinder?",
            context = context,
            conversationState = first.conversationState
        )
        assertEquals(CardFamily.DEFINITION, definition.structuredOutput.resolvedFamily)
        assertEquals("campfire_general_entry", definition.conversationState.pendingScenarioCardId)
        assertTrue(
            definition.answerText.contains("iasca", ignoreCase = true) ||
                definition.answerText.contains("materialul cel mai", ignoreCase = true)
        )

        val third = repository.answer(
            query = "N-am bricheta, am amnar",
            context = context,
            conversationState = definition.conversationState
        )
        assertEquals(CardFamily.SCENARIO, third.structuredOutput.resolvedFamily)
        assertTrue(third.answerText.contains("amnar", ignoreCase = true))
        assertFalse(third.answerText.contains("nu transforma focul", ignoreCase = true))
    }

    @Test
    fun wetFuelFollowUp_overridesScenarioWithConstraint() = runBlocking {
        val repository = createRepository()
        val context = DeviceContextSnapshot(localeTag = "ro")
        val starter = repository.answer("Cum fac focul?", context)

        val response = repository.answer(
            query = "Totu e ud",
            context = context,
            conversationState = starter.conversationState
        )

        assertEquals(GenerationMode.CARD_DIRECT, response.generationMode)
        assertEquals(CardFamily.CONSTRAINT, response.structuredOutput.resolvedFamily)
        assertEquals("campfire_general_entry", response.conversationState.pendingScenarioCardId)
        assertTrue(response.answerText.contains("interior", ignoreCase = true) || response.answerText.contains("ud", ignoreCase = true))
    }

    @Test
    fun noDirectIgnition_movesToAlternativesInsteadOfDeadEndAbort() = runBlocking {
        val repository = createRepository()
        val context = DeviceContextSnapshot(localeTag = "ro")

        val response = repository.answer(
            query = "Mi-e frig si n-am nimic de aprindere",
            context = context,
            conversationState = AssistantConversationState(activeTopic = "campfire")
        )

        assertEquals(GenerationMode.CARD_DIRECT, response.generationMode)
        assertTrue(
            response.answerText.contains("căldur", ignoreCase = true) ||
                response.answerText.contains("caldur", ignoreCase = true) ||
                response.answerText.contains("planului", ignoreCase = true)
        )
        assertTrue(
            response.structuredOutput.followUpQuestions.any {
                it.contains("scânteie", ignoreCase = true) ||
                    it.contains("scanteie", ignoreCase = true) ||
                    it.contains("adăpostit", ignoreCase = true) ||
                    it.contains("adapostit", ignoreCase = true)
            }
        )
    }

    @Test
    fun typoScenarioQuery_keepsGenericEntryAsPrimary() = runBlocking {
        val repository = createRepository()
        val context = DeviceContextSnapshot(localeTag = "ro")

        val response = repository.answer("Vreu sa fac focu", context)

        assertEquals(GenerationMode.CARD_DIRECT, response.generationMode)
        assertEquals(CardFamily.SCENARIO, response.structuredOutput.resolvedFamily)
        assertEquals("campfire_general_entry", response.conversationState.lastCardId)
        assertTrue(response.answerText.contains("varianta simplă", ignoreCase = true) || response.answerText.contains("foc mic", ignoreCase = true))
    }

    @Test
    fun definitionTypoFollowUp_promotesDefinitionAndPreservesPendingScenario() = runBlocking {
        val repository = createRepository()
        val context = DeviceContextSnapshot(localeTag = "ro")
        val starter = repository.answer("Cum fac focul?", context)

        val response = repository.answer(
            query = "Ce e tindar?",
            context = context,
            conversationState = starter.conversationState
        )

        assertEquals(CardFamily.DEFINITION, response.structuredOutput.resolvedFamily)
        assertEquals("campfire_general_entry", response.conversationState.pendingScenarioCardId)
        assertTrue(response.answerText.contains("iasca", ignoreCase = true))
    }

    @Test
    fun shortFollowUp_keepsPendingScenario() = runBlocking {
        val repository = createRepository()
        val context = DeviceContextSnapshot(localeTag = "ro")
        val starter = repository.answer("Cum fac focul?", context)

        val response = repository.answer(
            query = "Cum?",
            context = context,
            conversationState = starter.conversationState
        )

        assertEquals(CardFamily.SCENARIO, response.structuredOutput.resolvedFamily)
        assertEquals("campfire_general_entry", response.conversationState.pendingScenarioCardId)
        assertTrue(response.answerText.contains("varianta simplă", ignoreCase = true) || response.answerText.contains("foc mic", ignoreCase = true))
    }

    @Test
    fun rememberedIgnitionSource_skipsRepeatedIgnitionQuestionAndUsesContext() = runBlocking {
        val repository = createRepository()
        val context = DeviceContextSnapshot(localeTag = "ro")
        val starter = repository.answer("Am bricheta", context)

        assertEquals("lighter", starter.conversationState.facts["ignition_source"])

        val response = repository.answer(
            query = "M-am razgandit, vreau un foc pentru caldura",
            context = context,
            conversationState = starter.conversationState
        )

        assertEquals(CardFamily.SCENARIO, response.structuredOutput.resolvedFamily)
        assertEquals("campfire_for_warmth", response.conversationState.lastCardId)
        assertTrue(response.answerText.contains("brichet", ignoreCase = true))
        assertTrue(
            response.structuredOutput.followUpQuestions.any {
                it.contains("A plouat recent", ignoreCase = true) ||
                    it.contains("destul de uscat", ignoreCase = true)
            }
        )
        assertFalse(
            response.structuredOutput.followUpQuestions.any {
                it.contains("brichet", ignoreCase = true) ||
                    it.contains("chibrit", ignoreCase = true) ||
                    it.contains("amnar", ignoreCase = true)
            }
        )
    }

    @Test
    fun rainfallReply_deducesWetConditionsAndRoutesToWetGuidance() = runBlocking {
        val repository = createRepository()
        val context = DeviceContextSnapshot(localeTag = "ro")
        val withIgnition = repository.answer("Am bricheta", context)
        val starter = repository.answer(
            query = "Vreau un foc pentru caldura",
            context = context,
            conversationState = withIgnition.conversationState
        )

        assertTrue(
            starter.structuredOutput.followUpQuestions.any {
                it.contains("A plouat recent", ignoreCase = true) ||
                    it.contains("destul de uscat", ignoreCase = true)
            }
        )

        val response = repository.answer(
            query = "Tocmai ce a plouat",
            context = context,
            conversationState = starter.conversationState
        )

        assertEquals("wet", response.conversationState.facts["fuel_condition"])
        assertEquals(CardFamily.CONSTRAINT, response.structuredOutput.resolvedFamily)
        assertTrue(
            response.answerText.contains("ud", ignoreCase = true) ||
                response.answerText.contains("mai uscat", ignoreCase = true) ||
                response.answerText.contains("interior", ignoreCase = true)
        )
    }

    @Test
    fun indirectIgnitionReply_usesInterpreterToResolveOpenQuestion() = runBlocking {
        val repository = createRepository(
            slmInterpreterEngine = FixedSlmInterpreterEngine(
                """
                    {
                      "standalone_query":"Am o brichetă în rucsac",
                      "topic_hint":"campfire ignition",
                      "intent":"follow_up_resolution",
                      "slot_updates":{"ignition_source":"lighter"},
                      "resolved_open_question":true,
                      "needs_clarification":false,
                      "clarification_target":null,
                      "confidence":0.92
                    }
                """.trimIndent()
            )
        )
        val context = DeviceContextSnapshot(localeTag = "ro")
        val starterState = AssistantConversationState(
            activeTopic = "campfire",
            openQuestion = com.scouty.app.assistant.model.AssistantOpenQuestion(
                text = "Ai brichetă, chibrite sau amnar?",
                targetSlot = "ignition_source",
                allowedValues = listOf("lighter", "matches", "ferro", "recognized_spark", "none")
            ),
            askedFollowUps = listOf("Ai brichetă, chibrite sau amnar?")
        )

        val response = repository.answer(
            query = "Am una in rucsac",
            context = context,
            conversationState = starterState
        )

        assertEquals("lighter", response.conversationState.facts["ignition_source"])
        assertTrue(response.answerText.contains("brichet", ignoreCase = true))
    }

    @Test
    fun groundedWording_rephrasesSummaryWithoutChangingGroundedCard() = runBlocking {
        val repository = createRepository(
            groundedWordingEngine = FixedGroundedWordingEngine(
                GroundedWordingResult(summary = "Ține focul mic, pregătit și ușor de controlat.")
            )
        )
        val context = DeviceContextSnapshot(localeTag = "ro")

        val response = repository.answer("Am bricheta", context)

        assertEquals("campfire_light_with_lighter", response.conversationState.lastCardId)
        assertEquals("Aprindere cu brichetă", response.conversationState.lastRetrievedTitle)
        assertEquals("Ține focul mic, pregătit și ușor de controlat.", response.structuredOutput.summary)
        assertTrue(response.citations.any { it.sectionTitle == "Aprindere cu brichetă" })
    }

    @Test
    fun degradedRememberedMaterial_reorientsToAlternativeInsteadOfReusingOldMaterial() = runBlocking {
        val repository = createRepository()
        val context = DeviceContextSnapshot(localeTag = "ro")
        val intro = repository.answer("Cum fac focul?", context)
        val starter = repository.answer(
            query = "Am niste hartii",
            context = context,
            conversationState = intro.conversationState
        )

        assertEquals("paper", starter.conversationState.facts["tinder_material"])
        assertEquals("yes", starter.conversationState.facts["tinder_available"])

        val response = repository.answer(
            query = "Mi s-au udat foile",
            context = context,
            conversationState = starter.conversationState
        )

        assertEquals("paper", response.conversationState.facts["tinder_material"])
        assertEquals("wet", response.conversationState.facts["tinder_condition"])
        assertEquals("no", response.conversationState.facts["tinder_available"])
        assertEquals("improvise", response.conversationState.facts["tinder_strategy"])
        assertTrue(
            response.answerText.contains("material foarte fin", ignoreCase = true) ||
                response.answerText.contains("locuri protejate", ignoreCase = true) ||
                response.answerText.contains("mai uscat", ignoreCase = true)
        )
    }

    @Test
    fun lostIgnitionSource_reorientsWithApologyAndRemainingOptions() = runBlocking {
        val repository = createRepository()
        val context = DeviceContextSnapshot(localeTag = "ro")
        val starter = repository.answer("Am bricheta", context)

        assertEquals("lighter", starter.conversationState.facts["ignition_source"])

        val response = repository.answer(
            query = "Mi-am pierdut bricheta",
            context = context,
            conversationState = starter.conversationState
        )

        assertEquals("none", response.conversationState.facts["ignition_source"])
        assertEquals("lighter", response.conversationState.facts["compromised_item"])
        assertEquals("lost", response.conversationState.facts["compromised_reason"])
        assertTrue(response.answerText.contains("Îmi pare rău", ignoreCase = true))
        assertEquals(listOf("Ai chibrite sau amnar?"), response.structuredOutput.followUpQuestions)
    }

    @Test
    fun improvisationReply_routesToImprovisationTutorialInsteadOfRepeatingIgnitionCard() = runBlocking {
        val repository = createRepository()
        val context = DeviceContextSnapshot(localeTag = "ro")
        val starter = repository.answer("Am bricheta", context)

        assertEquals("campfire_light_with_lighter", starter.conversationState.lastCardId)

        val response = repository.answer(
            query = "Improvizam din ce am la mine",
            context = context,
            conversationState = starter.conversationState
        )

        assertEquals(CardFamily.SCENARIO, response.structuredOutput.resolvedFamily)
        assertEquals("campfire_improvise_tinder_from_available_materials", response.conversationState.lastCardId)
        assertTrue(
            response.answerText.contains("improvizează", ignoreCase = true) ||
            response.answerText.contains("material foarte fin", ignoreCase = true) ||
                response.answerText.contains("locuri protejate", ignoreCase = true)
        )
    }

    @Test
    fun freshAbortLikeQuery_staysOnScenarioWithoutSlotEvidence() = runBlocking {
        val repository = createRepository()
        val context = DeviceContextSnapshot(localeTag = "ro")

        val response = repository.answer("Nu merita focul?", context)

        assertEquals(CardFamily.SCENARIO, response.structuredOutput.resolvedFamily)
        assertEquals("campfire_general_entry", response.conversationState.lastCardId)
        assertFalse(response.answerText.contains("nu mai merită", ignoreCase = true))
    }

    @Test
    fun semanticReranking_surfacesWarmthScenarioForColloquialQuery() = runBlocking {
        val embeddingStore = testEmbeddingStore(
            queryPhrase = "tin cald cu foc",
            cardVectors = mapOf(
                "campfire_general_entry" to floatArrayOf(1f, 0f),
                "campfire_for_warmth" to floatArrayOf(0f, 1f)
            )
        )
        val repository = createRepository(embeddingStore = embeddingStore)
        val context = DeviceContextSnapshot(localeTag = "ro")

        val response = repository.answer("Tin cald cu foc?", context)

        assertEquals(CardFamily.SCENARIO, response.structuredOutput.resolvedFamily)
        assertEquals("campfire_for_warmth", response.conversationState.lastCardId)
        assertTrue(response.answerText.contains("căldură", ignoreCase = true) || response.answerText.contains("caldura", ignoreCase = true))
    }

    private fun createRepository(
        embeddingStore: CampfireEmbeddingStore = CampfireEmbeddingStore(topic = "campfire", language = "ro"),
        slmInterpreterEngine: SlmInterpreterEngine = UnavailableSlmInterpreterEngine(),
        groundedWordingEngine: GroundedWordingEngine = NoopGroundedWordingEngine
    ): AssistantRepository {
        val packStatus = KnowledgePackStatus(
            available = true,
            packVersion = "campfire-pack",
            hashValid = true,
            integrityValid = true
        )
        val cards = listOf(
            campfireCard(
                id = "campfire_general_entry",
                family = CardFamily.SCENARIO,
                priority = 20,
                title = "Pornire simplă și sigură a focului",
                lead = "Îți spun varianta simplă și sigură: pregătești mai întâi locul și materialul fin, apoi aprinzi un foc mic, nu o grămadă de lemne.",
                keywords = listOf("cum fac focul", "cum fac foc", "vreau sa fac foc", "pornesc focul"),
                userPhrasings = listOf("cum fac focul", "cum fac un foc", "vreau sa fac foc", "cum pornesc focul"),
                actionsNow = listOf(
                    "verifică dacă focul este permis",
                    "pregătește iasca și surcelele",
                    "pornește cu flacără mică"
                ),
                avoid = listOf("nu aprinde lângă vegetație uscată"),
                relatedCards = listOf("campfire_light_with_ferro", "campfire_wet_fuel"),
                followUps = listOf("Pentru ce îți trebuie: căldură, gătit sau fiert apă?", "Ai brichetă, chibrite sau amnar?")
            ),
            campfireCard(
                id = "campfire_for_warmth",
                family = CardFamily.SCENARIO,
                priority = 90,
                title = "Foc pentru căldură",
                lead = "Dacă vrei foc pentru căldură, ținta este unul mic și stabil, care pornește repede și nu-ți consumă inutil energia.",
                keywords = listOf("caldura", "incalzire", "mi e frig", "foc pentru caldura"),
                userPhrasings = listOf("vreau foc pentru caldura", "mi e frig si vreau foc", "foc pentru incalzire"),
                slotConstraints = mapOf("goal" to "warmth"),
                actionsNow = listOf("alege o vatră ferită de vânt", "pregătește suficient material fin", "menține focul compact"),
                followUps = listOf("Ai brichetă, chibrite sau amnar?", "Totul e uscat sau e ud în jur?")
            ),
            campfireCard(
                id = "campfire_light_with_lighter",
                family = CardFamily.SCENARIO,
                priority = 64,
                title = "Aprindere cu brichetă",
                lead = "Cu brichetă, cheia este să aprinzi iasca bine pregătită, nu să ții flacăra mult timp sub lemn gros.",
                keywords = listOf("bricheta", "lighter", "aprind cu bricheta"),
                userPhrasings = listOf("am bricheta", "aprind cu bricheta", "cum fac foc cu bricheta"),
                slotConstraints = mapOf("ignition_source" to "lighter"),
                actionsNow = listOf("adu flacăra în baza cuibului de iască", "ține lemnul mare deoparte până când focul respiră singur"),
                followUps = listOf("Ai iască sau improvizăm din ce ai la tine?"),
                relatedCards = listOf("campfire_prepare_tinder_kindling_fuel", "campfire_improvise_tinder_from_available_materials", "campfire_wet_fuel")
            ),
            campfireCard(
                id = "campfire_tinder_definition",
                family = CardFamily.DEFINITION,
                priority = 95,
                title = "Definiție: iască",
                lead = "Iasca înseamnă materialul cel mai ușor de aprins, cel care preia primul scânteia sau flacăra.",
                keywords = listOf("iasca", "ce e iasca", "ce inseamna iasca"),
                userPhrasings = listOf("ce e iasca", "ce inseamna iasca"),
                actionsNow = listOf("dacă nu ai iască dedicată, caută material foarte fin și uscat"),
                followUps = listOf("Ai deja ceva care poate funcționa ca iască?"),
                term = "iasca",
                plainLanguageDefinition = "Iasca este stratul cel mai fin din sistemul focului."
            ),
            campfireCard(
                id = "campfire_improvise_tinder_from_available_materials",
                family = CardFamily.SCENARIO,
                priority = 86,
                title = "Improvizează iasca din ce ai la tine sau din jur",
                lead = "Dacă nu ai iască dedicată, improvizează cu material foarte fin, uscat și aerat, mai întâi din ce ai la tine, apoi din locuri protejate din jur.",
                keywords = listOf("improvizez iasca", "improvizam din ce am la mine", "nu am iasca", "ce pot folosi ca iasca", "servetele", "hartie", "vata", "scame"),
                userPhrasings = listOf("improvizam din ce am la mine", "nu am iasca", "cum improvizez iasca", "ce pot folosi ca iasca", "am servetele", "am hartie", "am vata", "am scame"),
                slotConstraints = mapOf("tinder_strategy" to "improvise"),
                actionsNow = listOf(
                    "verifică întâi ce ai la tine: șervețele, hârtie, vată, scame sau alte fibre uscate",
                    "dacă nu ai nimic bun la tine, caută în locuri protejate: sub scoarță, în interiorul lemnului rupt sau sub crengi dese",
                    "mărunțește materialul până obții fibre foarte fine și ține-l separat de surcele"
                ),
                avoid = listOf("nu încerca direct pe frunze ude sau bucăți groase de coajă"),
                followUps = listOf("Ai deja ceva uscat la tine care poate funcționa ca iască?"),
                relatedCards = listOf("campfire_prepare_tinder_kindling_fuel", "campfire_tinder_definition", "campfire_wet_fuel")
            ),
            campfireCard(
                id = "campfire_light_with_ferro",
                family = CardFamily.SCENARIO,
                priority = 60,
                title = "Aprindere cu amnar",
                lead = "Cu amnarul, succesul vine din iască bună și scântei direcționate precis, nu din forță brută.",
                keywords = listOf("amnar", "ferro", "aprind cu amnar"),
                userPhrasings = listOf("am amnar", "aprind cu amnar", "cum fac foc cu amnar"),
                slotConstraints = mapOf("ignition_source" to "ferro"),
                actionsNow = listOf("fixează tija aproape de iască", "adaugă surcele foarte fine după ce iasca prinde"),
                followUps = listOf("Ai iască bună sau trebuie să o improvizăm?")
            ),
            campfireCard(
                id = "campfire_wet_fuel",
                family = CardFamily.CONSTRAINT,
                priority = 124,
                title = "Combustibil umed",
                lead = "Dacă totul este ud, nu încerca foc mare. Caută întâi material mai uscat la interior sau material fin protejat.",
                keywords = listOf("totul e ud", "lemn ud", "material ud"),
                userPhrasings = listOf("totul e ud", "e ud", "a plouat si e ud"),
                slotConstraints = mapOf("fuel_condition" to "wet"),
                actionsNow = listOf("rupe lemnul ca să ajungi la interior mai uscat", "pornește doar cu material fin care chiar poate prinde"),
                followUps = listOf("Poți găsi ceva mai uscat la interior sau sub zone protejate?"),
                constraintMode = "override"
            ),
            campfireCard(
                id = "campfire_no_direct_ignition",
                family = CardFamily.CONSTRAINT,
                priority = 122,
                title = "Fără aprindere directă",
                lead = "Dacă nu ai o metodă realistă de aprindere, nu transforma focul într-un obiectiv principal doar din ambiție.",
                keywords = listOf("nu am bricheta", "nu am chibrit", "nu am nimic de aprindere", "fara aprindere"),
                userPhrasings = listOf("n am nimic de aprindere", "nu am bricheta si nici chibrite", "fara aprindere directa"),
                slotConstraints = mapOf("ignition_source" to "none"),
                actionsNow = listOf("verifică întâi dacă poți produce totuși scânteie", "dacă nu, mută efortul spre conservarea căldurii și schimbarea planului"),
                followUps = listOf("Poți produce măcar scânteie în mod controlat?"),
                constraintMode = "override"
            ),
            campfireCard(
                id = "campfire_alternative_to_fire_for_warmth",
                family = CardFamily.SCENARIO,
                priority = 56,
                title = "Alternative când focul nu merită",
                lead = "Dacă nu ai aprindere realistă, câștigi mai mult din conservarea căldurii decât din încercări repetate de foc.",
                keywords = listOf("mai bine fac altceva", "alternativa la foc", "nu merita focul"),
                userPhrasings = listOf("mai bine fac altceva", "nu merita sa insist cu focul", "cum ma incalzesc fara foc"),
                slotConstraints = mapOf("goal" to "warmth", "ignition_source" to "none"),
                actionsNow = listOf("redu pierderea de căldură", "schimbă planul devreme"),
                followUps = listOf("Ai măcar un loc adăpostit de vânt?", "Mai ai lumină și energie să continui?")
            ),
            campfireCard(
                id = "campfire_not_worth_it_for_goal",
                family = CardFamily.CONSTRAINT,
                priority = 114,
                title = "Focul nu mai merită pentru scopul actual",
                lead = "Dacă focul nu este necesar acum și condițiile sunt slabe, de multe ori decizia bună este să nu mai insiști.",
                keywords = listOf("merita sa mai incerc", "mai bine fac altceva", "nu merita focul"),
                userPhrasings = listOf("merita sa mai incerc", "mai bine fac altceva", "nu cred ca merita focul"),
                slotConstraints = mapOf("need_level" to "optional"),
                actionsNow = listOf("închide încercarea devreme", "păstrează resursele pentru ce te ajută imediat"),
                followUps = listOf("Scopul este încă important sau putem schimba planul?"),
                constraintMode = "override"
            )
        )
        val knowledgeStore = TestCampfireStore(cards, packStatus, embeddingStore)

        return AssistantRepository(
            context = null,
            knowledgePackManager = TestPackStatusProvider(packStatus),
            knowledgeStore = knowledgeStore,
            queryAnalyzer = QueryAnalyzer(),
            retrievalEngine = RetrievalEngine(knowledgeStore),
            promptBuilder = PromptBuilder(),
            modelManager = ModelManager(
                modelLocator = FakeLocalModelLocator(LocalModelDiscovery(details = "missing bundle")),
                runtimeAdapter = FakeRuntimeAdapter()
            ),
            slmInterpreterEngine = slmInterpreterEngine,
            groundedWordingEngine = groundedWordingEngine,
            generationEngine = LocalLlmGenerationEngine(
                modelManager = ModelManager(
                    modelLocator = FakeLocalModelLocator(LocalModelDiscovery(details = "missing bundle")),
                    runtimeAdapter = FakeRuntimeAdapter()
                ),
                fallbackEngine = TemplateGenerationEngine()
            ),
            medicalSafetyPolicy = MedicalSafetyPolicy()
        )
    }

    private fun testEmbeddingStore(
        queryPhrase: String,
        cardVectors: Map<String, FloatArray>
    ): CampfireEmbeddingStore {
        val modelName = "test-harness"
        val backendLabel = "manual"
        val dimension = cardVectors.values.firstOrNull()?.size ?: 0
        return CampfireEmbeddingStore(
            topic = "campfire",
            language = "ro",
            cardEmbeddings = cardVectors.mapValues { (cardId, vector) ->
                CampfireCardEmbedding(
                    cardId = cardId,
                    topic = "campfire",
                    language = "ro",
                    queryEmbedding = vector,
                    contentEmbedding = vector,
                    modelName = modelName,
                    backendLabel = backendLabel,
                    dimension = dimension
                )
            },
            phrasingEmbeddings = listOf(
                CampfirePhrasingEmbedding(
                    cardId = "campfire_for_warmth",
                    topic = "campfire",
                    language = "ro",
                    phraseText = queryPhrase,
                    normalizedPhrase = "tin cald cu foc",
                    phraseKind = "user_phrasing",
                    embedding = cardVectors.getValue("campfire_for_warmth"),
                    modelName = modelName,
                    backendLabel = backendLabel,
                    dimension = dimension
                )
            ),
            modelName = modelName,
            backendLabel = backendLabel,
            dimension = dimension
        )
    }

    private fun campfireCard(
        id: String,
        family: CardFamily,
        priority: Int,
        title: String,
        lead: String,
        keywords: List<String> = listOf(title),
        userPhrasings: List<String> = emptyList(),
        slotConstraints: Map<String, String> = emptyMap(),
        actionsNow: List<String> = emptyList(),
        avoid: List<String> = emptyList(),
        followUps: List<String> = emptyList(),
        constraintMode: String = "support",
        relatedCards: List<String> = emptyList(),
        term: String? = null,
        plainLanguageDefinition: String? = null
    ): KnowledgeChunkRecord =
        KnowledgeChunkRecord(
            chunkId = id,
            domain = "field_know_how",
            topic = "campfire",
            language = "ro",
            title = title,
            body = listOf(title, lead, actionsNow.joinToString(" "), avoid.joinToString(" ")).joinToString(" "),
            sourceTitle = "Campfire cards",
            publisher = "Scouty",
            sourceLanguage = "ro",
            adaptedLanguage = "ro",
            sourceTrust = 0,
            packVersion = "campfire-pack",
            keywords = keywords.joinToString(" "),
            cardFamily = family,
            priority = priority,
            metadataJson = metadataJson(
                family = family,
                lead = lead,
                userPhrasings = userPhrasings,
                slotConstraints = slotConstraints,
                actionsNow = actionsNow,
                avoid = avoid,
                followUps = followUps,
                constraintMode = constraintMode,
                relatedCards = relatedCards,
                term = term,
                plainLanguageDefinition = plainLanguageDefinition
            )
        )

    private fun metadataJson(
        family: CardFamily,
        lead: String,
        userPhrasings: List<String>,
        slotConstraints: Map<String, String>,
        actionsNow: List<String>,
        avoid: List<String>,
        followUps: List<String>,
        constraintMode: String,
        relatedCards: List<String>,
        term: String?,
        plainLanguageDefinition: String?
    ): String {
        fun escape(value: String): String =
            value.replace("\\", "\\\\").replace("\"", "\\\"")

        fun stringArray(values: List<String>): String =
            values.joinToString(prefix = "[", postfix = "]") { "\"${escape(it)}\"" }

        fun objectArray(values: List<String>): String =
            values.joinToString(prefix = "[", postfix = "]") { "{\"question\":\"${escape(it)}\"}" }

        fun mapObject(values: Map<String, String>): String =
            values.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
                "\"${escape(key)}\":\"${escape(value)}\""
            }

        val termJson = term?.let { "\"${escape(it)}\"" } ?: "null"
        val definitionJson = plainLanguageDefinition?.let { "\"${escape(it)}\"" } ?: "null"
        return """
            {
              "family":"${family.name.lowercase()}",
              "intent_group":["test"],
              "user_phrasings":${stringArray(userPhrasings)},
              "slot_constraints":${mapObject(slotConstraints)},
              "lead":"${escape(lead)}",
              "actions_now":${stringArray(actionsNow)},
              "avoid":${stringArray(avoid)},
              "watch_for":[],
              "follow_up_questions":${objectArray(followUps)},
              "related_cards":${stringArray(relatedCards)},
              "constraint_mode":"${escape(constraintMode)}",
              "term":$termJson,
              "plain_language_definition":$definitionJson
            }
        """.trimIndent()
    }
}

private class TestCampfireStore(
    private val cards: List<KnowledgeChunkRecord>,
    private val packStatus: KnowledgePackStatus,
    private val embeddingStore: CampfireEmbeddingStore = CampfireEmbeddingStore(topic = "campfire", language = "ro")
) : KnowledgeChunkStore {
    override suspend fun packStatus(): KnowledgePackStatus = packStatus

    override suspend fun searchCandidates(
        query: String,
        preferredLanguages: List<String>,
        domainHints: List<String>,
        limit: Int
    ): List<KnowledgeChunkRecord> =
        cards.filter { it.language in preferredLanguages }.take(limit)

    override suspend fun searchStructuredCards(
        query: String,
        preferredLanguage: String,
        domain: String,
        topic: String,
        family: CardFamily?,
        limit: Int
    ): List<KnowledgeChunkRecord> =
        cards
            .filter { it.domain == domain && it.topic == topic && it.language == preferredLanguage }
            .filter { family == null || it.cardFamily == family }
            .sortedWith(compareByDescending<KnowledgeChunkRecord> { it.priority }.thenBy { it.title })
            .take(limit)

    override suspend fun loadCampfireEmbeddingStore(
        preferredLanguage: String,
        topic: String
    ): CampfireEmbeddingStore =
        if (embeddingStore.language == preferredLanguage && embeddingStore.topic == topic) {
            embeddingStore
        } else {
            CampfireEmbeddingStore(topic = topic, language = preferredLanguage)
        }
}

private class TestPackStatusProvider(
    initialStatus: KnowledgePackStatus
) : KnowledgePackStatusProvider {
    private val internalStatus = MutableStateFlow(initialStatus)

    override val status: StateFlow<KnowledgePackStatus> = internalStatus

    override suspend fun ensureReady(): KnowledgePackStatus = internalStatus.value
}
