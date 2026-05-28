package dev.antonlammers.macromind.domain.repository

import dev.antonlammers.macromind.domain.model.FoodEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface FoodEntryRepository {
    fun entriesForDate(date: LocalDate): Flow<List<FoodEntry>>
    suspend fun allEntries(): List<FoodEntry>
    suspend fun add(entry: FoodEntry)
    suspend fun delete(id: Long)
}
