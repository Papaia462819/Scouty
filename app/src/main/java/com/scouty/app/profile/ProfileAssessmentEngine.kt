package com.scouty.app.profile

import kotlin.math.roundToInt

object ProfileAssessmentEngine {

    const val AgeQuestionId = "age_range"

    val questions: List<ProfileQuestion> = listOf(
        ProfileQuestion(
            id = "hike_frequency",
            title = "How often do you hike?",
            helper = "This tells Scouty how active you already are on trails.",
            weight = 16,
            options = listOf(
                ProfileOption("rarely", "Rarely", "A couple of times a year or less.", 0),
                ProfileOption("seasonal", "Every few months", "You go when a good weekend shows up.", 1),
                ProfileOption("monthly", "1-2 times a month", "Hiking is already part of your routine.", 2),
                ProfileOption("weekly", "Nearly weekly", "You head out most weeks.", 3),
                ProfileOption("constant", "Multiple times a week", "Trails are part of your default rhythm.", 4)
            )
        ),
        ProfileQuestion(
            id = "max_distance",
            title = "What's the longest day hike you've completed?",
            helper = "Use the distance of a single day outing, not a multi-day total.",
            weight = 14,
            options = listOf(
                ProfileOption("under_5", "Under 5 km", "Mostly short walks or scenic loops.", 0),
                ProfileOption("5_10", "5-10 km", "Short to medium half-day routes.", 1),
                ProfileOption("10_15", "10-15 km", "Classic day-hike territory.", 2),
                ProfileOption("15_20", "15-20 km", "Longer efforts with real pacing.", 3),
                ProfileOption("20_plus", "20+ km", "Big days are already on the table.", 4)
            )
        ),
        ProfileQuestion(
            id = "physical_condition",
            title = "How is your physical condition right now?",
            helper = "This reflects your current shape, not your best year ever.",
            weight = 14,
            options = listOf(
                ProfileOption("restart", "Restarting gently", "You want to build back up carefully.", 0),
                ProfileOption("short", "Good for short hikes", "You can hold a steady pace for lighter days.", 1),
                ProfileOption("solid", "Solid", "A full hiking day feels realistic.", 2),
                ProfileOption("strong", "Very strong", "You recover well from long effort.", 3),
                ProfileOption("endurance", "Endurance-ready", "Long sustained output is part of the plan.", 4)
            )
        ),
        ProfileQuestion(
            id = "navigation",
            title = "How confident are you with navigation?",
            helper = "Think maps, route decisions, rerouting, and staying calm when trails get messy.",
            weight = 14,
            options = listOf(
                ProfileOption("marked_only", "Only marked routes", "You rely on obvious trail guidance.", 0),
                ProfileOption("basic_map", "Trail + simple map", "You can follow basic route aids.", 1),
                ProfileOption("gps_ok", "I can use GPS well", "Phone navigation already feels useful.", 2),
                ProfileOption("map_gps", "Map + GPS + rerouting", "You can recover from small mistakes.", 3),
                ProfileOption("independent", "Plan and navigate solo", "You can build and run a route on your own.", 4)
            )
        ),
        ProfileQuestion(
            id = "terrain",
            title = "What terrain can you handle?",
            helper = "Choose the option that still feels controlled, not chaotic.",
            weight = 12,
            options = listOf(
                ProfileOption("forest_road", "Forest roads", "Wide paths and very mellow terrain.", 0),
                ProfileOption("standard", "Standard trails", "Regular marked trails are fine.", 1),
                ProfileOption("steep", "Steep trails", "You are okay with sustained climbing.", 2),
                ProfileOption("technical_light", "Loose rock / technical bits", "You can stay composed on trickier footing.", 3),
                ProfileOption("ridge", "Ridges / exposed sections", "Narrow lines and airy terrain are manageable.", 4)
            )
        ),
        ProfileQuestion(
            id = "conditions",
            title = "In what conditions do you still go out?",
            helper = "This helps estimate both resilience and caution level.",
            weight = 10,
            options = listOf(
                ProfileOption("perfect", "Only perfect weather", "Dry, stable, and easy to read.", 0),
                ProfileOption("cool", "Cool or light wind", "Some discomfort is fine.", 1),
                ProfileOption("mixed", "Rain or stronger wind", "You can still move when conditions turn messy.", 2),
                ProfileOption("three_season", "Three-season mixed days", "Changing mountain weather is part of the deal.", 3),
                ProfileOption("winter", "Winter or snow as well", "Cold season outings are already in play.", 4)
            )
        ),
        ProfileQuestion(
            id = "gear_setup",
            title = "How dialed-in is your gear setup?",
            helper = "Scouty will use this to tune future recommendations.",
            weight = 8,
            options = listOf(
                ProfileOption("improvise", "I improvise", "You mostly pack by feel.", 0),
                ProfileOption("basics", "I know the basics", "You usually remember the essential items.", 1),
                ProfileOption("checklist", "I keep a checklist", "You follow a repeatable base setup.", 2),
                ProfileOption("route_tuned", "I tune gear to the route", "You adapt to distance, terrain, and forecast.", 3),
                ProfileOption("locked_in", "My kit is locked in", "You have a refined system already.", 4)
            )
        ),
        ProfileQuestion(
            id = "hike_style",
            title = "What kind of hikes pull you in?",
            helper = "Preference matters, but it should not overpower your skill score.",
            weight = 6,
            options = listOf(
                ProfileOption("scenic", "Short and scenic", "Easy payoff and low stress.", 0),
                ProfileOption("classic_day", "Classic day hikes", "A balanced full-day mountain plan.", 1),
                ProfileOption("long_effort", "Long effort days", "You enjoy distance and sustained pace.", 2),
                ProfileOption("peaks", "Peaks and ridges", "Summits and sharper terrain are the draw.", 3),
                ProfileOption("adventure", "Demanding adventure days", "Big objectives are part of the fun.", 4)
            )
        ),
        ProfileQuestion(
            id = AgeQuestionId,
            title = "What's your age range?",
            helper = "Used for profile context and future recommendations, not to lower your starter tier.",
            weight = 0,
            options = listOf(
                ProfileOption("under_18", "Under 18", "Young legs, growing engine.", 0),
                ProfileOption("18_24", "18-24", "Fast recovery years.", 0),
                ProfileOption("25_34", "25-34", "Strong prime hiking window.", 0),
                ProfileOption("35_44", "35-44", "Solid base and experience mix.", 0),
                ProfileOption("45_54", "45-54", "Efficiency starts to matter more.", 0),
                ProfileOption("55_plus", "55+", "Technique and pacing lead the day.", 0)
            )
        ),
        ProfileQuestion(
            id = "first_aid",
            title = "Do you know basic trail first aid?",
            helper = "This boosts how much autonomy Scouty assumes for you in the field.",
            weight = 6,
            options = listOf(
                ProfileOption("none", "Not really", "You would rather avoid making the wrong move.", 0),
                ProfileOption("few_basics", "A few basics", "You know the broad ideas.", 1),
                ProfileOption("common_issues", "Common issues, yes", "You can handle simple trail problems.", 3),
                ProfileOption("confident", "Yes, confidently", "You can act calmly on common scenarios.", 4)
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
