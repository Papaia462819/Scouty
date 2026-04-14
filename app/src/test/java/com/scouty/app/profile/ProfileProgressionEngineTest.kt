package com.scouty.app.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileProgressionEngineTest {

    @Test
    fun starterExperienceStaysInsideStarterTiers() {
        assertTrue(ProfileProgressionEngine.starterExperience(10) < 120)
        assertTrue(ProfileProgressionEngine.starterExperience(55) in 120 until 280)
        assertTrue(ProfileProgressionEngine.starterExperience(92) in 280 until 500)
    }

    @Test
    fun existingProfilesWithoutStoredExperienceStillResolveTheirTier() {
        val profile = UserProfile(
            email = "andrei@example.com",
            displayName = "Andrei",
            avatarId = "summit",
            homeRegion = "Bucegi",
            levelNumber = ScoutyLevel.LEVEL_3.number,
            levelTitle = ScoutyLevel.LEVEL_3.title,
            onboardingScore = 88,
            createdAtEpochMillis = 1_700_000_000_000,
            updatedAtEpochMillis = 1_700_000_000_000
        )

        val progress = ProfileProgressionEngine.progress(profile)

        assertEquals(ScoutyLevel.LEVEL_3, progress.level)
        assertTrue(progress.currentPoints > 0)
        assertTrue(progress.pointsRemaining > 0)
    }
}
