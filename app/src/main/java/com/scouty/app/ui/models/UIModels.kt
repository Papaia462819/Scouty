package com.scouty.app.ui.models

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
    val lastSyncTimestamp: Long? = null
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
    val gearList: List<GearItem> = emptyList()
)
