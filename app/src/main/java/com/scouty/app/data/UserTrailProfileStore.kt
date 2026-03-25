package com.scouty.app.data

import android.content.Context
import com.scouty.app.ui.models.UserTrailProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class UserTrailProfileStore(context: Context) {
    private val sharedPreferences =
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun load(): UserTrailProfile {
        val rawValue = sharedPreferences.getString(ProfileKey, null) ?: return UserTrailProfile()
        return runCatching {
            json.decodeFromString<UserTrailProfile>(rawValue)
        }.getOrElse {
            UserTrailProfile()
        }
    }

    fun save(profile: UserTrailProfile) {
        sharedPreferences.edit()
            .putString(ProfileKey, json.encodeToString(profile))
            .apply()
    }

    private companion object {
        private const val PreferencesName = "scouty_user_profile"
        private const val ProfileKey = "profile_json"
    }
}
