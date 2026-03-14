package com.scouty.app.utils

import android.net.Uri

data class MapDataConfig(
    val basePack: InstalledMapPack,
    val bucegiDemoPack: InstalledMapPack,
    val hasLocalGlyphs: Boolean
) {
    val isBasePackReady: Boolean
        get() = basePack.isReady

    val hasDemoPack: Boolean
        get() = bucegiDemoPack.isReady

    val styleKey: String
        get() = buildString {
            append(basePack.version)
            append(':')
            append(bucegiDemoPack.version)
            append(':')
            append(if (hasLocalGlyphs) "glyphs" else "no-glyphs")
        }

    fun baseSourceUri(): String? = basePack.takeIf { it.isReady }?.file?.let(::pmtilesFileUri)

    fun demoSourceUri(): String? = bucegiDemoPack.takeIf { it.isReady }?.file?.let(::pmtilesFileUri)

    fun glyphsUri(): String? =
        if (hasLocalGlyphs) {
            "asset://glyphs/{fontstack}/{range}.pbf"
        } else {
            null
        }

    private fun pmtilesFileUri(file: java.io.File): String =
        "pmtiles://${Uri.fromFile(file)}"

    companion object {
        fun fromRegistry(registry: MapPackRegistry): MapDataConfig =
            MapDataConfig(
                basePack = registry.basePack(),
                bucegiDemoPack = registry.demoPack(),
                hasLocalGlyphs = registry.hasLocalGlyphs
            )
    }
}
