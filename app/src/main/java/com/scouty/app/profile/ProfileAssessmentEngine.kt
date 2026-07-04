package com.scouty.app.profile

import kotlin.math.roundToInt

object ProfileAssessmentEngine {

    const val AgeQuestionId = "age_range"

    val questions: List<ProfileQuestion> = listOf(
        ProfileQuestion(
            id = "hike_frequency",
            title = "Cât de des ajungi pe munte?",
            helper = "Ne ajută să vedem cât de experimentat ești deja pe trasee.",
            weight = 16,
            options = listOf(
                ProfileOption("rarely", "Rar", "Câteva ieșiri pe an, cel mult.", 0),
                ProfileOption("seasonal", "Din când în când", "Când prind un weekend liber și vreme bună.", 1),
                ProfileOption("monthly", "O dată-două pe lună", "Muntele e deja parte din rutina mea.", 2),
                ProfileOption("weekly", "Aproape săptămânal", "Rar trece o săptămână fără o tură.", 3),
                ProfileOption("constant", "De mai multe ori pe săptămână", "Practic trăiesc pe trasee.", 4)
            )
        ),
        ProfileQuestion(
            id = "max_distance",
            title = "Cea mai lungă tură de o zi pe care ai dus-o până la capăt?",
            helper = "Distanța totală dus-întors, dintr-o singură zi de mers (nu doar până în vârf).",
            weight = 14,
            options = listOf(
                ProfileOption("under_5", "Sub 5 km", "O plimbare scurtă și lejeră.", 0),
                ProfileOption("5_10", "5-10 km", "O tură de-o jumătate de zi.", 1),
                ProfileOption("10_15", "10-15 km", "Clasica tură de o zi.", 2),
                ProfileOption("15_20", "15-20 km", "O zi lungă, cu ceva ritm.", 3),
                ProfileOption("20_plus", "Peste 20 km", "Zilele lungi nu mă sperie.", 4)
            )
        ),
        ProfileQuestion(
            id = "physical_condition",
            title = "Cum stai cu condiția fizică acum?",
            helper = "Cum te simți în perioada asta, nu în cea mai bună formă a ta.",
            weight = 14,
            options = listOf(
                ProfileOption("restart", "O iau ușor", "Vreau să revin treptat, fără să forțez.", 0),
                ProfileOption("short", "Bună pentru ture scurte", "Țin un ritm ok pe distanțe mici.", 1),
                ProfileOption("solid", "Solidă", "O zi întreagă pe munte nu-i o problemă.", 2),
                ProfileOption("strong", "Foarte bună", "Mă refac repede după un efort lung.", 3),
                ProfileOption("endurance", "De anduranță", "Efortul susținut e terenul meu.", 4)
            )
        ),
        ProfileQuestion(
            id = "navigation",
            title = "Cât de bine te orientezi pe munte?",
            helper = "Gândește-te la hărți, la GPS și la cât de calm rămâi când poteca nu mai e clară.",
            weight = 14,
            options = listOf(
                ProfileOption("marked_only", "Doar după marcaje", "Merg pe traseu marcat și după indicatoare.", 0),
                ProfileOption("gps_ok", "Cu harta și GPS-ul din aplicație", "Mă uit pe telefon și văd unde sunt.", 1),
                ProfileOption("basic_map", "Citesc harta și înțeleg traseul", "Îmi dau seama de distanțe, urcușuri, direcție.", 2),
                ProfileOption("map_gps", "Mă descurc și fără marcaje", "Dacă dispar semnele, tot găsesc drumul.", 3),
                ProfileOption("independent", "Îmi planific singur traseul", "Hartă, busolă, GPS — merg și pe unde nu-s poteci.", 4)
            )
        ),
        ProfileQuestion(
            id = "terrain",
            title = "Cu ce fel de teren te simți stăpân pe situație?",
            helper = "Alege ce încă ți se pare sigur, nu ce te-ar scoate din zona de confort.",
            weight = 12,
            options = listOf(
                ProfileOption("forest_road", "Teren plan", "Drumuri largi, poteci line, fără bătăi de cap.", 0),
                ProfileOption("standard", "Poteci normale, cu ceva urcuș", "Traseele marcate obișnuite.", 1),
                ProfileOption("steep", "Urcușuri lungi și abrupte", "Nu mă sperie o pantă serioasă.", 2),
                ProfileOption("technical_light", "Teren stâncos și accidentat", "Rămân calm pe piatră instabilă și pasaje tehnice.", 3),
                ProfileOption("ridge", "Cățărări", "Trasee care cer escaladă propriu-zisă, cu pasaje expuse.", 4)
            )
        ),
        ProfileQuestion(
            id = "conditions",
            title = "Cum te descurci cu vremea?",
            helper = "Ne ajută să știm cât de departe poți merge când nu e soare.",
            weight = 10,
            options = listOf(
                ProfileOption("perfect", "Doar pe vreme bună", "Uscat, senin, plăcut.", 0),
                ProfileOption("cool", "Puțină ploaie sau vânt nu mă sperie", "Un pic de disconfort e ok.", 1),
                ProfileOption("mixed", "Continui și când se strică serios", "Ploaie, vânt puternic — merg mai departe.", 2),
                ProfileOption("three_season", "Frig, ceață și ploaie rece", "Le-am prins pe toate.", 3),
                ProfileOption("winter", "Zăpadă, viscol, ger", "Vremea nu mă oprește!", 4)
            )
        ),
        ProfileQuestion(
            id = "gear_setup",
            title = "Cât de bine îți dai seama ce echipament îți trebuie pe un traseu?",
            helper = "Ne ajută să-ți dăm recomandări mai bune mai târziu.",
            weight = 8,
            options = listOf(
                ProfileOption("improvise", "Improvizez", "Iau ce-mi pică în mână și sper că-i bine.", 0),
                ProfileOption("basics", "Știu esențialul", "Apă, un strat în plus, încălțări bune.", 1),
                ProfileOption("checklist", "Merg pe o listă", "Am o rutină și o urmez.", 2),
                ProfileOption("route_tuned", "Adaptez la traseu", "Aleg după distanță, teren și vreme.", 3),
                ProfileOption("locked_in", "Am totul pus la punct", "Știu exact ce-mi trebuie, până la ultimul detaliu.", 4)
            )
        ),
        ProfileQuestion(
            id = "hike_style",
            title = "Ce fel de ture te atrag cel mai mult?",
            helper = "Preferința ta contează, dar nu-ți stabilește ea nivelul.",
            weight = 6,
            options = listOf(
                ProfileOption("scenic", "Scurte și cu priveliște", "Răsplată rapidă, fără bătăi de cap.", 0),
                ProfileOption("classic_day", "Ture clasice de o zi", "Echilibrate, cât o zi întreagă.", 1),
                ProfileOption("long_effort", "Zile lungi, cu efort", "Îmi place distanța și ritmul.", 2),
                ProfileOption("peaks", "Vârfuri și creste", "Mă cheamă înălțimile.", 3),
                ProfileOption("adventure", "Aventuri solicitante", "Cu cât e mai greu, cu atât îmi place mai mult.", 4)
            )
        ),
        ProfileQuestion(
            id = AgeQuestionId,
            title = "Ce vârstă ai?",
            helper = "Doar pentru context și recomandări — nu-ți scade nivelul.",
            weight = 0,
            options = listOf(
                ProfileOption("under_18", "Sub 18", "", 0),
                ProfileOption("18_24", "18-24", "", 0),
                ProfileOption("25_34", "25-34", "", 0),
                ProfileOption("35_44", "35-44", "", 0),
                ProfileOption("45_54", "45-54", "", 0),
                ProfileOption("55_plus", "55+", "", 0)
            )
        ),
        ProfileQuestion(
            id = "first_aid",
            title = "Cunoști primul ajutor?",
            helper = "Ne ajută să știm cât de autonom te putem lăsa în teren.",
            weight = 6,
            options = listOf(
                ProfileOption("none", "Nu prea", "Prefer să nu risc mișcări greșite.", 0),
                ProfileOption("few_basics", "Câteva noțiuni de bază", "Știu ideea generală.", 1),
                ProfileOption("common_issues", "Da, situații comune", "Mă descurc cu ce apare des pe traseu.", 3),
                ProfileOption("confident", "Da, cu încredere", "Pot acționa calm la nevoie.", 4)
            )
        )
    )

