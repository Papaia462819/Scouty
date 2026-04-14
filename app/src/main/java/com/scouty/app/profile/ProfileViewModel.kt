package com.scouty.app.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.security.MessageDigest
import kotlin.math.roundToInt

import com.scouty.app.ui.models.CompletedTrailSnapshot
import com.scouty.app.ui.models.TrailCompletionStatus

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LocalAccountRepository(application)

    private var pendingRegistrationEmail: String? = null
    private var pendingRegistrationHash: String? = null

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<ProfileSessionUiState> = _uiState.asStateFlow()

    fun clearAuthMessage() {
        _uiState.update { it.copy(authMessage = null) }
    }

    fun login(email: String, password: String) {
        val normalizedEmail = email.trim()
        val record = repository.load()
        when {
            normalizedEmail.isBlank() || password.isBlank() -> {
                _uiState.update { it.copy(authMessage = "Fill in both email and password.") }
            }

            record == null -> {
                _uiState.update {
                    it.copy(
                        authMessage = "No local account found yet. Start with Register."
                    )
                }
            }

            !normalizedEmail.equals(record.email, ignoreCase = true) || hashPassword(password) != record.passwordHash -> {
                _uiState.update { it.copy(authMessage = "That email and password pair does not match the local account.") }
            }

            record.profile == null -> {
                _uiState.value = ProfileSessionUiState(
                    stage = SessionStage.ONBOARDING,
                    accountExists = true,
                    accountEmail = record.email,
                    pendingRegistrationEmail = record.email,
                    authMessage = null
                )
            }

            else -> {
                val authenticated = record.copy(isAuthenticated = true)
                repository.save(authenticated)
                _uiState.value = buildAuthenticatedState(authenticated)
            }
        }
    }

    fun startRegistration(email: String, password: String) {
        val normalizedEmail = email.trim()
        when {
            normalizedEmail.isBlank() || !normalizedEmail.contains("@") -> {
                _uiState.update { it.copy(authMessage = "Use a valid email address to create the local account.") }
            }

            password.length < 6 -> {
                _uiState.update { it.copy(authMessage = "Use a password with at least 6 characters.") }
            }

            else -> {
                pendingRegistrationEmail = normalizedEmail
                pendingRegistrationHash = hashPassword(password)
                _uiState.value = ProfileSessionUiState(
                    stage = SessionStage.ONBOARDING,
                    accountExists = repository.load() != null,
                    accountEmail = repository.load()?.email,
                    pendingRegistrationEmail = normalizedEmail,
                    authMessage = null
                )
            }
        }
    }

    fun cancelRegistration() {
        pendingRegistrationEmail = null
        pendingRegistrationHash = null
        _uiState.value = loadInitialState()
    }

    fun completeRegistration(draft: OnboardingDraft) {
        val email = pendingRegistrationEmail ?: return
        val hash = pendingRegistrationHash ?: return
        val profile = ProfileAssessmentEngine.buildProfile(
            email = email,
            draft = draft,
            createdAtEpochMillis = System.currentTimeMillis()
        )
        val record = LocalAccountRecord(
            email = email,
            passwordHash = hash,
            isAuthenticated = true,
            profile = profile
        )
        repository.save(record)
        pendingRegistrationEmail = null
        pendingRegistrationHash = null
        _uiState.value = buildAuthenticatedState(record)
    }

    fun updateProfile(draft: OnboardingDraft) {
        val record = repository.load() ?: return
        val currentProfile = record.profile ?: return
        val updatedProfile = ProfileAssessmentEngine.buildProfile(
            email = record.email,
            draft = draft,
            createdAtEpochMillis = System.currentTimeMillis(),
            previousProfile = currentProfile
        )
        val updatedRecord = record.copy(
            isAuthenticated = true,
            profile = updatedProfile
        )
        repository.save(updatedRecord)
        _uiState.value = buildAuthenticatedState(updatedRecord)
    }

    fun recordTrailCompletion(snapshot: CompletedTrailSnapshot) {
        val record = repository.load() ?: return
        val currentProfile = record.profile ?: return
        if (currentProfile.trailHistory.any { it.id == snapshot.id }) {
            return
        }

        val outcome = when (snapshot.status) {
            TrailCompletionStatus.COMPLETED -> ProfileTrailOutcome.COMPLETED
            TrailCompletionStatus.ENDED_EARLY -> ProfileTrailOutcome.ENDED_EARLY
        }
        val earnedPoints = ProfileProgressionEngine.calculateTrailReward(
            input = TrailRewardInput(
                distanceKm = snapshot.distanceKm,
                elevationGainM = snapshot.elevationGainM,
                difficulty = snapshot.difficulty,
                region = snapshot.region,
                gearReady = snapshot.gearReady,
                outcome = outcome
            ),
            profile = currentProfile
        )
        val updatedExperience = if (outcome == ProfileTrailOutcome.COMPLETED) {
            ProfileProgressionEngine.effectiveExperience(currentProfile) + earnedPoints
        } else {
            ProfileProgressionEngine.effectiveExperience(currentProfile)
        }
        val updatedLevel = ProfileProgressionEngine.levelForExperience(updatedExperience)
        val updatedProfile = currentProfile.copy(
            levelNumber = updatedLevel.number,
            levelTitle = updatedLevel.title,
            experiencePoints = updatedExperience,
            completedHikes = currentProfile.completedHikes + if (outcome == ProfileTrailOutcome.COMPLETED) 1 else 0,
            totalDistanceKm = if (outcome == ProfileTrailOutcome.COMPLETED) {
                ((currentProfile.totalDistanceKm + snapshot.distanceKm) * 10.0).roundToInt() / 10.0
            } else {
                currentProfile.totalDistanceKm
            },
            totalElevationGainM = currentProfile.totalElevationGainM + if (outcome == ProfileTrailOutcome.COMPLETED) {
                snapshot.elevationGainM
            } else {
                0
            },
            trailHistory = listOf(
                ProfileTrailRecord(
                    id = snapshot.id,
                    name = snapshot.name,
                    region = snapshot.region,
                    completedAtEpochMillis = snapshot.completedAtEpochMillis,
                    distanceKm = snapshot.distanceKm,
                    elevationGainM = snapshot.elevationGainM,
                    durationText = snapshot.durationText,
                    difficulty = snapshot.difficulty,
                    imageUrl = snapshot.imageUrl,
                    outcome = outcome,
                    earnedPoints = earnedPoints
                )
            ) + currentProfile.trailHistory,
            updatedAtEpochMillis = snapshot.completedAtEpochMillis
        )
        val updatedRecord = record.copy(profile = updatedProfile)
        repository.save(updatedRecord)
        _uiState.value = if (updatedRecord.isAuthenticated) {
            buildAuthenticatedState(updatedRecord)
        } else {
            _uiState.value.copy(profile = updatedProfile)
        }
    }

    fun signOut() {
        val record = repository.load() ?: run {
            _uiState.value = loadInitialState()
            return
        }
        val updated = record.copy(isAuthenticated = false)
        repository.save(updated)
        _uiState.value = loadInitialState()
    }

    private fun loadInitialState(): ProfileSessionUiState {
        val record = repository.load()
        return when {
            record == null -> ProfileSessionUiState()
            record.isAuthenticated && record.profile != null -> buildAuthenticatedState(record)
            else -> ProfileSessionUiState(
                stage = SessionStage.AUTH,
                accountExists = true,
                accountEmail = record.email,
                profile = record.profile
            )
        }
    }

    private fun buildAuthenticatedState(record: LocalAccountRecord): ProfileSessionUiState =
        ProfileSessionUiState(
            stage = SessionStage.APP,
            accountExists = true,
            accountEmail = record.email,
            profile = record.profile
        )

    private fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return buildString(bytes.size * 2) {
            bytes.forEach { byte -> append("%02x".format(byte)) }
        }
    }
}
