package com.scouty.app.assistant.domain

import com.scouty.app.assistant.model.AssistantAction
import com.scouty.app.assistant.model.AssistantConversationState
import com.scouty.app.assistant.model.AssistantQuickReplyUiModel
import com.scouty.app.assistant.model.DailyForecastEntry
import com.scouty.app.assistant.model.DeviceContextSnapshot
import com.scouty.app.assistant.model.GearContextItem
import com.scouty.app.assistant.model.GenerationMode
import com.scouty.app.assistant.model.PendingGearAction
import com.scouty.app.assistant.model.QueryAnalysis
import com.scouty.app.assistant.model.ReasoningType
import com.scouty.app.assistant.model.ResponseSectionStyle
import com.scouty.app.assistant.model.SafetyOutcome
import com.scouty.app.assistant.model.StructuredAssistantOutput
import com.scouty.app.assistant.model.StructuredResponseSection
import com.scouty.app.assistant.model.TrailContextIntent
import com.scouty.app.assistant.model.TrailContextSnapshot
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

data class TrailContextResult(
    val structuredOutput: StructuredAssistantOutput,
    val conversationState: AssistantConversationState,
    val actions: List<AssistantAction> = emptyList(),
    val safetyOutcome: SafetyOutcome = SafetyOutcome.NORMAL
)

class TrailContextEngine {

    fun answer(
        query: String,
        context: DeviceContextSnapshot,
        queryAnalysis: QueryAnalysis,
        conversationState: AssistantConversationState
    ): TrailContextResult? {
        val trail = context.trail ?: return null
        val intent = queryAnalysis.trailContextIntent
        if (intent == TrailContextIntent.NONE) return null

        val isRomanian = queryAnalysis.preferredLanguage == "ro"

        return when (intent) {
            TrailContextIntent.TRAIL_INFO -> answerTrailInfo(query, trail, context, queryAnalysis, isRomanian)
            TrailContextIntent.WEATHER_FORECAST -> answerWeather(trail, queryAnalysis, context, isRomanian)
            TrailContextIntent.CAPABILITY_CHECK -> answerCapability(query, trail, context, isRomanian)
            TrailContextIntent.NEEDS_CHECK -> answerNeeds(trail, context, conversationState, isRomanian)
            TrailContextIntent.DURATION_ESTIMATE -> answerDuration(query, trail, isRomanian)
            TrailContextIntent.GEAR_REVIEW -> answerGearReview(trail, context, conversationState, isRomanian)
            TrailContextIntent.GEAR_UPDATE_CONFIRM -> processGearConfirmation(query, conversationState, isRomanian)
            TrailContextIntent.NONE -> null
        }
    }

    // ── Trail Info ──────────────────────────────────────────────────────

    private fun answerTrailInfo(
        query: String,
        trail: TrailContextSnapshot,
        context: DeviceContextSnapshot,
        queryAnalysis: QueryAnalysis,
        isRomanian: Boolean
    ): TrailContextResult {
        val normalized = normalize(query)
        val sections = mutableListOf<StructuredResponseSection>()
        val followUps = mutableListOf<String>()

        val specificAspect = detectTrailInfoAspect(normalized, queryAnalysis.tokens)

        val summary = when (specificAspect) {
            TrailInfoAspect.DIFFICULTY -> buildDifficultySummary(trail, isRomanian)
            TrailInfoAspect.DURATION -> buildDurationSummary(trail, isRomanian)
            TrailInfoAspect.MARKERS -> buildMarkersSummary(trail, isRomanian)
            TrailInfoAspect.ENDPOINTS -> buildEndpointsSummary(trail, isRomanian)
            TrailInfoAspect.DISTANCE -> buildDistanceSummary(trail, isRomanian)
            TrailInfoAspect.ELEVATION -> buildElevationSummary(trail, isRomanian)
            TrailInfoAspect.SUNSET -> buildSunsetSummary(trail, isRomanian)
            TrailInfoAspect.OVERVIEW -> buildOverviewSummary(trail, isRomanian)
        }

        if (specificAspect == TrailInfoAspect.OVERVIEW) {
            sections += buildFullTrailSection(trail, isRomanian)
            trail.descriptionRo?.takeIf { it.isNotBlank() }?.let { desc ->
                sections += StructuredResponseSection(
                    title = if (isRomanian) "Descriere" else "Description",
                    body = desc,
                    style = ResponseSectionStyle.CONTEXT
                )
            }
        }

        buildWeatherContextSection(trail, context, isRomanian)?.let { sections += it }

        followUps += trailInfoFollowUps(specificAspect, isRomanian)

        return buildResult(
            summary = summary,
            sections = sections,
            followUps = followUps,
            reasoningType = ReasoningType.ROUTE_CONTEXT,
            conversationState = AssistantConversationState(
                lastUserMessage = query,
                lastTrailContextIntent = "TRAIL_INFO"
            )
        )
    }

    private fun buildDifficultySummary(trail: TrailContextSnapshot, isRomanian: Boolean): String {
        val diffLabel = difficultyLabel(trail.difficulty, isRomanian)
        val trailName = trail.name
        return if (isRomanian) {
            buildString {
                append("Traseul $trailName are dificultatea $diffLabel.")
                trail.elevationGain?.takeIf { it > 0 }?.let {
                    append(" Diferenta de nivel este +$it m")
                }
                trail.distanceKm?.takeIf { it > 0 }?.let {
                    append(" pe o distanta de ${formatKm(it)} km.")
                }
                trail.estimatedDuration?.takeIf { it.isNotBlank() }?.let {
                    append(" Durata estimata: $it.")
                }
            }
        } else {
            buildString {
                append("Trail $trailName has $diffLabel difficulty.")
                trail.elevationGain?.takeIf { it > 0 }?.let {
                    append(" Elevation gain: +$it m")
                }
                trail.distanceKm?.takeIf { it > 0 }?.let {
                    append(" over ${formatKm(it)} km.")
                }
                trail.estimatedDuration?.takeIf { it.isNotBlank() }?.let {
                    append(" Estimated duration: $it.")
                }
            }
        }
    }

    private fun buildDurationSummary(trail: TrailContextSnapshot, isRomanian: Boolean): String {
        val duration = trail.estimatedDuration ?: (if (isRomanian) "nedeterminata" else "unknown")
        return if (isRomanian) {
            buildString {
                append("Durata estimata a traseului ${trail.name} este $duration.")
                trail.distanceKm?.takeIf { it > 0 }?.let {
                    append(" Distanta totala: ${formatKm(it)} km.")
                }
                trail.elevationGain?.takeIf { it > 0 }?.let {
                    append(" Diferenta de nivel: +$it m.")
                }
                trail.sunsetTime?.takeIf { it.isNotBlank() && it != "N/A" }?.let {
                    append(" Apusul este estimat la ora $it, planifica plecarea in consecinta.")
                }
            }
        } else {
            buildString {
                append("Estimated duration for ${trail.name} is $duration.")
                trail.distanceKm?.takeIf { it > 0 }?.let {
                    append(" Total distance: ${formatKm(it)} km.")
                }
                trail.elevationGain?.takeIf { it > 0 }?.let {
                    append(" Elevation gain: +$it m.")
                }
                trail.sunsetTime?.takeIf { it.isNotBlank() && it != "N/A" }?.let {
                    append(" Sunset is estimated at $it, plan departure accordingly.")
                }
            }
        }
    }

