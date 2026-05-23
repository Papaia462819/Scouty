package com.scouty.app.profile

import kotlin.math.roundToInt

data class LevelMilestone(
    val level: ScoutyLevel,
    val startPoints: Int,
    val nextLevelPoints: Int? = null
)

data class LevelProgressSnapshot(
    val level: ScoutyLevel,
    val totalPoints: Int,
    val currentPoints: Int,
    val pointsRequired: Int,
    val pointsRemaining: Int,
    val progressFraction: Float,
    val nextLevel: ScoutyLevel? = null
)

enum class AchievementCategory {
    COMPLETION,
    DISTANCE,
    ELEVATION,
    DIFFICULTY,
    EXPLORATION,
    PREP
}

data class AchievementDefinition(
    val id: String,
    val title: String,
    val points: Int,
    val category: AchievementCategory,
    val isUnlocked: (AchievementStats) -> Boolean
)

data class AchievementProgress(
    val definition: AchievementDefinition,
    val unlockedRecord: UnlockedAchievementRecord?
)

data class TrailRewardInput(
    val distanceKm: Double,
    val elevationGainM: Int,
    val difficulty: String,
    val region: String,
    val gearReady: Boolean,
    val outcome: ProfileTrailOutcome
)

data class AchievementStats(
    val completedTrailCount: Int,
    val totalDistanceKm: Double,
    val totalElevationGainM: Int,
    val easyTrailCount: Int,
    val mediumTrailCount: Int,
    val hardTrailCount: Int,
    val completedRegionCount: Int,
    val gearReadyFinishCount: Int
)

object ProfileProgressionEngine {

    val milestones: List<LevelMilestone> = listOf(
        LevelMilestone(ScoutyLevel.LEVEL_1, startPoints = 0, nextLevelPoints = 150),
        LevelMilestone(ScoutyLevel.LEVEL_2, startPoints = 150, nextLevelPoints = 350),
        LevelMilestone(ScoutyLevel.LEVEL_3, startPoints = 350, nextLevelPoints = 650),
        LevelMilestone(ScoutyLevel.LEVEL_4, startPoints = 650, nextLevelPoints = 1050),
        LevelMilestone(ScoutyLevel.LEVEL_5, startPoints = 1050, nextLevelPoints = 1550),
        LevelMilestone(ScoutyLevel.LEVEL_6, startPoints = 1550, nextLevelPoints = 2150),
        LevelMilestone(ScoutyLevel.LEVEL_7, startPoints = 2150, nextLevelPoints = 2850),
        LevelMilestone(ScoutyLevel.LEVEL_8, startPoints = 2850, nextLevelPoints = 3650),
        LevelMilestone(ScoutyLevel.LEVEL_9, startPoints = 3650, nextLevelPoints = 4600),
        LevelMilestone(ScoutyLevel.LEVEL_10, startPoints = 4600, nextLevelPoints = null)
    )

