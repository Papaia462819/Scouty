package com.scouty.app.ui.models

import com.scouty.app.data.RouteBounds
import com.scouty.app.data.RouteCenter
import com.scouty.app.data.RouteEnrichmentCatalog
import com.scouty.app.data.RouteEnrichmentEntry
import com.scouty.app.data.RouteGeometryEntry
import com.scouty.app.data.RouteGeometryIndex
import com.scouty.app.data.RouteMnData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRecommendationEngineTest {
    @Test
    fun recommend_beginnerFamilyProfilePrefersEasyNearbyRoute() {
        val recommendations = RouteRecommendationEngine.recommend(
            profile = UserTrailProfile(
                experienceLevel = ExperienceLevel.BEGINNER,
                fitnessLevel = FitnessLevel.LIGHT,
                maxPreferredHours = 4.0,
                maxPreferredElevationGain = 500,
                preferredTripType = PreferredTripType.FAMILY_FRIENDLY
            ),
            catalog = testCatalog(),
            geometryIndex = testGeometryIndex(),
            latitude = 45.00,
            longitude = 25.00,
            limit = 2
        )

        assertTrue(recommendations.isNotEmpty())
        assertEquals("ROUTE_EASY", recommendations.first().localCode)
        assertTrue(recommendations.none { it.localCode == "ROUTE_HARD" })
    }

    @Test
    fun recommend_advancedAdventureProfileCanPromoteLongHardRoute() {
        val recommendations = RouteRecommendationEngine.recommend(
            profile = UserTrailProfile(
                experienceLevel = ExperienceLevel.ADVANCED,
                fitnessLevel = FitnessLevel.STRONG,
                maxPreferredHours = 9.0,
                maxPreferredElevationGain = 1500,
                preferredTripType = PreferredTripType.LONG_ADVENTURE
            ),
            catalog = testCatalog(),
            geometryIndex = testGeometryIndex(),
            latitude = 45.00,
            longitude = 25.00,
            limit = 3
        )

        assertEquals("ROUTE_HARD", recommendations.first().localCode)
        assertTrue(recommendations.any { it.localCode == "ROUTE_MEDIUM" })
    }

    private fun testCatalog(): RouteEnrichmentCatalog =
        RouteEnrichmentCatalog(
            routesByLocalCode = mapOf(
                "ROUTE_EASY" to RouteEnrichmentEntry(
                    localCode = "ROUTE_EASY",
                    displayTitle = "Cascada de weekend",
                    region = "Bucegi",
                    localDescription = "Traseu lejer spre cascada si belvedere panoramica.",
                    symbols = listOf("blue:white:blue_triangle"),
                    sourceUrls = listOf("https://example.com/easy"),
                    mnData = RouteMnData(
                        difficultyLabel = "easy",
                        durationText = "~2h 30m",
                        distanceKm = 5.5,
                        ascentM = 220
                    )
                ),
                "ROUTE_MEDIUM" to RouteEnrichmentEntry(
                    localCode = "ROUTE_MEDIUM",
                    displayTitle = "Creasta scurta",
                    region = "Bucegi",
                    localDescription = "Traseu de antrenament cu creasta scurta si puncte de vedere largi.",
                    symbols = listOf("red:white:red_stripe"),
                    sourceUrls = listOf("https://example.com/medium"),
                    mnData = RouteMnData(
                        difficultyLabel = "medium",
                        durationText = "~4h 40m",
                        distanceKm = 9.8,
                        ascentM = 640
                    )
                ),
                "ROUTE_HARD" to RouteEnrichmentEntry(
                    localCode = "ROUTE_HARD",
                    displayTitle = "Circuit alpin lung",
                    region = "Fagaras",
                    localDescription = "Circuit lung de creasta, solicitant, pentru zi completa.",
                    symbols = listOf("yellow:white:yellow_dot"),
                    sourceUrls = listOf("https://example.com/hard"),
                    mnData = RouteMnData(
                        difficultyLabel = "hard",
                        durationText = "~8h",
                        distanceKm = 16.2,
                        ascentM = 1250
                    )
                )
            )
        )

    private fun testGeometryIndex(): RouteGeometryIndex =
        RouteGeometryIndex(
            routesByLocalCode = mapOf(
                "ROUTE_EASY" to geometryEntry("ROUTE_EASY", 45.01, 25.01),
                "ROUTE_MEDIUM" to geometryEntry("ROUTE_MEDIUM", 45.08, 25.06),
                "ROUTE_HARD" to geometryEntry("ROUTE_HARD", 45.12, 24.92)
            )
        )

    private fun geometryEntry(localCode: String, lat: Double, lon: Double): RouteGeometryEntry =
        RouteGeometryEntry(
            localCode = localCode,
            center = RouteCenter(lat = lat, lon = lon),
            bbox = RouteBounds(
                minLat = lat - 0.01,
                minLon = lon - 0.01,
                maxLat = lat + 0.01,
                maxLon = lon + 0.01
            ),
            segments = emptyList(),
            pointCount = 0
        )
}
