package com.scouty.app.assistant.domain

import com.scouty.app.assistant.data.CampfireEmbeddingStore
import com.scouty.app.assistant.data.KnowledgeChunkStore
import com.scouty.app.assistant.model.AssistantConversationState
import com.scouty.app.assistant.model.AssistantOpenQuestion
import com.scouty.app.assistant.model.CardFamily
import com.scouty.app.assistant.model.DeviceContextSnapshot
import com.scouty.app.assistant.model.GenerationMode
import com.scouty.app.assistant.model.KnowledgeChunkRecord
import com.scouty.app.assistant.model.KnowledgePackStatus
import com.scouty.app.assistant.model.QueryAnalysis
import com.scouty.app.assistant.model.ReasoningType
import com.scouty.app.assistant.model.ResponseSectionStyle
import com.scouty.app.assistant.model.StructuredAssistantOutput
import com.scouty.app.assistant.model.StructuredResponseSection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import kotlin.math.sqrt

data class CampfireConversationResult(
    val structuredOutput: StructuredAssistantOutput,
    val retrievedChunks: List<RetrievedChunk>,
    val conversationState: AssistantConversationState,
    val retrievalConfidence: RetrievalConfidenceAssessment
)

class CampfireConversationEngine(
    private val knowledgeStore: KnowledgeChunkStore,
    private val confidencePolicy: RetrievalConfidencePolicy = RetrievalConfidencePolicy(),
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false }
) {

    suspend fun answer(
        query: String,
        context: DeviceContextSnapshot,
        queryAnalysis: QueryAnalysis,
        knowledgePackStatus: KnowledgePackStatus,
        conversationState: AssistantConversationState,
        retrievalQuery: String = query,
        validatedSlotUpdates: Map<String, String> = emptyMap(),
        preprocessing: DeterministicPreprocessingResult = DeterministicPreprocessingResult(
            normalizedQuery = normalizeInterpreterText(query)
        )
    ): CampfireConversationResult {
        val cards = loadCards(queryAnalysis.preferredLanguage)
        val embeddingStore = knowledgeStore.loadCampfireEmbeddingStore(
            preferredLanguage = queryAnalysis.preferredLanguage,
            topic = Topic
        )
        val facts = mergeValidatedSlotUpdates(
            extractFacts(query, conversationState.facts),
            validatedSlotUpdates
        )
        val normalizedQuery = normalize(retrievalQuery)
        val cardById = cards.associateBy { it.chunk.chunkId }
        val semanticQueryContext = buildSemanticQueryContext(
            normalizedQuery = normalizedQuery,
            embeddingStore = embeddingStore
        )
        val scoredCards = scoreAllCards(
            cards = cards,
            normalizedQuery = normalizedQuery,
            facts = facts,
            state = conversationState,
            queryAnalysis = queryAnalysis,
            cardById = cardById,
            embeddingStore = embeddingStore,
            semanticQueryContext = semanticQueryContext
        )
        val scoredById = scoredCards.associateBy { it.card.chunk.chunkId }
        val targetFamily = resolveTargetFamily(queryAnalysis, normalizedQuery, scoredCards)
        val (primary, support) = selectPrimaryAndSupport(
            scoredCards = scoredCards,
            scoredById = scoredById,
            targetFamily = targetFamily,
            state = conversationState,
            facts = facts
        )

        val selectedPrimary = primary ?: fallbackScenario(cards)
        val selectedSupport = if (support?.chunk?.chunkId == selectedPrimary?.chunk?.chunkId) null else support
        val selectedPrimaryId = selectedPrimary?.chunk?.chunkId
        val alternativeTop = scoredCards.firstOrNull { it.card.chunk.chunkId != selectedPrimaryId }
        val retrievalConfidence = confidencePolicy.assessCampfire(
            signals = CampfireRetrievalSignals(
                primaryCardId = selectedPrimaryId,
                top1Score = scoredById[selectedPrimaryId]?.finalScore ?: 0.0,
                top2Score = alternativeTop?.finalScore ?: 0.0,
                slotCompatibility = scoredById[selectedPrimaryId]?.slotCompatibility ?: 0.0,
                conversationCarryOver = scoredById[selectedPrimaryId]?.conversationCarryOver ?: 0.0,
                semanticSimilarity = scoredById[selectedPrimaryId]?.semanticSimilarity ?: 0.0,
                lexicalHints = scoredById[selectedPrimaryId]?.lexicalHints ?: 0.0,
                extractedFactCount = facts.size
            ),
            conversationState = conversationState,
            preprocessing = preprocessing
        )
        val output = buildStructuredOutput(
            primary = selectedPrimary,
            support = selectedSupport,
            facts = facts,
            context = context,
            reasoningType = queryAnalysis.reasoningType,
            knowledgePackVersion = knowledgePackStatus.packVersion,
            previousState = conversationState
        )
        val followUps = output.followUpQuestions
        val openQuestion = followUps.firstOrNull()
            ?.let(::buildOpenQuestion)
            ?.takeIf { !isResolvedOpenQuestion(it, facts) }
        val resolvedSlot = validatedSlotUpdates.keys.firstOrNull()
            ?: facts.keys.firstOrNull { conversationState.facts[it] != facts[it] }
        val updatedState = AssistantConversationState(
            activeTopic = Topic,
            lastCardId = selectedPrimary?.chunk?.chunkId,
            pendingScenarioCardId = when {
                selectedPrimary?.family == CardFamily.SCENARIO -> selectedPrimary.chunk.chunkId
                selectedSupport?.family == CardFamily.SCENARIO -> selectedSupport.chunk.chunkId
                else -> conversationState.pendingScenarioCardId
            },
            facts = facts,
            askedFollowUps = followUps,
            resolvedTerms = (
                conversationState.resolvedTerms +
                    listOfNotNull(selectedPrimary?.payload?.term, selectedSupport?.payload?.term)
                ).distinct(),
            openQuestion = openQuestion,
            lastResolvedSlot = resolvedSlot,
            lastUserMessage = query,
            lastStandaloneQuery = retrievalQuery,
            lastRetrievedChunkId = selectedPrimary?.chunk?.chunkId,
            lastRetrievedTopic = selectedPrimary?.chunk?.topic,
            lastRetrievedTitle = selectedPrimary?.chunk?.title,
            lastInterpretationConfidence = retrievalConfidence.score
        )

        return CampfireConversationResult(
            structuredOutput = output,
            retrievedChunks = buildRetrievedChunks(selectedPrimary, selectedSupport),
            conversationState = updatedState,
            retrievalConfidence = retrievalConfidence
        )
    }

    private suspend fun loadCards(language: String): List<CampfireCard> =
        knowledgeStore.searchStructuredCards(
            query = "",
            preferredLanguage = language,
            domain = Domain,
            topic = Topic,
            family = null,
            limit = 64
        ).mapNotNull { chunk ->
            val payload = runCatching {
                json.decodeFromString(CampfireCardPayload.serializer(), chunk.metadataJson.orEmpty())
            }.getOrNull() ?: return@mapNotNull null
            CampfireCard(
                chunk = chunk,
                family = chunk.cardFamily ?: parseFamily(payload.family) ?: return@mapNotNull null,
                payload = payload
            )
        }

    private fun scoreAllCards(
        cards: List<CampfireCard>,
        normalizedQuery: String,
        facts: Map<String, String>,
        state: AssistantConversationState,
        queryAnalysis: QueryAnalysis,
        cardById: Map<String, CampfireCard>,
        embeddingStore: CampfireEmbeddingStore,
        semanticQueryContext: SemanticQueryContext
    ): List<ScoredCampfireCard> {
        val specificSignal = hasSpecificSignal(normalizedQuery, facts, state, queryAnalysis)
        val pendingScenario = state.pendingScenarioCardId?.let(cardById::get)
        val isFreshConversation = state.activeTopic == null &&
            state.facts.isEmpty() &&
            state.pendingScenarioCardId == null

        return cards.map { card ->
            val slotCompatibility = slotCompatibility(card, facts)
            val conversationCarryOver = conversationCarryOver(
                card = card,
                pendingScenario = pendingScenario,
                lastCardId = state.lastCardId,
                isFollowUp = queryAnalysis.isFollowUp,
                specificSignal = specificSignal
            )
            val lexicalHints = phraseMatchScore(card, normalizedQuery)
            val semanticSimilarity = semanticSimilarity(
                card = card,
                embeddingStore = embeddingStore,
                semanticQueryContext = semanticQueryContext
            )
            val priorityWeight = priorityWeight(card)
            val antiNegativeDefault = antiNegativeDefault(
                card = card,
                slotCompatibility = slotCompatibility,
                lexicalHints = lexicalHints,
                semanticSimilarity = semanticSimilarity,
                isFreshConversation = isFreshConversation
            )
            val resolvedTermPenalty = resolvedTermPenalty(card, normalizedQuery, state.resolvedTerms)
            val finalScore =
                (slotCompatibility * 35.0) +
                    (conversationCarryOver * 25.0) +
                    (semanticSimilarity * 20.0) +
                    (lexicalHints * 15.0) +
                    (priorityWeight * 5.0) +
                    antiNegativeDefault +
                    resolvedTermPenalty

            ScoredCampfireCard(
                card = card,
                slotCompatibility = slotCompatibility,
                conversationCarryOver = conversationCarryOver,
                semanticSimilarity = semanticSimilarity,
                lexicalHints = lexicalHints,
                priorityWeight = priorityWeight,
                antiNegativeDefault = antiNegativeDefault,
                resolvedTermPenalty = resolvedTermPenalty,
                finalScore = finalScore
            )
        }.sortedByDescending { it.finalScore }
    }

    private fun buildSemanticQueryContext(
        normalizedQuery: String,
        embeddingStore: CampfireEmbeddingStore
    ): SemanticQueryContext {
        if (embeddingStore.isEmpty || normalizedQuery.isBlank()) {
            return SemanticQueryContext.Empty
        }

        val candidates = embeddingStore.phrasingEmbeddings
            .asSequence()
            .map { phrasing ->
                val lookupScore = semanticLookupScore(
                    normalizedQuery = normalizedQuery,
                    normalizedPhrase = phrasing.normalizedPhrase
                )
                SemanticPhraseCandidate(
                    cardId = phrasing.cardId,
                    normalizedPhrase = phrasing.normalizedPhrase,
                    phraseKind = phrasing.phraseKind,
                    embedding = phrasing.embedding,
                    lookupScore = lookupScore
                )
            }
            .filter { it.lookupScore >= SemanticLookupThreshold }
            .sortedByDescending { it.lookupScore }
            .take(MaxSemanticPhraseCandidates)
            .toList()

        return if (candidates.isEmpty()) SemanticQueryContext.Empty else SemanticQueryContext(candidates)
    }

    private fun semanticSimilarity(
        card: CampfireCard,
        embeddingStore: CampfireEmbeddingStore,
        semanticQueryContext: SemanticQueryContext
    ): Double {
        if (embeddingStore.isEmpty || semanticQueryContext.candidates.isEmpty()) {
            return 0.0
        }
        val embeddings = embeddingStore.cardEmbeddings[card.chunk.chunkId] ?: return 0.0
        if (embeddings.queryEmbedding.isEmpty() && embeddings.contentEmbedding.isEmpty()) {
            return 0.0
        }

        return semanticQueryContext.candidates.maxOfOrNull { candidate ->
            val queryCosine = cosineSimilarity(candidate.embedding, embeddings.queryEmbedding)
            val contentCosine = cosineSimilarity(candidate.embedding, embeddings.contentEmbedding)
            val bestCosine = maxOf(queryCosine, contentCosine)
            normalizedCosine(bestCosine) * (0.55 + 0.45 * candidate.lookupScore)
        } ?: 0.0
    }

    private fun semanticLookupScore(
        normalizedQuery: String,
        normalizedPhrase: String
    ): Double {
        if (normalizedPhrase.isBlank()) {
            return 0.0
        }
        if (normalizedPhrase in normalizedQuery || normalizedQuery in normalizedPhrase) {
            return 1.0
        }
        val phraseCoverage = phraseCoverageScore(
            phrase = normalizedPhrase,
            normalizedQuery = normalizedQuery,
            maxTokenDistance = 2
        )
        val trigramSimilarity = charNGramSimilarity(
            left = normalizedPhrase,
            right = normalizedQuery,
            n = 3
        )
        return maxOf(phraseCoverage, trigramSimilarity)
    }

    private fun cosineSimilarity(left: FloatArray, right: FloatArray): Double {
        if (left.isEmpty() || right.isEmpty() || left.size != right.size) {
            return 0.0
        }

        var dot = 0.0
        var leftNorm = 0.0
        var rightNorm = 0.0
        left.indices.forEach { index ->
            val leftValue = left[index].toDouble()
            val rightValue = right[index].toDouble()
            dot += leftValue * rightValue
            leftNorm += leftValue * leftValue
            rightNorm += rightValue * rightValue
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0
        }
        return dot / (sqrt(leftNorm) * sqrt(rightNorm))
    }

    private fun normalizedCosine(value: Double): Double =
        value.coerceIn(0.0, 1.0)

    private fun charNGramSimilarity(left: String, right: String, n: Int): Double {
        val leftNgrams = charNGrams(left, n)
        val rightNgrams = charNGrams(right, n)
        if (leftNgrams.isEmpty() || rightNgrams.isEmpty()) {
            return 0.0
        }
        val intersection = leftNgrams.intersect(rightNgrams).size.toDouble()
        val union = leftNgrams.union(rightNgrams).size.toDouble()
        return if (union == 0.0) 0.0 else intersection / union
    }

    private fun charNGrams(value: String, n: Int): Set<String> {
        val compact = value.replace(" ", "")
        if (compact.length < n) {
            return emptySet()
        }
        return (0..(compact.length - n))
            .map { compact.substring(it, it + n) }
            .toSet()
    }

    private fun resolveTargetFamily(
        queryAnalysis: QueryAnalysis,
        normalizedQuery: String,
        scoredCards: List<ScoredCampfireCard>
    ): CardFamily {
        val targetFamily = queryAnalysis.targetFamily ?: CardFamily.SCENARIO
        if (targetFamily == CardFamily.DEFINITION) {
            return targetFamily
        }

        val bestDefinition = scoredCards
            .asSequence()
            .filter { it.card.family == CardFamily.DEFINITION }
            .maxByOrNull { it.finalScore }

        return when {
            looksLikeDefinitionQuery(normalizedQuery) && hasStrongDefinitionEvidence(bestDefinition) ->
                CardFamily.DEFINITION

            else -> targetFamily
        }
    }

    private fun selectPrimaryAndSupport(
        scoredCards: List<ScoredCampfireCard>,
        scoredById: Map<String, ScoredCampfireCard>,
        targetFamily: CardFamily,
        state: AssistantConversationState,
        facts: Map<String, String>
    ): Pair<CampfireCard?, CampfireCard?> {
        val bestScenario = bestScenarioCandidate(scoredCards)
        val bestDefinition = bestDefinitionCandidate(scoredCards)
        val bestConstraintSupport = bestConstraintSupportCandidate(scoredCards)
        val bestConstraintOverride = bestConstraintOverrideCandidate(scoredCards, bestScenario, targetFamily)
        val pendingScenario = state.pendingScenarioCardId?.let(scoredById::get)

        return when (targetFamily) {
            CardFamily.DEFINITION -> {
                val primary = bestDefinition?.card ?: bestScenario?.card
                val support = when (primary?.family) {
                    CardFamily.DEFINITION -> selectRelatedSupport(bestDefinition, scoredById)
                    else -> selectFallbackSupport(bestScenario, scoredCards)
                }
                primary to support?.card
            }

            CardFamily.CONSTRAINT -> {
                val primary = bestConstraintOverride?.card ?: bestScenario?.card
                val support = when (primary?.family) {
                    CardFamily.CONSTRAINT -> bestScenario ?: pendingScenario
                    CardFamily.SCENARIO -> bestConstraintSupport
                    else -> null
                }
                primary to support?.card
            }

            CardFamily.SCENARIO -> {
                val primary = bestConstraintOverride?.card ?: bestScenario?.card
                val support = when (primary?.family) {
                    CardFamily.CONSTRAINT -> bestScenario
                    CardFamily.SCENARIO -> selectScenarioSupport(bestScenario, bestConstraintSupport, scoredById, scoredCards, facts)
                    else -> null
                }
                primary to support?.card
            }
        }
    }

    private fun fallbackScenario(cards: List<CampfireCard>): CampfireCard? =
        cards.firstOrNull { it.chunk.chunkId == GeneralScenarioId }
            ?: cards.firstOrNull { it.family == CardFamily.SCENARIO }

    private fun bestScenarioCandidate(scoredCards: List<ScoredCampfireCard>): ScoredCampfireCard? {
        val scenarios = scoredCards.filter { it.card.family == CardFamily.SCENARIO }
        val best = scenarios.maxByOrNull { it.finalScore }
        val fallback = scenarios.firstOrNull { it.card.chunk.chunkId == GeneralScenarioId }

        return when {
            best == null -> fallback
            fallback == null -> best
            best.card.chunk.chunkId == fallback.card.chunk.chunkId -> best
            hasStrongScenarioEvidence(best) -> best
            else -> fallback
        }
    }

    private fun bestDefinitionCandidate(scoredCards: List<ScoredCampfireCard>): ScoredCampfireCard? =
        scoredCards
            .asSequence()
            .filter { it.card.family == CardFamily.DEFINITION }
            .filter(::hasStrongDefinitionEvidence)
            .maxByOrNull { it.finalScore }

    private fun bestConstraintSupportCandidate(scoredCards: List<ScoredCampfireCard>): ScoredCampfireCard? =
        scoredCards
            .asSequence()
            .filter { it.card.family == CardFamily.CONSTRAINT }
            .filter(::canConstraintBeSupport)
            .maxByOrNull { it.finalScore }

    private fun bestConstraintOverrideCandidate(
        scoredCards: List<ScoredCampfireCard>,
        bestScenario: ScoredCampfireCard?,
        targetFamily: CardFamily
    ): ScoredCampfireCard? =
        scoredCards
            .asSequence()
            .filter { it.card.family == CardFamily.CONSTRAINT }
            .filter { canConstraintBePrimary(it, bestScenario, targetFamily) }
            .maxByOrNull { it.finalScore }

    private fun selectRelatedSupport(
        primary: ScoredCampfireCard?,
        scoredById: Map<String, ScoredCampfireCard>
    ): ScoredCampfireCard? {
        primary ?: return null
        return primary.card.payload.relatedCards
            .asSequence()
            .mapNotNull(scoredById::get)
            .filter { candidate -> isEligibleSupportCandidate(candidate, primary) }
            .maxByOrNull { it.finalScore }
    }

    private fun selectScenarioSupport(
        primary: ScoredCampfireCard?,
        bestConstraintSupport: ScoredCampfireCard?,
        scoredById: Map<String, ScoredCampfireCard>,
        scoredCards: List<ScoredCampfireCard>,
        facts: Map<String, String>
    ): ScoredCampfireCard? {
        primary ?: return null
        val related = selectRelatedSupport(primary, scoredById)
        if (related != null) {
            return related
        }
        if (bestConstraintSupport != null && isEligibleSupportCandidate(bestConstraintSupport, primary)) {
            return bestConstraintSupport
        }
        val memorySupport = selectMemorySupport(primary, scoredCards, facts)
        if (memorySupport != null) {
            return memorySupport
        }
        return selectFallbackSupport(primary, scoredCards)
    }

    private fun selectMemorySupport(
        primary: ScoredCampfireCard?,
        scoredCards: List<ScoredCampfireCard>,
        facts: Map<String, String>
    ): ScoredCampfireCard? {
        primary ?: return null
        if (facts.isEmpty()) {
            return null
        }
        val primarySlotKeys = primary.card.payload.slotConstraints.keys
        return scoredCards
            .asSequence()
            .filter { candidate ->
                candidate.card.chunk.chunkId != primary.card.chunk.chunkId &&
                    candidate.card.family == CardFamily.SCENARIO &&
                    candidate.card.payload.slotConstraints.isNotEmpty() &&
                    candidate.slotCompatibility >= 1.0 &&
                    candidate.finalScore >= 20.0 &&
                    candidate.card.payload.slotConstraints.keys.any { key -> key in facts && key !in primarySlotKeys }
            }
            .maxByOrNull { it.finalScore }
    }

    private fun selectFallbackSupport(
        primary: ScoredCampfireCard?,
        scoredCards: List<ScoredCampfireCard>
    ): ScoredCampfireCard? {
        primary ?: return null
        return scoredCards
            .asSequence()
            .filter { candidate ->
                candidate.card.chunk.chunkId != primary.card.chunk.chunkId &&
                    candidate.card.family != primary.card.family &&
                    isEligibleSupportCandidate(candidate, primary)
            }
            .maxByOrNull { it.finalScore }
    }

    private fun isEligibleSupportCandidate(
        candidate: ScoredCampfireCard,
        primary: ScoredCampfireCard
    ): Boolean {
        val isRelated = candidate.card.chunk.chunkId in primary.card.payload.relatedCards
        if (candidate.card.chunk.chunkId == primary.card.chunk.chunkId) {
            return false
        }
        if (candidate.card.family == CardFamily.CONSTRAINT && !canConstraintBeSupport(candidate)) {
            return false
        }
        val hasSupportEvidence = isRelated ||
            candidate.lexicalHints >= 0.35 ||
            candidate.semanticSimilarity >= 0.6 ||
            candidate.slotCompatibility > 0.5 ||
            candidate.conversationCarryOver >= 0.3
        return hasSupportEvidence && candidate.finalScore >= 10.0
    }

    private fun hasStrongScenarioEvidence(candidate: ScoredCampfireCard?): Boolean {
        candidate ?: return false
        return candidate.lexicalHints >= 0.35 ||
            candidate.semanticSimilarity >= 0.6 ||
            candidate.slotCompatibility > 0.5 ||
            candidate.conversationCarryOver >= 0.3
    }

    private fun hasStrongDefinitionEvidence(candidate: ScoredCampfireCard?): Boolean {
        candidate ?: return false
        return candidate.lexicalHints >= 0.55 ||
            candidate.semanticSimilarity >= 0.65 ||
            candidate.finalScore >= 32.0
    }

    private fun canConstraintBeSupport(candidate: ScoredCampfireCard): Boolean =
        candidate.constraintActivationConfidence >= 0.3

    private fun canConstraintBePrimary(
        candidate: ScoredCampfireCard,
        bestScenario: ScoredCampfireCard?,
        targetFamily: CardFamily
    ): Boolean {
        if (targetFamily == CardFamily.DEFINITION) {
            return false
        }
        if (candidate.card.payload.constraintMode != OverrideConstraintMode) {
            return false
        }
        if (candidate.slotCompatibility < 0.8 || candidate.constraintActivationConfidence < 0.6) {
            return false
        }
        val scenarioScore = bestScenario?.finalScore ?: Double.NEGATIVE_INFINITY
        return candidate.finalScore >= scenarioScore - 15.0 ||
            (candidate.slotCompatibility == 1.0 && candidate.lexicalHints >= 0.5)
    }

    private fun slotCompatibility(card: CampfireCard, facts: Map<String, String>): Double {
        val constraints = card.payload.slotConstraints
        if (constraints.isEmpty()) {
            return 0.5
        }

        var matchedSlots = 0
        constraints.forEach { (key, value) ->
            val fact = facts[key] ?: return@forEach
            if (!fact.equals(value, ignoreCase = true)) {
                return 0.0
            }
            matchedSlots += 1
        }
        return matchedSlots.toDouble() / constraints.size.toDouble()
    }

    private fun conversationCarryOver(
        card: CampfireCard,
        pendingScenario: CampfireCard?,
        lastCardId: String?,
        isFollowUp: Boolean,
        specificSignal: Boolean
    ): Double {
        val cardId = card.chunk.chunkId
        if (isFollowUp && pendingScenario != null) {
            if (cardId == pendingScenario.chunk.chunkId) {
                return if (specificSignal) 0.3 else 0.9
            }
            if (cardId in pendingScenario.payload.relatedCards) {
                return 0.4
            }
        }
        return if (cardId == lastCardId) 0.1 else 0.0
    }

    private fun phraseMatchScore(card: CampfireCard, normalizedQuery: String): Double {
        if (normalizedQuery.isBlank()) {
            return 0.0
        }

        var maxScore = 0.0
        if (normalizedQuery == normalize(card.chunk.title)) {
            return 1.0
        }

        card.payload.term?.let { term ->
            maxScore = maxOf(maxScore, termMatchScore(normalize(term), normalizedQuery))
        }

        card.payload.userPhrasings.forEach { phrasing ->
            val normalizedPhrasing = normalize(phrasing)
            if (normalizedPhrasing in normalizedQuery || normalizedQuery in normalizedPhrasing) {
                maxScore = maxOf(maxScore, 0.9)
            } else {
                val ratio = phraseCoverageScore(
                    phrase = normalizedPhrasing,
                    normalizedQuery = normalizedQuery,
                    maxTokenDistance = 1
                )
                if (ratio >= 0.6) {
                    maxScore = maxOf(maxScore, 0.7 * ratio)
                }
            }
        }

        val cardTokens = tokenize(card.chunk.keywords)
        val queryTokens = tokenize(normalizedQuery)
        if (cardTokens.isNotEmpty() && queryTokens.isNotEmpty()) {
            val fuzzyOverlap = cardTokens.count { cardToken ->
                queryTokens.any { queryToken -> tokensMatch(cardToken, queryToken, maxDistance = 1) }
            }
            if (fuzzyOverlap > 0) {
                maxScore = maxOf(maxScore, 0.5 * fuzzyOverlap.toDouble() / cardTokens.size.toDouble())
            }
        }

        return maxScore
    }

    private fun termMatchScore(normalizedTerm: String, normalizedQuery: String): Double {
        if (normalizedTerm.isBlank()) {
            return 0.0
        }
        if (normalizedTerm in normalizedQuery) {
            return 0.95
        }
        val ratio = phraseCoverageScore(
            phrase = normalizedTerm,
            normalizedQuery = normalizedQuery,
            maxTokenDistance = 2
        )
        return when {
            ratio >= 1.0 -> 0.8
            ratio >= 0.6 -> 0.65 * ratio
            else -> 0.0
        }
    }

    private fun phraseCoverageScore(
        phrase: String,
        normalizedQuery: String,
        maxTokenDistance: Int
    ): Double {
        val phraseTokens = tokenize(phrase).toList()
        val queryTokens = tokenize(normalizedQuery).toList()
        if (phraseTokens.isEmpty() || queryTokens.isEmpty()) {
            return 0.0
        }

        var bestRatio = 0.0
        if (queryTokens.size >= phraseTokens.size) {
            for (start in 0..(queryTokens.size - phraseTokens.size)) {
                var matched = 0
                phraseTokens.indices.forEach { index ->
                    if (tokensMatch(phraseTokens[index], queryTokens[start + index], maxTokenDistance)) {
                        matched += 1
                    }
                }
                bestRatio = maxOf(bestRatio, matched.toDouble() / phraseTokens.size.toDouble())
                if (bestRatio == 1.0) {
                    return bestRatio
                }
            }
        }

        val fuzzyMatches = phraseTokens.count { phraseToken ->
            queryTokens.any { queryToken -> tokensMatch(phraseToken, queryToken, maxTokenDistance) }
        }
        return maxOf(bestRatio, fuzzyMatches.toDouble() / phraseTokens.size.toDouble())
    }

    private fun tokensMatch(left: String, right: String, maxDistance: Int): Boolean {
        if (left == right) {
            return true
        }
        val allowedDistance = when {
            minOf(left.length, right.length) <= 3 -> 0
            minOf(left.length, right.length) <= 6 -> minOf(maxDistance, 1)
            else -> maxDistance
        }
        return levenshtein(left, right) <= allowedDistance
    }

    private fun priorityWeight(card: CampfireCard): Double =
        card.chunk.priority.coerceIn(0, 130) / 130.0

    private fun antiNegativeDefault(
        card: CampfireCard,
        slotCompatibility: Double,
        lexicalHints: Double,
        semanticSimilarity: Double,
        isFreshConversation: Boolean
    ): Double {
        var penalty = 0.0
        if (card.family == CardFamily.CONSTRAINT) {
            penalty += when {
                slotCompatibility == 0.5 && lexicalHints < 0.3 -> -20.0
                lexicalHints < 0.4 && slotCompatibility < 0.8 && semanticSimilarity < 0.5 -> -25.0
                else -> 0.0
            }
        }
        if (card.chunk.chunkId in AbortCardIds && isFreshConversation) {
            penalty -= 15.0
        }
        return penalty
    }

    private fun resolvedTermPenalty(
        card: CampfireCard,
        normalizedQuery: String,
        resolvedTerms: List<String>
    ): Double {
        if (card.family != CardFamily.DEFINITION) {
            return 0.0
        }
        val term = card.payload.term?.let(::normalize) ?: return 0.0
        if (resolvedTerms.none { normalize(it) == term }) {
            return 0.0
        }
        return if (term in normalizedQuery) 0.0 else -5.0
    }

    private fun hasSpecificSignal(
        normalizedQuery: String,
        facts: Map<String, String>,
        state: AssistantConversationState,
        queryAnalysis: QueryAnalysis
    ): Boolean =
        queryAnalysis.targetFamily == CardFamily.DEFINITION ||
            queryAnalysis.targetFamily == CardFamily.CONSTRAINT ||
            looksLikeDefinitionQuery(normalizedQuery) ||
            hasSpecificScenarioSignal(normalizedQuery) ||
            facts != state.facts

    private fun hasSpecificScenarioSignal(normalizedQuery: String): Boolean =
        normalizedQuery.any { it.isLetterOrDigit() } &&
            (
                normalizedQuery.contains("pentru ") ||
                    normalizedQuery.contains("am ") ||
                    normalizedQuery.contains("nu am") ||
                    normalizedQuery.contains("improviz") ||
                    normalizedQuery.contains("ud") ||
                    normalizedQuery.contains("vant") ||
                    normalizedQuery.contains("frig") ||
                    normalizedQuery.contains("gatit") ||
                    normalizedQuery.contains("fierb")
                )

    private fun looksLikeDefinitionQuery(normalizedQuery: String): Boolean =
        normalizedQuery.startsWith("ce e ") ||
            normalizedQuery.startsWith("ce inseamna ") ||
            normalizedQuery.startsWith("ce este ") ||
            normalizedQuery.startsWith("adica ")

    private fun levenshtein(left: String, right: String): Int {
        if (left == right) {
            return 0
        }
        if (left.isEmpty()) {
            return right.length
        }
        if (right.isEmpty()) {
            return left.length
        }

        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)

        left.forEachIndexed { leftIndex, leftChar ->
            current[0] = leftIndex + 1
            right.forEachIndexed { rightIndex, rightChar ->
                val substitution = if (leftChar == rightChar) 0 else 1
                current[rightIndex + 1] = minOf(
                    previous[rightIndex + 1] + 1,
                    current[rightIndex] + 1,
                    previous[rightIndex] + substitution
                )
            }
            val swap = previous
            previous = current
            current = swap
        }

        return previous[right.length]
    }

    private fun extractFacts(
        query: String,
        previousFacts: Map<String, String>
    ): Map<String, String> {
        val facts = previousFacts.toMutableMap()
        val normalized = normalize(query)
        val previousIgnitionSource = previousFacts["ignition_source"]

        when {
            containsAny(normalized, "caldura", "incalz", "incalzire", "frig") -> facts["goal"] = "warmth"
            containsAny(normalized, "gatit", "gatesc", "mancare", "mancarea") -> facts["goal"] = "cooking"
            containsAny(normalized, "fierb apa", "fiert apa", "boil water", "apa pentru baut") -> facts["goal"] = "boil_water"
        }

        when {
            containsAny(normalized, "amnar", "ferro") -> facts["ignition_source"] = "ferro"
            containsAny(normalized, "bricheta", "brichete") -> facts["ignition_source"] = "lighter"
            containsAny(normalized, "chibrit", "chibrite") -> facts["ignition_source"] = "matches"
            containsAny(normalized, "pot face scanteie", "pot produce scanteie", "am scanteie", "spark") ->
                facts["ignition_source"] = "recognized_spark"
            containsAny(normalized, "n am nimic de aprindere", "nu am nimic de aprindere", "fara aprindere", "n am nimic", "nu am nimic") &&
                !containsAny(normalized, "amnar", "ferro", "bricheta", "chibrit") ->
                facts["ignition_source"] = "none"
        }

        val compromisedReason = compromiseReason(normalized)
        val compromisedIgnitionItem = compromisedIgnitionItem(
            normalized = normalized,
            previousIgnitionSource = previousIgnitionSource,
            compromisedReason = compromisedReason
        )
        if (compromisedIgnitionItem != null) {
            facts["ignition_source"] = "none"
            facts["compromised_item"] = compromisedIgnitionItem
            facts["compromised_reason"] = compromisedReason ?: "unusable"
        } else if (facts["ignition_source"] != null && facts["ignition_source"] != "none") {
            facts.remove("compromised_item")
            facts.remove("compromised_reason")
        }

        when {
            containsAny(normalized, "totul e ud", "tot e ud", "plouat", "ploua", "ud leoarca", "lemn ud") ->
                facts["fuel_condition"] = "wet"
            containsAny(normalized, "umed", "umezeala", "cam ud") ->
                facts["fuel_condition"] = "damp"
            containsAny(normalized, "nu gasesc material", "nu gasesc lemn", "nu am lemne", "material putin", "putin material", "nu gasesc surcele") ->
                facts["fuel_condition"] = "scarce"
        }

        when {
            containsAny(normalized, "vant puternic", "bate vantul tare", "bate vantul", "vijelie") ->
                facts["wind"] = "high"
            containsAny(normalized, "vant", "vanticel") ->
                facts["wind"] = "moderate"
        }

        when {
            containsAny(normalized, "nu am voie", "interzis", "interzisa", "forbidden") ->
                facts["permission"] = "forbidden"
            containsAny(normalized, "este permis", "e permis", "am voie aici", "pot face foc aici") ->
                facts["permission"] = "unknown"
        }

        when {
            containsAny(normalized, "radacini", "turba", "pamant turbos") ->
                facts["ground_risk"] = "roots_or_peat"
            containsAny(normalized, "iarba uscata", "vegetatie uscata", "litiera uscata") ->
                facts["ground_risk"] = "dry_vegetation"
            containsAny(normalized, "in cort", "langa cort", "sub prelata", "sub tarp") ->
                facts["ground_risk"] = "indoor_or_tent"
            containsAny(normalized, "vatra sigura", "pamant gol", "teren mineral", "groapa de foc") ->
                facts["ground_risk"] = "safe"
        }

        when {
            containsAny(normalized, "am iasca", "am servetele", "am hartie", "am vata", "am scame", "am puf") ->
                facts["tinder_available"] = "yes"
            containsAny(normalized, "n am iasca", "nu am iasca") ->
                facts["tinder_available"] = "no"
        }

        when {
            containsAny(normalized, "am hartie", "am hartii", "am niste hartii", "am foi", "am foile") -> {
                facts["tinder_available"] = "yes"
                facts["tinder_material"] = "paper"
            }
            containsAny(normalized, "am servetele", "am servetele uscate") -> {
                facts["tinder_available"] = "yes"
                facts["tinder_material"] = "tissue"
            }
            containsAny(normalized, "am vata", "am bumbac") -> {
                facts["tinder_available"] = "yes"
                facts["tinder_material"] = "cotton"
            }
            containsAny(normalized, "am scame", "am puf") -> {
                facts["tinder_available"] = "yes"
                facts["tinder_material"] = "lint"
            }
        }

        val explicitTinderReference = containsAny(
            normalized,
            "hartie",
            "hartii",
            "foi",
            "foile",
            "servetele",
            "vata",
            "bumbac",
            "scame",
            "puf",
            "materialul",
            "tinderul",
            "iasca"
        )

        when {
            (explicitTinderReference ||
                (facts["tinder_material"] != null && containsAny(normalized, "mi s au udat", "s a udat", "s au udat"))) &&
                containsAny(normalized, "mi s au udat", "s a udat", "s au udat", "e uda", "e ud", "este uda", "este ud") -> {
                facts["tinder_condition"] = "wet"
                facts["tinder_available"] = "no"
                facts["tinder_strategy"] = "improvise"
            }
            explicitTinderReference &&
                containsAny(normalized, "nu mai am", "am pierdut", "mi am pierdut", "s a terminat", "nu se mai poate folosi", "nu mai merge") -> {
                facts["tinder_condition"] = "unavailable"
                facts["tinder_available"] = "no"
                facts["tinder_strategy"] = "improvise"
            }
        }

        when {
            containsAny(
                normalized,
                "improvizam din ce am la mine",
                "improvizam din ce am",
                "improvizez iasca",
                "din materialele disponibile",
                "trebuie sa improvizam",
                "trebuie sa l improvizam",
                "nu am iasca",
                "n am iasca"
            ) -> facts["tinder_strategy"] = "improvise"
        }

        when {
            containsAny(normalized, "am surcele", "am crengute", "am betisoare") ->
                facts["kindling_available"] = "yes"
            containsAny(normalized, "nu am surcele", "nu gasesc crengute", "nu gasesc betisoare") ->
                facts["kindling_available"] = "no"
        }

        when {
            containsAny(normalized, "mi e frig", "mi e foarte frig", "trebuie neaparat", "am nevoie acum") ->
                facts["need_level"] = "necessary"
            containsAny(normalized, "ar fi util", "doar daca merge", "optional") ->
                facts["need_level"] = "optional"
        }

        when {
            containsAny(normalized, "se intuneca", "e noapte", "aproape noapte", "mai e putina lumina") ->
                facts["daylight"] = "low"
            containsAny(normalized, "e deja intuneric", "e deja noapte", "bezna") ->
                facts["daylight"] = "dark"
            containsAny(normalized, "mai e lumina", "inca e lumina") ->
                facts["daylight"] = "enough"
        }

        when {
            containsAny(normalized, "sunt obosit", "suntem obositi", "prea obosit", "epuizat") ->
                facts["fatigue"] = "high"
            containsAny(normalized, "putin obosit", "cam obosit") ->
                facts["fatigue"] = "moderate"
        }

        return facts
    }

    private fun buildStructuredOutput(
        primary: CampfireCard?,
        support: CampfireCard?,
        facts: Map<String, String>,
        context: DeviceContextSnapshot,
        reasoningType: ReasoningType,
        knowledgePackVersion: String?,
        previousState: AssistantConversationState
    ): StructuredAssistantOutput {
        val main = primary ?: return StructuredAssistantOutput(
            summary = "Nu am gasit inca un card bun pentru foc. Spune-mi ce resurse ai si in ce conditii esti.",
            sections = emptyList(),
            generationMode = GenerationMode.CARD_DIRECT,
            reasoningType = reasoningType,
            knowledgePackVersion = knowledgePackVersion,
            followUpQuestions = listOf(
                "Ai bricheta, chibrite sau amnar?",
                "Totul e uscat sau e ud in jur?"
            ),
            resolvedTopic = Topic,
            resolvedFamily = CardFamily.SCENARIO
        )
        val sections = mutableListOf<StructuredResponseSection>()

        when (main.family) {
            CardFamily.DEFINITION -> {
                val definitionBody = main.payload.plainLanguageDefinition
                    ?.takeIf { it.isNotBlank() }
                    ?: main.payload.lead
                sections += StructuredResponseSection(
                    title = "Pe scurt",
                    body = definitionBody,
                    style = ResponseSectionStyle.GUIDANCE
                )
                if (main.payload.actionsNow.isNotEmpty()) {
                    sections += StructuredResponseSection(
                        title = "Cum te ajuta acum",
                        body = renderItems(main.payload.actionsNow),
                        style = ResponseSectionStyle.ACTIONS
                    )
                }
            }

            else -> {
                if (main.payload.actionsNow.isNotEmpty()) {
                    val actionTitle = pickActionTitle(main)
                    sections += StructuredResponseSection(
                        title = actionTitle,
                        body = renderItems(main.payload.actionsNow),
                        style = ResponseSectionStyle.ACTIONS
                    )
                }
            }
        }

        if (main.payload.avoid.isNotEmpty()) {
            val avoidTitle = pickAvoidTitle(main)
            sections += StructuredResponseSection(
                title = avoidTitle,
                body = renderItems(main.payload.avoid),
                style = ResponseSectionStyle.GUIDANCE
            )
        }

        if (main.payload.watchFor.isNotEmpty()) {
            val watchTitle = pickWatchTitle(main)
            sections += StructuredResponseSection(
                title = watchTitle,
                body = renderItems(main.payload.watchFor),
                style = ResponseSectionStyle.GUIDANCE
            )
        }

        support?.let { extra ->
            val title = when (extra.family) {
                CardFamily.CONSTRAINT -> "Ai grija si la"
                CardFamily.DEFINITION -> "Clarificare utila"
                CardFamily.SCENARIO -> "Pasul urmator"
            }
            val body = when {
                extra.family == CardFamily.DEFINITION && !extra.payload.plainLanguageDefinition.isNullOrBlank() ->
                    extra.payload.plainLanguageDefinition
                extra.payload.actionsNow.isNotEmpty() ->
                    renderItems(extra.payload.actionsNow)
                extra.payload.lead.isNotBlank() ->
                    extra.payload.lead
                else -> extra.chunk.body
            }
            sections += StructuredResponseSection(
                title = title,
                body = body,
                style = if (extra.family == CardFamily.CONSTRAINT) ResponseSectionStyle.GUIDANCE else ResponseSectionStyle.ACTIONS
            )
        }

        val followUps = selectFollowUps(
            main = main,
            support = support,
            facts = facts,
            previousState = previousState
        )
        val ignitionCompromise = detectIgnitionCompromise(
            facts = facts,
            previousState = previousState
        )

        val summary = when {
            main.family == CardFamily.DEFINITION -> main.payload.lead
            context.trail != null && main.payload.lead.contains("nu merita", ignoreCase = true) ->
                "${main.payload.lead} Tine cont si de timpul ramas pe traseu."
            else -> contextualizeSummary(main.payload.lead, main, facts, ignitionCompromise)
        }

        return StructuredAssistantOutput(
            summary = summary,
            sections = sections.take(3),
            generationMode = GenerationMode.CARD_DIRECT,
            reasoningType = reasoningType,
            followUpQuestions = followUps,
            resolvedTopic = Topic,
            resolvedFamily = main.family,
            knowledgePackVersion = knowledgePackVersion
        )
    }

    private fun selectFollowUps(
        main: CampfireCard,
        support: CampfireCard?,
        facts: Map<String, String>,
        previousState: AssistantConversationState
    ): List<String> {
        remainingIgnitionOptionsQuestion(
            detectIgnitionCompromise(
                facts = facts,
                previousState = previousState
            )
        )?.let { return listOf(it) }

        val primaryQuestions = filterFollowUps(
            questions = main.payload.followUpQuestions.map { it.question },
            facts = facts,
            previousState = previousState
        )
        if (primaryQuestions.isNotEmpty()) {
            return primaryQuestions
        }
        val supportQuestions = support?.payload?.followUpQuestions?.map { it.question }.orEmpty()
        return filterFollowUps(
            questions = supportQuestions,
            facts = facts,
            previousState = previousState
        )
    }

    private fun filterFollowUps(
        questions: List<String>,
        facts: Map<String, String>,
        previousState: AssistantConversationState
    ): List<String> =
        questions
            .map(::rewriteFollowUpQuestion)
            .filter { it !in previousState.askedFollowUps }
            .filterNot { isResolvedFollowUp(it, facts) }
            .distinct()
            .take(1)

    private fun rewriteFollowUpQuestion(question: String): String {
        val normalizedQuestion = normalize(question)
        return when {
            containsAny(
                normalizedQuestion,
                "totul e uscat sau e ud",
                "totul e uscat sau e ud in jur",
                "materialul e uscat sau e ud",
                "materialul e uscat sau umed"
            ) -> "A plouat recent sau e destul de uscat în jur?"

            else -> question
        }
    }

    private fun isResolvedFollowUp(
        question: String,
        facts: Map<String, String>
    ): Boolean {
        if (facts.isEmpty()) {
            return false
        }
        val normalizedQuestion = normalize(question)
        return when {
            containsAny(normalizedQuestion, "bricheta", "chibrit", "amnar", "aprindere", "scanteie") ->
                facts["ignition_source"] != null && facts["ignition_source"] != "none"
            containsAny(normalizedQuestion, "caldura", "gatit", "fiert apa") ->
                "goal" in facts
            containsAny(normalizedQuestion, "iasca", "material fin", "ceva uscat") ->
                "tinder_available" in facts || "tinder_material" in facts || "tinder_strategy" in facts
            containsAny(normalizedQuestion, "ud", "uscat", "umezeala") ->
                "fuel_condition" in facts || "tinder_condition" in facts
            containsAny(normalizedQuestion, "surcele", "crengute", "betisoare") ->
                "kindling_available" in facts
            containsAny(normalizedQuestion, "permis", "voie") ->
                "permission" in facts
            else -> false
        }
    }

    private fun buildOpenQuestion(question: String): AssistantOpenQuestion? {
        val normalizedQuestion = normalize(question)
        return when {
            containsAny(normalizedQuestion, "bricheta", "chibrit", "amnar", "aprindere", "scanteie") ->
                AssistantOpenQuestion(
                    text = question,
                    targetSlot = "ignition_source",
                    allowedValues = listOf("lighter", "matches", "ferro", "recognized_spark", "none")
                )

            containsAny(normalizedQuestion, "caldura", "gatit", "fiert apa") ->
                AssistantOpenQuestion(
                    text = question,
                    targetSlot = "goal",
                    allowedValues = listOf("warmth", "cooking", "boil_water")
                )

            containsAny(normalizedQuestion, "ud", "uscat", "umezeala", "plouat") ->
                AssistantOpenQuestion(
                    text = question,
                    targetSlot = "fuel_condition",
                    allowedValues = listOf("dry", "damp", "wet", "unknown")
                )

            containsAny(normalizedQuestion, "iasca", "material fin", "ceva uscat") ->
                AssistantOpenQuestion(
                    text = question,
                    targetSlot = "tinder_available",
                    allowedValues = listOf("yes", "no"),
                    allowedAdditionalSlots = listOf("tinder_strategy", "tinder_material", "tinder_condition")
                )

            containsAny(normalizedQuestion, "surcele", "crengute", "betisoare") ->
                AssistantOpenQuestion(
                    text = question,
                    targetSlot = "kindling_available",
                    allowedValues = listOf("yes", "no")
                )

            containsAny(normalizedQuestion, "permis", "voie") ->
                AssistantOpenQuestion(
                    text = question,
                    targetSlot = "permission",
                    allowedValues = listOf("forbidden", "unknown")
                )

            containsAny(normalizedQuestion, "vant", "adapostit") ->
                AssistantOpenQuestion(
                    text = question,
                    targetSlot = "wind",
                    allowedValues = listOf("high", "moderate", "low")
                )

            containsAny(normalizedQuestion, "iarba uscata", "radacini", "cort") ->
                AssistantOpenQuestion(
                    text = question,
                    targetSlot = "ground_risk",
                    allowedValues = listOf("roots_or_peat", "dry_vegetation", "indoor_or_tent", "safe")
                )

            else -> null
        }
    }

    private fun isResolvedOpenQuestion(
        question: AssistantOpenQuestion,
        facts: Map<String, String>
    ): Boolean =
        question.targetSlot in facts

    private fun mergeValidatedSlotUpdates(
        facts: Map<String, String>,
        validatedSlotUpdates: Map<String, String>
    ): Map<String, String> {
        if (validatedSlotUpdates.isEmpty()) {
            return facts
        }
        val merged = facts.toMutableMap()
        validatedSlotUpdates.forEach { (slot, value) ->
            merged[slot] = value
        }
        return merged
    }

    private fun contextualizeSummary(
        summary: String,
        main: CampfireCard,
        facts: Map<String, String>,
        ignitionCompromise: IgnitionCompromise?
    ): String {
        if (main.family != CardFamily.SCENARIO) {
            return summary
        }
        ignitionCompromise?.let {
            return "Îmi pare rău. Hai să vedem ce alte opțiuni ai."
        }
        if ("ignition_source" !in facts || "ignition_source" in main.payload.slotConstraints) {
            return summary
        }
        val ignitionPrefix = when (facts["ignition_source"]) {
            "lighter" -> "Cum ai deja brichetă"
            "matches" -> "Cum ai deja chibrite"
            "ferro" -> "Cum ai deja amnar"
            "recognized_spark" -> "Cum poți deja produce scânteie"
            else -> null
        } ?: return summary
        val alreadySpecific = containsAny(normalize(summary), "bricheta", "chibrit", "amnar", "scanteie")
        if (alreadySpecific) {
            return summary
        }
        val loweredSummary = summary.replaceFirstChar { character ->
            if (character.isUpperCase()) character.lowercase() else character.toString()
        }
        return "$ignitionPrefix, $loweredSummary"
    }

    private fun compromiseReason(normalizedQuery: String): String? =
        when {
            containsAny(normalizedQuery, "am pierdut", "mi am pierdut", "nu mai am", "l am pierdut", "o am pierdut") -> "lost"
            containsAny(normalizedQuery, "s a stricat", "mi s a stricat", "e stricat", "sunt stricate", "s au stricat") -> "broken"
            containsAny(normalizedQuery, "nu mai merge", "nu functioneaza", "nu se mai poate folosi", "e inutilizabil", "e inutilizabila", "neutilizabil", "neutilizabila") -> "unusable"
            else -> null
        }

    private fun compromisedIgnitionItem(
        normalized: String,
        previousIgnitionSource: String?,
        compromisedReason: String?
    ): String? {
        if (compromisedReason == null) {
            return null
        }
        return when {
            previousIgnitionSource == "lighter" && containsAny(normalized, "bricheta", "bricheta mea") -> "lighter"
            previousIgnitionSource == "matches" && containsAny(normalized, "chibrit", "chibrite", "cutia de chibrite") -> "matches"
            previousIgnitionSource == "ferro" && containsAny(normalized, "amnar", "ferro", "tija") -> "ferro"
            previousIgnitionSource == "recognized_spark" && containsAny(normalized, "scanteie", "scanteia", "sursa de scanteie") -> "recognized_spark"
            previousIgnitionSource != null && previousIgnitionSource != "none" -> previousIgnitionSource
            else -> null
        }
    }

    private fun detectIgnitionCompromise(
        facts: Map<String, String>,
        previousState: AssistantConversationState
    ): IgnitionCompromise? {
        val previousIgnitionSource = previousState.facts["ignition_source"] ?: return null
        if (previousIgnitionSource == "none" || facts["ignition_source"] != "none") {
            return null
        }
        if (facts["compromised_item"] != previousIgnitionSource) {
            return null
        }
        val remainingOptions = IgnitionOptionOrder.filter { it != previousIgnitionSource }
        return IgnitionCompromise(
            item = previousIgnitionSource,
            remainingOptions = remainingOptions
        )
    }

    private fun remainingIgnitionOptionsQuestion(
        compromise: IgnitionCompromise?
    ): String? {
        compromise ?: return null
        val options = compromise.remainingOptions.mapNotNull(IgnitionOptionLabels::get)
        return when (options.size) {
            0 -> null
            1 -> "Mai ai ${options.first()}?"
            2 -> "Ai ${options[0]} sau ${options[1]}?"
            else -> "Ai ${options.dropLast(1).joinToString(", ")} sau ${options.last()}?"
        }
    }

    private fun buildRetrievedChunks(
        primary: CampfireCard?,
        support: CampfireCard?
    ): List<RetrievedChunk> =
        buildList {
            primary?.let { add(it.toRetrievedChunk(score = 100)) }
            support?.takeIf { it.chunk.chunkId != primary?.chunk?.chunkId }?.let {
                add(it.toRetrievedChunk(score = 88))
            }
        }

    private fun pickActionTitle(card: CampfireCard): String {
        val hash = card.chunk.chunkId.hashCode().toUInt()
        return when {
            card.family == CardFamily.CONSTRAINT -> "Concret"
            hash % 4u == 0u -> "Pașii tăi"
            hash % 4u == 1u -> "Ce faci acum"
            hash % 4u == 2u -> "Concret"
            else -> "De aici pornești"
        }
    }

    private fun pickAvoidTitle(card: CampfireCard): String {
        val hash = card.chunk.chunkId.hashCode().toUInt()
        return when (hash % 3u) {
            0u -> "Ce eviti"
            1u -> "Nu face asta"
            else -> "Greșeli frecvente"
        }
    }

    private fun pickWatchTitle(card: CampfireCard): String {
        val hash = card.chunk.chunkId.hashCode().toUInt()
        return when (hash % 3u) {
            0u -> "Semn de alarmă"
            1u -> "Fii atent la"
            else -> "La ce te uiți"
        }
    }

    private fun renderItems(items: List<String>): String =
        items.joinToString("; ") { it.trim().removeSuffix(".") + "." }

    private fun containsAny(normalized: String, vararg phrases: String): Boolean =
        phrases.any { phraseMatches(normalized, normalize(it)) }

    private fun phraseMatches(normalizedQuery: String, normalizedPhrase: String): Boolean {
        if (normalizedPhrase.isBlank()) {
            return false
        }
        if (normalizedPhrase in normalizedQuery) {
            return true
        }
        val ratio = phraseCoverageScore(
            phrase = normalizedPhrase,
            normalizedQuery = normalizedQuery,
            maxTokenDistance = 2
        )
        return ratio >= 1.0
    }

    private fun tokenize(value: String): Set<String> =
        normalize(value)
            .split(' ')
            .mapNotNull { token -> token.takeIf { it.length >= 3 } }
            .toSet()

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ", transform = ::normalizeRomanianToken)

    private fun normalizeRomanianToken(token: String): String {
        if (token.isBlank()) {
            return token
        }

        var normalized = token.replace("(.)\\1+".toRegex(), "$1")
        normalized = normalized.replace("([aeiou])x$".toRegex(), "$1s")
        if (normalized.endsWith('x') && normalized.length > 3) {
            normalized = normalized.dropLast(1) + "s"
        }
        normalized = when {
            normalized.endsWith("ului") && normalized.length > 6 -> normalized.dropLast(5)
            normalized.endsWith("ilor") && normalized.length > 6 -> normalized.dropLast(4)
            normalized.endsWith("elor") && normalized.length > 6 -> normalized.dropLast(4)
            normalized.endsWith("lor") && normalized.length > 5 -> normalized.dropLast(3)
            normalized.endsWith("ul") && normalized.length > 4 -> normalized.dropLast(2)
            normalized.endsWith("le") && normalized.length > 4 -> normalized.dropLast(2)
            normalized.endsWith('u') &&
                normalized.length > 3 &&
                normalized.takeLast(2) !in setOf("au", "eu", "iu", "ou") ->
                normalized.dropLast(1)
            else -> normalized
        }
        return normalized
    }

    private fun parseFamily(raw: String?): CardFamily? =
        when (raw?.trim()?.lowercase()) {
            "scenario" -> CardFamily.SCENARIO
            "definition" -> CardFamily.DEFINITION
            "constraint" -> CardFamily.CONSTRAINT
            else -> null
        }

    private fun CampfireCard.toRetrievedChunk(score: Int): RetrievedChunk =
        RetrievedChunk(
            topic = chunk.topic,
            sourceTitle = chunk.sourceTitle,
            sectionTitle = chunk.title,
            body = chunk.body,
            score = score,
            chunkId = chunk.chunkId,
            domain = chunk.domain,
            sourceUrl = chunk.sourceUrl,
            publisher = chunk.publisher,
            language = chunk.language,
            sourceTrust = 0,
            publishOrReviewDate = chunk.publishOrReviewDate,
            safetyTags = chunk.safetyTags,
            packVersion = chunk.packVersion
        )

    private companion object {
        private const val Domain = "field_know_how"
        private const val Topic = "campfire"
        private const val GeneralScenarioId = "campfire_general_entry"
        private const val OverrideConstraintMode = "override"
        private const val MaxSemanticPhraseCandidates = 5
        private const val SemanticLookupThreshold = 0.18
        private val IgnitionOptionOrder = listOf("lighter", "matches", "ferro")
        private val IgnitionOptionLabels = mapOf(
            "lighter" to "brichetă",
            "matches" to "chibrite",
            "ferro" to "amnar"
        )
        private val AbortCardIds = setOf(
            "campfire_not_worth_it_for_goal",
            "campfire_alternative_to_fire_for_warmth"
        )
    }
}

