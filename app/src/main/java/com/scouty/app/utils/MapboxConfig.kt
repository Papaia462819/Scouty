package com.scouty.app.utils

import android.net.Uri
import com.scouty.app.BuildConfig

data class OptionalTilesetConfig(
    val tilesetId: String,
    val sourceLayer: String
)

object MapboxConfig {
    const val BASE_SOURCE_ID = "romania-base-source"
    const val ELEVATION_SOURCE_ID = "romania-elevation-source"
    const val CUSTOM_TRAILS_SOURCE_ID = "custom-trails-source"
    const val CUSTOM_WILDLIFE_SOURCE_ID = "custom-wildlife-source"
    const val CUSTOM_ATTRACTIONS_SOURCE_ID = "custom-attractions-source"
    const val CUSTOM_WATER_SOURCE_ID = "custom-water-source"

    val accessToken: String
        get() = BuildConfig.MAPBOX_ACCESS_TOKEN

    val username: String
        get() = BuildConfig.MAPBOX_USERNAME

    val isConfigured: Boolean
        get() = accessToken.isNotBlank() &&
            BuildConfig.MAPBOX_BASE_TILESET_ID.isNotBlank()

    val hasElevationTileset: Boolean
        get() = BuildConfig.MAPBOX_ELEVATION_TILESET_ID.isNotBlank()

    fun baseTilesetUrl(): String = tileJsonUrl(BuildConfig.MAPBOX_BASE_TILESET_ID)

    fun elevationTilesetUrl(): String = tileJsonUrl(BuildConfig.MAPBOX_ELEVATION_TILESET_ID)

    fun glyphsUrl(): String {
        val encodedToken = Uri.encode(accessToken)
        return "https://api.mapbox.com/fonts/v1/$username/{fontstack}/{range}.pbf?access_token=$encodedToken"
    }

    fun customTrails(): OptionalTilesetConfig? = optionalTileset(
        BuildConfig.MAPBOX_TRAILS_TILESET_ID,
        BuildConfig.MAPBOX_TRAILS_SOURCE_LAYER
    )

    fun customWildlife(): OptionalTilesetConfig? = optionalTileset(
        BuildConfig.MAPBOX_WILDLIFE_TILESET_ID,
        BuildConfig.MAPBOX_WILDLIFE_SOURCE_LAYER
    )

    fun customAttractions(): OptionalTilesetConfig? = optionalTileset(
        BuildConfig.MAPBOX_ATTRACTIONS_TILESET_ID,
        BuildConfig.MAPBOX_ATTRACTIONS_SOURCE_LAYER
    )

    fun customWater(): OptionalTilesetConfig? = optionalTileset(
        BuildConfig.MAPBOX_WATER_TILESET_ID,
        BuildConfig.MAPBOX_WATER_SOURCE_LAYER
    )

    fun tileJsonUrl(tilesetId: String): String {
        val encodedToken = Uri.encode(accessToken)
        return "https://api.mapbox.com/v4/$tilesetId.json?secure&access_token=$encodedToken"
    }

    private fun optionalTileset(tilesetId: String, sourceLayer: String): OptionalTilesetConfig? {
        if (tilesetId.isBlank() || sourceLayer.isBlank()) {
            return null
        }
        return OptionalTilesetConfig(tilesetId = tilesetId, sourceLayer = sourceLayer)
    }
}
