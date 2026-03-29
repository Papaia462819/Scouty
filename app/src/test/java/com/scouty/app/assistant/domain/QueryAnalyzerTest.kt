package com.scouty.app.assistant.domain

import com.scouty.app.assistant.model.DeviceContextSnapshot
import org.junit.Assert.assertEquals
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
}
