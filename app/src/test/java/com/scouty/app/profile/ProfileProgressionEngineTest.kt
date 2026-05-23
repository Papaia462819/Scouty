package com.scouty.app.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileProgressionEngineTest {

    @Test
    fun starterExperienceStaysInsideStarterTiers() {
        assertTrue(ProfileProgressionEngine.starterExperience(10) in 0 until 150)
        assertTrue(ProfileProgressionEngine.starterExperience(55) in 150 until 350)
        assertTrue(ProfileProgressionEngine.starterExperience(92) in 350 until 650)
    }

    @Test
    fun existingProfilesWithoutStoredExperienceStillResolveTheirTier() {
        val profile = profile(onboardingScore = 88)

        assertEquals(ScoutyLevel.LEVEL_3, ProfileProgressionEngine.currentLevel(profile))
    }

    @Test
    fun calculateTrailXpUsesDifficultyDistanceElevationAndGear() {
        assertEquals(30, trailXp(difficulty = "EASY"))
        assertEquals(45, trailXp(difficulty = "MEDIUM"))
        assertEquals(65, trailXp(difficulty = "HARD"))
        assertEquals(85, trailXp(difficulty = "EXPERT"))
        assertEquals(
            74,
            trailXp(
                difficulty = "EASY",
                distanceKm = 10.0,
                elevationGainM = 500,
                gearReady = true
            )
        )
    }

    @Test
    fun endedEarlyTrailGetsNoXp() {
        assertEquals(
            0,
            trailXp(
                difficulty = "HARD",
                distanceKm = 20.0,
                elevationGainM = 1_000,
                gearReady = true,
                outcome = ProfileTrailOutcome.ENDED_EARLY
            )
        )
    }

    @Test
    fun distanceAchievementsUnlockAtRequestedThresholds() {
        val ids = achievementIds(
            profile(
                history = listOf(completedTrail(distanceKm = 250.0))
            )
        )

        assertTrue(
            ids.containsAll(
                listOf(
                    "10km_total",
                    "25km_total",
                    "50km_total",
                    "100km_total",
                    "250km_total"
                )
            )
        )
    }

    @Test
    fun elevationAchievementsUnlockAtRequestedThresholds() {
        val ids = achievementIds(
            profile(
                history = listOf(completedTrail(elevationGainM = 10_000))
            )
        )

        assertTrue(
            ids.containsAll(
                listOf(
                    "500m_gain",
                    "1000m_gain",
                    "5000m_gain",
                    "10000m_gain"
                )
            )
        )
    }

    @Test
    fun easyDifficultyAchievementsUnlockAtOneFiveAndTen() {
        val ids = achievementIds(
            profile(
                history = (1..10).map { index ->
                    completedTrail(id = "easy-$index", difficulty = "EASY")
                }
            )
        )

        assertTrue(ids.containsAll(listOf("first_easy", "5_easy", "10_easy")))
    }

    @Test
    fun mediumDifficultyAchievementsUnlockAtOneThreeAndFive() {
        val ids = achievementIds(
            profile(
                history = (1..5).map { index ->
                    completedTrail(id = "medium-$index", difficulty = "MEDIUM")
                }
            )
        )

        assertTrue(ids.containsAll(listOf("first_medium", "3_medium", "5_medium")))
    }

    @Test
    fun hardDifficultyAchievementsUnlockAtOneThreeAndFive() {
        val ids = achievementIds(
            profile(
                history = (1..5).map { index ->
                    completedTrail(id = "hard-$index", difficulty = "HARD")
                }
            )
        )

        assertTrue(ids.containsAll(listOf("first_hard", "3_hard", "5_hard")))
    }

    @Test
    fun achievementsAreOneTimeAndDoNotReturnAlreadyUnlockedIds() {
        val ids = achievementIds(
            profile(
                history = listOf(completedTrail(distanceKm = 10.0)),
                unlocked = listOf(
                    UnlockedAchievementRecord(
                        id = "10km_total",
                        title = "10 km Club",
                        unlockedAtEpochMillis = 1L,
                        earnedPoints = 30
                    )
                )
            )
        )

        assertFalse("10km_total" in ids)
    }

    @Test
    fun endedEarlyHistoryDoesNotUnlockAchievements() {
        val ids = achievementIds(
            profile(
                history = listOf(
                    completedTrail(
                        outcome = ProfileTrailOutcome.ENDED_EARLY,
                        distanceKm = 250.0,
                        elevationGainM = 10_000,
                        gearReady = true
                    )
                )
            )
        )

        assertTrue(ids.isEmpty())
    }

    @Test
    fun legacyProfilesWithoutUnlockedAchievementsRenderProgressSafely() {
        val progress = ProfileProgressionEngine.achievementProgress(profile())

        assertEquals(ProfileProgressionEngine.achievementDefinitions.size, progress.size)
        assertTrue(progress.all { it.unlockedRecord == null })
        assertNotNull(progress.firstOrNull { it.definition.id == "10km_total" })
        assertNotNull(progress.firstOrNull { it.definition.id == "25km_total" })
    }

    private fun trailXp(
        difficulty: String,
        distanceKm: Double = 0.0,
        elevationGainM: Int = 0,
        gearReady: Boolean = false,
        outcome: ProfileTrailOutcome = ProfileTrailOutcome.COMPLETED
    ): Int = ProfileProgressionEngine.calculateTrailXp(
        TrailRewardInput(
            distanceKm = distanceKm,
            elevationGainM = elevationGainM,
            difficulty = difficulty,
            region = "Bucegi",
            gearReady = gearReady,
            outcome = outcome
        )
    )

    private fun achievementIds(profile: UserProfile): List<String> =
        ProfileProgressionEngine.evaluateNewAchievements(
            profile = profile,
            unlockedAtEpochMillis = 1L
        ).map { it.id }

    private fun profile(
        history: List<ProfileTrailRecord> = emptyList(),
        unlocked: List<UnlockedAchievementRecord> = emptyList(),
        onboardingScore: Int = 0
    ): UserProfile = UserProfile(
        email = "test@example.com",
        displayName = "Tester",
        avatarId = "summit",
        homeRegion = "Bucegi",
        levelNumber = ScoutyLevel.LEVEL_1.number,
        levelTitle = ScoutyLevel.LEVEL_1.title,
        onboardingScore = onboardingScore,
        experiencePoints = 0,
        completedHikes = history.count { it.outcome == ProfileTrailOutcome.COMPLETED },
        totalDistanceKm = history
            .filter { it.outcome == ProfileTrailOutcome.COMPLETED }
            .sumOf { it.distanceKm },
        totalElevationGainM = history
            .filter { it.outcome == ProfileTrailOutcome.COMPLETED }
            .sumOf { it.elevationGainM },
        trailHistory = history,
        unlockedAchievements = unlocked,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L
    )

    private fun completedTrail(
        id: String = "trail",
        difficulty: String = "EASY",
        distanceKm: Double = 0.0,
        elevationGainM: Int = 0,
        region: String = "Bucegi",
        gearReady: Boolean = false,
        outcome: ProfileTrailOutcome = ProfileTrailOutcome.COMPLETED
    ): ProfileTrailRecord = ProfileTrailRecord(
        id = id,
        name = "Test trail",
        region = region,
        completedAtEpochMillis = 1L,
        distanceKm = distanceKm,
        elevationGainM = elevationGainM,
        durationText = "2h",
        difficulty = difficulty,
        outcome = outcome,
        gearReady = gearReady
    )
}