    private val questionsById = questions.associateBy(ProfileQuestion::id)

    fun evaluate(answers: Map<String, String>): AssessmentResult {
        val weightedScore = questions.fold(0.0) { total, question ->
            val optionId = answers[question.id] ?: return@fold total
            val option = question.options.firstOrNull { it.id == optionId } ?: return@fold total
            total + if (question.weight == 0) {
                0.0
            } else {
                (question.weight * (option.score / 4.0))
            }
        }
        val normalized = weightedScore.toInt().coerceIn(0, 100)
        val starterLevel = when {
            normalized >= 70 -> ScoutyLevel.LEVEL_3
            normalized >= 38 -> ScoutyLevel.LEVEL_2
            else -> ScoutyLevel.LEVEL_1
        }
        return AssessmentResult(
            score = normalized,
            starterLevel = starterLevel
        )
    }

    fun buildProfile(
        email: String,
        draft: OnboardingDraft,
        createdAtEpochMillis: Long,
        previousProfile: UserProfile? = null
    ): UserProfile {
        val result = evaluate(draft.answers)
        val now = createdAtEpochMillis
        val level = previousProfile?.let(ProfileProgressionEngine::currentLevel) ?: result.starterLevel
        return UserProfile(
            email = email,
            displayName = draft.displayName.trim(),
            avatarId = draft.avatarId,
            homeRegion = draft.homeRegion.trim(),
            levelNumber = level.number,
            levelTitle = level.title,
            onboardingScore = previousProfile?.onboardingScore ?: result.score,
            experiencePoints = previousProfile?.experiencePoints ?: ProfileProgressionEngine.starterExperience(result.score),
            completedHikes = previousProfile?.completedHikes ?: 0,
            totalDistanceKm = previousProfile?.totalDistanceKm ?: 0.0,
            totalElevationGainM = previousProfile?.totalElevationGainM ?: 0,
            trailHistory = previousProfile?.trailHistory ?: emptyList(),
            unlockedAchievements = previousProfile?.unlockedAchievements ?: emptyList(),
            createdAtEpochMillis = previousProfile?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
            answers = draft.answers
        )
    }

