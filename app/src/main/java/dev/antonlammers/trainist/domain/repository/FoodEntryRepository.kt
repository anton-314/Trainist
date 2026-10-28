package dev.antonlammers.trainist.domain.repository

import dev.antonlammers.trainist.domain.model.FoodEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface FoodEntryRepository {
    fun entriesForDate(date: LocalDate): Flow<List<FoodEntry>>
    fun entriesInRange(from: LocalDate, to: LocalDate): Flow<List<FoodEntry>>
    fun recentEntries(limit: Int = 500): Flow<List<FoodEntry>>
    suspend fun allEntries(): List<FoodEntry>
    suspend fun add(entry: FoodEntry)

    /** Adds all [entries] at once — either every one of them is stored or none is. */
    suspend fun addAll(entries: List<FoodEntry>)

    suspend fun update(entry: FoodEntry)
    suspend fun delete(id: Long)
}