    private fun buildMarkersSummary(trail: TrailContextSnapshot, isRomanian: Boolean): String {
        val marker = trail.markingLabel?.takeIf { it.isNotBlank() }
        return if (isRomanian) {
            if (marker != null) {
                "Marcajul traseului ${trail.name} este: $marker. Urmareste semnele pe copaci si pietre de-a lungul traseului."
            } else {
                "Nu am informatii despre marcajul traseului ${trail.name}. Verifica hartile locale sau intreaba la punctul de plecare."
            }
        } else {
            if (marker != null) {
                "The trail marker for ${trail.name} is: $marker. Follow the signs on trees and rocks along the route."
            } else {
                "I don't have marker information for ${trail.name}. Check local maps or ask at the starting point."
            }
        }
    }

    private fun buildEndpointsSummary(trail: TrailContextSnapshot, isRomanian: Boolean): String {
        val from = trail.fromName?.takeIf { it.isNotBlank() }
        val to = trail.toName?.takeIf { it.isNotBlank() }
        return if (isRomanian) {
            when {
                from != null && to != null -> "Traseul ${trail.name} porneste de la $from si ajunge la $to."
                from != null -> "Traseul ${trail.name} porneste de la $from."
                to != null -> "Traseul ${trail.name} ajunge la $to."
                else -> "Nu am informatii despre capetele traseului ${trail.name}."
            }
        } else {
            when {
                from != null && to != null -> "Trail ${trail.name} starts at $from and ends at $to."
                from != null -> "Trail ${trail.name} starts at $from."
                to != null -> "Trail ${trail.name} ends at $to."
                else -> "I don't have endpoint information for ${trail.name}."
            }
        }
    }

    private fun buildDistanceSummary(trail: TrailContextSnapshot, isRomanian: Boolean): String {
        val dist = trail.distanceKm?.takeIf { it > 0 }
        return if (isRomanian) {
            if (dist != null) {
                buildString {
                    append("Distanta totala a traseului ${trail.name} este ${formatKm(dist)} km.")
                    trail.elevationGain?.takeIf { it > 0 }?.let {
                        append(" Cu o diferenta de nivel de +$it m.")
                    }
                    trail.averageInclinePercent?.takeIf { it > 0 }?.let {
                        append(" Panta medie: ${formatPercent(it)}%.")
                    }
                }
            } else {
                "Nu am informatii despre distanta traseului ${trail.name}."
            }
        } else {
            if (dist != null) {
                buildString {
                    append("Total distance for ${trail.name} is ${formatKm(dist)} km.")
                    trail.elevationGain?.takeIf { it > 0 }?.let {
                        append(" With +$it m elevation gain.")
                    }
                    trail.averageInclinePercent?.takeIf { it > 0 }?.let {
                        append(" Average incline: ${formatPercent(it)}%.")
                    }
                }
            } else {
                "I don't have distance information for ${trail.name}."
            }
        }
    }

    private fun buildElevationSummary(trail: TrailContextSnapshot, isRomanian: Boolean): String {
        val gain = trail.elevationGain?.takeIf { it > 0 }
        return if (isRomanian) {
            if (gain != null) {
                buildString {
                    append("Diferenta de nivel pe traseul ${trail.name} este +$gain m.")
                    trail.averageInclinePercent?.takeIf { it > 0 }?.let {
                        append(" Panta medie: ${formatPercent(it)}%.")
                    }
                    if (gain >= 900) {
                        append(" Aceasta este o diferenta de nivel semnificativa. Betele de trekking sunt recomandate.")
                    } else if (gain >= 600) {
                        append(" O diferenta de nivel moderata. Betele de trekking pot fi utile.")
                    }
                }
            } else {
                "Nu am informatii despre diferenta de nivel pe traseul ${trail.name}."
            }
        } else {
            if (gain != null) {
                buildString {
                    append("Elevation gain on ${trail.name} is +$gain m.")
                    trail.averageInclinePercent?.takeIf { it > 0 }?.let {
                        append(" Average incline: ${formatPercent(it)}%.")
                    }
                    if (gain >= 900) {
                        append(" This is significant elevation gain. Trekking poles are recommended.")
                    } else if (gain >= 600) {
                        append(" Moderate elevation gain. Trekking poles may help.")
                    }
                }
            } else {
                "I don't have elevation information for ${trail.name}."
            }
        }
    }

    private fun buildSunsetSummary(trail: TrailContextSnapshot, isRomanian: Boolean): String {
        val sunset = trail.sunsetTime?.takeIf { it.isNotBlank() && it != "N/A" }
        return if (isRomanian) {
            if (sunset != null) {
                "Apusul soarelui la locatia traseului ${trail.name} este estimat la ora $sunset. Planifica sa fii pe drumul de intoarcere cu cel putin o ora inainte."
            } else {
                "Nu am informatii despre ora apusului pentru traseul ${trail.name}. Verifica inainte de plecare."
            }
        } else {
            if (sunset != null) {
                "Sunset at ${trail.name} is estimated at $sunset. Plan to be on the return path at least one hour before."
            } else {
                "I don't have sunset information for ${trail.name}. Check before departure."
            }
        }
    }

    private fun buildOverviewSummary(trail: TrailContextSnapshot, isRomanian: Boolean): String {
        val diffLabel = difficultyLabel(trail.difficulty, isRomanian)
        return if (isRomanian) {
            buildString {
                append("Traseul activ este ${trail.name}")
                trail.region?.takeIf { it.isNotBlank() }?.let { append(" din zona $it") }
                append(", de dificultate $diffLabel.")
            }
        } else {
            buildString {
                append("The active trail is ${trail.name}")
                trail.region?.takeIf { it.isNotBlank() }?.let { append(" in $it") }
                append(", $diffLabel difficulty.")
            }
        }
    }

    private fun buildFullTrailSection(trail: TrailContextSnapshot, isRomanian: Boolean): StructuredResponseSection {
        val body = buildList {
            if (!trail.fromName.isNullOrBlank() || !trail.toName.isNullOrBlank()) {
                add(if (isRomanian) {
                    "Traseu: ${trail.fromName ?: "?"} \u2192 ${trail.toName ?: "?"}"
                } else {
                    "Route: ${trail.fromName ?: "?"} \u2192 ${trail.toName ?: "?"}"
                })
            }
            trail.distanceKm?.takeIf { it > 0 }?.let {
                add(if (isRomanian) "Distanta: ${formatKm(it)} km" else "Distance: ${formatKm(it)} km")
            }
            trail.elevationGain?.takeIf { it > 0 }?.let {
                add(if (isRomanian) "Diferenta de nivel: +$it m" else "Elevation gain: +$it m")
            }
            trail.estimatedDuration?.takeIf { it.isNotBlank() }?.let {
                add(if (isRomanian) "Durata estimata: $it" else "Estimated duration: $it")
            }
            trail.markingLabel?.takeIf { it.isNotBlank() }?.let {
                add(if (isRomanian) "Marcaj: $it" else "Trail marker: $it")
            }
            trail.sunsetTime?.takeIf { it.isNotBlank() && it != "N/A" }?.let {
                add(if (isRomanian) "Apus estimat: $it" else "Estimated sunset: $it")
            }
            trail.weatherForecast?.takeIf { it.isNotBlank() }?.let {
                add(if (isRomanian) "Vreme: $it" else "Weather: $it")
            }
        }.joinToString("\n")

        return StructuredResponseSection(
            title = if (isRomanian) "Detalii traseu" else "Trail details",
            body = body,
            style = ResponseSectionStyle.CONTEXT
        )
    }

