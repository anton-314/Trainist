package dev.antonlammers.macrotrac.fake

import dev.antonlammers.macrotrac.domain.model.WeightEntry
import dev.antonlammers.macrotrac.domain.repository.WeightRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

class FakeWeightRepository : WeightRepository {

    private val _entries = MutableStateFlow<List<WeightEntry>>(emptyList())
    private var nextId = 1L

    override fun entryForDate(date: LocalDate): Flow<WeightEntry?> =
        _entries.map { list -> list.filter { it.date == date }.maxByOrNull { it.timestampMs } }

    override fun entriesInRange(from: LocalDate, to: LocalDate): Flow<List<WeightEntry>> =
        _entries.map { list ->
            list.filter { it.date >= from && it.date <= to }.sortedBy { it.date }
        }

    override suspend fun allEntries(): List<WeightEntry> =
        _entries.value.sortedBy { it.date }

    override suspend fun save(entry: WeightEntry) {
        _entries.update { list ->
            list.filterNot { it.date == entry.date } + entry.copy(id = nextId++)
        }
    }
}
