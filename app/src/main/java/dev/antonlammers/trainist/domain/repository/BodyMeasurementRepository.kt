package dev.antonlammers.trainist.domain.repository

import dev.antonlammers.trainist.domain.model.BodyMeasurementEntry
import dev.antonlammers.trainist.domain.model.MeasurementType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface BodyMeasurementRepository {
    fun entriesForDate(date: LocalDate): Flow<List<BodyMeasurementEntry>>
    fun entriesInRange(from: LocalDate, to: LocalDate): Flow<List<BodyMeasurementEntry>>
    suspend fun allEntries(): List<BodyMeasurementEntry>

    /** Writes [values] for [date]: a non-null entry upserts, `null` deletes any existing entry for that type. */
    suspend fun save(date: LocalDate, values: Map<MeasurementType, Double?>)
}
