package com.scouty.app.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

class MapPackRepository private constructor(
    private val context: Context,
    private val dao: MapPackDao,
    private val httpClient: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mapsDirectory: File
        get() = MapPackRegistryManager.resolveMapsDirectory(context)

    suspend fun registryFor(
        activeTrailCode: String?,
        isOnline: Boolean
    ): MapPackRegistry = withContext(Dispatchers.IO) {
        val currentEntity = if (activeTrailCode != null) {
            dao.get(activeTrailCode)
        } else {
            dao.currentTrail()
        }
        val resolvedTrailCode = activeTrailCode ?: currentEntity?.trailCode
        val storedPack = currentEntity?.toInstalledPack()
        val currentPack = when {
            storedPack?.isReady == true -> storedPack
            resolvedTrailCode != null -> inspectPreloadedRoutePack(resolvedTrailCode) ?: storedPack
            else -> storedPack
        }
        val remoteBase = InstalledMapPack(
            id = MapPackId.ROMANIA_BASE,
            file = File(mapsDirectory, MapPackId.ROMANIA_BASE.fileName),
            status = if (isOnline) MapPackStatus.AVAILABLE else MapPackStatus.MISSING,
            version = "remote:${MapPackRegistryManager.mapsBaseUrl()}",
            sourceUri = if (isOnline) MapPackRegistryManager.remoteMasterUri() else null,
            remoteUrl = "${MapPackRegistryManager.mapsBaseUrl()}/base/${MapPackId.ROMANIA_BASE.fileName}"
        )
        val activeSourceUri = when {
            isOnline -> remoteBase.sourceUri
            currentPack?.isReady == true -> currentPack.sourceUri
            else -> null
        }
        val activeBasePack = if (currentPack != null && activeSourceUri == currentPack.sourceUri) {
            currentPack.copy(id = MapPackId.ROMANIA_BASE)
        } else {
            remoteBase
        }

        MapPackRegistry(
            mapsDirectory = mapsDirectory,
            installedPacks = mapOf(
                MapPackId.ROMANIA_BASE to activeBasePack,
                MapPackId.ROUTE_OFFLINE to (currentPack ?: missingRoutePack(resolvedTrailCode))
            ),
            hasLocalGlyphs = MapPackRegistryManager.hasGlyphAssets(context),
            isOnline = isOnline,
            currentTrailPack = currentPack,
            activeSourceUri = activeSourceUri
        )
    }

    suspend fun markActiveTrail(trailCode: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.clearOtherCurrentTrails(trailCode)
        val existing = dao.get(trailCode)
        dao.upsert(
            existing?.copy(
                isCurrentTrail = true,
                lastUsedAt = now,
                updatedAt = now
            ) ?: MapPackEntity(
                trailCode = trailCode,
                status = MapPackStatus.NOT_REQUESTED.name,
                lastUsedAt = now,
                updatedAt = now,
                isCurrentTrail = true,
                message = "Pachetul de hartă locală este în așteptare."
            )
        )
        cleanupRecentPacks()
    }

    suspend fun releaseCurrentTrail() = withContext(Dispatchers.IO) {
        dao.clearCurrentTrail()
        cleanupRecentPacks()
    }

    fun enqueueDownload(trailCode: String, forceMetered: Boolean = false) {
        MapPackRegistryManager.enqueueRoutePackDownload(context, trailCode, forceMetered)
    }

    suspend fun downloadRoutePack(
        trailCode: String,
        forceMetered: Boolean
    ): MapPackDownloadResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = dao.get(trailCode)
        dao.upsert(
            (existing ?: MapPackEntity(
                trailCode = trailCode,
                status = MapPackStatus.NOT_REQUESTED.name,
                isCurrentTrail = true
            )).copy(
                status = MapPackStatus.DOWNLOADING.name,
                progressPercent = 0,
                updatedAt = now,
                message = "Se citește manifestul hărții de traseu."
            )
        )

        runCatching {
            val manifest = fetchManifest(trailCode)
            val sizeBytes = manifest.resolvedSizeBytes ?: 0L
            if (
                !forceMetered &&
                sizeBytes > MapPackRegistryManager.mobileConfirmationThresholdBytes() &&
                MapPackRegistryManager.isActiveNetworkMetered(context)
            ) {
                upsertFromManifest(
                    trailCode = trailCode,
                    manifest = manifest,
                    status = MapPackStatus.WAITING_CONFIRMATION,
                    progressPercent = null,
                    message = "Descărcarea depășește 100 MB pe date mobile."
                )
                return@withContext MapPackDownloadResult.WaitingForConfirmation
            }

            val targetFile = targetFileFor(trailCode)
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.part")
            targetFile.parentFile?.mkdirs()
            downloadToFile(
                trailCode = trailCode,
                remoteUrl = manifest.resolvedRemoteUrl(trailCode),
                targetFile = tempFile,
                expectedSizeBytes = manifest.resolvedSizeBytes,
                expectedSha256 = manifest.sha256
            )
            if (targetFile.exists() && !targetFile.delete()) {
                error("Nu pot înlocui pachetul existent de hartă pentru $trailCode.")
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            upsertFromManifest(
                trailCode = trailCode,
                manifest = manifest,
                status = MapPackStatus.AVAILABLE,
                localPath = targetFile.absolutePath,
                progressPercent = 100,
                message = "Harta locală este pregătită."
            )
            cleanupRecentPacks()
            MapPackDownloadResult.Ready
        }.getOrElse { error ->
            Log.w("ScoutyMapPacks", "Failed to download route map pack for $trailCode", error)
            val failed = dao.get(trailCode) ?: MapPackEntity(
                trailCode = trailCode,
                status = MapPackStatus.FAILED.name
            )
            dao.upsert(
                failed.copy(
                    status = MapPackStatus.FAILED.name,
                    progressPercent = null,
                    updatedAt = System.currentTimeMillis(),
                    message = error.message ?: "Descărcarea hărții locale a eșuat."
                )
            )
            MapPackDownloadResult.Failed
        }
    }

    private fun missingRoutePack(trailCode: String?): InstalledMapPack =
        InstalledMapPack(
            id = MapPackId.ROUTE_OFFLINE,
            file = trailCode?.let(::targetFileFor) ?: File(mapsDirectory, MapPackId.ROUTE_OFFLINE.fileName),
            status = if (trailCode == null) MapPackStatus.NOT_REQUESTED else MapPackStatus.MISSING,
            trailCode = trailCode
        )

    private fun targetFileFor(trailCode: String): File =
        File(File(mapsDirectory, "trails/${safePathSegment(trailCode)}"), MapPackId.ROUTE_OFFLINE.fileName)

    private fun inspectPreloadedRoutePack(trailCode: String): InstalledMapPack? {
        val file = targetFileFor(trailCode)
        if (!file.exists() || file.length() <= 0L) {
            return null
        }
        return InstalledMapPack(
            id = MapPackId.ROUTE_OFFLINE,
            file = file,
            status = MapPackStatus.AVAILABLE,
            sizeBytes = file.length(),
            version = "local-${file.lastModified()}",
            sourceUri = MapPackRegistryManager.pmtilesFileUri(file),
            trailCode = trailCode,
            message = "Harta locală este pregătită."
        )
    }

    private fun fetchManifest(trailCode: String): RouteMapPackManifest {
        val url = "${MapPackRegistryManager.mapsBaseUrl()}/trails/${safePathSegment(trailCode)}/manifest.json"
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Cererea manifestului a eșuat cu HTTP ${response.code}.")
            }
            val body = response.body?.string().orEmpty()
            return json.decodeFromString(RouteMapPackManifest.serializer(), body)
        }
    }

    private suspend fun downloadToFile(
        trailCode: String,
        remoteUrl: String,
        targetFile: File,
        expectedSizeBytes: Long?,
        expectedSha256: String?
    ) {
        val request = Request.Builder().url(remoteUrl).build()
        val digest = MessageDigest.getInstance("SHA-256")
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Cererea pachetului a eșuat cu HTTP ${response.code}.")
            }
            val body = response.body ?: error("Răspuns gol pentru pachetul hărții de traseu.")
            val totalBytes = expectedSizeBytes ?: body.contentLength().takeIf { it > 0L }
            var copiedBytes = 0L
            var nextProgressUpdate = 0
            targetFile.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        copiedBytes += read
                        if (totalBytes != null && totalBytes > 0L) {
                            val progress = ((copiedBytes * 100L) / totalBytes).toInt().coerceIn(0, 99)
                            if (progress >= nextProgressUpdate) {
                                updateProgress(trailCode, progress)
                                nextProgressUpdate = progress + 5
                            }
                        }
                    }
                }
            }

            if (expectedSizeBytes != null && expectedSizeBytes > 0L && copiedBytes != expectedSizeBytes) {
                targetFile.delete()
                error("Dimensiunea descărcată nu corespunde pentru $trailCode.")
            }
            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (!expectedSha256.isNullOrBlank() && !actualSha256.equals(expectedSha256, ignoreCase = true)) {
                targetFile.delete()
                error("Verificarea descărcării nu corespunde pentru $trailCode.")
            }
        }
    }

    private suspend fun updateProgress(trailCode: String, progressPercent: Int) {
        val entity = dao.get(trailCode) ?: return
        dao.upsert(
            entity.copy(
                progressPercent = progressPercent,
                updatedAt = System.currentTimeMillis(),
                message = "Se descarcă harta traseului $progressPercent%."
            )
        )
    }

    private suspend fun upsertFromManifest(
        trailCode: String,
        manifest: RouteMapPackManifest,
        status: MapPackStatus,
        localPath: String? = null,
        progressPercent: Int?,
        message: String
    ) {
        val existing = dao.get(trailCode)
        val bbox = manifest.bbox
        dao.upsert(
            (existing ?: MapPackEntity(trailCode = trailCode, status = status.name)).copy(
                status = status.name,
                localPath = localPath ?: existing?.localPath,
                remoteUrl = manifest.resolvedRemoteUrl(trailCode),
                version = manifest.version ?: manifest.generatedAtUtc ?: "remote",
                sizeBytes = manifest.resolvedSizeBytes ?: existing?.sizeBytes ?: 0L,
                sha256 = manifest.sha256 ?: existing?.sha256,
                minLat = bbox?.resolvedMinLat,
                minLon = bbox?.resolvedMinLon,
                maxLat = bbox?.resolvedMaxLat,
                maxLon = bbox?.resolvedMaxLon,
                progressPercent = progressPercent,
                lastUsedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isCurrentTrail = existing?.isCurrentTrail ?: true,
                message = message
            )
        )
    }

    private suspend fun cleanupRecentPacks() {
        val recent = dao.recentByStatus(MapPackStatus.AVAILABLE.name)
        recent.drop(MapPackRegistryManager.recentPackRetention()).forEach { stale ->
            deletePackFile(stale)
            dao.delete(stale.trailCode)
        }
    }

    private fun RouteMapPackManifest.resolvedRemoteUrl(trailCode: String): String =
        url?.takeIf { it.isNotBlank() }
            ?: "${MapPackRegistryManager.mapsBaseUrl()}/trails/${safePathSegment(trailCode)}/${file?.takeIf { it.isNotBlank() } ?: MapPackId.ROUTE_OFFLINE.fileName}"

    companion object {
        @Volatile
        private var instance: MapPackRepository? = null

        fun get(context: Context): MapPackRepository =
            instance ?: synchronized(this) {
                val appContext = context.applicationContext
                instance ?: MapPackRepository(
                    context = appContext,
                    dao = MapPackDatabase.get(appContext).mapPackDao(),
                    httpClient = OkHttpClient.Builder()
                        .connectTimeout(20, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .build()
                ).also { instance = it }
            }
    }
}

