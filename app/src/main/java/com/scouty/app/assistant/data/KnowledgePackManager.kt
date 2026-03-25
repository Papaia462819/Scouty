package com.scouty.app.assistant.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.scouty.app.assistant.model.KnowledgeChunkRecord
import com.scouty.app.assistant.model.KnowledgePackManifest
import com.scouty.app.assistant.model.KnowledgePackStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer

interface KnowledgeChunkStore {
    suspend fun packStatus(): KnowledgePackStatus
    suspend fun searchCandidates(
        query: String,
        preferredLanguages: List<String>,
        domainHints: List<String>,
        limit: Int = 24
    ): List<KnowledgeChunkRecord>
}

interface KnowledgePackStatusProvider {
    val status: StateFlow<KnowledgePackStatus>

    suspend fun ensureReady(): KnowledgePackStatus

    fun currentStatus(): KnowledgePackStatus = status.value
}

class KnowledgePackManager(private val context: Context) : KnowledgePackStatusProvider {
    private val json = Json { ignoreUnknownKeys = true }
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    private val installDir = File(context.noBackupFilesDir, "knowledge_pack")
    private val databaseFile = File(installDir, DatabaseAssetName)
    private val manifestFile = File(installDir, ManifestAssetName)
    private val mutex = Mutex()
    private val _status = MutableStateFlow(KnowledgePackStatus())
    override val status: StateFlow<KnowledgePackStatus> = _status.asStateFlow()

    override suspend fun ensureReady(): KnowledgePackStatus = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val manifest = readAssetManifest()
                installDir.mkdirs()
                if (shouldInstall(manifest)) {
                    copyAsset(DatabaseAssetName, databaseFile)
                    copyAsset(ManifestAssetName, manifestFile)
                    preferences.edit()
                        .putString(InstalledVersionKey, manifest.packVersion)
                        .putLong(InstalledAtKey, System.currentTimeMillis())
                        .apply()
                }

                val hashValid = databaseFile.exists() && sha256(databaseFile) == manifest.dbSha256
                val integrityValid = if (hashValid) runIntegrityCheck(databaseFile) else false

                KnowledgePackStatus(
                    available = databaseFile.exists() && manifestFile.exists(),
                    packVersion = manifest.packVersion,
                    generatedAt = manifest.generatedAt,
                    expectedChunkCount = manifest.chunkCount,
                    sourceCount = manifest.sourceCount,
                    hashValid = hashValid,
                    integrityValid = integrityValid,
                    installedAtEpochMs = preferences.getLong(InstalledAtKey, 0L).takeIf { it > 0L },
                    databasePath = databaseFile.absolutePath,
                    errorMessage = if (integrityValid) null else "Knowledge pack integrity check failed"
                )
            }.getOrElse { error ->
                KnowledgePackStatus(
                    available = false,
                    errorMessage = error.message ?: error::class.java.simpleName
                )
            }.also { resolvedStatus ->
                _status.value = resolvedStatus
            }
        }
    }

    private fun shouldInstall(manifest: KnowledgePackManifest): Boolean {
        if (!databaseFile.exists() || !manifestFile.exists()) {
            return true
        }
        val installedVersion = preferences.getString(InstalledVersionKey, null)
        if (installedVersion != manifest.packVersion) {
            return true
        }
        return sha256(databaseFile) != manifest.dbSha256
    }

    private fun readAssetManifest(): KnowledgePackManifest {
        val raw = context.assets.open(ManifestAssetName).bufferedReader().use { it.readText() }
        return json.decodeFromString(KnowledgePackManifest.serializer(), raw)
    }

    private fun copyAsset(assetName: String, target: File) {
        val tempFile = File(target.parentFile, "${target.name}.tmp")
        context.assets.open(assetName).use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
        if (target.exists()) {
            target.delete()
        }
        tempFile.renameTo(target)
    }

    private fun runIntegrityCheck(file: File): Boolean {
        var database: SQLiteDatabase? = null
        return try {
            database = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val cursor = database.rawQuery("PRAGMA integrity_check", emptyArray())
            cursor.use {
                it.moveToFirst()
                it.getString(0).equals("ok", ignoreCase = true)
            }
        } catch (_: Exception) {
            false
        } finally {
            database?.close()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        private const val PreferencesName = "scouty_knowledge_pack"
        private const val DatabaseAssetName = "knowledge_pack.sqlite"
        private const val ManifestAssetName = "knowledge_pack_manifest.json"
        private const val InstalledVersionKey = "installed_version"
        private const val InstalledAtKey = "installed_at"
    }
}

