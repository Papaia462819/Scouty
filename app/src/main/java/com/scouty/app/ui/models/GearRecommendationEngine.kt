package com.scouty.app.ui.models

import java.util.Locale

object GearRecommendationEngine {
    private const val CategoryNavigation = "Navigatie & energie"
    private const val CategorySafety = "Siguranta & urgenta"
    private const val CategoryClothing = "Imbracaminte & vreme"
    private const val CategoryFoodWater = "Apa & hrana"
    private const val CategoryTechnical = "Echipament tehnic"

    private val categoryOrder = mapOf(
        CategoryNavigation to 0,
        CategorySafety to 1,
        CategoryClothing to 2,
        CategoryFoodWater to 3,
        CategoryTechnical to 4
    )

    private val gearRules = listOf(
        GearRule(
            id = "offline_map",
            name = "Harta offline + GPX",
            category = CategoryNavigation,
            weightLabel = { "0 g" },
            weightGrams = { 0 },
            note = { "Baza de navigatie recomandata de NPS si AdventureSmart pentru orice drumetie." },
            necessity = { GearNecessity.MANDATORY }
        ),
        GearRule(
            id = "paper_map",
            name = "Harta tiparita / print traseu",
            category = CategoryNavigation,
            weightLabel = { "35 g" },
            weightGrams = { 35 },
            note = { "Backup independent de baterie, util cand telefonul cedeaza sau vremea se schimba rapid." },
            necessity = {
                if (it.difficulty >= TrailDemandLevel.HARD || it.isRemote) {
                    GearNecessity.MANDATORY
                } else {
                    GearNecessity.RECOMMENDED
                }
            }
        ),
        GearRule(
            id = "compass",
            name = "Busola",
            category = CategoryNavigation,
            weightLabel = { "45 g" },
            weightGrams = { 45 },
            note = { "Parte din setul clasic map + compass cerut de ghidurile de hillwalking." },
            necessity = { GearNecessity.MANDATORY }
        ),
        GearRule(
            id = "headlamp",
            name = "Frontala + baterii de rezerva",
            category = CategoryNavigation,
            weightLabel = { "120 g" },
            weightGrams = { 120 },
            note = {
                if (it.isLongDay) {
                    "Intarzierile la coborare sunt frecvente pe ture lungi; lumina trebuie sa ramana disponibila."
                } else {
                    "NPS include iluminarea in kitul esential chiar si pe ture aparent scurte."
                }
            },
            necessity = {
                if (it.difficulty == TrailDemandLevel.EASY && !it.isLongDay) {
                    GearNecessity.RECOMMENDED
                } else {
                    GearNecessity.MANDATORY
                }
            }
        ),
        GearRule(
            id = "power_bank",
            name = "Power bank + cablu",
            category = CategoryNavigation,
            weightLabel = { "220 g" },
            weightGrams = { 220 },
            note = { "Telefonul devine unealta de navigatie, SOS si prognoza; rezerva de baterie scade riscul real de blocaj." },
            necessity = {
                if (it.difficulty >= TrailDemandLevel.MEDIUM || it.isLongDay) {
                    GearNecessity.MANDATORY
                } else {
                    GearNecessity.RECOMMENDED
                }
            }
        ),
        GearRule(
            id = "first_aid",
            name = "Kit prim ajutor",
            category = CategorySafety,
            weightLabel = { "260 g" },
            weightGrams = { 260 },
            note = { "Taieturi, entorse si bataturi apar pe toate nivelurile de dificultate." },
            necessity = { GearNecessity.MANDATORY }
        ),
        GearRule(
            id = "emergency_bivy",
            name = "Folie / bivy de urgenta",
            category = CategorySafety,
            weightLabel = { "110 g" },
            weightGrams = { 110 },
            note = { "Shelter-ul de urgenta este parte din Ten Essentials si apare si in listele Mountain Rescue." },
            necessity = {
                if (it.difficulty >= TrailDemandLevel.MEDIUM || it.isRemote) {
                    GearNecessity.MANDATORY
                } else {
                    GearNecessity.RECOMMENDED
                }
            }
        ),
        GearRule(
            id = "whistle",
            name = "Fluier",
            category = CategorySafety,
            weightLabel = { "15 g" },
            weightGrams = { 15 },
            note = { "Semnalizare simpla, ieftina si eficienta in vant sau ceata." },
            necessity = { GearNecessity.MANDATORY }
        ),
        GearRule(
            id = "repair_kit",
            name = "Mini kit reparatii / multitool",
            category = CategorySafety,
            weightLabel = { "130 g" },
            weightGrams = { 130 },
            note = { "NPS include repair kit-ul in lista esentiala; util pentru betele de trekking, folie, fermoare si bocanci." },
            necessity = {
                when {
                    it.difficulty >= TrailDemandLevel.HARD || it.isRemote -> GearNecessity.MANDATORY
                    it.difficulty >= TrailDemandLevel.MEDIUM || it.isLongDay -> GearNecessity.RECOMMENDED
                    else -> GearNecessity.CONDITIONAL
                }
            }
        ),
        GearRule(
            id = "bear_deterrent",
            name = "Spray anti-urs / avertizor sonor",
            category = CategorySafety,
            weightLabel = { "290 g" },
            weightGrams = { 290 },
            note = { "Salvamont recomanda masuri active de descurajare in zonele cu fauna mare; util mai ales pe vai impadurite si la ore tarzii." },
            necessity = { GearNecessity.CONDITIONAL }
        ),
        GearRule(
            id = "rain_shell",
            name = "Geaca impermeabila",
            category = CategoryClothing,
            weightLabel = { "320 g" },
            weightGrams = { 320 },
            note = { "Vremea in munti se poate inchide repede; hardshell-ul ramane element de baza in toate ghidurile consultate." },
            necessity = { GearNecessity.MANDATORY }
        ),
        GearRule(
            id = "insulation_layer",
            name = "Strat termo / polar",
            category = CategoryClothing,
            weightLabel = { "360 g" },
            weightGrams = { 360 },
            note = { "Un strat cald separat de geaca de ploaie ajuta cand stai pe loc, la pauze sau dupa efort intens." },
            necessity = { GearNecessity.MANDATORY }
        ),
        GearRule(
            id = "hat_gloves",
            name = "Caciula subtire + manusi",
            category = CategoryClothing,
            weightLabel = { "120 g" },
            weightGrams = { 120 },
            note = { "Kit mic, dar foarte valoros pe creasta, in vant sau dupa apus." },
            necessity = {
                if (it.difficulty == TrailDemandLevel.EASY && !it.isLongDay) {
                    GearNecessity.RECOMMENDED
                } else {
                    GearNecessity.MANDATORY
                }
            }
        ),
        GearRule(
            id = "spare_layer",
            name = "Strat uscat de schimb",
            category = CategoryClothing,
            weightLabel = { "240 g" },
            weightGrams = { 240 },
            note = { "Devenine important cand turele sunt lungi, cu multa transpiratie sau cu schimbari de temperatura." },
            necessity = {
                when {
                    it.difficulty >= TrailDemandLevel.HARD || it.isLongDay -> GearNecessity.MANDATORY
                    it.difficulty >= TrailDemandLevel.MEDIUM -> GearNecessity.RECOMMENDED
                    else -> GearNecessity.CONDITIONAL
                }
            }
        ),
        GearRule(
            id = "sun_protection",
            name = "Protectie solara",
            category = CategoryClothing,
            weightLabel = { "140 g" },
            weightGrams = { 140 },
            note = { "Sapca, ochelari si SPF sunt recomandate oficial pentru expunere prelungita pe gol alpin." },
            necessity = { GearNecessity.MANDATORY }
        ),
        GearRule(
            id = "trekking_poles",
            name = "Bete trekking",
            category = CategoryClothing,
            weightLabel = { "460 g" },
            weightGrams = { 460 },
            note = { "Ajuta la echilibru, descarca genunchii si reduc oboseala pe urcari sau coborari lungi." },
            necessity = {
                when {
                    it.difficulty >= TrailDemandLevel.HARD || it.isSteep -> GearNecessity.RECOMMENDED
                    it.difficulty >= TrailDemandLevel.MEDIUM -> GearNecessity.CONDITIONAL
                    else -> GearNecessity.CONDITIONAL
                }
            }
        ),
        GearRule(
            id = "rain_pants",
            name = "Pantaloni impermeabili / parazapezi",
            category = CategoryClothing,
            weightLabel = { "220 g" },
            weightGrams = { 220 },
            note = { "Devine relevant cand terenul e ud, vegetatia e deasa sau traseul e suficient de lung incat ploaia sa schimbe ritmul." },
            necessity = {
                when {
                    it.difficulty >= TrailDemandLevel.EXPERT || it.isLongDay -> GearNecessity.RECOMMENDED
                    it.difficulty >= TrailDemandLevel.MEDIUM -> GearNecessity.CONDITIONAL
                    else -> GearNecessity.CONDITIONAL
                }
            }
        ),
        GearRule(
            id = "water",
            name = "Apa minima",
            category = CategoryFoodWater,
            weightLabel = { profile -> "${profile.waterLiters.formatOneDecimal()} L min" },
            weightGrams = { profile -> (profile.waterLiters * 1000).toInt() },
            note = { profile ->
                "Salvamont recomanda cel putin 2L la drumetii montane; pentru traseul acesta rezerva urca la ${profile.waterLiters.formatOneDecimal()}L."
            },
            necessity = { GearNecessity.MANDATORY }
        ),
        GearRule(
            id = "food",
            name = "Mancare + rezerva calorica",
            category = CategoryFoodWater,
            weightLabel = { profile ->
                if (profile.isLongDay || profile.difficulty >= TrailDemandLevel.HARD) {
                    "550 g"
                } else {
                    "420 g"
                }
            },
            weightGrams = { profile ->
                if (profile.isLongDay || profile.difficulty >= TrailDemandLevel.HARD) {
                    550
                } else {
                    420
                }
            },
            note = { "NPS si AdventureSmart insista pe aport energetic plus o rezerva separata pentru intarzieri." },
            necessity = { GearNecessity.MANDATORY }
        ),
        GearRule(
            id = "water_treatment",
            name = "Filtru / tablete purificare apa",
            category = CategoryFoodWater,
            weightLabel = { "85 g" },
            weightGrams = { 85 },
            note = { "Util cand sursele de pe traseu nu pot fi verificate sau cand apa transportata nu acopera toata ziua." },
            necessity = {
                when {
                    it.difficulty >= TrailDemandLevel.HARD || it.isRemote -> GearNecessity.RECOMMENDED
                    it.difficulty >= TrailDemandLevel.MEDIUM -> GearNecessity.CONDITIONAL
                    else -> GearNecessity.CONDITIONAL
                }
            }
        ),
        GearRule(
            id = "helmet",
            name = "Casca",
            category = CategoryTechnical,
            weightLabel = { "330 g" },
            weightGrams = { 330 },
            note = { "Casca devine importanta pe grohotis, abrupturi, hornuri si zone cu risc de pietre cazatoare." },
            necessity = {
                when {
                    it.difficulty == TrailDemandLevel.EXPERT -> GearNecessity.MANDATORY
                    it.difficulty == TrailDemandLevel.HARD || it.isSteep -> GearNecessity.RECOMMENDED
                    else -> GearNecessity.CONDITIONAL
                }
            }
        ),
        GearRule(
            id = "traction",
            name = "Microspikes / tractiune suplimentara",
            category = CategoryTechnical,
            weightLabel = { "380 g" },
            weightGrams = { 380 },
            note = { "Conditie de teren, nu regula generala. Devine relevanta pe zapada dura, gheata sau noroi rece." },
            necessity = {
                if (it.difficulty >= TrailDemandLevel.HARD) {
                    GearNecessity.CONDITIONAL
                } else {
                    null
                }
            }
        ),
        GearRule(
            id = "via_ferrata",
            name = "Ham + set via ferrata / coarda",
            category = CategoryTechnical,
            weightLabel = { "1.65 kg" },
            weightGrams = { 1650 },
            note = { "Apare doar pe trasee echipate, expuse sau de alpinism clasic. Verifica descrierea traseului, nu doar dificultatea generala." },
            necessity = {
                if (it.difficulty == TrailDemandLevel.EXPERT) {
                    GearNecessity.CONDITIONAL
                } else {
                    null
                }
            }
        ),
        GearRule(
            id = "satellite",
            name = "Mesager satelitar / baliza PLB",
            category = CategoryTechnical,
            weightLabel = { "120 g" },
            weightGrams = { 120 },
            note = { "Mountain Rescue considera comunicatia de urgenta foarte valoroasa pe ture lungi, izolate sau cu acoperire slaba." },
            necessity = {
                when {
                    it.difficulty == TrailDemandLevel.EXPERT || it.isRemote -> GearNecessity.RECOMMENDED
                    it.difficulty == TrailDemandLevel.HARD -> GearNecessity.CONDITIONAL
                    else -> null
                }
            }
        )
    )

