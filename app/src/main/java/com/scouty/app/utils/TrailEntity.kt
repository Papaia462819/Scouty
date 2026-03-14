package com.scouty.app.utils

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Database
import androidx.room.RoomDatabase

@Entity(tableName = "trail_difficulty")
data class TrailEntity(
    @PrimaryKey val id: String, // Usually the 'name' or a unique ID from GeoJSON
    val name: String,
    val difficulty: String,
    val totalAscent: Double,
    val totalDescent: Double,
    val avgIncline: Double,
    val lengthKm: Double,
    val durationHours: Double
)

@Dao
interface TrailDao {
    @Query("SELECT * FROM trail_difficulty WHERE id = :id")
    suspend fun getTrail(id: String): TrailEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrail(trail: TrailEntity)

    @Query("SELECT * FROM trail_difficulty")
    suspend fun getAll(): List<TrailEntity>
}

@Database(entities = [TrailEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trailDao(): TrailDao
}