    fun allQuestionsAnswered(answers: Map<String, String>): Boolean =
        questions.all { answers[it.id] != null }

    fun findQuestion(questionId: String): ProfileQuestion? = questionsById[questionId]

    fun answerLabel(questionId: String, optionId: String?): String? {
        val question = questionsById[questionId] ?: return null
        return question.options.firstOrNull { it.id == optionId }?.label
    }

    fun estimateTrailStats(answers: Map<String, String>): TrailStatsSummary {
        val completedHikes = when (answers["hike_frequency"]) {
            "rarely" -> 2
            "seasonal" -> 5
            "monthly" -> 12
            "weekly" -> 24
            "constant" -> 40
            else -> 0
        }

        val averageDistanceKm = when (answers["max_distance"]) {
            "under_5" -> 4.0
            "5_10" -> 7.5
            "10_15" -> 12.0
            "15_20" -> 17.0
            "20_plus" -> 22.0
            else -> 0.0
        }

        val distanceMultiplier = when (answers["hike_style"]) {
            "scenic" -> 0.82
            "classic_day" -> 1.0
            "long_effort" -> 1.14
            "peaks" -> 1.08
            "adventure" -> 1.2
            else -> 1.0
        }

        val averageElevationGainM = when (answers["terrain"]) {
            "forest_road" -> 120
            "standard" -> 240
            "steep" -> 430
            "technical_light" -> 680
            "ridge" -> 900
            else -> 150
        }

        val elevationMultiplier = when (answers["conditions"]) {
            "perfect" -> 0.92
            "cool" -> 1.0
            "mixed" -> 1.06
            "three_season" -> 1.12
            "winter" -> 1.18
            else -> 1.0
        }

        return TrailStatsSummary(
            completedHikes = completedHikes,
            totalDistanceKm = (completedHikes * averageDistanceKm * distanceMultiplier * 10.0).roundToInt() / 10.0,
            totalElevationGainM = (completedHikes * averageElevationGainM * elevationMultiplier).roundToInt()
        )
    }
}
