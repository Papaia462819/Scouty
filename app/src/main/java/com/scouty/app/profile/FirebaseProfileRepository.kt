package com.scouty.app.profile

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.scouty.app.ui.models.ExperienceLevel
import com.scouty.app.ui.models.FitnessLevel
import com.scouty.app.ui.models.PreferredTripType
import com.scouty.app.ui.models.UserTrailProfile
import kotlinx.coroutines.tasks.await

data class FirebaseProfileBundle(
    val profile: UserProfile,
    val routePreferences: UserTrailProfile
)

class FirebaseProfileRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    suspend fun registerWithEmail(email: String, password: String): FirebaseUser =
        auth.createUserWithEmailAndPassword(email, password).await().user
            ?: error("Contul nu a putut fi pregătit după înregistrare.")

    suspend fun signInWithEmail(email: String, password: String): FirebaseUser =
        auth.signInWithEmailAndPassword(email, password).await().user
            ?: error("Contul nu a putut fi pregătit după login.")

    suspend fun signInWithGoogle(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return auth.signInWithCredential(credential).await().user
            ?: error("Contul nu a putut fi pregătit după login cu Google.")
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun loadProfile(uid: String): FirebaseProfileBundle? {
        val userDocument = users.document(uid).get().await()
        if (!userDocument.exists()) {
            return null
        }
        val routePreferences = userDocument.routePreferences()
        val history = users.document(uid)
            .collection(TrailHistoryCollection)
            .orderBy("completedAtEpochMillis", Query.Direction.DESCENDING)
            .get()
            .await()
            .documents
            .mapNotNull { it.toTrailRecord() }

        return FirebaseProfileBundle(
            profile = userDocument.toUserProfile(history),
            routePreferences = routePreferences
        )
    }

    suspend fun saveProfile(uid: String, profile: UserProfile, routePreferences: UserTrailProfile) {
        users.document(uid)
            .set(profile.toFirestoreUserDocument(uid, routePreferences), SetOptions.merge())
            .await()
    }

    suspend fun saveTrailCompletion(
        uid: String,
        profile: UserProfile,
        routePreferences: UserTrailProfile,
        record: ProfileTrailRecord
    ) {
        val userReference = users.document(uid)
        val batch = firestore.batch()
        batch.set(userReference, profile.toFirestoreUserDocument(uid, routePreferences), SetOptions.merge())
        batch.set(
            userReference.collection(TrailHistoryCollection).document(record.id),
            record.toFirestoreTrailDocument(),
            SetOptions.merge()
        )
        batch.commit().await()
    }

    private val users
        get() = firestore.collection(UsersCollection)

    private fun UserProfile.toFirestoreUserDocument(
        uid: String,
        routePreferences: UserTrailProfile
    ): Map<String, Any?> = mapOf(
        "schemaVersion" to SchemaVersion,
        "uid" to uid,
        "email" to email,
        "displayName" to displayName,
        "avatarId" to avatarId,
        "homeRegion" to homeRegion,
        "levelNumber" to levelNumber,
        "levelTitle" to levelTitle,
        "onboardingScore" to onboardingScore,
        "experiencePoints" to experiencePoints,
        "completedHikes" to completedHikes,
        "totalDistanceKm" to totalDistanceKm,
        "totalElevationGainM" to totalElevationGainM,
        "unlockedAchievements" to unlockedAchievements.map { it.toFirestoreMap() },
        "answers" to answers,
        "routePreferences" to routePreferences.toFirestoreMap(),
        "createdAtEpochMillis" to createdAtEpochMillis,
        "updatedAtEpochMillis" to updatedAtEpochMillis
    )

    private fun UnlockedAchievementRecord.toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "title" to title,
        "unlockedAtEpochMillis" to unlockedAtEpochMillis,
        "earnedPoints" to earnedPoints
    )

    private fun ProfileTrailRecord.toFirestoreTrailDocument(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "region" to region,
        "completedAtEpochMillis" to completedAtEpochMillis,
        "distanceKm" to distanceKm,
        "elevationGainM" to elevationGainM,
        "durationText" to durationText,
        "difficulty" to difficulty,
        "imageUrl" to imageUrl,
        "outcome" to outcome.name,
        "earnedPoints" to earnedPoints,
        "gearReady" to gearReady,
        "unlockedAchievementIds" to unlockedAchievementIds
    )

    private fun UserTrailProfile.toFirestoreMap(): Map<String, Any> = mapOf(
        "experienceLevel" to experienceLevel.name,
        "fitnessLevel" to fitnessLevel.name,
        "maxPreferredHours" to maxPreferredHours,
        "maxPreferredElevationGain" to maxPreferredElevationGain,
        "preferredTripType" to preferredTripType.name,
        "selectedTrailCount" to selectedTrailCount
    )

    private fun DocumentSnapshot.toUserProfile(history: List<ProfileTrailRecord>): UserProfile =
        UserProfile(
            email = getString("email").orEmpty(),
            displayName = getString("displayName").orEmpty(),
            avatarId = getString("avatarId").orEmpty().ifBlank { "summit" },
            homeRegion = getString("homeRegion").orEmpty(),
            levelNumber = getLong("levelNumber")?.toInt() ?: ScoutyLevel.LEVEL_1.number,
            levelTitle = getString("levelTitle").orEmpty().ifBlank { ScoutyLevel.LEVEL_1.title },
            onboardingScore = getLong("onboardingScore")?.toInt() ?: 0,
            experiencePoints = getLong("experiencePoints")?.toInt() ?: 0,
            completedHikes = getLong("completedHikes")?.toInt() ?: 0,
            totalDistanceKm = getDouble("totalDistanceKm") ?: 0.0,
            totalElevationGainM = getLong("totalElevationGainM")?.toInt() ?: 0,
            trailHistory = history,
            unlockedAchievements = getUnlockedAchievements(),
            createdAtEpochMillis = getLong("createdAtEpochMillis") ?: System.currentTimeMillis(),
            updatedAtEpochMillis = getLong("updatedAtEpochMillis") ?: System.currentTimeMillis(),
            answers = getStringMap("answers")
        )

    private fun DocumentSnapshot.toTrailRecord(): ProfileTrailRecord? {
        val id = getString("id") ?: this.id
        val name = getString("name") ?: return null
        return ProfileTrailRecord(
            id = id,
            name = name,
            region = getString("region").orEmpty(),
            completedAtEpochMillis = getLong("completedAtEpochMillis") ?: 0L,
            distanceKm = getDouble("distanceKm") ?: 0.0,
            elevationGainM = getLong("elevationGainM")?.toInt() ?: 0,
            durationText = getString("durationText").orEmpty(),
            difficulty = getString("difficulty").orEmpty(),
            imageUrl = getString("imageUrl"),
            outcome = parseEnum(getString("outcome"), ProfileTrailOutcome.COMPLETED),
            earnedPoints = getLong("earnedPoints")?.toInt() ?: 0,
            gearReady = getBoolean("gearReady") ?: false,
            unlockedAchievementIds = getStringList("unlockedAchievementIds")
        )
    }

    private fun DocumentSnapshot.routePreferences(): UserTrailProfile {
        val raw = get("routePreferences") as? Map<*, *> ?: return UserTrailProfile()
        return UserTrailProfile(
            experienceLevel = parseEnum(raw["experienceLevel"] as? String, ExperienceLevel.BEGINNER),
            fitnessLevel = parseEnum(raw["fitnessLevel"] as? String, FitnessLevel.MODERATE),
            maxPreferredHours = (raw["maxPreferredHours"] as? Number)?.toDouble() ?: 5.0,
            maxPreferredElevationGain = (raw["maxPreferredElevationGain"] as? Number)?.toInt() ?: 700,
            preferredTripType = parseEnum(raw["preferredTripType"] as? String, PreferredTripType.RELAXED_SCENIC),
            selectedTrailCount = (raw["selectedTrailCount"] as? Number)?.toInt() ?: 0
        )
    }

    private fun DocumentSnapshot.getStringMap(field: String): Map<String, String> =
        (get(field) as? Map<*, *>)
            ?.mapNotNull { (key, value) ->
                val stringKey = key as? String ?: return@mapNotNull null
                val stringValue = value as? String ?: return@mapNotNull null
                stringKey to stringValue
            }
            ?.toMap()
            .orEmpty()

    private fun DocumentSnapshot.getStringList(field: String): List<String> =
        (get(field) as? List<*>)
            ?.mapNotNull { it as? String }
            .orEmpty()

    private fun DocumentSnapshot.getUnlockedAchievements(): List<UnlockedAchievementRecord> =
        (get("unlockedAchievements") as? List<*>)
            ?.mapNotNull { raw ->
                val map = raw as? Map<*, *> ?: return@mapNotNull null
                val id = map["id"] as? String ?: return@mapNotNull null
                val title = map["title"] as? String ?: return@mapNotNull null
                UnlockedAchievementRecord(
                    id = id,
                    title = title,
                    unlockedAtEpochMillis = (map["unlockedAtEpochMillis"] as? Number)?.toLong() ?: 0L,
                    earnedPoints = (map["earnedPoints"] as? Number)?.toInt() ?: 0
                )
            }
            .orEmpty()

    private inline fun <reified T : Enum<T>> parseEnum(raw: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: default

    private companion object {
        const val UsersCollection = "users"
        const val TrailHistoryCollection = "trailHistory"
        const val SchemaVersion = 1
    }
}
