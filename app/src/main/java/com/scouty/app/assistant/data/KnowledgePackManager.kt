package com.scouty.app.assistant.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.scouty.app.assistant.diagnostics.AssistantDiagnostics
import com.scouty.app.assistant.model.CardFamily
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.text.Normalizer

data class CampfireCardEmbedding(
    val cardId: String,
    val topic: String,
    val language: String,
    val queryEmbedding: FloatArray,
    val contentEmbedding: FloatArray,
    val modelName: String,
    val backendLabel: String,
    val dimension: Int
)

data class CampfirePhrasingEmbedding(
    val cardId: String,
    val topic: String,
    val language: String,
    val phraseText: String,
    val normalizedPhrase: String,
    val phraseKind: String,
    val embedding: FloatArray,
    val modelName: String,
    val backendLabel: String,
    val dimension: Int
)

data class CampfireEmbeddingStore(
    val topic: String,
    val language: String,
    val cardEmbeddings: Map<String, CampfireCardEmbedding> = emptyMap(),
    val phrasingEmbeddings: List<CampfirePhrasingEmbedding> = emptyList(),
    val modelName: String? = null,
    val backendLabel: String? = null,
    val dimension: Int = 0
) {
    val isEmpty: Boolean
        get() = cardEmbeddings.isEmpty() || phrasingEmbeddings.isEmpty()
}

interface KnowledgeChunkStore {
    suspend fun packStatus(): KnowledgePackStatus
    suspend fun searchCandidates(
        query: String,
        preferredLanguages: List<String>,
        domainHints: List<String>,
        limit: Int = 24
    ): List<KnowledgeChunkRecord>

    suspend fun searchStructuredCards(
        query: String,
        preferredLanguage: String,
        domain: String,
        topic: String,
        family: CardFamily? = null,
        limit: Int = 24
    ): List<KnowledgeChunkRecord> =
        searchCandidates(
            query = query,
            preferredLanguages = listOf(preferredLanguage),
            domainHints = listOf(domain),
            limit = limit * 2
        ).filter { chunk ->
            chunk.domain == domain &&
                chunk.topic == topic &&
                chunk.language == preferredLanguage &&
                (family == null || chunk.cardFamily == family)
        }.sortedWith(
            compareByDescending<KnowledgeChunkRecord> { it.priority }
                .thenBy { it.title }
        ).take(limit)

