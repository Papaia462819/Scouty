package com.scouty.app.assistant.domain

import com.scouty.app.assistant.model.AssistantConversationState
import com.scouty.app.assistant.model.CardFamily
import com.scouty.app.assistant.model.ConversationLane
import com.scouty.app.assistant.model.DeviceContextSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryAnalyzerTest {
    private val analyzer = QueryAnalyzer()

    @Test
    fun romanianQueryWithTypos_staysRomanian() {
        val analysis = analyzer.analyze(
            query = "Am facut o entorsa, cuim sa procedez?",
            context = DeviceContextSnapshot(localeTag = "ro-RO")
        )

        assertEquals("ro", analysis.preferredLanguage)
    }

    @Test
    fun englishQuery_staysEnglish() {
        val analysis = analyzer.analyze(
            query = "I twisted my ankle, what should I do?",
            context = DeviceContextSnapshot(localeTag = "en-US")
        )

        assertEquals("en", analysis.preferredLanguage)
    }

    @Test
    fun campfireQuery_usesStandardLaneByDefault() {
        val analysis = analyzer.analyze(
            query = "Cum fac focul?",
            context = DeviceContextSnapshot(localeTag = "ro-RO")
        )

        assertEquals(ConversationLane.STANDARD, analysis.knowledgeLane)
        assertEquals(null, analysis.resolvedTopic)
        assertEquals(null, analysis.targetFamily)
        assertEquals("campfire_basics", analysis.domainHints.first().domain)
        assertEquals("ro", analysis.preferredLanguage)
    }

    @Test
    fun campfireQuery_routesToKnowHowScenarioLaneWhenFlagEnabled() {
        val analysis = QueryAnalyzer(useCampfireLane = true).analyze(
            query = "Cum fac focul?",
            context = DeviceContextSnapshot(localeTag = "ro-RO")
        )

        assertEquals(ConversationLane.FIELD_KNOW_HOW, analysis.knowledgeLane)
        assertEquals("campfire", analysis.resolvedTopic)
        assertEquals(CardFamily.SCENARIO, analysis.targetFamily)
        assertEquals("ro", analysis.preferredLanguage)
    }

    @Test
    fun campfireDefinitionDetour_routesToDefinitionFamily() {
        val analysis = QueryAnalyzer(useCampfireLane = true).analyze(
            query = "Ce e tinder?",
            context = DeviceContextSnapshot(localeTag = "ro-RO"),
            conversationState = AssistantConversationState(activeTopic = "campfire")
        )

        assertEquals(ConversationLane.FIELD_KNOW_HOW, analysis.knowledgeLane)
        assertEquals(CardFamily.DEFINITION, analysis.targetFamily)
        assertTrue(analysis.isFollowUp)
    }

    @Test
    fun shortWetFollowUp_onActiveCampfireRoutesToConstraint() {
        val analysis = QueryAnalyzer(useCampfireLane = true).analyze(
            query = "Totul e ud",
            context = DeviceContextSnapshot(localeTag = "ro-RO"),
            conversationState = AssistantConversationState(activeTopic = "campfire")
        )

        assertEquals(ConversationLane.FIELD_KNOW_HOW, analysis.knowledgeLane)
        assertEquals(CardFamily.CONSTRAINT, analysis.targetFamily)
        assertTrue(analysis.isFollowUp)
    }

    @Test
    fun genericWarmthQuestion_onActiveCampfireCanAbstainFromCampfire() {
        val analysis = QueryAnalyzer(useCampfireLane = true).analyze(
            query = "cum tin de cald",
            context = DeviceContextSnapshot(localeTag = "ro-RO"),
            conversationState = AssistantConversationState(activeTopic = "campfire")
        )

        assertEquals(ConversationLane.STANDARD, analysis.knowledgeLane)
        assertEquals(null, analysis.resolvedTopic)
    }
}
