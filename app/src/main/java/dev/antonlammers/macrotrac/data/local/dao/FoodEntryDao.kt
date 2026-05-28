package dev.antonlammers.macrotrac.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.antonlammers.macrotrac.data.local.entity.FoodEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodEntryDao {
    @Query("SELECT * FROM food_entries WHERE date = :date ORDER BY timestampMs DESC")
    fun entriesForDate(date: String): Flow<List<FoodEntryEntity>>

    @Insert
    suspend fun insert(entry: FoodEntryEntity)

    @Query("SELECT * FROM food_entries ORDER BY date DESC, timestampMs DESC")
    suspend fun allEntries(): List<FoodEntryEntity>

    @Query("DELETE FROM food_entries WHERE id = :id")
    suspend fun delete(id: Long)
}