    // ── Weather ─────────────────────────────────────────────────────────

    private fun answerWeather(
        trail: TrailContextSnapshot,
        queryAnalysis: QueryAnalysis,
        context: DeviceContextSnapshot,
        isRomanian: Boolean
    ): TrailContextResult {
        val requestedDate = queryAnalysis.weatherQueryDate
        val sections = mutableListOf<StructuredResponseSection>()
        val followUps = mutableListOf<String>()

        val summary: String
        if (requestedDate != null) {
            val entry = trail.dailyForecast.find { it.date == requestedDate }
            if (entry != null) {
                summary = buildDateWeatherSummary(trail.name, requestedDate, entry, isRomanian)
                buildWeatherAdviceSection(entry, isRomanian)?.let { sections += it }
            } else {
                val formattedDate = formatDateForDisplay(requestedDate, isRomanian)
                summary = if (isRomanian) {
                    "Nu sunt disponibile inca date meteo pentru $formattedDate. Prognoza acopera de obicei urmatoarele 7 zile. Verifica din nou mai aproape de data dorita."
                } else {
                    "Weather data is not yet available for $formattedDate. The forecast usually covers the next 7 days. Check again closer to the date."
                }
            }
        } else {
            val currentWeather = trail.weatherForecast?.takeIf { it.isNotBlank() }
            val todayEntry = trail.dailyForecast.firstOrNull()
            summary = if (currentWeather != null) {
                if (isRomanian) {
                    buildString {
                        append("Vremea la ${trail.name}: $currentWeather.")
                        todayEntry?.let { entry ->
                            entry.temperatureMax?.let { max ->
                                entry.temperatureMin?.let { min ->
                                    append(" Temperatura azi intre ${formatTemp(min)} si ${formatTemp(max)}.")
                                }
                            }
                            entry.precipitationProbability?.takeIf { it > 20 }?.let {
                                append(" Probabilitate precipitatii: $it%.")
                            }
                        }
                    }
                } else {
                    buildString {
                        append("Weather at ${trail.name}: $currentWeather.")
                        todayEntry?.let { entry ->
                            entry.temperatureMax?.let { max ->
                                entry.temperatureMin?.let { min ->
                                    append(" Today's range: ${formatTemp(min)} to ${formatTemp(max)}.")
                                }
                            }
                            entry.precipitationProbability?.takeIf { it > 20 }?.let {
                                append(" Precipitation probability: $it%.")
                            }
                        }
                    }
                }
            } else {
                if (isRomanian) {
                    "Nu am date meteo actualizate pentru traseul ${trail.name}. Asigura-te ca ai conexiune la internet pentru sincronizare."
                } else {
                    "No updated weather data for ${trail.name}. Make sure you have internet connectivity for syncing."
                }
            }

            if (todayEntry != null) {
                buildWeatherAdviceSection(todayEntry, isRomanian)?.let { sections += it }
            }
        }

        if (trail.dailyForecast.size > 1) {
            sections += buildMultiDayForecastSection(trail, isRomanian)
        }

        followUps += weatherFollowUps(isRomanian)

        return buildResult(
            summary = summary,
            sections = sections,
            followUps = followUps,
            reasoningType = ReasoningType.WEATHER_CONTEXT,
            conversationState = AssistantConversationState(
                lastTrailContextIntent = "WEATHER_FORECAST"
            )
        )
    }

    private fun buildDateWeatherSummary(
        trailName: String,
        date: String,
        entry: DailyForecastEntry,
        isRomanian: Boolean
    ): String {
        val formattedDate = formatDateForDisplay(date, isRomanian)
        return if (isRomanian) {
            buildString {
                append("Prognoza meteo pentru $trailName pe $formattedDate: ${entry.description}.")
                entry.temperatureMax?.let { max ->
                    entry.temperatureMin?.let { min ->
                        append(" Temperatura intre ${formatTemp(min)} si ${formatTemp(max)}.")
                    }
                }
                entry.precipitationProbability?.takeIf { it > 0 }?.let {
                    append(" Probabilitate precipitatii: $it%.")
                }
                entry.sunrise?.takeIf { it.isNotBlank() }?.let { sr ->
                    entry.sunset?.takeIf { it.isNotBlank() }?.let { ss ->
                        val srTime = sr.substringAfter(" ").take(5)
                        val ssTime = ss.substringAfter(" ").take(5)
                        append(" Rasarit: $srTime, apus: $ssTime.")
                    }
                }
            }
        } else {
            buildString {
                append("Weather forecast for $trailName on $formattedDate: ${entry.description}.")
                entry.temperatureMax?.let { max ->
                    entry.temperatureMin?.let { min ->
                        append(" Temperature from ${formatTemp(min)} to ${formatTemp(max)}.")
                    }
                }
                entry.precipitationProbability?.takeIf { it > 0 }?.let {
                    append(" Precipitation probability: $it%.")
                }
                entry.sunrise?.takeIf { it.isNotBlank() }?.let { sr ->
                    entry.sunset?.takeIf { it.isNotBlank() }?.let { ss ->
                        val srTime = sr.substringAfter(" ").take(5)
                        val ssTime = ss.substringAfter(" ").take(5)
                        append(" Sunrise: $srTime, sunset: $ssTime.")
                    }
                }
            }
        }
    }

