package com.scouty.app.assistant.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeFeatureFlagsTest {
    @Test
    fun defaultsEnableFastQwenRuntimePath() {
        val flags = RuntimeFeatureFlags()

        assertTrue(flags.useCrossEncoderReranker)
        assertTrue(flags.useLlamaCpp)
        assertTrue(flags.useConversationMemory)
        assertTrue(flags.useLlmSummarizer)
        assertTrue(flags.useQwenDefault)
        assertFalse(flags.useCardParaphraseExpression)
        assertFalse(flags.useGroundedWording)
        assertFalse(flags.useGrammarToolCalling)
        assertFalse(flags.useLegacyInterpreter)
    }
}
