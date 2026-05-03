package com.scouty.app.assistant.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class ConversationRole(val storageValue: String) {
    USER("user"),
    ASSISTANT("assistant");

    companion object {
        fun fromStorage(value: String): ConversationRole =
            entries.firstOrNull { it.storageValue == value } ?: USER
    }
}

data class ConversationTurn(
    val conversationId: String,
    val turnIdx: Int,
    val role: ConversationRole,
    val text: String,
    val timestamp: Long,
    val retrievedChunkId: String?
)

class ConversationStore(context: Context) {
    private val databaseFile = File(context.noBackupFilesDir, DatabaseName)
    private val mutex = Mutex()

    suspend fun appendTurn(
        conversationId: String,
        role: ConversationRole,
        text: String,
        chunkId: String?
    ) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                openDatabase().use { database ->
                    val now = System.currentTimeMillis()
                    ensureConversation(database, conversationId, trailId = null, now = now)
                    val turnIdx = nextTurnIndex(database, conversationId)
                    val values = ContentValues().apply {
                        put("conversation_id", conversationId)
                        put("turn_idx", turnIdx)
                        put("role", role.storageValue)
                        put("text", text)
                        put("timestamp", now)
                        put("retrieved_chunk_id", chunkId)
                    }
                    database.insertOrThrow("turns", null, values)
                    touchConversation(database, conversationId, now)
                }
            }
        }
    }

    suspend fun loadRecent(conversationId: String, maxTurns: Int): List<ConversationTurn> =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                openDatabase().use { database ->
                    database.rawQuery(
                        """
                        SELECT conversation_id, turn_idx, role, text, timestamp, retrieved_chunk_id
                        FROM turns
                        WHERE conversation_id = ?
                        ORDER BY turn_idx DESC
                        LIMIT ?
                        """.trimIndent(),
                        arrayOf(conversationId, maxTurns.coerceAtLeast(0).toString())
                    ).use { cursor ->
                        val rows = mutableListOf<ConversationTurn>()
                        while (cursor.moveToNext()) {
                            rows += readTurn(cursor)
                        }
                        rows.asReversed()
                    }
                }
            }
        }

    suspend fun loadSummary(conversationId: String): String? =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                openDatabase().use { database ->
                    database.rawQuery(
                        "SELECT summary FROM conversations WHERE conversation_id = ?",
                        arrayOf(conversationId)
                    ).use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getString(0)?.takeIf { it.isNotBlank() }
                        } else {
                            null
                        }
                    }
                }
            }
        }

    suspend fun updateSummary(conversationId: String, summary: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                openDatabase().use { database ->
                    val now = System.currentTimeMillis()
                    ensureConversation(database, conversationId, trailId = null, now = now)
                    val values = ContentValues().apply {
                        put("summary", summary)
                        put("summary_token_count", estimateTokens(summary))
                        put("last_active_at", now)
                    }
                    database.update(
                        "conversations",
                        values,
                        "conversation_id = ?",
                        arrayOf(conversationId)
                    )
                }
            }
        }
    }

    suspend fun ensureConversation(conversationId: String, trailId: String?) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                openDatabase().use { database ->
                    ensureConversation(
                        database = database,
                        conversationId = conversationId,
                        trailId = trailId,
                        now = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    suspend fun loadAllTurns(conversationId: String): List<ConversationTurn> =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                openDatabase().use { database ->
                    database.rawQuery(
                        """
                        SELECT conversation_id, turn_idx, role, text, timestamp, retrieved_chunk_id
                        FROM turns
                        WHERE conversation_id = ?
                        ORDER BY turn_idx ASC
                        """.trimIndent(),
                        arrayOf(conversationId)
                    ).use { cursor ->
                        val rows = mutableListOf<ConversationTurn>()
                        while (cursor.moveToNext()) {
                            rows += readTurn(cursor)
                        }
                        rows
                    }
                }
            }
        }

    suspend fun countTurns(conversationId: String): Int =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                openDatabase().use { database ->
                    database.rawQuery(
                        "SELECT COUNT(*) FROM turns WHERE conversation_id = ?",
                        arrayOf(conversationId)
                    ).use { cursor ->
                        if (cursor.moveToFirst()) cursor.getInt(0) else 0
                    }
                }
            }
        }

    suspend fun pruneToRecent(conversationId: String, keepLastTurns: Int) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                openDatabase().use { database ->
                    database.execSQL(
                        """
                        DELETE FROM turns
                        WHERE conversation_id = ?
                          AND turn_idx NOT IN (
                            SELECT turn_idx
                            FROM turns
                            WHERE conversation_id = ?
                            ORDER BY turn_idx DESC
                            LIMIT ?
                          )
                        """.trimIndent(),
                        arrayOf(conversationId, conversationId, keepLastTurns.coerceAtLeast(0))
                    )
                }
            }
        }
    }

    suspend fun deleteConversation(conversationId: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                openDatabase().use { database ->
                    database.delete("turns", "conversation_id = ?", arrayOf(conversationId))
                    database.delete("conversations", "conversation_id = ?", arrayOf(conversationId))
                }
            }
        }
    }

    private fun openDatabase(): SQLiteDatabase {
        databaseFile.parentFile?.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(databaseFile, null).also(::ensureSchema)
    }

    private fun ensureSchema(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS conversations (
              conversation_id TEXT PRIMARY KEY,
              started_at INTEGER NOT NULL,
              last_active_at INTEGER NOT NULL,
              trail_id TEXT,
              summary TEXT,
              summary_token_count INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS turns (
              conversation_id TEXT NOT NULL,
              turn_idx INTEGER NOT NULL,
              role TEXT NOT NULL,
              text TEXT NOT NULL,
              timestamp INTEGER NOT NULL,
              retrieved_chunk_id TEXT,
              PRIMARY KEY (conversation_id, turn_idx)
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_turns_conversation_idx ON turns(conversation_id, turn_idx)")
    }

    private fun ensureConversation(
        database: SQLiteDatabase,
        conversationId: String,
        trailId: String?,
        now: Long
    ) {
        val values = ContentValues().apply {
            put("conversation_id", conversationId)
            put("started_at", now)
            put("last_active_at", now)
            put("trail_id", trailId)
            put("summary_token_count", 0)
        }
        database.insertWithOnConflict("conversations", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        val update = ContentValues().apply {
            put("last_active_at", now)
            if (trailId != null) {
                put("trail_id", trailId)
            }
        }
        database.update("conversations", update, "conversation_id = ?", arrayOf(conversationId))
    }

    private fun touchConversation(database: SQLiteDatabase, conversationId: String, now: Long) {
        val values = ContentValues().apply { put("last_active_at", now) }
        database.update("conversations", values, "conversation_id = ?", arrayOf(conversationId))
    }

    private fun nextTurnIndex(database: SQLiteDatabase, conversationId: String): Int =
        database.rawQuery(
            "SELECT COALESCE(MAX(turn_idx), -1) + 1 FROM turns WHERE conversation_id = ?",
            arrayOf(conversationId)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private fun readTurn(cursor: android.database.Cursor): ConversationTurn =
        ConversationTurn(
            conversationId = cursor.getString(0),
            turnIdx = cursor.getInt(1),
            role = ConversationRole.fromStorage(cursor.getString(2)),
            text = cursor.getString(3),
            timestamp = cursor.getLong(4),
            retrievedChunkId = cursor.getString(5)
        )

    private fun estimateTokens(value: String): Int =
        (value.split(Regex("\\s+")).count { it.isNotBlank() } * 1.35).toInt().coerceAtLeast(0)

    private companion object {
        private const val DatabaseName = "conversations.sqlite"
    }
}