class SqliteKnowledgeChunkStore(
    private val manager: KnowledgePackManager
) : KnowledgeChunkStore {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun packStatus(): KnowledgePackStatus = manager.ensureReady()

    override suspend fun searchCandidates(
        query: String,
        preferredLanguages: List<String>,
        domainHints: List<String>,
        limit: Int
    ): List<KnowledgeChunkRecord> = withContext(Dispatchers.IO) {
        val status = manager.ensureReady()
        if (!status.isReady || status.databasePath.isNullOrBlank()) {
            return@withContext emptyList()
        }

        val tokens = buildSearchTokens(query)
        val ftsQuery = buildFtsQuery(tokens)
        val domainWindow = domainHints.take(3)
        val results = LinkedHashMap<String, KnowledgeChunkRecord>()
        var database: SQLiteDatabase? = null

        try {
            database = SQLiteDatabase.openDatabase(status.databasePath, null, SQLiteDatabase.OPEN_READONLY)
            if (ftsQuery != null) {
                if (domainWindow.isNotEmpty()) {
                    absorb(results, queryFts(database, ftsQuery, preferredLanguages, domainWindow, limit * 3))
                }
                absorb(results, queryFts(database, ftsQuery, preferredLanguages, emptyList(), limit * 3))
                absorb(results, queryFts(database, ftsQuery, listOf("ro", "en"), emptyList(), limit * 4))
            }

            if (results.size < limit * 2) {
                absorb(results, queryRecent(database, preferredLanguages, domainWindow, limit * 2))
                absorb(results, queryRecent(database, listOf("ro", "en"), emptyList(), limit * 2))
            }
        } finally {
            database?.close()
        }

        results.values.take(limit * 4).toList()
    }

    private fun absorb(
        target: LinkedHashMap<String, KnowledgeChunkRecord>,
        source: List<KnowledgeChunkRecord>
    ) {
        source.forEach { chunk -> target.putIfAbsent(chunk.chunkId, chunk) }
    }

    private fun queryFts(
        database: SQLiteDatabase,
        ftsQuery: String,
        languages: List<String>,
        domains: List<String>,
        limit: Int
    ): List<KnowledgeChunkRecord> {
        val sql = buildString {
            append(
                """
                SELECT kc.chunk_id, kc.domain, kc.topic, kc.language, kc.title, kc.body,
                       kc.source_title, kc.source_url, kc.publisher, kc.source_language,
                       kc.adapted_language, kc.publish_or_review_date, kc.source_trust,
                       kc.safety_tags, kc.country_scope, kc.pack_version, kc.keywords
                FROM knowledge_chunks kc
                JOIN knowledge_chunks_fts fts ON kc.row_id = fts.rowid
                WHERE knowledge_chunks_fts MATCH ?
                """.trimIndent()
            )
            append(" AND kc.language IN (${languages.joinToString(",") { "?" }})")
            if (domains.isNotEmpty()) {
                append(" AND kc.domain IN (${domains.joinToString(",") { "?" }})")
            }
            append(" LIMIT ?")
        }

        val args = buildList {
            add(ftsQuery)
            addAll(languages)
            addAll(domains)
            add(limit.toString())
        }.toTypedArray()
        return database.rawQuery(sql, args).use { cursor -> readChunks(cursor) }
    }

    private fun queryRecent(
        database: SQLiteDatabase,
        languages: List<String>,
        domains: List<String>,
        limit: Int
    ): List<KnowledgeChunkRecord> {
        val sql = buildString {
            append(
                """
                SELECT kc.chunk_id, kc.domain, kc.topic, kc.language, kc.title, kc.body,
                       kc.source_title, kc.source_url, kc.publisher, kc.source_language,
                       kc.adapted_language, kc.publish_or_review_date, kc.source_trust,
                       kc.safety_tags, kc.country_scope, kc.pack_version, kc.keywords
                FROM knowledge_chunks kc
                WHERE kc.language IN (${languages.joinToString(",") { "?" }})
                """.trimIndent()
            )
            if (domains.isNotEmpty()) {
                append(" AND kc.domain IN (${domains.joinToString(",") { "?" }})")
            }
            append(" ORDER BY kc.source_trust DESC, kc.publish_or_review_date DESC LIMIT ?")
        }

        val args = buildList {
            addAll(languages)
            addAll(domains)
            add(limit.toString())
        }.toTypedArray()
        return database.rawQuery(sql, args).use { cursor -> readChunks(cursor) }
    }

    private fun readChunks(cursor: android.database.Cursor): List<KnowledgeChunkRecord> {
        val chunks = mutableListOf<KnowledgeChunkRecord>()
        while (cursor.moveToNext()) {
            chunks += KnowledgeChunkRecord(
                chunkId = cursor.getString(0),
                domain = cursor.getString(1),
                topic = cursor.getString(2),
                language = cursor.getString(3),
                title = cursor.getString(4),
                body = cursor.getString(5),
                sourceTitle = cursor.getString(6),
                sourceUrl = cursor.getString(7),
                publisher = cursor.getString(8),
                sourceLanguage = cursor.getString(9),
                adaptedLanguage = cursor.getString(10),
                publishOrReviewDate = cursor.getString(11),
                sourceTrust = cursor.getInt(12),
                safetyTags = parseSafetyTags(cursor.getString(13)),
                countryScope = cursor.getString(14),
                packVersion = cursor.getString(15),
                keywords = cursor.getString(16)
            )
        }
        return chunks
    }

    private fun parseSafetyTags(rawValue: String?): List<String> {
        if (rawValue.isNullOrBlank()) {
            return emptyList()
        }
        return runCatching {
            json.decodeFromString<List<String>>(rawValue)
        }.getOrElse {
            rawValue.split(',').map { it.trim() }.filter { it.isNotBlank() }
        }
    }
}

