package com.scouty.app.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
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
import com.scouty.app.assistant.model.AssistantHourlyWeather
import com.scouty.app.assistant.model.AssistantWeatherRequest
import com.scouty.app.assistant.model.AssistantWeatherResult
import com.scouty.app.assistant.domain.ModelManager
import com.scouty.app.assistant.model.GenerationMode
import com.scouty.app.assistant.model.GearItemDraft
import com.scouty.app.assistant.model.GearItemUpdate
import com.scouty.app.assistant.model.TrailHistoryEntry
import com.scouty.app.BuildConfig
import com.google.android.gms.location.*
import com.scouty.app.api.MeteoblueLocationResult
import com.scouty.app.api.MeteoblueResponse
import com.scouty.app.data.ActiveTrailStore
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
import com.scouty.app.ui.models.GearItem
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
import com.scouty.app.profile.ProfileTrailRecord
import com.scouty.app.utils.MapPackRepository
import com.scouty.app.utils.MapPackRegistryManager
import com.scouty.app.utils.OfflineWaterSourceRepository
import com.scouty.app.utils.SolarCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class MainViewModel(application: Application) : AndroidViewModel(application), DeviceContextProvider, ChatActionHandler {

    private companion object {
        const val TrailStartDepartureKm = 0.12
        const val TrailAutoCompleteMinElapsedMs = 90_000L
        const val ActiveTrailProgressPersistIntervalMs = 30_000L
        const val ForecastHorizonDays = 14L
        const val RecentHistoryDays = 4L
        const val WeatherDailyRefreshMs = 24 * 60 * 60 * 1000L
        const val WeatherNearTrailRefreshMs = 30 * 60 * 1000L
        const val WeatherSameDayRefreshMs = 60 * 60 * 1000L
        const val WeatherTwoDayRefreshMs = 6 * 60 * 60 * 1000L
        const val UnavailableWeatherLabel = "Indisponibil"
    }

    private data class WeatherLookupResult(
        val response: MeteoblueResponse? = null,
        val fallbackLocation: MeteoblueLocationResult? = null,
        val usedFallbackLocation: Boolean = false
    )

    private data class WeatherRequestWindow(
        val targetDate: String,
        val daysFromToday: Long,
        val forecastDays: Int?,
        val historyDays: Int?,
        val canQuery: Boolean
    )

    private data class SelectedDateWeather(
        val summary: String,
        val sunsetTime: String?,
        val dailyForecast: List<DailyForecastEntry>
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
    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val userTrailProfileStore = UserTrailProfileStore(application)
    private val activeTrailStore = ActiveTrailStore(application)
    private val assistantRuntimeGraph = AssistantRuntimeGraph.get(application)
    private val knowledgePackManager: KnowledgePackManager = assistantRuntimeGraph.knowledgePackManager
    private val modelManager: ModelManager = assistantRuntimeGraph.modelManager
    private val mapPackRepository = MapPackRepository.get(application)
    private val waterSourceRepository = OfflineWaterSourceRepository(application)

    private val _uiState = MutableStateFlow(HomeStatus(userProfile = userTrailProfileStore.load()))
    val uiState: StateFlow<HomeStatus> = _uiState.asStateFlow()
    private val _userTrailProfileEvents = MutableSharedFlow<UserTrailProfile>(extraBufferCapacity = 1)
    val userTrailProfileEvents: SharedFlow<UserTrailProfile> = _userTrailProfileEvents.asSharedFlow()
    private val _mapSessionState = MutableStateFlow(MapSessionState())
    val mapSessionState: StateFlow<MapSessionState> = _mapSessionState.asStateFlow()
    private val _deviceContext = MutableStateFlow(_uiState.value.toDeviceContextSnapshot())
    override val deviceContext: StateFlow<DeviceContextSnapshot> = _deviceContext.asStateFlow()
    private var lastRecommendationLocation: Pair<Double, Double>? = null
    private var lastRecommendationRefreshMs: Long = 0L
    private var lastActiveTrailPersistMs: Long = 0L
    private var lastWaterContextAnchor: Pair<Double, Double>? = null

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
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshOnlineState()
            retryActiveTrailMapPack()
            refreshActiveTrailWeatherIfNeeded()
        }

        override fun onLost(network: Network) {
            refreshOnlineState()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            refreshOnlineState()
            retryActiveTrailMapPack()
            refreshActiveTrailWeatherIfNeeded()
        }
    }
    private var networkCallbackRegistered = false

    init {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(batteryReceiver, filter)
        }
        startLocationUpdates()
        loadDefaultGear()
        registerNetworkCallback()
        refreshOnlineState()
        restoreActiveTrail()
        observeAssistantRuntime()
        refreshAssistantRuntimeStatus()
        warmMapRuntime()
        maybeRefreshRouteRecommendations(force = true)
        refreshNearbyWaterContextForCurrentState(force = true)
    }

    private fun loadDefaultGear() {
        updateUiState { it.copy(gearList = emptyList()) }
    }

    private fun buildGearList(
        trail: ActiveTrail?,
        profile: UserTrailProfile,
        previousItems: List<GearItem> = emptyList()
    ): List<GearItem> =
        if (trail == null) {
            emptyList()
        } else {
            GearRecommendationEngine.build(
                trail = trail,
                profile = profile,
                previousItems = previousItems
            )
        }

    private fun restoreActiveTrail() {
        val restoredTrail = activeTrailStore.load() ?: return
        updateUiState { currentState ->
            currentState.copy(
                activeTrail = restoredTrail,
                gearList = buildGearList(
                    trail = restoredTrail,
                    profile = currentState.userProfile,
                    previousItems = currentState.gearList
                )
            )
        }
        _mapSessionState.update { currentState ->
            currentState.copy(
                selectedTrail = restoredTrail.toTrailSelectionSnapshot(),
                mode = if (restoredTrail.trackingState == ActiveTrailState.ACTIVE) {
                    MapTrailMode.ACTIVE
                } else {
                    MapTrailMode.ORIENTED
                }
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            prepareOfflineMapForTrail(restoredTrail)
        }
        refreshNearbyWaterContextForCurrentState(force = true)
        refreshActiveTrailWeatherIfNeeded()
    }

    private fun persistActiveTrail(trail: ActiveTrail, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastActiveTrailPersistMs < ActiveTrailProgressPersistIntervalMs) {
            return
        }
        lastActiveTrailPersistMs = now
        activeTrailStore.save(trail)
    }

    private fun findMatchingActiveTrail(
        name: String,
        date: Calendar,
        lat: Double,
        lon: Double,
        localCode: String?
    ): ActiveTrail? {
        val trail = _uiState.value.activeTrail ?: return null
        if (trailLocalDate(trail.date) != trailLocalDate(date)) {
            return null
        }
        val sameCode = !localCode.isNullOrBlank() && trail.localCode == localCode
        val sameRoute = trail.name == name &&
            abs(trail.latitude - lat) < 0.0001 &&
            abs(trail.longitude - lon) < 0.0001
        return if (sameCode || sameRoute) trail else null
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

    override fun addGearItems(items: List<GearItemDraft>) {
        if (items.isEmpty()) return
        updateUiState { currentState ->
            val existingIds = currentState.gearList.map { it.id }.toSet()
            val additions = items
                .filterNot { it.id in existingIds }
                .map { draft ->
                    GearItem(
                        id = draft.id,
                        name = draft.name,
                        weight = "",
                        category = draft.category.ifBlank { "Custom" },
                        necessity = parseGearNecessity(draft.necessity),
                        note = draft.note,
                        isPacked = draft.packed
                    )
                }
            currentState.copy(gearList = currentState.gearList + additions)
        }
    }

    override fun removeGearItems(itemIds: List<String>) {
        if (itemIds.isEmpty()) return
        updateUiState { currentState ->
            val ids = itemIds.toSet()
            currentState.copy(gearList = currentState.gearList.filterNot { it.id in ids })
        }
    }

    override fun updateGearItems(updates: List<GearItemUpdate>) {
        if (updates.isEmpty()) return
        updateUiState { currentState ->
            val byId = updates.associateBy { it.itemId }
            val updated = currentState.gearList.map { item ->
                val update = byId[item.id] ?: return@map item
                item.copy(
                    name = update.name ?: item.name,
                    category = update.category ?: item.category,
                    necessity = update.necessity?.let(::parseGearNecessity) ?: item.necessity,
                    note = update.note ?: item.note,
                    isPacked = update.packed ?: item.isPacked
                )
            }
            currentState.copy(gearList = updated)
        }
    }

    private fun parseGearNecessity(raw: String): GearNecessity =
        when (raw.uppercase(Locale.ROOT)) {
            "MANDATORY", "OBLIGATORIU" -> GearNecessity.MANDATORY
            "CONDITIONAL", "OPTIONAL" -> GearNecessity.CONDITIONAL
            else -> GearNecessity.RECOMMENDED
        }

    fun updateUserProfile(profile: UserTrailProfile) {
        saveUserTrailProfile(profile, notifyFirebase = true)
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

    fun replaceUserProfileFromFirebase(profile: UserTrailProfile) {
        saveUserTrailProfile(profile, notifyFirebase = false)
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

    fun updateTrailHistory(history: List<ProfileTrailRecord>) {
        val mappedHistory = history.map { record ->
            TrailHistoryEntry(
                name = record.name,
                region = record.region,
                completedAtEpochMillis = record.completedAtEpochMillis,
                distanceKm = record.distanceKm,
                elevationGainM = record.elevationGainM,
                durationText = record.durationText,
                difficulty = record.difficulty,
                outcome = record.outcome.name
            )
        }
        updateUiState { it.copy(trailHistory = mappedHistory) }
    }

    private fun saveUserTrailProfile(profile: UserTrailProfile, notifyFirebase: Boolean) {
        userTrailProfileStore.save(profile)
        if (notifyFirebase) {
            _userTrailProfileEvents.tryEmit(profile)
        }
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

        var updatedTrail: ActiveTrail? = null
        updateUiState { currentState ->
            val trail = currentState.activeTrail ?: return@updateUiState currentState
            val nextTrail = trail.copy(
                trackingState = ActiveTrailState.ACTIVE,
                startedAtEpochMillis = System.currentTimeMillis(),
                progress = 0f,
                distanceCompletedKm = 0.0,
                remainingDistanceKm = trail.distanceKm,
                hasLeftStartZone = false,
                remainingRouteSegments = trail.routeSegments
            )
            updatedTrail = nextTrail
            currentState.copy(
                activeTrail = nextTrail
            )
        }
        updatedTrail?.let { persistActiveTrail(it, force = true) }
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
                locationName = "Locația curentă"
            )
        }
        refreshNearbyGuideMetrics(location)
        refreshNearbyWaterContext(latitude = location.latitude, longitude = location.longitude)
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

    private fun refreshNearbyWaterContextForCurrentState(force: Boolean = false) {
        val state = _uiState.value
        refreshNearbyWaterContext(
            latitude = state.latitude ?: state.activeTrail?.latitude,
            longitude = state.longitude ?: state.activeTrail?.longitude,
            force = force
        )
    }

    private fun refreshNearbyWaterContext(
        latitude: Double?,
        longitude: Double?,
        force: Boolean = false
    ) {
        if (latitude == null || longitude == null) {
            if (_uiState.value.nearbyWaterSources.isNotEmpty()) {
                updateUiState { it.copy(nearbyWaterSources = emptyList()) }
            }
            lastWaterContextAnchor = null
            return
        }

        val previousAnchor = lastWaterContextAnchor
        if (!force && previousAnchor != null &&
            calculateDistance(previousAnchor.first, previousAnchor.second, latitude, longitude) < 0.2
        ) {
            return
        }
        lastWaterContextAnchor = latitude to longitude

        viewModelScope.launch(Dispatchers.IO) {
            val nearestSources = waterSourceRepository.nearest(
                latitude = latitude,
                longitude = longitude
            )
            updateUiState { currentState ->
                val currentLatitude = currentState.latitude ?: currentState.activeTrail?.latitude
                val currentLongitude = currentState.longitude ?: currentState.activeTrail?.longitude
                if (currentLatitude == null || currentLongitude == null) {
                    currentState.copy(nearbyWaterSources = emptyList())
                } else if (calculateDistance(currentLatitude, currentLongitude, latitude, longitude) > 0.5) {
                    currentState
                } else {
                    currentState.copy(nearbyWaterSources = nearestSources)
                }
            }
        }
    }

    private fun checkSmartSync(currentLocation: Location) {
        val trail = _uiState.value.activeTrail ?: return
        if (!isInternetAvailable()) return
        if (!resolveWeatherRequestWindow(trail.date).canQuery) return

        val now = System.currentTimeMillis()
        val diffHours = (trail.date.timeInMillis - now) / (1000 * 60 * 60)
        val distanceKm = calculateDistance(
            currentLocation.latitude, currentLocation.longitude,
            trail.latitude, trail.longitude
        )

        val syncIntervalMs = weatherRefreshIntervalMs(diffHours = diffHours, distanceKm = distanceKm)
        if (now - (trail.lastSyncTimestamp ?: 0L) > syncIntervalMs) {
            refreshWeatherForTrail(trail)
        }
    }

    private fun refreshActiveTrailWeatherIfNeeded(force: Boolean = false) {
        val trail = _uiState.value.activeTrail ?: return
        if (!force && !shouldRefreshWeather(trail)) {
            return
        }
        refreshWeatherForTrail(trail)
    }

    private fun shouldRefreshWeather(trail: ActiveTrail, now: Long = System.currentTimeMillis()): Boolean {
        if (!isInternetAvailable() || meteoblueApiKey.isBlank()) {
            return false
        }
        if (!resolveWeatherRequestWindow(trail.date).canQuery) {
            return false
        }
        val lastSync = trail.lastSyncTimestamp ?: return true
        val diffHours = (trail.date.timeInMillis - now) / (1000 * 60 * 60)
        return now - lastSync > weatherRefreshIntervalMs(diffHours = diffHours)
    }

    private fun weatherRefreshIntervalMs(diffHours: Long, distanceKm: Double? = null): Long =
        when {
            distanceKm != null && distanceKm < 10 -> WeatherNearTrailRefreshMs
            diffHours < 12 -> WeatherSameDayRefreshMs
            diffHours < 48 -> WeatherTwoDayRefreshMs
            else -> WeatherDailyRefreshMs
        }

    private fun refreshWeatherForTrail(trail: ActiveTrail) {
        fetchWeatherData(
            name = trail.name,
            date = trail.date,
            partyComposition = trail.partyComposition,
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

        var updatedTrail: ActiveTrail? = null
        updateUiState { currentState ->
            val trail = currentState.activeTrail ?: return@updateUiState currentState
            val nextTrail = trail.copy(
                progress = stabilizedProgressFraction,
                distanceCompletedKm = stabilizedCompletedKm,
                remainingDistanceKm = stabilizedRemainingKm,
                offTrailDistanceKm = progress.distanceToTrailKm,
                hasLeftStartZone = hasLeftStartZone,
                remainingRouteSegments = stabilizedRemainingSegments
            )
            updatedTrail = nextTrail
            currentState.copy(
                activeTrail = nextTrail
            )
        }
        updatedTrail?.let { persistActiveTrail(it) }

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
        activeTrailStore.clear()
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
        releaseOfflineMapForCurrentTrail()
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

    private suspend fun loadForecastWithFallbacks(
        lat: Double,
        lon: Double,
        asl: Int?,
        forecastDays: Int? = null,
        historyDays: Int? = null
    ): WeatherLookupResult {
        val directResponse = requestForecast(
            lat = lat,
            lon = lon,
            asl = asl,
            forecastDays = forecastDays,
            historyDays = historyDays
        )
        if (directResponse.hasForecastData()) {
            return WeatherLookupResult(response = directResponse)
        }

        var firstFallbackPayload: MeteoblueResponse? = null
        var firstFallbackLocation: MeteoblueLocationResult? = null
        searchNearbyWeatherLocations(lat, lon).forEach { location ->
            val fallbackResponse = requestForecast(
                lat = location.lat,
                lon = location.lon,
                asl = location.asl,
                forecastDays = forecastDays,
                historyDays = historyDays
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

    override suspend fun queryWeather(request: AssistantWeatherRequest): AssistantWeatherResult {
        if (!isInternetAvailable()) {
            return AssistantWeatherResult(
                available = false,
                isLive = false,
                locationLabel = request.locationLabel,
                summary = if (request.preferredLanguage == "ro") {
                    "Nu pot verifica vremea în timp real fără conexiune la internet."
                } else {
                    "Nu pot verifica vremea în timp real fără conexiune la internet."
                },
                errorMessage = "fără internet"
            )
        }
        if (meteoblueApiKey.isBlank()) {
            return AssistantWeatherResult(
                available = false,
                isLive = false,
                locationLabel = request.locationLabel,
                summary = if (request.preferredLanguage == "ro") {
                    "Nu pot verifica vremea în timp real fără cheia Meteoblue configurată."
                } else {
                    "Nu pot verifica vremea în timp real fără cheia Meteoblue configurată."
                },
                errorMessage = "missing_api_key"
            )
        }

        val requestedDateWindow = request.targetDate?.let { resolveWeatherRequestWindow(it) }
        if (request.targetDate != null && requestedDateWindow == null) {
            return AssistantWeatherResult(
                available = false,
                isLive = true,
                locationLabel = request.locationLabel,
                summary = unavailableWeatherSummary(request),
                errorMessage = "invalid_target_date"
            )
        }
        if (requestedDateWindow?.canQuery == false) {
            return AssistantWeatherResult(
                available = false,
                isLive = true,
                locationLabel = request.locationLabel,
                summary = unavailableWeatherSummary(request),
                errorMessage = "date_unavailable"
            )
        }

        val lookup = loadForecastWithFallbacks(
            lat = request.latitude,
            lon = request.longitude,
            asl = request.altitudeMeters,
            forecastDays = requestedDateWindow?.forecastDays,
            historyDays = requestedDateWindow?.historyDays
        )
        val response = lookup.response
        if (!response.hasForecastData()) {
            return AssistantWeatherResult(
                available = false,
                isLive = true,
                locationLabel = request.locationLabel,
                summary = if (request.preferredLanguage == "ro") {
                    "Am încercat să verific vremea în timp real, dar prognoza nu are date utile pentru locația cerută."
                } else {
                    "I tried to check live weather, but the forecast has no usable data for that location."
                },
                errorMessage = "empty_forecast"
            )
        }

        val hourly = request.targetDate?.let { targetDate ->
            selectHourlyWeatherForDate(response, targetDate, request.targetHour ?: 12)
        } ?: selectHourlyWeather(response, request)
        val daily = selectDailyWeather(response, request)
        if (request.targetDate != null && !hasUsableWeatherResult(hourly, daily)) {
            return AssistantWeatherResult(
                available = false,
                isLive = true,
                locationLabel = request.locationLabel,
                summary = unavailableWeatherSummary(request),
                errorMessage = "date_unavailable"
            )
        }
        val locationSuffix = when {
            lookup.usedFallbackLocation && !lookup.fallbackLocation?.name.isNullOrBlank() ->
                lookup.fallbackLocation?.name
            else -> request.locationLabel
        }
        return AssistantWeatherResult(
            available = true,
            isLive = true,
            locationLabel = locationSuffix,
            summary = buildAssistantWeatherSummary(hourly, daily, request),
            hourly = hourly,
            daily = daily,
            hazard = request.hazard
        )
    }

    private suspend fun requestForecast(
        lat: Double,
        lon: Double,
        asl: Int?,
        forecastDays: Int? = null,
        historyDays: Int? = null
    ): MeteoblueResponse? {
        if (meteoblueApiKey.isBlank()) {
            return null
        }
        return runCatching {
            meteoblueService.getForecast(
                lat = lat,
                lon = lon,
                asl = asl,
                apiKey = meteoblueApiKey,
                forecastDays = forecastDays,
                historyDays = historyDays
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

    private fun selectHourlyWeather(
        response: MeteoblueResponse?,
        request: AssistantWeatherRequest
    ): AssistantHourlyWeather? {
        val hourly = response?.data1h ?: return response?.current?.let { current ->
            AssistantHourlyWeather(
                time = current.time,
                temperatureC = current.temperature,
                pictocode = current.pictocode,
                windSpeedKmh = current.windspeed
            )
        }
        val times = hourly.time
        if (times.isEmpty()) {
            return null
        }
        val index = resolveHourlyIndex(times, request)
        return AssistantHourlyWeather(
            time = times.getOrNull(index).orEmpty(),
            temperatureC = hourly.temperature?.getOrNull(index),
            precipitationMm = hourly.precipitation?.getOrNull(index),
            precipitationProbability = hourly.precipitationProbability?.getOrNull(index),
            pictocode = hourly.pictocode?.getOrNull(index),
            visibilityKm = hourly.visibility?.getOrNull(index),
            windSpeedKmh = if (index == 0) response.current?.windspeed else null
        )
    }

    private fun selectDailyWeather(
        response: MeteoblueResponse?,
        request: AssistantWeatherRequest
    ): DailyForecastEntry? {
        val daily = buildDailyForecast(response)
        if (daily.isEmpty()) {
            return null
        }
        request.targetDate?.let { date ->
            return daily.firstOrNull { it.date == date }
        }
        return daily.firstOrNull()
    }

    private fun resolveHourlyIndex(times: List<String>, request: AssistantWeatherRequest): Int {
        val fallbackIndex = request.offsetHours?.coerceAtLeast(0)?.coerceAtMost(times.lastIndex) ?: 0
        val target = when {
            request.targetDate != null && request.targetHour != null -> runCatching {
                LocalDate.parse(request.targetDate).atTime(request.targetHour, 0)
            }.getOrNull()
            request.offsetHours != null -> LocalDateTime.now().plusHours(request.offsetHours.toLong())
            else -> LocalDateTime.now()
        } ?: return fallbackIndex

        val indexedTimes = times.mapIndexedNotNull { index, raw ->
            parseForecastTime(raw)?.let { parsed -> index to parsed }
        }
        if (indexedTimes.isEmpty()) {
            return fallbackIndex
        }
        return indexedTimes.minByOrNull { (_, parsed) ->
            kotlin.math.abs(Duration.between(target, parsed).toMinutes())
        }?.first ?: fallbackIndex
    }

    private fun parseForecastTime(raw: String): LocalDateTime? {
        val value = raw.trim()
        val normalized = value.replace("T", " ").substringBefore("+").substringBefore("Z")
        val patterns = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        )
        patterns.forEach { formatter ->
            runCatching { LocalDateTime.parse(normalized, formatter) }.getOrNull()?.let { return it }
        }
        return runCatching { LocalDateTime.parse(value) }.getOrNull()
    }

    private fun buildAssistantWeatherSummary(
        hourly: AssistantHourlyWeather?,
        daily: DailyForecastEntry?,
        request: AssistantWeatherRequest
    ): String {
        val isRomanian = request.preferredLanguage == "ro"
        val pieces = mutableListOf<String>()
        hourly?.temperatureC?.let { pieces += formatForecastTemperature(it) }
        hourly?.pictocode?.let { pieces += getPictocodeDescription(it) }
        hourly?.precipitationProbability?.let {
            pieces += if (isRomanian) "precipitatii $it%" else "precipitation $it%"
        }
        hourly?.precipitationMm?.takeIf { it > 0.0 }?.let {
            pieces += if (isRomanian) {
                String.format(Locale.getDefault(), "ploaie %.1f mm", it)
            } else {
                String.format(Locale.getDefault(), "rain %.1f mm", it)
            }
        }
        hourly?.windSpeedKmh?.let {
            pieces += String.format(Locale.getDefault(), "vant %.0f km/h", it)
        }
        if (pieces.isEmpty() && daily != null) {
            daily.temperatureMin?.let { pieces += formatForecastTemperature(it) }
            daily.temperatureMax?.let { pieces += formatForecastTemperature(it) }
            daily.precipitationProbability?.let {
                pieces += if (isRomanian) "precipitatii $it%" else "precipitation $it%"
            }
            pieces += daily.description
        }
        return pieces.ifEmpty {
            listOf(if (isRomanian) "prognoza live disponibila" else "live forecast available")
        }.joinToString(", ")
    }

    private fun hasUsableWeatherResult(
        hourly: AssistantHourlyWeather?,
        daily: DailyForecastEntry?
    ): Boolean =
        hourly?.temperatureC != null ||
            hourly?.pictocode != null ||
            hourly?.precipitationMm != null ||
            hourly?.precipitationProbability != null ||
            hourly?.windSpeedKmh != null ||
            daily?.temperatureMin != null ||
            daily?.temperatureMax != null ||
            daily?.precipitationProbability != null

    private fun unavailableWeatherSummary(request: AssistantWeatherRequest): String =
        if (request.preferredLanguage == "ro") {
            "Nu am gasit date Meteoblue disponibile pentru data ceruta in locatia ceruta."
        } else {
            "I could not find Meteoblue data for the requested date and location."
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

    private fun resolveWeatherRequestWindow(date: Calendar): WeatherRequestWindow =
        buildWeatherRequestWindow(trailLocalDate(date))

    private fun resolveWeatherRequestWindow(targetDate: String): WeatherRequestWindow? =
        runCatching { LocalDate.parse(targetDate) }
            .getOrNull()
            ?.let(::buildWeatherRequestWindow)

    private fun buildWeatherRequestWindow(targetDate: LocalDate): WeatherRequestWindow {
        val today = LocalDate.now()
        val daysFromToday = ChronoUnit.DAYS.between(today, targetDate)
        val canQuery = daysFromToday in -RecentHistoryDays..ForecastHorizonDays
        return WeatherRequestWindow(
            targetDate = targetDate.toString(),
            daysFromToday = daysFromToday,
            forecastDays = if (canQuery) {
                if (daysFromToday >= 0) {
                    (daysFromToday + 1).coerceAtMost(ForecastHorizonDays).toInt()
                } else {
                    1
                }
            } else {
                null
            },
            historyDays = if (canQuery && daysFromToday < 0) {
                (-daysFromToday).coerceAtMost(RecentHistoryDays).toInt()
            } else {
                null
            },
            canQuery = canQuery
        )
    }

    private fun trailLocalDate(date: Calendar): LocalDate =
        date.time.toInstant().atZone(date.timeZone.toZoneId()).toLocalDate()

    private fun buildSelectedDateWeather(
        weatherLookup: WeatherLookupResult,
        targetDate: String
    ): SelectedDateWeather {
        val response = weatherLookup.response
        val dailyForecast = buildDailyForecast(response)
        val daily = dailyForecast.firstOrNull { it.date == targetDate }
        val hourly = selectHourlyWeatherForDate(response, targetDate)
        val summary = formatSelectedDateWeatherSummary(
            hourly = hourly,
            daily = daily,
            suffix = weatherLocationSuffix(weatherLookup)
        )
        return SelectedDateWeather(
            summary = summary,
            sunsetTime = daily?.sunset?.let(::extractForecastClock),
            dailyForecast = dailyForecast
        )
    }

    private fun selectHourlyWeatherForDate(
        response: MeteoblueResponse?,
        targetDate: String,
        preferredHour: Int = 12
    ): AssistantHourlyWeather? {
        val hourly = response?.data1h ?: return null
        val target = runCatching {
            LocalDate.parse(targetDate).atTime(preferredHour.coerceIn(0, 23), 0)
        }.getOrNull() ?: return null
        val candidates = hourly.time.mapIndexedNotNull { index, raw ->
            val parsed = parseForecastTime(raw) ?: return@mapIndexedNotNull null
            if (parsed.toLocalDate().toString() == targetDate) {
                index to parsed
            } else {
                null
            }
        }
        if (candidates.isEmpty()) {
            return null
        }
        val index = candidates.minByOrNull { (_, parsed) ->
            kotlin.math.abs(Duration.between(target, parsed).toMinutes())
        }?.first ?: return null
        return AssistantHourlyWeather(
            time = hourly.time.getOrNull(index).orEmpty(),
            temperatureC = hourly.temperature?.getOrNull(index),
            precipitationMm = hourly.precipitation?.getOrNull(index),
            precipitationProbability = hourly.precipitationProbability?.getOrNull(index),
            pictocode = hourly.pictocode?.getOrNull(index),
            visibilityKm = hourly.visibility?.getOrNull(index),
            windSpeedKmh = if (index == 0) response.current?.windspeed else null
        )
    }

    private fun formatSelectedDateWeatherSummary(
        hourly: AssistantHourlyWeather?,
        daily: DailyForecastEntry?,
        suffix: String
    ): String {
        val pieces = mutableListOf<String>()
        when {
            hourly?.temperatureC != null -> pieces += formatForecastTemperature(hourly.temperatureC)
            daily != null -> formatTemperatureRange(daily.temperatureMin, daily.temperatureMax)?.let { pieces += it }
        }
        val hasDailyWeatherValues = daily?.temperatureMin != null ||
            daily?.temperatureMax != null ||
            daily?.precipitationProbability != null
        val description = hourly?.pictocode?.let(::getPictocodeDescription)
            ?: daily?.description?.takeIf { hasDailyWeatherValues && it.isNotBlank() }
        description?.let { pieces += it }
        (hourly?.precipitationProbability ?: daily?.precipitationProbability)?.let {
            pieces += "precipitații $it%"
        }
        return pieces.ifEmpty { listOf(UnavailableWeatherLabel) }.joinToString(", ") + suffix
    }

    private fun formatTemperatureRange(minTemperature: Double?, maxTemperature: Double?): String? =
        when {
            minTemperature != null && maxTemperature != null ->
                "${formatForecastTemperature(minTemperature)} / ${formatForecastTemperature(maxTemperature)}"
            maxTemperature != null -> "max ${formatForecastTemperature(maxTemperature)}"
            minTemperature != null -> "min ${formatForecastTemperature(minTemperature)}"
            else -> null
        }

    private fun weatherLocationSuffix(weatherLookup: WeatherLookupResult): String =
        when {
            weatherLookup.usedFallbackLocation && !weatherLookup.fallbackLocation?.name.isNullOrBlank() ->
                " (${weatherLookup.fallbackLocation?.name})"
            weatherLookup.usedFallbackLocation -> " (punct apropiat)"
            else -> ""
        }

    private fun normalizeForecastDate(raw: String?): String? =
        raw?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.substringBefore(" ")
            ?.substringBefore("T")

    private fun extractForecastClock(raw: String): String? {
        val value = raw.trim()
        if (value.isBlank()) {
            return null
        }
        parseForecastTime(value)?.let { parsed ->
            return parsed.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
        }
        return value.substringAfter(" ", value).substringAfter("T", value).take(5)
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
            val weatherWindow = resolveWeatherRequestWindow(date)
            val cachedTrail = findMatchingActiveTrail(
                name = name,
                date = date,
                lat = lat,
                lon = lon,
                localCode = localCode
            )
            var dailyForecast = cachedTrail?.dailyForecast.orEmpty()
            var sunsetStr = dailyForecast
                .firstOrNull { it.date == weatherWindow.targetDate }
                ?.sunset
                ?.let(::extractForecastClock)
                ?: cachedTrail?.sunsetTime
                ?: "N/A"
            var weatherInfo = cachedTrail?.weatherForecast?.takeIf { it.isNotBlank() } ?: UnavailableWeatherLabel
            var syncTime: Long? = cachedTrail?.lastSyncTimestamp
            refreshOnlineState()

            val shouldUseCachedWeather = cachedTrail != null && !shouldRefreshWeather(cachedTrail)
            if (
                !shouldUseCachedWeather &&
                weatherWindow.canQuery &&
                isInternetAvailable() &&
                meteoblueApiKey.isNotBlank()
            ) {
                try {
                    val weatherLookup = loadForecastWithFallbacks(
                        lat = lat,
                        lon = lon,
                        asl = _uiState.value.altitude?.toInt(),
                        forecastDays = weatherWindow.forecastDays,
                        historyDays = weatherWindow.historyDays
                    )
                    val selectedWeather = buildSelectedDateWeather(
                        weatherLookup = weatherLookup,
                        targetDate = weatherWindow.targetDate
                    )
                    weatherInfo = selectedWeather.summary
                    dailyForecast = selectedWeather.dailyForecast
                    sunsetStr = selectedWeather.sunsetTime ?: sunsetStr
                    syncTime = System.currentTimeMillis()
                    Log.d("ScoutyAPI", "Weather updated for $name on ${weatherWindow.targetDate}")
                } catch (e: Exception) {
                    Log.e("ScoutyAPI", "Sync failed", e)
                    syncTime = System.currentTimeMillis()
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
                lastSyncTimestamp = syncTime,
                difficulty = difficulty,
                distanceKm = distanceKm,
                elevationGain = elevationGain,
                averageInclinePercent = calculateAverageInclinePercent(distanceKm, elevationGain),
                estimatedDuration = estimatedDuration,
                imageUrl = imageUrl,
                routeSegments = routeSegments,
                remainingRouteSegments = cachedTrail?.remainingRouteSegments ?: routeSegments,
                routeBounds = routeBounds,
                imageAttribution = imageAttribution,
                imageLicense = imageLicense,
                imageSourcePageUrl = imageSourcePageUrl,
                imageScope = imageScope,
                trackingState = cachedTrail?.trackingState ?: ActiveTrailState.PLANNED,
                progress = cachedTrail?.progress ?: 0f,
                distanceCompletedKm = cachedTrail?.distanceCompletedKm ?: 0.0,
                remainingDistanceKm = cachedTrail?.remainingDistanceKm ?: distanceKm,
                offTrailDistanceKm = cachedTrail?.offTrailDistanceKm ?: 0.0,
                hasLeftStartZone = cachedTrail?.hasLeftStartZone ?: false,
                startedAtEpochMillis = cachedTrail?.startedAtEpochMillis,
                dailyForecast = dailyForecast
            )

            val profileForTrail = if (recordSelection) {
                _uiState.value.userProfile.adaptToTrail(trail).also { adaptedProfile ->
                    saveUserTrailProfile(adaptedProfile, notifyFirebase = true)
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
            persistActiveTrail(trail, force = true)
            refreshNearbyWaterContextForCurrentState(force = true)
            _mapSessionState.update { currentState ->
                currentState.copy(
                    selectedTrail = trail.toTrailSelectionSnapshot(),
                    isBottomSheetVisible = false,
                    mode = if (trail.trackingState == ActiveTrailState.ACTIVE) {
                        MapTrailMode.ACTIVE
                    } else {
                        MapTrailMode.ORIENTED
                    },
                    focusRequestToken = System.currentTimeMillis()
                )
            }
            prepareOfflineMapForTrail(trail)
            maybeRefreshRouteRecommendations(force = true, latitude = lat, longitude = lon)
        }
    }

    private fun getPictocodeDescription(code: Int?): String {
        return when(code) {
            1 -> "Cer senin"
            2 -> "Mai mult senin"
            3 -> "Parțial noros"
            4 -> "Acoperit"
            5 -> "Ceață"
            6 -> "Burniță"
            11 -> "Ploaie"
            14 -> "Furtună"
            else -> "Noros"
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
            region = region?.takeIf { it.isNotBlank() } ?: "Regiune necunoscută",
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
            val normalizedDate = normalizeForecastDate(dateStr) ?: return@mapIndexedNotNull null
            DailyForecastEntry(
                date = normalizedDate,
                temperatureMax = dayData.temperatureMax?.getOrNull(index),
                temperatureMin = dayData.temperatureMin?.getOrNull(index),
                precipitationProbability = dayData.precipitationProbability?.getOrNull(index),
                description = selectHourlyWeatherForDate(response, normalizedDate)
                    ?.pictocode
                    ?.let(::getPictocodeDescription)
                    ?: getPictocodeDescription(null),
                sunrise = dayData.sunrise?.getOrNull(index),
                sunset = dayData.sunset?.getOrNull(index)
            )
        }
    }

    private fun isInternetAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
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

    fun confirmOfflineMapDownload() {
        val trailCode = _uiState.value.activeTrail?.localCode?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            mapPackRepository.markActiveTrail(trailCode)
            mapPackRepository.enqueueDownload(trailCode, forceMetered = true)
        }
    }

    private suspend fun prepareOfflineMapForTrail(trail: ActiveTrail) {
        val trailCode = trail.localCode?.takeIf { it.isNotBlank() } ?: return
        mapPackRepository.markActiveTrail(trailCode)
        mapPackRepository.enqueueDownload(trailCode, forceMetered = false)
    }

    private fun retryActiveTrailMapPack() {
        val trailCode = _uiState.value.activeTrail?.localCode?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            mapPackRepository.markActiveTrail(trailCode)
            mapPackRepository.enqueueDownload(trailCode, forceMetered = false)
        }
    }

    private fun releaseOfflineMapForCurrentTrail() {
        viewModelScope.launch(Dispatchers.IO) {
            mapPackRepository.releaseCurrentTrail()
        }
    }

    private fun registerNetworkCallback() {
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        }.onFailure { error ->
            Log.w("ScoutyNetwork", "Could not register network callback", error)
        }
    }

    private fun refreshAssistantRuntimeStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                knowledgePackManager.ensureReady()
                modelManager.refreshStatus()
            }.onFailure { error ->
                Log.w("ScoutyAssistant", "Failed to refresh assistant runtime status", error)
            }
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
        if (networkCallbackRegistered) {
            runCatching {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            }
        }
    }
}
