package com.scouty.app.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapDataConfigTest {

    @Test
    fun `fromRegistry reflects required and optional pack states`() {
        val mapsDir = File("build/test-maps")
        val registry = MapPackRegistry(
            mapsDirectory = mapsDir,
            installedPacks = mapOf(
                MapPackId.ROMANIA_BASE to InstalledMapPack(
                    id = MapPackId.ROMANIA_BASE,
                    file = File(mapsDir, MapPackId.ROMANIA_BASE.fileName),
                    status = MapPackStatus.AVAILABLE,
                    sizeBytes = 128L,
                    version = "local-100"
                ),
                MapPackId.BUCEGI_HIGH to InstalledMapPack(
                    id = MapPackId.BUCEGI_HIGH,
                    file = File(mapsDir, MapPackId.BUCEGI_HIGH.fileName),
                    status = MapPackStatus.MISSING
                )
            ),
            hasLocalGlyphs = false
        )

        val config = MapDataConfig.fromRegistry(registry)

        assertTrue(config.isBasePackReady)
        assertFalse(config.hasDemoPack)
        assertEquals("local-100:missing:no-glyphs", config.styleKey)
    }
}