    private fun buildWeatherAdviceSection(
        entry: DailyForecastEntry,
        isRomanian: Boolean
    ): StructuredResponseSection? {
        val advice = mutableListOf<String>()
        val precipProb = entry.precipitationProbability ?: 0
        val maxTemp = entry.temperatureMax
        val minTemp = entry.temperatureMin

        if (precipProb >= 60) {
            advice += if (isRomanian) {
                "Probabilitate ridicata de precipitatii ($precipProb%). Ia geaca impermeabila si protejeaza electronicele."
            } else {
                "High precipitation probability ($precipProb%). Bring a waterproof jacket and protect electronics."
            }
        } else if (precipProb >= 30) {
            advice += if (isRomanian) {
                "Posibilitate de precipitatii ($precipProb%). Tine geaca impermeabila la indemana."
            } else {
                "Possible precipitation ($precipProb%). Keep rain jacket accessible."
            }
        }

        if (maxTemp != null && maxTemp >= 28) {
            advice += if (isRomanian) {
                "Temperatura ridicata estimata (${formatTemp(maxTemp)}). Ia apa suplimentara si protectie solara."
            } else {
                "High temperature expected (${formatTemp(maxTemp)}). Bring extra water and sun protection."
            }
        }

        if (minTemp != null && minTemp <= 5) {
            advice += if (isRomanian) {
                "Temperaturi scazute estimate (minim ${formatTemp(minTemp)}). Ia strat cald si folie de urgenta."
            } else {
                "Low temperatures expected (min ${formatTemp(minTemp)}). Bring insulation layer and emergency bivy."
            }
        }

        val descNormalized = entry.description.lowercase(Locale.ROOT)
        if ("thunder" in descNormalized || "storm" in descNormalized) {
            advice += if (isRomanian) {
                "Furtuna posibila. Evita crestele expuse si zonele inalte. Coboara din timp daca se innoureaza rapid."
            } else {
                "Possible thunderstorm. Avoid exposed ridges and high areas. Descend early if clouds build rapidly."
            }
        }
        if ("fog" in descNormalized) {
            advice += if (isRomanian) {
                "Ceata posibila. Navigheaza cu atentie si tine GPS-ul activ."
            } else {
                "Possible fog. Navigate carefully and keep GPS active."
            }
        }

        return advice.takeIf { it.isNotEmpty() }?.let {
            StructuredResponseSection(
                title = if (isRomanian) "Recomandari meteo" else "Weather advice",
                body = it.joinToString(" "),
                style = ResponseSectionStyle.GUIDANCE
            )
        }
    }

    private fun buildMultiDayForecastSection(
        trail: TrailContextSnapshot,
        isRomanian: Boolean
    ): StructuredResponseSection {
        val lines = trail.dailyForecast.take(5).map { entry ->
            val dateLabel = formatDateForDisplay(entry.date, isRomanian)
            val tempRange = if (entry.temperatureMin != null && entry.temperatureMax != null) {
                "${formatTemp(entry.temperatureMin)}-${formatTemp(entry.temperatureMax)}"
            } else {
                "--"
            }
            val precip = entry.precipitationProbability?.let { "${it}%" } ?: "--"
            "$dateLabel: ${entry.description}, $tempRange, precipitatii $precip"
        }
        return StructuredResponseSection(
            title = if (isRomanian) "Prognoza pe mai multe zile" else "Multi-day forecast",
            body = lines.joinToString("\n"),
            style = ResponseSectionStyle.CONTEXT
        )
    }

    // ── Capability Check ────────────────────────────────────────────────

    private fun answerCapability(
        query: String,
        trail: TrailContextSnapshot,
        context: DeviceContextSnapshot,
        isRomanian: Boolean
    ): TrailContextResult {
        val normalized = normalize(query)
        val sections = mutableListOf<StructuredResponseSection>()
        val followUps = mutableListOf<String>()
        val diffLabel = difficultyLabel(trail.difficulty, isRomanian)
        val diffRank = parseDifficultyRank(trail.difficulty)

        val condition = extractCondition(normalized)

        val summary: String
        val suitability: String

        when {
            containsAny(normalized, "incepator", "beginner", "prima data", "first time") -> {
                suitability = when (diffRank) {
                    0 -> if (isRomanian) "potrivit" else "suitable"
                    1 -> if (isRomanian) "fezabil cu o pregatire adecvata" else "feasible with proper preparation"
                    2 -> if (isRomanian) "provocator si necesita experienta anterioara" else "challenging and requires prior experience"
                    else -> if (isRomanian) "nu este recomandat fara experienta si ghid" else "not recommended without experience and a guide"
                }
                summary = if (isRomanian) {
                    "Traseul ${trail.name} ($diffLabel) este $suitability pentru un incepator."
                } else {
                    "Trail ${trail.name} ($diffLabel) is $suitability for a beginner."
                }
                sections += buildCapabilityDetailsSection(trail, diffRank, isRomanian)
            }

            containsAny(normalized, "genunchi", "knee", "articulat", "joint") -> {
                val risky = (trail.elevationGain ?: 0) > 600 || diffRank >= 2
                summary = if (isRomanian) {
                    if (risky) {
                        "Traseul ${trail.name} are o diferenta de nivel de +${trail.elevationGain ?: 0} m, ceea ce poate fi solicitant pentru genunchi. Betele de trekking sunt puternic recomandate, iar coborarea trebuie facuta cu atentie."
                    } else {
                        "Traseul ${trail.name} are o diferenta de nivel moderata (+${trail.elevationGain ?: 0} m). Cu bete de trekking si un ritm constant, ar trebui sa fie gestionabil."
                    }
                } else {
                    if (risky) {
                        "Trail ${trail.name} has +${trail.elevationGain ?: 0} m elevation gain, which can be demanding on knees. Trekking poles are strongly recommended, and descents should be taken carefully."
                    } else {
                        "Trail ${trail.name} has moderate elevation gain (+${trail.elevationGain ?: 0} m). With trekking poles and a steady pace, it should be manageable."
                    }
                }
            }

            containsAny(normalized, "copil", "copii", "child", "children", "kid", "familie", "family") -> {
                val suitable = diffRank <= 1 && (trail.distanceKm ?: 0.0) <= 10.0
                summary = if (isRomanian) {
                    if (suitable) {
                        "Traseul ${trail.name} ($diffLabel, ${formatKm(trail.distanceKm ?: 0.0)} km) este potrivit pentru familii cu copii. Planifica pauze mai dese si ia apa si gustari suplimentare."
                    } else {
                        "Traseul ${trail.name} ($diffLabel, ${formatKm(trail.distanceKm ?: 0.0)} km, +${trail.elevationGain ?: 0} m) nu este ideal pentru copii. Cauta o alternativa mai scurta si mai usoara."
                    }
                } else {
                    if (suitable) {
                        "Trail ${trail.name} ($diffLabel, ${formatKm(trail.distanceKm ?: 0.0)} km) is suitable for families with children. Plan more frequent breaks and bring extra water and snacks."
                    } else {
                        "Trail ${trail.name} ($diffLabel, ${formatKm(trail.distanceKm ?: 0.0)} km, +${trail.elevationGain ?: 0} m) is not ideal for children. Look for a shorter, easier alternative."
                    }
                }
            }

            containsAny(normalized, "batran", "varstnic", "elderly", "senior", "old") -> {
                val suitable = diffRank <= 1 && (trail.elevationGain ?: 0) <= 500
                summary = if (isRomanian) {
                    if (suitable) {
                        "Traseul ${trail.name} ($diffLabel) este accesibil pentru persoane in varsta cu o conditie fizica rezonabila. Planifica un ritm mai lejer si pauze regulate."
                    } else {
                        "Traseul ${trail.name} ($diffLabel, +${trail.elevationGain ?: 0} m) poate fi dificil. Evalueaza conditia fizica si ia in considerare o alternativa mai usoara."
                    }
                } else {
                    if (suitable) {
                        "Trail ${trail.name} ($diffLabel) is accessible for elderly people with reasonable fitness. Plan a relaxed pace with regular breaks."
                    } else {
                        "Trail ${trail.name} ($diffLabel, +${trail.elevationGain ?: 0} m) may be challenging. Evaluate fitness level and consider an easier alternative."
                    }
                }
            }

            containsAny(normalized, "echipament special", "special gear", "fara echipament", "no gear", "without equipment") -> {
                val mandatoryGear = context.gearItems.filter { it.necessity == "MANDATORY" && !it.isPacked }
                summary = if (isRomanian) {
                    if (mandatoryGear.isEmpty()) {
                        "Traseul ${trail.name} nu necesita echipament tehnic special peste echipamentul standard de drumetie."
                    } else {
                        "Pentru traseul ${trail.name} ai nevoie cel putin de: ${mandatoryGear.joinToString(", ") { it.name }}. Nu pleca fara acestea."
                    }
                } else {
                    if (mandatoryGear.isEmpty()) {
                        "Trail ${trail.name} does not require special technical gear beyond standard hiking equipment."
                    } else {
                        "For trail ${trail.name} you need at least: ${mandatoryGear.joinToString(", ") { it.name }}. Do not start without these."
                    }
                }
            }

            else -> {
                summary = if (isRomanian) {
                    buildString {
                        append("Traseul ${trail.name} are dificultatea $diffLabel")
                        trail.distanceKm?.let { append(", ${formatKm(it)} km") }
                        trail.elevationGain?.let { append(", +$it m") }
                        trail.estimatedDuration?.let { append(", durata $it") }
                        append(".")
                        if (condition.isNotBlank()) {
                            append(" Spune-mi mai precis conditia ta ca sa pot evalua mai bine.")
                        }
                    }
                } else {
                    buildString {
                        append("Trail ${trail.name} is $diffLabel difficulty")
                        trail.distanceKm?.let { append(", ${formatKm(it)} km") }
                        trail.elevationGain?.let { append(", +$it m") }
                        trail.estimatedDuration?.let { append(", duration $it") }
                        append(".")
                        if (condition.isNotBlank()) {
                            append(" Tell me more about your condition so I can evaluate better.")
                        }
                    }
                }
            }
        }

        followUps += capabilityFollowUps(isRomanian)

        return buildResult(
            summary = summary,
            sections = sections,
            followUps = followUps,
            reasoningType = ReasoningType.ROUTE_CONTEXT,
            conversationState = AssistantConversationState(
                lastTrailContextIntent = "CAPABILITY_CHECK"
            )
        )
    }

