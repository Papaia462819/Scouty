package com.scouty.app.assistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scouty.app.assistant.data.ConversationRole
import com.scouty.app.assistant.data.ConversationStore
import com.scouty.app.assistant.domain.memory.ConversationContextAssembler
import com.scouty.app.assistant.domain.memory.SummaryCompactor
import com.scouty.app.assistant.model.AssistantConversationState
import com.scouty.app.assistant.model.DeviceContextSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ConversationStoreInstrumentedTest {
    @Test
    fun turnsAndSummarySurviveStoreRecreation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val conversationId = "android-test:${UUID.randomUUID()}"
        val store = ConversationStore(context)
        try {
            store.appendTurn(conversationId, ConversationRole.USER, "Cum aprind focul cu lemne ude?", null)
            store.appendTurn(conversationId, ConversationRole.ASSISTANT, "Cauta iasca uscata sub scoarta.", "campfire_001")
            store.updateSummary(conversationId, "Utilizatorul a întrebat despre lemne ude.")

            val reopened = ConversationStore(context)
            val turns = reopened.loadRecent(conversationId, maxTurns = 6)

            assertEquals(2, turns.size)
            assertEquals(ConversationRole.USER, turns.first().role)
            assertEquals(ConversationRole.ASSISTANT, turns.last().role)
            assertEquals("campfire_001", turns.last().retrievedChunkId)
            assertNotNull(reopened.loadSummary(conversationId))
        } finally {
            store.deleteConversation(conversationId)
        }
    }

    @Test
    fun compactionKeepsPriorTopicAvailableForRecallContext() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val conversationId = "android-test:${UUID.randomUUID()}"
        val store = ConversationStore(context)
        try {
            repeat(8) { index ->
                store.appendTurn(
                    conversationId,
                    ConversationRole.USER,
                    "Întrebarea $index despre lemne ude și iască uscată după ploaie pe traseu.",
                    null
                )
                store.appendTurn(
                    conversationId,
                    ConversationRole.ASSISTANT,
                    "Am răspuns că trebuie căutată iască uscată sub scoarță și că focul rămâne mic.",
                    "campfire_wet_wood_$index"
                )
            }

            val result = SummaryCompactor(store, budgetTokens = 120).compactIfNeeded(conversationId)
            val summary = store.loadSummary(conversationId).orEmpty()
            val recent = store.loadRecent(conversationId, maxTurns = 20)
            val history = ConversationContextAssembler(store).assemble(
                conversationId = conversationId,
                currentUserQuery = "Ce am vorbit mai devreme despre lemne ude?",
                conversationState = AssistantConversationState(activeTopic = "campfire"),
                deviceContext = DeviceContextSnapshot()
            )

            assertTrue(result.compacted)
            assertEquals(4, recent.size)
            assertTrue(summary.contains("lemne ude", ignoreCase = true))
            assertTrue(history.contextBlock.contains("lemne ude", ignoreCase = true))
            assertTrue(history.historyTokenEstimate > 0)
        } finally {
            store.deleteConversation(conversationId)
        }
    }
}
