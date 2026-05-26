package com.scouty.app.utils

import android.content.Context
import android.net.ConnectivityManager
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.scouty.app.BuildConfig
import java.io.File

enum class MapPackId(
    val storageName: String,
    val fileName: String,
    val required: Boolean,
    val bundledAssetPath: String
) {
    ROMANIA_BASE(
        storageName = "romania-high-detail",
        fileName = "romania-high-detail.pmtiles",
        required = true,
        bundledAssetPath = "romania-high-detail.pmtiles"
    ),
    ROUTE_OFFLINE(
        storageName = "route-offline",
        fileName = "offline.pmtiles",
        required = false,
        bundledAssetPath = ""
    );
}

enum class MapPackStatus {
    AVAILABLE,
    MISSING,
    INVALID,
    NOT_REQUESTED,
    WAITING_CONFIRMATION,
    DOWNLOADING,
    FAILED,
    STALE
}

data class InstalledMapPack(
    val id: MapPackId,
    val file: File,
    val status: MapPackStatus,
    val sizeBytes: Long = 0L,
    val version: String = "missing",
    val sourceUri: String? = null,
    val remoteUrl: String? = null,
    val trailCode: String? = null,
    val progressPercent: Int? = null,
    val message: String? = null,
    val requiresUserConfirmation: Boolean = false
) {
    val isReady: Boolean
        get() = status == MapPackStatus.AVAILABLE

    val isInProgress: Boolean
        get() = status == MapPackStatus.DOWNLOADING || status == MapPackStatus.WAITING_CONFIRMATION
}

data class MapPackRegistry(
    val mapsDirectory: File,
    val installedPacks: Map<MapPackId, InstalledMapPack>,
    val hasLocalGlyphs: Boolean,
    val isOnline: Boolean = true,
    val currentTrailPack: InstalledMapPack? = null,
    val activeSourceUri: String? = null
) {
    fun pack(id: MapPackId): InstalledMapPack = installedPacks.getValue(id)

    fun basePack(): InstalledMapPack = pack(MapPackId.ROMANIA_BASE)
}

object MapPackRegistryManager {
    private const val MapsDirectoryName = "maps"
    private const val RecentPackRetention = 3
    private const val MobileConfirmationThresholdBytes = 100L * 1024L * 1024L

    fun mapsBaseUrl(): String =
        BuildConfig.MAPS_BASE_URL.trim().trimEnd('/').ifBlank { "https://maps.scouty.app" }

    suspend fun load(
        context: Context,
        activeTrailCode: String? = null,
        isOnline: Boolean = true
    ): MapPackRegistry {
        val repository = MapPackRepository.get(context)
        return repository.registryFor(
            activeTrailCode = activeTrailCode?.takeIf { it.isNotBlank() },
            isOnline = isOnline
        )
    }

    fun enqueueRoutePackDownload(
        context: Context,
        trailCode: String,
        forceMetered: Boolean = false
    ) {
        val request = OneTimeWorkRequestBuilder<MapPackDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(
                workDataOf(
                    MapPackDownloadWorker.InputTrailCode to trailCode,
                    MapPackDownloadWorker.InputForceMetered to forceMetered
                )
            )
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "map-pack-$trailCode",
            ExistingWorkPolicy.REPLACE,
            request
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
            version = version,
            sourceUri = if (status == MapPackStatus.AVAILABLE) pmtilesFileUri(targetFile) else null
        )
    }

    internal fun resolveMapsDirectory(context: Context): File {
        val externalDirectory = context.getExternalFilesDir(null)?.let { File(it, MapsDirectoryName) }
        return (externalDirectory ?: File(context.filesDir, MapsDirectoryName)).apply { mkdirs() }
    }

    internal fun hasGlyphAssets(context: Context): Boolean =
        runCatching {
            val requiredRanges = listOf(
                "0-255.pbf",
                "256-511.pbf",
                "512-767.pbf",
                "768-1023.pbf"
            )
            listOf("Open Sans Regular", "Open Sans Semibold").all { fontStack ->
                val availableRanges = context.assets.list("glyphs/$fontStack").orEmpty().toSet()
                requiredRanges.all(availableRanges::contains)
            }
        }.getOrDefault(false)

    internal fun remoteMasterUri(): String =
        "pmtiles://${mapsBaseUrl()}/base/${MapPackId.ROMANIA_BASE.fileName}"

    internal fun pmtilesFileUri(file: File): String =
        "pmtiles://file://${file.absolutePath.replace(File.separatorChar, '/')}"

    internal fun mobileConfirmationThresholdBytes(): Long = MobileConfirmationThresholdBytes

    internal fun recentPackRetention(): Int = RecentPackRetention

    internal fun isActiveNetworkMetered(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivityManager.isActiveNetworkMetered
    }
}