sealed class MapPackDownloadResult {
    object Ready : MapPackDownloadResult()
    object WaitingForConfirmation : MapPackDownloadResult()
    object Failed : MapPackDownloadResult()
}

@Serializable
data class RouteMapPackManifest(
    val id: String? = null,
    val version: String? = null,
    val url: String? = null,
    val file: String? = null,
    val sha256: String? = null,
    val bbox: RouteMapPackBbox? = null,
    @SerialName("generated_at_utc")
    val generatedAtUtc: String? = null,
    @SerialName("size_bytes")
    val sizeBytesSnake: Long? = null,
    val sizeBytes: Long? = null
) {
    val resolvedSizeBytes: Long?
        get() = sizeBytes ?: sizeBytesSnake
}

@Serializable
data class RouteMapPackBbox(
    @SerialName("min_lat")
    val minLatSnake: Double? = null,
    @SerialName("min_lon")
    val minLonSnake: Double? = null,
    @SerialName("max_lat")
    val maxLatSnake: Double? = null,
    @SerialName("max_lon")
    val maxLonSnake: Double? = null,
    @SerialName("minLat")
    val minLatCamel: Double? = null,
    @SerialName("minLon")
    val minLonCamel: Double? = null,
    @SerialName("maxLat")
    val maxLatCamel: Double? = null,
    @SerialName("maxLon")
    val maxLonCamel: Double? = null
) {
    val resolvedMinLat: Double?
        get() = minLatCamel ?: minLatSnake
    val resolvedMinLon: Double?
        get() = minLonCamel ?: minLonSnake
    val resolvedMaxLat: Double?
        get() = maxLatCamel ?: maxLatSnake
    val resolvedMaxLon: Double?
        get() = maxLonCamel ?: maxLonSnake
}

private fun safePathSegment(value: String): String =
    value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9._-]+"), "_").trim('_').ifBlank { "route" }
