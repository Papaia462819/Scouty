package com.scouty.app.assistant

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.scouty.app.MainActivity
import org.junit.Rule
import org.junit.Test

class AssistantChatRuntimeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun campfireQuery_usesLocalLlm_andGroundsOnCampfireChunk() {
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithText("Chat", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Chat", useUnmergedTree = true).performClick()

        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNode(hasSetTextAction(), useUnmergedTree = true)
            .performTextClearance()
        composeRule.onNode(hasSetTextAction(), useUnmergedTree = true)
            .performTextInput("cum fac focul")
        composeRule.onNode(hasContentDescription("Send"), useUnmergedTree = true)
            .performClick()

        composeRule.waitUntil(timeoutMillis = 600_000) {
            val hasAnswer = composeRule.onAllNodesWithText(
                "Verifica intotdeauna daca focul este permis",
                substring = true,
                useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
            val hasSourcesToggle = composeRule.onAllNodesWithText("Surse", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
            hasAnswer && hasSourcesToggle
        }

        check(
            composeRule.onAllNodesWithText(
                "Verifica intotdeauna daca focul este permis",
                substring = true,
                useUnmergedTree = true
            )
                .fetchSemanticsNodes().isNotEmpty()
        ) { "Expected a grounded campfire answer in chat output." }
        check(
            composeRule.onAllNodesWithText("Surse", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        ) { "Expected a collapsed sources toggle in chat output." }
        check(
            composeRule.onAllNodesWithText("LOCAL_LLM", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        ) { "Did not expect runtime footer text in chat output." }
        check(
            composeRule.onAllNodesWithText("MODEL_LOADED", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        ) { "Did not expect model footer text in chat output." }
        check(
            composeRule.onAllNodesWithText("Caution", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        ) { "Did not expect caution banner text in chat output." }
        check(
            composeRule.onAllNodesWithText("Atentie", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        ) { "Did not expect caution label text in chat output." }

        composeRule.onNodeWithText("Surse", useUnmergedTree = true).performClick()

        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(
                "Campfires · Foc de tabara in siguranta",
                useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
        }

        check(
            composeRule.onAllNodesWithText(
                "Campfires · Foc de tabara in siguranta",
                useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
        ) { "Expected campfire source after expanding sources." }
        check(
            composeRule.onAllNodesWithText("Circuitul Galbenei", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        ) { "Did not expect unrelated route sources for the campfire query." }
    }
}