private data class CampfireCard(
    val chunk: KnowledgeChunkRecord,
    val family: CardFamily,
    val payload: CampfireCardPayload
)

private data class ScoredCampfireCard(
    val card: CampfireCard,
    val slotCompatibility: Double,
    val conversationCarryOver: Double,
    val semanticSimilarity: Double,
    val lexicalHints: Double,
    val priorityWeight: Double,
    val antiNegativeDefault: Double,
    val resolvedTermPenalty: Double,
    val finalScore: Double
) {
    val constraintActivationConfidence: Double
        get() = slotCompatibility * lexicalHints
}

private data class SemanticPhraseCandidate(
    val cardId: String,
    val normalizedPhrase: String,
    val phraseKind: String,
    val embedding: FloatArray,
    val lookupScore: Double
)

private data class SemanticQueryContext(
    val candidates: List<SemanticPhraseCandidate>
) {
    companion object {
        val Empty = SemanticQueryContext(emptyList())
    }
}

private data class IgnitionCompromise(
    val item: String,
    val remainingOptions: List<String>
)

@Serializable
private data class CampfireCardPayload(
    val family: String? = null,
    @SerialName("intent_group")
    val intentGroup: List<String> = emptyList(),
    @SerialName("user_phrasings")
    val userPhrasings: List<String> = emptyList(),
    @SerialName("slot_constraints")
    val slotConstraints: Map<String, String> = emptyMap(),
    val lead: String,
    @SerialName("actions_now")
    val actionsNow: List<String> = emptyList(),
    val avoid: List<String> = emptyList(),
    @SerialName("watch_for")
    val watchFor: List<String> = emptyList(),
    @SerialName("follow_up_questions")
    val followUpQuestions: List<CampfireFollowUpQuestion> = emptyList(),
    @SerialName("related_cards")
    val relatedCards: List<String> = emptyList(),
    @SerialName("constraint_mode")
    val constraintMode: String = "support",
    val term: String? = null,
    @SerialName("plain_language_definition")
    val plainLanguageDefinition: String? = null
)

@Serializable
private data class CampfireFollowUpQuestion(
    val question: String
)
