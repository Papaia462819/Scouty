package com.scouty.app.profile

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalAccountRepository(context: Context) {

    private val prefs = context.getSharedPreferences("scouty_profile_store", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): LocalAccountRecord? {
        val payload = prefs.getString(AccountKey, null) ?: return null
        return runCatching { json.decodeFromString<LocalAccountRecord>(payload) }.getOrNull()
    }

    fun save(record: LocalAccountRecord) {
        prefs.edit().putString(AccountKey, json.encodeToString(record)).apply()
    }

    fun clear() {
        prefs.edit().remove(AccountKey).apply()
    }

    private companion object {
        const val AccountKey = "local_account_json"
    }
}