    private fun buildCapabilityDetailsSection(
        trail: TrailContextSnapshot,
        diffRank: Int,
        isRomanian: Boolean
    ): StructuredResponseSection {
        val tips = mutableListOf<String>()
        if (isRomanian) {
            if (diffRank >= 1) tips += "Ia bete de trekking pentru stabilitate pe urcari si coborari."
            if ((trail.elevationGain ?: 0) > 500) tips += "Pregateste-te pentru urcare sustinuta. Planifica pauze la fiecare 45-60 de minute."
            if ((trail.distanceKm ?: 0.0) > 10) tips += "Distanta este semnificativa. Ia apa si gustari suficiente."
            tips += "Verifica prognoza meteo inainte de plecare si informeaza pe cineva despre traseul ales."
        } else {
            if (diffRank >= 1) tips += "Bring trekking poles for stability on ascents and descents."
            if ((trail.elevationGain ?: 0) > 500) tips += "Prepare for sustained climbing. Plan breaks every 45-60 minutes."
            if ((trail.distanceKm ?: 0.0) > 10) tips += "Distance is significant. Bring enough water and snacks."
            tips += "Check the weather forecast before departure and inform someone about your chosen trail."
        }
        return StructuredResponseSection(
            title = if (isRomanian) "Sfaturi" else "Tips",
            body = tips.joinToString(" "),
            style = ResponseSectionStyle.GUIDANCE
        )
    }

    // ── Needs Check ─────────────────────────────────────────────────────

    private fun answerNeeds(
        trail: TrailContextSnapshot,
        context: DeviceContextSnapshot,
        conversationState: AssistantConversationState,
        isRomanian: Boolean
    ): TrailContextResult {
        val gearItems = context.gearItems
        val mandatory = gearItems.filter { it.necessity == "MANDATORY" }
        val recommended = gearItems.filter { it.necessity == "RECOMMENDED" }
        val notPacked = gearItems.filter { !it.isPacked }
        val sections = mutableListOf<StructuredResponseSection>()
        val followUps = mutableListOf<String>()

        val summary = if (isRomanian) {
            buildString {
                append("Pentru traseul ${trail.name}")
                trail.difficulty?.let { append(" ($it)") }
                append(", ai nevoie de:")
            }
        } else {
            buildString {
                append("For trail ${trail.name}")
                trail.difficulty?.let { append(" ($it)") }
                append(", you need:")
            }
        }

        if (mandatory.isNotEmpty()) {
            val mandatoryBody = mandatory.joinToString("\n") { item ->
                val packed = if (item.isPacked) {
                    if (isRomanian) " [impachetat]" else " [packed]"
                } else {
                    ""
                }
                "\u2022 ${item.name}$packed"
            }
            sections += StructuredResponseSection(
                title = if (isRomanian) "Obligatoriu" else "Mandatory",
                body = mandatoryBody,
                style = ResponseSectionStyle.IMPORTANT
            )
        }

        if (recommended.isNotEmpty()) {
            val recommendedBody = recommended.joinToString("\n") { item ->
                val packed = if (item.isPacked) {
                    if (isRomanian) " [impachetat]" else " [packed]"
                } else {
                    ""
                }
                "\u2022 ${item.name} - ${item.note}$packed"
            }
            sections += StructuredResponseSection(
                title = if (isRomanian) "Recomandat" else "Recommended",
                body = recommendedBody,
                style = ResponseSectionStyle.GUIDANCE
            )
        }

        val notPackedMandatory = mandatory.filter { !it.isPacked }
        if (notPackedMandatory.isNotEmpty()) {
            val warningBody = if (isRomanian) {
                "Nu ai bifat inca: ${notPackedMandatory.joinToString(", ") { it.name }}. Asigura-te ca le ai inainte de plecare."
            } else {
                "Not yet checked: ${notPackedMandatory.joinToString(", ") { it.name }}. Make sure you have them before departure."
            }
            sections += StructuredResponseSection(
                title = if (isRomanian) "Atentie" else "Warning",
                body = warningBody,
                style = ResponseSectionStyle.IMPORTANT
            )
        }

        buildWeatherGearSection(trail, isRomanian)?.let { sections += it }

        followUps += if (isRomanian) {
            listOf(
                "Vrei sa actualizez lista de echipament?",
                "Am nevoie de ceva in plus?",
                "Cat apa sa iau?"
            )
        } else {
            listOf(
                "Want me to update the gear list?",
                "Do I need anything extra?",
                "How much water should I bring?"
            )
        }

        return buildResult(
            summary = summary,
            sections = sections,
            followUps = followUps,
            reasoningType = ReasoningType.GEAR_ADVICE,
            conversationState = AssistantConversationState(
                lastTrailContextIntent = "NEEDS_CHECK"
            )
        )
    }

