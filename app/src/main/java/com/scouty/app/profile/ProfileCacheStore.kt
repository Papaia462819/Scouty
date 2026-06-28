package com.scouty.app.profile

import android.content.Context
import com.scouty.app.ui.models.UserTrailProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class CachedProfileBundle(
    val profile: UserProfile,
    val routePreferences: UserTrailProfile
)

class ProfileCacheStore(context: Context) {
    private val prefs = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun load(uid: String): CachedProfileBundle? {
        val raw = prefs.getString(keyFor(uid), null) ?: return null
        return runCatching {
            serializer.decodeFromString<PersistedCachedProfile>(raw).toBundle()
        }.getOrNull()
    }

    fun save(uid: String, profile: UserProfile, routePreferences: UserTrailProfile) {
        val payload = PersistedCachedProfile(
            uid = uid,
            profile = profile,
            routePreferences = routePreferences,
            cachedAtEpochMillis = System.currentTimeMillis()
        )
        prefs.edit()
            .putString(keyFor(uid), serializer.encodeToString(payload))
            .apply()
    }

    fun clear(uid: String) {
        prefs.edit().remove(keyFor(uid)).apply()
    }

    private fun keyFor(uid: String): String = "$ProfileKeyPrefix$uid"

    private companion object {
        const val PreferencesName = "scouty_profile_cache"
        const val ProfileKeyPrefix = "profile_"

        val serializer: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

@Serializable
private data class PersistedCachedProfile(
    val uid: String,
    val profile: UserProfile,
    val routePreferences: UserTrailProfile,
    val cachedAtEpochMillis: Long
) {
    fun toBundle(): CachedProfileBundle =
        CachedProfileBundle(
            profile = profile,
            routePreferences = routePreferences
        )
}