    val achievementDefinitions: List<AchievementDefinition> = listOf(
        AchievementDefinition("first_trail", "Prima tură", 40, AchievementCategory.COMPLETION) {
            it.completedTrailCount >= 1
        },
        AchievementDefinition("5_trails", "5 ture finalizate", 80, AchievementCategory.COMPLETION) {
            it.completedTrailCount >= 5
        },
        AchievementDefinition("10_trails", "10 ture finalizate", 140, AchievementCategory.COMPLETION) {
            it.completedTrailCount >= 10
        },
        AchievementDefinition("25_trails", "25 ture finalizate", 240, AchievementCategory.COMPLETION) {
            it.completedTrailCount >= 25
        },
        AchievementDefinition("10km_total", "10 km parcurși", 30, AchievementCategory.DISTANCE) {
            it.totalDistanceKm >= 10.0
        },
        AchievementDefinition("25km_total", "25 km parcurși", 50, AchievementCategory.DISTANCE) {
            it.totalDistanceKm >= 25.0
        },
        AchievementDefinition("50km_total", "50 km parcurși", 80, AchievementCategory.DISTANCE) {
            it.totalDistanceKm >= 50.0
        },
        AchievementDefinition("100km_total", "100 km parcurși", 130, AchievementCategory.DISTANCE) {
            it.totalDistanceKm >= 100.0
        },
        AchievementDefinition("250km_total", "250 km parcurși", 220, AchievementCategory.DISTANCE) {
            it.totalDistanceKm >= 250.0
        },
        AchievementDefinition("500m_gain", "500 m urcați", 25, AchievementCategory.ELEVATION) {
            it.totalElevationGainM >= 500
        },
        AchievementDefinition("1000m_gain", "1.000 m urcați", 50, AchievementCategory.ELEVATION) {
            it.totalElevationGainM >= 1_000
        },
        AchievementDefinition("5000m_gain", "5.000 m urcați", 130, AchievementCategory.ELEVATION) {
            it.totalElevationGainM >= 5_000
        },
        AchievementDefinition("10000m_gain", "10.000 m urcați", 220, AchievementCategory.ELEVATION) {
            it.totalElevationGainM >= 10_000
        },
        AchievementDefinition("first_easy", "Primul traseu ușor", 30, AchievementCategory.DIFFICULTY) {
            it.easyTrailCount >= 1
        },
        AchievementDefinition("5_easy", "5 trasee ușoare", 70, AchievementCategory.DIFFICULTY) {
            it.easyTrailCount >= 5
        },
        AchievementDefinition("10_easy", "10 trasee ușoare", 120, AchievementCategory.DIFFICULTY) {
            it.easyTrailCount >= 10
        },
        AchievementDefinition("first_medium", "Primul traseu mediu", 45, AchievementCategory.DIFFICULTY) {
            it.mediumTrailCount >= 1
        },
        AchievementDefinition("3_medium", "3 trasee medii", 90, AchievementCategory.DIFFICULTY) {
            it.mediumTrailCount >= 3
        },
        AchievementDefinition("5_medium", "5 trasee medii", 140, AchievementCategory.DIFFICULTY) {
            it.mediumTrailCount >= 5
        },
        AchievementDefinition("first_hard", "Primul traseu dificil", 70, AchievementCategory.DIFFICULTY) {
            it.hardTrailCount >= 1
        },
        AchievementDefinition("3_hard", "3 trasee dificile", 140, AchievementCategory.DIFFICULTY) {
            it.hardTrailCount >= 3
        },
        AchievementDefinition("5_hard", "5 trasee dificile", 220, AchievementCategory.DIFFICULTY) {
            it.hardTrailCount >= 5
        },
        AchievementDefinition("first_new_region", "Prima regiune finalizată", 50, AchievementCategory.EXPLORATION) {
            it.completedRegionCount >= 1
        },
        AchievementDefinition("3_regions", "3 regiuni finalizate", 120, AchievementCategory.EXPLORATION) {
            it.completedRegionCount >= 3
        },
        AchievementDefinition("gear_ready_finish", "Tură cu echipament complet", 40, AchievementCategory.PREP) {
            it.gearReadyFinishCount >= 1
        }
    )

    fun currentLevel(profile: UserProfile): ScoutyLevel =
        levelForExperience(effectiveExperience(profile))

    fun effectiveExperience(profile: UserProfile): Int =
        if (profile.experiencePoints > 0) {
            profile.experiencePoints
        } else {
            starterExperience(profile.onboardingScore)
        }

    fun levelForExperience(points: Int): ScoutyLevel =
        milestones.lastOrNull { points >= it.startPoints }?.level ?: ScoutyLevel.LEVEL_1

    fun milestoneFor(level: ScoutyLevel): LevelMilestone =
        milestones.firstOrNull { it.level == level } ?: milestones.first()

    fun progress(profile: UserProfile): LevelProgressSnapshot {
        val totalPoints = effectiveExperience(profile)
        val level = currentLevel(profile)
        val milestone = milestoneFor(level)
        val nextLevel = milestone.nextLevelPoints?.let { levelForExperience(it) }
        val pointsRequired = (milestone.nextLevelPoints ?: milestone.startPoints) - milestone.startPoints
        val currentPoints = (totalPoints - milestone.startPoints).coerceAtLeast(0)
        val pointsRemaining = (milestone.nextLevelPoints?.minus(totalPoints) ?: 0).coerceAtLeast(0)
        val progressFraction = when {
            milestone.nextLevelPoints == null -> 1f
            pointsRequired <= 0 -> 0f
            else -> (currentPoints.toFloat() / pointsRequired.toFloat()).coerceIn(0f, 1f)
        }
        return LevelProgressSnapshot(
            level = level,
            totalPoints = totalPoints,
            currentPoints = currentPoints,
            pointsRequired = pointsRequired.coerceAtLeast(0),
            pointsRemaining = pointsRemaining,
            progressFraction = progressFraction,
            nextLevel = nextLevel
        )
    }

