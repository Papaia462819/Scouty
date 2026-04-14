package com.scouty.app.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileAssessmentEngineTest {

    @Test
    fun noviceAnswersStayAtJunior() {
        val result = ProfileAssessmentEngine.evaluate(
            mapOf(
                "hike_frequency" to "rarely",
                "max_distance" to "under_5",
                "physical_condition" to "restart",
                "navigation" to "marked_only",
                "terrain" to "forest_road",
                "conditions" to "perfect",
                "gear_setup" to "improvise",
                "hike_style" to "scenic",
                "age_range" to "25_34",
                "first_aid" to "none"
            )
        )

        assertEquals(ScoutyLevel.LEVEL_1, result.starterLevel)
        assertTrue(result.score < 38)
    }

    @Test
    fun balancedAnswersReachBootCamper() {
        val result = ProfileAssessmentEngine.evaluate(
            mapOf(
                "hike_frequency" to "monthly",
                "max_distance" to "10_15",
                "physical_condition" to "solid",
                "navigation" to "gps_ok",
                "terrain" to "standard",
                "conditions" to "cool",
                "gear_setup" to "checklist",
                "hike_style" to "classic_day",
                "age_range" to "35_44",
                "first_aid" to "few_basics"
            )
        )

        assertEquals(ScoutyLevel.LEVEL_2, result.starterLevel)
        assertTrue(result.score in 38..69)
    }

    @Test
    fun topAnswersAreCappedAtTrailExplorer() {
        val result = ProfileAssessmentEngine.evaluate(
            mapOf(
                "hike_frequency" to "constant",
                "max_distance" to "20_plus",
                "physical_condition" to "endurance",
                "navigation" to "independent",
                "terrain" to "ridge",
                "conditions" to "winter",
                "gear_setup" to "locked_in",
                "hike_style" to "adventure",
                "age_range" to "18_24",
                "first_aid" to "confident"
            )
        )

        assertEquals(ScoutyLevel.LEVEL_3, result.starterLevel)
        assertEquals("Trail Explorer", result.starterLevel.title)
        assertTrue(result.score >= 70)
    }
}
