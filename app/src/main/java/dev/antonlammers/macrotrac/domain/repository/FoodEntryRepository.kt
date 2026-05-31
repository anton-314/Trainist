package dev.antonlammers.macrotrac.domain.repository

import dev.antonlammers.macrotrac.domain.model.FoodEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface FoodEntryRepository {
    fun entriesForDate(date: LocalDate): Flow<List<FoodEntry>>
    fun entriesInRange(from: LocalDate, to: LocalDate): Flow<List<FoodEntry>>
    fun recentFoods(limit: Int = 15): Flow<List<FoodEntry>>
    fun recentEntries(limit: Int = 500): Flow<List<FoodEntry>>
    suspend fun allEntries(): List<FoodEntry>
    suspend fun add(entry: FoodEntry)
    suspend fun update(entry: FoodEntry)
    suspend fun delete(id: Long)
}
