package com.scouty.app.assistant.domain

import android.content.Context
import com.scouty.app.assistant.diagnostics.AssistantDiagnostics
import com.scouty.app.assistant.data.ChatActionHandler
import com.scouty.app.assistant.data.ConversationRole
import com.scouty.app.assistant.data.ConversationStore
import com.scouty.app.assistant.data.KnowledgeChunkStore
import com.scouty.app.assistant.data.KnowledgePackManager
import com.scouty.app.assistant.data.KnowledgePackStatusProvider
import com.scouty.app.assistant.data.SqliteKnowledgeChunkStore
import com.scouty.app.assistant.data.buildSearchTokens
import com.scouty.app.assistant.domain.memory.ConversationContextAssembler
import com.scouty.app.assistant.domain.memory.ConversationHistory
import com.scouty.app.assistant.domain.memory.SummaryCompactor
import com.scouty.app.assistant.domain.expression.CardParaphraseEngine
import com.scouty.app.assistant.domain.expression.CardParaphraseRequest
import com.scouty.app.assistant.domain.expression.ModelManagerCardParaphraseModel
import com.scouty.app.assistant.domain.retrieval.CrossEncoderReranker
import com.scouty.app.assistant.domain.tools.GrammarToolCallPlanner
import com.scouty.app.assistant.domain.tools.ModelManagerToolCallModel
import com.scouty.app.assistant.domain.tools.ToolDispatchRequest
import com.scouty.app.assistant.domain.tools.ToolDispatchResult
import com.scouty.app.assistant.domain.tools.ToolDispatcher
import com.scouty.app.assistant.model.AssistantConversationState
import com.scouty.app.assistant.model.AssistantCitation
import com.scouty.app.assistant.model.AssistantAction
import com.scouty.app.assistant.model.AssistantOpenQuestion
import com.scouty.app.assistant.model.AssistantResponse
import com.scouty.app.assistant.model.AssistantWeatherRequest
import com.scouty.app.assistant.model.AssistantWeatherResult
import com.scouty.app.assistant.model.CardFamily
import com.scouty.app.assistant.model.ConversationLane
import com.scouty.app.assistant.model.DeviceContextSnapshot
import com.scouty.app.assistant.model.DomainHint
import com.scouty.app.assistant.model.GearContextItem
import com.scouty.app.assistant.model.GearItemDraft
import com.scouty.app.assistant.model.GearItemUpdate
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
import com.scouty.app.assistant.model.TrailContextIntent
import com.scouty.app.assistant.model.WeatherHazard
import com.scouty.app.assistant.model.WeatherInteractionIntent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
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
    val packVersion: String? = null,
    val cardFamily: CardFamily? = null,
    val metadataJson: String? = null
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
    val knowledgePackStatus: KnowledgePackStatus,
    val conversationHistory: ConversationHistory? = null,
    val allowLocalModel: Boolean = false
)

private data class ConversationMemorySession(
    val conversationId: String,
    val history: ConversationHistory
)

private data class ExpressionLayerResult(
    val output: StructuredAssistantOutput,
    val retrievedChunks: List<RetrievedChunk>
)

