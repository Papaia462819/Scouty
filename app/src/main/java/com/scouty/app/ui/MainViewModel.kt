package com.scouty.app.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scouty.app.assistant.data.ChatActionHandler
import com.scouty.app.assistant.data.DeviceContextProvider
import com.scouty.app.assistant.data.KnowledgePackManager
import com.scouty.app.assistant.domain.AssistantRuntimeGraph
import com.scouty.app.assistant.model.DailyForecastEntry
import com.scouty.app.assistant.model.DeviceContextSnapshot
import com.scouty.app.assistant.model.AssistantRuntimeDebugInfo
import com.scouty.app.assistant.domain.ModelManager
import com.scouty.app.assistant.model.GenerationMode
import com.scouty.app.BuildConfig
import com.google.android.gms.location.*
import com.scouty.app.api.MeteoblueLocationResult
import com.scouty.app.api.MeteoblueResponse
import com.scouty.app.data.RouteEnrichmentRepository
import com.scouty.app.data.RouteBounds
import com.scouty.app.data.RouteCoordinate
import com.scouty.app.data.RouteGeometryRepository
import com.scouty.app.data.UserTrailProfileStore
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.scouty.app.api.MeteoblueService
import com.scouty.app.ui.models.ActiveTrail
import com.scouty.app.ui.models.ActiveTrailState
import com.scouty.app.ui.models.CompletedTrailSnapshot
import com.scouty.app.ui.models.GearNecessity
import com.scouty.app.ui.models.GearRecommendationEngine
import com.scouty.app.ui.models.HomeStatus
import com.scouty.app.ui.models.MapCameraSnapshot
import com.scouty.app.ui.models.MapSessionState
import com.scouty.app.ui.models.MapTrailMode
import com.scouty.app.ui.models.NearbyGuideRequest
import com.scouty.app.ui.models.NearbyGuideTarget
import com.scouty.app.ui.models.NearbyGuideType
import com.scouty.app.ui.models.RouteRecommendationEngine
import com.scouty.app.ui.models.TrailPartyComposition
import com.scouty.app.ui.models.TrailCompletionStatus
import com.scouty.app.ui.models.TrailSelectionSnapshot
import com.scouty.app.ui.models.TrailMetadataFormatter
import com.scouty.app.ui.models.UserTrailProfile
import com.scouty.app.ui.models.adaptToTrail
import com.scouty.app.ui.models.toDeviceContextSnapshot
import com.scouty.app.utils.MapPackRegistryManager
import com.scouty.app.utils.SolarCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class MainViewModel(application: Application) : AndroidViewModel(application), DeviceContextProvider, ChatActionHandler {

    private companion object {
        const val TrailStartDepartureKm = 0.12
        const val TrailAutoCompleteMinElapsedMs = 90_000L
    }

    private data class WeatherLookupResult(
        val response: MeteoblueResponse? = null,
        val fallbackLocation: MeteoblueLocationResult? = null,
        val usedFallbackLocation: Boolean = false
    )

    private data class TrailProgressComputation(
        val progressFraction: Float,
        val distanceCompletedKm: Double,
        val remainingDistanceKm: Double,
        val distanceToTrailKm: Double,
        val distanceToStartKm: Double,
        val distanceToEndKm: Double,
        val remainingSegments: List<List<RouteCoordinate>>
    )

    private val meteoblueApiKey = BuildConfig.METEOBLUE_API_KEY
    private val userTrailProfileStore = UserTrailProfileStore(application)
    private val assistantRuntimeGraph = AssistantRuntimeGraph.get(application)
    private val knowledgePackManager: KnowledgePackManager = assistantRuntimeGraph.knowledgePackManager
    private val modelManager: ModelManager = assistantRuntimeGraph.modelManager

    private val _uiState = MutableStateFlow(HomeStatus(userProfile = userTrailProfileStore.load()))
    val uiState: StateFlow<HomeStatus> = _uiState.asStateFlow()
    private val _mapSessionState = MutableStateFlow(MapSessionState())
    val mapSessionState: StateFlow<MapSessionState> = _mapSessionState.asStateFlow()
    private val _deviceContext = MutableStateFlow(_uiState.value.toDeviceContextSnapshot())
    override val deviceContext: StateFlow<DeviceContextSnapshot> = _deviceContext.asStateFlow()
    private var lastRecommendationLocation: Pair<Double, Double>? = null
    private var lastRecommendationRefreshMs: Long = 0L

    private val json = Json { ignoreUnknownKeys = true }
    private val retrofit = Retrofit.Builder()
        .baseUrl(MeteoblueService.BASE_URL)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
    private val meteoblueService = retrofit.create(MeteoblueService::class.java)

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(application)

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation ?: return
            updateLocationData(location)
            checkSmartSync(location)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level != -1 && scale != -1) {
                val pct = (level * 100 / scale.toFloat()).toInt()
                updateUiState {
                    it.copy(
                        batteryPercent = pct,
                        batterySafe = pct < 15
                    )
                }
            }
        }
    }

    init {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(batteryReceiver, filter)
        }
        startLocationUpdates()
        loadDefaultGear()
        refreshOnlineState()
        observeAssistantRuntime()
        refreshAssistantRuntimeStatus()
        warmMapRuntime()
        maybeRefreshRouteRecommendations(force = true)
    }

    private fun loadDefaultGear() {
        updateUiState { it.copy(gearList = emptyList()) }
    }

    private fun buildGearList(
        trail: ActiveTrail?,
        profile: UserTrailProfile,
        previousItems: List<com.scouty.app.ui.models.GearItem> = emptyList()
    ): List<com.scouty.app.ui.models.GearItem> =
        if (trail == null) {
            emptyList()
        } else {
            GearRecommendationEngine.build(
                trail = trail,
                profile = profile,
                previousItems = previousItems
            )
        }

    fun toggleGearItem(itemId: String) {
        updateUiState { currentState ->
            val newList = currentState.gearList.map {
                if (it.id == itemId) it.copy(isPacked = !it.isPacked) else it
            }
            currentState.copy(gearList = newList)
        }
    }

    override fun toggleGearPacked(itemIds: List<String>, packed: Boolean) {
        updateUiState { currentState ->
            val idSet = itemIds.toSet()
            val newList = currentState.gearList.map { item ->
                if (item.id in idSet) item.copy(isPacked = packed) else item
            }
            currentState.copy(gearList = newList)
        }
    }

    fun updateUserProfile(profile: UserTrailProfile) {
        userTrailProfileStore.save(profile)
        updateUiState { currentState ->
            currentState.copy(
                userProfile = profile,
                gearList = buildGearList(
                    trail = currentState.activeTrail,
                    profile = profile,
                    previousItems = currentState.gearList
                )
            )
        }
        maybeRefreshRouteRecommendations(force = true)
    }

    fun selectMapTrail(selection: TrailSelectionSnapshot, showBottomSheet: Boolean = true) {
        _mapSessionState.update { currentState ->
            currentState.copy(
                selectedTrail = selection,
                isBottomSheetVisible = showBottomSheet,
                nearbyGuideRequest = null,
                nearbyGuide = null,
                mode = if (currentState.mode == MapTrailMode.ACTIVE) {
                    MapTrailMode.ACTIVE
                } else {
                    MapTrailMode.BROWSING
                }
            )
        }
    }

    fun showTrailDetails(visible: Boolean) {
        _mapSessionState.update { it.copy(isBottomSheetVisible = visible) }
    }

    fun persistMapCamera(snapshot: MapCameraSnapshot) {
        _mapSessionState.update { it.copy(cameraSnapshot = snapshot) }
    }

    fun orientToTrail(selection: TrailSelectionSnapshot? = _mapSessionState.value.selectedTrail) {
        if (selection == null) return
        _mapSessionState.update { currentState ->
            currentState.copy(
                selectedTrail = selection,
                isBottomSheetVisible = false,
                nearbyGuideRequest = null,
                nearbyGuide = null,
                mode = MapTrailMode.ORIENTED,
                focusRequestToken = System.currentTimeMillis()
            )
        }
    }

    fun focusActiveTrailOnMap() {
        val activeTrail = _uiState.value.activeTrail ?: return
        val selection = activeTrail.toTrailSelectionSnapshot()
        _mapSessionState.update { currentState ->
            currentState.copy(
                selectedTrail = selection,
                isBottomSheetVisible = false,
                nearbyGuideRequest = null,
                nearbyGuide = null,
                mode = if (activeTrail.trackingState == ActiveTrailState.ACTIVE) {
                    MapTrailMode.ACTIVE
                } else {
                    MapTrailMode.ORIENTED
                },
                focusRequestToken = System.currentTimeMillis()
            )
        }
    }

    fun startActiveTrail() {
        val activeTrail = _uiState.value.activeTrail ?: return
        if (activeTrail.trackingState == ActiveTrailState.ACTIVE) {
            _mapSessionState.update {
                it.copy(
                    mode = MapTrailMode.ACTIVE,
                    isBottomSheetVisible = false,
                    focusRequestToken = System.currentTimeMillis()
                )
            }
            return
        }

        updateUiState { currentState ->
            val trail = currentState.activeTrail ?: return@updateUiState currentState
            currentState.copy(
                activeTrail = trail.copy(
                    trackingState = ActiveTrailState.ACTIVE,
                    startedAtEpochMillis = System.currentTimeMillis(),
                    progress = 0f,
                    distanceCompletedKm = 0.0,
                    remainingDistanceKm = trail.distanceKm,
                    hasLeftStartZone = false,
                    remainingRouteSegments = trail.routeSegments
                )
            )
        }
        _mapSessionState.update {
            it.copy(
                mode = MapTrailMode.ACTIVE,
                isBottomSheetVisible = false,
                focusRequestToken = System.currentTimeMillis()
            )
        }
    }

    fun endActiveTrail() {
        completeActiveTrail(manual = true)
    }

    fun recenterActiveTrailOnUser() {
        val activeTrail = _uiState.value.activeTrail ?: return
        if (activeTrail.trackingState != ActiveTrailState.ACTIVE) {
            return
        }
        _mapSessionState.update {
            it.copy(
                mode = MapTrailMode.ACTIVE,
                isBottomSheetVisible = false,
                focusRequestToken = System.currentTimeMillis()
            )
        }
    }

    fun consumeLastCompletedTrail() {
        _mapSessionState.update { currentState ->
            if (currentState.lastCompletedTrail == null) {
                currentState
            } else {
                currentState.copy(lastCompletedTrail = null)
            }
        }
    }

    fun requestNearbyGuide(type: NearbyGuideType) {
        _mapSessionState.update { currentState ->
            currentState.copy(
                isBottomSheetVisible = false,
                selectedTrail = _uiState.value.activeTrail?.let { currentState.selectedTrail } ?: null,
                nearbyGuideRequest = NearbyGuideRequest(type = type),
                nearbyGuide = null,
                mode = if (_uiState.value.activeTrail?.trackingState == ActiveTrailState.ACTIVE) {
                    MapTrailMode.ACTIVE
                } else {
                    MapTrailMode.BROWSING
                },
                focusRequestToken = System.currentTimeMillis()
            )
        }
    }

    fun resolveNearbyGuideTarget(
        type: NearbyGuideType,
        sourceId: String,
        title: String,
        subtitle: String,
        latitude: Double,
        longitude: Double
    ) {
        val currentLatitude = _uiState.value.latitude ?: return
        val currentLongitude = _uiState.value.longitude ?: return
        _mapSessionState.update { currentState ->
            currentState.copy(
                nearbyGuideRequest = null,
                nearbyGuide = NearbyGuideTarget(
                    sourceId = sourceId,
                    type = type,
                    title = title,
                    subtitle = subtitle,
                    latitude = latitude,
                    longitude = longitude,
                    distanceKm = calculateDistance(currentLatitude, currentLongitude, latitude, longitude),
                    bearingDegrees = calculateBearingDegrees(currentLatitude, currentLongitude, latitude, longitude)
                ),
                isBottomSheetVisible = false,
                focusRequestToken = System.currentTimeMillis()
            )
        }
    }

    fun clearNearbyGuide() {
        _mapSessionState.update { currentState ->
            currentState.copy(
                nearbyGuideRequest = null,
                nearbyGuide = null
            )
        }
    }

    fun focusNearbyGuideOnMap() {
        if (_mapSessionState.value.nearbyGuide == null) return
        _mapSessionState.update { currentState ->
            currentState.copy(
                isBottomSheetVisible = false,
                focusRequestToken = System.currentTimeMillis()
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            Log.e("ScoutyGPS", "Error starting updates", e)
        }
    }

    private fun updateLocationData(location: Location) {
        val isOnline = isInternetAvailable()
        updateUiState {
            it.copy(
                isOnline = isOnline,
                gpsFixed = true,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                accuracy = location.accuracy,
                locationName = "Current Location" 
            )
        }
        refreshNearbyGuideMetrics(location)
        updateActiveTrailProgress(location)
        maybeRefreshRouteRecommendations(latitude = location.latitude, longitude = location.longitude)
    }

    private fun refreshNearbyGuideMetrics(location: Location) {
        _mapSessionState.value.nearbyGuide ?: return
        _mapSessionState.update { currentState ->
            val latestGuide = currentState.nearbyGuide ?: return@update currentState
            currentState.copy(
                nearbyGuide = latestGuide.copy(
                    distanceKm = calculateDistance(
                        location.latitude,
                        location.longitude,
                        latestGuide.latitude,
                        latestGuide.longitude
                    ),
                    bearingDegrees = calculateBearingDegrees(
                        location.latitude,
                        location.longitude,
                        latestGuide.latitude,
                        latestGuide.longitude
                    )
                )
            )
        }
    }

    private fun checkSmartSync(currentLocation: Location) {
        val trail = _uiState.value.activeTrail ?: return
        if (!isInternetAvailable()) return

        val now = System.currentTimeMillis()
        val lastSync = trail.lastSyncTimestamp ?: 0L
        
        val diffHours = (trail.date.timeInMillis - now) / (1000 * 60 * 60)
        val distanceKm = calculateDistance(
            currentLocation.latitude, currentLocation.longitude,
            trail.latitude, trail.longitude
        )

        val syncIntervalMs = when {
            distanceKm < 10 -> 30 * 60 * 1000L
            diffHours < 12 -> 1 * 60 * 60 * 1000L
            diffHours < 48 -> 6 * 60 * 60 * 1000L
            else -> 24 * 60 * 60 * 1000L
        }

        if (now - lastSync > syncIntervalMs) {
            fetchWeatherData(
                name = trail.name,
                date = trail.date,
                lat = trail.latitude,
                lon = trail.longitude,
                localCode = trail.localCode,
                region = trail.region,
                descriptionRo = trail.descriptionRo,
                localDescription = trail.localDescription,
                routeSummary = trail.routeSummary,
                fromName = trail.fromName,
                toName = trail.toName,
                markingSymbols = trail.markingSymbols,
                sourceUrls = trail.sourceUrls,
                difficulty = trail.difficulty,
                distanceKm = trail.distanceKm,
                elevationGain = trail.elevationGain,
                estimatedDuration = trail.estimatedDuration,
                imageUrl = trail.imageUrl,
                routeSegments = trail.routeSegments,
                routeBounds = trail.routeBounds,
                imageAttribution = trail.imageAttribution,
                imageLicense = trail.imageLicense,
                imageSourcePageUrl = trail.imageSourcePageUrl,
                imageScope = trail.imageScope
            )
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun calculateBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val startLatRad = Math.toRadians(lat1)
        val endLatRad = Math.toRadians(lat2)
        val deltaLon = Math.toRadians(lon2 - lon1)
        val y = sin(deltaLon) * cos(endLatRad)
        val x = cos(startLatRad) * sin(endLatRad) -
            sin(startLatRad) * cos(endLatRad) * cos(deltaLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun updateActiveTrailProgress(location: Location) {
        val activeTrail = _uiState.value.activeTrail ?: return
        if (activeTrail.trackingState != ActiveTrailState.ACTIVE) {
            return
        }
        if (activeTrail.routeSegments.isEmpty()) {
            return
        }

        val progress = computeTrailProgress(
            routeSegments = activeTrail.routeSegments,
            latitude = location.latitude,
            longitude = location.longitude,
            totalDistanceKm = activeTrail.distanceKm
        ) ?: return

        val hasLeftStartZone = activeTrail.hasLeftStartZone || progress.distanceToStartKm >= TrailStartDepartureKm
        val stabilizedProgressFraction = if (hasLeftStartZone) {
            max(activeTrail.progress, progress.progressFraction)
        } else {
            0f
        }
        val stabilizedCompletedKm = if (hasLeftStartZone) {
            max(activeTrail.distanceCompletedKm, progress.distanceCompletedKm)
        } else {
            0.0
        }
        val stabilizedRemainingKm = (activeTrail.distanceKm - stabilizedCompletedKm).coerceAtLeast(0.0)
        val stabilizedRemainingSegments = if (hasLeftStartZone) {
            trimRouteSegments(
                originalSegments = activeTrail.routeSegments,
                routeDistanceKm = stabilizedCompletedKm
            )
        } else {
            activeTrail.routeSegments
        }

        updateUiState { currentState ->
            val trail = currentState.activeTrail ?: return@updateUiState currentState
            currentState.copy(
                activeTrail = trail.copy(
                    progress = stabilizedProgressFraction,
                    distanceCompletedKm = stabilizedCompletedKm,
                    remainingDistanceKm = stabilizedRemainingKm,
                    offTrailDistanceKm = progress.distanceToTrailKm,
                    hasLeftStartZone = hasLeftStartZone,
                    remainingRouteSegments = stabilizedRemainingSegments
                )
            )
        }

        if (shouldAutoCompleteTrail(
                trail = activeTrail,
                hasLeftStartZone = hasLeftStartZone,
                progressFraction = stabilizedProgressFraction,
                distanceCompletedKm = stabilizedCompletedKm,
                distanceToEndKm = progress.distanceToEndKm
            )
        ) {
            completeActiveTrail(manual = false)
        }
    }

    private fun shouldAutoCompleteTrail(
        trail: ActiveTrail,
        hasLeftStartZone: Boolean,
        progressFraction: Float,
        distanceCompletedKm: Double,
        distanceToEndKm: Double
    ): Boolean {
        if (!hasLeftStartZone) return false

        val elapsedMs = trail.startedAtEpochMillis?.let { System.currentTimeMillis() - it } ?: 0L
        if (elapsedMs < TrailAutoCompleteMinElapsedMs) return false

        val minimumDistanceForCompletion = max(0.45, trail.distanceKm * 0.25)
        if (distanceCompletedKm < minimumDistanceForCompletion) return false

        return progressFraction >= 0.985f ||
            (distanceToEndKm <= 0.12 && progressFraction >= 0.94f)
    }

    private fun completeActiveTrail(manual: Boolean) {
        val activeTrail = _uiState.value.activeTrail ?: return
        val completedAtEpochMillis = System.currentTimeMillis()
        val completionSnapshot = activeTrail.toCompletedTrailSnapshot(
            completedAtEpochMillis = completedAtEpochMillis,
            endedEarly = manual,
            gearReady = isTrailGearReady(_uiState.value.gearList)
        )
        updateUiState { currentState ->
            currentState.copy(
                activeTrail = null,
                gearList = emptyList()
            )
        }
        _mapSessionState.update { currentState ->
            currentState.copy(
                selectedTrail = null,
                isBottomSheetVisible = false,
                mode = MapTrailMode.BROWSING,
                lastCompletedTrail = completionSnapshot
            )
        }
        maybeRefreshRouteRecommendations(force = true)
    }

    private fun computeTrailProgress(
        routeSegments: List<List<RouteCoordinate>>,
        latitude: Double,
        longitude: Double,
        totalDistanceKm: Double
    ): TrailProgressComputation? {
        val flattened = flattenRouteSegments(routeSegments)
        if (flattened.size < 2) {
            return null
        }

        var traversedKm = 0.0
        var bestProjection: ProjectedPoint? = null
        var bestDistanceKm = Double.MAX_VALUE

        flattened.zipWithNext().forEach { (start, end) ->
            val segmentLengthKm = calculateDistance(start.lat, start.lon, end.lat, end.lon)
            if (segmentLengthKm <= 0.0) {
                return@forEach
            }

            val projection = projectPointOntoSegment(
                point = RouteCoordinate(latitude, longitude),
                start = start,
                end = end
            )
            if (projection.distanceKm < bestDistanceKm) {
                bestDistanceKm = projection.distanceKm
                bestProjection = projection.copy(distanceAlongRouteKm = traversedKm + (segmentLengthKm * projection.segmentFraction))
            }
            traversedKm += segmentLengthKm
        }

        val resolvedProjection = bestProjection ?: return null
        val resolvedTotalDistanceKm = totalDistanceKm.takeIf { it > 0.0 } ?: traversedKm
        val completedKm = resolvedProjection.distanceAlongRouteKm.coerceIn(0.0, resolvedTotalDistanceKm)
        val remainingKm = (resolvedTotalDistanceKm - completedKm).coerceAtLeast(0.0)
        val progressFraction = if (resolvedTotalDistanceKm > 0.0) {
            (completedKm / resolvedTotalDistanceKm).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

        return TrailProgressComputation(
            progressFraction = progressFraction,
            distanceCompletedKm = completedKm,
            remainingDistanceKm = remainingKm,
            distanceToTrailKm = resolvedProjection.distanceKm,
            distanceToStartKm = calculateDistance(
                latitude,
                longitude,
                flattened.first().lat,
                flattened.first().lon
            ),
            distanceToEndKm = calculateDistance(
                latitude,
                longitude,
                flattened.last().lat,
                flattened.last().lon
            ),
            remainingSegments = trimRouteSegments(
                originalSegments = routeSegments,
                routeDistanceKm = completedKm
            )
        )
    }

    private fun flattenRouteSegments(routeSegments: List<List<RouteCoordinate>>): List<RouteCoordinate> =
        buildList {
            routeSegments.forEach { segment ->
                if (segment.isEmpty()) return@forEach
                if (isEmpty()) {
                    addAll(segment)
                } else {
                    addAll(segment.drop(1))
                }
            }
        }

    private fun trimRouteSegments(
        originalSegments: List<List<RouteCoordinate>>,
        routeDistanceKm: Double
    ): List<List<RouteCoordinate>> {
        var remainingDistanceToTrim = routeDistanceKm.coerceAtLeast(0.0)
        val remainingSegments = mutableListOf<List<RouteCoordinate>>()

        originalSegments.forEach { segment ->
            if (segment.size < 2) {
                return@forEach
            }
            if (remainingDistanceToTrim <= 0.0) {
                remainingSegments += segment
                return@forEach
            }

            val trimmed = mutableListOf<RouteCoordinate>()
            var carryTrim = remainingDistanceToTrim
            var segmentConsumed = false

            segment.zipWithNext().forEachIndexed { index, (start, end) ->
                val sectionKm = calculateDistance(start.lat, start.lon, end.lat, end.lon)
                if (segmentConsumed) {
                    if (trimmed.isEmpty()) {
                        trimmed += start
                    }
                    trimmed += end
                    return@forEachIndexed
                }

                if (carryTrim >= sectionKm) {
                    carryTrim -= sectionKm
                    if (index == segment.lastIndex - 1) {
                        remainingDistanceToTrim = carryTrim
                    }
                    return@forEachIndexed
                }

                val fraction = if (sectionKm == 0.0) 0.0 else carryTrim / sectionKm
                val newStart = interpolateCoordinate(start, end, fraction)
                trimmed += newStart
                trimmed += end
                segmentConsumed = true
                remainingDistanceToTrim = 0.0
            }

            if (trimmed.size >= 2) {
                remainingSegments += trimmed
            }
        }

        return remainingSegments
    }

    private fun interpolateCoordinate(
        start: RouteCoordinate,
        end: RouteCoordinate,
        fraction: Double
    ): RouteCoordinate =
        RouteCoordinate(
            lat = start.lat + ((end.lat - start.lat) * fraction.coerceIn(0.0, 1.0)),
            lon = start.lon + ((end.lon - start.lon) * fraction.coerceIn(0.0, 1.0))
        )

    private fun projectPointOntoSegment(
        point: RouteCoordinate,
        start: RouteCoordinate,
        end: RouteCoordinate
    ): ProjectedPoint {
        val originLatRad = Math.toRadians((point.lat + start.lat + end.lat) / 3.0)
        val pointX = point.lon * 111.320 * cos(originLatRad)
        val pointY = point.lat * 110.574
        val startX = start.lon * 111.320 * cos(originLatRad)
        val startY = start.lat * 110.574
        val endX = end.lon * 111.320 * cos(originLatRad)
        val endY = end.lat * 110.574
        val dx = endX - startX
        val dy = endY - startY
        if (dx == 0.0 && dy == 0.0) {
            return ProjectedPoint(
                segmentFraction = 0.0,
                distanceKm = sqrt(((pointX - startX) * (pointX - startX)) + ((pointY - startY) * (pointY - startY))),
                distanceAlongRouteKm = 0.0
            )
        }

        val rawFraction = (((pointX - startX) * dx) + ((pointY - startY) * dy)) / ((dx * dx) + (dy * dy))
        val fraction = rawFraction.coerceIn(0.0, 1.0)
        val nearestX = startX + (fraction * dx)
        val nearestY = startY + (fraction * dy)
        val distanceKm = sqrt(((pointX - nearestX) * (pointX - nearestX)) + ((pointY - nearestY) * (pointY - nearestY)))
        return ProjectedPoint(
            segmentFraction = fraction,
            distanceKm = distanceKm,
            distanceAlongRouteKm = 0.0
        )
    }

    private data class ProjectedPoint(
        val segmentFraction: Double,
        val distanceKm: Double,
        val distanceAlongRouteKm: Double
    )

    private suspend fun loadForecastWithFallbacks(lat: Double, lon: Double, asl: Int?): WeatherLookupResult {
        val directResponse = requestForecast(lat = lat, lon = lon, asl = asl)
        if (directResponse.hasForecastData()) {
            return WeatherLookupResult(response = directResponse)
        }

        var firstFallbackPayload: MeteoblueResponse? = null
        var firstFallbackLocation: MeteoblueLocationResult? = null
        searchNearbyWeatherLocations(lat, lon).forEach { location ->
            val fallbackResponse = requestForecast(
                lat = location.lat,
                lon = location.lon,
                asl = location.asl
            )
            if (fallbackResponse == null) {
                return@forEach
            }
            if (fallbackResponse.hasForecastData()) {
                Log.d(
                    "ScoutyAPI",
                    "Weather fallback used for $lat,$lon via ${location.name ?: "nearest point"}"
                )
                return WeatherLookupResult(
                    response = fallbackResponse,
                    fallbackLocation = location,
                    usedFallbackLocation = true
                )
            }
            if (firstFallbackPayload == null) {
                firstFallbackPayload = fallbackResponse
                firstFallbackLocation = location
            }
        }

        return WeatherLookupResult(
            response = directResponse ?: firstFallbackPayload,
            fallbackLocation = firstFallbackLocation,
            usedFallbackLocation = firstFallbackPayload != null
        )
    }

    private suspend fun requestForecast(lat: Double, lon: Double, asl: Int?): MeteoblueResponse? {
        if (meteoblueApiKey.isBlank()) {
            return null
        }
        return runCatching {
            meteoblueService.getForecast(
                lat = lat,
                lon = lon,
                asl = asl,
                apiKey = meteoblueApiKey
            )
        }.onFailure { error ->
            Log.e("ScoutyAPI", "Forecast request failed for $lat,$lon", error)
        }.getOrNull()?.let { response ->
            if (!response.isSuccessful) {
                Log.w("ScoutyAPI", "Forecast request returned HTTP ${response.code()} for $lat,$lon")
                null
            } else {
                response.body()
            }
        }
    }

    private suspend fun searchNearbyWeatherLocations(lat: Double, lon: Double): List<MeteoblueLocationResult> {
        if (meteoblueApiKey.isBlank()) {
            return emptyList()
        }

        val response = runCatching {
            meteoblueService.searchLocations(
                query = "$lat $lon",
                apiKey = meteoblueApiKey
            )
        }.onFailure { error ->
            Log.e("ScoutyAPI", "Location search failed for $lat,$lon", error)
        }.getOrNull() ?: return emptyList()

        if (!response.isSuccessful) {
            Log.w("ScoutyAPI", "Location search returned HTTP ${response.code()} for $lat,$lon")
            return emptyList()
        }

        val results = response.body()?.results.orEmpty()
        val nearbyResults = results.filter { (it.distance ?: Double.MAX_VALUE) <= 40.0 }
        return (if (nearbyResults.isNotEmpty()) nearbyResults else results)
            .filterNot { candidate ->
                val distance = candidate.distance ?: Double.MAX_VALUE
                distance <= 0.1
            }
            .sortedWith(
                compareBy<MeteoblueLocationResult>(
                    { weatherLocationPriority(it) },
                    { it.distance ?: Double.MAX_VALUE }
                )
            )
            .take(6)
    }

    private fun weatherLocationPriority(location: MeteoblueLocationResult): Int =
        when {
            location.featureClass == "P" && (location.population ?: 0) > 0 -> 0
            location.featureClass == "P" -> 1
            location.featureClass == "T" -> 2
            else -> 3
        }

    private fun MeteoblueResponse?.hasForecastData(): Boolean {
        if (this == null) {
            return false
        }
        return current != null ||
            !data1h?.temperature.isNullOrEmpty() ||
            !data1h?.pictocode.isNullOrEmpty() ||
            !dataDay?.temperatureMax.isNullOrEmpty() ||
            !dataDay?.temperatureMin.isNullOrEmpty() ||
            !dataDay?.sunset.isNullOrEmpty()
    }

    private fun buildWeatherSummary(weatherLookup: WeatherLookupResult): String {
        val response = weatherLookup.response
        val temperature = response?.current?.temperature ?: response?.data1h?.temperature?.firstOrNull()
        val pictocode = response?.current?.pictocode ?: response?.data1h?.pictocode?.firstOrNull()
        val suffix = when {
            weatherLookup.usedFallbackLocation && !weatherLookup.fallbackLocation?.name.isNullOrBlank() ->
                " (${weatherLookup.fallbackLocation?.name})"
            weatherLookup.usedFallbackLocation -> " (Nearest point)"
            temperature != null || pictocode != null -> " (Synced)"
            else -> ""
        }

        return when {
            temperature != null && pictocode != null ->
                "${formatForecastTemperature(temperature)}, ${getPictocodeDescription(pictocode)}$suffix"
            temperature != null ->
                "${formatForecastTemperature(temperature)}, Forecast available$suffix"
            pictocode != null ->
                "N/A, ${getPictocodeDescription(pictocode)}$suffix"
            else ->
                "Forecast unavailable"
        }
    }

    private fun formatForecastTemperature(temperature: Double): String =
        String.format(Locale.getDefault(), "%.1f°C", temperature)

    fun setActiveTrail(
        name: String,
        date: Calendar,
        partyComposition: TrailPartyComposition,
        lat: Double,
        lon: Double
    ) {
        setActiveTrail(
            name = name,
            date = date,
            partyComposition = partyComposition,
            lat = lat,
            lon = lon,
            difficulty = "MEDIUM",
            distanceKm = 12.4,
            elevationGain = 1234,
            estimatedDuration = "~6h",
            imageUrl = null,
            routeSegments = emptyList(),
            routeBounds = null,
            recordSelection = true
        )
    }

    fun setActiveTrail(
        name: String,
        date: Calendar,
        partyComposition: TrailPartyComposition = TrailPartyComposition(),
        lat: Double,
        lon: Double,
        difficulty: String,
        distanceKm: Double,
        elevationGain: Int,
        estimatedDuration: String,
        imageUrl: String?,
        routeSegments: List<List<com.scouty.app.data.RouteCoordinate>> = emptyList(),
        routeBounds: com.scouty.app.data.RouteBounds? = null,
        localCode: String? = null,
        region: String? = null,
        descriptionRo: String? = null,
        localDescription: String? = null,
        routeSummary: String? = null,
        fromName: String? = null,
        toName: String? = null,
        markingSymbols: List<String> = emptyList(),
        sourceUrls: List<String> = emptyList(),
        imageAttribution: String? = null,
        imageLicense: String? = null,
        imageSourcePageUrl: String? = null,
        imageScope: String? = null,
        recordSelection: Boolean = true
    ) {
        fetchWeatherData(
            name = name,
            date = date,
            partyComposition = partyComposition,
            lat = lat,
            lon = lon,
            localCode = localCode,
            region = region,
            descriptionRo = descriptionRo,
            localDescription = localDescription,
            routeSummary = routeSummary,
            fromName = fromName,
            toName = toName,
            markingSymbols = markingSymbols,
            sourceUrls = sourceUrls,
            difficulty = difficulty,
            distanceKm = distanceKm,
            elevationGain = elevationGain,
            estimatedDuration = estimatedDuration,
            imageUrl = imageUrl,
            routeSegments = routeSegments,
            routeBounds = routeBounds,
            imageAttribution = imageAttribution,
            imageLicense = imageLicense,
            imageSourcePageUrl = imageSourcePageUrl,
            imageScope = imageScope,
            recordSelection = recordSelection
        )
    }

    private fun fetchWeatherData(
        name: String,
        date: Calendar,
        partyComposition: TrailPartyComposition = TrailPartyComposition(),
        lat: Double,
        lon: Double,
        localCode: String? = null,
        region: String? = null,
        descriptionRo: String? = null,
        localDescription: String? = null,
        routeSummary: String? = null,
        fromName: String? = null,
        toName: String? = null,
        markingSymbols: List<String> = emptyList(),
        sourceUrls: List<String> = emptyList(),
        difficulty: String,
        distanceKm: Double,
        elevationGain: Int,
        estimatedDuration: String,
        imageUrl: String?,
        routeSegments: List<List<com.scouty.app.data.RouteCoordinate>> = emptyList(),
        routeBounds: com.scouty.app.data.RouteBounds? = null,
        imageAttribution: String? = null,
        imageLicense: String? = null,
        imageSourcePageUrl: String? = null,
        imageScope: String? = null,
        recordSelection: Boolean = false
    ) {
        viewModelScope.launch {
            var sunsetStr = "N/A"
            var weatherInfo = "Unknown (Offline)"
            var syncTime: Long? = null
            var weatherResponse: com.scouty.app.api.MeteoblueResponse? = null
            refreshOnlineState()
            weatherInfo = if (isInternetAvailable()) "Forecast unavailable" else "Unknown (Offline)"

            if (isInternetAvailable() && meteoblueApiKey.isNotBlank()) {
                try {
                    val weatherLookup = loadForecastWithFallbacks(
                        lat = lat,
                        lon = lon,
                        asl = _uiState.value.altitude?.toInt()
                    )
                    val data = weatherLookup.response
                    weatherResponse = data
                    weatherInfo = buildWeatherSummary(weatherLookup)

                    if (data.hasForecastData()) {
                        sunsetStr = data?.dataDay?.sunset?.firstOrNull()?.let { it.substringAfter(" ") } ?: sunsetStr
                        syncTime = System.currentTimeMillis()
                        Log.d("ScoutyAPI", "Weather Backup Updated for $name")
                    }
                } catch (e: Exception) {
                    Log.e("ScoutyAPI", "Sync failed", e)
                }
            }

            if (sunsetStr == "N/A") {
                val sunset = SolarCalculator.getSunsetTime(lat, lon, date)
                sunsetStr = sunset?.let {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(it.time)
                } ?: "N/A"
            }

            val markerLabel = TrailMetadataFormatter.formatTrailMarkers(markingSymbols)
            val resolvedRouteSummary = routeSummary ?: TrailMetadataFormatter.buildRouteSummary(
                durationText = estimatedDuration,
                elevationGain = elevationGain,
                difficulty = com.scouty.app.ui.models.TrailDifficultyRank.from(difficulty),
                markerLabel = markerLabel,
                fromName = fromName,
                toName = toName
            )

            val dailyForecast = if (weatherResponse != null) {
                buildDailyForecast(weatherResponse)
            } else {
                _uiState.value.activeTrail?.dailyForecast.orEmpty()
            }

            val trail = ActiveTrail(
                name = name,
                date = date,
                partyComposition = partyComposition,
                latitude = lat,
                longitude = lon,
                localCode = localCode,
                region = region,
                descriptionRo = descriptionRo,
                localDescription = localDescription,
                routeSummary = resolvedRouteSummary,
                fromName = fromName,
                toName = toName,
                markingSymbols = markingSymbols,
                sourceUrls = sourceUrls,
                sunsetTime = sunsetStr,
                weatherForecast = weatherInfo,
                lastSyncTimestamp = syncTime ?: _uiState.value.activeTrail?.lastSyncTimestamp,
                difficulty = difficulty,
                distanceKm = distanceKm,
                elevationGain = elevationGain,
                averageInclinePercent = calculateAverageInclinePercent(distanceKm, elevationGain),
                estimatedDuration = estimatedDuration,
                imageUrl = imageUrl,
                routeSegments = routeSegments,
                remainingRouteSegments = routeSegments,
                routeBounds = routeBounds,
                imageAttribution = imageAttribution,
                imageLicense = imageLicense,
                imageSourcePageUrl = imageSourcePageUrl,
                imageScope = imageScope,
                dailyForecast = dailyForecast
            )

            val profileForTrail = if (recordSelection) {
                _uiState.value.userProfile.adaptToTrail(trail).also { adaptedProfile ->
                    userTrailProfileStore.save(adaptedProfile)
                }
            } else {
                _uiState.value.userProfile
            }

            updateUiState { currentState ->
                val updatedGear = buildGearList(
                    trail = trail,
                    profile = profileForTrail,
                    previousItems = currentState.gearList
                )
                currentState.copy(
                    activeTrail = trail,
                    gearList = updatedGear,
                    userProfile = profileForTrail
                )
            }
            _mapSessionState.update { currentState ->
                currentState.copy(
                    selectedTrail = trail.toTrailSelectionSnapshot(),
                    isBottomSheetVisible = false,
                    mode = MapTrailMode.ORIENTED,
                    focusRequestToken = System.currentTimeMillis()
                )
            }
            maybeRefreshRouteRecommendations(force = true, latitude = lat, longitude = lon)
        }
    }

    private fun getPictocodeDescription(code: Int?): String {
        return when(code) {
            1 -> "Clear sky"
            2 -> "Mostly clear"
            3 -> "Partly cloudy"
            4 -> "Overcast"
            5 -> "Fog"
            6 -> "Drizzle"
            11 -> "Rain"
            14 -> "Thunderstorm"
            else -> "Cloudy"
        }
    }

    private fun isTrailGearReady(items: List<com.scouty.app.ui.models.GearItem>): Boolean {
        val mandatoryItems = items.filter { it.necessity == GearNecessity.MANDATORY }
        return mandatoryItems.isNotEmpty() && mandatoryItems.all { it.isPacked }
    }

    private fun ActiveTrail.toCompletedTrailSnapshot(
        completedAtEpochMillis: Long,
        endedEarly: Boolean,
        gearReady: Boolean
    ): CompletedTrailSnapshot {
        val progressFraction = progress.coerceIn(0f, 1f)
        val recordedDistanceKm = when {
            endedEarly && distanceCompletedKm > 0.0 -> distanceCompletedKm
            !endedEarly && distanceKm > 0.0 -> distanceKm
            distanceCompletedKm > 0.0 -> distanceCompletedKm
            else -> distanceKm
        }
        val recordedElevationGainM = when {
            endedEarly -> (elevationGain * progressFraction).roundToInt().coerceAtLeast(0)
            else -> elevationGain.coerceAtLeast(0)
        }
        val durationText = startedAtEpochMillis?.let { startedAt ->
            formatElapsedDuration(completedAtEpochMillis - startedAt)
        } ?: estimatedDuration
        return CompletedTrailSnapshot(
            id = listOfNotNull(localCode, completedAtEpochMillis.toString()).joinToString(":"),
            name = name,
            region = region?.takeIf { it.isNotBlank() } ?: "Unknown region",
            localCode = localCode,
            completedAtEpochMillis = completedAtEpochMillis,
            distanceKm = recordedDistanceKm.coerceAtLeast(0.0),
            elevationGainM = recordedElevationGainM,
            durationText = durationText,
            difficulty = difficulty,
            imageUrl = imageUrl,
            gearReady = gearReady,
            status = if (endedEarly) {
                TrailCompletionStatus.ENDED_EARLY
            } else {
                TrailCompletionStatus.COMPLETED
            }
        )
    }

    private fun formatElapsedDuration(elapsedMillis: Long): String {
        val totalMinutes = (elapsedMillis / 60_000L).coerceAtLeast(1L)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours <= 0L -> "${minutes}m"
            minutes == 0L -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }

    private fun buildDailyForecast(response: com.scouty.app.api.MeteoblueResponse?): List<DailyForecastEntry> {
        val dayData = response?.dataDay ?: return emptyList()
        val times = dayData.time
        return times.mapIndexedNotNull { index, dateStr ->
            val pictocode = dayData.precipitationProbability?.getOrNull(index)
            DailyForecastEntry(
                date = dateStr,
                temperatureMax = dayData.temperatureMax?.getOrNull(index),
                temperatureMin = dayData.temperatureMin?.getOrNull(index),
                precipitationProbability = dayData.precipitationProbability?.getOrNull(index),
                description = response.data1h?.pictocode?.let { hourly ->
                    val dayStartIndex = index * 24
                    val midDayCode = hourly.getOrNull(dayStartIndex + 12)
                        ?: hourly.getOrNull(dayStartIndex + 6)
                        ?: hourly.getOrNull(dayStartIndex)
                    midDayCode?.let(::getPictocodeDescription)
                } ?: getPictocodeDescription(null),
                sunrise = dayData.sunrise?.getOrNull(index),
                sunset = dayData.sunset?.getOrNull(index)
            )
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    private fun calculateAverageInclinePercent(distanceKm: Double, elevationGain: Int): Double {
        if (distanceKm <= 0.0 || elevationGain <= 0) {
            return 0.0
        }
        return (elevationGain / (distanceKm * 1000.0)) * 100.0
    }

    private fun refreshOnlineState() {
        val online = isInternetAvailable()
        updateUiState { it.copy(isOnline = online) }
    }

    private fun refreshAssistantRuntimeStatus() {
        viewModelScope.launch {
            knowledgePackManager.ensureReady()
            modelManager.refreshStatus()
            modelManager.ensureLoaded()
        }
    }

    private fun warmMapRuntime() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                MapPackRegistryManager.load(getApplication())
            }.onFailure { error ->
                Log.w("ScoutyMap", "Failed to warm bundled map packs", error)
            }
        }
    }

    private fun observeAssistantRuntime() {
        viewModelScope.launch {
            combine(knowledgePackManager.status, modelManager.status) { packStatus, modelStatus ->
                AssistantRuntimeDebugInfo(
                    knowledgePackStatus = packStatus,
                    modelStatus = modelStatus,
                    generationMode = if (modelStatus.canGenerateLocally) {
                        GenerationMode.LOCAL_LLM
                    } else {
                        GenerationMode.FALLBACK_STRUCTURED
                    }
                )
            }.collect { runtimeDebugInfo ->
                updateUiState { it.copy(assistantRuntime = runtimeDebugInfo) }
            }
        }
    }

    private fun maybeRefreshRouteRecommendations(
        force: Boolean = false,
        latitude: Double? = _uiState.value.latitude,
        longitude: Double? = _uiState.value.longitude
    ) {
        val now = System.currentTimeMillis()
        if (!force && latitude != null && longitude != null) {
            val previousLocation = lastRecommendationLocation
            val recentlyUpdated = now - lastRecommendationRefreshMs < 45_000
            val barelyMoved = previousLocation?.let { previous ->
                calculateDistance(previous.first, previous.second, latitude, longitude) < 2.0
            } ?: false
            if (recentlyUpdated && barelyMoved) {
                return
            }
        }

        if (latitude != null && longitude != null) {
            lastRecommendationLocation = latitude to longitude
        }
        lastRecommendationRefreshMs = now

        viewModelScope.launch {
            val catalog = RouteEnrichmentRepository.load(getApplication())
            val geometryIndex = RouteGeometryRepository.load(getApplication())
            val currentState = _uiState.value
            val recommendations = RouteRecommendationEngine.recommend(
                profile = currentState.userProfile,
                catalog = catalog,
                geometryIndex = geometryIndex,
                latitude = currentState.latitude,
                longitude = currentState.longitude,
                activeTrail = currentState.activeTrail
            )
            updateUiState { it.copy(routeRecommendations = recommendations) }
        }
    }

    private fun updateUiState(transform: (HomeStatus) -> HomeStatus) {
        _uiState.update { currentState ->
            transform(currentState).also { updated ->
                _deviceContext.value = updated.toDeviceContextSnapshot()
            }
        }
    }

    private fun ActiveTrail.toTrailSelectionSnapshot(
        selectionToken: Long = System.currentTimeMillis()
    ): TrailSelectionSnapshot =
        TrailSelectionSnapshot(
            name = name,
            difficulty = runCatching {
                com.scouty.app.utils.TrailDifficulty.valueOf(difficulty)
            }.getOrDefault(com.scouty.app.utils.TrailDifficulty.MEDIUM),
            latitude = latitude,
            longitude = longitude,
            distanceKm = distanceKm,
            elevationGain = elevationGain,
            estimatedDuration = estimatedDuration,
            selectionToken = selectionToken,
            localCode = localCode,
            region = region,
            descriptionRo = descriptionRo,
            localDescription = localDescription,
            routeSummary = routeSummary,
            fromName = fromName,
            toName = toName,
            markingSymbols = markingSymbols,
            sourceUrls = sourceUrls,
            imageUrl = imageUrl,
            imageAttribution = imageAttribution,
            imageLicense = imageLicense,
            imageSourcePageUrl = imageSourcePageUrl,
            imageScope = imageScope,
            highlightSegments = routeSegments,
            highlightBounds = routeBounds
        )

    override fun onCleared() {
        super.onCleared()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        try {
            getApplication<Application>().unregisterReceiver(batteryReceiver)
        } catch (e: Exception) { }
    }
}
