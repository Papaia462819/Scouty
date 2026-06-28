package com.scouty.app.profile

import android.content.Context
import com.scouty.app.ui.models.CompletedTrailSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PendingTrailCompletionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun loadForUser(uid: String): List<PendingTrailCompletion> =
        loadAll()
            .filter { it.ownerUid == uid }
            .sortedBy { it.snapshot.completedAtEpochMillis }

    fun upsert(completion: PendingTrailCompletion) {
        val merged = loadAll()
            .filterNot { it.ownerUid == completion.ownerUid && it.snapshot.id == completion.snapshot.id } +
            completion
        saveAll(merged.sortedWith(compareBy<PendingTrailCompletion> { it.ownerUid }.thenBy { it.snapshot.completedAtEpochMillis }))
    }

    fun removeForUser(uid: String, completionIds: Collection<String>) {
        if (completionIds.isEmpty()) return
        val ids = completionIds.toSet()
        val remaining = loadAll()
            .filterNot { it.ownerUid == uid && it.snapshot.id in ids }
        saveAll(remaining)
    }

    private fun loadAll(): List<PendingTrailCompletion> {
        val raw = prefs.getString(PendingKey, null) ?: return emptyList()
        return runCatching {
            serializer.decodeFromString<List<PendingTrailCompletion>>(raw)
        }.getOrElse {
            emptyList()
        }
    }

    private fun saveAll(completions: List<PendingTrailCompletion>) {
        prefs.edit()
            .putString(PendingKey, serializer.encodeToString(completions))
            .apply()
    }

    private companion object {
        const val PreferencesName = "scouty_pending_trail_completions"
        const val PendingKey = "pending_json"

        val serializer: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

@Serializable
data class PendingTrailCompletion(
    val ownerUid: String,
    val snapshot: CompletedTrailSnapshot,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)
