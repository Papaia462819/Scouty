package com.scouty.app.assistant.domain

import com.scouty.app.assistant.data.KnowledgeChunkStore
import com.scouty.app.assistant.model.AssistantConversationState
import com.scouty.app.assistant.model.KnowledgeChunkRecord
import com.scouty.app.assistant.model.KnowledgePackStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicQueryResolverTest {
    private val store = object : KnowledgeChunkStore {
        override suspend fun packStatus(): KnowledgePackStatus = KnowledgePackStatus()
        override suspend fun searchCandidates(
            query: String,
            preferredLanguages: List<String>,
            domainHints: List<String>,
            limit: Int
        ): List<KnowledgeChunkRecord> = emptyList()
        override suspend fun loadSearchVocabulary(): Map<String, Long> = mapOf(
            "focul" to 50L, "foc" to 40L, "bricheta" to 21L, "amnar" to 6L,
            "lemne" to 18L, "vant" to 14L, "urs" to 9L, "apa" to 30L
        )
    }
    private val resolver = DeterministicQueryResolver(store)

    private fun preprocessing(
        fragmented: Boolean = false,
        followUpShort: Boolean = false,
        pronoun: Boolean = false
    ) = DeterministicPreprocessingResult(
        normalizedQuery = "",
        isFragmented = fragmented,
        isFollowUpShortAnswer = followUpShort,
        hasPronounReference = pronoun
    )

    @Test
    fun typoCorrectedOnFreshQuery() = runBlocking {
        val result = resolver.resolve(
            rawQuery = "cum aprind focul cu amnnar",
            conversationState = AssistantConversationState(),
            preprocessing = preprocessing()
        )
        assertTrue(result.wasTypoCorrected)
        assertTrue(result.query.contains("amnar"))
        assertFalse(result.wasFollowUp)
    }

    @Test
    fun ellipticalFollowUpConcatenatesPreviousQuery() = runBlocking {
        val state = AssistantConversationState(lastStandaloneQuery = "cum aprind focul fara bricheta")
        val result = resolver.resolve(
            rawQuery = "si daca ploua?",
            conversationState = state,
            preprocessing = preprocessing(fragmented = true)
        )
        assertTrue(result.wasFollowUp)
        assertTrue(result.query.startsWith("cum aprind focul fara bricheta"))
        assertTrue(result.query.contains("ploua"))
    }

    @Test
    fun newSubjectIsNotConcatenated() = runBlocking {
        val state = AssistantConversationState(lastStandaloneQuery = "cum aprind focul fara bricheta")
        val result = resolver.resolve(
            rawQuery = "si daca vad un urs",
            conversationState = state,
            preprocessing = preprocessing(fragmented = true, pronoun = true)
        )
        assertFalse("a message bringing its own subject (urs) must not be glued to fire", result.wasFollowUp)
        assertFalse(result.query.contains("focul"))
        assertTrue(result.query.contains("urs"))
    }

    @Test
    fun noFollowUpWithoutPriorContext() = runBlocking {
        val result = resolver.resolve(
            rawQuery = "si daca ploua?",
            conversationState = AssistantConversationState(),
            preprocessing = preprocessing(fragmented = true)
        )
        assertFalse(result.wasFollowUp)
    }

    @Test
    fun fullStandaloneQuestionIsNotConcatenated() = runBlocking {
        val state = AssistantConversationState(lastStandaloneQuery = "cum aprind focul fara bricheta")
        val result = resolver.resolve(
            rawQuery = "cat de greu e traseul",
            conversationState = state,
            preprocessing = preprocessing() // not fragmented, no pronoun
        )
        assertFalse(result.wasFollowUp)
    }
}
