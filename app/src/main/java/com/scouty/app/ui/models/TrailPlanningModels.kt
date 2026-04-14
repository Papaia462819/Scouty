package com.scouty.app.ui.models

import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@Serializable
data class TrailPartyComposition(
    val adults: Int = 1,
    val children: Int = 0
) {
    val totalPeople: Int
        get() = adults + children

    val summaryRo: String
        get() = buildList {
            if (adults > 0) add("$adults ${if (adults == 1) "adult" else "adulti"}")
            if (children > 0) add("$children ${if (children == 1) "copil" else "copii"}")
        }.ifEmpty { listOf("1 adult") }.joinToString(" • ")
}

@Serializable
enum class ExperienceLevel(
    val labelRo: String,
    val summaryRo: String
) {
    BEGINNER("Incepator", "Prefera trasee clare si conservatoare"),
    INTERMEDIATE("Intermediar", "Poate duce ture medii cu diferenta de nivel"),
    ADVANCED("Avansat", "Poate gestiona trasee lungi sau solicitante")
}

@Serializable
enum class FitnessLevel(
    val labelRo: String,
    val summaryRo: String
) {
    LIGHT("Usoara", "Ritm relaxat, pauze dese"),
    MODERATE("Medie", "Poate sustine o tura de o zi"),
    STRONG("Buna", "Tolereaza urcari lungi si ritm alert")
}

@Serializable
enum class PreferredTripType(
    val labelRo: String,
    val summaryRo: String
) {
    RELAXED_SCENIC("Panoramic", "Accent pe privelisti si ritm lejer"),
    TRAINING("Antrenament", "Accent pe efort si progres"),
    FAMILY_FRIENDLY("Lejer", "Accent pe simplitate si siguranta"),
    LONG_ADVENTURE("Aventura lunga", "Accent pe durata si varietate")
}

@Serializable
enum class TrailDifficultyRank(
    val labelRo: String
) {
    EASY("Usor"),
    MEDIUM("Mediu"),
    HARD("Dificil"),
    EXPERT("Expert");

    companion object {
        fun from(rawValue: String?): TrailDifficultyRank {
            val normalized = rawValue
                ?.trim()
                ?.lowercase(Locale.ROOT)
                .orEmpty()

            return when {
                "expert" in normalized -> EXPERT
                "dificil" in normalized || "hard" in normalized -> HARD
                "mediu" in normalized || "medium" in normalized -> MEDIUM
                "usor" in normalized || "ușor" in normalized || "easy" in normalized -> EASY
                else -> MEDIUM
            }
        }
    }
}

@Serializable
data class UserTrailProfile(
    val experienceLevel: ExperienceLevel = ExperienceLevel.BEGINNER,
    val fitnessLevel: FitnessLevel = FitnessLevel.MODERATE,
    val maxPreferredHours: Double = 5.0,
    val maxPreferredElevationGain: Int = 700,
    val preferredTripType: PreferredTripType = PreferredTripType.RELAXED_SCENIC,
    val selectedTrailCount: Int = 0
) {
    val comfortDifficulty: TrailDifficultyRank
        get() = when (experienceLevel) {
            ExperienceLevel.BEGINNER -> {
                if (fitnessLevel == FitnessLevel.STRONG) TrailDifficultyRank.MEDIUM else TrailDifficultyRank.EASY
            }
            ExperienceLevel.INTERMEDIATE -> {
                if (fitnessLevel == FitnessLevel.LIGHT) TrailDifficultyRank.MEDIUM else TrailDifficultyRank.HARD
            }
            ExperienceLevel.ADVANCED -> {
                if (fitnessLevel == FitnessLevel.LIGHT) TrailDifficultyRank.HARD else TrailDifficultyRank.EXPERT
            }
        }

    val shortSummaryRo: String
        get() = buildString {
            append(experienceLevel.labelRo)
            append(" • ")
            append(fitnessLevel.labelRo.lowercase(Locale.ROOT))
            append(" • ")
            append(maxPreferredHours.formatOneDecimal())
            append(" h max")
            append(" • +")
            append(maxPreferredElevationGain)
            append(" m")
        }
}

data class RouteRecommendation(
    val localCode: String,
    val title: String,
    val region: String? = null,
    val difficulty: TrailDifficultyRank = TrailDifficultyRank.MEDIUM,
    val distanceKm: Double = 0.0,
    val elevationGain: Int = 0,
    val durationText: String = "--",
    val proximityKm: Double? = null,
    val markerLabel: String? = null,
    val routeSummary: String = "",
    val whyItFits: String = "",
    val fitScore: Int = 0,
    val imageUrl: String? = null,
    val sourceUrls: List<String> = emptyList()
) {
    val secondarySummary: String
        get() = listOfNotNull(
            durationText.takeIf { it.isNotBlank() && it != "--" },
            distanceKm.takeIf { it > 0.0 }?.let { "${it.formatOneDecimal()} km" },
            elevationGain.takeIf { it > 0 }?.let { "+$it m" }
        ).joinToString(" • ")
}

