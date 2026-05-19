package com.scouty.app.assistant.domain.expression

import com.scouty.app.assistant.domain.LocalLlmGenerationOptions
import com.scouty.app.assistant.domain.RetrievalConfidenceTier
import com.scouty.app.assistant.domain.RetrievedChunk
import com.scouty.app.assistant.model.DeviceContextSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardParaphraseEngineTest {
    @Test
    fun tierA_invokesModelAndReturnsRephrase() = runBlocking {
        val model = FakeParaphraseModel(
            "Aragazul portabil este mai sigur în zona alpină decât focul deschis la 2000 m."
        )
        val engine = CardParaphraseEngine(model)

        val result = engine.maybeParaphrase(
            request(tier = "A", confidence = RetrievalConfidenceTier.HIGH)
        )

        assertNotNull(result)
        assertEquals(model.response, result?.text)
        assertEquals(1, model.calls)
    }

    @Test
    fun tierB_invokesModel() = runBlocking {
        val model = FakeParaphraseModel(
            "Aragazul portabil este mai sigur în zona alpină decât focul deschis la 2000 m."
        )
        val engine = CardParaphraseEngine(model)

        val result = engine.maybeParaphrase(request(tier = "B"))

        assertNotNull(result)
        assertEquals(1, model.calls)
    }

    @Test
    fun missingTier_skipsWithoutCallingModel() = runBlocking {
        val model = FakeParaphraseModel("nu contează")
        val engine = CardParaphraseEngine(model)

        val result = engine.maybeParaphrase(requestWithoutTier())

        assertNull(result)
        assertEquals(0, model.calls)
    }

    @Test
    fun lowConfidence_skipsWithoutCallingModel() = runBlocking {
        val model = FakeParaphraseModel("nu contează")
        val engine = CardParaphraseEngine(model)

        val result = engine.maybeParaphrase(
            request(tier = "B", confidence = RetrievalConfidenceTier.LOW)
        )

        assertNull(result)
        assertEquals(0, model.calls)
    }

    @Test
    fun emptyOutput_isRejected() = runBlocking {
        val model = FakeParaphraseModel("   ")
        val engine = CardParaphraseEngine(model)

        val result = engine.maybeParaphrase(request())

        assertNull(result)
        assertEquals(1, model.calls)
    }

    @Test
    fun outputWithUnknownNumber_isRejected() = runBlocking {
        // Source mentions 2000 m. Model invents 1500 m → must be rejected.
        val model = FakeParaphraseModel(
            "Aragazul portabil este mai sigur în zona alpină decât focul deschis la 1500 m."
        )
        val engine = CardParaphraseEngine(model)

        val result = engine.maybeParaphrase(request())

        assertNull(result)
        assertEquals(1, model.calls)
    }

    @Test
    fun outputWithUnknownProperNoun_isRejected() = runBlocking {
        // Model invents a proper noun ("Bucegi") not in source/lead/query/context.
        val model = FakeParaphraseModel(
            "Folosește aragazul portabil în masivul Bucegi pentru gătit sigur."
        )
        val engine = CardParaphraseEngine(model)

        val result = engine.maybeParaphrase(request())

        assertNull(result)
        assertEquals(1, model.calls)
    }

    @Test
    fun paraphraseDroppingMostSourceWords_isAccepted() = runBlocking {
        // Old coverage rule would reject this; new rule accepts because no
        // new numbers or proper nouns are introduced.
        val model = FakeParaphraseModel("Mai bine cu aragaz decât cu foc deschis sus.")
        val engine = CardParaphraseEngine(model)

        val result = engine.maybeParaphrase(request())

        assertNotNull(result)
        assertEquals(1, model.calls)
    }

    @Test
    fun faithfulOutput_passesThroughWithSampler() = runBlocking {
        val model = FakeParaphraseModel(
            "În zona alpină, folosește aragazul portabil: este mai sigur decât focul deschis la 2000 m."
        )
        val engine = CardParaphraseEngine(model)

        val result = engine.maybeParaphrase(request())

        assertEquals(model.response, result?.text)
        assertEquals(1, model.calls)
        assertEquals(110, model.lastOptions?.sampler?.maxTokens)
        assertEquals(0.25f, model.lastOptions?.sampler?.temperature)
        assertEquals(0.9f, model.lastOptions?.sampler?.topP)
    }

    @Test
    fun cacheHit_avoidsSecondModelCall() = runBlocking {
        val model = FakeParaphraseModel(
            "Aragazul portabil este mai sigur în zona alpină decât focul deschis la 2000 m."
        )
        val engine = CardParaphraseEngine(model)

        val first = engine.maybeParaphrase(request())
        val second = engine.maybeParaphrase(request())

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(first?.text, second?.text)
        assertEquals(1, model.calls)
        assertEquals(0L, second?.latencyMs)
    }

    @Test
    fun introducesUnknownFacts_detectsHallucinatedNumber() {
        val engine = CardParaphraseEngine(FakeParaphraseModel(""))
        val sources = listOf(DefaultBody, DefaultLead, "", "")

        assertTrue(
            engine.introducesUnknownFacts(sources, "Folosește aragazul la 3500 m altitudine.")
        )
        assertFalse(
            engine.introducesUnknownFacts(sources, "Folosește aragazul la 2000 m altitudine.")
        )
    }

    @Test
    fun introducesUnknownFacts_ignoresSentenceInitialCapitals() {
        val engine = CardParaphraseEngine(FakeParaphraseModel(""))
        val sources = listOf(DefaultBody, DefaultLead, "", "")

        // "Aragazul" appears at sentence start in response — must NOT be
        // treated as an unknown proper noun even if absent from source.
        assertFalse(
            engine.introducesUnknownFacts(sources, "Aragazul ajută la gătit sigur.")
        )
    }

    private fun request(
        tier: String = "B",
        confidence: RetrievalConfidenceTier = RetrievalConfidenceTier.HIGH
    ): CardParaphraseRequest =
        CardParaphraseRequest(
            featureEnabled = true,
            chunk = RetrievedChunk(
                topic = "fire_alpine_stove_alt",
                sourceTitle = "Scouty",
                sectionTitle = "Aragaz în zona alpină",
                body = DefaultBody,
                score = 85,
                chunkId = "cg-campfire",
                domain = "campfire_basics",
                metadataJson = """{"tier":"$tier","tone":"conversational","lead":"$DefaultLead"}"""
            ),
            userQuery = "Pot face foc în golul alpin?",
            confidenceTier = confidence,
            deviceContext = DeviceContextSnapshot(localeTag = "ro"),
            conversationHistory = null,
            preferredLanguage = "ro"
        )

    private fun requestWithoutTier(): CardParaphraseRequest =
        CardParaphraseRequest(
            featureEnabled = true,
            chunk = RetrievedChunk(
                topic = "fire_alpine_stove_alt",
                sourceTitle = "Scouty",
                sectionTitle = "Aragaz în zona alpină",
                body = DefaultBody,
                score = 85,
                chunkId = "cg-campfire",
                domain = "campfire_basics",
                metadataJson = """{"tone":"conversational"}"""
            ),
            userQuery = "Pot face foc în golul alpin?",
            confidenceTier = RetrievalConfidenceTier.HIGH,
            deviceContext = DeviceContextSnapshot(localeTag = "ro"),
            conversationHistory = null,
            preferredLanguage = "ro"
        )

    private class FakeParaphraseModel(
        val response: String
    ) : CardParaphraseModel {
        var calls = 0
        var lastOptions: LocalLlmGenerationOptions? = null

        override suspend fun generate(prompt: String, options: LocalLlmGenerationOptions): String {
            calls += 1
            lastOptions = options
            return response
        }
    }

    private companion object {
        private const val DefaultLead =
            "Aragazul portabil este mai sigur în zona alpină decât focul deschis."
        private const val DefaultBody =
            "Aragazul portabil în zona alpină gătește mai sigur decât focul deschis la 2000 m."
    }
}