    fun build(
        trail: ActiveTrail?,
        previousItems: List<GearItem> = emptyList()
    ): List<GearItem> {
        val profile = TrailDemandProfile.from(trail)
        val previousPackedState = previousItems.associate { it.id to it.isPacked }

        return gearRules
            .mapNotNull { rule ->
                val necessity = rule.necessity(profile) ?: return@mapNotNull null
                GearItem(
                    id = rule.id,
                    name = rule.name,
                    weight = rule.weightLabel(profile),
                    category = rule.category,
                    weightGrams = rule.weightGrams(profile),
                    necessity = necessity,
                    note = rule.note(profile),
                    isPacked = previousPackedState[rule.id] ?: false
                )
            }
            .sortedWith(
                compareBy<GearItem> { categoryOrder[it.category] ?: Int.MAX_VALUE }
                    .thenBy { it.necessity.sortOrder }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            )
    }

    private fun Double.formatOneDecimal(): String = String.format(Locale.US, "%.1f", this)
}

private data class GearRule(
    val id: String,
    val name: String,
    val category: String,
    val weightLabel: (TrailDemandProfile) -> String,
    val weightGrams: (TrailDemandProfile) -> Int?,
    val note: (TrailDemandProfile) -> String,
    val necessity: (TrailDemandProfile) -> GearNecessity?
)

