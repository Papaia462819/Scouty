package com.scouty.app.assistant.domain

import android.content.Context
import com.scouty.app.assistant.diagnostics.AssistantDiagnostics
import com.scouty.app.assistant.data.KnowledgeChunkStore
import com.scouty.app.assistant.data.KnowledgePackManager
import com.scouty.app.assistant.data.KnowledgePackStatusProvider
import com.scouty.app.assistant.data.SqliteKnowledgeChunkStore
import com.scouty.app.assistant.data.buildSearchTokens
import com.scouty.app.assistant.model.AssistantConversationState
import com.scouty.app.assistant.model.AssistantCitation
import com.scouty.app.assistant.model.AssistantResponse
import com.scouty.app.assistant.model.CardFamily
import com.scouty.app.assistant.model.ConversationLane
import com.scouty.app.assistant.model.DeviceContextSnapshot
import com.scouty.app.assistant.model.DomainHint
import com.scouty.app.assistant.model.GenerationMode
import com.scouty.app.assistant.model.KnowledgeChunkRecord
import com.scouty.app.assistant.model.KnowledgePackStatus
import com.scouty.app.assistant.model.ModelRuntimeState
import com.scouty.app.assistant.model.ModelStatus
import com.scouty.app.assistant.model.QueryAnalysis
import com.scouty.app.assistant.model.ReasoningType
import com.scouty.app.assistant.model.ResponseSectionStyle
import com.scouty.app.assistant.model.SafetyOutcome
import com.scouty.app.assistant.model.StructuredAssistantOutput
import com.scouty.app.assistant.model.StructuredResponseSection
import java.text.Normalizer
import java.time.Year
import kotlin.math.min

data class RetrievedChunk(
    val topic: String,
    val sourceTitle: String,
    val sectionTitle: String,
    val body: String,
    val score: Int,
    val chunkId: String = "",
    val domain: String = "",
    val sourceUrl: String? = null,
    val publisher: String? = null,
    val language: String = "ro",
    val sourceTrust: Int = 0,
    val publishOrReviewDate: String? = null,
    val safetyTags: List<String> = emptyList(),
    val packVersion: String? = null
)

data class AssistantPrompt(
    val query: String,
    val contextSummary: String,
    val citationsSummary: String,
    val reasoningSummary: String = ""
)

data class GenerationInput(
    val query: String,
    val prompt: AssistantPrompt,
    val queryAnalysis: QueryAnalysis,
    val retrievedChunks: List<RetrievedChunk>,
    val context: DeviceContextSnapshot,
    val safetyOutcome: SafetyOutcome,
    val generationMode: GenerationMode,
    val modelStatus: ModelStatus,
    val knowledgePackStatus: KnowledgePackStatus
)

class QueryAnalyzer {
    fun analyze(
        query: String,
        context: DeviceContextSnapshot,
        conversationState: AssistantConversationState = AssistantConversationState()
    ): QueryAnalysis {
        val tokens = buildSearchTokens(query)
        val rawTokens = buildLanguageTokens(query)
        val preferredLanguage = detectLanguage(query, rawTokens, tokens, context.localeTag)
        val normalizedQuery = normalizeTokenString(query)
        val routeContextQuery = tokens.any { it in RouteTokens } ||
            (context.trail != null && tokens.any { it in RouteContextTokens })
        val gearQuery = tokens.any { it in GearTokens }
        val campfireDefinitionQuery = isCampfireDefinitionQuery(normalizedQuery)
        val campfireConstraintQuery = isCampfireConstraintQuery(normalizedQuery)
        val campfireTopicQuery = tokens.any { it in CampfireTokens } ||
            containsAny(normalizedQuery, "foc", "campfire", "iasca", "amnar", "bricheta", "chibrit")
        val wildlifeBreakout = tokens.any { it in WildlifeBreakoutTokens }
        val campfireFollowUp = conversationState.activeTopic == "campfire" && !wildlifeBreakout && (
            tokens.size <= 4 ||
                conversationState.openQuestion != null ||
                campfireDefinitionQuery ||
                campfireConstraintQuery ||
                normalizedQuery.startsWith("cum") ||
                normalizedQuery.startsWith("si daca") ||
                normalizedQuery.startsWith("dar") ||
                normalizedQuery.startsWith("iar") ||
                normalizedQuery.startsWith("ce e") ||
                normalizedQuery.startsWith("ce inseamna")
            )
        val campfireLane = campfireTopicQuery || campfireFollowUp

        val domainHints = DomainKeywordMap.mapNotNull { (domain, keywords) ->
            val matches = tokens.count { token -> keywords.any { keyword -> keyword.startsWith(token) || token.startsWith(keyword) || token in keyword } }
            val weight = matches.toDouble() + when {
                campfireLane && domain == CampfireDomain -> 4.0
                routeContextQuery && domain == RouteDomain -> 3.0
                gearQuery && domain == GearDomain -> 3.0
                else -> 0.0
            }
            weight.takeIf { it > 0.0 }?.let { DomainHint(domain, it) }
        }.sortedByDescending { it.weight }
            .take(3)
            .ifEmpty {
                when {
                    campfireLane -> listOf(DomainHint(CampfireDomain, 3.0))
                    routeContextQuery -> listOf(DomainHint(RouteDomain, 2.0))
                    gearQuery -> listOf(DomainHint(GearDomain, 2.0))
                    else -> listOf(DomainHint("mountain_safety", 1.0))
                }
            }

        val reasoningType = when {
            campfireLane -> ReasoningType.KNOW_HOW
            routeContextQuery -> ReasoningType.ROUTE_CONTEXT
            gearQuery -> ReasoningType.GEAR_ADVICE
            domainHints.firstOrNull()?.domain == "weather_and_season" -> ReasoningType.WEATHER_CONTEXT
            domainHints.firstOrNull()?.domain in SafetyDomains -> ReasoningType.SAFETY_GUIDANCE
            else -> ReasoningType.GENERAL_RETRIEVAL
        }

        return QueryAnalysis(
            preferredLanguage = preferredLanguage,
            tokens = tokens,
            domainHints = domainHints,
            reasoningType = reasoningType,
            knowledgeLane = if (campfireLane) ConversationLane.FIELD_KNOW_HOW else ConversationLane.STANDARD,
            resolvedTopic = if (campfireLane) "campfire" else null,
            targetFamily = when {
                !campfireLane -> null
                campfireDefinitionQuery -> CardFamily.DEFINITION
                campfireConstraintQuery -> CardFamily.CONSTRAINT
                else -> CardFamily.SCENARIO
            },
            isFollowUp = campfireFollowUp,
            routeContextQuery = routeContextQuery,
            gearQuery = gearQuery,
            safetyTags = detectSafetyTags(tokens)
        ).also { analysis ->
            AssistantDiagnostics.logQueryAnalysis(query, analysis)
        }
    }

