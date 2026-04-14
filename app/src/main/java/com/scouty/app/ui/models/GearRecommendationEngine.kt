package com.scouty.app.ui.models

import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

object GearRecommendationEngine {
    private const val CategoryCore = "Baza traseu"
    private const val CategoryHydration = "Apa & hrana"
    private const val CategoryWeather = "Straturi & vreme"
    private const val CategorySafety = "Siguranta & navigatie"
    private const val CategoryKids = "Copii"

    private val categoryOrder = mapOf(
        CategoryCore to 0,
        CategoryHydration to 1,
        CategoryWeather to 2,
        CategorySafety to 3,
        CategoryKids to 4
    )

    fun build(
        trail: ActiveTrail?,
        profile: UserTrailProfile = UserTrailProfile(),
        previousItems: List<GearItem> = emptyList()
    ): List<GearItem> {
        val packingProfile = PackingProfile.from(trail = trail, userProfile = profile)
        val previousPackedState = previousItems.associate { it.id to it.isPacked }
        val generatedItems = linkedMapOf<String, GearItem>()

        generatedItems.register(buildFootwearItem(packingProfile))
        generatedItems.register(buildBackpackItem(packingProfile))
        generatedItems.register(buildPhoneItem(packingProfile))
        generatedItems.register(buildWaterItem(packingProfile))

        if (packingProfile.includeSnacks) {
            generatedItems.register(buildSnackItem(packingProfile))
        }
        if (packingProfile.difficulty == TrailDifficultyRank.EASY) {
            generatedItems.register(
                gearItem(
                    id = "blister_patches",
                    name = "Plasturi pentru bataturi",
                    quickValue = "kit",
                    category = CategorySafety,
                    necessity = GearNecessity.MANDATORY,
                    note = "Pentru frecare si basic blister care.",
                    weightGrams = 20
                )
            )
            generatedItems.register(
                gearItem(
                    id = "trekking_poles",
                    name = "Bete trekking",
                    quickValue = "optional",
                    category = CategorySafety,
                    necessity = GearNecessity.CONDITIONAL,
                    note = "Mai ales daca vrei sa scazi presiunea din genunchi.",
                    weightGrams = 420
                )
            )
        } else {
            generatedItems.register(
                gearItem(
                    id = "headlamp",
                    name = "Lanterna frontala",
                    quickValue = "1 buc",
                    category = CategorySafety,
                    necessity = GearNecessity.MANDATORY,
                    note = "Ramane in rucsac chiar daca pleci dimineata.",
                    weightGrams = 95
                )
            )
            generatedItems.register(
                gearItem(
                    id = "emergency_blanket",
                    name = "Folie de supravietuire",
                    quickValue = "1 buc",
                    category = CategorySafety,
                    necessity = GearNecessity.MANDATORY,
                    note = "Mic item, mare diferenta daca te opresti fortat.",
                    weightGrams = 70
                )
            )
            generatedItems.register(
                gearItem(
                    id = "first_aid",
                    name = "Trusa prim ajutor",
                    quickValue = "1 kit",
                    category = CategorySafety,
                    necessity = GearNecessity.MANDATORY,
                    note = "Fasa, dezinfectant, penseta capuse, plasturi.",
                    weightGrams = 220
                )
            )
            generatedItems.register(
                gearItem(
                    id = "trekking_poles",
                    name = "Bete trekking",
                    quickValue = "opțional",
                    category = CategorySafety,
                    necessity = if (packingProfile.difficulty >= TrailDifficultyRank.HARD || packingProfile.elevationGain >= 900) {
                        GearNecessity.RECOMMENDED
                    } else {
                        GearNecessity.CONDITIONAL
                    },
                    note = "Mai utile pe coborari lungi, radacini si zone inclinate.",
                    weightGrams = 420
                )
            )
            generatedItems.register(
                gearItem(
                    id = "bear_spray",
                    name = "Spray de urs",
                    quickValue = "optional",
                    category = CategorySafety,
                    necessity = GearNecessity.CONDITIONAL,
                    note = "Merita luat pe ture mai lungi sau retrase.",
                    weightGrams = 280
                )
            )
        }

        if (packingProfile.difficulty >= TrailDifficultyRank.HARD) {
            generatedItems.register(
                gearItem(
                    id = "whistle",
                    name = "Fluier de semnalizare",
                    quickValue = "1 buc",
                    category = CategorySafety,
                    necessity = GearNecessity.MANDATORY,
                    note = "Semnalizare rapida in ceata, padure sau accidentare.",
                    weightGrams = 15
                )
            )
            generatedItems.register(
                gearItem(
                    id = "water_filter",
                    name = "Filtru sau tablete apa",
                    quickValue = "optional",
                    category = CategoryHydration,
                    necessity = GearNecessity.CONDITIONAL,
                    note = "Util cand rezerva de apa devine prea grea pentru toata ziua.",
                    weightGrams = 80
                )
            )
        }

        if (packingProfile.difficulty >= TrailDifficultyRank.HARD && packingProfile.isRockyOrExposed) {
            generatedItems.register(
                gearItem(
                    id = "helmet",
                    name = "Casca usoara",
                    quickValue = "optional",
                    category = CategorySafety,
                    necessity = GearNecessity.CONDITIONAL,
                    note = "Foarte utila in zone stancoase expuse.",
                    weightGrams = 320
                )
            )
        }

        when (packingProfile.weatherBand) {
            TrailWeatherBand.HOT -> {
                generatedItems.register(
                    gearItem(
                        id = "heat_kit",
                        name = "Kit soare",
                        quickValue = "SPF",
                        category = CategoryWeather,
                        necessity = GearNecessity.MANDATORY,
                        note = "Tricou tehnic, sapca, ochelari cat. 3, SPF 50.",
                        weightGrams = 260
                    )
                )
                generatedItems.register(
                    gearItem(
                        id = "electrolytes",
                        name = "Electroliti",
                        quickValue = "optional",
                        category = CategoryHydration,
                        necessity = GearNecessity.RECOMMENDED,
                        note = "Ajuta cand transpiri mult si pierzi saruri.",
                        weightGrams = 40
                    )
                )
                if (packingProfile.stormRisk) {
                    generatedItems.register(
                        gearItem(
                            id = "storm_shell",
                            name = "Shell usor de ploaie",
                            quickValue = "1 buc",
                            category = CategoryWeather,
                            necessity = GearNecessity.MANDATORY,
                            note = "Pentru furtuna scurta sau vant tare peste creasta.",
                            weightGrams = 240
                        )
                    )
                }
            }

            TrailWeatherBand.MILD -> {
                generatedItems.register(
                    gearItem(
                        id = "layer_system",
                        name = "Sistem de straturi",
                        quickValue = "3 straturi",
                        category = CategoryWeather,
                        necessity = if (packingProfile.difficulty >= TrailDifficultyRank.MEDIUM || packingProfile.stormRisk) {
                            GearNecessity.MANDATORY
                        } else {
                            GearNecessity.RECOMMENDED
                        },
                        note = "Baza tehnica, polar/fleece si geaca de ploaie-vant.",
                        weightGrams = 760
                    )
                )
                generatedItems.register(
                    gearItem(
                        id = "summit_backup",
                        name = "Micro-puf compact",
                        quickValue = "optional",
                        category = CategoryWeather,
                        necessity = GearNecessity.CONDITIONAL,
                        note = "Pentru varf, pauze lungi sau vant rece.",
                        weightGrams = 300
                    )
                )
                generatedItems.register(
                    gearItem(
                        id = "light_beanie_gloves",
                        name = "Caciula + manusi subtiri",
                        quickValue = "optional",
                        category = CategoryWeather,
                        necessity = GearNecessity.CONDITIONAL,
                        note = "Stau in rucsac pana cand vremea se schimba.",
                        weightGrams = 120
                    )
                )
            }

            TrailWeatherBand.COLD -> {
                generatedItems.register(
                    gearItem(
                        id = "cold_layers",
                        name = "Straturi calde",
                        quickValue = "3 straturi",
                        category = CategoryWeather,
                        necessity = GearNecessity.MANDATORY,
                        note = "Baza tehnica, polar mai serios si shell protector.",
                        weightGrams = 880
                    )
                )
                generatedItems.register(
                    gearItem(
                        id = "micro_puff",
                        name = "Geaca termo compacta",
                        quickValue = "1 buc",
                        category = CategoryWeather,
                        necessity = GearNecessity.MANDATORY,
                        note = "Buna pentru pauze, varf si schimbari rapide de vreme.",
                        weightGrams = 360
                    )
                )
                generatedItems.register(
                    gearItem(
                        id = "cold_accessories",
                        name = "Caciula + manusi",
                        quickValue = "1 set",
                        category = CategoryWeather,
                        necessity = GearNecessity.MANDATORY,
                        note = "Mic volum, dar mare castig termic.",
                        weightGrams = 150
                    )
                )
            }

            TrailWeatherBand.WINTER -> {
                generatedItems.register(
                    gearItem(
                        id = "winter_layers",
                        name = "Straturi de iarna",
                        quickValue = "full kit",
                        category = CategoryWeather,
                        necessity = GearNecessity.MANDATORY,
                        note = "Strat tehnic, polar gros, geaca de puf si shell.",
                        weightGrams = 1250
                    )
                )
                generatedItems.register(
                    gearItem(
                        id = "winter_lower",
                        name = "Pantaloni softshell + sosete merino",
                        quickValue = "1 set",
                        category = CategoryWeather,
                        necessity = GearNecessity.MANDATORY,
                        note = "Pentru frig, vant si umezeala la picior.",
                        weightGrams = 520
                    )
                )
                generatedItems.register(
                    gearItem(
                        id = "snow_accessories",
                        name = "Parazapezi + 2 perechi manusi",
                        quickValue = "1 set",
                        category = CategoryWeather,
                        necessity = GearNecessity.MANDATORY,
                        note = "Te ajuta sa ramai uscat si functional in zapada.",
                        weightGrams = 380
                    )
                )
                generatedItems.register(
                    gearItem(
                        id = "thermos",
                        name = "Termos cu ceai cald",
                        quickValue = "0.7 L",
                        category = CategoryHydration,
                        necessity = GearNecessity.MANDATORY,
                        note = "Caldura rapida si hidratare mai usor de dus in frig.",
                        weightGrams = 900
                    )
                )
                if (packingProfile.difficulty >= TrailDifficultyRank.HARD) {
                    generatedItems.register(
                        gearItem(
                            id = "crampons_axe",
                            name = "Coltari + piolet",
                            quickValue = "tech",
                            category = CategorySafety,
                            necessity = GearNecessity.CONDITIONAL,
                            note = "Pentru gheata sau pante abrupte de iarna.",
                            weightGrams = 1250
                        )
                    )
                }
                if (packingProfile.difficulty == TrailDifficultyRank.EXPERT) {
                    generatedItems.register(
                        gearItem(
                            id = "avalanche_kit",
                            name = "Kit avalansa",
                            quickValue = "DVA",
                            category = CategorySafety,
                            necessity = GearNecessity.CONDITIONAL,
                            note = "Doar pentru teren si risc real de avalansa.",
                            weightGrams = 1900
                        )
                    )
                }
            }
        }

        if (packingProfile.hasChildren) {
            generatedItems.register(
                gearItem(
                    id = "child_footwear",
                    name = "Ghete stabile pentru copii",
                    quickValue = "${packingProfile.children} copii",
                    category = CategoryKids,
                    necessity = GearNecessity.MANDATORY,
                    note = "Fara tenisi pe teren accidentat.",
                    weightGrams = null
                )
            )
            generatedItems.register(
                gearItem(
                    id = "child_change_kit",
                    name = "Haine complete de schimb",
                    quickValue = "${packingProfile.children} set",
                    category = CategoryKids,
                    necessity = GearNecessity.MANDATORY,
                    note = "Inclusiv sosete si strat exterior, puse in pungi impermeabile.",
                    weightGrams = 520 * packingProfile.children
                )
            )
            generatedItems.register(
                gearItem(
                    id = "child_visibility",
                    name = "Vizibilitate ridicata",
                    quickValue = "culori vii",
                    category = CategoryKids,
                    necessity = GearNecessity.MANDATORY,
                    note = "Rosu, galben sau portocaliu ca sa-i vezi imediat.",
                    weightGrams = null
                )
            )
            generatedItems.register(
                gearItem(
                    id = "child_whistle",
                    name = "Fluier pentru copil",
                    quickValue = "${packingProfile.children} buc",
                    category = CategoryKids,
                    necessity = GearNecessity.MANDATORY,
                    note = "Prins de geaca sau de rucsacul lor.",
                    weightGrams = 15 * packingProfile.children
                )
            )
            generatedItems.register(
                gearItem(
                    id = "child_sun",
                    name = "Ochelari buni pentru copii",
                    quickValue = "${packingProfile.children} buc",
                    category = CategoryKids,
                    necessity = GearNecessity.MANDATORY,
                    note = "Ochii lor sunt mai sensibili la UV.",
                    weightGrams = 40 * packingProfile.children
                )
            )
            generatedItems.register(
                gearItem(
                    id = "child_meds",
                    name = "Medicamente pediatrice",
                    quickValue = "recom.",
                    category = CategoryKids,
                    necessity = GearNecessity.RECOMMENDED,
                    note = "Antitermic, plasturi, dezinfectant bland.",
                    weightGrams = 180
                )
            )
            generatedItems.register(
                gearItem(
                    id = "child_morale",
                    name = "Gustari-motivatie",
                    quickValue = "recom.",
                    category = CategoryKids,
                    necessity = GearNecessity.RECOMMENDED,
                    note = "Cateva gustari preferate tin ritmul sus.",
                    weightGrams = 120 * packingProfile.children
                )
            )
            generatedItems.register(
                gearItem(
                    id = "child_carrier",
                    name = "Optiune port-bebe",
                    quickValue = "optional",
                    category = CategoryKids,
                    necessity = GearNecessity.CONDITIONAL,
                    note = "Doar daca unul dintre copii e foarte mic.",
                    weightGrams = 2200
                )
            )
        }

        return generatedItems.values
            .map { item -> item.copy(isPacked = previousPackedState[item.id] ?: false) }
            .sortedWith(
                compareBy<GearItem> { categoryOrder[it.category] ?: Int.MAX_VALUE }
                    .thenBy { it.necessity.sortOrder }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            )
    }