    private fun buildWeatherGearSection(
        trail: TrailContextSnapshot,
        isRomanian: Boolean
    ): StructuredResponseSection? {
        val weather = trail.weatherForecast?.lowercase(Locale.ROOT) ?: return null
        val advice = mutableListOf<String>()

        if (listOf("rain", "storm", "thunder", "ploaie", "furtuna").any { it in weather }) {
            advice += if (isRomanian) {
                "Prognoza indica ploaie. Geaca impermeabila si protectia echipamentului sunt prioritare."
            } else {
                "Forecast indicates rain. Waterproof jacket and gear protection are priorities."
            }
        }
        if (listOf("clear", "sun", "soare").any { it in weather }) {
            val temp = extractTemperature(trail.weatherForecast)
            if (temp != null && temp >= 24) {
                advice += if (isRomanian) {
                    "Temperaturi ridicate (${formatTemp(temp)}). Ia apa suplimentara si protectie solara."
                } else {
                    "High temperatures (${formatTemp(temp)}). Bring extra water and sun protection."
                }
            }
        }

        return advice.takeIf { it.isNotEmpty() }?.let {
            StructuredResponseSection(
                title = if (isRomanian) "Echipament specific vremii" else "Weather-specific gear",
                body = it.joinToString(" "),
                style = ResponseSectionStyle.GUIDANCE
            )
        }
    }

    // ── Duration Estimate ───────────────────────────────────────────────

    private fun answerDuration(
        query: String,
        trail: TrailContextSnapshot,
        isRomanian: Boolean
    ): TrailContextResult {
        val normalized = normalize(query)
        val baseDuration = parseDurationHours(trail.estimatedDuration)
        val followUps = mutableListOf<String>()

        val factor: Double
        val explanation: String

        when {
            containsAny(normalized, "lent", "incet", "slow", "slowly", "lejer") -> {
                factor = 1.35
                explanation = if (isRomanian) "un ritm mai lejer" else "a slower pace"
            }
            containsAny(normalized, "rapid", "repede", "fast", "quick") -> {
                factor = 0.8
                explanation = if (isRomanian) "un ritm rapid" else "a fast pace"
            }
            containsAny(normalized, "copil", "copii", "child", "children", "kid") -> {
                factor = 1.6
                explanation = if (isRomanian) "ritmul cu copii" else "pace with children"
            }
            containsAny(normalized, "batran", "varstnic", "elderly", "senior") -> {
                factor = 1.4
                explanation = if (isRomanian) "un ritm adaptat" else "an adapted pace"
            }
            containsAny(normalized, "grup", "group") -> {
                factor = 1.25
                explanation = if (isRomanian) "deplasarea in grup" else "group travel"
            }
            containsAny(normalized, "pauze", "pauza", "break", "breaks", "odihna") -> {
                factor = 1.3
                explanation = if (isRomanian) "pauze frecvente" else "frequent breaks"
            }
            else -> {
                factor = 1.0
                explanation = if (isRomanian) "ritmul standard" else "standard pace"
            }
        }

        val summary = if (baseDuration != null) {
            val adjusted = baseDuration * factor
            val hours = adjusted.toInt()
            val minutes = ((adjusted - hours) * 60).roundToInt()
            val timeStr = if (minutes > 0) "${hours}h ${minutes}min" else "${hours}h"

            if (isRomanian) {
                buildString {
                    if (factor != 1.0) {
                        append("Cu $explanation, traseul ${trail.name} ar dura aproximativ $timeStr")
                        append(" (fata de ${trail.estimatedDuration} standard).")
                    } else {
                        append("Durata estimata a traseului ${trail.name} este ${trail.estimatedDuration}.")
                    }
                    trail.sunsetTime?.takeIf { it.isNotBlank() && it != "N/A" }?.let {
                        append(" Apusul este la $it.")
                    }
                }
            } else {
                buildString {
                    if (factor != 1.0) {
                        append("With $explanation, trail ${trail.name} would take approximately $timeStr")
                        append(" (vs. ${trail.estimatedDuration} standard).")
                    } else {
                        append("Estimated duration for ${trail.name} is ${trail.estimatedDuration}.")
                    }
                    trail.sunsetTime?.takeIf { it.isNotBlank() && it != "N/A" }?.let {
                        append(" Sunset is at $it.")
                    }
                }
            }
        } else {
            if (isRomanian) {
                "Nu am suficiente informatii despre durata traseului ${trail.name} pentru a estima timpul."
            } else {
                "I don't have enough duration information for ${trail.name} to estimate time."
            }
        }

        followUps += durationFollowUps(isRomanian)

        return buildResult(
            summary = summary,
            sections = emptyList(),
            followUps = followUps,
            reasoningType = ReasoningType.ROUTE_CONTEXT,
            conversationState = AssistantConversationState(
                lastTrailContextIntent = "DURATION_ESTIMATE"
            )
        )
    }

    // ── Gear Review ─────────────────────────────────────────────────────

    private fun answerGearReview(
        trail: TrailContextSnapshot,
        context: DeviceContextSnapshot,
        conversationState: AssistantConversationState,
        isRomanian: Boolean
    ): TrailContextResult {
        val gearItems = context.gearItems
        val sections = mutableListOf<StructuredResponseSection>()
        val followUps = mutableListOf<String>()
        val actions = mutableListOf<AssistantAction>()

        val packed = gearItems.filter { it.isPacked }
        val notPacked = gearItems.filter { !it.isPacked }
        val mandatoryNotPacked = notPacked.filter { it.necessity == "MANDATORY" }

        val summary = if (isRomanian) {
            buildString {
                append("Lista de echipament pentru ${trail.name}: ")
                append("${packed.size} bifate din ${gearItems.size} total.")
                if (mandatoryNotPacked.isNotEmpty()) {
                    append(" Atentie: ${mandatoryNotPacked.size} obligatorii nebifate!")
                }
            }
        } else {
            buildString {
                append("Gear list for ${trail.name}: ")
                append("${packed.size} checked out of ${gearItems.size} total.")
                if (mandatoryNotPacked.isNotEmpty()) {
                    append(" Warning: ${mandatoryNotPacked.size} mandatory items unchecked!")
                }
            }
        }

        val gearBody = gearItems.joinToString("\n") { item ->
            val status = if (item.isPacked) "\u2705" else "\u2B1C"
            val necessity = when (item.necessity) {
                "MANDATORY" -> if (isRomanian) " [obligatoriu]" else " [mandatory]"
                "RECOMMENDED" -> if (isRomanian) " [recomandat]" else " [recommended]"
                else -> ""
            }
            "$status ${item.name}$necessity"
        }
        sections += StructuredResponseSection(
            title = if (isRomanian) "Echipament" else "Gear",
            body = gearBody,
            style = ResponseSectionStyle.CONTEXT
        )

        buildWeatherGearSection(trail, isRomanian)?.let { sections += it }

        if (notPacked.isNotEmpty()) {
            val packIds = notPacked.map { it.id }
            followUps += if (isRomanian) {
                listOf(
                    "Bifez totul ca impachetat",
                    "Ce e cel mai important?",
                    "Am impachetat tot ce e obligatoriu"
                )
            } else {
                listOf(
                    "Mark all as packed",
                    "What is most important?",
                    "I packed everything mandatory"
                )
            }
        }

        return buildResult(
            summary = summary,
            sections = sections,
            followUps = followUps,
            reasoningType = ReasoningType.GEAR_ADVICE,
            conversationState = AssistantConversationState(
                lastTrailContextIntent = "GEAR_REVIEW",
                pendingGearAction = if (notPacked.isNotEmpty()) {
                    PendingGearAction(packItemIds = notPacked.map { it.id })
                } else null
            )
        )
    }

