package dev.antonlammers.macrotrac.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.antonlammers.macrotrac.data.local.entity.WeightEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightEntryDao {
    @Query("SELECT * FROM weight_entries WHERE date = :date LIMIT 1")
    fun entryForDate(date: String): Flow<WeightEntryEntity?>

    @Query("SELECT * FROM weight_entries WHERE date >= :from AND date <= :to ORDER BY date ASC")
    fun entriesInRange(from: String, to: String): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entries ORDER BY date ASC")
    suspend fun allEntries(): List<WeightEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WeightEntryEntity)
}
