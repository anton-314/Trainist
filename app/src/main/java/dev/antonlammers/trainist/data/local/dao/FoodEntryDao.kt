package dev.antonlammers.trainist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.antonlammers.trainist.data.local.entity.FoodEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodEntryDao {
    @Query("SELECT * FROM food_entries WHERE date = :date ORDER BY timestampMs DESC")
    fun entriesForDate(date: String): Flow<List<FoodEntryEntity>>

    @Query("SELECT * FROM food_entries WHERE date >= :from AND date <= :to ORDER BY date ASC, timestampMs ASC")
    fun entriesInRange(from: String, to: String): Flow<List<FoodEntryEntity>>

    @Query("SELECT * FROM food_entries ORDER BY timestampMs DESC LIMIT :limit")
    fun recentEntries(limit: Int): Flow<List<FoodEntryEntity>>

    @Insert
    suspend fun insert(entry: FoodEntryEntity)

    /** One statement, so a multi-entry add (a saved meal) is atomic. */
    @Insert
    suspend fun insertAll(entries: List<FoodEntryEntity>)

    @Update
    suspend fun update(entry: FoodEntryEntity)

    @Query("SELECT * FROM food_entries ORDER BY date DESC, timestampMs DESC")
    suspend fun allEntries(): List<FoodEntryEntity>

    @Query("DELETE FROM food_entries WHERE id = :id")
    suspend fun delete(id: Long)
}
