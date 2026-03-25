package com.scouty.app.ui.models

import java.util.Locale
import kotlin.math.roundToInt

object GearRecommendationEngine {
    private const val CategoryPlanning = "Planificare & navigatie"
    private const val CategoryHydration = "Apa & energie"
    private const val CategoryWeather = "Vreme & confort"
    private const val CategorySafety = "Siguranta"

    private val categoryOrder = mapOf(
        CategoryPlanning to 0,
        CategoryHydration to 1,
        CategoryWeather to 2,
        CategorySafety to 3
    )

    private val gearRules = listOf(
        GearRule(
            id = "offline_map",
            name = "Telefon + harta offline",
            category = CategoryPlanning,
            weightLabel = { "0 g" },
            weightGrams = { 0 },
            note = { "Ramane baza pentru orientare, check-in rapid si apel de urgenta." },
            necessity = { GearNecessity.MANDATORY }
        ),
        GearRule(
            id = "water",
            name = "Apa",
            category = CategoryHydration,
            weightLabel = { profile -> "${profile.waterLiters.formatOneDecimal()} L min" },
            weightGrams = { profile -> (profile.waterLiters * 1000).roundToInt() },
            note = { profile ->
                if (profile.hotWeather) {
                    "Temperatura sau expunerea la soare cer o rezerva mai mare de apa."
                } else {
                    "Rezerva minima este calculata din durata, diferenta de nivel si ritmul estimat."
                }
            },
            necessity = { GearNecessity.MANDATORY }
        ),
        GearRule(
            id = "food",
            name = "Gustare + rezerva de energie",
            category = CategoryHydration,
            weightLabel = { profile -> "${profile.foodGrams} g" },
            weightGrams = { profile -> profile.foodGrams },
            note = { "O gustare simpla plus o mica rezerva acopera intarzierile si scaderile de energie." },
            necessity = { GearNecessity.MANDATORY }
        ),
        GearRule(
            id = "rain_shell",
            name = "Geaca impermeabila",
            category = CategoryWeather,
            weightLabel = { "300 g" },
            weightGrams = { 300 },
            note = { profile ->
                if (profile.stormRisk) {
                    "Prognoza sugereaza ploaie sau instabilitate, deci geaca trebuie sa fie la indemana."
                } else {
                    "Pastrata in rucsac, te acopera rapid daca vantul sau ploaia schimba ritmul turei."
                }
            },
            necessity = { GearNecessity.MANDATORY }
        ),
        GearRule(
            id = "insulation_layer",
            name = "Strat cald",
            category = CategoryWeather,
            weightLabel = { "260 g" },
            weightGrams = { 260 },
            note = { "Util la pauze, dupa apus sau cand vremea se schimba mai repede decat era estimat." },
            necessity = {
                when {
                    it.isLongDay || it.isRemote || it.stormRisk -> GearNecessity.MANDATORY
                    else -> GearNecessity.RECOMMENDED
                }
            }
        ),
        GearRule(
            id = "sun_protection",
            name = "Protectie solara",
            category = CategoryWeather,
            weightLabel = { "120 g" },
            weightGrams = { 120 },
            note = { "Sapca, SPF si ochelari sunt utile chiar si pe ture medii daca stai mult in gol alpin." },
            necessity = {
                if (it.hotWeather || it.distanceKm >= 8.0) {
                    GearNecessity.MANDATORY
                } else {
                    GearNecessity.RECOMMENDED
                }
            }
        ),
        GearRule(
            id = "headlamp",
            name = "Frontala",
            category = CategoryPlanning,
            weightLabel = { "95 g" },
            weightGrams = { 95 },
            note = { "Orice intarziere la intoarcere transforma o tura scurta intr-o coborare pe lumina slaba." },
            necessity = {
                when {
                    it.isLongDay || it.isRemote -> GearNecessity.MANDATORY
                    else -> GearNecessity.RECOMMENDED
                }
            }
        ),
        GearRule(
            id = "first_aid",
            name = "Mini kit prim ajutor",
            category = CategorySafety,
            weightLabel = { "180 g" },
            weightGrams = { 180 },
            note = { "Ramane util pentru basic care: plasturi, compresa, tifon, banda si manusi." },
            necessity = { GearNecessity.MANDATORY }
        ),
        GearRule(
            id = "whistle",
            name = "Fluier",
            category = CategorySafety,
            weightLabel = { "15 g" },
            weightGrams = { 15 },
            note = { "Semnalizarea sonora costa putin si ramane utila in ceata, padure sau dupa o accidentare." },
            necessity = {
                when {
                    it.difficulty >= TrailDifficultyRank.MEDIUM || it.isRemote -> GearNecessity.MANDATORY
                    else -> GearNecessity.RECOMMENDED
                }
            }
        ),
        GearRule(
            id = "trekking_poles",
            name = "Bete trekking",
            category = CategoryWeather,
            weightLabel = { "420 g" },
            weightGrams = { 420 },
            note = { "Devin utile pe urcari si mai ales pe coborari lungi, unde scad sarcina din genunchi." },
            necessity = {
                when {
                    it.elevationGain >= 900 || it.difficulty >= TrailDifficultyRank.HARD -> GearNecessity.RECOMMENDED
                    it.elevationGain >= 600 -> GearNecessity.CONDITIONAL
                    else -> null
                }
            }
        ),
        GearRule(
            id = "water_treatment",
            name = "Filtru sau tablete pentru apa",
            category = CategoryHydration,
            weightLabel = { "80 g" },
            weightGrams = { 80 },
            note = { "Merita daca tura e lunga sau rezerva de apa transportata devine greu de dus de la start." },
            necessity = {
                when {
                    it.isRemote || it.distanceKm >= 15.0 || it.durationHours >= 6.5 -> GearNecessity.RECOMMENDED
                    it.distanceKm >= 10.0 -> GearNecessity.CONDITIONAL
                    else -> null
                }
            }
        ),
        GearRule(
            id = "emergency_bivy",
            name = "Folie de urgenta",
            category = CategorySafety,
            weightLabel = { "110 g" },
            weightGrams = { 110 },
            note = { "Ramane una dintre cele mai bune piese mici pentru pauze fortate, frig sau asteptarea ajutorului." },
            necessity = {
                when {
                    it.isRemote || it.difficulty >= TrailDifficultyRank.HARD -> GearNecessity.MANDATORY
                    it.isLongDay -> GearNecessity.RECOMMENDED
                    else -> GearNecessity.CONDITIONAL
                }
            }
        )
    )

