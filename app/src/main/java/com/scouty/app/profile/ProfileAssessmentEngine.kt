package com.scouty.app.profile

import kotlin.math.roundToInt

object ProfileAssessmentEngine {

    const val AgeQuestionId = "age_range"

    val questions: List<ProfileQuestion> = listOf(
        ProfileQuestion(
            id = "hike_frequency",
            title = "Cât de des mergi în drumeții?",
            helper = "Asta îi arată lui Scouty cât de activ ești deja pe trasee.",
            weight = 16,
            options = listOf(
                ProfileOption("rarely", "Rar", "De câteva ori pe an sau mai puțin.", 0),
                ProfileOption("seasonal", "La câteva luni", "Ieși când apare un weekend bun.", 1),
                ProfileOption("monthly", "1-2 ori pe lună", "Drumeția face deja parte din rutină.", 2),
                ProfileOption("weekly", "Aproape săptămânal", "Ajungi pe traseu în majoritatea săptămânilor.", 3),
                ProfileOption("constant", "De mai multe ori pe săptămână", "Traseele sunt ritmul tău normal.", 4)
            )
        ),
        ProfileQuestion(
            id = "max_distance",
            title = "Care este cea mai lungă tură de o zi pe care ai terminat-o?",
            helper = "Alege distanța unei singure zile, nu totalul unei ture de mai multe zile.",
            weight = 14,
            options = listOf(
                ProfileOption("under_5", "Sub 5 km", "Plimbări scurte sau bucle ușoare.", 0),
                ProfileOption("5_10", "5-10 km", "Ture scurte spre medii, de jumătate de zi.", 1),
                ProfileOption("10_15", "10-15 km", "Zona clasică pentru o tură de o zi.", 2),
                ProfileOption("15_20", "15-20 km", "Efort mai lung, cu ritm real.", 3),
                ProfileOption("20_plus", "20+ km", "Zilele lungi sunt deja în plan.", 4)
            )
        ),
        ProfileQuestion(
            id = "physical_condition",
            title = "Cum este condiția ta fizică acum?",
            helper = "Contează forma actuală, nu cel mai bun an al tău.",
            weight = 14,
            options = listOf(
                ProfileOption("restart", "Reîncep ușor", "Vrei să revii treptat și cu grijă.", 0),
                ProfileOption("short", "Bun pentru ture scurte", "Poți ține un ritm stabil în zile mai ușoare.", 1),
                ProfileOption("solid", "Solid", "O zi întreagă de drumeție pare realistă.", 2),
                ProfileOption("strong", "Foarte bun", "Te refaci bine după efort lung.", 3),
                ProfileOption("endurance", "Pregătit pentru anduranță", "Efortul susținut face parte din plan.", 4)
            )
        ),
        ProfileQuestion(
            id = "navigation",
            title = "Cât de sigur ești pe orientare?",
            helper = "Gândește-te la hărți, decizii de traseu, rerutare și calm când poteca devine neclară.",
            weight = 14,
            options = listOf(
                ProfileOption("marked_only", "Doar trasee marcate", "Te bazezi pe marcaje evidente.", 0),
                ProfileOption("basic_map", "Traseu + hartă simplă", "Poți urma indicații de bază pentru rută.", 1),
                ProfileOption("gps_ok", "Folosesc GPS bine", "Navigația pe telefon îți este deja utilă.", 2),
                ProfileOption("map_gps", "Hartă + GPS + rerutare", "Poți corecta mici greșeli de traseu.", 3),
                ProfileOption("independent", "Planific și navighez singur", "Poți construi și urma o rută pe cont propriu.", 4)
            )
        ),
        ProfileQuestion(
            id = "terrain",
            title = "Ce teren poți gestiona controlat?",
            helper = "Alege varianta care încă se simte stabilă, nu haotică.",
            weight = 12,
            options = listOf(
                ProfileOption("forest_road", "Drumuri forestiere", "Poteci late și teren foarte blând.", 0),
                ProfileOption("standard", "Trasee standard", "Traseele marcate normale sunt în regulă.", 1),
                ProfileOption("steep", "Urcări abrupte", "Ești ok cu urcare susținută.", 2),
                ProfileOption("technical_light", "Piatra instabilă / pasaje tehnice", "Rămâi calm pe teren mai dificil.", 3),
                ProfileOption("ridge", "Creste / zone expuse", "Liniile înguste și aeriene sunt gestionabile.", 4)
            )
        ),
        ProfileQuestion(
            id = "conditions",
            title = "În ce condiții ieși totuși pe traseu?",
            helper = "Ajută la estimarea rezistenței și a nivelului de prudență.",
            weight = 10,
            options = listOf(
                ProfileOption("perfect", "Doar vreme perfectă", "Uscat, stabil și ușor de citit.", 0),
                ProfileOption("cool", "Răcoare sau vânt ușor", "Un pic de disconfort este acceptabil.", 1),
                ProfileOption("mixed", "Ploaie sau vânt mai tare", "Poți continua când vremea se strică.", 2),
                ProfileOption("three_season", "Zile mixte trei sezoane", "Vremea schimbătoare de munte intră în calcul.", 3),
                ProfileOption("winter", "Și iarna sau pe zăpadă", "Turele de sezon rece sunt deja în joc.", 4)
            )
        ),
        ProfileQuestion(
            id = "gear_setup",
            title = "Cât de bine este pus la punct echipamentul tău?",
            helper = "Scouty folosește asta pentru recomandări mai bune mai târziu.",
            weight = 8,
            options = listOf(
                ProfileOption("improvise", "Improvizez", "Împachetezi mai mult după instinct.", 0),
                ProfileOption("basics", "Știu baza", "De obicei iei lucrurile esențiale.", 1),
                ProfileOption("checklist", "Am listă", "Urmărești o structură repetabilă.", 2),
                ProfileOption("route_tuned", "Adaptez la traseu", "Ajustezi după distanță, teren și prognoză.", 3),
                ProfileOption("locked_in", "Kitul e stabil", "Ai deja un sistem rafinat.", 4)
            )
        ),
        ProfileQuestion(
            id = "hike_style",
            title = "Ce fel de ture te atrag?",
            helper = "Preferința contează, dar nu trebuie să domine nivelul de start.",
            weight = 6,
            options = listOf(
                ProfileOption("scenic", "Scurt și scenic", "Recompensă rapidă și stres mic.", 0),
                ProfileOption("classic_day", "Ture clasice de o zi", "Plan montan echilibrat, pe o zi întreagă.", 1),
                ProfileOption("long_effort", "Zile lungi de efort", "Îți plac distanța și ritmul susținut.", 2),
                ProfileOption("peaks", "Vârfuri și creste", "Vârfurile și terenul mai ascuțit te atrag.", 3),
                ProfileOption("adventure", "Zile solicitante de aventură", "Obiectivele mari fac parte din plăcere.", 4)
            )
        ),
        ProfileQuestion(
            id = AgeQuestionId,
            title = "Care este intervalul tău de vârstă?",
            helper = "Este folosit pentru context de profil și recomandări, nu ca să îți scadă nivelul de start.",
            weight = 0,
            options = listOf(
                ProfileOption("under_18", "Sub 18", "Energie tânără, motor în creștere.", 0),
                ProfileOption("18_24", "18-24", "Ani cu recuperare rapidă.", 0),
                ProfileOption("25_34", "25-34", "Fereastră puternică pentru drumeții.", 0),
                ProfileOption("35_44", "35-44", "Bază solidă și experiență în amestec.", 0),
                ProfileOption("45_54", "45-54", "Eficiența începe să conteze mai mult.", 0),
                ProfileOption("55_plus", "55+", "Tehnica și ritmul conduc ziua.", 0)
            )
        ),
        ProfileQuestion(
            id = "first_aid",
            title = "Cunoști primul ajutor de bază pe traseu?",
            helper = "Asta crește autonomia pe care Scouty o presupune pentru tine în teren.",
            weight = 6,
            options = listOf(
                ProfileOption("none", "Nu prea", "Preferi să eviți mișcările greșite.", 0),
                ProfileOption("few_basics", "Câteva baze", "Știi ideile generale.", 1),
                ProfileOption("common_issues", "Da, probleme comune", "Poți gestiona situații simple pe traseu.", 3),
                ProfileOption("confident", "Da, cu încredere", "Poți acționa calm în scenarii comune.", 4)
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
        val estimatedTrailStats = estimateTrailStats(draft.answers)
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
            completedHikes = previousProfile?.completedHikes ?: estimatedTrailStats.completedHikes,
            totalDistanceKm = previousProfile?.totalDistanceKm ?: estimatedTrailStats.totalDistanceKm,
            totalElevationGainM = previousProfile?.totalElevationGainM ?: estimatedTrailStats.totalElevationGainM,
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