    private fun buildFootwearItem(profile: PackingProfile): GearItem =
        gearItem(
            id = "footwear",
            name = when (profile.difficulty) {
                TrailDifficultyRank.EASY -> "Incaltaminte trail cu talpa profilata"
                TrailDifficultyRank.MEDIUM -> "Bocanci cu suport pentru glezna"
                TrailDifficultyRank.HARD, TrailDifficultyRank.EXPERT -> "Bocanci tehnici cu aderenta buna"
            },
            quickValue = if (profile.adults == 1) "adult" else "${profile.adults} adulti",
            category = CategoryCore,
            necessity = GearNecessity.MANDATORY,
            note = "Modelul stabil se alege dupa dificultatea traseului.",
            weightGrams = null
        )

    private fun buildBackpackItem(profile: PackingProfile): GearItem =
        gearItem(
            id = "backpack",
            name = "Rucsac",
            quickValue = when {
                profile.difficulty >= TrailDifficultyRank.HARD -> "28-35L"
                profile.difficulty >= TrailDifficultyRank.MEDIUM -> "20-28L"
                else -> "15-20L"
            },
            category = CategoryCore,
            necessity = GearNecessity.MANDATORY,
            note = "Volum minim per adult care duce echipament.",
            weightGrams = null
        )

    private fun buildPhoneItem(profile: PackingProfile): GearItem =
        gearItem(
            id = "phone_navigation",
            name = if (profile.difficulty >= TrailDifficultyRank.HARD) {
                "Telefon + baterie externa + harta offline"
            } else {
                "Telefon incarcat 100%"
            },
            quickValue = if (profile.difficulty >= TrailDifficultyRank.HARD) "power" else "100%",
            category = CategorySafety,
            necessity = GearNecessity.MANDATORY,
            note = if (profile.difficulty >= TrailDifficultyRank.HARD) {
                "Navigatie de baza pentru creasta, stanca sau zi lunga."
            } else {
                "Ramane minimul pentru orientare si apel de urgenta."
            },
            weightGrams = if (profile.difficulty >= TrailDifficultyRank.HARD) 260 else 0
        )