    // ── Gear Update Confirmation ────────────────────────────────────────

    private fun processGearConfirmation(
        query: String,
        conversationState: AssistantConversationState,
        isRomanian: Boolean
    ): TrailContextResult? {
        val pending = conversationState.pendingGearAction ?: return null
        val normalized = normalize(query)
        val actions = mutableListOf<AssistantAction>()

        return when {
            isAffirmative(normalized) -> {
                if (pending.packItemIds.isNotEmpty()) {
                    actions += AssistantAction.ToggleGearPacked(
                        itemIds = pending.packItemIds,
                        packed = true
                    )
                }
                if (pending.unpackItemIds.isNotEmpty()) {
                    actions += AssistantAction.ToggleGearPacked(
                        itemIds = pending.unpackItemIds,
                        packed = false
                    )
                }
                val summary = if (isRomanian) {
                    "Lista de echipament a fost actualizata."
                } else {
                    "The gear list has been updated."
                }
                buildResult(
                    summary = summary,
                    sections = emptyList(),
                    followUps = if (isRomanian) {
                        listOf("Mai am nevoie de ceva?", "Spune-mi despre traseu")
                    } else {
                        listOf("Do I need anything else?", "Tell me about the trail")
                    },
                    reasoningType = ReasoningType.GEAR_ADVICE,
                    conversationState = AssistantConversationState(
                        lastTrailContextIntent = "GEAR_UPDATE_CONFIRM",
                        pendingGearAction = null
                    ),
                    actions = actions
                )
            }

            isNegative(normalized) -> {
                val summary = if (isRomanian) {
                    "Lista de echipament ramane neschimbata."
                } else {
                    "The gear list remains unchanged."
                }
                buildResult(
                    summary = summary,
                    sections = emptyList(),
                    followUps = if (isRomanian) {
                        listOf("Ce echipament am nevoie?", "Spune-mi despre traseu")
                    } else {
                        listOf("What gear do I need?", "Tell me about the trail")
                    },
                    reasoningType = ReasoningType.GEAR_ADVICE,
                    conversationState = AssistantConversationState(
                        lastTrailContextIntent = null,
                        pendingGearAction = null
                    )
                )
            }

            // If the user says something about specific items, try to parse it
            containsAny(normalized, "bifez", "impachetat", "packed", "mark", "tot", "all", "obligatoriu", "mandatory") -> {
                val itemsToToggle = when {
                    containsAny(normalized, "tot", "all", "totul") -> pending.packItemIds
                    containsAny(normalized, "obligatoriu", "mandatory") ->
                        pending.packItemIds // In a real implementation, filter by mandatory
                    else -> pending.packItemIds
                }
                if (itemsToToggle.isNotEmpty()) {
                    actions += AssistantAction.ToggleGearPacked(
                        itemIds = itemsToToggle,
                        packed = true
                    )
                }
                val summary = if (isRomanian) {
                    "Am bifat ${itemsToToggle.size} articole ca impachetate."
                } else {
                    "Marked ${itemsToToggle.size} items as packed."
                }
                buildResult(
                    summary = summary,
                    sections = emptyList(),
                    followUps = if (isRomanian) {
                        listOf("Mai am nevoie de ceva?", "Arata-mi lista")
                    } else {
                        listOf("Do I need anything else?", "Show me the list")
                    },
                    reasoningType = ReasoningType.GEAR_ADVICE,
                    conversationState = AssistantConversationState(
                        lastTrailContextIntent = "GEAR_UPDATE_CONFIRM",
                        pendingGearAction = null
                    ),
                    actions = actions
                )
            }

            else -> null
        }
    }

    // ── Weather context section (reusable) ──────────────────────────────

    private fun buildWeatherContextSection(
        trail: TrailContextSnapshot,
        context: DeviceContextSnapshot,
        isRomanian: Boolean
    ): StructuredResponseSection? {
        val weather = trail.weatherForecast?.takeIf { it.isNotBlank() } ?: return null
        val body = if (isRomanian) "Vreme curenta: $weather." else "Current weather: $weather."
        return StructuredResponseSection(
            title = if (isRomanian) "Context meteo" else "Weather context",
            body = body,
            style = ResponseSectionStyle.CONTEXT
        )
    }

    // ── Follow-up generators ────────────────────────────────────────────

    private fun trailInfoFollowUps(aspect: TrailInfoAspect, isRomanian: Boolean): List<String> =
        if (isRomanian) {
            when (aspect) {
                TrailInfoAspect.DIFFICULTY -> listOf(
                    "Pot face traseul daca sunt incepator?",
                    "Ce echipament am nevoie?",
                    "Cat ar dura daca merg incet?"
                )
                TrailInfoAspect.DURATION -> listOf(
                    "Cat ar dura daca merg cu copii?",
                    "La ce ora apune soarele?",
                    "Am nevoie de frontala?"
                )
                TrailInfoAspect.MARKERS -> listOf(
                    "De unde porneste traseul?",
                    "Care e dificultatea?",
                    "Cat dureaza traseul?"
                )
                TrailInfoAspect.ENDPOINTS -> listOf(
                    "Ce marcaj are traseul?",
                    "Cat dureaza?",
                    "Am nevoie de transport la intoarcere?"
                )
                TrailInfoAspect.OVERVIEW -> listOf(
                    "Pot face traseul daca sunt incepator?",
                    "Ce echipament am nevoie?",
                    "Cum va fi vremea?"
                )
                else -> listOf(
                    "Spune-mi mai multe despre traseu",
                    "Ce echipament am nevoie?",
                    "Cum va fi vremea?"
                )
            }
        } else {
            when (aspect) {
                TrailInfoAspect.DIFFICULTY -> listOf(
                    "Can I do this as a beginner?",
                    "What gear do I need?",
                    "How long if I go slow?"
                )
                TrailInfoAspect.DURATION -> listOf(
                    "How long with children?",
                    "What time is sunset?",
                    "Do I need a headlamp?"
                )
                TrailInfoAspect.MARKERS -> listOf(
                    "Where does the trail start?",
                    "What is the difficulty?",
                    "How long is the trail?"
                )
                TrailInfoAspect.ENDPOINTS -> listOf(
                    "What trail markers does it have?",
                    "How long is it?",
                    "Do I need transport back?"
                )
                TrailInfoAspect.OVERVIEW -> listOf(
                    "Can I do this as a beginner?",
                    "What gear do I need?",
                    "What will the weather be like?"
                )
                else -> listOf(
                    "Tell me more about the trail",
                    "What gear do I need?",
                    "What will the weather be like?"
                )
            }
        }