    suspend fun loadCampfireEmbeddingStore(
        preferredLanguage: String,
        topic: String
    ): CampfireEmbeddingStore = CampfireEmbeddingStore(topic = topic, language = preferredLanguage)
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
            database = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            database.rawQuery("PRAGMA integrity_check", emptyArray()).use { cursor ->
                val rows = buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(0))
                    }
                }
                rows.size == 1 && rows.first().equals("ok", ignoreCase = true)
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
    private val campfireEmbeddingCache = mutableMapOf<String, CampfireEmbeddingStore>()

    override suspend fun packStatus(): KnowledgePackStatus = manager.ensureReady()

    override suspend fun searchCandidates(
        query: String,
        preferredLanguages: List<String>,
        domainHints: List<String>,
        limit: Int
    ): List<KnowledgeChunkRecord> = withContext(Dispatchers.IO) {
        val status = manager.ensureReady()
        val tokens = buildSearchTokens(query)
        val ftsQuery = buildFtsQuery(tokens)
        if (!status.isReady || status.databasePath.isNullOrBlank()) {
            AssistantDiagnostics.logSqliteSearch(
                query = query,
                preferredLanguages = preferredLanguages,
                domainHints = domainHints,
                tokens = tokens,
                ftsQuery = ftsQuery,
                packStatus = status,
                candidates = emptyList()
            )
            return@withContext emptyList()
        }

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

        results.values.take(limit * 4).toList().also { candidates ->
            AssistantDiagnostics.logSqliteSearch(
                query = query,
                preferredLanguages = preferredLanguages,
                domainHints = domainHints,
                tokens = tokens,
                ftsQuery = ftsQuery,
                packStatus = status,
                candidates = candidates
            )
        }
    }

    override suspend fun searchStructuredCards(
        query: String,
        preferredLanguage: String,
        domain: String,
        topic: String,
        family: CardFamily?,
        limit: Int
    ): List<KnowledgeChunkRecord> = withContext(Dispatchers.IO) {
        val status = manager.ensureReady()
        val tokens = buildSearchTokens(query)
        val ftsQuery = buildFtsQuery(tokens)
        if (!status.isReady || status.databasePath.isNullOrBlank()) {
            return@withContext emptyList()
        }

        var database: SQLiteDatabase? = null
        try {
            database = SQLiteDatabase.openDatabase(status.databasePath, null, SQLiteDatabase.OPEN_READONLY)
            if (ftsQuery != null) {
                val matched = queryStructuredFts(
                    database = database,
                    ftsQuery = ftsQuery,
                    preferredLanguage = preferredLanguage,
                    domain = domain,
                    topic = topic,
                    family = family,
                    limit = limit
                )
                if (matched.isNotEmpty()) {
                    return@withContext matched
                }
            }
            queryStructuredByTopic(
                database = database,
                preferredLanguage = preferredLanguage,
                domain = domain,
                topic = topic,
                family = family,
                limit = limit
            )
        } finally {
            database?.close()
        }
    }

    override suspend fun loadCampfireEmbeddingStore(
        preferredLanguage: String,
        topic: String
    ): CampfireEmbeddingStore = withContext(Dispatchers.IO) {
        val status = manager.ensureReady()
        if (!status.isReady || status.databasePath.isNullOrBlank()) {
            return@withContext CampfireEmbeddingStore(topic = topic, language = preferredLanguage)
        }

        val cacheKey = listOfNotNull(status.packVersion, preferredLanguage, topic).joinToString(":")
        campfireEmbeddingCache[cacheKey]?.let { return@withContext it }

        var database: SQLiteDatabase? = null
        val resolvedStore = try {
            database = SQLiteDatabase.openDatabase(status.databasePath, null, SQLiteDatabase.OPEN_READONLY)
            val cardEmbeddings = queryCardEmbeddings(
                database = database,
                preferredLanguage = preferredLanguage,
                topic = topic
            )
            val phrasingEmbeddings = queryPhrasingEmbeddings(
                database = database,
                preferredLanguage = preferredLanguage,
                topic = topic
            )
            CampfireEmbeddingStore(
                topic = topic,
                language = preferredLanguage,
                cardEmbeddings = cardEmbeddings.associateBy { it.cardId },
                phrasingEmbeddings = phrasingEmbeddings,
                modelName = cardEmbeddings.firstOrNull()?.modelName ?: phrasingEmbeddings.firstOrNull()?.modelName,
                backendLabel = cardEmbeddings.firstOrNull()?.backendLabel ?: phrasingEmbeddings.firstOrNull()?.backendLabel,
                dimension = cardEmbeddings.firstOrNull()?.dimension ?: phrasingEmbeddings.firstOrNull()?.dimension ?: 0
            )
        } catch (_: Exception) {
            CampfireEmbeddingStore(topic = topic, language = preferredLanguage)
        } finally {
            database?.close()
        }

        campfireEmbeddingCache[cacheKey] = resolvedStore
        resolvedStore
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
                       kc.safety_tags, kc.country_scope, kc.pack_version, kc.keywords,
                       kc.card_family, kc.priority, kc.metadata_json
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
                       kc.safety_tags, kc.country_scope, kc.pack_version, kc.keywords,
                       kc.card_family, kc.priority, kc.metadata_json
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

    private fun queryStructuredFts(
        database: SQLiteDatabase,
        ftsQuery: String,
        preferredLanguage: String,
        domain: String,
        topic: String,
        family: CardFamily?,
        limit: Int
    ): List<KnowledgeChunkRecord> {
        val sql = buildString {
            append(
                """
                SELECT kc.chunk_id, kc.domain, kc.topic, kc.language, kc.title, kc.body,
                       kc.source_title, kc.source_url, kc.publisher, kc.source_language,
                       kc.adapted_language, kc.publish_or_review_date, kc.source_trust,
                       kc.safety_tags, kc.country_scope, kc.pack_version, kc.keywords,
                       kc.card_family, kc.priority, kc.metadata_json
                FROM knowledge_chunks kc
                JOIN knowledge_chunks_fts fts ON kc.row_id = fts.rowid
                WHERE knowledge_chunks_fts MATCH ?
                  AND kc.domain = ?
                  AND kc.topic = ?
                  AND kc.language = ?
                """.trimIndent()
            )
            if (family != null) {
                append(" AND kc.card_family = ?")
            }
            append(" ORDER BY kc.priority DESC, kc.title ASC LIMIT ?")
        }
        val args = buildList {
            add(ftsQuery)
            add(domain)
            add(topic)
            add(preferredLanguage)
            family?.let { add(it.name.lowercase()) }
            add(limit.toString())
        }.toTypedArray()
        return database.rawQuery(sql, args).use { cursor -> readChunks(cursor) }
    }

    private fun queryStructuredByTopic(
        database: SQLiteDatabase,
        preferredLanguage: String,
        domain: String,
        topic: String,
        family: CardFamily?,
        limit: Int
    ): List<KnowledgeChunkRecord> {
        val sql = buildString {
            append(
                """
                SELECT kc.chunk_id, kc.domain, kc.topic, kc.language, kc.title, kc.body,
                       kc.source_title, kc.source_url, kc.publisher, kc.source_language,
                       kc.adapted_language, kc.publish_or_review_date, kc.source_trust,
                       kc.safety_tags, kc.country_scope, kc.pack_version, kc.keywords,
                       kc.card_family, kc.priority, kc.metadata_json
                FROM knowledge_chunks kc
                WHERE kc.domain = ?
                  AND kc.topic = ?
                  AND kc.language = ?
                """.trimIndent()
            )
            if (family != null) {
                append(" AND kc.card_family = ?")
            }
            append(" ORDER BY kc.priority DESC, kc.title ASC LIMIT ?")
        }
        val args = buildList {
            add(domain)
            add(topic)
            add(preferredLanguage)
            family?.let { add(it.name.lowercase()) }
            add(limit.toString())
        }.toTypedArray()
        return database.rawQuery(sql, args).use { cursor -> readChunks(cursor) }
    }

    private fun queryCardEmbeddings(
        database: SQLiteDatabase,
        preferredLanguage: String,
        topic: String
    ): List<CampfireCardEmbedding> {
        val sql = """
            SELECT card_id, topic, language, query_embedding, content_embedding,
                   embedding_model, embedding_backend, embedding_dimension
            FROM card_embeddings
            WHERE topic = ? AND language = ?
        """.trimIndent()
        return database.rawQuery(sql, arrayOf(topic, preferredLanguage)).use { cursor ->
            val rows = mutableListOf<CampfireCardEmbedding>()
            while (cursor.moveToNext()) {
                rows += CampfireCardEmbedding(
                    cardId = cursor.getString(0),
                    topic = cursor.getString(1),
                    language = cursor.getString(2),
                    queryEmbedding = blobToFloatArray(cursor.getBlob(3)),
                    contentEmbedding = blobToFloatArray(cursor.getBlob(4)),
                    modelName = cursor.getString(5),
                    backendLabel = cursor.getString(6),
                    dimension = cursor.getInt(7)
                )
            }
            rows
        }
    }

    private fun queryPhrasingEmbeddings(
        database: SQLiteDatabase,
        preferredLanguage: String,
        topic: String
    ): List<CampfirePhrasingEmbedding> {
        val sql = """
            SELECT card_id, topic, language, phrase_text, normalized_phrase, phrase_kind,
                   embedding, embedding_model, embedding_backend, embedding_dimension
            FROM phrasing_embeddings
            WHERE topic = ? AND language = ?
        """.trimIndent()
        return database.rawQuery(sql, arrayOf(topic, preferredLanguage)).use { cursor ->
            val rows = mutableListOf<CampfirePhrasingEmbedding>()
            while (cursor.moveToNext()) {
                rows += CampfirePhrasingEmbedding(
                    cardId = cursor.getString(0),
                    topic = cursor.getString(1),
                    language = cursor.getString(2),
                    phraseText = cursor.getString(3),
                    normalizedPhrase = cursor.getString(4),
                    phraseKind = cursor.getString(5),
                    embedding = blobToFloatArray(cursor.getBlob(6)),
                    modelName = cursor.getString(7),
                    backendLabel = cursor.getString(8),
                    dimension = cursor.getInt(9)
                )
            }
            rows
        }
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
                keywords = cursor.getString(16),
                cardFamily = parseCardFamily(cursor.getString(17)),
                priority = cursor.getInt(18),
                metadataJson = cursor.getString(19)
            )
        }
        return chunks
    }

    private fun blobToFloatArray(blob: ByteArray?): FloatArray {
        if (blob == null || blob.isEmpty() || blob.size % 4 != 0) {
            return FloatArray(0)
        }
        val floatCount = blob.size / 4
        val floatArray = FloatArray(floatCount)
        val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
        for (index in 0 until floatCount) {
            floatArray[index] = buffer.float
        }
        return floatArray
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

    private fun parseCardFamily(rawValue: String?): CardFamily? =
        when (rawValue?.trim()?.lowercase()) {
            "scenario" -> CardFamily.SCENARIO
            "definition" -> CardFamily.DEFINITION
            "constraint" -> CardFamily.CONSTRAINT
            else -> null
        }
}

fun buildSearchTokens(rawQuery: String, shouldLog: Boolean = true): List<String> =
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
        .also { tokens ->
            if (shouldLog) {
                AssistantDiagnostics.logBuildSearchTokens(rawQuery, tokens)
            }
        }

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