    private fun buildWaterItem(profile: PackingProfile): GearItem =
        gearItem(
            id = "water",
            name = "Apa totala",
            quickValue = "${profile.totalWaterLiters.formatOneDecimal()} L",
            category = CategoryHydration,
            necessity = GearNecessity.MANDATORY,
            note = "Calculata pentru ${profile.partySummaryRo.lowercase(Locale.ROOT)}.",
            weightGrams = (profile.totalWaterLiters * 1000).roundToInt()
        )

    private fun buildSnackItem(profile: PackingProfile): GearItem =
        gearItem(
            id = "snacks",
            name = "Hrana calorica",
            quickValue = "${profile.totalFoodGrams} g",
            category = CategoryHydration,
            necessity = GearNecessity.MANDATORY,
            note = "Nuci, batoane, gustari dense pentru toata tura.",
            weightGrams = profile.totalFoodGrams
        )

    private fun gearItem(
        id: String,
        name: String,
        quickValue: String,
        category: String,
        necessity: GearNecessity,
        note: String,
        weightGrams: Int?
    ): GearItem =
        GearItem(
            id = id,
            name = name,
            weight = quickValue,
            category = category,
            weightGrams = weightGrams,
            necessity = necessity,
            note = note
        )

    private fun MutableMap<String, GearItem>.register(item: GearItem) {
        val existing = this[item.id]
        if (existing == null || item.necessity.sortOrder < existing.necessity.sortOrder) {
            this[item.id] = item
        }
    }
}

