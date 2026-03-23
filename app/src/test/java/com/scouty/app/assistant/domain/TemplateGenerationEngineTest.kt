package com.scouty.app.assistant.domain

import com.scouty.app.assistant.model.DeviceContextSnapshot
import com.scouty.app.assistant.model.SafetyOutcome
import com.scouty.app.assistant.model.TrailContextSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateGenerationEngineTest {
    private val engine = TemplateGenerationEngine()

    private fun makeInput(
        query: String = "test",
        chunks: List<RetrievedChunk> = listOf(defaultChunk()),
        safety: SafetyOutcome = SafetyOutcome.NORMAL,
        localeTag: String = "ro",
        batterySafe: Boolean = false,
        sunsetTime: String? = null
    ) = GenerationInput(
        query = query,
        prompt = AssistantPrompt(query, "context", "citations"),
        retrievedChunks = chunks,
        context = DeviceContextSnapshot(
            batteryPercent = 80,
            batterySafe = batterySafe,
            localeTag = localeTag,
            trail = sunsetTime?.let {
                TrailContextSnapshot(name = "Test Trail", sunsetTime = it)
            }
        ),
        safetyOutcome = safety
    )

    private fun defaultChunk(body: String = "Aplică presiune directă pe rană și menține 10 minute.") =
        RetrievedChunk(
            topic = "bleeding",
            sourceTitle = "Scouty First Aid Field Notes",
            sectionTitle = "Control sângerare",
            body = body,
            score = 30
        )

    @Test
    fun normalResponse_containsGuidelines_ro() = runBlocking {
        val result = engine.generate(makeInput(localeTag = "ro"))
        assertTrue(result.contains("ghidurilor offline"))
        assertTrue(result.contains("Aplică presiune directă"))
    }

    @Test
    fun normalResponse_containsGuidelines_en() = runBlocking {
        val result = engine.generate(makeInput(localeTag = "en"))
        assertTrue(result.contains("offline guidelines"))
    }

    @Test
    fun emergencyResponse_contains112_ro() = runBlocking {
        val result = engine.generate(makeInput(safety = SafetyOutcome.EMERGENCY_ESCALATION, localeTag = "ro"))
        assertTrue(result.contains("Sună 112 imediat"))
        assertTrue(result.contains("semne de alarmă"))
    }

    @Test
    fun emergencyResponse_contains112_en() = runBlocking {
        val result = engine.generate(makeInput(safety = SafetyOutcome.EMERGENCY_ESCALATION, localeTag = "en"))
        assertTrue(result.contains("Call 112 immediately"))
        assertTrue(result.contains("warning signs"))
    }

    @Test
    fun cautionResponse_hasSafetyIntro_ro() = runBlocking {
        val result = engine.generate(makeInput(safety = SafetyOutcome.CAUTION, localeTag = "ro"))
        assertTrue(result.contains("prudent"))
    }

    @Test
    fun cautionResponse_hasSafetyIntro_en() = runBlocking {
        val result = engine.generate(makeInput(safety = SafetyOutcome.CAUTION, localeTag = "en"))
        assertTrue(result.contains("cautious"))
    }

    @Test
    fun multipleChunks_showsAdditionally() = runBlocking {
        val chunks = listOf(
            defaultChunk("Primul pas este presiunea directă."),
            defaultChunk("Ridică membrul afectat deasupra inimii.")
        )
        val result = engine.generate(makeInput(chunks = chunks, localeTag = "ro"))
        assertTrue(result.contains("De asemenea:"))
        assertTrue(result.contains("Ridică membrul"))
    }

    @Test
    fun threeChunks_showsNote() = runBlocking {
        val chunks = listOf(
            defaultChunk("Primul pas."),
            defaultChunk("Al doilea pas."),
            defaultChunk("Al treilea pas cu informații suplimentare.")
        )
        val result = engine.generate(makeInput(chunks = chunks, localeTag = "ro"))
        assertTrue(result.contains("Notă:"))
    }

    @Test
    fun batterySafe_showsWarning_ro() = runBlocking {
        val result = engine.generate(makeInput(batterySafe = true, localeTag = "ro"))
        assertTrue(result.contains("Battery Safe"))
    }

    @Test
    fun batterySafe_showsWarning_en() = runBlocking {
        val result = engine.generate(makeInput(batterySafe = true, localeTag = "en"))
        assertTrue(result.contains("Battery Safe"))
    }

    @Test
    fun sunset_showsTime_ro() = runBlocking {
        val result = engine.generate(makeInput(sunsetTime = "18:42", localeTag = "ro"))
        assertTrue(result.contains("18:42"))
        assertTrue(result.contains("apusul"))
    }

    @Test
    fun sunset_showsTime_en() = runBlocking {
        val result = engine.generate(makeInput(sunsetTime = "18:42", localeTag = "en"))
        assertTrue(result.contains("18:42"))
        assertTrue(result.contains("sunset"))
    }

    @Test
    fun emptyChunks_stillHasSafetyIntro() = runBlocking {
        val result = engine.generate(makeInput(chunks = emptyList()))
        assertFalse(result.isBlank())
    }

    @Test
    fun chunkBodyIsPreservedCoherently() = runBlocking {
        val longBody = "Hipotermia apare când temperatura centrală a corpului scade sub 35°C. " +
                "Semnele progresive sunt: frison intens, confuzie, vorbire neclară și somnolență."
        val result = engine.generate(makeInput(chunks = listOf(defaultChunk(longBody))))
        assertTrue("Chunk body should be intact, not split into steps", result.contains("Semnele progresive sunt"))
        assertTrue(result.contains("35°C"))
    }
}
