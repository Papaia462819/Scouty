package com.scouty.app.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

enum class MapPackId(
    val storageName: String,
    val fileName: String,
    val required: Boolean,
    val bundledAssetPath: String
) {
    ROMANIA_BASE(
        storageName = "romania-base",
        fileName = "romania-base.pmtiles",
        required = true,
        bundledAssetPath = "romania-base.pmtiles"
    ),
    BUCEGI_HIGH(
        storageName = "bucegi-high",
        fileName = "bucegi-high.pmtiles",
        required = false,
        bundledAssetPath = "bucegi-high.pmtiles"
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

private data class MapPackStorage(
    val preferredDirectory: File,
    val searchDirectories: List<File>
)

object MapPackRegistryManager {
    private const val MapsDirectoryName = "maps"
    private const val LogTag = "ScoutyMapPacks"

    suspend fun load(context: Context): MapPackRegistry = withContext(Dispatchers.IO) {
        val storage = resolveStorage(context)
        val assetSource = AndroidAssetPackSource(context)
        val installer = BundledMapPackInstaller(assetSource)
        MapPackId.entries.forEach { packId ->
            if (inspectPack(storage.searchDirectories, storage.preferredDirectory, packId).isReady) {
                return@forEach
            }
            runCatching {
                installer.ensureInstalled(storage.preferredDirectory, packId)
            }.onSuccess { copied ->
                if (copied) {
                    Log.i(
                        LogTag,
                        "Installed bundled map pack ${packId.fileName} into ${storage.preferredDirectory.absolutePath}"
                    )
                }
            }.onFailure { error ->
                Log.w(LogTag, "Failed to bootstrap bundled map pack ${packId.fileName}", error)
            }
        }

        buildRegistry(
            mapsDirectory = storage.preferredDirectory,
            searchDirectories = storage.searchDirectories,
            hasLocalGlyphs = hasGlyphAssets(context)
        )
    }

    fun targetFile(context: Context, packId: MapPackId): File =
        File(resolveStorage(context).preferredDirectory, packId.fileName)

    fun copyImportedPack(
        context: Context,
        packId: MapPackId,
        sourceUri: Uri
    ): InstalledMapPack {
        val storage = resolveStorage(context)
        val targetFile = File(storage.preferredDirectory, packId.fileName)
        targetFile.parentFile?.mkdirs()
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

        return inspectPack(storage.searchDirectories, storage.preferredDirectory, packId)
    }

    private fun buildRegistry(
        mapsDirectory: File,
        searchDirectories: List<File>,
        hasLocalGlyphs: Boolean
    ): MapPackRegistry {
        val installedPacks = MapPackId.entries.associateWith { id ->
            inspectPack(searchDirectories, mapsDirectory, id)
        }
        return MapPackRegistry(
            mapsDirectory = mapsDirectory,
            installedPacks = installedPacks,
            hasLocalGlyphs = hasLocalGlyphs
        )
    }

    internal fun inspectPack(
        searchDirectories: List<File>,
        preferredDirectory: File,
        packId: MapPackId
    ): InstalledMapPack {
        val distinctDirectories = searchDirectories.distinctBy { it.absolutePath }
        val candidates = distinctDirectories.map { inspectPack(it, packId) }
        val availablePack = candidates
            .filter { it.status == MapPackStatus.AVAILABLE }
            .sortedWith(
                compareByDescending<InstalledMapPack> { it.file.lastModified() }
                    .thenBy { directoryPriority(it.file.parentFile, distinctDirectories) }
            )
            .firstOrNull()
        if (availablePack != null) {
            return availablePack
        }

        return candidates.firstOrNull { it.status == MapPackStatus.INVALID }
            ?: InstalledMapPack(
                id = packId,
                file = File(preferredDirectory, packId.fileName),
                status = MapPackStatus.MISSING
            )
    }

    internal fun inspectPack(mapsDirectory: File, packId: MapPackId): InstalledMapPack {
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

    private fun resolveStorage(context: Context): MapPackStorage {
        val internalDirectory = File(context.filesDir, MapsDirectoryName)
        val externalDirectory = context.getExternalFilesDir(null)?.let { File(it, MapsDirectoryName) }
        val preferredDirectory = externalDirectory ?: internalDirectory
        val searchDirectories = buildList {
            externalDirectory?.let(::add)
            add(internalDirectory)
        }.distinctBy { it.absolutePath }

        preferredDirectory.mkdirs()
        return MapPackStorage(
            preferredDirectory = preferredDirectory,
            searchDirectories = searchDirectories
        )
    }

    private fun directoryPriority(directory: File?, searchDirectories: List<File>): Int =
        directory?.let { candidate ->
            searchDirectories.indexOfFirst { it.absolutePath == candidate.absolutePath }
                .takeIf { it >= 0 }
        } ?: Int.MAX_VALUE
}

internal interface MapPackAssetSource {
    fun exists(assetPath: String): Boolean

    fun open(assetPath: String): InputStream
}

internal class BundledMapPackInstaller(
    private val assetSource: MapPackAssetSource
) {
    fun ensureInstalled(
        mapsDirectory: File,
        packId: MapPackId
    ): Boolean {
        val targetFile = File(mapsDirectory, packId.fileName)
        if (targetFile.exists() && targetFile.length() > 0L) {
            return false
        }
        if (!assetSource.exists(packId.bundledAssetPath)) {
            return false
        }

        mapsDirectory.mkdirs()
        val tempFile = File(mapsDirectory, "${packId.fileName}.part")
        assetSource.open(packId.bundledAssetPath).use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (targetFile.exists() && !targetFile.delete()) {
            error("Unable to replace existing bundled map pack ${packId.fileName}.")
        }
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
        return true
    }
}

private class AndroidAssetPackSource(
    private val context: Context
) : MapPackAssetSource {
    override fun exists(assetPath: String): Boolean =
        runCatching {
            context.assets.open(assetPath).close()
            true
        }.getOrDefault(false)

    override fun open(assetPath: String): InputStream =
        context.assets.open(assetPath)
}