class QueryAnalyzer(
    private val useCampfireLane: Boolean = false
) {
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
        val campfireFollowUp = conversationState.activeTopic == "campfire" && !wildlifeBreakout &&
            isCampfireFollowUpSignal(
                normalizedQuery = normalizedQuery,
                tokens = tokens,
                conversationState = conversationState,
                campfireDefinitionQuery = campfireDefinitionQuery,
                campfireConstraintQuery = campfireConstraintQuery
            )
        val campfireLane = useCampfireLane && (campfireTopicQuery || campfireFollowUp)

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
            campfireLane || domainHints.firstOrNull()?.domain == CampfireDomain -> ReasoningType.KNOW_HOW
            routeContextQuery -> ReasoningType.ROUTE_CONTEXT
            gearQuery -> ReasoningType.GEAR_ADVICE
            domainHints.firstOrNull()?.domain == "weather_and_season" -> ReasoningType.WEATHER_CONTEXT
            domainHints.firstOrNull()?.domain in SafetyDomains -> ReasoningType.SAFETY_GUIDANCE
            else -> ReasoningType.GENERAL_RETRIEVAL
        }

        val trailContextResult = if (!campfireLane) {
            detectTrailContextIntent(normalizedQuery, tokens, context, conversationState)
        } else {
            TrailContextDetection()
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
            safetyTags = detectSafetyTags(tokens),
            trailContextIntent = trailContextResult.intent,
            weatherQueryDate = trailContextResult.weatherDate
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
            containsAny(normalizedQuery, "iasca", "tinder", "tindar", "kindling", "amnar", "triunghiul focului", "vatra")

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

    private data class TrailContextDetection(
        val intent: TrailContextIntent = TrailContextIntent.NONE,
        val weatherDate: String? = null
    )

    private fun detectTrailContextIntent(
        normalizedQuery: String,
        tokens: List<String>,
        context: DeviceContextSnapshot,
        conversationState: AssistantConversationState
    ): TrailContextDetection {
        if (hasPerformanceHistoryTokens(normalizedQuery, tokens)) {
            return TrailContextDetection(intent = TrailContextIntent.PERFORMANCE_HISTORY)
        }

        if (context.trail == null) return TrailContextDetection()

        if (conversationState.pendingGearAction != null) {
            if (isGearConfirmation(normalizedQuery)) {
                return TrailContextDetection(intent = TrailContextIntent.GEAR_UPDATE_CONFIRM)
            }
        }

        if (hasWeatherTokens(tokens, normalizedQuery)) {
            val date = extractWeatherDate(normalizedQuery)
            return TrailContextDetection(
                intent = TrailContextIntent.WEATHER_FORECAST,
                weatherDate = date
            )
        }

        if (hasCapabilityTokens(normalizedQuery)) {
            return TrailContextDetection(intent = TrailContextIntent.CAPABILITY_CHECK)
        }

        if (hasDurationEstimateTokens(normalizedQuery)) {
            return TrailContextDetection(intent = TrailContextIntent.DURATION_ESTIMATE)
        }

        if (hasNeedsTokens(normalizedQuery)) {
            return TrailContextDetection(intent = TrailContextIntent.NEEDS_CHECK)
        }

        if (hasGearReviewTokens(normalizedQuery, tokens)) {
            return TrailContextDetection(intent = TrailContextIntent.GEAR_REVIEW)
        }

        if (hasTrailInfoTokens(normalizedQuery, tokens)) {
            return TrailContextDetection(intent = TrailContextIntent.TRAIL_INFO)
        }

        return TrailContextDetection()
    }

    private fun hasWeatherTokens(tokens: List<String>, normalizedQuery: String): Boolean =
        tokens.any { it in WeatherIntentTokens } ||
            containsAny(normalizedQuery, "cum va fi vremea", "prognoza meteo", "forecast",
                "ce vreme", "weather", "meteo", "precipitatii", "ploua", "ninge")

    private fun hasCapabilityTokens(normalizedQuery: String): Boolean =
        (containsAny(normalizedQuery, "pot face", "pot sa fac", "pot merge", "pot urca",
            "reusesc", "fac fata", "can i do", "can i make", "am i able", "is it possible") ||
            (normalizedQuery.startsWith("pot ") && containsAny(normalizedQuery, "daca", "if")) ||
            containsAny(normalizedQuery, "potrivit pentru", "suitable for", "recomandat pentru",
                "bun pentru", "good for", "ok pentru"))

    private fun hasDurationEstimateTokens(normalizedQuery: String): Boolean =
        containsAny(normalizedQuery, "cat ar dura", "cat dureaza daca", "how long would",
            "how long if", "cat timp daca", "cat de repede")

    private fun hasNeedsTokens(normalizedQuery: String): Boolean =
        containsAny(normalizedQuery, "am nevoie", "trebuie sa iau", "trebuie sa am",
            "do i need", "should i bring", "should i take", "what do i need",
            "ce am nevoie", "ce imi trebuie", "ce sa iau", "necesar")

    private fun hasGearReviewTokens(normalizedQuery: String, tokens: List<String>): Boolean =
        containsAny(normalizedQuery, "lista echipament", "arata lista", "gear list",
            "show list", "ce echipament", "what gear", "echipament complet", "full gear",
            "rucsac complet", "arata echipament", "bifez", "impachetat", "pack list",
            "lista de impachetat", "verifica echipament", "check gear",
            "vrei sa actualizez lista", "actualizez lista") ||
            (tokens.any { it in setOf("echipament", "gear", "rucsac") } &&
                tokens.any { it in setOf("lista", "list", "complet", "full", "arata", "show", "tot", "all") })

    private fun hasTrailInfoTokens(normalizedQuery: String, tokens: List<String>): Boolean =
        containsAny(normalizedQuery, "spune mi despre traseu", "tell me about the trail",
            "despre traseu", "about the trail", "detalii traseu", "trail details",
            "info traseu", "trail info") ||
            (tokens.any { it in setOf("traseu", "trail", "ruta", "route") } &&
                tokens.any { it in setOf("dificultate", "difficulty", "marcaj", "marker",
                    "durata", "duration", "distanta", "distance", "porneste", "start",
                    "termina", "end", "apus", "sunset", "descriere", "describe",
                    "informatii", "info", "detalii", "details") }) ||
            containsAny(normalizedQuery, "care e dificultatea", "what is the difficulty",
                "ce marcaj", "what marker", "cat dureaza", "how long is",
                "de unde porneste", "where does it start", "unde ajunge", "where does it end",
                "cat de lung", "how far", "ce distanta", "diferenta de nivel",
                "la ce ora apune", "what time sunset")

    private fun hasPerformanceHistoryTokens(normalizedQuery: String, tokens: List<String>): Boolean =
        containsAny(
            normalizedQuery,
            "ce performante am avut",
            "performantele mele",
            "istoric trasee",
            "istoricul traseelor",
            "trail history",
            "hiking history",
            "cat mi a luat",
            "cat timp mi a luat",
            "cat am facut traseul",
            "ce trasee am facut",
            "trasee am facut",
            "trasee terminate",
            "cate trasee am terminat",
            "distanta totala",
            "cati km am facut",
            "kilometri am facut",
            "ultima tura",
            "ultimul traseu"
        ) ||
            (tokens.any { it in setOf("performante", "istoric", "history") } &&
                tokens.any { it in setOf("trasee", "traseu", "trail", "hike", "ture") }) ||
            (containsAny(normalizedQuery, "cat", "cati", "cate", "durata", "timp") &&
                containsAny(normalizedQuery, "am facut", "am terminat", "mi a luat"))

    private fun isGearConfirmation(normalizedQuery: String): Boolean =
        normalizedQuery.trim() in setOf("da", "yes", "ok", "sigur", "sure", "bine", "hai",
            "fa o", "go ahead", "confirm", "nu", "no", "nope", "las", "lasa", "renunt",
            "cancel", "stop") ||
            containsAny(normalizedQuery, "bifez", "impachetat", "packed", "mark",
                "actualizeaza", "update", "tot ca impachetat", "all packed",
                "obligatoriu", "mandatory")

    private fun isCampfireFollowUpSignal(
        normalizedQuery: String,
        tokens: List<String>,
        conversationState: AssistantConversationState,
        campfireDefinitionQuery: Boolean,
        campfireConstraintQuery: Boolean
    ): Boolean {
        if (conversationState.openQuestion != null) {
            return true
        }
        if (campfireDefinitionQuery || campfireConstraintQuery) {
            return true
        }
        if (normalizedQuery.startsWith("si daca") || normalizedQuery.startsWith("dar") || normalizedQuery.startsWith("iar")) {
            return true
        }
        if (containsAny(
                normalizedQuery,
                "aprind", "aprinde", "aprindere", "sting", "stinge", "stins", "jar", "cenusa",
                "amnar", "bricheta", "chibrit", "iasca", "surcele", "vreascuri", "scanteie",
                "totul e ud", "daca e ud", "lemne ude", "plouat", "vant", "foc"
            )
        ) {
            return true
        }
        if (tokens.size <= 4 && tokens.any { it in CampfireFollowUpTokens }) {
            return true
        }
        return false
    }

    private fun extractWeatherDate(normalizedQuery: String): String? {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE

        if (containsAny(normalizedQuery, "azi", "today", "acum", "now")) {
            return today.format(formatter)
        }
        if (containsAny(normalizedQuery, "maine", "tomorrow", "miine")) {
            return today.plusDays(1).format(formatter)
        }
        if (containsAny(normalizedQuery, "poimaine", "day after tomorrow")) {
            return today.plusDays(2).format(formatter)
        }

        val dayNames = mapOf(
            "luni" to java.time.DayOfWeek.MONDAY,
            "marti" to java.time.DayOfWeek.TUESDAY,
            "miercuri" to java.time.DayOfWeek.WEDNESDAY,
            "joi" to java.time.DayOfWeek.THURSDAY,
            "vineri" to java.time.DayOfWeek.FRIDAY,
            "sambata" to java.time.DayOfWeek.SATURDAY,
            "duminica" to java.time.DayOfWeek.SUNDAY,
            "monday" to java.time.DayOfWeek.MONDAY,
            "tuesday" to java.time.DayOfWeek.TUESDAY,
            "wednesday" to java.time.DayOfWeek.WEDNESDAY,
            "thursday" to java.time.DayOfWeek.THURSDAY,
            "friday" to java.time.DayOfWeek.FRIDAY,
            "saturday" to java.time.DayOfWeek.SATURDAY,
            "sunday" to java.time.DayOfWeek.SUNDAY
        )
        dayNames.forEach { (name, dayOfWeek) ->
            if (name in normalizedQuery) {
                var target = today
                while (target.dayOfWeek != dayOfWeek || target == today) {
                    target = target.plusDays(1)
                }
                return target.format(formatter)
            }
        }

        val monthNames = mapOf(
            "ianuarie" to 1, "january" to 1, "ian" to 1, "jan" to 1,
            "februarie" to 2, "february" to 2, "feb" to 2,
            "martie" to 3, "march" to 3, "mar" to 3,
            "aprilie" to 4, "april" to 4, "apr" to 4,
            "mai" to 5, "may" to 5,
            "iunie" to 6, "june" to 6, "iun" to 6, "jun" to 6,
            "iulie" to 7, "july" to 7, "iul" to 7, "jul" to 7,
            "august" to 8, "aug" to 8,
            "septembrie" to 9, "september" to 9, "sep" to 9, "sept" to 9,
            "octombrie" to 10, "october" to 10, "oct" to 10,
            "noiembrie" to 11, "november" to 11, "nov" to 11,
            "decembrie" to 12, "december" to 12, "dec" to 12
        )

        for ((monthName, monthNum) in monthNames) {
            val pattern = "(\\d{1,2})\\s+$monthName".toRegex()
            val match = pattern.find(normalizedQuery)
            if (match != null) {
                val day = match.groupValues[1].toIntOrNull() ?: continue
                return try {
                    LocalDate.of(today.year, monthNum, day).let { date ->
                        if (date.isBefore(today)) date.plusYears(1) else date
                    }.format(formatter)
                } catch (_: Exception) { null }
            }
            val patternReverse = "$monthName\\s+(\\d{1,2})".toRegex()
            val matchReverse = patternReverse.find(normalizedQuery)
            if (matchReverse != null) {
                val day = matchReverse.groupValues[1].toIntOrNull() ?: continue
                return try {
                    LocalDate.of(today.year, monthNum, day).let { date ->
                        if (date.isBefore(today)) date.plusYears(1) else date
                    }.format(formatter)
                } catch (_: Exception) { null }
            }
        }

        val datePattern = "(\\d{1,2})\\s*[./]\\s*(\\d{1,2})".toRegex()
        datePattern.find(normalizedQuery)?.let { match ->
            val first = match.groupValues[1].toIntOrNull() ?: return null
            val second = match.groupValues[2].toIntOrNull() ?: return null
            val (day, month) = if (first <= 12 && second > 12) second to first else first to second
            return try {
                LocalDate.of(today.year, month, day).let { date ->
                    if (date.isBefore(today)) date.plusYears(1) else date
                }.format(formatter)
            } catch (_: Exception) { null }
        }

        if (containsAny(normalizedQuery, "weekend", "sfarsit de saptamana")) {
            var target = today
            while (target.dayOfWeek != java.time.DayOfWeek.SATURDAY) {
                target = target.plusDays(1)
            }
            return target.format(formatter)
        }

        return null
    }

    private companion object {
        private const val CampfireDomain = "campfire_basics"
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
        private val CampfireFollowUpTokens = setOf(
            "ud", "umed", "plouat", "vant", "amnar", "ferro", "bricheta", "chibrit",
            "iasca", "surcele", "vreascuri", "jar", "cenusa", "aprind", "sting"
        )
        private val RomanianMarkers = setOf(
            "am", "si", "sa", "traseu", "glezna", "entorsa", "durere", "rana", "arsura", "urs",
            "sarpe", "marcaj", "cum", "ce", "care", "cand", "unde", "munte", "fac", "facut",
            "procedez", "trebuie", "pot", "foc", "focul", "tabara", "siguranta"
        )
        private val EnglishMarkers = setOf(
            "the", "trail", "ankle", "bear", "snake", "marker", "how", "what", "when", "where", "mountain"
        )
        private val WeatherIntentTokens = setOf(
            "vreme", "meteo", "weather", "forecast", "prognoza", "temperatura",
            "ploaie", "rain", "ninge", "snow", "precipitat", "furtuna", "storm"
        )
        private val DomainKeywordMap = mapOf(
            "campfire_basics" to setOf("foc", "focul", "campfire", "iasca", "amnar", "bricheta", "chibrit", "jar", "surcele", "vreascuri"),
            "gear_and_preparation" to setOf("gear", "echipament", "headlamp", "frontala", "water", "apa", "kit", "rucsac", "bocanci", "jacheta"),
            "wildlife_romania" to setOf("urs", "ursi", "bear", "bears", "snake", "sarpe", "lup", "wolf", "urme", "animal"),
            "weather_and_season" to setOf("weather", "vreme", "meteo", "fulger", "lightning", "avalansa", "avalanche", "ploaie", "vant", "ninsoare"),
            "route_intelligence_romania" to setOf("traseu", "route", "marcaj", "marker", "durata", "distance", "distanta", "refugiu", "cabana"),
            "survival_basics" to setOf("apa", "water", "purifica", "purify", "adapost", "shelter", "supravietuire"),
            "tips_and_tricks" to setOf("sfat", "truc", "tip", "tips", "trick", "practic", "rapid"),
            "trail_culture_ro" to setOf("cabana", "cultura", "obicei", "refugiu", "salvamont", "eticheta"),
            "motivation_and_morale" to setOf("moral", "motivatie", "frica", "oboseala", "renunt", "continui"),
            "medical_emergency" to setOf("glezna", "fractura", "bleeding", "sangerare", "altitude", "heat", "trauma", "accident", "vipera", "hipotermie"),
            "mountain_safety" to setOf("salvamont", "112", "lost", "ratacit", "plan", "rescue", "siguranta", "furtuna")
        )
    }
}

