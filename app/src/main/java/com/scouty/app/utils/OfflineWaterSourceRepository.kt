package com.scouty.app.utils

import android.content.Context
import com.scouty.app.assistant.model.WaterSourceContextItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class OfflineWaterSourceRepository(
    context: Context,
    private val assetName: String = "Izvoare_Adapost.geojson"
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedPoints: List<WaterPoint>? = null

    fun nearest(
        latitude: Double,
        longitude: Double,
        limit: Int = 3,
        maxDistanceKm: Double = 30.0
    ): List<WaterSourceContextItem> =
        loadPoints()
            .asSequence()
            .map { point ->
                val distanceKm = haversineKm(latitude, longitude, point.latitude, point.longitude)
                point to distanceKm
            }
            .filter { (_, distanceKm) -> distanceKm <= maxDistanceKm }
            .sortedWith(compareBy<Pair<WaterPoint, Double>> { it.second }.thenByDescending { it.first.priority })
            .take(limit)
            .map { (point, distanceKm) ->
                WaterSourceContextItem(
                    sourceId = point.sourceId,
                    title = point.title,
                    subtitle = point.subtitle,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    distanceKm = distanceKm,
                    bearingDegrees = bearingDegrees(latitude, longitude, point.latitude, point.longitude),
                    isPotable = point.isPotable
                )
            }
            .toList()

    private fun loadPoints(): List<WaterPoint> {
        cachedPoints?.let { return it }
        return synchronized(this) {
            cachedPoints ?: readPoints().also { cachedPoints = it }
        }
    }

    private fun readPoints(): List<WaterPoint> =
        runCatching {
            appContext.assets.open(assetName).bufferedReader().use { reader ->
                json.decodeFromString(GeoJsonFeatureCollection.serializer(), reader.readText())
            }.features.mapNotNull(::toWaterPoint)
        }.getOrDefault(emptyList())

    private fun toWaterPoint(feature: GeoJsonFeature): WaterPoint? {
        val coordinates = feature.geometry?.pointCoordinates() ?: return null
        val properties = feature.properties
        val amenity = properties.string("amenity")?.lowercase(Locale.ROOT)
        val natural = properties.string("natural")?.lowercase(Locale.ROOT)
        val drinkingWater = properties.string("drinking_water")?.lowercase(Locale.ROOT)
        val shelter = properties.string("shelter")?.lowercase(Locale.ROOT)
        val description = properties.string("description")?.takeIf { it.isNotBlank() }
        val name = properties.string("name:ro", "name", "name:en")?.takeIf { it.isNotBlank() }

        val matches = amenity == "drinking_water" || drinkingWater == "yes" || natural == "spring"
        if (!matches) return null

        val isPotable = when {
            drinkingWater == "no" -> false
            amenity == "drinking_water" || drinkingWater == "yes" -> true
            else -> null
        }
        val priority = when {
            isPotable == true -> 3
            natural == "spring" -> 2
            else -> 1
        }
        val defaultTitle = when {
            natural == "spring" && shelter == "yes" -> "Izvor adapostit"
            natural == "spring" -> "Izvor"
            else -> "Sursa de apa"
        }
        val subtitle = listOfNotNull(
            if (natural == "spring") "Izvor" else null,
            when (isPotable) {
                true -> "Potabila mapata"
                false -> "Potabilitate marcata negativ"
                null -> "Potabilitate neconfirmata"
            },
            description
        ).joinToString(" · ")

        return WaterPoint(
            sourceId = properties.string("@id") ?: feature.id ?: "water:${coordinates.latitude}:${coordinates.longitude}",
            title = name ?: defaultTitle,
            subtitle = subtitle,
            latitude = coordinates.latitude,
            longitude = coordinates.longitude,
            priority = priority,
            isPotable = isPotable
        )
    }

    private fun GeoJsonGeometry.pointCoordinates(): Coordinates? {
        if (type != "Point") return null
        val array = runCatching { coordinates.jsonArray }.getOrNull() ?: return null
        val lon = array.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return null
        val lat = array.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return null
        return Coordinates(latitude = lat, longitude = lon)
    }

    private fun JsonObject.string(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            this[key]?.jsonPrimitive?.contentOrNull
        }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val startLat = Math.toRadians(lat1)
        val endLat = Math.toRadians(lat2)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(startLat) * cos(endLat) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return radiusKm * c
    }

    private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val startLat = Math.toRadians(lat1)
        val endLat = Math.toRadians(lat2)
        val deltaLon = Math.toRadians(lon2 - lon1)
        val y = sin(deltaLon) * cos(endLat)
        val x = cos(startLat) * sin(endLat) - sin(startLat) * cos(endLat) * cos(deltaLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    @Serializable
    private data class GeoJsonFeatureCollection(
        val features: List<GeoJsonFeature> = emptyList()
    )

    @Serializable
    private data class GeoJsonFeature(
        val id: String? = null,
        val properties: JsonObject = JsonObject(emptyMap()),
        val geometry: GeoJsonGeometry? = null
    )

    @Serializable
    private data class GeoJsonGeometry(
        val type: String,
        val coordinates: JsonElement
    )

    private data class Coordinates(
        val latitude: Double,
        val longitude: Double
    )

    private data class WaterPoint(
        val sourceId: String,
        val title: String,
        val subtitle: String,
        val latitude: Double,
        val longitude: Double,
        val priority: Int,
        val isPotable: Boolean?
    )
}
