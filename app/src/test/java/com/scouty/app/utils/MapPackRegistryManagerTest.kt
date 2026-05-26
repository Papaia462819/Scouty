package com.scouty.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MapPackRegistryManagerTest {
    @Test
    fun inspectPack_marksNonEmptyFileAvailable() {
        val mapsDirectory = Files.createTempDirectory("scouty-maps").toFile()
        val pack = File(mapsDirectory, MapPackId.ROMANIA_BASE.fileName).apply {
            writeText("pmtiles")
        }

        val installed = MapPackRegistryManager.inspectPack(mapsDirectory, MapPackId.ROMANIA_BASE)

        assertEquals(MapPackStatus.AVAILABLE, installed.status)
        assertEquals(pack.absolutePath, installed.file.absolutePath)
        assertTrue(installed.sourceUri?.startsWith("pmtiles://file://") == true)
    }

    @Test
    fun inspectPack_marksEmptyFileInvalid() {
        val mapsDirectory = Files.createTempDirectory("scouty-empty-maps").toFile()
        File(mapsDirectory, MapPackId.ROMANIA_BASE.fileName).createNewFile()

        val installed = MapPackRegistryManager.inspectPack(mapsDirectory, MapPackId.ROMANIA_BASE)

        assertEquals(MapPackStatus.INVALID, installed.status)
        assertNull(installed.sourceUri)
    }

    @Test
    fun remoteMasterUri_usesPmtilesHttpsProtocol() {
        val uri = MapPackRegistryManager.remoteMasterUri()

        assertTrue(uri.startsWith("pmtiles://https://"))
        assertTrue(uri.endsWith("/base/${MapPackId.ROMANIA_BASE.fileName}"))
    }
}
