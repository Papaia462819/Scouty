package com.scouty.app.utils

import kotlin.math.*

enum class TrailDifficulty {
    EASY, MEDIUM, HARD, EXPERT
}

data class TrailMetrics(
    val lengthKm: Double,
    val elevationGainM: Double,
    val segmentDensity: Double // Points per km
)

data class ElevationProfile(
    val coordinates: List<Pair<Double, Double>>, // Lat, Lng
    val altitudes: List<Double>
)

data class TrailDifficultyResult(
    val difficulty: String,
    val totalAscent: Double,
    val totalDescent: Double,
    val avgIncline: Double,
    val lengthKm: Double,
    val durationHours: Double
)

object TrailDifficultyCalculator {

    fun calculateDifficulty(metrics: TrailMetrics): TrailDifficulty {
        val effortScore = metrics.lengthKm + (metrics.elevationGainM / 50.0)
        val technicalMultiplier = if (metrics.segmentDensity > 30) 1.3 else 1.0
        val finalScore = effortScore * technicalMultiplier

        return when {
            finalScore < 12 -> TrailDifficulty.EASY
            finalScore < 30 -> TrailDifficulty.MEDIUM
            finalScore < 50 -> TrailDifficulty.HARD
            else -> TrailDifficulty.EXPERT
        }
    }

    fun classifyTrail(profile: ElevationProfile): TrailDifficultyResult {
        var totalAscent = 0.0
        var totalDescent = 0.0
        var maxIncline = 0.0
        var totalDistance = 0.0

        if (profile.altitudes.size < 2) {
            return TrailDifficultyResult("MEDIUM", 0.0, 0.0, 0.0, 0.0, 0.0)
        }

        for (i in 0 until profile.altitudes.size - 1) {
            val h1 = profile.altitudes[i]
            val h2 = profile.altitudes[i + 1]
            val diff = h2 - h1
            
            if (diff > 0) totalAscent += diff else totalDescent += abs(diff)

            val dist = calculateDistance(
                profile.coordinates[i].first, profile.coordinates[i].second,
                profile.coordinates[i+1].first, profile.coordinates[i+1].second
            )
            totalDistance += dist

            if (dist > 0) {
                val incline = (diff / (dist * 1000)) * 100
                if (abs(incline) > maxIncline) maxIncline = abs(incline)
            }
        }

        val avgIncline = if (totalDistance > 0) (totalAscent / (totalDistance * 1000)) * 100 else 0.0
        
        val difficulty = when {
            maxIncline > 45 || avgIncline > 25 -> "EXPERT"
            maxIncline > 30 || avgIncline > 15 -> "HARD"
            maxIncline > 15 || avgIncline > 8 -> "MEDIUM"
            else -> "EASY"
        }

        val durationHours = (totalDistance / 4.0) + (totalAscent / 400.0)

        return TrailDifficultyResult(
            difficulty = difficulty,
            totalAscent = totalAscent,
            totalDescent = totalDescent,
            avgIncline = avgIncline,
            lengthKm = totalDistance,
            durationHours = durationHours
        )
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
