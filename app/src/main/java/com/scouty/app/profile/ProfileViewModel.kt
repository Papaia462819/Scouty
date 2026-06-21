package com.scouty.app.profile

import android.app.Application
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CustomCredential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.scouty.app.R
import com.scouty.app.data.UserTrailProfileStore
import com.scouty.app.ui.models.CompletedTrailSnapshot
import com.scouty.app.ui.models.TrailCompletionStatus
import com.scouty.app.ui.models.UserTrailProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FirebaseProfileRepository()
    private val userTrailProfileStore = UserTrailProfileStore(application)

    private val _uiState = MutableStateFlow(ProfileSessionUiState(isLoading = true))
    val uiState: StateFlow<ProfileSessionUiState> = _uiState.asStateFlow()

    init {
        refreshFirebaseSession()
    }

    fun clearAuthMessage() {
        _uiState.update { it.copy(authMessage = null) }
    }

    fun continueWithoutAccount() {
        _uiState.value = ProfileSessionUiState(
            stage = SessionStage.APP,
            isLoading = false,
            uid = GuestUid,
            isGuest = true,
            accountExists = false,
            profile = buildGuestProfile(),
            routePreferences = UserTrailProfile()
        )
    }

    fun login(email: String, password: String) {
        val normalizedEmail = email.trim()
        when {
            normalizedEmail.isBlank() || password.isBlank() -> {
                _uiState.update { it.copy(authMessage = "Completează emailul și parola.") }
            }

            else -> {
                _uiState.update { it.copy(isLoading = true, authMessage = null) }
                viewModelScope.launch {
                    runCatching {
                        val user = repository.signInWithEmail(normalizedEmail, password)
                        clearLegacyLocalState()
                        loadSessionForUser(user.uid, user.email.orEmpty(), user.displayName)
                    }.onFailure { error ->
                        _uiState.value = ProfileSessionUiState(
                            stage = SessionStage.AUTH,
                            authMessage = error.toUserMessage("Nu am putut autentifica utilizatorul.")
                        )
                    }
                }
            }
        }
    }

    fun startRegistration(email: String, password: String) {
        val normalizedEmail = email.trim()
        when {
            normalizedEmail.isBlank() || !normalizedEmail.contains("@") -> {
                _uiState.update { it.copy(authMessage = "Folosește o adresă de email validă.") }
            }

            password.length < 6 -> {
                _uiState.update { it.copy(authMessage = "Parola trebuie să aibă cel puțin 6 caractere.") }
            }

            else -> {
                _uiState.update { it.copy(isLoading = true, authMessage = null) }
                viewModelScope.launch {
                    runCatching {
                        val user = repository.registerWithEmail(normalizedEmail, password)
                        clearLegacyLocalState()
                        startOnboardingForUser(user.uid, user.email.orEmpty(), user.displayName)
                    }.onFailure { error ->
                        _uiState.value = ProfileSessionUiState(
                            stage = SessionStage.AUTH,
                            authMessage = error.toUserMessage("Nu am putut crea contul.")
                        )
                    }
                }
            }
        }
    }

    fun signInWithGoogle(context: Context) {
        _uiState.update { it.copy(isLoading = true, authMessage = null) }
        viewModelScope.launch {
            runCatching {
                val credentialManager = CredentialManager.create(context)
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(
                        GetSignInWithGoogleOption.Builder(
                            serverClientId = getApplication<Application>().getString(R.string.default_web_client_id)
                        )
                            .build()
                    )
                    .build()
                val credential = credentialManager.getCredential(
                    context = context,
                    request = request
                ).credential
                val googleCredential = when {
                    credential is CustomCredential &&
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ->
                        GoogleIdTokenCredential.createFrom(credential.data)

                    else -> error("Credential Manager did not return a Google ID token.")
                }
                val user = repository.signInWithGoogle(googleCredential.idToken)
                clearLegacyLocalState()
                loadSessionForUser(user.uid, user.email.orEmpty(), user.displayName)
            }.onFailure { error ->
                _uiState.value = ProfileSessionUiState(
                    stage = SessionStage.AUTH,
                    authMessage = error.toUserMessage("Nu am putut autentifica utilizatorul cu Google.")
                )
            }
        }
    }

    fun cancelRegistration() {
        viewModelScope.launch {
            repository.signOut()
            clearCredentialState()
            _uiState.value = ProfileSessionUiState()
        }
    }

    fun completeRegistration(draft: OnboardingDraft) {
        val uid = _uiState.value.uid ?: repository.currentUser?.uid ?: return
        val email = _uiState.value.pendingRegistrationEmail
            ?: repository.currentUser?.email
            ?: return
        val displayName = draft.displayName.ifBlank {
            repository.currentUser?.displayName.orEmpty()
        }
        val profile = ProfileAssessmentEngine.buildProfile(
            email = email,
            draft = draft.copy(displayName = displayName),
            createdAtEpochMillis = System.currentTimeMillis()
        )
        val routePreferences = UserTrailProfile()
        _uiState.update { it.copy(isLoading = true, authMessage = null) }
        viewModelScope.launch {
            runCatching {
                repository.saveProfile(uid, profile, routePreferences)
                userTrailProfileStore.save(routePreferences)
                _uiState.value = buildAuthenticatedState(uid, profile, routePreferences)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        authMessage = error.toUserMessage("Profilul nu a putut fi salvat.")
                    )
                }
            }
        }
    }

    fun updateProfile(draft: OnboardingDraft) {
        val currentState = _uiState.value
        val currentProfile = currentState.profile ?: return
        val routePreferences = currentState.routePreferences
        val updatedProfile = ProfileAssessmentEngine.buildProfile(
            email = currentProfile.email,
            draft = draft,
            createdAtEpochMillis = System.currentTimeMillis(),
            previousProfile = currentProfile
        )
        if (currentState.isGuest) {
            _uiState.value = currentState.copy(
                stage = SessionStage.APP,
                isLoading = false,
                authMessage = null,
                profile = updatedProfile,
                routePreferences = routePreferences
            )
            return
        }

        val uid = currentState.uid ?: return
        _uiState.update { it.copy(isLoading = true, authMessage = null) }
        viewModelScope.launch {
            runCatching {
                repository.saveProfile(uid, updatedProfile, routePreferences)
                _uiState.value = buildAuthenticatedState(uid, updatedProfile, routePreferences)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        authMessage = error.toUserMessage("Profilul nu a putut fi actualizat.")
                    )
                }
            }
        }
    }

    fun updateRoutePreferences(routePreferences: UserTrailProfile) {
        val currentState = _uiState.value
        val profile = currentState.profile ?: return
        if (currentState.routePreferences == routePreferences) {
            return
        }
        if (currentState.isGuest) {
            _uiState.update { it.copy(routePreferences = routePreferences) }
            userTrailProfileStore.save(routePreferences)
            return
        }

        val uid = currentState.uid ?: return
        _uiState.update { it.copy(routePreferences = routePreferences) }
        viewModelScope.launch {
            runCatching {
                repository.saveProfile(uid, profile.copy(updatedAtEpochMillis = System.currentTimeMillis()), routePreferences)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(authMessage = error.toUserMessage("Preferințele de traseu nu au putut fi sincronizate."))
                }
            }
        }
    }

    fun recordTrailCompletion(snapshot: CompletedTrailSnapshot) {
        val currentState = _uiState.value
        val currentProfile = currentState.profile ?: return
        if (!currentState.isGuest && currentState.uid == null) {
            return
        }
        val routePreferences = currentState.routePreferences
        if (currentProfile.trailHistory.any { it.id == snapshot.id }) {
            return
        }

        val outcome = when (snapshot.status) {
            TrailCompletionStatus.COMPLETED -> ProfileTrailOutcome.COMPLETED
            TrailCompletionStatus.ENDED_EARLY -> ProfileTrailOutcome.ENDED_EARLY
        }
        val trailXp = ProfileProgressionEngine.calculateTrailXp(
            input = TrailRewardInput(
                distanceKm = snapshot.distanceKm,
                elevationGainM = snapshot.elevationGainM,
                difficulty = snapshot.difficulty,
                region = snapshot.region,
                gearReady = snapshot.gearReady,
                outcome = outcome
            )
        )
        val baseRecord = ProfileTrailRecord(
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
            gearReady = snapshot.gearReady
        )
        val profileAfterTrailStats = currentProfile.copy(
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
            trailHistory = listOf(baseRecord) + currentProfile.trailHistory,
            updatedAtEpochMillis = snapshot.completedAtEpochMillis
        )
        val newAchievements = if (outcome == ProfileTrailOutcome.COMPLETED) {
            ProfileProgressionEngine.evaluateNewAchievements(
                profile = profileAfterTrailStats,
                previousUnlockedIds = currentProfile.unlockedAchievements.mapTo(mutableSetOf()) { it.id },
                unlockedAtEpochMillis = snapshot.completedAtEpochMillis
            )
        } else {
            emptyList()
        }
        val achievementXp = newAchievements.sumOf { it.earnedPoints }
        val earnedPoints = trailXp + achievementXp
        val updatedExperience = if (outcome == ProfileTrailOutcome.COMPLETED) {
            ProfileProgressionEngine.effectiveExperience(currentProfile) + earnedPoints
        } else {
            ProfileProgressionEngine.effectiveExperience(currentProfile)
        }
        val updatedLevel = ProfileProgressionEngine.levelForExperience(updatedExperience)
        val newRecord = baseRecord.copy(
            earnedPoints = earnedPoints,
            unlockedAchievementIds = newAchievements.map { it.id }
        )
        val updatedProfile = profileAfterTrailStats.copy(
            levelNumber = updatedLevel.number,
            levelTitle = updatedLevel.title,
            experiencePoints = updatedExperience,
            trailHistory = listOf(newRecord) + currentProfile.trailHistory,
            unlockedAchievements = currentProfile.unlockedAchievements + newAchievements
        )

        if (currentState.isGuest) {
            _uiState.value = currentState.copy(
                stage = SessionStage.APP,
                isLoading = false,
                authMessage = null,
                profile = updatedProfile,
                routePreferences = routePreferences
            )
            return
        }

        val uid = currentState.uid ?: return
        _uiState.value = buildAuthenticatedState(uid, updatedProfile, routePreferences)
        viewModelScope.launch {
            runCatching {
                repository.saveTrailCompletion(uid, updatedProfile, routePreferences, newRecord)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(authMessage = error.toUserMessage("Tura a fost salvată local în sesiune, dar nu a fost sincronizată încă."))
                }
            }
        }
    }

    fun signOut() {
        if (_uiState.value.isGuest) {
            _uiState.value = ProfileSessionUiState()
            return
        }
        viewModelScope.launch {
            repository.signOut()
            clearCredentialState()
            _uiState.value = ProfileSessionUiState()
        }
    }

    private fun refreshFirebaseSession() {
        val user = repository.currentUser
        if (user == null) {
            _uiState.value = ProfileSessionUiState()
            return
        }
        viewModelScope.launch {
            runCatching {
                clearLegacyLocalState()
                loadSessionForUser(user.uid, user.email.orEmpty(), user.displayName)
            }.onFailure { error ->
                repository.signOut()
                _uiState.value = ProfileSessionUiState(
                    authMessage = error.toUserMessage("Sesiunea nu a putut fi încărcată.")
                )
            }
        }
    }

    private suspend fun loadSessionForUser(uid: String, email: String, displayName: String?) {
        val bundle = repository.loadProfile(uid)
        if (bundle == null) {
            startOnboardingForUser(uid, email, displayName)
            return
        }
        userTrailProfileStore.save(bundle.routePreferences)
        _uiState.value = buildAuthenticatedState(uid, bundle.profile, bundle.routePreferences)
    }

    private fun startOnboardingForUser(uid: String, email: String, displayName: String?) {
        _uiState.value = ProfileSessionUiState(
            stage = SessionStage.ONBOARDING,
            isLoading = false,
            uid = uid,
            accountExists = true,
            accountEmail = email,
            pendingRegistrationEmail = email,
            profile = displayName
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    UserProfile(
                        email = email,
                        displayName = it,
                        avatarId = "summit",
                        homeRegion = "",
                        levelNumber = ScoutyLevel.LEVEL_1.number,
                        levelTitle = ScoutyLevel.LEVEL_1.title,
                        onboardingScore = 0,
                        createdAtEpochMillis = System.currentTimeMillis(),
                        updatedAtEpochMillis = System.currentTimeMillis()
                    )
                },
            routePreferences = UserTrailProfile()
        )
    }

    private fun buildAuthenticatedState(
        uid: String,
        profile: UserProfile,
        routePreferences: UserTrailProfile
    ): ProfileSessionUiState =
        ProfileSessionUiState(
            stage = SessionStage.APP,
            isLoading = false,
            uid = uid,
            accountExists = true,
            accountEmail = profile.email,
            profile = profile,
            routePreferences = routePreferences
        )

    private fun buildGuestProfile(): UserProfile {
        val now = System.currentTimeMillis()
        return UserProfile(
            email = "Fără cont",
            displayName = "Oaspete",
            avatarId = "summit",
            homeRegion = "",
            levelNumber = ScoutyLevel.LEVEL_1.number,
            levelTitle = ScoutyLevel.LEVEL_1.title,
            onboardingScore = 0,
            experiencePoints = 0,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now
        )
    }

    private fun clearLegacyLocalState() {
        getApplication<Application>()
            .getSharedPreferences(LegacyProfilePreferencesName, Context.MODE_PRIVATE)
            .edit()
            .remove(LegacyAccountKey)
            .apply()
        userTrailProfileStore.clear()
    }

    private suspend fun clearCredentialState() {
        runCatching {
            CredentialManager.create(getApplication<Application>())
                .clearCredentialState(ClearCredentialStateRequest())
        }
    }

    private fun Throwable.toUserMessage(fallback: String): String =
        when (this) {
            is FirebaseNetworkException -> "Verifică conexiunea la internet și încearcă din nou."
            is FirebaseAuthException -> when (errorCode) {
                "ERROR_EMAIL_ALREADY_IN_USE" -> "Există deja un cont pentru acest email."
                "ERROR_INVALID_EMAIL" -> "Adresa de email nu este validă."
                "ERROR_INVALID_CREDENTIAL",
                "ERROR_WRONG_PASSWORD",
                "ERROR_USER_NOT_FOUND" -> "Emailul sau parola nu sunt corecte."
                "ERROR_WEAK_PASSWORD" -> "Parola este prea slabă."
                else -> fallback
            }
            is GetCredentialException -> "Autentificarea Google a fost anulată sau nu este disponibilă."
            else -> localizedMessage ?: fallback
        }

    private companion object {
        const val GuestUid = "guest-local"
        const val LegacyProfilePreferencesName = "scouty_profile_store"
        const val LegacyAccountKey = "local_account_json"
    }
}
