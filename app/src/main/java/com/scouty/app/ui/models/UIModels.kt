package com.scouty.app.ui.models

import com.scouty.app.assistant.model.DailyForecastEntry
import com.scouty.app.assistant.model.DeviceContextSnapshot
import com.scouty.app.assistant.model.GearContextItem
import com.scouty.app.assistant.model.TrailContextSnapshot
import com.scouty.app.assistant.model.AssistantRuntimeDebugInfo
import com.scouty.app.data.RouteBounds
import com.scouty.app.data.RouteCoordinate
import com.scouty.app.utils.TrailDifficulty

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
    val remainingRouteSegments: List<List<RouteCoordinate>> = routeSegments,
    val routeBounds: RouteBounds? = null,
    val trackingState: ActiveTrailState = ActiveTrailState.PLANNED,
    val progress: Float = 0f,
    val distanceCompletedKm: Double = 0.0,
    val remainingDistanceKm: Double = distanceKm,
    val offTrailDistanceKm: Double = 0.0,
    val hasLeftStartZone: Boolean = false,
    val startedAtEpochMillis: Long? = null,
    val lastSyncTimestamp: Long? = null,
    val dailyForecast: List<DailyForecastEntry> = emptyList()
)

enum class ActiveTrailState {
    PLANNED,
    ACTIVE
}

enum class MapTrailMode {
    BROWSING,
    ORIENTED,
    ACTIVE
}

enum class TrailCompletionStatus {
    COMPLETED,
    ENDED_EARLY
}

data class MapCameraSnapshot(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val bearing: Double = 0.0
)

data class TrailSelectionSnapshot(
    val name: String,
    val difficulty: TrailDifficulty,
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double,
    val elevationGain: Int,
    val estimatedDuration: String,
    val selectionToken: Long = System.currentTimeMillis(),
    val localCode: String? = null,
    val region: String? = null,
    val descriptionRo: String? = null,
    val localDescription: String? = null,
    val routeSummary: String? = null,
    val fromName: String? = null,
    val toName: String? = null,
    val markingSymbols: List<String> = emptyList(),
    val sourceUrls: List<String> = emptyList(),
    val imageUrl: String? = null,
    val imageAttribution: String? = null,
    val imageLicense: String? = null,
    val imageSourcePageUrl: String? = null,
    val imageScope: String? = null,
    val highlightSegments: List<List<RouteCoordinate>> = emptyList(),
    val highlightBounds: RouteBounds? = null
)

data class CompletedTrailSnapshot(
    val id: String,
    val name: String,
    val region: String,
    val localCode: String? = null,
    val completedAtEpochMillis: Long,
    val distanceKm: Double,
    val elevationGainM: Int,
    val durationText: String,
    val difficulty: String,
    val imageUrl: String? = null,
    val gearReady: Boolean,
    val status: TrailCompletionStatus
)

data class MapSessionState(
    val selectedTrail: TrailSelectionSnapshot? = null,
    val isBottomSheetVisible: Boolean = false,
    val mode: MapTrailMode = MapTrailMode.BROWSING,
    val focusRequestToken: Long = 0L,
    val cameraSnapshot: MapCameraSnapshot? = null,
    val lastCompletedTrail: CompletedTrailSnapshot? = null
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
