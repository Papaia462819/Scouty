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
import com.scouty.app.data.RouteGeometryRepository
import com.scouty.app.data.UserTrailProfileStore
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.scouty.app.api.MeteoblueService
import com.scouty.app.ui.models.ActiveTrail
import com.scouty.app.ui.models.GearRecommendationEngine
import com.scouty.app.ui.models.HomeStatus
import com.scouty.app.ui.models.RouteRecommendationEngine
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

    private data class WeatherLookupResult(
        val response: MeteoblueResponse? = null,
        val fallbackLocation: MeteoblueLocationResult? = null,
        val usedFallbackLocation: Boolean = false
    )

    private val meteoblueApiKey = BuildConfig.METEOBLUE_API_KEY
    private val userTrailProfileStore = UserTrailProfileStore(application)
    private val assistantRuntimeGraph = AssistantRuntimeGraph.get(application)
    private val knowledgePackManager: KnowledgePackManager = assistantRuntimeGraph.knowledgePackManager
    private val modelManager: ModelManager = assistantRuntimeGraph.modelManager

    private val _uiState = MutableStateFlow(HomeStatus(userProfile = userTrailProfileStore.load()))
    val uiState: StateFlow<HomeStatus> = _uiState.asStateFlow()
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
        val defaultGear = GearRecommendationEngine.build(
            trail = null,
            profile = _uiState.value.userProfile
        )
        updateUiState { it.copy(gearList = defaultGear) }
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
                gearList = GearRecommendationEngine.build(
                    trail = currentState.activeTrail,
                    profile = profile,
                    previousItems = currentState.gearList
                )
            )
        }
        maybeRefreshRouteRecommendations(force = true)
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
        maybeRefreshRouteRecommendations(latitude = location.latitude, longitude = location.longitude)
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
                trail.name,
                trail.date,
                trail.latitude,
                trail.longitude,
                trail.localCode,
                trail.region,
                trail.descriptionRo,
                trail.localDescription,
                trail.routeSummary,
                trail.fromName,
                trail.toName,
                trail.markingSymbols,
                trail.sourceUrls,
                trail.difficulty,
                trail.distanceKm,
                trail.elevationGain,
                trail.estimatedDuration,
                trail.imageUrl,
                trail.imageAttribution,
                trail.imageLicense,
                trail.imageSourcePageUrl,
                trail.imageScope
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

    fun setActiveTrail(name: String, date: Calendar, lat: Double, lon: Double) {
        setActiveTrail(
            name = name,
            date = date,
            lat = lat,
            lon = lon,
            difficulty = "MEDIUM",
            distanceKm = 12.4,
            elevationGain = 1234,
            estimatedDuration = "~6h",
            imageUrl = null,
            recordSelection = true
        )
    }

    fun setActiveTrail(
        name: String,
        date: Calendar,
        lat: Double,
        lon: Double,
        difficulty: String,
        distanceKm: Double,
        elevationGain: Int,
        estimatedDuration: String,
        imageUrl: String?,
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
                val updatedGear = GearRecommendationEngine.build(
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

    override fun onCleared() {
        super.onCleared()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        try {
            getApplication<Application>().unregisterReceiver(batteryReceiver)
        } catch (e: Exception) { }
    }
}
