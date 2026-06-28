package com.scouty.app.assistant.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeFeatureFlagsTest {
    @Test
    fun defaultsUseDeterministicOfflineAssistantPath() {
        val flags = RuntimeFeatureFlags()

        assertTrue(flags.useCrossEncoderReranker)
        assertTrue(flags.useGeneralPathReranker)
        assertFalse(flags.useCampfireLane)
        assertTrue(flags.useConversationMemory)
        assertTrue(flags.useLlmSummarizer)
        assertTrue(flags.useGeminiApi)
        assertFalse(flags.useCardParaphraseExpression)
        assertFalse(flags.useGroundedWording)
        assertFalse(flags.useGrammarToolCalling)
        assertFalse(flags.useLegacyInterpreter)
    }
}
