package com.scouty.app.assistant.domain

import com.scouty.app.assistant.data.KnowledgeChunkStore
import com.scouty.app.assistant.data.buildSearchTokens
import com.scouty.app.assistant.model.KnowledgeChunkRecord
import com.scouty.app.assistant.model.KnowledgePackStatus
import com.scouty.app.assistant.model.TrailContextSnapshot
import com.scouty.app.assistant.model.DeviceContextSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetrievalEngineTest {
    @Test
    fun retrieve_prefersRomanianChunkForRomanianSafetyQuery() = runBlocking {
        val store = FakeKnowledgeStore(
            listOf(
                chunk(
                    chunkId = "snake_ro",
                    domain = "wildlife_romania",
                    topic = "snakebite_response",
                    language = "ro",
                    title = "Ce faci dupa o muscatura de sarpe",
                    body = "Imobilizeaza victima si cauta ajutor medical rapid.",
                    sourceTrust = 5,
                    safetyTags = listOf("snakebite")
                ),
                chunk(
                    chunkId = "snake_en",
                    domain = "wildlife_romania",
                    topic = "snakebite_response",
                    language = "en",
                    title = "What to do after a snakebite",
                    body = "Immobilize the person and seek medical care quickly.",
                    sourceTrust = 5,
                    safetyTags = listOf("snakebite")
                ),
                chunk(
                    chunkId = "bear_ro",
                    domain = "wildlife_romania",
                    topic = "bear_encounter",
                    language = "ro",
                    title = "Intalnire cu ursul",
                    body = "Pastreaza distanta si retrage-te lent.",
                    sourceTrust = 5,
                    safetyTags = listOf("bear")
                )
            )
        )
        val engine = RetrievalEngine(store)
        val context = DeviceContextSnapshot(localeTag = "ro")
        val analysis = engine.analyze("M-a muscat un sarpe pe traseu", context)

        val results = engine.retrieve(
            query = "M-a muscat un sarpe pe traseu",
            context = context,
            queryAnalysis = analysis,
            limit = 2
        )

        assertEquals("snakebite_response", results.first().topic)
        assertEquals("ro", results.first().language)
    }

    @Test
    fun retrieve_boostsActiveTrailChunkForRouteContext() = runBlocking {
        val store = FakeKnowledgeStore(
            listOf(
                chunk(
                    chunkId = "route_01",
                    domain = "route_intelligence_romania",
                    topic = "01MN01",
                    language = "ro",
                    title = "Sinaia - Cabana Padina",
                    body = "Marcaj: banda rosie. Date cheie: durata 6h.",
                    sourceTrust = 5
                ),
                chunk(
                    chunkId = "route_02",
                    domain = "route_intelligence_romania",
                    topic = "02MN10",
                    language = "ro",
                    title = "Alta ruta",
                    body = "Marcaj: triunghi albastru.",
                    sourceTrust = 5
                )
            )
        )
        val engine = RetrievalEngine(store)
        val context = DeviceContextSnapshot(
            localeTag = "ro",
            trail = TrailContextSnapshot(
                name = "Sinaia - Cabana Padina",
                localCode = "01MN01",
                markingLabel = "banda rosie"
            )
        )
        val analysis = engine.analyze("Care e marcajul traseului activ?", context)

        val results = engine.retrieve(
            query = "Care e marcajul traseului activ?",
            context = context,
            queryAnalysis = analysis,
            limit = 1
        )

        assertEquals("01MN01", results.first().topic)
        assertTrue(results.first().body.contains("Marcaj"))
    }

    @Test
    fun retrieve_handlesRomanianDefiniteArticlesForSurvivalQuery() = runBlocking {
        val store = FakeKnowledgeStore(
            listOf(
                chunk(
                    chunkId = "campfire_ro",
                    domain = "survival_basics",
                    topic = "campfire",
                    language = "ro",
                    title = "Foc de tabara in siguranta",
                    body = "Pastreaza focul mic, controlabil si departe de vegetatie uscata.",
                    sourceTrust = 5
                ),
                chunk(
                    chunkId = "water_ro",
                    domain = "survival_basics",
                    topic = "water",
                    language = "ro",
                    title = "Purificarea apei",
                    body = "Fierbe apa sau foloseste un filtru adecvat.",
                    sourceTrust = 5
                )
            )
        )
        val engine = RetrievalEngine(store)
        val context = DeviceContextSnapshot(localeTag = "ro")
        val analysis = engine.analyze("Cum fac focul?", context)

        val results = engine.retrieve(
            query = "Cum fac focul?",
            context = context,
            queryAnalysis = analysis,
            limit = 1
        )

        assertEquals("campfire", results.first().topic)
        assertTrue(analysis.tokens.contains("foc"))
        assertTrue(analysis.tokens.none { it == "cum" || it == "fac" })
    }

    private fun chunk(
        chunkId: String,
        domain: String,
        topic: String,
        language: String,
        title: String,
        body: String,
        sourceTrust: Int,
        safetyTags: List<String> = emptyList()
    ) = KnowledgeChunkRecord(
        chunkId = chunkId,
        domain = domain,
        topic = topic,
        language = language,
        title = title,
        body = body,
        sourceTitle = "Test source",
        publisher = "Test publisher",
        sourceLanguage = language,
        adaptedLanguage = language,
        sourceTrust = sourceTrust,
        safetyTags = safetyTags,
        packVersion = "test-pack"
    )
}

private class FakeKnowledgeStore(
    private val chunks: List<KnowledgeChunkRecord>
) : KnowledgeChunkStore {
    override suspend fun packStatus(): KnowledgePackStatus =
        KnowledgePackStatus(available = true, packVersion = "test-pack", hashValid = true, integrityValid = true)

    override suspend fun searchCandidates(
        query: String,
        preferredLanguages: List<String>,
        domainHints: List<String>,
        limit: Int
    ): List<KnowledgeChunkRecord> {
        val tokens = buildSearchTokens(query)
        return chunks
            .filter { chunk ->
                (domainHints.isEmpty() || chunk.domain in domainHints || chunk.topic in domainHints) &&
                    tokens.any { token ->
                        chunk.title.lowercase().contains(token) || chunk.body.lowercase().contains(token)
                    }
            }
            .sortedByDescending { if (it.language in preferredLanguages) 1 else 0 }
            .take(limit)
    }
}
