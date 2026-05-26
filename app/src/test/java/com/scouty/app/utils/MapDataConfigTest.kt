package com.scouty.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MapDataConfigTest {

    @Test
    fun fromRegistry_usesRemoteSourceWhenOnline() {
        val registry = registry(
            isOnline = true,
            activeSourceUri = "pmtiles://https://maps.scouty.app/base/romania-high-detail.pmtiles"
        )

        val config = MapDataConfig.fromRegistry(registry)

        assertTrue(config.isBasePackReady)
        assertEquals("pmtiles://https://maps.scouty.app/base/romania-high-detail.pmtiles", config.baseSourceUri())
    }

    @Test
    fun fromRegistry_usesLocalRoutePackWhenOfflineAndReady() {
        val routePack = InstalledMapPack(
            id = MapPackId.ROUTE_OFFLINE,
            file = File("offline.pmtiles"),
            status = MapPackStatus.AVAILABLE,
            sourceUri = "pmtiles://file:///offline.pmtiles",
            version = "route-v1"
        )
        val registry = registry(
            isOnline = false,
            routePack = routePack,
            activeSourceUri = routePack.sourceUri
        )

        val config = MapDataConfig.fromRegistry(registry)

        assertTrue(config.isBasePackReady)
        assertEquals("pmtiles://file:///offline.pmtiles", config.baseSourceUri())
    }

    @Test
    fun fromRegistry_hasNoSourceWhenOfflineWithoutRoutePack() {
        val registry = registry(isOnline = false, activeSourceUri = null)

        val config = MapDataConfig.fromRegistry(registry)

        assertFalse(config.isBasePackReady)
    }

    private fun registry(
        isOnline: Boolean,
        activeSourceUri: String?,
        routePack: InstalledMapPack? = null
    ): MapPackRegistry {
        val mapsDir = File("build/test-maps")
        val basePack = InstalledMapPack(
            id = MapPackId.ROMANIA_BASE,
            file = File(mapsDir, MapPackId.ROMANIA_BASE.fileName),
            status = if (isOnline) MapPackStatus.AVAILABLE else MapPackStatus.MISSING,
            sourceUri = activeSourceUri
        )
        return MapPackRegistry(
            mapsDirectory = mapsDir,
            installedPacks = mapOf(
                MapPackId.ROMANIA_BASE to basePack,
                MapPackId.ROUTE_OFFLINE to (routePack ?: InstalledMapPack(
                    id = MapPackId.ROUTE_OFFLINE,
                    file = File(""),
                    status = MapPackStatus.NOT_REQUESTED
                ))
            ),
            hasLocalGlyphs = false,
            isOnline = isOnline,
            currentTrailPack = routePack,
            activeSourceUri = activeSourceUri
        )
    }
}
