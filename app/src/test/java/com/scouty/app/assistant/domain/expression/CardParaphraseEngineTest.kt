package com.scouty.app.assistant.domain.expression

import com.scouty.app.assistant.domain.LocalLlmGenerationOptions
import com.scouty.app.assistant.domain.RetrievalConfidenceTier
import com.scouty.app.assistant.domain.RetrievedChunk
import com.scouty.app.assistant.model.DeviceContextSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardParaphraseEngineTest {
    @Test
    fun tierA_skipsWithoutCallingModel() = runBlocking {
        val model = FakeParaphraseModel("nu contează")
        val engine = CardParaphraseEngine(model)

        val result = engine.maybeParaphrase(
            request(tier = "A", confidence = RetrievalConfidenceTier.HIGH)
        )

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
    fun unfaithfulOutput_isRejected() = runBlocking {
        val model = FakeParaphraseModel("Ia o lanternă și pornește spre cabană.")
        val engine = CardParaphraseEngine(model)

        val result = engine.maybeParaphrase(request())

        assertNull(result)
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
        assertEquals(180, model.lastOptions?.sampler?.maxTokens)
        assertEquals(0.4f, model.lastOptions?.sampler?.temperature)
        assertEquals(0.9f, model.lastOptions?.sampler?.topP)
    }

    @Test
    fun keyFactCoverage_usesNumbersCapitalizedWordsAndLeadTokens() {
        val engine = CardParaphraseEngine(FakeParaphraseModel(""))

        val facts = engine.keyFactTokens(
            source = DefaultBody,
            lead = DefaultLead
        )

        assertTrue("expected numeric fact", "2000" in facts)
        assertTrue("expected lead token", "aragazul" in facts)
        assertTrue(
            engine.isFaithful(
                source = DefaultBody,
                lead = DefaultLead,
                response = "Aragazul portabil este mai sigur în zona alpină decât focul deschis la 2000 m."
            )
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
            conversationHistory = null
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
