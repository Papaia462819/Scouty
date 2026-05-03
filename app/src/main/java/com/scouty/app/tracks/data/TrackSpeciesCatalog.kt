package com.scouty.app.tracks.data

data class TrackSpecies(
    val id: String,
    val romanianName: String,
    val scientificName: String,
    val safetyLevel: TrackSafetyLevel,
    val features: List<String>,
)

enum class TrackSafetyLevel {
    NORMAL,
    CAUTION,
    DANGER,
}

object TrackSpeciesCatalog {
    private val species = listOf(
        TrackSpecies(
            id = "bear_brown",
            romanianName = "Urs brun",
            scientificName = "Ursus arctos",
            safetyLevel = TrackSafetyLevel.DANGER,
            features = listOf("urma mare si lata", "degete dispuse arcuit", "gheare vizibile in substrat moale"),
        ),
        TrackSpecies(
            id = "wolf_gray",
            romanianName = "Lup cenusiu",
            scientificName = "Canis lupus",
            safetyLevel = TrackSafetyLevel.DANGER,
            features = listOf("urma ovala", "degete stranse", "gheare fine orientate inainte"),
        ),
        TrackSpecies(
            id = "lynx_eurasian",
            romanianName = "Ras eurasiatic",
            scientificName = "Lynx lynx",
            safetyLevel = TrackSafetyLevel.DANGER,
            features = listOf("urma rotunda", "gheare rareori imprimate", "pernuta centrala lata"),
        ),
        TrackSpecies(
            id = "red_deer",
            romanianName = "Cerb carpatin",
            scientificName = "Cervus elaphus",
            safetyLevel = TrackSafetyLevel.NORMAL,
            features = listOf("doua copite alungite", "varfuri relativ ascutite", "urma simetrica pe teren moale"),
        ),
        TrackSpecies(
            id = "wild_boar",
            romanianName = "Mistret",
            scientificName = "Sus scrofa",
            safetyLevel = TrackSafetyLevel.CAUTION,
            features = listOf("copite mai rotunjite", "pintenii pot aparea lateral", "urma mai lata decat la cerb"),
        ),
        TrackSpecies(
            id = "red_fox",
            romanianName = "Vulpe roscata",
            scientificName = "Vulpes vulpes",
            safetyLevel = TrackSafetyLevel.NORMAL,
            features = listOf("urma mica de canid", "forma ovala ingusta", "pas adesea aliniat"),
        ),
        TrackSpecies(
            id = "dog_domestic",
            romanianName = "Caine domestic",
            scientificName = "Canis familiaris",
            safetyLevel = TrackSafetyLevel.NORMAL,
            features = listOf("forma foarte variabila", "degete mai rasfirate", "confuzor principal pentru lup"),
        ),
    )

    val all: List<TrackSpecies> = species

    fun find(id: String): TrackSpecies? = species.firstOrNull { it.id == id }
}
