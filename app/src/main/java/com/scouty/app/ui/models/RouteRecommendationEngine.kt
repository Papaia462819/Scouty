package com.scouty.app.ui.models

import com.scouty.app.data.RouteEnrichmentCatalog
import com.scouty.app.data.RouteEnrichmentEntry
import com.scouty.app.data.RouteGeometryIndex
import com.scouty.app.data.RouteGeometryRepository
import com.scouty.app.data.bestDescriptionRo
import kotlin.math.roundToInt

object RouteRecommendationEngine {
    fun recommend(
        profile: UserTrailProfile,
        catalog: RouteEnrichmentCatalog,
        geometryIndex: RouteGeometryIndex,
        latitude: Double? = null,
        longitude: Double? = null,
        activeTrail: ActiveTrail? = null,
        limit: Int = 4
    ): List<RouteRecommendation> {
        return catalog.routesByLocalCode
            .asSequence()
            .mapNotNull { (localCode, entry) ->
                val geometry = RouteGeometryRepository.findByLocalCode(geometryIndex, localCode) ?: return@mapNotNull null
                val distanceKm = entry.mnData?.distanceKm ?: entry.localDistanceKm ?: return@mapNotNull null
                val elevationGain = entry.mnData?.ascentM ?: 0
                val durationHours = TrailMetadataFormatter.parseDurationHours(entry.mnData?.durationText)
                    ?: TrailMetadataFormatter.estimateDurationHours(distanceKm, elevationGain)
                val durationText = entry.mnData?.durationText
                    ?: "${durationHours.roundToInt()}h"
                val difficulty = TrailDifficultyRank.from(entry.mnData?.difficultyLabel)
                val proximityKm = if (latitude != null && longitude != null) {
                    haversineKm(latitude, longitude, geometry.center.lat, geometry.center.lon)
                } else {
                    null
                }

                val score = scoreRoute(
                    profile = profile,
                    entry = entry,
                    difficulty = difficulty,
                    distanceKm = distanceKm,
                    elevationGain = elevationGain,
                    durationHours = durationHours,
                    proximityKm = proximityKm,
                    activeTrail = activeTrail
                )
                if (score < 25) {
                    return@mapNotNull null
                }

                val markerLabel = TrailMetadataFormatter.formatTrailMarkers(entry.symbols)
                val routeSummary = TrailMetadataFormatter.buildRouteSummary(
                    durationText = durationText,
                    elevationGain = elevationGain,
                    difficulty = difficulty,
                    markerLabel = markerLabel,
                    fromName = entry.from,
                    toName = entry.to
                )

                RouteRecommendation(
                    localCode = localCode,
                    title = entry.displayTitle ?: entry.title ?: localCode,
                    region = entry.region,
                    difficulty = difficulty,
                    distanceKm = distanceKm,
                    elevationGain = elevationGain,
                    durationText = durationText,
                    proximityKm = proximityKm,
                    markerLabel = markerLabel,
                    routeSummary = routeSummary,
                    whyItFits = TrailSuitabilityReasoner.explain(
                        profile = profile,
                        difficulty = difficulty,
                        distanceKm = distanceKm,
                        elevationGain = elevationGain,
                        durationHours = durationHours,
                        proximityKm = proximityKm,
                        activeTrail = activeTrail,
                        description = entry.bestDescriptionRo(),
                        region = entry.region
                    ),
                    fitScore = score,
                    imageUrl = entry.image?.thumbnailUrl ?: entry.image?.imageUrl,
                    sourceUrls = entry.sourceUrls.ifEmpty {
                        listOfNotNull(entry.mnData?.pageUrl, entry.image?.sourcePageUrl).distinct()
                    }
                )
            }
            .sortedWith(
                compareByDescending<RouteRecommendation> { it.fitScore }
                    .thenBy { it.proximityKm ?: Double.MAX_VALUE }
                    .thenBy { it.title }
            )
            .take(limit)
            .toList()
    }

