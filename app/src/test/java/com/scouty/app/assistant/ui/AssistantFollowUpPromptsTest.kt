package com.scouty.app.assistant.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantFollowUpPromptsTest {
    @Test
    fun buildSequentialFollowUpPrompt_keepsQuestionAndExtractsColonReplies() {
        val prompt = buildSequentialFollowUpPrompt(
            listOf("Pentru ce îți trebuie: căldură, gătit sau fiert apă?")
        )

        requireNotNull(prompt)
        assertEquals("Pentru ce îți trebuie: căldură, gătit sau fiert apă?", prompt.question)
        assertEquals(
            listOf(
                "Căldură" to "Căldură",
                "Gătit" to "Gătit",
                "Fiert apă" to "Fiert apă"
            ),
            prompt.suggestedReplies.map { it.label to it.query }
        )
    }

    @Test
    fun extractSuggestedReplies_handlesAiQuestion() {
        val replies = extractSuggestedReplies("Ai brichetă, chibrite sau amnar?")

        assertEquals(
            listOf(
                "Brichetă" to "Am brichetă",
                "Chibrite" to "Am chibrite",
                "Amnar" to "Am amnar"
            ),
            replies.map { it.label to it.query }
        )
    }

    @Test
    fun extractSuggestedReplies_transposesImprovisationReplyToFirstPersonQuery() {
        val replies = extractSuggestedReplies("Ai iască sau improvizăm din ce ai la tine?")

        assertEquals(
            listOf(
                "Iască" to "Am iască",
                "Improvizăm din ce ai la tine" to "Improvizăm din ce am la mine"
            ),
            replies.map { it.label to it.query }
        )
    }

    @Test
    fun extractSuggestedReplies_returnsEmptyForOpenEndedQuestion() {
        val replies = extractSuggestedReplies("Poți găsi ceva mai uscat la interior sau sub zone protejate?")

        assertTrue(replies.isEmpty())
    }

}
