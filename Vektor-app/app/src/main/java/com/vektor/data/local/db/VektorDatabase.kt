package com.vektor.data.local.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "emergency_queue")
data class EmergencyPayloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uid: String,
    val payloadJson: String,
    val timestamp: Long,
    val synced: Boolean = false
)

@Dao
interface EmergencyQueueDao {
    @Insert
    suspend fun insertPayload(payload: EmergencyPayloadEntity)

    @Query("SELECT * FROM emergency_queue WHERE synced = 0")
    suspend fun getUnsyncedPayloads(): List<EmergencyPayloadEntity>

    @Query("UPDATE emergency_queue SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Int)
}

@Database(entities = [EmergencyPayloadEntity::class], version = 1, exportSchema = false)
abstract class VektorDatabase : RoomDatabase() {
    abstract fun emergencyQueueDao(): EmergencyQueueDao
}
