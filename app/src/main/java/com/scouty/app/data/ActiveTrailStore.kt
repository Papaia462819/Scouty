package com.scouty.app.data

import android.content.Context
import com.scouty.app.assistant.model.DailyForecastEntry
import com.scouty.app.ui.models.ActiveTrail
import com.scouty.app.ui.models.ActiveTrailState
import com.scouty.app.ui.models.TrailPartyComposition
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar

class ActiveTrailStore(context: Context) {
    private val prefs = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    fun load(): ActiveTrail? {
        val json = prefs.getString(KeyActiveTrail, null) ?: return null
        return runCatching {
            serializer.decodeFromString<PersistedActiveTrail>(json).toActiveTrail()
        }.getOrNull()
    }

    fun save(trail: ActiveTrail) {
        val json = serializer.encodeToString(PersistedActiveTrail.from(trail))
        prefs.edit().putString(KeyActiveTrail, json).apply()
    }

    fun clear() {
        prefs.edit().remove(KeyActiveTrail).apply()
    }

    private companion object {
        const val PrefsName = "scouty_active_trail"
        const val KeyActiveTrail = "active_trail_json"

        val serializer: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

@Serializable
private data class PersistedActiveTrail(
    val name: String,
    val dateEpochMillis: Long,
    val partyComposition: TrailPartyComposition = TrailPartyComposition(),
    val latitude: Double,
    val longitude: Double,
    val localCode: String? = null,
    val region: String? = null,
    val descriptionRo: String? = null,
    val localDescription: String? = null,
    val routeSummary: String? = null,
    val fromName: String? = null,
    val toName: String? = null,
    val markingSymbols: List<String> = emptyList(),
    val sourceUrls: List<String> = emptyList(),
    val sunsetTime: String? = null,
    val weatherForecast: String? = null,
    val difficulty: String = "MEDIUM",
    val distanceKm: Double = 12.4,
    val elevationGain: Int = 1234,
    val averageInclinePercent: Double = 0.0,
    val estimatedDuration: String = "~6h",
    val imageUrl: String? = null,
    val imageAttribution: String? = null,
    val imageLicense: String? = null,
    val imageSourcePageUrl: String? = null,
    val imageScope: String? = null,
    val routeSegments: List<List<RouteCoordinate>> = emptyList(),
    val remainingRouteSegments: List<List<RouteCoordinate>> = emptyList(),
    val routeBounds: RouteBounds? = null,
    val trackingState: String = ActiveTrailState.PLANNED.name,
    val progress: Float = 0f,
    val distanceCompletedKm: Double = 0.0,
    val remainingDistanceKm: Double = distanceKm,
    val offTrailDistanceKm: Double = 0.0,
    val hasLeftStartZone: Boolean = false,
    val startedAtEpochMillis: Long? = null,
    val lastSyncTimestamp: Long? = null,
    val dailyForecast: List<DailyForecastEntry> = emptyList()
) {
    fun toActiveTrail(): ActiveTrail {
        val date = Calendar.getInstance().apply { timeInMillis = dateEpochMillis }
        val state = runCatching { ActiveTrailState.valueOf(trackingState) }
            .getOrDefault(ActiveTrailState.PLANNED)
        return ActiveTrail(
            name = name,
            date = date,
            partyComposition = partyComposition,
            latitude = latitude,
            longitude = longitude,
            localCode = localCode,
            region = region,
            descriptionRo = descriptionRo,
            localDescription = localDescription,
            routeSummary = routeSummary,
            fromName = fromName,
            toName = toName,
            markingSymbols = markingSymbols,
            sourceUrls = sourceUrls,
            sunsetTime = sunsetTime,
            weatherForecast = weatherForecast,
            difficulty = difficulty,
            distanceKm = distanceKm,
            elevationGain = elevationGain,
            averageInclinePercent = averageInclinePercent,
            estimatedDuration = estimatedDuration,
            imageUrl = imageUrl,
            imageAttribution = imageAttribution,
            imageLicense = imageLicense,
            imageSourcePageUrl = imageSourcePageUrl,
            imageScope = imageScope,
            routeSegments = routeSegments,
            remainingRouteSegments = remainingRouteSegments.ifEmpty { routeSegments },
            routeBounds = routeBounds,
            trackingState = state,
            progress = progress,
            distanceCompletedKm = distanceCompletedKm,
            remainingDistanceKm = remainingDistanceKm,
            offTrailDistanceKm = offTrailDistanceKm,
            hasLeftStartZone = hasLeftStartZone,
            startedAtEpochMillis = startedAtEpochMillis,
            lastSyncTimestamp = lastSyncTimestamp,
            dailyForecast = dailyForecast
        )
    }

    companion object {
        fun from(trail: ActiveTrail): PersistedActiveTrail =
            PersistedActiveTrail(
                name = trail.name,
                dateEpochMillis = trail.date.timeInMillis,
                partyComposition = trail.partyComposition,
                latitude = trail.latitude,
                longitude = trail.longitude,
                localCode = trail.localCode,
                region = trail.region,
                descriptionRo = trail.descriptionRo,
                localDescription = trail.localDescription,
                routeSummary = trail.routeSummary,
                fromName = trail.fromName,
                toName = trail.toName,
                markingSymbols = trail.markingSymbols,
                sourceUrls = trail.sourceUrls,
                sunsetTime = trail.sunsetTime,
                weatherForecast = trail.weatherForecast,
                difficulty = trail.difficulty,
                distanceKm = trail.distanceKm,
                elevationGain = trail.elevationGain,
                averageInclinePercent = trail.averageInclinePercent,
                estimatedDuration = trail.estimatedDuration,
                imageUrl = trail.imageUrl,
                imageAttribution = trail.imageAttribution,
                imageLicense = trail.imageLicense,
                imageSourcePageUrl = trail.imageSourcePageUrl,
                imageScope = trail.imageScope,
                routeSegments = trail.routeSegments,
                remainingRouteSegments = trail.remainingRouteSegments,
                routeBounds = trail.routeBounds,
                trackingState = trail.trackingState.name,
                progress = trail.progress,
                distanceCompletedKm = trail.distanceCompletedKm,
                remainingDistanceKm = trail.remainingDistanceKm,
                offTrailDistanceKm = trail.offTrailDistanceKm,
                hasLeftStartZone = trail.hasLeftStartZone,
                startedAtEpochMillis = trail.startedAtEpochMillis,
                lastSyncTimestamp = trail.lastSyncTimestamp,
                dailyForecast = trail.dailyForecast
            )
    }
}
