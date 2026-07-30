package dev.antonlammers.trainist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.antonlammers.trainist.data.local.entity.BodyMeasurementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyMeasurementDao {
    @Query("SELECT * FROM body_measurements WHERE date = :date")
    fun entriesForDate(date: String): Flow<List<BodyMeasurementEntity>>

    @Query("SELECT * FROM body_measurements WHERE date >= :from AND date <= :to ORDER BY date ASC")
    fun entriesInRange(from: String, to: String): Flow<List<BodyMeasurementEntity>>

    @Query("SELECT * FROM body_measurements ORDER BY date ASC")
    suspend fun allEntries(): List<BodyMeasurementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: BodyMeasurementEntity)

    @Query("DELETE FROM body_measurements WHERE date = :date AND type = :type")
    suspend fun deleteByDateAndType(date: String, type: String)
}
