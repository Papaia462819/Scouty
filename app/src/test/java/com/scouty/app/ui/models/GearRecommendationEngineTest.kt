package com.scouty.app.ui.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class GearRecommendationEngineTest {
    @Test
    fun build_defaultChecklistContainsCoreTrailItems() {
        val gear = GearRecommendationEngine.build(trail = null)

        assertTrue(gear.any { it.id == "footwear" && it.necessity == GearNecessity.MANDATORY })
        assertTrue(gear.any { it.id == "backpack" && it.necessity == GearNecessity.MANDATORY })
        assertTrue(gear.any { it.id == "phone_navigation" && it.necessity == GearNecessity.MANDATORY })
        assertTrue(gear.any { it.id == "water" && it.necessity == GearNecessity.MANDATORY })
    }

    @Test
    fun build_hardWinterTrailWithChildrenAddsTechnicalAndKidsItems() {
        val trail = buildTrail(
            difficulty = "HARD",
            distanceKm = 17.5,
            elevationGain = 1350,
            estimatedDuration = "~8h 20m",
            weatherForecast = "-3 C, snow showers",
            month = Calendar.JANUARY,
            party = TrailPartyComposition(adults = 2, children = 1)
        )

        val gear = GearRecommendationEngine.build(trail)

        assertTrue(gear.any { it.id == "winter_layers" && it.necessity == GearNecessity.MANDATORY })
        assertTrue(gear.any { it.id == "thermos" && it.necessity == GearNecessity.MANDATORY })
        assertTrue(gear.any { it.id == "crampons_axe" && it.necessity == GearNecessity.CONDITIONAL })
        assertTrue(gear.any { it.id == "child_change_kit" && it.necessity == GearNecessity.MANDATORY })
        assertEquals("7.8 L", gear.first { it.id == "water" }.weight)
    }

    @Test
    fun build_easyHotTrailKeepsListLeanAndAddsHeatKit() {
        val trail = buildTrail(
            difficulty = "EASY",
            distanceKm = 4.4,
            elevationGain = 220,
            estimatedDuration = "~2h 30m",
            weatherForecast = "28 C, clear sky",
            month = Calendar.JULY
        )

        val gear = GearRecommendationEngine.build(trail)

        assertTrue(gear.any { it.id == "heat_kit" && it.necessity == GearNecessity.MANDATORY })
        assertTrue(gear.any { it.id == "blister_patches" && it.necessity == GearNecessity.MANDATORY })
        assertFalse(gear.any { it.id == "headlamp" })
        assertFalse(gear.any { it.id == "first_aid" })
    }

    @Test
    fun build_preservesPackedStateForMatchingItems() {
        val previous = listOf(
            GearItem(
                id = "phone_navigation",
                name = "Telefon incarcat 100%",
                weight = "100%",
                category = "Siguranta & navigatie",
                weightGrams = 0,
                necessity = GearNecessity.MANDATORY,
                isPacked = true
            )
        )

        val gear = GearRecommendationEngine.build(buildTrail(), previousItems = previous)

        val phoneItem = gear.firstOrNull { it.id == "phone_navigation" }
        assertNotNull(phoneItem)
        assertTrue(phoneItem!!.isPacked)
    }

    private fun buildTrail(
        difficulty: String = "MEDIUM",
        distanceKm: Double = 11.4,
        elevationGain: Int = 620,
        estimatedDuration: String = "~5h 20m",
        weatherForecast: String? = null,
        month: Int = Calendar.MAY,
        party: TrailPartyComposition = TrailPartyComposition()
    ): ActiveTrail =
        ActiveTrail(
            name = "Test trail",
            date = Calendar.getInstance().apply { set(Calendar.MONTH, month) },
            partyComposition = party,
            latitude = 45.4,
            longitude = 25.4,
            difficulty = difficulty,
            distanceKm = distanceKm,
            elevationGain = elevationGain,
            estimatedDuration = estimatedDuration,
            weatherForecast = weatherForecast
        )
}
