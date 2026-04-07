package com.scouty.app.ui.models

import com.scouty.app.assistant.model.DailyForecastEntry
import com.scouty.app.assistant.model.DeviceContextSnapshot
import com.scouty.app.assistant.model.GearContextItem
import com.scouty.app.assistant.model.TrailContextSnapshot
import com.scouty.app.assistant.model.AssistantRuntimeDebugInfo

import java.util.Calendar

enum class GearNecessity {
    MANDATORY,
    RECOMMENDED,
    CONDITIONAL
}

data class GearItem(
    val id: String,
    val name: String,
    val weight: String,
    val category: String,
    val weightGrams: Int? = null,
    val necessity: GearNecessity = GearNecessity.RECOMMENDED,
    val note: String = "",
    val isPacked: Boolean = false
) {
    val isCritical: Boolean
        get() = necessity == GearNecessity.MANDATORY
}

data class ActiveTrail(
    val name: String,
    val date: Calendar,
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
    val progress: Float = 0f,
    val lastSyncTimestamp: Long? = null,
    val dailyForecast: List<DailyForecastEntry> = emptyList()
)

data class HomeStatus(
    val batteryPercent: Int = 67,
    val batterySafe: Boolean = false,
    val isOnline: Boolean = false,
    val gpsFixed: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val accuracy: Float? = null,
    val locationName: String = "Waiting for fix...",
    val activeTrail: ActiveTrail? = null,
    val gearList: List<GearItem> = emptyList(),
    val userProfile: UserTrailProfile = UserTrailProfile(),
    val routeRecommendations: List<RouteRecommendation> = emptyList(),
    val assistantRuntime: AssistantRuntimeDebugInfo = AssistantRuntimeDebugInfo()
)

fun HomeStatus.toDeviceContextSnapshot(): DeviceContextSnapshot =
    DeviceContextSnapshot(
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        accuracyMeters = accuracy,
        batteryPercent = batteryPercent,
        batterySafe = batterySafe,
        isOnline = isOnline,
        gpsFixed = gpsFixed,
        trail = activeTrail?.let { trail ->
            TrailContextSnapshot(
                name = trail.name,
                localCode = trail.localCode,
                region = trail.region,
                fromName = trail.fromName,
                toName = trail.toName,
                markingLabel = TrailMetadataFormatter.formatTrailMarkers(trail.markingSymbols),
                routeSummary = trail.routeSummary,
                sourceUrls = trail.sourceUrls,
                sunsetTime = trail.sunsetTime,
                weatherForecast = trail.weatherForecast,
                difficulty = trail.difficulty,
                estimatedDuration = trail.estimatedDuration,
                distanceKm = trail.distanceKm,
                elevationGain = trail.elevationGain,
                averageInclinePercent = trail.averageInclinePercent,
                descriptionRo = trail.descriptionRo,
                dailyForecast = trail.dailyForecast
            )
        },
        recommendedGear = gearList
            .sortedBy { it.necessity.ordinal }
            .take(6)
            .map { it.name },
        gearItems = gearList.map { item ->
            GearContextItem(
                id = item.id,
                name = item.name,
                necessity = item.necessity.name,
                isPacked = item.isPacked,
                note = item.note
            )
        }
    )
