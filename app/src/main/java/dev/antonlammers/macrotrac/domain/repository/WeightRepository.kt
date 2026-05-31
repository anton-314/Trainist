package dev.antonlammers.macrotrac.domain.repository

import dev.antonlammers.macrotrac.domain.model.WeightEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface WeightRepository {
    fun entryForDate(date: LocalDate): Flow<WeightEntry?>
    fun entriesInRange(from: LocalDate, to: LocalDate): Flow<List<WeightEntry>>
    suspend fun allEntries(): List<WeightEntry>
    suspend fun save(entry: WeightEntry)
}
