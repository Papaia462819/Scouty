package com.scouty.app.utils

import android.content.Context
import android.util.Log
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

@Entity(tableName = "map_packs")
data class MapPackEntity(
    @PrimaryKey val trailCode: String,
    val status: String,
    val localPath: String? = null,
    val remoteUrl: String? = null,
    val version: String = "missing",
    val sizeBytes: Long = 0L,
    val sha256: String? = null,
    val minLat: Double? = null,
    val minLon: Double? = null,
    val maxLat: Double? = null,
    val maxLon: Double? = null,
    val progressPercent: Int? = null,
    val lastUsedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isCurrentTrail: Boolean = false,
    val message: String? = null
)

@Dao
interface MapPackDao {
    @Query("SELECT * FROM map_packs WHERE trailCode = :trailCode")
    suspend fun get(trailCode: String): MapPackEntity?

    @Query("SELECT * FROM map_packs WHERE isCurrentTrail = 1 ORDER BY lastUsedAt DESC LIMIT 1")
    suspend fun currentTrail(): MapPackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MapPackEntity)

    @Query("UPDATE map_packs SET isCurrentTrail = 0 WHERE isCurrentTrail = 1 AND trailCode != :trailCode")
    suspend fun clearOtherCurrentTrails(trailCode: String)

    @Query("UPDATE map_packs SET isCurrentTrail = 0 WHERE isCurrentTrail = 1")
    suspend fun clearCurrentTrail()

    @Query("SELECT * FROM map_packs WHERE isCurrentTrail = 0 AND status = :status ORDER BY lastUsedAt DESC")
    suspend fun recentByStatus(status: String): List<MapPackEntity>

    @Query("DELETE FROM map_packs WHERE trailCode = :trailCode")
    suspend fun delete(trailCode: String)
}

@Database(entities = [MapPackEntity::class], version = 1, exportSchema = false)
abstract class MapPackDatabase : RoomDatabase() {
    abstract fun mapPackDao(): MapPackDao

    companion object {
        @Volatile
        private var instance: MapPackDatabase? = null

        fun get(context: Context): MapPackDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MapPackDatabase::class.java,
                    "scouty_map_packs.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}

internal fun MapPackEntity.toInstalledPack(): InstalledMapPack {
    val status = runCatching { MapPackStatus.valueOf(status) }
        .getOrDefault(MapPackStatus.FAILED)
    val file = localPath?.let(::File) ?: File("")
    val ready = status == MapPackStatus.AVAILABLE && file.exists() && file.length() > 0L
    return InstalledMapPack(
        id = MapPackId.ROUTE_OFFLINE,
        file = file,
        status = if (ready) MapPackStatus.AVAILABLE else status,
        sizeBytes = if (ready) file.length() else sizeBytes,
        version = version,
        sourceUri = if (ready) MapPackRegistryManager.pmtilesFileUri(file) else null,
        remoteUrl = remoteUrl,
        trailCode = trailCode,
        progressPercent = progressPercent,
        message = message,
        requiresUserConfirmation = status == MapPackStatus.WAITING_CONFIRMATION
    )
}

internal fun deletePackFile(entity: MapPackEntity) {
    val path = entity.localPath ?: return
    runCatching {
        File(path).takeIf { it.exists() }?.delete()
    }.onFailure { error ->
        Log.w("ScoutyMapPacks", "Failed to delete stale map pack $path", error)
    }
}
