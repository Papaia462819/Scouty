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

    @Query("SELECT * FROM knowledge_chunks")
    suspend fun getAllChunks(): List<KnowledgeChunkEntity>
}

@Database(
    entities = [KnowledgeChunkEntity::class, KnowledgeChunkFtsEntity::class],
    version = 1,
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
                ).build().also { instance = it }
            }
    }
}

class AssistantKnowledgeRepository(context: Context) {
    private val dao = AssistantKnowledgeDatabase.getInstance(context).knowledgeDao()

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (dao.countChunks() > 0) {
            return@withContext
        }
        val chunks = seedKnowledgeChunks()
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

    suspend fun allChunks(): List<KnowledgeChunkEntity> = withContext(Dispatchers.IO) {
        dao.getAllChunks()
    }

    private fun buildFtsQuery(rawQuery: String): String =
        rawQuery
            .lowercase()
            .replace("[^a-z0-9ăâîșşțţ ]".toRegex(), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }
            .joinToString(" OR ") { "$it*" }
}

private fun seedKnowledgeChunks(): List<KnowledgeChunkEntity> = listOf(
    KnowledgeChunkEntity(
        id = "ankle-basic",
        topic = "ankle",
        sourceTitle = "Scouty First Aid Field Notes",
        sectionTitle = "Entorsă / gleznă",
        body = "Dacă ți-ai sucit glezna, oprește mersul, redu sprijinul pe picior, protejează articulația și aplică răcire locală dacă poți. Dacă durerea, umflătura sau instabilitatea cresc rapid, nu continua traseul.",
        language = "ro"
    ),
    KnowledgeChunkEntity(
        id = "ankle-red-flags",
        topic = "ankle",
        sourceTitle = "Scouty First Aid Field Notes",
        sectionTitle = "Red flags după traumatism",
        body = "Durere severă, deformare, imposibilitate de a călca, amorțeală, sângerare sau pierderea sensibilității sunt semne de alarmă. În astfel de cazuri prioritizează 112 sau un mesaj SOS și evită deplasarea inutilă.",
        language = "ro"
    ),
    KnowledgeChunkEntity(
        id = "water-purification",
        topic = "water",
        sourceTitle = "Scouty Survival Notes",
        sectionTitle = "Purificarea apei",
        body = "Cea mai sigură variantă în teren este filtrare urmată de fierbere. Dacă nu poți fierbe apa, folosește tablete dedicate conform instrucțiunilor și evită sursele aflate aproape de animale sau de apă stagnantă.",
        language = "ro"
    ),
    KnowledgeChunkEntity(
        id = "bear-signs",
        topic = "wildlife",
        sourceTitle = "Scouty Wildlife Notes",
        sectionTitle = "Urme de urs",
        body = "Dacă observi urme proaspete de urs, oprește-te, fă zgomot controlat, nu te apropia de zona cu urme și retrage-te calm pe traseu. Nu alerga și nu lăsa mâncare expusă.",
        language = "ro"
    ),
    KnowledgeChunkEntity(
        id = "hypothermia",
        topic = "cold",
        sourceTitle = "Scouty First Aid Field Notes",
        sectionTitle = "Hipotermie",
        body = "Frison puternic, confuzie, vorbire lentă și coordonare slabă pot indica hipotermie. Oprește expunerea, izolează persoana de sol, adaugă straturi uscate și cere ajutor dacă starea se agravează.",
        language = "ro"
    ),
    KnowledgeChunkEntity(
        id = "bleeding",
        topic = "bleeding",
        sourceTitle = "Scouty First Aid Field Notes",
        sectionTitle = "Sângerare",
        body = "Pentru sângerare externă aplică presiune directă și folosește pansament curat. Dacă sângerarea este masivă sau nu se oprește, activează imediat 112 și prioritizează controlul hemoragiei.",
        language = "ro"
    )
)