private enum class TrailDemandLevel {
    EASY,
    MEDIUM,
    HARD,
    EXPERT;

    companion object {
        fun from(rawValue: String?): TrailDemandLevel =
            when (rawValue?.trim()?.uppercase(Locale.ROOT)) {
                "EASY" -> EASY
                "HARD" -> HARD
                "EXPERT" -> EXPERT
                else -> MEDIUM
            }
    }
}

private data class TrailDemandProfile(
    val difficulty: TrailDemandLevel,
    val distanceKm: Double,
    val elevationGain: Int,
    val durationHours: Double,
    val waterLiters: Double,
    val isLongDay: Boolean,
    val isSteep: Boolean,
    val isRemote: Boolean
) {
    companion object {
        fun from(trail: ActiveTrail?): TrailDemandProfile {
            val difficulty = TrailDemandLevel.from(trail?.difficulty)
            val distanceKm = trail?.distanceKm?.takeIf { it > 0 } ?: 10.0
            val elevationGain = trail?.elevationGain?.takeIf { it > 0 } ?: 450
            val durationHours = parseDurationHours(trail?.estimatedDuration)
                ?: estimateDurationHours(distanceKm, elevationGain)
            val isLongDay = durationHours >= 6.0 || distanceKm >= 14.0
            val isSteep = elevationGain >= 900 || (distanceKm > 0 && elevationGain / distanceKm >= 95)
            val isRemote = difficulty == TrailDemandLevel.EXPERT || durationHours >= 7.0 || distanceKm >= 18.0
            val waterLiters = when {
                difficulty == TrailDemandLevel.EXPERT || isRemote -> 2.5
                difficulty == TrailDemandLevel.HARD || isLongDay || elevationGain >= 900 -> 2.0
                else -> 1.5
            }
            return TrailDemandProfile(
                difficulty = difficulty,
                distanceKm = distanceKm,
                elevationGain = elevationGain,
                durationHours = durationHours,
                waterLiters = waterLiters,
                isLongDay = isLongDay,
                isSteep = isSteep,
                isRemote = isRemote
            )
        }

        private fun parseDurationHours(rawValue: String?): Double? {
            if (rawValue.isNullOrBlank()) {
                return null
            }
            val hours = "(\\d+)\\s*h".toRegex(RegexOption.IGNORE_CASE)
                .find(rawValue)
                ?.groupValues
                ?.getOrNull(1)
                ?.toDoubleOrNull()
                ?: 0.0
            val minutes = "(\\d+)\\s*m".toRegex(RegexOption.IGNORE_CASE)
                .find(rawValue)
                ?.groupValues
                ?.getOrNull(1)
                ?.toDoubleOrNull()
                ?: 0.0
            val total = hours + (minutes / 60.0)
            return total.takeIf { it > 0.0 }
        }

        private fun estimateDurationHours(distanceKm: Double, elevationGain: Int): Double =
            (distanceKm / 4.2) + (elevationGain / 500.0)
    }
}

private val GearNecessity.sortOrder: Int
    get() = when (this) {
        GearNecessity.MANDATORY -> 0
        GearNecessity.RECOMMENDED -> 1
        GearNecessity.CONDITIONAL -> 2
    }
