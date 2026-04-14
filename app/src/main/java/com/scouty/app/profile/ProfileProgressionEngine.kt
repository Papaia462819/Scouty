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

data class ActivityReward(
    val id: String,
    val title: String,
    val points: Int,
    val description: String
)

data class TrailRewardInput(
    val distanceKm: Double,
    val elevationGainM: Int,
    val difficulty: String,
    val region: String,
    val gearReady: Boolean,
    val outcome: ProfileTrailOutcome
)

object ProfileProgressionEngine {

    val milestones: List<LevelMilestone> = listOf(
        LevelMilestone(ScoutyLevel.LEVEL_1, startPoints = 0, nextLevelPoints = 120),
        LevelMilestone(ScoutyLevel.LEVEL_2, startPoints = 120, nextLevelPoints = 280),
        LevelMilestone(ScoutyLevel.LEVEL_3, startPoints = 280, nextLevelPoints = 500),
        LevelMilestone(ScoutyLevel.LEVEL_4, startPoints = 500, nextLevelPoints = 780),
        LevelMilestone(ScoutyLevel.LEVEL_5, startPoints = 780, nextLevelPoints = 1120),
        LevelMilestone(ScoutyLevel.LEVEL_6, startPoints = 1120, nextLevelPoints = 1530),
        LevelMilestone(ScoutyLevel.LEVEL_7, startPoints = 1530, nextLevelPoints = 2010),
        LevelMilestone(ScoutyLevel.LEVEL_8, startPoints = 2010, nextLevelPoints = 2580),
        LevelMilestone(ScoutyLevel.LEVEL_9, startPoints = 2580, nextLevelPoints = 3240),
        LevelMilestone(ScoutyLevel.LEVEL_10, startPoints = 3240, nextLevelPoints = null)
    )

    val activityRewards: List<ActivityReward> = listOf(
        ActivityReward(
            id = "trail_complete",
            title = "Complete a trail",
            points = 35,
            description = "Base reward for finishing a hike and logging it to history."
        ),
        ActivityReward(
            id = "distance_chunk",
            title = "Every 5 km covered",
            points = 10,
            description = "Distance should matter, but not more than finishing the route."
        ),
        ActivityReward(
            id = "elevation_chunk",
            title = "Every 250 m climbed",
            points = 8,
            description = "Steady gain should move the bar even on shorter mountain days."
        ),
        ActivityReward(
            id = "hard_bonus",
            title = "Hard route bonus",
            points = 12,
            description = "Extra reward for higher difficulty trails."
        ),
        ActivityReward(
            id = "expert_bonus",
            title = "Expert route bonus",
            points = 20,
            description = "Big bonus reserved for the sharp end of the route catalog."
        ),
        ActivityReward(
            id = "new_region",
            title = "First trail in a new region",
            points = 18,
            description = "Exploration should push progression, not just repetition."
        ),
        ActivityReward(
            id = "full_gear_sync",
            title = "Trail finished with full gear sync",
            points = 6,
            description = "Small reward for actually using Scouty as a prep tool."
        )
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
                minPoints = 300,
                maxPoints = 455
            )

            normalizedScore >= 38 -> scale(
                value = normalizedScore,
                minValue = 38,
                maxValue = 69,
                minPoints = 138,
                maxPoints = 255
            )

            else -> scale(
                value = normalizedScore,
                minValue = 0,
                maxValue = 37,
                minPoints = 18,
                maxPoints = 100
            )
        }
    }

    fun calculateTrailReward(
        input: TrailRewardInput,
        profile: UserProfile
    ): Int {
        if (input.outcome != ProfileTrailOutcome.COMPLETED) {
            return 0
        }

        var points = reward("trail_complete")
        points += (input.distanceKm / 5.0).toInt() * reward("distance_chunk")
        points += (input.elevationGainM / 250) * reward("elevation_chunk")

        when (input.difficulty.trim().uppercase()) {
            "EXPERT" -> points += reward("expert_bonus")
            "HARD" -> points += reward("hard_bonus")
        }

        val normalizedRegion = input.region.trim()
        val isNewCompletedRegion = normalizedRegion.isNotBlank() &&
            profile.trailHistory.none { entry ->
                entry.outcome == ProfileTrailOutcome.COMPLETED &&
                    entry.region.equals(normalizedRegion, ignoreCase = true)
            }
        if (isNewCompletedRegion) {
            points += reward("new_region")
        }

        if (input.gearReady) {
            points += reward("full_gear_sync")
        }

        return points
    }

    private fun reward(id: String): Int =
        activityRewards.firstOrNull { it.id == id }?.points ?: 0

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
}