    fun starterExperience(score: Int): Int {
        val normalizedScore = score.coerceIn(0, 100)
        return when {
            normalizedScore >= 70 -> scale(
                value = normalizedScore,
                minValue = 70,
                maxValue = 100,
                minPoints = 370,
                maxPoints = 590
            )

            normalizedScore >= 38 -> scale(
                value = normalizedScore,
                minValue = 38,
                maxValue = 69,
                minPoints = 170,
                maxPoints = 320
            )

            else -> scale(
                value = normalizedScore,
                minValue = 0,
                maxValue = 37,
                minPoints = 20,
                maxPoints = 130
            )
        }
    }

    fun calculateTrailXp(input: TrailRewardInput): Int {
        if (input.outcome != ProfileTrailOutcome.COMPLETED) {
            return 0
        }

        val basePoints = when (normalizeDifficulty(input.difficulty)) {
            "EXPERT" -> 85
            "HARD" -> 65
            "MEDIUM" -> 45
            else -> 30
        }
        val distancePoints = (input.distanceKm.coerceAtLeast(0.0) / 5.0).toInt() * 10
        val elevationPoints = (input.elevationGainM.coerceAtLeast(0) / 250) * 8
        val gearPoints = if (input.gearReady) 8 else 0

        return basePoints + distancePoints + elevationPoints + gearPoints
    }

    fun evaluateNewAchievements(
        profile: UserProfile,
        previousUnlockedIds: Set<String> = profile.unlockedAchievements.mapTo(mutableSetOf()) { it.id },
        unlockedAtEpochMillis: Long = System.currentTimeMillis()
    ): List<UnlockedAchievementRecord> {
        val stats = buildAchievementStats(profile)
        return achievementDefinitions
            .filterNot { it.id in previousUnlockedIds }
            .filter { it.isUnlocked(stats) }
            .map { definition ->
                UnlockedAchievementRecord(
                    id = definition.id,
                    title = definition.title,
                    unlockedAtEpochMillis = unlockedAtEpochMillis,
                    earnedPoints = definition.points
                )
            }
    }

    fun achievementProgress(profile: UserProfile): List<AchievementProgress> {
        val recordsById = profile.unlockedAchievements.associateBy { it.id }
        return achievementDefinitions.map { definition ->
            AchievementProgress(
                definition = definition,
                unlockedRecord = recordsById[definition.id]
            )
        }
    }

    private fun buildAchievementStats(profile: UserProfile): AchievementStats {
        val completed = profile.trailHistory.filter { it.outcome == ProfileTrailOutcome.COMPLETED }
        val difficultyCounts = completed.groupingBy { normalizeDifficulty(it.difficulty) }.eachCount()
        val completedRegions = completed
            .map { normalizeRegion(it.region) }
            .filter { it.isNotBlank() && it != UnknownRegionKey }
            .toSet()

        return AchievementStats(
            completedTrailCount = completed.size,
            totalDistanceKm = completed.sumOf { it.distanceKm.coerceAtLeast(0.0) },
            totalElevationGainM = completed.sumOf { it.elevationGainM.coerceAtLeast(0) },
            easyTrailCount = difficultyCounts["EASY"] ?: 0,
            mediumTrailCount = difficultyCounts["MEDIUM"] ?: 0,
            hardTrailCount = difficultyCounts["HARD"] ?: 0,
            completedRegionCount = completedRegions.size,
            gearReadyFinishCount = completed.count { it.gearReady }
        )
    }

    private fun normalizeDifficulty(rawValue: String): String {
        val normalized = rawValue.trim().lowercase()
        return when {
            "expert" in normalized -> "EXPERT"
            "dificil" in normalized || "hard" in normalized -> "HARD"
            "mediu" in normalized || "medium" in normalized -> "MEDIUM"
            "usor" in normalized || "ușor" in normalized || "uşor" in normalized || "easy" in normalized -> "EASY"
            else -> "MEDIUM"
        }
    }

    private fun normalizeRegion(rawValue: String): String =
        rawValue.trim().lowercase().ifBlank { UnknownRegionKey }

    private fun scale(
        value: Int,
        minValue: Int,
        maxValue: Int,
        minPoints: Int,
        maxPoints: Int
    ): Int {
        if (maxValue <= minValue) return minPoints
        val fraction = (value - minValue).toFloat() / (maxValue - minValue).toFloat()
        return (minPoints + ((maxPoints - minPoints) * fraction)).roundToInt()
    }

    private const val UnknownRegionKey = "unknown region"
}
