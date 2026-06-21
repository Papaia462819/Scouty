package com.scouty.app.assistant.domain

import com.scouty.app.assistant.data.KnowledgeChunkStore
import com.scouty.app.assistant.model.AssistantConversationState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.Normalizer

/**
 * Offline, non-ML spell corrector (SymSpell — symmetric delete). It maps a
 * mistyped token to the closest real token from the knowledge-pack vocabulary,
 * biased by frequency. It can only ever return a word that exists in the pack,
 * so it cannot invent garbage. Correction is intended for retrieval, not display.
 */
class SpellCorrector(
    private val vocabulary: Map<String, Long>,
    private val maxEditDistance: Int = 2,
    private val minTokenLength: Int = 4
) {
    // delete-variant -> set of real terms that produce it after <= maxEditDistance deletes
    private val deletes: HashMap<String, MutableSet<String>> = HashMap()

    init {
        for (term in vocabulary.keys) {
            for (variant in editsWithin(term)) {
                deletes.getOrPut(variant) { HashSet() }.add(term)
            }
        }
    }

    val isEmpty: Boolean get() = vocabulary.isEmpty()

    /** Correct a single normalized token. Returns the token unchanged when no safe fix exists. */
    fun correctToken(token: String): String {
        if (token.length < minTokenLength || token.any { it.isDigit() }) return token
        if (vocabulary.containsKey(token)) return token
        // A token that is already a prefix of a real vocabulary word is a valid stem
        // (e.g. "sting" -> "stinge", "focu" -> "focul"); FTS prefix matching covers it.
        // Correcting it risks turning a real word into a different one ("sting" -> "stang").
        if (isPrefixOfVocabTerm(token)) return token

        val candidates = HashSet<String>()
        for (variant in editsWithin(token)) {
            deletes[variant]?.let { candidates.addAll(it) }
        }
        if (candidates.isEmpty()) return token

        var best: String? = null
        var bestDistance = Int.MAX_VALUE
        var bestFreq = -1L
        for (candidate in candidates) {
            val distance = boundedLevenshtein(token, candidate, maxEditDistance)
            if (distance > maxEditDistance) continue
            val freq = vocabulary[candidate] ?: 0L
            if (distance < bestDistance || (distance == bestDistance && freq > bestFreq)) {
                best = candidate
                bestDistance = distance
                bestFreq = freq
            }
        }
        return best ?: token
    }

    /**
     * Correct each word of a free-text query. Non-content words (short, numeric)
     * and words already in the vocabulary are left untouched; the surrounding
     * surface text is preserved as much as possible.
     */
    fun correctQuery(query: String): String {
        if (isEmpty) return query
        return query.split(WHITESPACE).joinToString(" ") { word ->
            val normalized = normalize(word)
            if (normalized.isBlank()) {
                word
            } else {
                val corrected = correctToken(normalized)
                if (corrected != normalized) corrected else word
            }
        }
    }

    private fun isPrefixOfVocabTerm(token: String): Boolean =
        vocabulary.keys.any { term -> term.length > token.length && term.startsWith(token) }

    private fun editsWithin(term: String): Set<String> {
        if (term.length <= 1) return setOf(term)
        var frontier = setOf(term)
        val all = HashSet<String>()
        all.add(term)
        repeat(maxEditDistance) {
            val next = HashSet<String>()
            for (word in frontier) {
                for (i in word.indices) {
                    val deleted = word.substring(0, i) + word.substring(i + 1)
                    if (all.add(deleted)) next.add(deleted)
                }
            }
            frontier = next
        }
        return all
    }

    private fun boundedLevenshtein(a: String, b: String, max: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > max) return max + 1
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }

    private companion object {
        private val WHITESPACE = Regex("\\s+")
        private val DIACRITICS = Regex("\\p{Mn}+")
        private val NON_ALNUM = Regex("[^a-z0-9]")

        private fun normalize(value: String): String =
            NON_ALNUM.replace(
                DIACRITICS.replace(Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD), ""),
                ""
            )
    }
}

data class ResolvedQuery(
    val query: String,
    val correctedQuery: String,
    val wasTypoCorrected: Boolean,
    val wasFollowUp: Boolean
)

/**
 * Deterministic query resolver: typo correction (SymSpell) + follow-up handling.
 *
 * For an elliptical/pronoun follow-up that carries no new subject of its own
 * ("și dacă plouă?"), it prepends the previous standalone query so retrieval keeps
 * the thread. A message that brings its own strong subject ("ce fac dacă văd un urs")
 * is treated as a fresh question. This cannot resolve same-parameter-different-value
 * follow-ups (iarnă -> vară); those need a model.
 */
class DeterministicQueryResolver(
    private val store: KnowledgeChunkStore
) {
    private val mutex = Mutex()
    private var corrector: SpellCorrector? = null

    suspend fun resolve(
        rawQuery: String,
        conversationState: AssistantConversationState,
        preprocessing: DeterministicPreprocessingResult
    ): ResolvedQuery {
        val corrector = ensureCorrector()
        val corrected = corrector.correctQuery(rawQuery)
        val wasTypoCorrected = corrected != rawQuery

        val previous = conversationState.lastStandaloneQuery?.trim().orEmpty()
        val looksLikeFollowUp = preprocessing.isFragmented ||
            preprocessing.isFollowUpShortAnswer ||
            preprocessing.hasPronounReference
        val isFollowUp = looksLikeFollowUp &&
            previous.isNotBlank() &&
            !introducesNewSubject(rawQuery)

        val resolved = if (isFollowUp) "$previous $corrected".trim() else corrected.trim()
        runCatching {
            android.util.Log.d(
                "RETRIEVAL_AUDIT",
                "[resolve] raw=\"$rawQuery\" -> \"$resolved\" typo=${corrected != rawQuery} followUp=$isFollowUp"
            )
        }
        return ResolvedQuery(
            query = resolved,
            correctedQuery = corrected,
            wasTypoCorrected = wasTypoCorrected,
            wasFollowUp = isFollowUp
        )
    }

    private suspend fun ensureCorrector(): SpellCorrector {
        corrector?.let { return it }
        return mutex.withLock {
            corrector ?: SpellCorrector(store.loadSearchVocabulary()).also { corrector = it }
        }
    }

    private fun introducesNewSubject(rawQuery: String): Boolean {
        val normalized = " ${normalizeInterpreterText(rawQuery).trim()} "
        return StrongSubjectTokens.any { token -> " $token " in normalized || normalized.contains(" $token") }
    }

    private companion object {
        // Subject nouns that make a message self-sufficient (a new question), as
        // opposed to weather/condition words (ploaie, vant, frig, iarna) which only
        // modify the existing thread and should be concatenated.
        private val StrongSubjectTokens = setOf(
            "urs", "ursi", "lup", "lupi", "sarpe", "vipera", "caine", "caini", "animal",
            "foc", "focul", "apa", "adapost", "cort", "bocanci", "rucsac", "harta", "busola",
            "entorsa", "glezna", "rana", "sangerare", "fractura", "muscatura", "mancare",
            "traseu", "marcaj", "cabana", "avalansa", "fulger", "trasnet"
        )
    }
}
