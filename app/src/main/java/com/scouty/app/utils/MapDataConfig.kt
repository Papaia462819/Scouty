package com.scouty.app.utils

data class MapDataConfig(
    val basePack: InstalledMapPack,
    val routePack: InstalledMapPack?,
    val hasLocalGlyphs: Boolean,
    val activeSourceUri: String?,
    val isOnline: Boolean
) {
    val isBasePackReady: Boolean
        get() = !activeSourceUri.isNullOrBlank()

    val styleKey: String
        get() = buildString {
            append(activeSourceUri ?: "no-source")
            append(':')
            append(routePack?.version ?: "no-route-pack")
            append(':')
            append(if (isOnline) "online" else "offline")
            append(':')
            append(if (hasLocalGlyphs) "glyphs" else "no-glyphs")
        }

    fun baseSourceUri(): String? = activeSourceUri

    fun glyphsUri(): String? =
        if (hasLocalGlyphs) {
            "asset://glyphs/{fontstack}/{range}.pbf"
        } else {
            null
        }

    companion object {
        fun fromRegistry(registry: MapPackRegistry): MapDataConfig =
            MapDataConfig(
                basePack = registry.basePack(),
                routePack = registry.currentTrailPack,
                hasLocalGlyphs = registry.hasLocalGlyphs,
                activeSourceUri = registry.activeSourceUri,
                isOnline = registry.isOnline
            )
    }
}