    private fun detectLanguage(
        query: String,
        rawTokens: List<String>,
        searchTokens: List<String>,
        localeTag: String
    ): String {
        val raw = query.lowercase()
        if (raw.any { it in "ăâîșşțţ" }) {
            return "ro"
        }
        val tokens = (rawTokens + searchTokens).distinct()
        val romanianHits = tokens.count { it in RomanianMarkers }
        val englishHits = tokens.count { it in EnglishMarkers }
        return when {
            romanianHits > englishHits -> "ro"
            englishHits > romanianHits -> "en"
            localeTag.startsWith("ro") -> "ro"
            else -> "en"
        }
    }

    private fun detectSafetyTags(tokens: List<String>): Set<String> {
        val tags = mutableSetOf<String>()
        tokens.forEach { token ->
            when {
                token in setOf("sangerare", "bleeding", "hemoragie") -> tags += "bleeding"
                token in setOf("sarpe", "snake", "muscatura", "bite", "bitten") -> tags += "snakebite"
                token in setOf("urs", "bear") -> tags += "bear"
                token in setOf("fulger", "lightning", "furtuna", "storm", "thunder") -> tags += "lightning"
                token in setOf("altitudine", "altitude", "hace", "hape") -> tags += "altitude"
                token in setOf("caldura", "heat", "deshidratare", "dehydration", "insolatie") -> tags += "heat"
                token in setOf("avalansa", "avalanche") -> tags += "avalanche"
                token in setOf("pierdut", "ratacit", "lost") -> tags += "lost"
            }
        }
        return tags
    }

