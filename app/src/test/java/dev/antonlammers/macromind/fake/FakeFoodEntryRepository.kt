package dev.antonlammers.macromind.fake

import dev.antonlammers.macromind.domain.model.FoodEntry
import dev.antonlammers.macromind.domain.repository.FoodEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

class FakeFoodEntryRepository : FoodEntryRepository {

    private val _entries = MutableStateFlow<List<FoodEntry>>(emptyList())
    private var nextId = 1L

    override fun entriesForDate(date: LocalDate): Flow<List<FoodEntry>> =
        _entries.map { list -> list.filter { it.date == date } }

    override suspend fun allEntries(): List<FoodEntry> = _entries.value

    override suspend fun add(entry: FoodEntry) {
        _entries.update { it + entry.copy(id = nextId++) }
    }

    override suspend fun delete(id: Long) {
        _entries.update { it.filterNot { e -> e.id == id } }
    }
}
