package com.scouty.app.profile

import kotlinx.serialization.Serializable

@Serializable
enum class ScoutyLevel(val number: Int, val title: String) {
    LEVEL_1(1, "Junior"),
    LEVEL_2(2, "Boot Camper"),
    LEVEL_3(3, "Trail Explorer"),
    LEVEL_4(4, "Hill Hopper"),
    LEVEL_5(5, "Trail Surfer"),
    LEVEL_6(6, "Peak Hunter"),
    LEVEL_7(7, "Ridge Rider"),
    LEVEL_8(8, "Hiking Wizard"),
    LEVEL_9(9, "Summit Beast"),
    LEVEL_10(10, "Mountain Gremlin");

    companion object {
        val starters = listOf(LEVEL_1, LEVEL_2, LEVEL_3)

        fun fromNumber(number: Int): ScoutyLevel =
            entries.firstOrNull { it.number == number } ?: LEVEL_1
    }
}

@Serializable
data class UserProfile(
    val email: String,
    val displayName: String,
    val avatarId: String,
    val homeRegion: String,
    val levelNumber: Int,
    val levelTitle: String,
    val onboardingScore: Int,
    val experiencePoints: Int = 0,
    val completedHikes: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val totalElevationGainM: Int = 0,
    val trailHistory: List<ProfileTrailRecord> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val answers: Map<String, String> = emptyMap()
)

@Serializable
enum class ProfileTrailOutcome {
    COMPLETED,
    ENDED_EARLY
}

@Serializable
data class ProfileTrailRecord(
    val id: String,
    val name: String,
    val region: String,
    val completedAtEpochMillis: Long,
    val distanceKm: Double,
    val elevationGainM: Int,
    val durationText: String,
    val difficulty: String,
    val imageUrl: String? = null,
    val outcome: ProfileTrailOutcome = ProfileTrailOutcome.COMPLETED,
    val earnedPoints: Int = 0
)

@Serializable
data class LocalAccountRecord(
    val email: String,
    val passwordHash: String,
    val isAuthenticated: Boolean,
    val profile: UserProfile? = null
)

data class OnboardingDraft(
    val displayName: String = "",
    val avatarId: String = "summit",
    val homeRegion: String = "",
    val answers: Map<String, String> = emptyMap()
)

data class ProfileOption(
    val id: String,
    val label: String,
    val description: String,
    val score: Int
)

data class ProfileQuestion(
    val id: String,
    val title: String,
    val helper: String,
    val weight: Int,
    val options: List<ProfileOption>
)

data class AssessmentResult(
    val score: Int,
    val starterLevel: ScoutyLevel
)

data class TrailStatsSummary(
    val completedHikes: Int,
    val totalDistanceKm: Double,
    val totalElevationGainM: Int
)

enum class SessionStage {
    AUTH,
    ONBOARDING,
    APP
}

data class ProfileSessionUiState(
    val stage: SessionStage = SessionStage.AUTH,
    val accountExists: Boolean = false,
    val accountEmail: String? = null,
    val pendingRegistrationEmail: String? = null,
    val profile: UserProfile? = null,
    val authMessage: String? = null
)

fun UserProfile.toOnboardingDraft(): OnboardingDraft =
    OnboardingDraft(
        displayName = displayName,
        avatarId = avatarId,
        homeRegion = homeRegion,
        answers = answers
    )
