package com.scouty.app.ui.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class GearRecommendationEngineTest {
    @Test
    fun build_defaultChecklistContainsCoreItemsOnly() {
        val gear = GearRecommendationEngine.build(trail = null)

        assertTrue(gear.any { it.id == "offline_map" && it.necessity == GearNecessity.MANDATORY })
        assertTrue(gear.any { it.id == "compass" && it.necessity == GearNecessity.MANDATORY })
        assertTrue(gear.any { it.id == "first_aid" && it.necessity == GearNecessity.MANDATORY })
        assertFalse(gear.any { it.id == "via_ferrata" })
    }

    @Test
    fun build_hardTrailPromotesWaterHelmetAndPaperMap() {
        val trail = buildTrail(
            difficulty = "HARD",
            distanceKm = 15.2,
            elevationGain = 980,
            estimatedDuration = "~7h 10m"
        )

        val gear = GearRecommendationEngine.build(trail)

        assertEquals("2.5 L min", gear.first { it.id == "water" }.weight)
        assertEquals(GearNecessity.RECOMMENDED, gear.first { it.id == "helmet" }.necessity)
        assertEquals(GearNecessity.MANDATORY, gear.first { it.id == "paper_map" }.necessity)
    }

    @Test
    fun build_expertTrailAddsTechnicalContingencyItems() {
        val trail = buildTrail(
            difficulty = "EXPERT",
            distanceKm = 18.5,
            elevationGain = 1550,
            estimatedDuration = "~9h"
        )

        val gear = GearRecommendationEngine.build(trail)

        assertEquals(GearNecessity.MANDATORY, gear.first { it.id == "helmet" }.necessity)
        assertEquals(GearNecessity.CONDITIONAL, gear.first { it.id == "via_ferrata" }.necessity)
        assertEquals(GearNecessity.RECOMMENDED, gear.first { it.id == "satellite" }.necessity)
    }

    @Test
    fun build_preservesPackedStateForMatchingItems() {
        val previous = listOf(
            GearItem(
                id = "offline_map",
                name = "Harta offline + GPX",
                weight = "0 g",
                category = "Navigatie & energie",
                weightGrams = 0,
                necessity = GearNecessity.MANDATORY,
                isPacked = true
            )
        )

        val gear = GearRecommendationEngine.build(buildTrail(), previous)

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
