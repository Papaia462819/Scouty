package com.scouty.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class MapPackRegistryManagerTest {
    @Test
    fun bundledInstaller_copiesMissingPackIntoMapsDirectory() {
        val mapsDirectory = Files.createTempDirectory("scouty-maps").toFile()
        val installer = BundledMapPackInstaller(
            FakeMapPackAssetSource(
                mapOf(MapPackId.ROMANIA_BASE.bundledAssetPath to "pmtiles-bundle".toByteArray())
            )
        )

        val copied = installer.ensureInstalled(mapsDirectory, MapPackId.ROMANIA_BASE)
        val installed = MapPackRegistryManager.inspectPack(mapsDirectory, MapPackId.ROMANIA_BASE)

        assertTrue(copied)
        assertEquals(MapPackStatus.AVAILABLE, installed.status)
        assertTrue(installed.file.exists())
        assertEquals("pmtiles-bundle", installed.file.readText())
    }

    @Test
    fun bundledInstaller_keepsExistingValidPack() {
        val mapsDirectory = Files.createTempDirectory("scouty-existing-maps").toFile()
        val existingFile = File(mapsDirectory, MapPackId.ROMANIA_BASE.fileName).apply {
            writeText("existing-pack")
        }
        val installer = BundledMapPackInstaller(
            FakeMapPackAssetSource(
                mapOf(MapPackId.ROMANIA_BASE.bundledAssetPath to "new-pack".toByteArray())
            )
        )

        val copied = installer.ensureInstalled(mapsDirectory, MapPackId.ROMANIA_BASE)

        assertFalse(copied)
        assertEquals("existing-pack", existingFile.readText())
    }

    @Test
    fun bundledInstaller_skipsWhenAssetIsMissing() {
        val mapsDirectory = Files.createTempDirectory("scouty-no-asset-maps").toFile()
        val installer = BundledMapPackInstaller(FakeMapPackAssetSource())

        val copied = installer.ensureInstalled(mapsDirectory, MapPackId.ROMANIA_BASE)
        val inspected = MapPackRegistryManager.inspectPack(mapsDirectory, MapPackId.ROMANIA_BASE)

        assertFalse(copied)
        assertEquals(MapPackStatus.MISSING, inspected.status)
    }

    @Test
    fun inspectPack_findsPackInSecondaryDirectory() {
        val preferredDirectory = Files.createTempDirectory("scouty-preferred-maps").toFile()
        val legacyDirectory = Files.createTempDirectory("scouty-legacy-maps").toFile()
        val legacyPack = File(legacyDirectory, MapPackId.ROMANIA_BASE.fileName).apply {
            writeText("legacy-pack")
        }

        val installed = MapPackRegistryManager.inspectPack(
            searchDirectories = listOf(preferredDirectory, legacyDirectory),
            preferredDirectory = preferredDirectory,
            packId = MapPackId.ROMANIA_BASE
        )

        assertEquals(MapPackStatus.AVAILABLE, installed.status)
        assertEquals(legacyPack.absolutePath, installed.file.absolutePath)
    }

    @Test
    fun inspectPack_prefersMostRecentPackAcrossDirectories() {
        val preferredDirectory = Files.createTempDirectory("scouty-preferred-latest").toFile()
        val fallbackDirectory = Files.createTempDirectory("scouty-fallback-latest").toFile()
        File(preferredDirectory, MapPackId.ROMANIA_BASE.fileName).apply {
            writeText("older-pack")
            setLastModified(10L)
        }
        val newerPack = File(fallbackDirectory, MapPackId.ROMANIA_BASE.fileName).apply {
            writeText("newer-pack")
            setLastModified(20L)
        }

        val installed = MapPackRegistryManager.inspectPack(
            searchDirectories = listOf(preferredDirectory, fallbackDirectory),
            preferredDirectory = preferredDirectory,
            packId = MapPackId.ROMANIA_BASE
        )

        assertEquals(MapPackStatus.AVAILABLE, installed.status)
        assertEquals(newerPack.absolutePath, installed.file.absolutePath)
    }
}

private class FakeMapPackAssetSource(
    private val assets: Map<String, ByteArray> = emptyMap()
) : MapPackAssetSource {
    override fun exists(assetPath: String): Boolean = assets.containsKey(assetPath)

    override fun open(assetPath: String) =
        ByteArrayInputStream(requireNotNull(assets[assetPath]) { "Missing asset $assetPath" })
}
