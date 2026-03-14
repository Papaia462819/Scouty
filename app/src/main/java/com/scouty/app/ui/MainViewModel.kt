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
import com.scouty.app.BuildConfig
import com.google.android.gms.location.*
import com.scouty.app.api.MeteoblueLocationResult
import com.scouty.app.api.MeteoblueResponse
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.scouty.app.api.MeteoblueService
import com.scouty.app.ui.models.ActiveTrail
import com.scouty.app.ui.models.GearRecommendationEngine
import com.scouty.app.ui.models.HomeStatus
import com.scouty.app.utils.SolarCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private data class WeatherLookupResult(
        val response: MeteoblueResponse? = null,
        val fallbackLocation: MeteoblueLocationResult? = null,
        val usedFallbackLocation: Boolean = false
    )

    private val meteoblueApiKey = BuildConfig.METEOBLUE_API_KEY

    private val _uiState = MutableStateFlow(HomeStatus())
    val uiState: StateFlow<HomeStatus> = _uiState.asStateFlow()

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
                _uiState.update { it.copy(
                    batteryPercent = pct,
                    batterySafe = pct < 15
                ) }
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
    }

    private fun loadDefaultGear() {
        val defaultGear = GearRecommendationEngine.build(trail = null)
        _uiState.update { it.copy(gearList = defaultGear) }
    }

    fun toggleGearItem(itemId: String) {
        _uiState.update { currentState ->
            val newList = currentState.gearList.map { 
                if (it.id == itemId) it.copy(isPacked = !it.isPacked) else it
            }
            currentState.copy(gearList = newList)
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
        _uiState.update { 
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
            imageUrl = null
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
        imageAttribution: String? = null,
        imageLicense: String? = null,
        imageSourcePageUrl: String? = null,
        imageScope: String? = null
    ) {
        fetchWeatherData(
            name = name,
            date = date,
            lat = lat,
            lon = lon,
            localCode = localCode,
            region = region,
            descriptionRo = descriptionRo,
            difficulty = difficulty,
            distanceKm = distanceKm,
            elevationGain = elevationGain,
            estimatedDuration = estimatedDuration,
            imageUrl = imageUrl,
            imageAttribution = imageAttribution,
            imageLicense = imageLicense,
            imageSourcePageUrl = imageSourcePageUrl,
            imageScope = imageScope
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
        difficulty: String,
        distanceKm: Double,
        elevationGain: Int,
        estimatedDuration: String,
        imageUrl: String?,
        imageAttribution: String? = null,
        imageLicense: String? = null,
        imageSourcePageUrl: String? = null,
        imageScope: String? = null
    ) {
        viewModelScope.launch {
            var sunsetStr = "N/A"
            var weatherInfo = "Unknown (Offline)"
            var syncTime: Long? = null
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

            val trail = ActiveTrail(
                name = name,
                date = date,
                latitude = lat,
                longitude = lon,
                localCode = localCode,
                region = region,
                descriptionRo = descriptionRo,
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
                imageScope = imageScope
            )
            
            _uiState.update { currentState ->
                val updatedGear = GearRecommendationEngine.build(
                    trail = trail,
                    previousItems = currentState.gearList
                )
                currentState.copy(activeTrail = trail, gearList = updatedGear)
            }
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
        _uiState.update { it.copy(isOnline = online) }
    }

    override fun onCleared() {
        super.onCleared()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        try {
            getApplication<Application>().unregisterReceiver(batteryReceiver)
        } catch (e: Exception) { }
    }
}
