package com.scouty.app.utils

import android.content.Context
import android.net.Uri
import java.io.File

enum class MapPackId(
    val storageName: String,
    val fileName: String,
    val required: Boolean
) {
    ROMANIA_BASE(
        storageName = "romania-base",
        fileName = "romania-base.pmtiles",
        required = true
    ),
    BUCEGI_HIGH(
        storageName = "bucegi-high",
        fileName = "bucegi-high.pmtiles",
        required = false
    );
}

enum class MapPackStatus {
    AVAILABLE,
    MISSING,
    INVALID
}

data class InstalledMapPack(
    val id: MapPackId,
    val file: File,
    val status: MapPackStatus,
    val sizeBytes: Long = 0L,
    val version: String = "missing"
) {
    val isReady: Boolean
        get() = status == MapPackStatus.AVAILABLE
}

data class MapPackRegistry(
    val mapsDirectory: File,
    val installedPacks: Map<MapPackId, InstalledMapPack>,
    val hasLocalGlyphs: Boolean
) {
    fun pack(id: MapPackId): InstalledMapPack = installedPacks.getValue(id)

    fun basePack(): InstalledMapPack = pack(MapPackId.ROMANIA_BASE)

    fun demoPack(): InstalledMapPack = pack(MapPackId.BUCEGI_HIGH)
}

object MapPackRegistryManager {
    private const val MapsDirectoryName = "maps"

    fun load(context: Context): MapPackRegistry {
        val mapsDirectory = File(context.filesDir, MapsDirectoryName).apply { mkdirs() }
        val installedPacks = MapPackId.entries.associateWith { id ->
            inspectPack(mapsDirectory, id)
        }

        return MapPackRegistry(
            mapsDirectory = mapsDirectory,
            installedPacks = installedPacks,
            hasLocalGlyphs = hasGlyphAssets(context)
        )
    }

    fun targetFile(context: Context, packId: MapPackId): File =
        File(File(context.filesDir, MapsDirectoryName).apply { mkdirs() }, packId.fileName)

    fun copyImportedPack(
        context: Context,
        packId: MapPackId,
        sourceUri: Uri
    ): InstalledMapPack {
        val targetFile = targetFile(context, packId)
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.part")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("Unable to open selected map pack.")

        if (targetFile.exists() && !targetFile.delete()) {
            error("Unable to replace existing map pack.")
        }
        if (!tempFile.renameTo(targetFile)) {
            error("Unable to finalize imported map pack.")
        }

        return inspectPack(targetFile.parentFile ?: File(context.filesDir, MapsDirectoryName), packId)
    }

    private fun inspectPack(mapsDirectory: File, packId: MapPackId): InstalledMapPack {
        val targetFile = File(mapsDirectory, packId.fileName)
        if (!targetFile.exists()) {
            return InstalledMapPack(
                id = packId,
                file = targetFile,
                status = MapPackStatus.MISSING
            )
        }

        val sizeBytes = targetFile.length()
        val status = if (sizeBytes > 0L) MapPackStatus.AVAILABLE else MapPackStatus.INVALID
        val version = if (status == MapPackStatus.AVAILABLE) {
            "local-${targetFile.lastModified()}"
        } else {
            "invalid"
        }

        return InstalledMapPack(
            id = packId,
            file = targetFile,
            status = status,
            sizeBytes = sizeBytes,
            version = version
        )
    }

    private fun hasGlyphAssets(context: Context): Boolean =
        runCatching {
            context.assets.list("glyphs").orEmpty().isNotEmpty()
        }.getOrDefault(false)
}