object TrailMetadataFormatter {
    fun parseDurationHours(rawValue: String?): Double? {
        if (rawValue.isNullOrBlank()) {
            return null
        }
        val cleaned = rawValue.lowercase(Locale.ROOT)
        val hours = "(\\d+)\\s*h".toRegex().find(cleaned)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        val minutes = "(\\d+)\\s*m".toRegex().find(cleaned)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        val total = hours + (minutes / 60.0)
        return total.takeIf { it > 0.0 }
    }

    fun estimateDurationHours(distanceKm: Double, elevationGain: Int): Double =
        (distanceKm / 4.2) + (elevationGain / 500.0)

    fun formatTrailMarkers(symbols: List<String>): String? {
        val labels = symbols
            .map(::formatSingleTrailMarker)
            .filter { it.isNotBlank() }
            .distinct()
        return labels.takeIf { it.isNotEmpty() }?.joinToString(" • ")
    }

    fun formatSingleTrailMarker(rawMarker: String): String {
        val parts = rawMarker.split(":")
        if (parts.size < 3) {
            return rawMarker.replace('_', ' ')
        }

        val shapeToken = parts[2].lowercase(Locale.ROOT)
        val (shapeLabel, grammaticalGender) = when (shapeToken) {
            "red_stripe", "blue_stripe", "yellow_stripe", "green_stripe", "stripe" -> "banda" to "f"
            "triangle" -> "triunghi" to "m"
            "dot" -> "punct" to "m"
            "cross" -> "cruce" to "f"
            "circle" -> "cerc" to "m"
            else -> parts[2].replace('_', ' ') to "m"
        }
        val color = markerColorLabel(parts[0], grammaticalGender)
        return "$shapeLabel $color"
    }

    fun buildRouteSummary(
        durationText: String?,
        elevationGain: Int,
        difficulty: TrailDifficultyRank,
        markerLabel: String?,
        fromName: String? = null,
        toName: String? = null
    ): String {
        val overview = mutableListOf<String>()
        if (!fromName.isNullOrBlank() && !toName.isNullOrBlank()) {
            overview += "$fromName → $toName"
        }
        durationText?.takeIf { it.isNotBlank() && it != "--" }?.let { overview += it }
        if (elevationGain > 0) {
            overview += "+$elevationGain m"
        }
        overview += difficulty.labelRo.lowercase(Locale.ROOT)

        return buildString {
            append(overview.joinToString(" • "))
            markerLabel?.takeIf { it.isNotBlank() }?.let {
                append(". Marcaj util: ")
                append(it)
            }
        }
    }

    fun parseTemperatureC(weatherSummary: String?): Double? =
        weatherSummary
            ?.let { "(-?\\d+(?:\\.\\d+)?)\\s*°?c".toRegex(RegexOption.IGNORE_CASE).find(it) }
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()

    fun hasStormRisk(weatherSummary: String?): Boolean {
        val normalized = weatherSummary?.lowercase(Locale.ROOT).orEmpty()
        return listOf("storm", "thunder", "furtuna", "fulger", "rain", "ploaie").any { it in normalized }
    }

    fun isLikelyHot(weatherSummary: String?): Boolean {
        val normalized = weatherSummary?.lowercase(Locale.ROOT).orEmpty()
        val temperature = parseTemperatureC(weatherSummary)
        return temperature != null && temperature >= 24.0 ||
            listOf("clear", "sun", "soare", "heat", "canicula").any { it in normalized }
    }
}

private fun markerColorLabel(rawColor: String, grammaticalGender: String): String =
    when (rawColor.lowercase(Locale.ROOT)) {
        "red" -> if (grammaticalGender == "f") "rosie" else "rosu"
        "blue" -> if (grammaticalGender == "f") "albastra" else "albastru"
        "yellow" -> if (grammaticalGender == "f") "galbena" else "galben"
        "green" -> "verde"
        else -> rawColor.lowercase(Locale.ROOT)
    }

fun UserTrailProfile.adaptToTrail(trail: ActiveTrail): UserTrailProfile {
    val trailHours = TrailMetadataFormatter.parseDurationHours(trail.estimatedDuration)
        ?: TrailMetadataFormatter.estimateDurationHours(trail.distanceKm, trail.elevationGain)
    val blendedHours = ((maxPreferredHours * 0.85) + (trailHours * 0.15)).coerceIn(3.0, 10.0)
    val blendedGain = ((maxPreferredElevationGain * 0.85) + (trail.elevationGain * 0.15))
        .roundToInt()
        .coerceIn(350, 1800)

    return copy(
        maxPreferredHours = max(maxPreferredHours, blendedHours).coerceAtMost(10.0),
        maxPreferredElevationGain = max(maxPreferredElevationGain, blendedGain),
        selectedTrailCount = selectedTrailCount + 1
    )
}

private fun Double.formatOneDecimal(): String = String.format(Locale.US, "%.1f", this)