    fun build(
        trail: ActiveTrail?,
        profile: UserTrailProfile = UserTrailProfile(),
        previousItems: List<GearItem> = emptyList()
    ): List<GearItem> {
        val packingProfile = PackingProfile.from(trail = trail, userProfile = profile)
        val previousPackedState = previousItems.associate { it.id to it.isPacked }

        return gearRules
            .mapNotNull { rule ->
                val necessity = rule.necessity(packingProfile) ?: return@mapNotNull null
                GearItem(
                    id = rule.id,
                    name = rule.name,
                    weight = rule.weightLabel(packingProfile),
                    category = rule.category,
                    weightGrams = rule.weightGrams(packingProfile),
                    necessity = necessity,
                    note = rule.note(packingProfile),
                    isPacked = previousPackedState[rule.id] ?: false
                )
            }
            .sortedWith(
                compareBy<GearItem> { categoryOrder[it.category] ?: Int.MAX_VALUE }
                    .thenBy { it.necessity.sortOrder }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            )
    }
}

private data class GearRule(
    val id: String,
    val name: String,
    val category: String,
    val weightLabel: (PackingProfile) -> String,
    val weightGrams: (PackingProfile) -> Int?,
    val note: (PackingProfile) -> String,
    val necessity: (PackingProfile) -> GearNecessity?
)

private data class PackingProfile(
    val difficulty: TrailDifficultyRank,
    val distanceKm: Double,
    val elevationGain: Int,
    val durationHours: Double,
    val waterLiters: Double,
    val foodGrams: Int,
    val isLongDay: Boolean,
    val isRemote: Boolean,
    val stormRisk: Boolean,
    val hotWeather: Boolean
) {
    companion object {
        fun from(trail: ActiveTrail?, userProfile: UserTrailProfile): PackingProfile {
            val difficulty = TrailDifficultyRank.from(trail?.difficulty ?: userProfile.comfortDifficulty.name)
            val distanceKm = trail?.distanceKm?.takeIf { it > 0.0 }
                ?: (userProfile.maxPreferredHours * 3.8).coerceAtLeast(6.0)
            val elevationGain = trail?.elevationGain?.takeIf { it > 0 }
                ?: (userProfile.maxPreferredElevationGain * 0.75).roundToInt()
            val durationHours = TrailMetadataFormatter.parseDurationHours(trail?.estimatedDuration)
                ?: TrailMetadataFormatter.estimateDurationHours(distanceKm, elevationGain)
            val stormRisk = TrailMetadataFormatter.hasStormRisk(trail?.weatherForecast)
            val hotWeather = TrailMetadataFormatter.isLikelyHot(trail?.weatherForecast)
            val isLongDay = durationHours >= 5.5 || distanceKm >= 12.0
            val isRemote = durationHours >= 7.0 || distanceKm >= 16.0 || difficulty >= TrailDifficultyRank.HARD
            val waterLiters = buildWaterLiters(
                durationHours = durationHours,
                elevationGain = elevationGain,
                hotWeather = hotWeather,
                isRemote = isRemote
            )
            val foodGrams = when {
                isRemote || durationHours >= 7.0 -> 520
                isLongDay || elevationGain >= 900 -> 430
                else -> 300
            }

            return PackingProfile(
                difficulty = difficulty,
                distanceKm = distanceKm,
                elevationGain = elevationGain,
                durationHours = durationHours,
                waterLiters = waterLiters,
                foodGrams = foodGrams,
                isLongDay = isLongDay,
                isRemote = isRemote,
                stormRisk = stormRisk,
                hotWeather = hotWeather
            )
        }

        private fun buildWaterLiters(
            durationHours: Double,
            elevationGain: Int,
            hotWeather: Boolean,
            isRemote: Boolean
        ): Double {
            var liters = when {
                durationHours >= 7.0 || elevationGain >= 1100 -> 2.4
                durationHours >= 5.0 || elevationGain >= 800 -> 1.9
                durationHours >= 3.5 -> 1.5
                else -> 1.2
            }
            if (hotWeather) liters += 0.5
            if (isRemote) liters += 0.2
            return (liters * 10).roundToInt() / 10.0
        }
    }
}

private val GearNecessity.sortOrder: Int
    get() = when (this) {
        GearNecessity.MANDATORY -> 0
        GearNecessity.RECOMMENDED -> 1
        GearNecessity.CONDITIONAL -> 2
    }

private fun Double.formatOneDecimal(): String = String.format(Locale.US, "%.1f", this)
