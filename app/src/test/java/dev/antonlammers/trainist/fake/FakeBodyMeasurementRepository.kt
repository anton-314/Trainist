package dev.antonlammers.trainist.fake

import dev.antonlammers.trainist.domain.model.BodyMeasurementEntry
import dev.antonlammers.trainist.domain.model.MeasurementType
import dev.antonlammers.trainist.domain.repository.BodyMeasurementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

class FakeBodyMeasurementRepository : BodyMeasurementRepository {

    private val _entries = MutableStateFlow<List<BodyMeasurementEntry>>(emptyList())
    private var nextId = 1L

    override fun entriesForDate(date: LocalDate): Flow<List<BodyMeasurementEntry>> =
        _entries.map { list -> list.filter { it.date == date } }

    override fun entriesInRange(from: LocalDate, to: LocalDate): Flow<List<BodyMeasurementEntry>> =
        _entries.map { list -> list.filter { it.date >= from && it.date <= to }.sortedBy { it.date } }

    override suspend fun allEntries(): List<BodyMeasurementEntry> = _entries.value.sortedBy { it.date }

    override suspend fun save(date: LocalDate, values: Map<MeasurementType, Double?>) {
        _entries.update { list ->
            val kept = list.filterNot { it.date == date && it.type in values.keys }
            val upserted = values.mapNotNull { (type, value) ->
                value?.let { BodyMeasurementEntry(id = nextId++, date = date, type = type, valueCm = it) }
            }
            kept + upserted
        }
    }
}