private data class PackingProfile(
    val difficulty: TrailDifficultyRank,
    val distanceKm: Double,
    val elevationGain: Int,
    val durationHours: Double,
    val adults: Int,
    val children: Int,
    val weatherBand: TrailWeatherBand,
    val stormRisk: Boolean,
    val totalWaterLiters: Double,
    val totalFoodGrams: Int
) {
    val hasChildren: Boolean
        get() = children > 0

    val includeSnacks: Boolean
        get() = durationHours >= 2.5 || difficulty >= TrailDifficultyRank.MEDIUM || hasChildren

    val isRockyOrExposed: Boolean
        get() = difficulty >= TrailDifficultyRank.HARD || elevationGain >= 1200

    val partySummaryRo: String
        get() = TrailPartyComposition(adults = adults, children = children).summaryRo

    companion object {
        fun from(trail: ActiveTrail?, userProfile: UserTrailProfile): PackingProfile {
            val difficulty = TrailDifficultyRank.from(trail?.difficulty ?: userProfile.comfortDifficulty.name)
            val distanceKm = trail?.distanceKm?.takeIf { it > 0.0 }
                ?: (userProfile.maxPreferredHours * 3.6).coerceAtLeast(5.0)
            val elevationGain = trail?.elevationGain?.takeIf { it > 0 }
                ?: (userProfile.maxPreferredElevationGain * 0.7).roundToInt().coerceAtLeast(250)
            val durationHours = TrailMetadataFormatter.parseDurationHours(trail?.estimatedDuration)
                ?: TrailMetadataFormatter.estimateDurationHours(distanceKm, elevationGain)
            val party = trail?.partyComposition ?: TrailPartyComposition()
            val weatherBand = resolveWeatherBand(trail)
            val stormRisk = TrailMetadataFormatter.hasStormRisk(trail?.weatherForecast)
            val adultWaterLiters = when {
                difficulty >= TrailDifficultyRank.HARD || durationHours >= 8.0 -> 3.0
                difficulty >= TrailDifficultyRank.MEDIUM || durationHours >= 5.0 -> 2.6
                else -> 1.3
            } + when (weatherBand) {
                TrailWeatherBand.HOT -> 0.5
                TrailWeatherBand.COLD -> 0.1
                TrailWeatherBand.WINTER -> 0.0
                TrailWeatherBand.MILD -> 0.2
            }
            val childWaterLiters = adultWaterLiters * 0.6
            val totalWaterLiters = (
                party.adults.coerceAtLeast(1) * adultWaterLiters +
                    party.children * childWaterLiters
                ).coerceAtLeast(adultWaterLiters)

            val adultFoodGrams = when {
                difficulty >= TrailDifficultyRank.HARD || durationHours >= 8.0 -> 520
                difficulty >= TrailDifficultyRank.MEDIUM || durationHours >= 5.0 -> 380
                durationHours >= 2.5 -> 220
                else -> 120
            }
            val childFoodGrams = (adultFoodGrams * 0.6).roundToInt()
            val totalFoodGrams = (
                party.adults.coerceAtLeast(1) * adultFoodGrams +
                    party.children * childFoodGrams
                ).coerceAtLeast(adultFoodGrams)

            return PackingProfile(
                difficulty = difficulty,
                distanceKm = distanceKm,
                elevationGain = elevationGain,
                durationHours = durationHours,
                adults = party.adults.coerceAtLeast(1),
                children = party.children.coerceAtLeast(0),
                weatherBand = weatherBand,
                stormRisk = stormRisk,
                totalWaterLiters = (totalWaterLiters * 10).roundToInt() / 10.0,
                totalFoodGrams = totalFoodGrams
            )
        }

        private fun resolveWeatherBand(trail: ActiveTrail?): TrailWeatherBand {
            val summary = trail?.weatherForecast
            val normalizedSummary = summary?.lowercase(Locale.ROOT).orEmpty()
            val month = trail?.date?.get(Calendar.MONTH) ?: Calendar.getInstance().get(Calendar.MONTH)
            val temperature = TrailMetadataFormatter.parseTemperatureC(summary)
            val winterMonths = setOf(Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY)
            val coldMonths = winterMonths + setOf(Calendar.NOVEMBER, Calendar.MARCH)
            val hasSnowSignals = listOf("snow", "zapada", "zăpadă", "gheata", "gheață", "ice").any {
                it in normalizedSummary
            }

            return when {
                hasSnowSignals || temperature != null && temperature <= 2.0 || month in winterMonths -> TrailWeatherBand.WINTER
                temperature != null && temperature <= 8.0 || month in coldMonths -> TrailWeatherBand.COLD
                TrailMetadataFormatter.isLikelyHot(summary) || temperature != null && temperature >= 24.0 -> TrailWeatherBand.HOT
                else -> TrailWeatherBand.MILD
            }
        }
    }
}

private enum class TrailWeatherBand {
    HOT,
    MILD,
    COLD,
    WINTER
}

private val GearNecessity.sortOrder: Int
    get() = when (this) {
        GearNecessity.MANDATORY -> 0
        GearNecessity.RECOMMENDED -> 1
        GearNecessity.CONDITIONAL -> 2
    }

private fun Double.formatOneDecimal(): String = String.format(Locale.US, "%.1f", this)
