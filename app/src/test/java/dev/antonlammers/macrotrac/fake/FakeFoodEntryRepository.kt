package dev.antonlammers.macrotrac.fake

import dev.antonlammers.macrotrac.domain.model.FoodEntry
import dev.antonlammers.macrotrac.domain.repository.FoodEntryRepository
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

    override fun entriesInRange(from: LocalDate, to: LocalDate): Flow<List<FoodEntry>> =
        _entries.map { list ->
            list.filter { it.date >= from && it.date <= to }.sortedBy { it.date }
        }

    override fun recentFoods(limit: Int): Flow<List<FoodEntry>> =
        _entries.map { list ->
            list.sortedByDescending { it.timestampMs }
                .distinctBy { it.foodName }
                .take(limit)
        }

    override fun recentEntries(limit: Int): Flow<List<FoodEntry>> =
        _entries.map { list ->
            list.sortedByDescending { it.timestampMs }.take(limit)
        }

    override suspend fun allEntries(): List<FoodEntry> = _entries.value

    override suspend fun add(entry: FoodEntry) {
        _entries.update { it + entry.copy(id = nextId++) }
    }

    override suspend fun update(entry: FoodEntry) {
        _entries.update { list -> list.map { if (it.id == entry.id) entry else it } }
    }

    override suspend fun delete(id: Long) {
        _entries.update { it.filterNot { e -> e.id == id } }
    }
}
