package com.scouty.app.assistant.domain.tools

import com.scouty.app.assistant.domain.CampfireSlotCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantToolsTest {
    private val parser = ToolCallParser()

    @Test
    fun parser_acceptsEachToolShape() {
        val payloads = listOf(
            """{"tool":"lookup_card","domain":"campfire_basics","slot_filters":{"fuel_condition":"wet"}}""" to AssistantToolName.LOOKUP_CARD,
            """{"tool":"set_gear_packed","item_id":"headlamp","packed":true}""" to AssistantToolName.SET_GEAR_PACKED,
            """{"tool":"check_capability","metric":"duration"}""" to AssistantToolName.CHECK_CAPABILITY,
            """{"tool":"ask_clarification","slot":"ignition_source","options":["lighter","matches","ferro"]}""" to AssistantToolName.ASK_CLARIFICATION,
            """{"tool":"recall_previous","topic":"lemne ude"}""" to AssistantToolName.RECALL_PREVIOUS,
            """{"tool":"respond_directly"}""" to AssistantToolName.RESPOND_DIRECTLY
        )

        payloads.forEach { (payload, expectedTool) ->
            assertEquals(expectedTool, parser.parse(payload)?.tool)
        }
    }

    @Test
    fun parser_rejectsUnknownToolAndInvalidSlotValues() {
        assertEquals(null, parser.parse("""{"tool":"invent_answer"}"""))

        val parsed = parser.parse(
            """{"tool":"lookup_card","domain":"campfire_basics","slot_filters":{"fuel_condition":"lava"}}"""
        )

        assertNotNull(parsed)
        assertTrue(parsed!!.slotFilters.isEmpty())
    }

    @Test
    fun grammar_listsKnownCampfireSlotKeys() {
        CampfireSlotCatalog.allowedValues.keys.forEach { slot ->
            assertTrue("missing $slot", slot in ToolCallGrammar.Text)
        }
    }
}
