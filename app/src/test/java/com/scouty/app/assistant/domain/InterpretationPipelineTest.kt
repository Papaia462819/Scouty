package com.scouty.app.assistant.domain

import com.scouty.app.assistant.model.AssistantConversationState
import com.scouty.app.assistant.model.AssistantOpenQuestion
import com.scouty.app.assistant.model.DeviceContextSnapshot
import com.scouty.app.assistant.model.ModelStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterpretationPipelineTest {
    private val gate = InterpreterGate()
    private val validator = InterpreterOutputValidator()
    private val analyzer = QueryAnalyzer()

    @Test
    fun highConfidenceDeterministicPath_skipsInterpreter() {
        val decision = gate.decide(
            assessment = RetrievalConfidenceAssessment(
                score = 0.82,
                tier = RetrievalConfidenceTier.HIGH,
                top1Strength = 0.8,
                margin = 0.7,
                channelAgreement = 0.8,
                slotCoverage = 0.5,
                continuity = 0.7,
                contradictionPenalty = 0.0
            ),
            preprocessing = DeterministicPreprocessingResult(
                normalizedQuery = normalizeInterpreterText("Cum fac focul?")
            ),
            conversationState = AssistantConversationState()
        )

        assertFalse(decision.shouldInvoke)
    }

    @Test
    fun mediumConfidenceAmbiguousFollowUp_invokesInterpreter() {
        val decision = gate.decide(
            assessment = RetrievalConfidenceAssessment(
                score = 0.56,
                tier = RetrievalConfidenceTier.MEDIUM,
                top1Strength = 0.58,
                margin = 0.2,
                channelAgreement = 0.45,
                slotCoverage = 0.1,
                continuity = 0.3,
                contradictionPenalty = 0.2
            ),
            preprocessing = DeterministicPreprocessingResult(
                normalizedQuery = normalizeInterpreterText("Am una in rucsac"),
                isFollowUpShortAnswer = true
            ),
            conversationState = AssistantConversationState(
                activeTopic = "campfire",
                openQuestion = AssistantOpenQuestion(
                    text = "Ai brichetă, chibrite sau amnar?",
                    targetSlot = "ignition_source",
                    allowedValues = listOf("lighter", "matches", "ferro", "recognized_spark", "none")
                )
            )
        )

        assertTrue(decision.shouldInvoke)
    }

    @Test
    fun validator_rejectsInvalidJson() {
        val request = buildRequest("Am una in rucsac")

        val result = validator.validate(
            request = request,
            execution = InterpreterExecutionResult(
                rawOutput = "not json",
                modelStatus = ModelStatus()
            )
        )

        assertNull(result)
    }

    @Test
    fun validator_rejectsLowConfidenceOutput() {
        val request = buildRequest("Am una in rucsac")

        val result = validator.validate(
            request = request,
            execution = InterpreterExecutionResult(
                rawOutput = """
                    {
                      "standalone_query":"Am o sursă de aprindere în rucsac",
                      "topic_hint":"campfire",
                      "intent":"follow_up",
                      "slot_updates":{"ignition_source":"lighter"},
                      "resolved_open_question":true,
                      "needs_clarification":false,
                      "clarification_target":null,
                      "confidence":0.31
                    }
                """.trimIndent(),
                modelStatus = ModelStatus()
            )
        )

        assertNull(result)
    }

    @Test
    fun rewriteAcceptance_requiresConfidenceGain() {
        val accepted = RetrievalConfidencePolicy().shouldAcceptRewrite(
            before = RetrievalConfidenceAssessment(
                score = 0.41,
                tier = RetrievalConfidenceTier.LOW,
                top1Strength = 0.42,
                margin = 0.12,
                channelAgreement = 0.35,
                slotCoverage = 0.5,
                continuity = 0.4,
                contradictionPenalty = 0.18
            ),
            after = RetrievalConfidenceAssessment(
                score = 0.63,
                tier = RetrievalConfidenceTier.MEDIUM,
                top1Strength = 0.68,
                margin = 0.38,
                channelAgreement = 0.57,
                slotCoverage = 0.5,
                continuity = 0.5,
                contradictionPenalty = 0.0
            ),
            interpretation = ValidatedInterpretation(
                standaloneQuery = "Sunt ursi pe traseul activ?",
                confidence = 0.84
            )
        )

        val rejected = RetrievalConfidencePolicy().shouldAcceptRewrite(
            before = RetrievalConfidenceAssessment(
                score = 0.58,
                tier = RetrievalConfidenceTier.MEDIUM,
                top1Strength = 0.61,
                margin = 0.33,
                channelAgreement = 0.5,
                slotCoverage = 0.5,
                continuity = 0.4,
                contradictionPenalty = 0.0
            ),
            after = RetrievalConfidenceAssessment(
                score = 0.6,
                tier = RetrievalConfidenceTier.MEDIUM,
                top1Strength = 0.62,
                margin = 0.31,
                channelAgreement = 0.5,
                slotCoverage = 0.5,
                continuity = 0.42,
                contradictionPenalty = 0.0
            ),
            interpretation = ValidatedInterpretation(
                standaloneQuery = "Cum merg pe stâncă udă?",
                confidence = 0.8
            )
        )

        assertTrue(accepted)
        assertFalse(rejected)
    }

    @Test
    fun pronounRewrite_buildsStandaloneTrailQueryForRetrieval() {
        val analysis = analyzer.analyze(
            query = "Sunt ursi pe el?",
            context = DeviceContextSnapshot(
                localeTag = "ro",
                trail = com.scouty.app.assistant.model.TrailContextSnapshot(
                    name = "Sinaia - Cabana Padina"
                )
            ),
            conversationState = AssistantConversationState(
                lastUserMessage = "Cat de lung e traseul activ?",
                lastRetrievedTitle = "Sinaia - Cabana Padina"
            )
        )
        val preprocessing = DeterministicAssistantPreprocessor().preprocess(
            query = "Sunt ursi pe el?",
            conversationState = AssistantConversationState(
                lastUserMessage = "Cat de lung e traseul activ?",
                lastRetrievedTitle = "Sinaia - Cabana Padina"
            ),
            queryAnalysis = analysis
        )

        val plan = GroundedQueryBuilder().build(
            originalQuery = "Sunt ursi pe el?",
            interpretation = ValidatedInterpretation(
                standaloneQuery = "Sunt ursi pe traseul activ?",
                confidence = 0.89
            ),
            preprocessing = preprocessing
        )

        assertTrue(preprocessing.hasPronounReference)
        assertEquals("Sunt ursi pe traseul activ?", plan.retrievalQuery)
    }

    private fun buildRequest(query: String): InterpreterRequest =
        InterpreterRequest(
            query = query,
            preferredLanguage = "ro",
            queryAnalysis = analyzer.analyze(query, DeviceContextSnapshot(localeTag = "ro")),
            conversationState = AssistantConversationState(
                activeTopic = "campfire",
                openQuestion = AssistantOpenQuestion(
                    text = "Ai brichetă, chibrite sau amnar?",
                    targetSlot = "ignition_source",
                    allowedValues = listOf("lighter", "matches", "ferro", "recognized_spark", "none")
                )
            ),
            retrievalConfidence = RetrievalConfidenceAssessment(
                score = 0.5,
                tier = RetrievalConfidenceTier.MEDIUM,
                top1Strength = 0.5,
                margin = 0.2,
                channelAgreement = 0.4,
                slotCoverage = 0.1,
                continuity = 0.3,
                contradictionPenalty = 0.1
            ),
            preprocessing = DeterministicPreprocessingResult(
                normalizedQuery = normalizeInterpreterText(query),
                isFollowUpShortAnswer = true
            )
        )
}
