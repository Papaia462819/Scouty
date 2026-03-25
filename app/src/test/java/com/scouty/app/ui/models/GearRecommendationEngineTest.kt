package com.scouty.app.ui.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class GearRecommendationEngineTest {
    @Test
    fun build_defaultChecklistContainsLeanCoreItems() {
        val gear = GearRecommendationEngine.build(trail = null)

        assertTrue(gear.any { it.id == "offline_map" && it.necessity == GearNecessity.MANDATORY })
        assertTrue(gear.any { it.id == "water" && it.necessity == GearNecessity.MANDATORY })
        assertTrue(gear.any { it.id == "first_aid" && it.necessity == GearNecessity.MANDATORY })
        assertFalse(gear.any { it.id == "paper_map" })
        assertFalse(gear.any { it.id == "via_ferrata" })
    }

    @Test
    fun build_hardTrailPromotesWaterTreatmentBivyAndPoles() {
        val trail = buildTrail(
            difficulty = "HARD",
            distanceKm = 15.2,
            elevationGain = 980,
            estimatedDuration = "~7h 10m"
        )

        val gear = GearRecommendationEngine.build(trail)

        assertEquals("2.6 L min", gear.first { it.id == "water" }.weight)
        assertEquals(GearNecessity.RECOMMENDED, gear.first { it.id == "trekking_poles" }.necessity)
        assertEquals(GearNecessity.RECOMMENDED, gear.first { it.id == "water_treatment" }.necessity)
        assertEquals(GearNecessity.MANDATORY, gear.first { it.id == "emergency_bivy" }.necessity)
    }

    @Test
    fun build_easyShortTrailKeepsListCompact() {
        val trail = buildTrail(
            difficulty = "EASY",
            distanceKm = 4.4,
            elevationGain = 220,
            estimatedDuration = "~2h 30m"
        )

        val gear = GearRecommendationEngine.build(trail)

        assertFalse(gear.any { it.id == "water_treatment" })
        assertFalse(gear.any { it.id == "trekking_poles" })
        assertEquals(GearNecessity.RECOMMENDED, gear.first { it.id == "headlamp" }.necessity)
    }

    @Test
    fun build_preservesPackedStateForMatchingItems() {
        val previous = listOf(
            GearItem(
                id = "offline_map",
                name = "Telefon + harta offline",
                weight = "0 g",
                category = "Planificare & navigatie",
                weightGrams = 0,
                necessity = GearNecessity.MANDATORY,
                isPacked = true
            )
        )

        val gear = GearRecommendationEngine.build(buildTrail(), previousItems = previous)

        val offlineMap = gear.firstOrNull { it.id == "offline_map" }
        assertNotNull(offlineMap)
        assertTrue(offlineMap!!.isPacked)
    }

    private fun buildTrail(
        difficulty: String = "MEDIUM",
        distanceKm: Double = 11.4,
        elevationGain: Int = 620,
        estimatedDuration: String = "~5h 20m"
    ): ActiveTrail =
        ActiveTrail(
            name = "Test trail",
            date = Calendar.getInstance(),
            latitude = 45.4,
            longitude = 25.4,
            difficulty = difficulty,
            distanceKm = distanceKm,
            elevationGain = elevationGain,
            estimatedDuration = estimatedDuration
        )
}
