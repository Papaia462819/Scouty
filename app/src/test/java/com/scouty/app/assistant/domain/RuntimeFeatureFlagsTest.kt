package com.scouty.app.assistant.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeFeatureFlagsTest {
    @Test
    fun defaultsEnableQwenValidationPath() {
        val flags = RuntimeFeatureFlags()

        assertTrue(flags.useCrossEncoderReranker)
        assertTrue(flags.useLlamaCpp)
        assertTrue(flags.useConversationMemory)
        assertTrue(flags.useLlmSummarizer)
        assertTrue(flags.useQwenDefault)
        assertTrue(flags.useCardParaphraseExpression)
        assertTrue(flags.useGrammarToolCalling)
        assertFalse(flags.useLegacyInterpreter)
    }
}