fun buildSearchTokens(rawQuery: String): List<String> =
    normalizeSearchText(rawQuery)
        .split(Regex("\\s+"))
        .asSequence()
        .map { it.trim() }
        .filter { it.length >= 2 }
        .filterNot { it in SearchStopWords }
        .flatMap { expandSearchToken(it).asSequence() }
        .filter { it.length >= 2 }
        .filterNot { it in SearchStopWords }
        .distinct()
        .toList()

private fun buildFtsQuery(tokens: List<String>): String? =
    tokens.takeIf { it.isNotEmpty() }?.joinToString(" OR ") { "$it*" }

private fun normalizeSearchText(rawValue: String): String =
    Normalizer.normalize(rawValue.lowercase(), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .replace("[^a-z0-9 ]".toRegex(), " ")

private fun expandSearchToken(token: String): List<String> {
    val variants = linkedSetOf(token)
    romanianBaseForm(token)?.let { variants += it }
    return variants.toList()
}

private fun romanianBaseForm(token: String): String? =
    when {
        token.length <= 4 -> null
        token.endsWith("ului") && token.length > 6 -> token.dropLast(5)
        token.endsWith("eul") && token.length > 5 -> token.dropLast(1)
        token.endsWith("ul") && token.length > 4 -> token.dropLast(2)
        token.endsWith("le") && token.length > 4 -> token.dropLast(2)
        token.endsWith("ilor") && token.length > 6 -> token.dropLast(4)
        token.endsWith("elor") && token.length > 6 -> token.dropLast(4)
        token.endsWith("lor") && token.length > 5 -> token.dropLast(3)
        else -> null
    }?.takeIf { it.length >= 2 && it != token }

private val SearchStopWords = setOf(
    "a", "ai", "ale", "am", "as", "at", "au", "ca", "care", "cand", "ce", "cum",
    "cu", "de", "despre", "din", "do", "este", "fac", "for", "how", "i", "in",
    "is", "la", "mai", "mi", "my", "or", "pe", "sa", "si", "sunt", "the", "to",
    "un", "una", "unde", "what", "when", "where"
)