    private fun weatherFollowUps(isRomanian: Boolean): List<String> =
        if (isRomanian) {
            listOf(
                "Am nevoie de geaca impermeabila?",
                "E bine sa merg maine?",
                "Cat apa sa iau?"
            )
        } else {
            listOf(
                "Do I need a rain jacket?",
                "Is tomorrow a good day to go?",
                "How much water should I bring?"
            )
        }

    private fun capabilityFollowUps(isRomanian: Boolean): List<String> =
        if (isRomanian) {
            listOf(
                "Ce echipament am nevoie?",
                "Cat ar dura in ritmul meu?",
                "Cum va fi vremea?"
            )
        } else {
            listOf(
                "What gear do I need?",
                "How long at my pace?",
                "What will the weather be like?"
            )
        }

    private fun durationFollowUps(isRomanian: Boolean): List<String> =
        if (isRomanian) {
            listOf(
                "La ce ora apune soarele?",
                "Am nevoie de frontala?",
                "Cat apa sa iau?"
            )
        } else {
            listOf(
                "What time is sunset?",
                "Do I need a headlamp?",
                "How much water should I bring?"
            )
        }

    // ── Result builder ──────────────────────────────────────────────────

    private fun buildResult(
        summary: String,
        sections: List<StructuredResponseSection>,
        followUps: List<String>,
        reasoningType: ReasoningType,
        conversationState: AssistantConversationState,
        actions: List<AssistantAction> = emptyList()
    ): TrailContextResult {
        return TrailContextResult(
            structuredOutput = StructuredAssistantOutput(
                summary = summary,
                sections = sections,
                generationMode = GenerationMode.CARD_DIRECT,
                reasoningType = reasoningType,
                followUpQuestions = followUps
            ),
            conversationState = conversationState,
            actions = actions
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private enum class TrailInfoAspect {
        DIFFICULTY, DURATION, MARKERS, ENDPOINTS, DISTANCE, ELEVATION, SUNSET, OVERVIEW
    }

    private fun detectTrailInfoAspect(normalized: String, tokens: List<String>): TrailInfoAspect =
        when {
            containsAny(normalized, "dificultate", "dificil", "difficulty", "greu", "usor", "nivel") ->
                TrailInfoAspect.DIFFICULTY
            containsAny(normalized, "dureaza", "durata", "duration", "cat timp", "how long", "ore") ->
                TrailInfoAspect.DURATION
            containsAny(normalized, "marcaj", "marker", "culoare", "semnalizare", "marking") ->
                TrailInfoAspect.MARKERS
            containsAny(normalized, "porneste", "incepe", "termina", "ajunge", "plecare", "sosire",
                "de unde", "pana unde", "start", "end", "from", "to") ->
                TrailInfoAspect.ENDPOINTS
            containsAny(normalized, "distanta", "km", "kilometr", "distance", "lung", "cat de lung") ->
                TrailInfoAspect.DISTANCE
            containsAny(normalized, "urcare", "coborare", "diferenta de nivel", "elevation", "inclinatie",
                "panta", "altitudine") ->
                TrailInfoAspect.ELEVATION
            containsAny(normalized, "apus", "soare", "sunset", "lumina", "intuneric", "noapte") ->
                TrailInfoAspect.SUNSET
            else -> TrailInfoAspect.OVERVIEW
        }

    private fun difficultyLabel(raw: String?, isRomanian: Boolean): String {
        val normalized = raw?.lowercase(Locale.ROOT)?.trim().orEmpty()
        return when {
            "expert" in normalized -> if (isRomanian) "expert" else "expert"
            "hard" in normalized || "dificil" in normalized -> if (isRomanian) "dificil" else "hard"
            "medium" in normalized || "mediu" in normalized -> if (isRomanian) "mediu" else "medium"
            "easy" in normalized || "usor" in normalized -> if (isRomanian) "usor" else "easy"
            else -> if (isRomanian) "mediu" else "medium"
        }
    }

    private fun parseDifficultyRank(raw: String?): Int {
        val normalized = raw?.lowercase(Locale.ROOT)?.trim().orEmpty()
        return when {
            "easy" in normalized || "usor" in normalized -> 0
            "medium" in normalized || "mediu" in normalized -> 1
            "hard" in normalized || "dificil" in normalized -> 2
            "expert" in normalized -> 3
            else -> 1
        }
    }

    private fun parseDurationHours(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.lowercase(Locale.ROOT)
        val hours = "(\\d+)\\s*h".toRegex().find(cleaned)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        val minutes = "(\\d+)\\s*m(?:in)?".toRegex().find(cleaned)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        val total = hours + (minutes / 60.0)
        return total.takeIf { it > 0.0 }
    }

    private fun extractCondition(normalized: String): String {
        val markers = listOf("daca", "if", "cand", "when", "cu", "with", "fara", "without")
        markers.forEach { marker ->
            val idx = normalized.indexOf(marker)
            if (idx >= 0) {
                return normalized.substring(idx).take(60)
            }
        }
        return ""
    }

    private fun extractTemperature(weatherSummary: String?): Double? =
        weatherSummary
            ?.let { "(-?\\d+(?:\\.\\d+)?)\\s*°?c".toRegex(RegexOption.IGNORE_CASE).find(it) }
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()

    private fun isAffirmative(normalized: String): Boolean =
        normalized.trim() in setOf("da", "yes", "ok", "sigur", "sure", "bine", "hai", "fa-o", "go ahead", "confirm")

    private fun isNegative(normalized: String): Boolean =
        normalized.trim() in setOf("nu", "no", "nope", "las", "lasa", "renunt", "cancel", "stop")

    private fun containsAny(text: String, vararg terms: String): Boolean =
        terms.any { it in text }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    private fun formatKm(km: Double): String = String.format(Locale.US, "%.1f", km)

    private fun formatTemp(temp: Double): String = String.format(Locale.US, "%.1f°C", temp)

    private fun formatPercent(pct: Double): String = String.format(Locale.US, "%.1f", pct)

    private fun formatDateForDisplay(isoDate: String, isRomanian: Boolean): String {
        return try {
            val date = LocalDate.parse(isoDate, DateTimeFormatter.ISO_LOCAL_DATE)
            val today = LocalDate.now()
            when {
                date == today -> if (isRomanian) "azi" else "today"
                date == today.plusDays(1) -> if (isRomanian) "maine" else "tomorrow"
                date == today.plusDays(2) -> if (isRomanian) "poimaine" else "day after tomorrow"
                else -> {
                    val dayOfWeek = date.dayOfWeek.getDisplayName(
                        TextStyle.FULL,
                        if (isRomanian) Locale.forLanguageTag("ro") else Locale.ENGLISH
                    )
                    val month = date.month.getDisplayName(
                        TextStyle.FULL,
                        if (isRomanian) Locale.forLanguageTag("ro") else Locale.ENGLISH
                    )
                    "$dayOfWeek, ${date.dayOfMonth} $month"
                }
            }
        } catch (_: Exception) {
            isoDate
        }
    }
}