class RetrievalEngine(
    private val knowledgeStore: KnowledgeChunkStore,
    private val queryAnalyzer: QueryAnalyzer = QueryAnalyzer(),
    private val crossEncoderReranker: CrossEncoderReranker? = null,
    private val useGeneralPathReranker: Boolean = true,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false }
) {
    fun analyze(query: String, context: DeviceContextSnapshot): QueryAnalysis =
        queryAnalyzer.analyze(query, context)

    suspend fun retrieve(
        query: String,
        context: DeviceContextSnapshot,
        queryAnalysis: QueryAnalysis,
        limit: Int = 4
    ): List<RetrievedChunk> {
        val hintedCandidates = knowledgeStore.searchCandidates(
            query = query,
            preferredLanguages = listOf(queryAnalysis.preferredLanguage),
            domainHints = queryAnalysis.domainHints.map { it.domain },
            limit = limit * 6
        )
        val broadCandidates = if (queryAnalysis.domainHints.isNotEmpty()) {
            knowledgeStore.searchCandidates(
                query = query,
                preferredLanguages = listOf(queryAnalysis.preferredLanguage),
                domainHints = emptyList(),
                limit = limit * 6
            )
        } else {
            emptyList()
        }
        val candidates = (hintedCandidates + broadCandidates).distinctBy { it.chunkId }

        val startedAtNanos = System.nanoTime()
        val lexicalScores = candidates.associateTo(mutableMapOf()) { candidate ->
            candidate.chunkId to rerankScore(queryAnalysis, candidate, context)
        }
        val rankedCandidates = applyCrossEncoderReranker(
            query = query,
            candidates = candidates,
            lexicalScores = lexicalScores
        )
        val scoredCandidates = rankedCandidates.map { candidate ->
            toRetrievedChunk(
                candidate = candidate,
                score = lexicalScores.getValue(candidate.chunkId)
            )
        }.sortedByDescending { it.score }

        return selectWithRedundancyPenalty(scoredCandidates, limit).also { selected ->
            AssistantDiagnostics.logRetrieval(
                query = query,
                selected = selected,
                scoredCandidates = scoredCandidates,
                elapsedMs = elapsedMsSince(startedAtNanos)
            )
        }
    }

    private suspend fun applyCrossEncoderReranker(
        query: String,
        candidates: List<KnowledgeChunkRecord>,
        lexicalScores: MutableMap<String, Int>
    ): List<KnowledgeChunkRecord> {
        val reranker = crossEncoderReranker ?: return candidates.sortedByDescending { lexicalScores.getValue(it.chunkId) }
        if (!useGeneralPathReranker || candidates.size < 2) {
            return candidates.sortedByDescending { lexicalScores.getValue(it.chunkId) }
        }

        val lexicalRanked = candidates.sortedByDescending { lexicalScores.getValue(it.chunkId) }
        val startedAtNanos = System.nanoTime()
        return runCatching {
            val byId = candidates.associateBy { it.chunkId }
            val reranked = reranker.rerank(
                query = query,
                candidates = lexicalRanked,
                topK = lexicalRanked.size
            )
            if (reranked.isEmpty()) {
                return lexicalRanked
            }

            val finalScores = lexicalScores.toMutableMap()
            reranked.forEach { result ->
                val lexical = lexicalScores[result.chunk.chunkId]?.toDouble() ?: 0.0
                finalScores[result.chunk.chunkId] = ((lexical * DeterministicScoreBlendWeight) +
                    (result.score * RerankerScoreBlendWeight)).toInt()
            }
            val top1 = reranked.firstOrNull()
            val top1Delta = top1?.let { result ->
                result.score - ((lexicalScores[result.chunk.chunkId] ?: 0).toDouble() / 100.0).coerceIn(0.0, 1.0)
            } ?: 0.0
            AssistantDiagnostics.logReranker(
                query = query,
                candidateCount = lexicalRanked.size,
                elapsedMs = elapsedMsSince(startedAtNanos),
                top1ScoreDeltaVsLexical = top1Delta,
                error = null
            )

            finalScores.forEach { (chunkId, score) ->
                if (chunkId in lexicalScores) {
                    lexicalScores[chunkId] = score
                }
            }
            finalScores.entries.sortedByDescending { it.value }.mapNotNull { byId[it.key] }
        }.getOrElse { error ->
            AssistantDiagnostics.logReranker(
                query = query,
                candidateCount = lexicalRanked.size,
                elapsedMs = elapsedMsSince(startedAtNanos),
                top1ScoreDeltaVsLexical = 0.0,
                error = error.message ?: error::class.java.simpleName
            )
            lexicalRanked
        }
    }

    private fun toRetrievedChunk(
        candidate: KnowledgeChunkRecord,
        score: Int
    ): RetrievedChunk =
        RetrievedChunk(
            topic = candidate.topic,
            sourceTitle = candidate.sourceTitle,
            sectionTitle = candidate.title,
            body = candidate.body,
            score = score,
            chunkId = candidate.chunkId,
            domain = candidate.domain,
            sourceUrl = candidate.sourceUrl,
            publisher = candidate.publisher,
            language = candidate.language,
            sourceTrust = candidate.sourceTrust,
            publishOrReviewDate = candidate.publishOrReviewDate,
            safetyTags = candidate.safetyTags,
            packVersion = candidate.packVersion,
            cardFamily = candidate.cardFamily,
            metadataJson = candidate.metadataJson
        )

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
        val normalizedLead = normalize(metadataText(candidate, "lead").orEmpty())

        var lexicalScore = 0.0
        var matchedTokens = 0
        queryAnalysis.tokens.forEach { token ->
            val tokenScore = when {
                normalizedTitle.contains(token) -> 16.0
                normalizedTopic.contains(token) -> 14.0
                normalizedLead.contains(token) -> 13.0
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

    private fun metadataText(candidate: KnowledgeChunkRecord, key: String): String? =
        metadata(candidate)?.get(key)
            ?.let { value ->
                when (value) {
                    is JsonPrimitive -> value.toString().trim('"')
                    else -> value.toString().trim('"')
                }
            }
            ?.takeIf { it.isNotBlank() }

    private fun metadata(candidate: KnowledgeChunkRecord): JsonObject? =
        candidate.metadataJson
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() }

    private fun elapsedMsSince(startedAtNanos: Long): Long =
        (System.nanoTime() - startedAtNanos) / 1_000_000

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .trim()

    private companion object {
        private const val DeterministicScoreBlendWeight = 0.8
        private const val RerankerScoreBlendWeight = 20.0
    }
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

private class DeterministicInteractionEngine {
    suspend fun answer(
        query: String,
        context: DeviceContextSnapshot,
        queryAnalysis: QueryAnalysis,
        conversationState: AssistantConversationState,
        packStatus: KnowledgePackStatus,
        interactionHandler: ChatActionHandler?
    ): AssistantResponse? {
        val normalized = normalize(query)
        detectGearIntent(normalized, context)?.let { intent ->
            return answerGear(
                intent = intent,
                query = query,
                normalized = normalized,
                context = context,
                queryAnalysis = queryAnalysis,
                conversationState = conversationState,
                packStatus = packStatus
            )
        }
        detectWeatherIntent(normalized)?.let { intent ->
            return answerWeather(
                intent = intent,
                context = context,
                queryAnalysis = queryAnalysis,
                conversationState = conversationState,
                packStatus = packStatus,
                interactionHandler = interactionHandler
            )
        }
        return null
    }

    private fun detectGearIntent(normalized: String, context: DeviceContextSnapshot): GearInteractionIntent? {
        val mentionsGearList = containsAny(normalized, "echipament", "gear", "rucsac", "lista", "checklist")
        val mentionsKnownGear = context.gearItems.any { item ->
            gearTextMatches(normalized, item) || gearAliasesFor(item).any { alias -> alias in normalized }
        }
        val objectText = extractGearObject(normalized)
        val hasAdd = containsAny(normalized, "adauga", "adaug", "pune ", "trece ", "add ")
        val hasRemove = containsAny(normalized, "scoate", "sterge", "elimina", "remove", "delete")
        val hasUpdate = containsAny(normalized, "obligator", "recomandat", "recomandata", "optional", "conditionat")
        val hasPacked = containsAny(normalized, "bifeaza", "am pus", "am luat", "am impachetat", "impachetat", "packed", "mark")
        val hasUnpacked = containsAny(normalized, "debifeaza", "nu am", "n am", "nu iau", "unpacked", "unmark")
        val showList = containsAny(normalized, "arata lista", "lista echipament", "show gear", "gear list")
        val missing = containsAny(normalized, "ce imi lipseste", "ce lipseste", "ce mai lipseste", "missing")
        val weatherAdvice = mentionsGearList && containsAny(normalized, "ploua", "ploaie", "furtuna", "frig", "cald", "ninsoare", "zapada", "vreme")
        val campfireContext = containsAny(normalized, "foc", "aprind", "aprindere", "amnar", "bricheta", "chibrit", "iasca")
        if (campfireContext && !mentionsGearList && !hasAdd && !hasRemove && !hasUpdate && !showList && !missing) {
            return null
        }

        return when {
            showList -> GearInteractionIntent.SHOW_LIST
            missing -> GearInteractionIntent.MISSING_ITEMS
            weatherAdvice -> GearInteractionIntent.WEATHER_BASED_ADVICE
            hasRemove -> GearInteractionIntent.REMOVE_ITEM
            hasAdd -> GearInteractionIntent.ADD_ITEM
            hasUpdate && (mentionsGearList || mentionsKnownGear) -> GearInteractionIntent.UPDATE_ITEM
            hasUnpacked && (mentionsKnownGear || mentionsGearList) -> GearInteractionIntent.MARK_UNPACKED
            hasPacked && (mentionsKnownGear || mentionsGearList || objectText.isNotBlank()) -> GearInteractionIntent.MARK_PACKED
            else -> null
        }
    }

    private fun answerGear(
        intent: GearInteractionIntent,
        query: String,
        normalized: String,
        context: DeviceContextSnapshot,
        queryAnalysis: QueryAnalysis,
        conversationState: AssistantConversationState,
        packStatus: KnowledgePackStatus
    ): AssistantResponse {
        val isRomanian = queryAnalysis.preferredLanguage == "ro"
        val objectText = extractGearObject(normalized)
        val match = matchGearItem(objectText, context.gearItems)
        val actions = mutableListOf<AssistantAction>()
        val sections = mutableListOf<StructuredResponseSection>()
        val followUps = mutableListOf<String>()
        var summary: String

        when (intent) {
            GearInteractionIntent.SHOW_LIST -> {
                val packed = context.gearItems.count { it.isPacked }
                summary = if (isRomanian) {
                    "Lista are $packed din ${context.gearItems.size} articole bifate."
                } else {
                    "The list has $packed of ${context.gearItems.size} items packed."
                }
                sections += StructuredResponseSection(
                    title = if (isRomanian) "Echipament" else "Gear",
                    body = context.gearItems.ifEmpty { emptyList() }.joinToString("\n") { item ->
                        val status = if (item.isPacked) {
                            if (isRomanian) "impachetat" else "packed"
                        } else {
                            if (isRomanian) "neimpachetat" else "not packed"
                        }
                        "- ${item.name} ($status)"
                    }.ifBlank {
                        if (isRomanian) "Nu ai articole in lista." else "There are no items in the list."
                    },
                    style = ResponseSectionStyle.ACTIONS
                )
            }

            GearInteractionIntent.MISSING_ITEMS -> {
                val missing = context.gearItems.filter { !it.isPacked }
                val mandatory = missing.filter { it.necessity.equals("MANDATORY", ignoreCase = true) }
                summary = if (mandatory.isNotEmpty()) {
                    if (isRomanian) {
                        "Iti lipsesc ${mandatory.size} articole obligatorii."
                    } else {
                        "You are missing ${mandatory.size} mandatory items."
                    }
                } else if (missing.isNotEmpty()) {
                    if (isRomanian) {
                        "Obligatoriul pare acoperit, dar mai ai ${missing.size} articole nebifate."
                    } else {
                        "Mandatory gear looks covered, but ${missing.size} items are still unchecked."
                    }
                } else {
                    if (isRomanian) "Lista este bifata complet." else "The list is fully checked."
                }
                if (missing.isNotEmpty()) {
                    sections += StructuredResponseSection(
                        title = if (isRomanian) "Nebifate" else "Unchecked",
                        body = missing.joinToString("\n") { "- ${it.name}" },
                        style = ResponseSectionStyle.ACTIONS
                    )
                }
            }

            GearInteractionIntent.WEATHER_BASED_ADVICE -> {
                val weather = context.trail?.weatherForecast.orEmpty()
                val rainItems = context.gearItems.filter { item ->
                    val text = normalize("${item.name} ${item.note}")
                    containsAny(text, "ploaie", "impermeabil", "pelerina", "jacheta", "husa", "poncho", "rain", "waterproof")
                }
                summary = if (weather.isNotBlank()) {
                    if (isRomanian) "Pentru vremea estimata ($weather), protejeaza-te de conditii meteo." else
                        "For the expected weather ($weather), prioritize weather protection."
                } else {
                    if (isRomanian) "Nu am o prognoza clara in context, dar pot verifica lista pentru protectie meteo." else
                        "I do not have a clear forecast in context, but I can check the list for weather protection."
                }
                sections += StructuredResponseSection(
                    title = if (isRomanian) "Prioritar" else "Priority",
                    body = rainItems.takeIf { it.isNotEmpty() }?.joinToString("\n") { "- ${it.name}" }
                        ?: if (isRomanian) "Adauga protectie de ploaie/vant daca prognoza se inrautateste." else
                            "Add rain/wind protection if the forecast worsens.",
                    style = ResponseSectionStyle.GUIDANCE
                )
            }

            GearInteractionIntent.ADD_ITEM -> {
                when (match) {
                    is GearItemMatch.Single -> {
                        if (containsAny(normalized, "am pus", "am luat", "impachetat", "packed")) {
                            actions += AssistantAction.ToggleGearPacked(listOf(match.item.id), true)
                            summary = if (isRomanian) {
                                "Am marcat ${match.item.name} ca impachetat."
                            } else {
                                "I marked ${match.item.name} as packed."
                            }
                        } else {
                            summary = if (isRomanian) {
                                "${match.item.name} este deja in lista."
                            } else {
                                "${match.item.name} is already in the list."
                            }
                        }
                    }
                    is GearItemMatch.Ambiguous -> return ambiguousGearResponse(match.items, queryAnalysis, conversationState, packStatus)
                    GearItemMatch.None -> {
                        val name = displayGearName(objectText.ifBlank { "articol" })
                        val draft = GearItemDraft(
                            id = customGearId(name),
                            name = name,
                            packed = containsAny(normalized, "am pus", "am luat", "impachetat", "packed")
                        )
                        actions += AssistantAction.AddGearItems(listOf(draft))
                        summary = if (isRomanian) {
                            "Am adaugat $name in lista${if (draft.packed) " si l-am bifat" else ""}."
                        } else {
                            "I added $name to the list${if (draft.packed) " and marked it packed" else ""}."
                        }
                    }
                }
            }

            GearInteractionIntent.REMOVE_ITEM -> {
                when (match) {
                    is GearItemMatch.Single -> {
                        actions += AssistantAction.RemoveGearItems(listOf(match.item.id))
                        summary = if (isRomanian) {
                            "Am scos ${match.item.name} din lista."
                        } else {
                            "I removed ${match.item.name} from the list."
                        }
                        if (match.item.necessity.equals("MANDATORY", ignoreCase = true)) {
                            sections += StructuredResponseSection(
                                title = if (isRomanian) "Atentie" else "Note",
                                body = if (isRomanian) {
                                    "Era marcat ca obligatoriu pentru traseu; verifica daca ai o alternativa inainte sa pleci."
                                } else {
                                    "It was marked mandatory for the trail; check that you have an alternative before leaving."
                                },
                                style = ResponseSectionStyle.IMPORTANT
                            )
                        }
                    }
                    is GearItemMatch.Ambiguous -> return ambiguousGearResponse(match.items, queryAnalysis, conversationState, packStatus)
                    GearItemMatch.None -> {
                        summary = if (isRomanian) "Nu am gasit articolul in lista." else "I could not find that item in the list."
                    }
                }
            }

            GearInteractionIntent.MARK_PACKED,
            GearInteractionIntent.MARK_UNPACKED -> {
                val packed = intent == GearInteractionIntent.MARK_PACKED
                when (match) {
                    is GearItemMatch.Single -> {
                        actions += AssistantAction.ToggleGearPacked(listOf(match.item.id), packed)
                        summary = if (packed) {
                            if (isRomanian) "Am bifat ${match.item.name} ca impachetat." else "I marked ${match.item.name} as packed."
                        } else {
                            if (isRomanian) "Am debifat ${match.item.name}." else "I marked ${match.item.name} as not packed."
                        }
                    }
                    is GearItemMatch.Ambiguous -> return ambiguousGearResponse(match.items, queryAnalysis, conversationState, packStatus)
                    GearItemMatch.None -> {
                        if (intent == GearInteractionIntent.MARK_PACKED && objectText.isNotBlank()) {
                            val name = displayGearName(objectText)
                            actions += AssistantAction.AddGearItems(
                                listOf(GearItemDraft(id = customGearId(name), name = name, packed = true))
                            )
                            summary = if (isRomanian) "Nu era in lista, asa ca am adaugat $name si l-am bifat." else
                                "It was not in the list, so I added $name and marked it packed."
                        } else {
                            summary = if (isRomanian) "Nu am gasit articolul in lista." else "I could not find that item in the list."
                        }
                    }
                }
            }

            GearInteractionIntent.UPDATE_ITEM -> {
                val necessity = detectNecessity(normalized)
                when (match) {
                    is GearItemMatch.Single -> {
                        actions += AssistantAction.UpdateGearItems(
                            listOf(GearItemUpdate(itemId = match.item.id, necessity = necessity))
                        )
                        summary = if (isRomanian) {
                            "Am actualizat ${match.item.name} la ${necessityLabel(necessity, true)}."
                        } else {
                            "I updated ${match.item.name} to ${necessityLabel(necessity, false)}."
                        }
                    }
                    is GearItemMatch.Ambiguous -> return ambiguousGearResponse(match.items, queryAnalysis, conversationState, packStatus)
                    GearItemMatch.None -> {
                        summary = if (isRomanian) "Nu am gasit articolul de actualizat." else "I could not find the item to update."
                    }
                }
            }
        }

        if (followUps.isEmpty()) {
            followUps += if (isRomanian) listOf("Arata-mi lista", "Ce imi lipseste?") else listOf("Show me the list", "What is missing?")
        }
        return structuredInteractionResponse(
            summary = summary,
            sections = sections,
            followUps = followUps,
            reasoningType = ReasoningType.GEAR_ADVICE,
            queryAnalysis = queryAnalysis,
            conversationState = conversationState.copy(
                activeTopic = null,
                pendingGearAction = null,
                lastTrailContextIntent = "GEAR_${intent.name}"
            ),
            packStatus = packStatus,
            actions = actions
        )
    }

    private suspend fun answerWeather(
        intent: WeatherInteraction,
        context: DeviceContextSnapshot,
        queryAnalysis: QueryAnalysis,
        conversationState: AssistantConversationState,
        packStatus: KnowledgePackStatus,
        interactionHandler: ChatActionHandler?
    ): AssistantResponse {
        val isRomanian = queryAnalysis.preferredLanguage == "ro"
        val trail = context.trail
        val lat = trail?.latitude ?: context.latitude
        val lon = trail?.longitude ?: context.longitude
        val locationLabel = when {
            trail?.latitude != null && trail.longitude != null -> trail.name
            context.gpsFixed -> if (isRomanian) "locatia curenta" else "current location"
            else -> trail?.name
        }
        if (lat == null || lon == null) {
            val cached = trail?.weatherForecast?.takeIf { it.isNotBlank() }
            val summary = cached?.let {
                if (isRomanian) "Nu am coordonate pentru verificare live. Ultima prognoza din traseu: $it." else
                    "I do not have coordinates for a live check. Last trail forecast: $it."
            } ?: if (isRomanian) {
                "Nu pot verifica vremea live fara traseu activ sau pozitie GPS."
            } else {
                "I cannot check live weather without an active trail or GPS position."
            }
            return structuredInteractionResponse(
                summary = summary,
                sections = emptyList(),
                followUps = emptyList(),
                reasoningType = ReasoningType.WEATHER_CONTEXT,
                queryAnalysis = queryAnalysis,
                conversationState = conversationState.copy(lastTrailContextIntent = "WEATHER_FORECAST"),
                packStatus = packStatus,
                safetyOutcome = SafetyOutcome.CAUTION
            )
        }

        val request = AssistantWeatherRequest(
            latitude = lat,
            longitude = lon,
            altitudeMeters = context.altitude?.toInt(),
            locationLabel = locationLabel,
            intent = intent.intent,
            offsetHours = intent.offsetHours,
            targetDate = intent.targetDate,
            targetHour = intent.targetHour,
            hazard = intent.hazard,
            preferredLanguage = queryAnalysis.preferredLanguage
        )
        val result = interactionHandler?.queryWeather(request)
            ?: cachedWeatherResult(context, request)
        val sections = mutableListOf<StructuredResponseSection>()
        result.hourly?.let { hourly ->
            sections += StructuredResponseSection(
                title = if (isRomanian) "Detalii" else "Details",
                body = hourlyDetails(hourly, isRomanian),
                style = ResponseSectionStyle.CONTEXT
            )
        }
        if (intent.hazard != null) {
            sections += StructuredResponseSection(
                title = if (isRomanian) "Interpretare" else "Interpretation",
                body = hazardAdvice(intent.hazard, result, isRomanian),
                style = ResponseSectionStyle.GUIDANCE
            )
        }
        return structuredInteractionResponse(
            summary = weatherSummary(result, intent, isRomanian),
            sections = sections,
            followUps = if (isRomanian) listOf("Ce echipament imi trebuie?", "Verifica peste 3 ore") else
                listOf("What gear do I need?", "Check in 3 hours"),
            reasoningType = ReasoningType.WEATHER_CONTEXT,
            queryAnalysis = queryAnalysis,
            conversationState = conversationState.copy(
                activeTopic = null,
                lastTrailContextIntent = "WEATHER_FORECAST"
            ),
            packStatus = packStatus,
            safetyOutcome = if (result.available) SafetyOutcome.NORMAL else SafetyOutcome.CAUTION
        )
    }

    private fun detectWeatherIntent(normalized: String): WeatherInteraction? {
        val hazard = detectWeatherHazard(normalized)
        val mentionsWeather = containsAny(
            normalized,
            "vreme", "meteo", "weather", "forecast", "prognoza", "ploua", "ploaie",
            "furtuna", "fulger", "frig", "cald", "ceata", "vant", "ninge", "zapada"
        )
        val directWeatherAsk = containsAny(
            normalized,
            "vreme", "meteo", "weather", "forecast", "prognoza", "cum va fi",
            "ploua peste", "ploua in", "va ploua", "o sa ploua", "e risc", "risc de",
            "cat de frig va fi", "cat de cald va fi", "e furtuna", "sunt fulgere"
        ) || normalized.startsWith("ploua ") ||
            normalized.startsWith("ninge ") ||
            normalized.startsWith("e frig") ||
            normalized.startsWith("e cald")
        if ((!mentionsWeather && hazard == null) || !directWeatherAsk) {
            return null
        }
        val offsetHours = Regex("""(?:peste|in|în)\s+(\d{1,2})\s+(?:ore|ora|hours?|h)\b""")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        val targetHour = Regex("""(?:la\s+ora|ora)\s+(\d{1,2})""")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.takeIf { it in 0..23 }
        val targetDate = when {
            containsAny(normalized, "maine", "miine", "tomorrow") -> LocalDate.now().plusDays(1).toString()
            containsAny(normalized, "poimaine") -> LocalDate.now().plusDays(2).toString()
            else -> LocalDate.now().toString()
        }
        val intent = when {
            hazard != null -> WeatherInteractionIntent.HAZARD_CHECK
            offsetHours != null || targetHour != null || containsAny(normalized, "diseara", "seara") -> WeatherInteractionIntent.HOURLY_OFFSET
            containsAny(normalized, "maine", "miine", "poimaine", "tomorrow") -> WeatherInteractionIntent.DAILY_FORECAST
            else -> WeatherInteractionIntent.CURRENT
        }
        val resolvedHour = targetHour ?: if (containsAny(normalized, "diseara", "seara")) 18 else null
        return WeatherInteraction(
            intent = intent,
            offsetHours = offsetHours,
            targetDate = targetDate,
            targetHour = resolvedHour,
            hazard = hazard
        )
    }

    private fun detectWeatherHazard(normalized: String): WeatherHazard? =
        when {
            containsAny(normalized, "fulger", "fulgere", "traznet", "furtuna", "thunder", "lightning", "storm") ->
                WeatherHazard.THUNDERSTORM
            containsAny(normalized, "ploua", "ploaie", "precipitatii", "rain") -> WeatherHazard.RAIN
            containsAny(normalized, "frig", "rece", "inghet", "cold", "freez") -> WeatherHazard.COLD
            containsAny(normalized, "cald", "canicula", "heat", "hot") -> WeatherHazard.HEAT
            containsAny(normalized, "ninsoare", "zapada", "snow", "ice", "gheata") -> WeatherHazard.SNOW
            containsAny(normalized, "ceata", "vizibilitate", "fog", "visibility") -> WeatherHazard.FOG
            containsAny(normalized, "vant", "vijelie", "wind") -> WeatherHazard.WIND
            else -> null
        }

    private fun cachedWeatherResult(
        context: DeviceContextSnapshot,
        request: AssistantWeatherRequest
    ): AssistantWeatherResult {
        val cached = context.trail?.weatherForecast?.takeIf { it.isNotBlank() }
        val isRomanian = request.preferredLanguage == "ro"
        return AssistantWeatherResult(
            available = cached != null,
            isLive = false,
            locationLabel = request.locationLabel,
            summary = cached?.let {
                if (isRomanian) "Nu am putut face apel live; ultima prognoza salvata este: $it." else
                    "I could not make a live call; the last saved forecast is: $it."
            } ?: if (isRomanian) {
                "Nu am date meteo live disponibile acum."
            } else {
                "I do not have live weather data available now."
            },
            hazard = request.hazard,
            errorMessage = "weather_handler_missing"
        )
    }

    private fun weatherSummary(
        result: AssistantWeatherResult,
        intent: WeatherInteraction,
        isRomanian: Boolean
    ): String {
        if (!result.available) {
            return result.summary
        }
        val place = result.locationLabel?.let { if (isRomanian) " pentru $it" else " for $it" }.orEmpty()
        val time = result.hourly?.time?.let { if (isRomanian) " la $it" else " at $it" }.orEmpty()
        val prefix = when (intent.intent) {
            WeatherInteractionIntent.CURRENT -> if (isRomanian) "Vremea acum$place" else "Weather now$place"
            WeatherInteractionIntent.HOURLY_OFFSET -> if (isRomanian) "Prognoza$place$time" else "Forecast$place$time"
            WeatherInteractionIntent.DAILY_FORECAST -> if (isRomanian) "Prognoza pe zi$place" else "Daily forecast$place"
            WeatherInteractionIntent.HAZARD_CHECK -> if (isRomanian) "Verificare risc meteo$place$time" else "Weather hazard check$place$time"
        }
        return "$prefix: ${result.summary}."
    }

    private fun hourlyDetails(hourly: com.scouty.app.assistant.model.AssistantHourlyWeather, isRomanian: Boolean): String {
        val details = mutableListOf<String>()
        hourly.temperatureC?.let { details += String.format(Locale.getDefault(), "%.1f°C", it) }
        hourly.precipitationProbability?.let {
            details += if (isRomanian) "probabilitate precipitatii $it%" else "precipitation probability $it%"
        }
        hourly.precipitationMm?.let {
            details += String.format(Locale.getDefault(), if (isRomanian) "precipitatii %.1f mm" else "precipitation %.1f mm", it)
        }
        hourly.visibilityKm?.let {
            details += String.format(Locale.getDefault(), if (isRomanian) "vizibilitate %.1f km" else "visibility %.1f km", it)
        }
        hourly.windSpeedKmh?.let {
            details += String.format(Locale.getDefault(), if (isRomanian) "vant %.0f km/h" else "wind %.0f km/h", it)
        }
        return details.ifEmpty { listOf(if (isRomanian) "Nu sunt disponibile detalii orare suplimentare." else "No extra hourly details available.") }
            .joinToString("; ")
    }

    private fun hazardAdvice(hazard: WeatherHazard, result: AssistantWeatherResult, isRomanian: Boolean): String {
        val hourly = result.hourly
        val risk = when (hazard) {
            WeatherHazard.THUNDERSTORM -> hourly?.pictocode == 14
            WeatherHazard.RAIN -> (hourly?.precipitationProbability ?: 0) >= 50 || (hourly?.precipitationMm ?: 0.0) > 0.2
            WeatherHazard.COLD -> (hourly?.temperatureC ?: 99.0) <= 5.0
            WeatherHazard.HEAT -> (hourly?.temperatureC ?: -99.0) >= 28.0
            WeatherHazard.SNOW -> (hourly?.temperatureC ?: 99.0) <= 2.0 && ((hourly?.precipitationProbability ?: 0) >= 40)
            WeatherHazard.FOG -> hourly?.pictocode == 5 || (hourly?.visibilityKm ?: 99.0) <= 5.0
            WeatherHazard.WIND -> (hourly?.windSpeedKmh ?: 0.0) >= 35.0
        }
        return if (risk) {
            when (hazard) {
                WeatherHazard.THUNDERSTORM -> if (isRomanian) "Exista semnal de furtuna: evita crestele, zonele expuse si copacii izolati." else "There is a storm signal: avoid ridges, exposed areas, and isolated trees."
                WeatherHazard.RAIN -> if (isRomanian) "Ia protectie de ploaie si trateaza poteca drept alunecoasa." else "Carry rain protection and treat the trail as slippery."
                WeatherHazard.COLD -> if (isRomanian) "Pregateste strat termic si protectie de vant; pauzele lungi pot raci rapid corpul." else "Prepare insulation and wind protection; long stops can cool you quickly."
                WeatherHazard.HEAT -> if (isRomanian) "Mareste rezerva de apa, redu ritmul si evita orele expuse." else "Increase water, reduce pace, and avoid exposed hours."
                WeatherHazard.SNOW -> if (isRomanian) "Ia in calcul gheata/zapada si verifica daca traseul ramane potrivit." else "Plan for snow/ice and check that the route remains suitable."
                WeatherHazard.FOG -> if (isRomanian) "Navigatia poate deveni dificila; tine marcajul si harta offline la indemana." else "Navigation may get difficult; keep markers and the offline map close."
                WeatherHazard.WIND -> if (isRomanian) "Evita muchiile expuse si ajusteaza straturile pentru vant." else "Avoid exposed ridges and adjust layers for wind."
            }
        } else if (result.available) {
            if (isRomanian) "Nu vad un semnal puternic pentru acest risc in datele disponibile, dar verifica din nou daca vremea se schimba." else
                "I do not see a strong signal for that risk in the available data, but check again if conditions change."
        } else {
            result.summary
        }
    }

    private fun ambiguousGearResponse(
        items: List<GearContextItem>,
        queryAnalysis: QueryAnalysis,
        conversationState: AssistantConversationState,
        packStatus: KnowledgePackStatus
    ): AssistantResponse {
        val isRomanian = queryAnalysis.preferredLanguage == "ro"
        val names = items.take(3).map { it.name }
        return structuredInteractionResponse(
            summary = if (isRomanian) "Am gasit mai multe articole posibile. Pe care il modific?" else
                "I found multiple possible items. Which one should I change?",
            sections = emptyList(),
            followUps = names,
            reasoningType = ReasoningType.GEAR_ADVICE,
            queryAnalysis = queryAnalysis,
            conversationState = conversationState.copy(lastTrailContextIntent = "GEAR_CLARIFY"),
            packStatus = packStatus
        )
    }

    private fun structuredInteractionResponse(
        summary: String,
        sections: List<StructuredResponseSection>,
        followUps: List<String>,
        reasoningType: ReasoningType,
        queryAnalysis: QueryAnalysis,
        conversationState: AssistantConversationState,
        packStatus: KnowledgePackStatus,
        actions: List<AssistantAction> = emptyList(),
        safetyOutcome: SafetyOutcome = SafetyOutcome.NORMAL
    ): AssistantResponse {
        val output = StructuredAssistantOutput(
            summary = summary,
            sections = sections,
            generationMode = GenerationMode.FALLBACK_STRUCTURED,
            reasoningType = reasoningType,
            followUpQuestions = followUps,
            knowledgePackVersion = packStatus.packVersion
        )
        return AssistantResponse(
            answerText = renderInteractionOutput(output),
            structuredOutput = output,
            citations = emptyList(),
            safetyOutcome = safetyOutcome,
            generationMode = output.generationMode,
            reasoningType = reasoningType,
            conversationState = conversationState,
            knowledgePackVersion = output.knowledgePackVersion,
            usedFallback = true,
            actions = actions
        )
    }

    private fun renderInteractionOutput(output: StructuredAssistantOutput): String =
        buildString {
            append(output.summary.trim())
            output.sections
                .filter { it.body.isNotBlank() }
                .take(2)
                .forEach { section ->
                    appendLine()
                    appendLine()
                    append(section.body.trim())
                }
        }.trim()

    private fun matchGearItem(objectText: String, items: List<GearContextItem>): GearItemMatch {
        if (objectText.isBlank() || items.isEmpty()) {
            return GearItemMatch.None
        }
        val normalizedObject = normalize(objectText)
        val objectTokens = normalizedObject.split(" ").filter { it.isNotBlank() }.map(::stemGearToken).toSet()
        val scored = items.mapNotNull { item ->
            val score = gearMatchScore(normalizedObject, objectTokens, item)
            if (score >= 0.45) item to score else null
        }.sortedByDescending { it.second }
        val top = scored.firstOrNull() ?: return GearItemMatch.None
        val tied = scored.filter { top.second - it.second <= 0.08 }
        return if (tied.size > 1) {
            GearItemMatch.Ambiguous(tied.map { it.first })
        } else {
            GearItemMatch.Single(top.first)
        }
    }

    private fun gearMatchScore(
        normalizedObject: String,
        objectTokens: Set<String>,
        item: GearContextItem
    ): Double {
        val variants = gearAliasesFor(item)
        if (variants.any { it == normalizedObject }) {
            return 1.0
        }
        if (variants.any { it.contains(normalizedObject) || normalizedObject.contains(it) }) {
            return 0.85
        }
        val variantTokens = variants.flatMap { variant ->
            variant.split(" ").filter { it.isNotBlank() }.map(::stemGearToken)
        }.toSet()
        val overlap = objectTokens.intersect(variantTokens).size
        if (overlap == 0) {
            return 0.0
        }
        return overlap.toDouble() / objectTokens.size.coerceAtLeast(1).toDouble()
    }

    private fun gearTextMatches(normalized: String, item: GearContextItem): Boolean =
        gearAliasesFor(item).any { alias -> alias in normalized }

    private fun gearAliasesFor(item: GearContextItem): Set<String> {
        val base = setOf(item.id, item.name).map(::normalize).filter { it.isNotBlank() }.toMutableSet()
        val text = base.joinToString(" ")
        GearAliasGroups.forEach { aliases ->
            if (aliases.any { alias -> alias in text }) {
                base += aliases
            }
        }
        return base
    }

    private fun extractGearObject(normalized: String): String {
        var text = normalized
        listOf(
            "adauga", "adaug", "pune in lista", "pune pe lista", "trece pe lista", "bifeaza",
            "pune", "trece", "debifeaza", "am pus", "am luat", "am impachetat", "scoate", "sterge", "elimina",
            "fa", "schimba", "marcheaza", "din lista", "in lista", "pe lista", "ca", "la",
            "obligatorie", "obligatoriu", "recomandata", "recomandat", "optional", "conditionat"
        ).forEach { phrase ->
            text = text.replace(phrase, " ")
        }
        val stopwords = setOf("te", "rog", "imi", "mi", "si", "lista", "echipament", "gear", "rucsac", "un", "o", "ul")
        return text.split(" ")
            .map { it.trim() }
            .filter { it.isNotBlank() && it !in stopwords }
            .joinToString(" ")
            .trim()
    }

    private fun detectNecessity(normalized: String): String =
        when {
            containsAny(normalized, "obligator") -> "MANDATORY"
            containsAny(normalized, "optional", "conditionat") -> "CONDITIONAL"
            else -> "RECOMMENDED"
        }

    private fun necessityLabel(value: String, isRomanian: Boolean): String =
        when (value) {
            "MANDATORY" -> if (isRomanian) "obligatoriu" else "mandatory"
            "CONDITIONAL" -> if (isRomanian) "optional" else "conditional"
            else -> if (isRomanian) "recomandat" else "recommended"
        }

    private fun customGearId(name: String): String {
        val slug = normalize(name).replace(" ", "_").ifBlank { "item" }.take(32)
        val suffix = abs(name.lowercase(Locale.ROOT).hashCode()).toString(36)
        return "custom_${slug}_$suffix"
    }

    private fun displayGearName(value: String): String =
        normalize(value)
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { token -> token.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } }
            .ifBlank { "Articol" }

    private fun stemGearToken(raw: String): String {
        var token = raw
        token = when {
            token.endsWith("ului") && token.length > 6 -> token.dropLast(5)
            token.endsWith("ilor") && token.length > 6 -> token.dropLast(4)
            token.endsWith("elor") && token.length > 6 -> token.dropLast(4)
            token.endsWith("lor") && token.length > 5 -> token.dropLast(3)
            token.endsWith("ele") && token.length > 5 -> token.dropLast(2)
            token.endsWith("le") && token.length > 4 -> token.dropLast(2)
            token.endsWith("ul") && token.length > 4 -> token.dropLast(2)
            else -> token
        }
        return token
    }

    private fun containsAny(value: String, vararg needles: String): Boolean =
        needles.any { it in value }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    private companion object {
        private val GearAliasGroups = listOf(
            setOf("frontala", "lanterna frontala", "headlamp"),
            setOf("apa", "water", "sticla apa", "bidon"),
            setOf("pelerina", "poncho", "geaca ploaie", "jacheta ploaie", "rain jacket", "waterproof"),
            setOf("bete", "betele", "bete trekking", "trekking poles"),
            setOf("spray", "spray urs", "bear spray"),
            setOf("manusi", "gloves"),
            setOf("harta", "map"),
            setOf("telefon", "phone"),
            setOf("trusa", "first aid", "prim ajutor")
        )
    }
}

private enum class GearInteractionIntent {
    MARK_PACKED,
    MARK_UNPACKED,
    ADD_ITEM,
    REMOVE_ITEM,
    UPDATE_ITEM,
    SHOW_LIST,
    MISSING_ITEMS,
    WEATHER_BASED_ADVICE
}

private data class WeatherInteraction(
    val intent: WeatherInteractionIntent,
    val offsetHours: Int? = null,
    val targetDate: String? = null,
    val targetHour: Int? = null,
    val hazard: WeatherHazard? = null
)

private sealed class GearItemMatch {
    data class Single(val item: GearContextItem) : GearItemMatch()
    data class Ambiguous(val items: List<GearContextItem>) : GearItemMatch()
    object None : GearItemMatch()
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
    private val featureFlags: RuntimeFeatureFlags = RuntimeFeatureFlags(),
    private val crossEncoderReranker: CrossEncoderReranker? =
        context?.takeIf { featureFlags.useCrossEncoderReranker }?.let(::CrossEncoderReranker),
    private val campfireConversationEngine: CampfireConversationEngine = CampfireConversationEngine(
        knowledgeStore = knowledgeStore,
        confidencePolicy = retrievalConfidencePolicy,
        crossEncoderReranker = crossEncoderReranker
    ),
    private val promptBuilder: PromptBuilder = PromptBuilder(),
    private val modelManager: ModelManager = context?.let { ModelManager(it, featureFlags) }
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
    private val medicalSafetyPolicy: MedicalSafetyPolicy = MedicalSafetyPolicy(),
    private val trailContextEngine: TrailContextEngine = TrailContextEngine(),
    private val conversationStore: ConversationStore? =
        context?.takeIf { featureFlags.useConversationMemory }?.let(::ConversationStore),
    private val conversationContextAssembler: ConversationContextAssembler? = conversationStore?.let(::ConversationContextAssembler),
    private val summaryCompactor: SummaryCompactor? = conversationStore?.let {
        SummaryCompactor(
            store = it,
            useLlmSummarizer = featureFlags.useLlmSummarizer
        )
    },
    private val cardParaphraseEngine: CardParaphraseEngine? =
        if (featureFlags.useCardParaphraseExpression) {
            CardParaphraseEngine(ModelManagerCardParaphraseModel(modelManager))
        } else {
            null
        },
    private val useCardParaphraseExpression: Boolean = featureFlags.useCardParaphraseExpression,
    private val toolCallPlanner: GrammarToolCallPlanner? =
        if (featureFlags.useGrammarToolCalling) {
            GrammarToolCallPlanner(ModelManagerToolCallModel(modelManager))
        } else {
            null
        },
    private val toolDispatcher: ToolDispatcher? =
        if (featureFlags.useGrammarToolCalling) {
            ToolDispatcher(
                retrievalEngine = retrievalEngine,
                queryAnalyzer = queryAnalyzer
            )
        } else {
            null
        },
    private val useGrammarToolCalling: Boolean = featureFlags.useGrammarToolCalling,
    private val useLegacyInterpreter: Boolean = featureFlags.useLegacyInterpreter
) {
    private var sessionConversationId: String = "session:${UUID.randomUUID()}"
    private val interactionEngine = DeterministicInteractionEngine()
    private val onlineGenerationPolicy = generationEngine as? OnlineGenerationPolicy

    fun canAttemptOnlineGeneration(context: DeviceContextSnapshot): Boolean =
        onlineGenerationPolicy?.shouldAttemptRemote(context) == true

    suspend fun answer(
        query: String,
        context: DeviceContextSnapshot,
        conversationState: AssistantConversationState = AssistantConversationState(),
        interactionHandler: ChatActionHandler? = null,
        allowLocalModel: Boolean = false
    ): AssistantResponse {
        val memorySession = prepareConversationMemory(
            query = query,
            context = context,
            conversationState = conversationState
        )
        val queryAnalysis = queryAnalyzer.analyze(query, context, conversationState)
        val preprocessing = deterministicPreprocessor.preprocess(query, conversationState, queryAnalysis)
        val packStatus = knowledgePackManager.ensureReady()
        interactionEngine.answer(
            query = query,
            context = context,
            queryAnalysis = queryAnalysis,
            conversationState = conversationState,
            packStatus = packStatus,
            interactionHandler = interactionHandler
        )?.let { interactionResponse ->
            return persistAssistantTurn(memorySession, interactionResponse)
        }

        val response = if (queryAnalysis.trailContextIntent != TrailContextIntent.NONE) {
            val trailResult = trailContextEngine.answer(
                query = query,
                context = context,
                queryAnalysis = queryAnalysis,
                conversationState = conversationState
            )
            if (trailResult != null) {
                answerFromTrailContext(trailResult, queryAnalysis)
            } else if (shouldUseCampfireLane(queryAnalysis)) {
                answerCampfire(
                    query = query,
                    context = context,
                    conversationState = conversationState,
                    queryAnalysis = queryAnalysis,
                    packStatus = packStatus,
                    preprocessing = preprocessing,
                    conversationHistory = memorySession?.history,
                    allowLocalModel = allowLocalModel
                )
            } else {
                answerStandard(
                    query = query,
                    context = context,
                    conversationState = conversationState,
                    initialAnalysis = queryAnalysis,
                    preprocessing = preprocessing,
                    packStatus = packStatus,
                    conversationHistory = memorySession?.history,
                    allowLocalModel = allowLocalModel
                )
            }
        } else if (shouldUseCampfireLane(queryAnalysis)) {
            answerCampfire(
                query = query,
                context = context,
                conversationState = conversationState,
                queryAnalysis = queryAnalysis,
                packStatus = packStatus,
                preprocessing = preprocessing,
                conversationHistory = memorySession?.history,
                allowLocalModel = allowLocalModel
            )
        } else {
            answerStandard(
                query = query,
                context = context,
                conversationState = conversationState,
                initialAnalysis = queryAnalysis,
                preprocessing = preprocessing,
                packStatus = packStatus,
                conversationHistory = memorySession?.history,
                allowLocalModel = allowLocalModel
            )
        }

        return persistAssistantTurn(memorySession, response)
    }

    private fun shouldUseCampfireLane(queryAnalysis: QueryAnalysis): Boolean =
        featureFlags.useCampfireLane &&
            queryAnalysis.knowledgeLane == ConversationLane.FIELD_KNOW_HOW &&
            queryAnalysis.resolvedTopic == "campfire"

    suspend fun resetConversation(context: DeviceContextSnapshot) {
        val store = conversationStore ?: return
        val conversationId = resolveConversationId(context)
        store.deleteConversation(conversationId)
        if (context.trail == null) {
            sessionConversationId = "session:${UUID.randomUUID()}"
        }
    }

    private suspend fun prepareConversationMemory(
        query: String,
        context: DeviceContextSnapshot,
        conversationState: AssistantConversationState
    ): ConversationMemorySession? {
        val store = conversationStore ?: return null
        val assembler = conversationContextAssembler ?: return null
        val conversationId = resolveConversationId(context)
        val trailId = context.trail?.localCode ?: context.trail?.name

        store.ensureConversation(conversationId, trailId)
        store.appendTurn(
            conversationId = conversationId,
            role = ConversationRole.USER,
            text = query,
            chunkId = null
        )
        val compaction = summaryCompactor?.compactIfNeeded(conversationId)
        val history = assembler.assemble(
            conversationId = conversationId,
            currentUserQuery = query,
            conversationState = conversationState,
            deviceContext = context
        )
        AssistantDiagnostics.logConversationMemory(
            conversationId = conversationId,
            historyTokensSent = history.historyTokenEstimate,
            summaryCompactionCount = if (compaction?.compacted == true) 1 else 0,
            prefixKeyStabilityRate = history.prefixKeyStabilityRate,
            recentTurnCount = history.recentTurns.size
        )
        return ConversationMemorySession(conversationId, history)
    }

    private suspend fun persistAssistantTurn(
        memorySession: ConversationMemorySession?,
        response: AssistantResponse
    ): AssistantResponse {
        val store = conversationStore ?: return response
        val session = memorySession ?: return response
        store.appendTurn(
            conversationId = session.conversationId,
            role = ConversationRole.ASSISTANT,
            text = response.answerText,
            chunkId = response.conversationState.lastRetrievedChunkId
        )
        return response
    }

    private fun resolveConversationId(context: DeviceContextSnapshot): String =
        context.trail?.let { trail ->
            "trail:${trail.localCode?.takeIf { it.isNotBlank() } ?: stableConversationKey(trail.name)}"
        } ?: sessionConversationId

    private fun stableConversationKey(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^a-z0-9]+".toRegex(), "-")
            .trim('-')
            .ifBlank { UUID.randomUUID().toString() }

    private fun answerFromTrailContext(
        result: TrailContextResult,
        queryAnalysis: QueryAnalysis
    ): AssistantResponse {
        val structuredOutput = result.structuredOutput
        return AssistantResponse(
            answerText = buildDisplayText(structuredOutput, result.safetyOutcome),
            structuredOutput = structuredOutput,
            citations = emptyList(),
            safetyOutcome = result.safetyOutcome,
            generationMode = structuredOutput.generationMode,
            reasoningType = structuredOutput.reasoningType,
            conversationState = result.conversationState,
            actions = result.actions
        )
    }

    private suspend fun answerCampfire(
        query: String,
        context: DeviceContextSnapshot,
        conversationState: AssistantConversationState,
        queryAnalysis: QueryAnalysis,
        packStatus: KnowledgePackStatus,
        preprocessing: DeterministicPreprocessingResult,
        conversationHistory: ConversationHistory? = null,
        allowLocalModel: Boolean = false
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
        val toolResult = maybeDispatchToolCall(
            query = query,
            context = context,
            analysis = queryAnalysis,
            conversationState = conversationState,
            preprocessing = preprocessing,
            retrievedChunks = initial.retrievedChunks,
            confidence = initial.retrievalConfidence,
            packStatus = packStatus,
            conversationHistory = conversationHistory,
            allowLocalModel = allowLocalModel
        )
        if (toolResult?.isTerminal == true) {
            return buildToolTerminalResponse(
                result = toolResult,
                query = query,
                context = context,
                analysis = queryAnalysis,
                packStatus = packStatus,
                previousState = conversationState
            )
        }
        if (toolResult?.retrievedChunks?.isNotEmpty() == true) {
            return answerToolLookup(
                query = query,
                context = context,
                analysis = toolResult.queryAnalysis ?: queryAnalysis,
                retrievedChunks = toolResult.retrievedChunks,
                confidence = toolResult.retrievalConfidence ?: initial.retrievalConfidence,
                packStatus = packStatus,
                conversationState = conversationState,
                conversationHistory = conversationHistory,
                allowLocalModel = allowLocalModel
            )
        }
        val gateDecision = interpreterGate.decide(
            assessment = initial.retrievalConfidence,
            preprocessing = preprocessing,
            conversationState = conversationState
        )
        val interpretation = if (allowLocalModel && toolResult == null && useLegacyInterpreter && gateDecision.shouldInvoke) {
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

        if (finalCampfire.retrievedChunks.isEmpty()) {
            AssistantDiagnostics.logCampfireFallback(
                query = query,
                reason = "no_field_know_how_chunks"
            )
            return answerStandard(
                query = query,
                context = context,
                conversationState = conversationState,
                initialAnalysis = queryAnalysis.copy(
                    knowledgeLane = ConversationLane.STANDARD,
                    resolvedTopic = null
                ),
                preprocessing = preprocessing,
                packStatus = packStatus,
                conversationHistory = conversationHistory,
                allowLocalModel = allowLocalModel
            )
        }

        val expressionResult = maybeBuildExpressionResult(
            query = query,
            context = context,
            analysis = queryAnalysis,
            retrievedChunks = finalCampfire.retrievedChunks,
            confidence = finalCampfire.retrievalConfidence,
            knowledgePackStatus = packStatus,
            conversationHistory = conversationHistory,
            allowLocalModel = allowLocalModel
        )
        val responseRetrievedChunks = expressionResult?.retrievedChunks ?: finalCampfire.retrievedChunks
        val safetyOutcome = medicalSafetyPolicy.evaluate(query, responseRetrievedChunks, context)
        val wordedOutput = expressionResult?.output ?: applyCampfireWordingIfSafe(
            query = query,
            preferredLanguage = queryAnalysis.preferredLanguage,
            structuredOutput = finalCampfire.structuredOutput,
            retrievedChunks = finalCampfire.retrievedChunks,
            confidence = finalCampfire.retrievalConfidence,
            conversationHistory = conversationHistory,
            allowLocalModel = allowLocalModel
        )
        val structuredOutput = medicalSafetyPolicy.applyFinalGuardrails(
            output = wordedOutput,
            safetyOutcome = safetyOutcome,
            isRomanian = queryAnalysis.preferredLanguage == "ro"
        )
        val modelStatus = modelManager.currentStatus()
        val responseConversationState = expressionResult
            ?.retrievedChunks
            ?.firstOrNull()
            ?.let { chunk ->
                finalCampfire.conversationState.copy(
                    lastRetrievedChunkId = chunk.chunkId,
                    lastRetrievedTopic = chunk.topic,
                    lastRetrievedTitle = chunk.sectionTitle,
                    lastInterpretationConfidence = finalCampfire.retrievalConfidence.score
                )
            }
            ?: finalCampfire.conversationState
        return AssistantResponse(
            answerText = buildDisplayText(structuredOutput, safetyOutcome),
            structuredOutput = structuredOutput,
            citations = buildCitations(queryAnalysis, context, responseRetrievedChunks),
            safetyOutcome = safetyOutcome,
            generationMode = structuredOutput.generationMode,
            reasoningType = structuredOutput.reasoningType,
            conversationState = responseConversationState,
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
        packStatus: KnowledgePackStatus,
        conversationHistory: ConversationHistory? = null,
        allowLocalModel: Boolean = false
    ): AssistantResponse {
        val answerStartedAtNanos = System.nanoTime()
        val modelStatus = modelManager.refreshStatus()
        val generationMode = generationModeForAttempt(modelStatus, context, allowLocalModel)
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
        val toolResult = maybeDispatchToolCall(
            query = query,
            context = context,
            analysis = initialAnalysis,
            conversationState = conversationState,
            preprocessing = preprocessing,
            retrievedChunks = initialRetrieved,
            confidence = initialAssessment,
            packStatus = packStatus,
            conversationHistory = conversationHistory,
            allowLocalModel = allowLocalModel
        )
        if (toolResult?.isTerminal == true) {
            return buildToolTerminalResponse(
                result = toolResult,
                query = query,
                context = context,
                analysis = initialAnalysis,
                packStatus = packStatus,
                previousState = conversationState
            )
        }
        val gateDecision = interpreterGate.decide(
            assessment = initialAssessment,
            preprocessing = preprocessing,
            conversationState = conversationState
        )

        var finalAnalysis = toolResult?.queryAnalysis ?: initialAnalysis
        var finalRetrieved = toolResult?.retrievedChunks?.takeIf { it.isNotEmpty() } ?: initialRetrieved
        var finalAssessment = toolResult?.retrievalConfidence ?: initialAssessment
        var acceptedInterpretation: ValidatedInterpretation? = null
        if (allowLocalModel && toolResult == null && useLegacyInterpreter && gateDecision.shouldInvoke) {
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
                    finalAssessment = candidateAssessment
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
        val expressionResult = if (shouldUseOnlineGeneration(context) || !allowLocalModel) {
            null
        } else {
            maybeBuildExpressionResult(
                query = query,
                context = context,
                analysis = finalAnalysis,
                retrievedChunks = finalRetrieved,
                confidence = finalAssessment,
                knowledgePackStatus = packStatus,
                conversationHistory = conversationHistory,
                allowLocalModel = allowLocalModel
            )
        }
        val structuredOutput = medicalSafetyPolicy.applyFinalGuardrails(
            output = expressionResult?.output
                ?: generationEngine.generate(
                    GenerationInput(
                        query = query,
                        prompt = prompt,
                        queryAnalysis = finalAnalysis,
                        retrievedChunks = finalRetrieved,
                        context = context,
                        safetyOutcome = safetyOutcome,
                        generationMode = generationMode,
                        modelStatus = modelStatus,
                        knowledgePackStatus = packStatus,
                        conversationHistory = conversationHistory,
                        allowLocalModel = allowLocalModel
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
            retrievedChunks = finalRetrieved,
            totalElapsedMs = elapsedMsSince(answerStartedAtNanos)
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
            modelVersion = structuredOutput.modelVersion ?: finalModelStatus.modelVersion.takeIf {
                finalModelStatus.availableOnDisk || finalModelStatus.state != ModelRuntimeState.MISSING
            },
            modelRuntimeState = finalModelStatus.state,
            modelStatusDetails = finalModelStatus.details,
            knowledgePackVersion = structuredOutput.knowledgePackVersion,
            usedFallback = structuredOutput.generationMode == GenerationMode.FALLBACK_STRUCTURED
        )
    }

    private suspend fun maybeBuildExpressionResult(
        query: String,
        context: DeviceContextSnapshot,
        analysis: QueryAnalysis,
        retrievedChunks: List<RetrievedChunk>,
        confidence: RetrievalConfidenceAssessment,
        knowledgePackStatus: KnowledgePackStatus,
        conversationHistory: ConversationHistory?,
        allowLocalModel: Boolean
    ): ExpressionLayerResult? {
        val engine = cardParaphraseEngine ?: return null
        if (!allowLocalModel) {
            return null
        }
        if (!useCardParaphraseExpression) {
            AssistantDiagnostics.logExpressionLayer(
                chunkId = "",
                invocationCount = 0,
                fallbackCount = 0,
                tokenLatencyMs = 0,
                reason = "feature_disabled"
            )
            return null
        }
        if (retrievedChunks.isEmpty()) {
            AssistantDiagnostics.logExpressionLayer(
                chunkId = "",
                invocationCount = 0,
                fallbackCount = 0,
                tokenLatencyMs = 0,
                reason = "no_primary_chunk"
            )
            return null
        }
        val primary = retrievedChunks.firstOrNull { engine.isEligibleForParaphrase(it) }
            ?: retrievedChunks.first()
        val paraphrased = engine.maybeParaphrase(
            CardParaphraseRequest(
                featureEnabled = useCardParaphraseExpression,
                chunk = primary,
                userQuery = query,
                confidenceTier = confidence.tier,
                deviceContext = context,
                conversationHistory = conversationHistory,
                preferredLanguage = analysis.preferredLanguage
            )
        ) ?: return null
        val modelStatus = modelManager.currentStatus()
        return ExpressionLayerResult(
            output = StructuredAssistantOutput(
                summary = paraphrased.text,
                sections = emptyList(),
                generationMode = GenerationMode.LOCAL_LLM,
                reasoningType = analysis.reasoningType,
                resolvedTopic = primary.topic,
                resolvedFamily = primary.cardFamily,
                modelVersion = modelStatus.modelVersion.takeIf {
                    modelStatus.availableOnDisk || modelStatus.state != ModelRuntimeState.MISSING
                },
                knowledgePackVersion = knowledgePackStatus.packVersion ?: primary.packVersion
            ),
            retrievedChunks = retrievedChunks
        )
    }

    private suspend fun maybeDispatchToolCall(
        query: String,
        context: DeviceContextSnapshot,
        analysis: QueryAnalysis,
        conversationState: AssistantConversationState,
        preprocessing: DeterministicPreprocessingResult,
        retrievedChunks: List<RetrievedChunk>,
        confidence: RetrievalConfidenceAssessment,
        packStatus: KnowledgePackStatus,
        conversationHistory: ConversationHistory?,
        allowLocalModel: Boolean
    ): ToolDispatchResult? {
        if (!allowLocalModel) {
            return null
        }
        if (!useGrammarToolCalling) {
            return null
        }
        val planner = toolCallPlanner ?: return null
        val dispatcher = toolDispatcher ?: return null
        if (!shouldInvokeToolCalling(confidence, preprocessing, conversationState)) {
            return null
        }
        val startedAt = System.nanoTime()
        val call = runCatching {
            planner.plan(
                com.scouty.app.assistant.domain.tools.ToolPlanningRequest(
                    query = query,
                    queryAnalysis = analysis,
                    conversationState = conversationState,
                    retrievalConfidence = confidence,
                    preprocessingSlots = preprocessing.obviousSlotUpdates,
                    retrievedChunks = retrievedChunks,
                    deviceContext = context,
                    conversationHistory = conversationHistory
                )
            )
        }.getOrElse { error ->
            AssistantDiagnostics.logToolCalling(
                query = query,
                invoked = true,
                toolName = null,
                elapsedMs = elapsedMsSince(startedAt),
                status = "planner_error",
                error = error.message ?: error::class.java.simpleName
            )
            return null
        } ?: run {
            AssistantDiagnostics.logToolCalling(
                query = query,
                invoked = true,
                toolName = null,
                elapsedMs = elapsedMsSince(startedAt),
                status = "no_valid_tool"
            )
            return null
        }

        val result = runCatching {
            dispatcher.dispatch(
                call = call,
                request = ToolDispatchRequest(
                    query = query,
                    context = context,
                    queryAnalysis = analysis,
                    conversationState = conversationState,
                    preprocessing = preprocessing,
                    retrievedChunks = retrievedChunks,
                    retrievalConfidence = confidence,
                    knowledgePackStatus = packStatus,
                    conversationHistory = conversationHistory
                )
            )
        }.getOrElse { error ->
            AssistantDiagnostics.logToolCalling(
                query = query,
                invoked = true,
                toolName = call.tool.wireName,
                elapsedMs = elapsedMsSince(startedAt),
                status = "dispatcher_error",
                error = error.message ?: error::class.java.simpleName
            )
            return null
        }
        AssistantDiagnostics.logToolCalling(
            query = query,
            invoked = true,
            toolName = call.tool.wireName,
            elapsedMs = elapsedMsSince(startedAt),
            status = if (result.isTerminal) "terminal" else "continue"
        )
        return result
    }

    private fun shouldInvokeToolCalling(
        confidence: RetrievalConfidenceAssessment,
        preprocessing: DeterministicPreprocessingResult,
        conversationState: AssistantConversationState
    ): Boolean {
        val unresolvedOpenQuestion = conversationState.openQuestion != null &&
            conversationState.openQuestion.targetSlot !in preprocessing.obviousSlotUpdates
        return confidence.score < ToolCallingConfidenceThreshold || unresolvedOpenQuestion
    }

    private fun buildToolTerminalResponse(
        result: ToolDispatchResult,
        query: String,
        context: DeviceContextSnapshot,
        analysis: QueryAnalysis,
        packStatus: KnowledgePackStatus,
        previousState: AssistantConversationState
    ): AssistantResponse {
        val output = result.output ?: error("Tool terminal response requires output")
        val safetyOutcome = medicalSafetyPolicy.evaluate(query, result.retrievedChunks, context)
        val guardedOutput = medicalSafetyPolicy.applyFinalGuardrails(
            output = output,
            safetyOutcome = safetyOutcome,
            isRomanian = analysis.preferredLanguage == "ro"
        )
        val modelStatus = modelManager.currentStatus()
        return AssistantResponse(
            answerText = buildDisplayText(guardedOutput, safetyOutcome),
            structuredOutput = guardedOutput,
            citations = buildCitations(analysis, context, result.retrievedChunks),
            safetyOutcome = safetyOutcome,
            generationMode = guardedOutput.generationMode,
            reasoningType = guardedOutput.reasoningType,
            conversationState = result.conversationState ?: previousState,
            modelVersion = modelStatus.modelVersion.takeIf {
                modelStatus.availableOnDisk || modelStatus.state != ModelRuntimeState.MISSING
            },
            modelRuntimeState = modelStatus.state,
            modelStatusDetails = modelStatus.details,
            knowledgePackVersion = guardedOutput.knowledgePackVersion ?: packStatus.packVersion,
            usedFallback = guardedOutput.generationMode == GenerationMode.FALLBACK_STRUCTURED,
            actions = result.actions
        )
    }

    private suspend fun answerToolLookup(
        query: String,
        context: DeviceContextSnapshot,
        analysis: QueryAnalysis,
        retrievedChunks: List<RetrievedChunk>,
        confidence: RetrievalConfidenceAssessment,
        packStatus: KnowledgePackStatus,
        conversationState: AssistantConversationState,
        conversationHistory: ConversationHistory?,
        allowLocalModel: Boolean
    ): AssistantResponse {
        val modelStatus = modelManager.refreshStatus()
        val generationMode = generationModeForAttempt(modelStatus, context, allowLocalModel)
        val prompt = promptBuilder.build(
            query = query,
            context = context,
            retrievedChunks = retrievedChunks,
            queryAnalysis = analysis
        )
        val safetyOutcome = medicalSafetyPolicy.evaluate(query, retrievedChunks, context)
        val expressionResult = if (shouldUseOnlineGeneration(context) || !allowLocalModel) {
            null
        } else {
            maybeBuildExpressionResult(
                query = query,
                context = context,
                analysis = analysis,
                retrievedChunks = retrievedChunks,
                confidence = confidence,
                knowledgePackStatus = packStatus,
                conversationHistory = conversationHistory,
                allowLocalModel = allowLocalModel
            )
        }
        val output = medicalSafetyPolicy.applyFinalGuardrails(
            output = expressionResult?.output ?: generationEngine.generate(
                GenerationInput(
                    query = query,
                    prompt = prompt,
                    queryAnalysis = analysis,
                    retrievedChunks = retrievedChunks,
                    context = context,
                    safetyOutcome = safetyOutcome,
                    generationMode = generationMode,
                    modelStatus = modelStatus,
                    knowledgePackStatus = packStatus,
                    conversationHistory = conversationHistory,
                    allowLocalModel = allowLocalModel
                )
            ),
            safetyOutcome = safetyOutcome,
            isRomanian = analysis.preferredLanguage == "ro"
        )
        val finalModelStatus = modelManager.currentStatus()
        return AssistantResponse(
            answerText = buildDisplayText(output, safetyOutcome),
            structuredOutput = output,
            citations = buildCitations(analysis, context, retrievedChunks),
            safetyOutcome = safetyOutcome,
            generationMode = output.generationMode,
            reasoningType = output.reasoningType,
            conversationState = buildStandardConversationState(
                previousState = conversationState,
                originalQuery = query,
                analysis = analysis,
                retrieved = retrievedChunks,
                acceptedInterpretation = null
            ),
            modelVersion = output.modelVersion ?: finalModelStatus.modelVersion.takeIf {
                finalModelStatus.availableOnDisk || finalModelStatus.state != ModelRuntimeState.MISSING
            },
            modelRuntimeState = finalModelStatus.state,
            modelStatusDetails = finalModelStatus.details,
            knowledgePackVersion = output.knowledgePackVersion,
            usedFallback = output.generationMode == GenerationMode.FALLBACK_STRUCTURED
        )
    }

    private fun elapsedMsSince(startedAtNanos: Long): Long =
        (System.nanoTime() - startedAtNanos) / 1_000_000

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
        confidence: RetrievalConfidenceAssessment,
        conversationHistory: ConversationHistory? = null,
        allowLocalModel: Boolean = false
    ): StructuredAssistantOutput {
        if (!allowLocalModel) {
            return structuredOutput
        }
        if (confidence.tier == RetrievalConfidenceTier.LOW || retrievedChunks.isEmpty()) {
            return structuredOutput
        }
        val wording = groundedWordingEngine.rephrase(
            GroundedWordingRequest(
                query = query,
                preferredLanguage = preferredLanguage,
                deterministicOutput = structuredOutput,
                retrievedChunks = retrievedChunks,
                conversationHistory = conversationHistory
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
        if (output.generationMode == GenerationMode.GEMINI_API) {
            val rawAnswer = output.summary.trim()
            val emergencyLead = if (safetyOutcome == SafetyOutcome.EMERGENCY_ESCALATION) {
                output.sections.firstOrNull { it.style == ResponseSectionStyle.IMPORTANT }
                    ?.body
                    ?.trim()
                    ?.takeIf { it.isNotBlank() && !rawAnswer.contains(it) }
            } else {
                null
            }
            return listOfNotNull(emergencyLead, rawAnswer.takeIf { it.isNotBlank() })
                .joinToString("\n\n")
                .trim()
        }

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

    private fun shouldUseOnlineGeneration(context: DeviceContextSnapshot): Boolean =
        canAttemptOnlineGeneration(context)

    private fun generationModeForAttempt(
        modelStatus: ModelStatus,
        context: DeviceContextSnapshot,
        allowLocalModel: Boolean
    ): GenerationMode =
        when {
            shouldUseOnlineGeneration(context) -> GenerationMode.GEMINI_API
            allowLocalModel && modelStatus.state == ModelRuntimeState.LOADED -> GenerationMode.LOCAL_LLM
            allowLocalModel && modelStatus.availableOnDisk && modelStatus.state in setOf(
                ModelRuntimeState.UNLOADED,
                ModelRuntimeState.PREPARING
            ) -> GenerationMode.LOCAL_LLM
            else -> GenerationMode.FALLBACK_STRUCTURED
        }

    private companion object {
        private const val ToolCallingConfidenceThreshold = 0.55

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
