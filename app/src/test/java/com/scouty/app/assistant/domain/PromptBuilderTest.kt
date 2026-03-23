package com.scouty.app.assistant.domain

import com.scouty.app.assistant.model.DeviceContextSnapshot
import com.scouty.app.assistant.model.TrailContextSnapshot
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {
    private val builder = PromptBuilder()

    @Test
    fun build_includesTrailAndBatteryContext() {
        val prompt = builder.build(
            query = "Cum pot purifica apa?",
            context = DeviceContextSnapshot(
                batteryPercent = 14,
                batterySafe = true,
                gpsFixed = true,
                latitude = 45.4123,
                longitude = 25.5112,
                trail = TrailContextSnapshot(
                    name = "Bucegi Demo",
                    region = "Bucegi",
                    sunsetTime = "18:42"
                )
            ),
            retrievedChunks = listOf(
                RetrievedChunk(
                    topic = "water",
                    sourceTitle = "Scouty Survival Notes",
                    sectionTitle = "Purificarea apei",
                    body = "Filtrează și fierbe apa.",
                    score = 30
                )
            )
        )

        assertTrue(prompt.contextSummary.contains("Bucegi Demo"))
        assertTrue(prompt.contextSummary.contains("Baterie 14%"))
        assertTrue(prompt.citationsSummary.contains("Purificarea apei"))
    }
}