    private fun buildLanguageTokens(query: String): List<String> =
        Normalizer.normalize(query.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .split(Regex("\\s+"))
            .mapNotNull { token ->
                token.trim()
                    .takeIf { it.length >= 2 }
                    ?.let { rawToken ->
                        listOfNotNull(rawToken, romanianBaseFormForLanguage(rawToken))
                    }
            }
            .flatten()
            .distinct()

    private fun romanianBaseFormForLanguage(token: String): String? =
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

    private fun isCampfireDefinitionQuery(normalizedQuery: String): Boolean =
        (normalizedQuery.startsWith("ce e") ||
            normalizedQuery.startsWith("ce inseamna") ||
            normalizedQuery.startsWith("ce este") ||
            normalizedQuery.startsWith("adica")) &&
            containsAny(normalizedQuery, "iasca", "kindling", "amnar", "triunghiul focului", "vatra")

    private fun isCampfireConstraintQuery(normalizedQuery: String): Boolean =
        containsAny(
            normalizedQuery,
            "totul e ud",
            "bate vantul",
            "vant puternic",
            "nu gasesc",
            "nu am",
            "nu merge",
            "nu se aprinde",
            "am voie",
            "interzis",
            "radacini",
            "iarba uscata",
            "in cort",
            "se intuneca",
            "sunt obosit",
            "merita sa mai incerc",
            "mai bine fac altceva"
        )

    private fun containsAny(normalized: String, vararg terms: String): Boolean =
        terms.any { normalizeTokenString(it) in normalized }

    private fun normalizeTokenString(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    private companion object {
        private const val CampfireDomain = "field_know_how"
        private const val RouteDomain = "route_intelligence_romania"
        private const val GearDomain = "gear_and_preparation"
        private val SafetyDomains = setOf(
            "medical_emergency",
            "mountain_safety",
            "weather_and_season",
            "wildlife_romania"
        )
        private val RouteTokens = setOf(
            "traseu", "route", "marcaj", "marker", "durata", "distance", "distanta",
            "plecare", "sosire", "porneste", "regiune", "provenienta", "source"
        )
        private val RouteContextTokens = setOf("cat", "care", "ce", "unde", "from", "to", "trail")
        private val GearTokens = setOf(
            "echipament", "gear", "rucsac", "backpack", "apa", "water", "jacheta",
            "geaca", "frontala", "headlamp", "kit", "iau"
        )
        private val WildlifeBreakoutTokens = setOf(
            "urs", "ursi", "bear", "bears", "sarpe", "snake", "lup", "wolf"
        )
        private val CampfireTokens = setOf(
            "foc", "focul", "campfire", "iasca", "amnar", "ferro", "bricheta",
            "brichete", "chibrit", "chibrite", "surcele", "vreascuri", "jar", "vatr"
        )
        private val RomanianMarkers = setOf(
            "am", "si", "sa", "traseu", "glezna", "entorsa", "durere", "rana", "arsura", "urs",
            "sarpe", "marcaj", "cum", "ce", "care", "cand", "unde", "munte", "fac", "facut",
            "procedez", "trebuie", "pot", "foc", "focul", "tabara", "siguranta"
        )
        private val EnglishMarkers = setOf(
            "the", "trail", "ankle", "bear", "snake", "marker", "how", "what", "when", "where", "mountain"
        )
        private val DomainKeywordMap = mapOf(
            "field_know_how" to setOf("foc", "focul", "campfire", "iasca", "amnar", "bricheta", "chibrit"),
            "medical_emergency" to setOf("glezna", "fractura", "bleeding", "sangerare", "altitude", "heat", "trauma", "accident"),
            "mountain_safety" to setOf("salvamont", "112", "lost", "ratacit", "plan", "rescue"),
            "survival_basics" to setOf("apa", "water", "purifica", "purify", "campfire", "foc"),
            "wildlife_romania" to setOf("urs", "bear", "snake", "sarpe"),
            "weather_and_season" to setOf("weather", "vreme", "fulger", "lightning", "avalansa", "avalanche"),
            "route_intelligence_romania" to setOf("traseu", "route", "marcaj", "marker", "durata", "distance", "distanta"),
            "gear_and_preparation" to setOf("gear", "echipament", "headlamp", "frontala", "water", "apa", "kit")
        )
    }
}

class RetrievalEngine(
    private val knowledgeStore: KnowledgeChunkStore,
    private val queryAnalyzer: QueryAnalyzer = QueryAnalyzer()
) {
    fun analyze(query: String, context: DeviceContextSnapshot): QueryAnalysis =
        queryAnalyzer.analyze(query, context)

    suspend fun retrieve(
        query: String,
        context: DeviceContextSnapshot,
        queryAnalysis: QueryAnalysis,
        limit: Int = 4
    ): List<RetrievedChunk> {
        val candidates = knowledgeStore.searchCandidates(
            query = query,
            preferredLanguages = listOf(queryAnalysis.preferredLanguage),
            domainHints = queryAnalysis.domainHints.map { it.domain },
            limit = limit * 6
        )

        val scoredCandidates = candidates.map { candidate ->
            RetrievedChunk(
                topic = candidate.topic,
                sourceTitle = candidate.sourceTitle,
                sectionTitle = candidate.title,
                body = candidate.body,
                score = rerankScore(queryAnalysis, candidate, context),
                chunkId = candidate.chunkId,
                domain = candidate.domain,
                sourceUrl = candidate.sourceUrl,
                publisher = candidate.publisher,
                language = candidate.language,
                sourceTrust = candidate.sourceTrust,
                publishOrReviewDate = candidate.publishOrReviewDate,
                safetyTags = candidate.safetyTags,
                packVersion = candidate.packVersion
            )
        }.sortedByDescending { it.score }

        return selectWithRedundancyPenalty(scoredCandidates, limit).also { selected ->
            AssistantDiagnostics.logRetrieval(
                query = query,
                selected = selected,
                scoredCandidates = scoredCandidates
            )
        }
    }

    private fun rerankScore(
        queryAnalysis: QueryAnalysis,
        candidate: KnowledgeChunkRecord,
        context: DeviceContextSnapshot
    ): Int {
        val normalizedTitle = normalize(candidate.title)
        val normalizedBody = normalize(candidate.body)
        val normalizedTopic = normalize(candidate.topic)
        val normalizedKeywords = normalize(candidate.keywords)
        val normalizedSource = normalize(candidate.sourceTitle)

        var lexicalScore = 0.0
        var matchedTokens = 0
        queryAnalysis.tokens.forEach { token ->
            val tokenScore = when {
                normalizedTitle.contains(token) -> 16.0
                normalizedTopic.contains(token) -> 14.0
                normalizedKeywords.contains(token) -> 11.0
                normalizedBody.contains(token) -> min(8.0, countOccurrences(normalizedBody, token) * 2.5)
                normalizedSource.contains(token) -> 4.0
                else -> 0.0
            }
            if (tokenScore > 0) {
                matchedTokens += 1
            }
            lexicalScore += tokenScore
        }
        if (matchedTokens > 1) {
            lexicalScore += matchedTokens * 4.0
        }

        val domainScore = queryAnalysis.domainHints.firstOrNull { it.domain == candidate.domain }?.let { it.weight * 12.0 } ?: 0.0
        val languageScore = when (candidate.language) {
            queryAnalysis.preferredLanguage -> 12.0
            else -> 2.0
        }
        val trustScore = candidate.sourceTrust * 3.0
        val freshnessScore = freshnessScore(candidate.publishOrReviewDate)
        val safetyScore = queryAnalysis.safetyTags.intersect(candidate.safetyTags.toSet()).size * 5.0

        val trail = context.trail
        val routeBoost = when {
            trail?.localCode != null && candidate.topic.equals(trail.localCode, ignoreCase = true) -> 24.0
            queryAnalysis.routeContextQuery && candidate.domain == "route_intelligence_romania" -> 10.0
            trail?.name != null && normalize(trail.name) in normalize(candidate.title) -> 6.0
            else -> 0.0
        }

        return (lexicalScore + domainScore + languageScore + trustScore + freshnessScore + safetyScore + routeBoost).toInt()
    }

    private fun selectWithRedundancyPenalty(
        candidates: List<RetrievedChunk>,
        limit: Int
    ): List<RetrievedChunk> {
        val selected = mutableListOf<RetrievedChunk>()
        val remaining = candidates.toMutableList()

        while (selected.size < limit && remaining.isNotEmpty()) {
            val next = remaining.maxByOrNull { candidate ->
                candidate.score - redundancyPenalty(candidate, selected)
            } ?: break
            selected += next.copy(score = next.score - redundancyPenalty(next, selected))
            remaining.remove(next)
        }
        return selected
    }

    private fun redundancyPenalty(candidate: RetrievedChunk, selected: List<RetrievedChunk>): Int {
        var penalty = 0.0
        selected.forEach { existing ->
            if (existing.topic == candidate.topic) {
                penalty += 18.0
            }
            if (existing.sourceTitle == candidate.sourceTitle) {
                penalty += 6.0
            }
            penalty += tokenOverlap(existing.body, candidate.body) * 10.0
        }
        return penalty.toInt()
    }

    private fun tokenOverlap(left: String, right: String): Double {
        val leftTokens = buildSearchTokens(left, shouldLog = false).toSet()
        val rightTokens = buildSearchTokens(right, shouldLog = false).toSet()
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0.0
        }
        val intersection = leftTokens.intersect(rightTokens).size.toDouble()
        val union = leftTokens.union(rightTokens).size.toDouble()
        return intersection / union
    }

    private fun freshnessScore(rawDate: String?): Double {
        val year = rawDate?.take(4)?.toIntOrNull() ?: return 0.0
        val age = Year.now().value - year
        return when {
            age <= 1 -> 6.0
            age <= 3 -> 4.0
            age <= 6 -> 2.0
            else -> 1.0
        }
    }

    private fun countOccurrences(text: String, token: String): Int {
        var count = 0
        var startIndex = 0
        while (true) {
            val index = text.indexOf(token, startIndex)
            if (index < 0) break
            count += 1
            startIndex = index + token.length
        }
        return count
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .trim()
}

class PromptBuilder {
    fun build(
        query: String,
        context: DeviceContextSnapshot,
        retrievedChunks: List<RetrievedChunk>,
        queryAnalysis: QueryAnalysis? = null
    ): AssistantPrompt {
        val trailSummary = context.trail?.let { trail ->
            buildString {
                append("Traseu activ: ${trail.name}")
                trail.region?.takeIf { it.isNotBlank() }?.let { append(" ($it)") }
                if (!trail.fromName.isNullOrBlank() || !trail.toName.isNullOrBlank()) {
                    append(", ${trail.fromName ?: "?"} -> ${trail.toName ?: "?"}")
                }
                trail.markingLabel?.takeIf { it.isNotBlank() }?.let { append(", marcaj $it") }
                trail.routeSummary?.takeIf { it.isNotBlank() }?.let { append(", $it") }
                trail.sunsetTime?.takeIf { it.isNotBlank() }?.let { append(", apus $it") }
                trail.weatherForecast?.takeIf { it.isNotBlank() }?.let { append(", vreme $it") }
            }
        } ?: "Fără traseu activ"

        val batterySummary = "Baterie ${context.batteryPercent}%${if (context.batterySafe) " / Battery Safe" else ""}"
        val gpsSummary = if (context.gpsFixed && context.latitude != null && context.longitude != null) {
            "GPS fix (${String.format("%.4f", context.latitude)}, ${String.format("%.4f", context.longitude)})"
        } else {
            "GPS fără fix stabil"
        }
        val gearSummary = context.recommendedGear.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let {
            "Gear shortlist: $it"
        } ?: "Gear shortlist indisponibil"

        return AssistantPrompt(
            query = query,
            contextSummary = listOf(trailSummary, batterySummary, gpsSummary, gearSummary).joinToString(" | "),
            citationsSummary = retrievedChunks.joinToString(" | ") { "${it.sourceTitle} -> ${it.sectionTitle}" },
            reasoningSummary = queryAnalysis?.let {
                listOfNotNull(
                    it.reasoningType.label,
                    it.domainHints.firstOrNull()?.domain,
                    it.preferredLanguage
                ).joinToString(" | ")
            }.orEmpty()
        )
    }
}

open class MedicalSafetyPolicy {
    open fun evaluate(
        query: String,
        retrievedChunks: List<RetrievedChunk>,
        context: DeviceContextSnapshot? = null
    ): SafetyOutcome {
        val haystack = buildString {
            append(normalize(query))
            append(' ')
            append(retrievedChunks.joinToString(" ") { normalize(it.body) })
            context?.trail?.routeSummary?.let { append(' '); append(normalize(it)) }
        }

        if (EmergencyMarkers.any { it in haystack }) {
            return SafetyOutcome.EMERGENCY_ESCALATION
        }

        return if (CautionMarkers.any { it in haystack }) {
            SafetyOutcome.CAUTION
        } else {
            SafetyOutcome.NORMAL
        }
    }

    fun applyFinalGuardrails(
        output: StructuredAssistantOutput,
        safetyOutcome: SafetyOutcome,
        isRomanian: Boolean
    ): StructuredAssistantOutput {
        if (safetyOutcome == SafetyOutcome.NORMAL || safetyOutcome == SafetyOutcome.CAUTION) {
            return output
        }

        val leadingSection = when (safetyOutcome) {
            SafetyOutcome.EMERGENCY_ESCALATION -> StructuredResponseSection(
                title = if (isRomanian) "Prioritate maxima" else "Top priority",
                body = if (isRomanian) {
                    "Semnele descrise cer prioritizarea 112 / SOS si limitarea deplasarilor inutile pana cand situatia este stabilizata."
                } else {
                    "The described warning signs require prioritizing 112 / SOS and limiting unnecessary movement until the situation is stabilized."
                },
                style = ResponseSectionStyle.IMPORTANT
            )
            SafetyOutcome.NORMAL -> return output
            SafetyOutcome.CAUTION -> return output
        }

        val sections = if (output.sections.firstOrNull()?.style == ResponseSectionStyle.IMPORTANT) {
            output.sections
        } else {
            listOf(leadingSection) + output.sections
        }

        val summary = if (output.summary.isNotBlank()) {
            output.summary
        } else if (isRomanian) {
            "Prioritizeaza 112 / SOS si apoi urmeaza pasii de baza confirmati offline."
        } else {
            "Prioritize 112 / SOS first and then follow the confirmed offline basics."
        }
        return output.copy(summary = summary, sections = sections)
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")

    private companion object {
        private val EmergencyMarkers = listOf(
            "nu pot sa calc", "nu pot calca", "deformare", "hemorag", "sangerare masiva", "inconstient",
            "nu respira", "durere in piept", "convulsii", "lesin", "stop cardiac", "pierderea cunostintei",
            "can't walk", "cannot walk", "deformity", "massive bleeding", "unconscious", "not breathing",
            "chest pain", "seizure", "cardiac arrest", "loss of consciousness", "collapsed"
        )
        private val CautionMarkers = listOf(
            "urs", "fractur", "hipoterm", "sangerare", "entorsa", "glezna", "sarpe", "muscatura",
            "fulger", "pierdut", "ratacit", "ameteli", "deshidratare", "rana", "taietura", "altitudine",
            "confuzie", "heat stroke", "heatstroke", "bear", "fractur", "hypotherm", "bleeding", "sprain",
            "ankle", "snake", "bite", "lightning", "lost", "dizziness", "dehydrat", "burn", "wound"
        )
    }
}

interface GenerationEngine {
    suspend fun generate(input: GenerationInput): StructuredAssistantOutput
}

class TemplateGenerationEngine : GenerationEngine {
    override suspend fun generate(input: GenerationInput): StructuredAssistantOutput {
        val isRomanian = input.queryAnalysis.preferredLanguage == "ro"
        val sections = mutableListOf<StructuredResponseSection>()

        input.retrievedChunks.firstOrNull()?.let { chunk ->
            sections += StructuredResponseSection(
                title = if (isRomanian) "Baza offline" else "Offline guidance",
                body = chunk.body,
                style = ResponseSectionStyle.GUIDANCE
            )
        }

        input.retrievedChunks.drop(1).take(2).takeIf { it.isNotEmpty() }?.let { extras ->
            sections += StructuredResponseSection(
                title = if (isRomanian) "Detalii utile" else "Useful details",
                body = extras.joinToString(" ") { it.body },
                style = ResponseSectionStyle.GUIDANCE
            )
        }

        buildMissingGroundingSection(input, isRomanian)?.let { sections += it }
        buildTrailContextSection(input, isRomanian)?.let { sections += it }
        buildActionSection(input, isRomanian)?.let { sections += it }

        val summary = when {
            input.safetyOutcome == SafetyOutcome.EMERGENCY_ESCALATION && isRomanian ->
                "Prioritatea este siguranta imediata, apoi pasii de baza verificati din knowledge pack."
            input.safetyOutcome == SafetyOutcome.EMERGENCY_ESCALATION ->
                "Immediate safety comes first, followed by the verified basic steps from the knowledge pack."
            input.queryAnalysis.routeContextQuery && isRomanian ->
                "Am combinat knowledge pack-ul offline cu contextul traseului activ."
            input.queryAnalysis.routeContextQuery ->
                "I combined the offline knowledge pack with the active trail context."
            input.retrievedChunks.isEmpty() && isRomanian ->
                "Nu am gasit inca un chunk suficient de apropiat in pack, asa ca raspund prudent si iti spun cum sa reformulezi pentru grounding mai bun."
            input.retrievedChunks.isEmpty() ->
                "I did not find a close enough knowledge chunk yet, so I am answering conservatively and showing how to rephrase for better grounding."
            isRomanian ->
                "Am selectat cele mai relevante chunk-uri offline pentru intrebarea ta."
            else ->
                "I selected the most relevant offline chunks for your question."
        }

        return StructuredAssistantOutput(
            summary = summary,
            sections = sections,
            generationMode = input.generationMode,
            reasoningType = input.queryAnalysis.reasoningType,
            modelVersion = input.modelStatus.modelVersion,
            knowledgePackVersion = input.knowledgePackStatus.packVersion
        )
    }

    private fun buildMissingGroundingSection(
        input: GenerationInput,
        isRomanian: Boolean
    ): StructuredResponseSection? {
        if (input.retrievedChunks.isNotEmpty()) {
            return null
        }

        val examples = when (input.queryAnalysis.domainHints.firstOrNull()?.domain) {
            "survival_basics" -> if (isRomanian) {
                "Exemple bune: \"foc de tabara in siguranta\", \"cum purific apa\", \"adapost temporar\"."
            } else {
                "Good examples: \"safe campfire basics\", \"how to purify water\", \"temporary shelter\"."
            }
            "medical_emergency" -> if (isRomanian) {
                "Exemple bune: \"mi-am sucit glezna\", \"sangerare puternica\", \"nu pot calca\"."
            } else {
                "Good examples: \"I twisted my ankle\", \"heavy bleeding\", \"I cannot walk\"."
            }
            "wildlife_romania" -> if (isRomanian) {
                "Exemple bune: \"urs aproape de cort\", \"muscatura de sarpe\", \"urme de urs\"."
            } else {
                "Good examples: \"bear near tent\", \"snakebite\", \"bear tracks\"."
            }
            else -> if (isRomanian) {
                "Exemple bune: \"cum purific apa\", \"mi-am sucit glezna\", \"ce fac daca vad urs\"."
            } else {
                "Good examples: \"how do I purify water\", \"I twisted my ankle\", \"what do I do if I see a bear\"."
            }
        }

        val body = if (isRomanian) {
            "Nu inventez pasi fara un chunk verificat din knowledge pack. Reformuleaza cu termeni concreti din problema ta ca sa pot ancora raspunsul mai bine. $examples"
        } else {
            "I do not invent steps without a verified knowledge chunk. Rephrase with concrete problem terms so I can ground the answer better. $examples"
        }

        return StructuredResponseSection(
            title = if (isRomanian) "Grounding mai bun" else "Better grounding",
            body = body,
            style = ResponseSectionStyle.ACTIONS
        )
    }

    private fun buildTrailContextSection(
        input: GenerationInput,
        isRomanian: Boolean
    ): StructuredResponseSection? {
        val trail = input.context.trail ?: return null
        val body = buildList {
            add(if (isRomanian) "Traseu activ: ${trail.name}." else "Active trail: ${trail.name}.")
            if (!trail.fromName.isNullOrBlank() || !trail.toName.isNullOrBlank()) {
                add(if (isRomanian) {
                    "Capete de traseu: ${trail.fromName ?: "?"} -> ${trail.toName ?: "?"}."
                } else {
                    "Route endpoints: ${trail.fromName ?: "?"} -> ${trail.toName ?: "?"}."
                })
            }
            trail.markingLabel?.takeIf { it.isNotBlank() }?.let {
                add(if (isRomanian) "Marcaj: $it." else "Trail marker: $it.")
            }
            trail.routeSummary?.takeIf { it.isNotBlank() }?.let {
                add(if (isRomanian) "Rezumat metadata: $it." else "Metadata summary: $it.")
            }
            trail.weatherForecast?.takeIf { it.isNotBlank() }?.let {
                add(if (isRomanian) "Vreme estimata: $it." else "Expected weather: $it.")
            }
            trail.sunsetTime?.takeIf { it.isNotBlank() }?.let {
                add(if (isRomanian) "Apus estimat: $it." else "Estimated sunset: $it.")
            }
            add(if (isRomanian) {
                "Baterie ${input.context.batteryPercent}%${if (input.context.batterySafe) " cu Battery Safe activ" else ""}; GPS ${if (input.context.gpsFixed) "disponibil" else "fara fix stabil"}."
            } else {
                "Battery ${input.context.batteryPercent}%${if (input.context.batterySafe) " with Battery Safe active" else ""}; GPS ${if (input.context.gpsFixed) "available" else "without a stable fix"}."
            })
            if (input.queryAnalysis.gearQuery && input.context.recommendedGear.isNotEmpty()) {
                add(if (isRomanian) {
                    "Shortlist gear curent: ${input.context.recommendedGear.joinToString(", ")}."
                } else {
                    "Current gear shortlist: ${input.context.recommendedGear.joinToString(", ")}."
                })
            }
        }.joinToString(" ")

        return StructuredResponseSection(
            title = if (isRomanian) "Context de teren" else "Field context",
            body = body,
            style = ResponseSectionStyle.CONTEXT
        )
    }

    private fun buildActionSection(
        input: GenerationInput,
        isRomanian: Boolean
    ): StructuredResponseSection? {
        val actions = mutableListOf<String>()
        if (input.context.batterySafe) {
            actions += if (isRomanian) {
                "Battery Safe este activ; pastreaza bateria pentru navigatie si apel de urgenta."
            } else {
                "Battery Safe is active; preserve battery for navigation and emergency calling."
            }
        }
        if (input.queryAnalysis.routeContextQuery && input.context.trail?.sourceUrls?.isNotEmpty() == true) {
            actions += if (isRomanian) {
                "Daca ai nevoie de confirmare, verifica si provenienta traseului din sursele atasate."
            } else {
                "If you need confirmation, also check the trail provenance from the attached sources."
            }
        }
        if (input.queryAnalysis.gearQuery && input.context.recommendedGear.isNotEmpty()) {
            actions += if (isRomanian) {
                "Pastreaza la indemana in primul rand echipamentul critic, nu tot rucsacul."
            } else {
                "Keep the critical gear accessible first, not the entire pack."
            }
        }
        return actions.takeIf { it.isNotEmpty() }?.let {
            StructuredResponseSection(
                title = if (isRomanian) "Actiuni imediate" else "Immediate actions",
                body = it.joinToString(" "),
                style = ResponseSectionStyle.ACTIONS
            )
        }
    }
}

class AssistantRepository(
    context: Context? = null,
    private val knowledgePackManager: KnowledgePackStatusProvider = context?.let(::KnowledgePackManager)
        ?: error("knowledgePackManager is required when context is null"),
    private val knowledgeStore: KnowledgeChunkStore = createKnowledgeStore(context, knowledgePackManager),
    private val queryAnalyzer: QueryAnalyzer = QueryAnalyzer(),
    private val retrievalEngine: RetrievalEngine = RetrievalEngine(knowledgeStore, queryAnalyzer),
    private val deterministicPreprocessor: DeterministicAssistantPreprocessor = DeterministicAssistantPreprocessor(),
    private val retrievalConfidencePolicy: RetrievalConfidencePolicy = RetrievalConfidencePolicy(),
    private val interpreterGate: InterpreterGate = InterpreterGate(retrievalConfidencePolicy),
    private val translationEngine: OnDeviceTranslationEngine = OnDeviceTranslationEngine(),
    private val interpreterPromptBuilder: InterpreterPromptBuilder = InterpreterPromptBuilder(translationEngine),
    private val campfireConversationEngine: CampfireConversationEngine = CampfireConversationEngine(
        knowledgeStore = knowledgeStore,
        confidencePolicy = retrievalConfidencePolicy
    ),
    private val promptBuilder: PromptBuilder = PromptBuilder(),
    private val modelManager: ModelManager = context?.let(::ModelManager)
        ?: error("modelManager is required when context is null"),
    private val slmInterpreterEngine: SlmInterpreterEngine = OnDeviceSlmInterpreterEngine(
        modelManager = modelManager,
        promptBuilder = interpreterPromptBuilder
    ),
    private val interpreterOutputValidator: InterpreterOutputValidator = InterpreterOutputValidator(),
    private val groundedQueryBuilder: GroundedQueryBuilder = GroundedQueryBuilder(),
    private val groundedWordingEngine: GroundedWordingEngine = OnDeviceGroundedWordingEngine(modelManager),
    private val generationEngine: GenerationEngine = LocalLlmGenerationEngine(
        modelManager = modelManager,
        fallbackEngine = TemplateGenerationEngine()
    ),
    private val medicalSafetyPolicy: MedicalSafetyPolicy = MedicalSafetyPolicy()
) {
    suspend fun answer(
        query: String,
        context: DeviceContextSnapshot,
        conversationState: AssistantConversationState = AssistantConversationState()
    ): AssistantResponse {
        val queryAnalysis = queryAnalyzer.analyze(query, context, conversationState)
        val preprocessing = deterministicPreprocessor.preprocess(query, conversationState, queryAnalysis)
        val packStatus = knowledgePackManager.ensureReady()
        if (queryAnalysis.knowledgeLane == ConversationLane.FIELD_KNOW_HOW && queryAnalysis.resolvedTopic == "campfire") {
            return answerCampfire(
                query = query,
                context = context,
                conversationState = conversationState,
                queryAnalysis = queryAnalysis,
                packStatus = packStatus,
                preprocessing = preprocessing
            )
        }
        return answerStandard(
            query = query,
            context = context,
            conversationState = conversationState,
            initialAnalysis = queryAnalysis,
            preprocessing = preprocessing,
            packStatus = packStatus
        )
    }

    private suspend fun answerCampfire(
        query: String,
        context: DeviceContextSnapshot,
        conversationState: AssistantConversationState,
        queryAnalysis: QueryAnalysis,
        packStatus: KnowledgePackStatus,
        preprocessing: DeterministicPreprocessingResult
    ): AssistantResponse {
        val initial = campfireConversationEngine.answer(
            query = query,
            context = context,
            queryAnalysis = queryAnalysis,
            knowledgePackStatus = packStatus,
            conversationState = conversationState,
            retrievalQuery = query,
            validatedSlotUpdates = preprocessing.obviousSlotUpdates,
            preprocessing = preprocessing
        )
        val gateDecision = interpreterGate.decide(
            assessment = initial.retrievalConfidence,
            preprocessing = preprocessing,
            conversationState = conversationState
        )
        val interpretation = if (gateDecision.shouldInvoke) {
            attemptValidatedInterpretation(
                query = query,
                context = context,
                conversationState = conversationState,
                queryAnalysis = queryAnalysis,
                assessment = initial.retrievalConfidence,
                preprocessing = preprocessing
            )
        } else {
            null
        }
        val finalCampfire = if (
            interpretation != null &&
            (interpretation.standaloneQuery != null || interpretation.slotUpdates.isNotEmpty())
        ) {
            val plan = groundedQueryBuilder.build(query, interpretation, preprocessing)
            val interpretedAnalysis = queryAnalyzer.analyze(plan.retrievalQuery, context, conversationState)
            val campfireAnalysis = if (
                interpretedAnalysis.knowledgeLane == ConversationLane.FIELD_KNOW_HOW &&
                interpretedAnalysis.resolvedTopic == "campfire"
            ) {
                interpretedAnalysis
            } else {
                queryAnalysis
            }
            val rerun = campfireConversationEngine.answer(
                query = query,
                context = context,
                queryAnalysis = campfireAnalysis,
                knowledgePackStatus = packStatus,
                conversationState = conversationState,
                retrievalQuery = plan.retrievalQuery,
                validatedSlotUpdates = plan.slotUpdates,
                preprocessing = preprocessing
            )
            if (retrievalConfidencePolicy.shouldAcceptRewrite(initial.retrievalConfidence, rerun.retrievalConfidence, interpretation)) {
                rerun
            } else {
                initial
            }
        } else {
            initial
        }

        val safetyOutcome = medicalSafetyPolicy.evaluate(query, finalCampfire.retrievedChunks, context)
        val wordedOutput = applyCampfireWordingIfSafe(
            query = query,
            preferredLanguage = queryAnalysis.preferredLanguage,
            structuredOutput = finalCampfire.structuredOutput,
            retrievedChunks = finalCampfire.retrievedChunks,
            confidence = finalCampfire.retrievalConfidence
        )
        val structuredOutput = medicalSafetyPolicy.applyFinalGuardrails(
            output = wordedOutput,
            safetyOutcome = safetyOutcome,
            isRomanian = queryAnalysis.preferredLanguage == "ro"
        )
        val modelStatus = modelManager.currentStatus()
        return AssistantResponse(
            answerText = buildDisplayText(structuredOutput, safetyOutcome),
            structuredOutput = structuredOutput,
            citations = buildCitations(queryAnalysis, context, finalCampfire.retrievedChunks),
            safetyOutcome = safetyOutcome,
            generationMode = structuredOutput.generationMode,
            reasoningType = structuredOutput.reasoningType,
            conversationState = finalCampfire.conversationState,
            modelVersion = modelStatus.modelVersion.takeIf { modelStatus.availableOnDisk },
            modelRuntimeState = modelStatus.state,
            modelStatusDetails = modelStatus.details,
            knowledgePackVersion = structuredOutput.knowledgePackVersion,
            usedFallback = false
        )
    }

    private suspend fun answerStandard(
        query: String,
        context: DeviceContextSnapshot,
        conversationState: AssistantConversationState,
        initialAnalysis: QueryAnalysis,
        preprocessing: DeterministicPreprocessingResult,
        packStatus: KnowledgePackStatus
    ): AssistantResponse {
        val modelStatus = modelManager.refreshStatus()
        val generationMode = generationModeForAttempt(modelStatus)
        AssistantDiagnostics.logAnswerStart(
            query = query,
            packStatus = packStatus,
            modelStatus = modelStatus,
            generationMode = generationMode
        )

        val initialRetrieved = retrievalEngine.retrieve(
            query = query,
            context = context,
            queryAnalysis = initialAnalysis,
            limit = 4
        )
        val initialAssessment = retrievalConfidencePolicy.assessStandard(
            query = query,
            queryAnalysis = initialAnalysis,
            conversationState = conversationState,
            retrieved = initialRetrieved,
            preprocessing = preprocessing
        )
        val gateDecision = interpreterGate.decide(
            assessment = initialAssessment,
            preprocessing = preprocessing,
            conversationState = conversationState
        )

        var finalAnalysis = initialAnalysis
        var finalRetrieved = initialRetrieved
        var acceptedInterpretation: ValidatedInterpretation? = null
        if (gateDecision.shouldInvoke) {
            val interpretation = attemptValidatedInterpretation(
                query = query,
                context = context,
                conversationState = conversationState,
                queryAnalysis = initialAnalysis,
                assessment = initialAssessment,
                preprocessing = preprocessing
            )
            if (interpretation != null) {
                val plan = groundedQueryBuilder.build(query, interpretation, preprocessing)
                val candidateAnalysis = queryAnalyzer.analyze(plan.retrievalQuery, context, conversationState)
                val candidateRetrieved = retrievalEngine.retrieve(
                    query = plan.retrievalQuery,
                    context = context,
                    queryAnalysis = candidateAnalysis,
                    limit = 4
                )
                val candidateAssessment = retrievalConfidencePolicy.assessStandard(
                    query = plan.retrievalQuery,
                    queryAnalysis = candidateAnalysis,
                    conversationState = conversationState,
                    retrieved = candidateRetrieved,
                    preprocessing = preprocessing
                )
                val acceptForResolvedAnaphora =
                    preprocessing.hasPronounReference &&
                        candidateRetrieved.firstOrNull()?.chunkId != initialRetrieved.firstOrNull()?.chunkId &&
                        candidateAssessment.score >= initialAssessment.score
                if (
                    retrievalConfidencePolicy.shouldAcceptRewrite(initialAssessment, candidateAssessment, interpretation) ||
                    acceptForResolvedAnaphora
                ) {
                    finalAnalysis = candidateAnalysis
                    finalRetrieved = candidateRetrieved
                    acceptedInterpretation = interpretation
                }
            }
        }

        val prompt = promptBuilder.build(
            query = query,
            context = context,
            retrievedChunks = finalRetrieved,
            queryAnalysis = finalAnalysis
        )
        val safetyOutcome = medicalSafetyPolicy.evaluate(query, finalRetrieved, context)
        val structuredOutput = medicalSafetyPolicy.applyFinalGuardrails(
            output = generationEngine.generate(
                GenerationInput(
                    query = query,
                    prompt = prompt,
                    queryAnalysis = finalAnalysis,
                    retrievedChunks = finalRetrieved,
                    context = context,
                    safetyOutcome = safetyOutcome,
                    generationMode = generationMode,
                    modelStatus = modelStatus,
                    knowledgePackStatus = packStatus
                )
            ),
            safetyOutcome = safetyOutcome,
            isRomanian = finalAnalysis.preferredLanguage == "ro"
        )
        val finalModelStatus = modelManager.currentStatus()
        AssistantDiagnostics.logAnswerEnd(
            query = query,
            packStatus = packStatus,
            modelStatus = finalModelStatus,
            generationMode = structuredOutput.generationMode,
            safetyOutcome = safetyOutcome,
            retrievedChunks = finalRetrieved
        )

        return AssistantResponse(
            answerText = buildDisplayText(structuredOutput, safetyOutcome),
            structuredOutput = structuredOutput,
            citations = buildCitations(finalAnalysis, context, finalRetrieved),
            safetyOutcome = safetyOutcome,
            generationMode = structuredOutput.generationMode,
            reasoningType = structuredOutput.reasoningType,
            conversationState = buildStandardConversationState(
                previousState = conversationState,
                originalQuery = query,
                analysis = finalAnalysis,
                retrieved = finalRetrieved,
                acceptedInterpretation = acceptedInterpretation
            ),
            modelVersion = finalModelStatus.modelVersion.takeIf {
                finalModelStatus.availableOnDisk || finalModelStatus.state != ModelRuntimeState.MISSING
            },
            modelRuntimeState = finalModelStatus.state,
            modelStatusDetails = finalModelStatus.details,
            knowledgePackVersion = structuredOutput.knowledgePackVersion,
            usedFallback = structuredOutput.generationMode == GenerationMode.FALLBACK_STRUCTURED
        )
    }

    private suspend fun attemptValidatedInterpretation(
        query: String,
        context: DeviceContextSnapshot,
        conversationState: AssistantConversationState,
        queryAnalysis: QueryAnalysis,
        assessment: RetrievalConfidenceAssessment,
        preprocessing: DeterministicPreprocessingResult
    ): ValidatedInterpretation? {
        val request = InterpreterRequest(
            query = query,
            preferredLanguage = queryAnalysis.preferredLanguage,
            queryAnalysis = queryAnalysis,
            conversationState = conversationState,
            retrievalConfidence = assessment,
            preprocessing = preprocessing,
            activeTrailLabel = context.trail?.name ?: conversationState.lastRetrievedTitle
        )
        val execution = slmInterpreterEngine.interpret(request)
        return interpreterOutputValidator.validate(request, execution)
    }

    private suspend fun applyCampfireWordingIfSafe(
        query: String,
        preferredLanguage: String,
        structuredOutput: StructuredAssistantOutput,
        retrievedChunks: List<RetrievedChunk>,
        confidence: RetrievalConfidenceAssessment
    ): StructuredAssistantOutput {
        if (confidence.tier == RetrievalConfidenceTier.LOW || retrievedChunks.isEmpty()) {
            return structuredOutput
        }
        val wording = groundedWordingEngine.rephrase(
            GroundedWordingRequest(
                query = query,
                preferredLanguage = preferredLanguage,
                deterministicOutput = structuredOutput,
                retrievedChunks = retrievedChunks
            )
        ) ?: return structuredOutput
        return structuredOutput.copy(summary = wording.summary)
    }

    private fun buildStandardConversationState(
        previousState: AssistantConversationState,
        originalQuery: String,
        analysis: QueryAnalysis,
        retrieved: List<RetrievedChunk>,
        acceptedInterpretation: ValidatedInterpretation?
    ): AssistantConversationState {
        val primary = retrieved.firstOrNull()
        return AssistantConversationState(
            activeTopic = null,
            lastUserMessage = originalQuery,
            lastStandaloneQuery = acceptedInterpretation?.standaloneQuery ?: originalQuery,
            lastRetrievedChunkId = primary?.chunkId,
            lastRetrievedTopic = primary?.topic ?: previousState.lastRetrievedTopic,
            lastRetrievedTitle = primary?.sectionTitle ?: previousState.lastRetrievedTitle,
            lastResolvedSlot = acceptedInterpretation?.slotUpdates?.keys?.firstOrNull(),
            lastInterpretationConfidence = acceptedInterpretation?.confidence,
            openQuestion = null
        )
    }

    private fun buildCitations(
        queryAnalysis: QueryAnalysis,
        context: DeviceContextSnapshot,
        retrieved: List<RetrievedChunk>
    ): List<AssistantCitation> {
        val citations = mutableListOf<AssistantCitation>()
        if (queryAnalysis.routeContextQuery && context.trail != null && context.trail.sourceUrls.isNotEmpty()) {
            citations += AssistantCitation(
                sourceTitle = context.trail.name,
                sectionTitle = "Active trail context",
                snippet = listOfNotNull(
                    context.trail.markingLabel?.let { "Marker: $it" },
                    context.trail.routeSummary
                ).joinToString(" | "),
                sourceUrl = context.trail.sourceUrls.firstOrNull(),
                publisher = "Scouty local route catalog"
            )
        }
        val visibleRetrieved = selectVisibleCitations(queryAnalysis, retrieved)
        citations += visibleRetrieved.map { chunk ->
            AssistantCitation(
                sourceTitle = chunk.sourceTitle,
                sectionTitle = chunk.sectionTitle,
                snippet = chunk.body.take(160).trimEnd() + if (chunk.body.length > 160) "..." else "",
                sourceUrl = chunk.sourceUrl,
                publisher = chunk.publisher
            )
        }
        return citations.distinctBy { Triple(it.sourceTitle, it.sectionTitle, it.sourceUrl) }.take(4)
    }

    private fun selectVisibleCitations(
        queryAnalysis: QueryAnalysis,
        retrieved: List<RetrievedChunk>
    ): List<RetrievedChunk> {
        if (queryAnalysis.routeContextQuery) {
            return retrieved.take(4)
        }

        val primary = retrieved.firstOrNull() ?: return emptyList()
        val narrowed = retrieved.filter { chunk ->
            chunk.language == queryAnalysis.preferredLanguage &&
                chunk.domain == primary.domain &&
                chunk.score >= primary.score - 18
        }
        val nonRoute = narrowed.ifEmpty {
            retrieved.filter { it.domain != "route_intelligence_romania" }
        }
        return nonRoute.ifEmpty { listOf(primary) }.take(3)
    }

    private fun buildDisplayText(
        output: StructuredAssistantOutput,
        safetyOutcome: SafetyOutcome
    ): String {
        val summary = sanitizeDisplayText(output.summary)
        val emergencyLead = if (safetyOutcome == SafetyOutcome.EMERGENCY_ESCALATION) {
            output.sections.firstOrNull { it.style == ResponseSectionStyle.IMPORTANT }
                ?.body
                ?.let(::sanitizeDisplayText)
                ?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val visibleBodies = output.sections.asSequence()
            .filter { it.style == ResponseSectionStyle.GUIDANCE || it.style == ResponseSectionStyle.ACTIONS }
            .map { section ->
                val body = sanitizeDisplayText(section.body)
                if (output.generationMode == GenerationMode.CARD_DIRECT) {
                    "${sanitizeDisplayText(section.title)}: $body"
                } else {
                    body
                }
            }
            .filter { it.isNotBlank() }
            .filter { normalizeForDisplay(it) != normalizeForDisplay(summary) }
            .distinctBy(::normalizeForDisplay)
            .take(2)
            .toList()

        val parts = mutableListOf<String>()
        emergencyLead?.let { parts += it }
        if (summary.isNotBlank() && !looksLikeMetaSummary(summary)) {
            parts += summary
        }
        parts += visibleBodies
        if (parts.isEmpty() && summary.isNotBlank()) {
            parts += summary
        }

        return parts.joinToString("\n\n").trim()
    }
    private fun looksLikeMetaSummary(summary: String): Boolean {
        val normalized = normalizeForDisplay(summary)
        return normalized.startsWith("am selectat") ||
            normalized.startsWith("i selected") ||
            normalized.startsWith("am combinat") ||
            normalized.startsWith("i combined") ||
            normalized.startsWith("raspuns prudent") ||
            normalized.startsWith("cautious answer") ||
            normalized.contains("knowledge pack") ||
            normalized.contains("chunk")
    }

    private fun sanitizeDisplayText(value: String): String =
        value.replace('\n', ' ')
            .replace("\\s+".toRegex(), " ")
            .trim()

    private fun normalizeForDisplay(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    private fun generationModeForAttempt(modelStatus: ModelStatus): GenerationMode =
        when {
            modelStatus.state == ModelRuntimeState.LOADED -> GenerationMode.LOCAL_LLM
            modelStatus.availableOnDisk && modelStatus.state in setOf(
                ModelRuntimeState.UNLOADED,
                ModelRuntimeState.PREPARING
            ) -> GenerationMode.LOCAL_LLM
            else -> GenerationMode.FALLBACK_STRUCTURED
        }

    private companion object {
        fun createKnowledgeStore(
            context: Context?,
            knowledgePackManager: KnowledgePackStatusProvider
        ): KnowledgeChunkStore {
            val concreteManager = knowledgePackManager as? KnowledgePackManager
            return when {
                concreteManager != null -> SqliteKnowledgeChunkStore(concreteManager)
                context == null -> error("knowledgeStore is required when context is null and knowledgePackManager is custom")
                else -> SqliteKnowledgeChunkStore(KnowledgePackManager(context))
            }
        }
    }
}
