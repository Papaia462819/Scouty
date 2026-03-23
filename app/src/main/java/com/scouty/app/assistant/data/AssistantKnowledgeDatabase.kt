package com.scouty.app.assistant.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Entity(tableName = "knowledge_chunks")
data class KnowledgeChunkEntity(
    @PrimaryKey val id: String,
    val topic: String,
    val sourceTitle: String,
    val sectionTitle: String,
    val body: String,
    val language: String
)

@Fts4
@Entity(tableName = "knowledge_chunks_fts")
data class KnowledgeChunkFtsEntity(
    val chunkId: String,
    val topic: String,
    val sourceTitle: String,
    val sectionTitle: String,
    val body: String
)

@Dao
interface AssistantKnowledgeDao {
    @Query("SELECT COUNT(*) FROM knowledge_chunks")
    suspend fun countChunks(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<KnowledgeChunkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFtsChunks(chunks: List<KnowledgeChunkFtsEntity>)

    @Query("SELECT chunkId FROM knowledge_chunks_fts WHERE knowledge_chunks_fts MATCH :query LIMIT :limit")
    suspend fun searchChunkIds(query: String, limit: Int): List<String>

    @Query("SELECT * FROM knowledge_chunks WHERE id IN (:ids)")
    suspend fun getChunksByIds(ids: List<String>): List<KnowledgeChunkEntity>

    @Query("SELECT * FROM knowledge_chunks WHERE id IN (:ids) AND language = :language")
    suspend fun getChunksByIdsAndLanguage(ids: List<String>, language: String): List<KnowledgeChunkEntity>

    @Query("SELECT * FROM knowledge_chunks")
    suspend fun getAllChunks(): List<KnowledgeChunkEntity>

    @Query("SELECT * FROM knowledge_chunks WHERE language = :language")
    suspend fun getAllChunksByLanguage(language: String): List<KnowledgeChunkEntity>
}

@Database(
    entities = [KnowledgeChunkEntity::class, KnowledgeChunkFtsEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AssistantKnowledgeDatabase : RoomDatabase() {
    abstract fun knowledgeDao(): AssistantKnowledgeDao

    companion object {
        @Volatile
        private var instance: AssistantKnowledgeDatabase? = null

        fun getInstance(context: Context): AssistantKnowledgeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AssistantKnowledgeDatabase::class.java,
                    "assistant_knowledge.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}

@Serializable
private data class CorpusChunkDto(
    val id: String,
    val topic: String,
    val sourceTitle: String,
    val sectionTitle: String,
    val body: String,
    val language: String
)

private val corpusJson = Json { ignoreUnknownKeys = true }

class AssistantKnowledgeRepository(private val context: Context) {
    private val dao = AssistantKnowledgeDatabase.getInstance(context).knowledgeDao()

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (dao.countChunks() > 0) {
            return@withContext
        }
        val chunks = loadCorpusFromAssets()
        dao.insertChunks(chunks)
        dao.insertFtsChunks(
            chunks.map { chunk ->
                KnowledgeChunkFtsEntity(
                    chunkId = chunk.id,
                    topic = chunk.topic,
                    sourceTitle = chunk.sourceTitle,
                    sectionTitle = chunk.sectionTitle,
                    body = chunk.body
                )
            }
        )
    }

    suspend fun search(query: String, limit: Int): List<KnowledgeChunkEntity> = withContext(Dispatchers.IO) {
        val ftsQuery = buildFtsQuery(query)
        if (ftsQuery.isBlank()) {
            return@withContext emptyList()
        }
        val ids = dao.searchChunkIds(ftsQuery, limit)
        if (ids.isEmpty()) {
            return@withContext emptyList()
        }
        val chunksById = dao.getChunksByIds(ids).associateBy { it.id }
        ids.mapNotNull(chunksById::get)
    }

    suspend fun searchByLanguage(query: String, limit: Int, language: String): List<KnowledgeChunkEntity> =
        withContext(Dispatchers.IO) {
            val ftsQuery = buildFtsQuery(query)
            if (ftsQuery.isBlank()) {
                return@withContext emptyList()
            }
            val ids = dao.searchChunkIds(ftsQuery, limit * 3)
            if (ids.isEmpty()) {
                return@withContext emptyList()
            }
            val preferred = dao.getChunksByIdsAndLanguage(ids, language)
            if (preferred.size >= limit) {
                val idOrder = ids.withIndex().associate { (i, id) -> id to i }
                return@withContext preferred.sortedBy { idOrder[it.id] ?: Int.MAX_VALUE }.take(limit)
            }
            val allMatches = dao.getChunksByIds(ids)
            val idOrder = ids.withIndex().associate { (i, id) -> id to i }
            val sorted = allMatches.sortedWith(
                compareBy<KnowledgeChunkEntity> { if (it.language == language) 0 else 1 }
                    .thenBy { idOrder[it.id] ?: Int.MAX_VALUE }
            )
            sorted.take(limit)
        }

    suspend fun allChunks(): List<KnowledgeChunkEntity> = withContext(Dispatchers.IO) {
        dao.getAllChunks()
    }

    suspend fun allChunksByLanguage(language: String): List<KnowledgeChunkEntity> = withContext(Dispatchers.IO) {
        dao.getAllChunksByLanguage(language)
    }

    private fun loadCorpusFromAssets(): List<KnowledgeChunkEntity> {
        val jsonText = context.assets.open("knowledge_corpus.json").bufferedReader().use { it.readText() }
        val dtos = corpusJson.decodeFromString<List<CorpusChunkDto>>(jsonText)
        return dtos.map { dto ->
            KnowledgeChunkEntity(
                id = dto.id,
                topic = dto.topic,
                sourceTitle = dto.sourceTitle,
                sectionTitle = dto.sectionTitle,
                body = dto.body,
                language = dto.language
            )
        }
    }

    private fun buildFtsQuery(rawQuery: String): String =
        rawQuery
            .lowercase()
            .replace("[^a-z0-9ăâîșşțţ ]".toRegex(), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }
            .joinToString(" OR ") { "$it*" }
}