    private fun scoreRoute(
        profile: UserTrailProfile,
        entry: RouteEnrichmentEntry,
        difficulty: TrailDifficultyRank,
        distanceKm: Double,
        elevationGain: Int,
        durationHours: Double,
        proximityKm: Double?,
        activeTrail: ActiveTrail?
    ): Int {
        var score = 100

        val comfortDifficulty = profile.comfortDifficulty.ordinal
        val routeDifficulty = difficulty.ordinal
        if (routeDifficulty > comfortDifficulty) {
            score -= (routeDifficulty - comfortDifficulty) * 24
        } else {
            score += 8
        }

        val overtimeHours = (durationHours - profile.maxPreferredHours).coerceAtLeast(0.0)
        score -= (overtimeHours * 18).roundToInt()

        val excessGain = (elevationGain - profile.maxPreferredElevationGain).coerceAtLeast(0)
        score -= (excessGain / 70.0).roundToInt()

        proximityKm?.let { distanceFromUser ->
            score += when {
                distanceFromUser <= 20.0 -> 16
                distanceFromUser <= 45.0 -> 12
                distanceFromUser <= 90.0 -> 6
                else -> 0
            }
        }

        if (!activeTrail?.region.isNullOrBlank() && activeTrail?.region == entry.region) {
            score += 8
        }

        if (entry.bestDescriptionRo().isNullOrBlank().not()) {
            score += 3
        }
        if (entry.symbols.isNotEmpty()) {
            score += 3
        }
        if (entry.sourceUrls.isNotEmpty() || !entry.mnData?.pageUrl.isNullOrBlank()) {
            score += 4
        }

        score += tripTypeBonus(
            preferredTripType = profile.preferredTripType,
            difficulty = difficulty,
            distanceKm = distanceKm,
            elevationGain = elevationGain,
            durationHours = durationHours,
            description = entry.bestDescriptionRo(),
            title = entry.displayTitle ?: entry.title
        )

        return score
    }

    private fun tripTypeBonus(
        preferredTripType: PreferredTripType,
        difficulty: TrailDifficultyRank,
        distanceKm: Double,
        elevationGain: Int,
        durationHours: Double,
        description: String?,
        title: String?
    ): Int {
        val scenicText = "${title.orEmpty()} ${description.orEmpty()}".lowercase()
        val hasScenicCue = listOf("panoram", "lac", "cascad", "creast", "belvedere", "view").any { it in scenicText }

        return when (preferredTripType) {
            PreferredTripType.RELAXED_SCENIC -> when {
                hasScenicCue && difficulty <= TrailDifficultyRank.MEDIUM && durationHours <= 6.0 -> 12
                difficulty <= TrailDifficultyRank.MEDIUM -> 8
                else -> 0
            }
            PreferredTripType.TRAINING -> when {
                elevationGain >= 800 && difficulty >= TrailDifficultyRank.MEDIUM -> 12
                elevationGain >= 500 -> 7
                else -> 0
            }
            PreferredTripType.FAMILY_FRIENDLY -> when {
                difficulty == TrailDifficultyRank.EASY && distanceKm <= 9.0 && durationHours <= 4.5 -> 14
                difficulty <= TrailDifficultyRank.MEDIUM && durationHours <= 5.0 -> 8
                else -> -8
            }
            PreferredTripType.LONG_ADVENTURE -> when {
                durationHours >= 6.0 || distanceKm >= 14.0 || difficulty >= TrailDifficultyRank.HARD -> 12
                durationHours >= 4.5 -> 6
                else -> -4
            }
        }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return earthRadiusKm * c
    }
}

object TrailSuitabilityReasoner {
    fun explain(
        profile: UserTrailProfile,
        difficulty: TrailDifficultyRank,
        distanceKm: Double,
        elevationGain: Int,
        durationHours: Double,
        proximityKm: Double?,
        activeTrail: ActiveTrail?,
        description: String?,
        region: String?
    ): String {
        val reasons = mutableListOf<String>()
        val withinHours = durationHours <= profile.maxPreferredHours + 0.4
        val withinGain = elevationGain <= profile.maxPreferredElevationGain + 80

        reasons += when {
            withinHours && withinGain -> "Se incadreaza bine in plafonul tau de efort."
            withinHours -> "Durata e potrivita, dar urcarea cere ceva rezerve."
            withinGain -> "Urcarea e acceptabila, dar durata e spre limita profilului tau."
            else -> "Este un traseu putin mai ambitios decat profilul tau curent."
        }

        if (difficulty > profile.comfortDifficulty) {
            reasons += "Dificultatea depaseste zona ta de confort obisnuita."
        } else {
            reasons += "Dificultatea ramane in zona pe care o poti gestiona."
        }

        proximityKm?.let {
            reasons += if (it <= 45.0) {
                "Este relativ aproape de pozitia ta curenta."
            } else {
                "Necesita deplasare suplimentara fata de pozitia ta actuala."
            }
        }

        if (!activeTrail?.region.isNullOrBlank() && activeTrail?.region == region) {
            reasons += "Ramane in aceeasi zona cu traseul tau activ."
        }

        description?.takeIf { it.isNotBlank() }?.let {
            if (listOf("panoram", "cascad", "lac", "creast").any { token -> token in it.lowercase() }) {
                reasons += "Are indicii bune de traseu placut si variat."
            }
        }

        return reasons.take(3).joinToString(" ")
    }
}
