package com.scouty.app.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MeteoblueResponse(
    val metadata: Metadata? = null,
    val current: CurrentWeather? = null,
    @SerialName("data_1h") val data1h: HourlyData? = null,
    @SerialName("data_day") val dataDay: DailyData? = null
)

@Serializable
data class Metadata(
    val latitude: Double,
    val longitude: Double,
    val height: Int,
    @SerialName("timezone_abbreviation") val timezoneAbbreviation: String? = null,
    @SerialName("utc_timeoffset") val utcTimeoffset: Double? = null
)

@Serializable
data class CurrentWeather(
    val time: String,
    val temperature: Double,
    val windspeed: Double,
    val pictocode: Int
)

@Serializable
data class HourlyData(
    val time: List<String>,
    val temperature: List<Double>? = null,
    val precipitation: List<Double>? = null,
    @SerialName("precipitation_probability") val precipitationProbability: List<Int>? = null,
    val pictocode: List<Int>? = null,
    val visibility: List<Double>? = null
)

@Serializable
data class DailyData(
    val time: List<String>,
    @SerialName("temperature_max") val temperatureMax: List<Double>? = null,
    @SerialName("temperature_min") val temperatureMin: List<Double>? = null,
    val sunrise: List<String>? = null,
    val sunset: List<String>? = null,
    @SerialName("precipitation_probability") val precipitationProbability: List<Int>? = null
)

@Serializable
data class MeteoblueLocationSearchResponse(
    val results: List<MeteoblueLocationResult> = emptyList()
)

@Serializable
data class MeteoblueLocationResult(
    val name: String? = null,
    val country: String? = null,
    val admin1: String? = null,
    val lat: Double,
    val lon: Double,
    val asl: Int? = null,
    val distance: Double? = null,
    val population: Int? = null,
    @SerialName("featureClass") val featureClass: String? = null,
    @SerialName("featureCode") val featureCode: String? = null
)
