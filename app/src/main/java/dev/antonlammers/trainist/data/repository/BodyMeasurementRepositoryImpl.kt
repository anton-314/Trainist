package dev.antonlammers.trainist.data.repository

import dev.antonlammers.trainist.data.local.dao.BodyMeasurementDao
import dev.antonlammers.trainist.data.local.entity.BodyMeasurementEntity
import dev.antonlammers.trainist.domain.model.BodyMeasurementEntry
import dev.antonlammers.trainist.domain.model.MeasurementType
import dev.antonlammers.trainist.domain.repository.BodyMeasurementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class BodyMeasurementRepositoryImpl @Inject constructor(
    private val dao: BodyMeasurementDao,
) : BodyMeasurementRepository {

    override fun entriesForDate(date: LocalDate): Flow<List<BodyMeasurementEntry>> =
        dao.entriesForDate(date.toString()).map { list -> list.mapNotNull { it.toDomain() } }

    override fun entriesInRange(from: LocalDate, to: LocalDate): Flow<List<BodyMeasurementEntry>> =
        dao.entriesInRange(from.toString(), to.toString()).map { list -> list.mapNotNull { it.toDomain() } }

    override suspend fun allEntries(): List<BodyMeasurementEntry> =
        dao.allEntries().mapNotNull { it.toDomain() }

    override suspend fun save(date: LocalDate, values: Map<MeasurementType, Double?>) {
        values.forEach { (type, value) ->
            if (value != null) {
                dao.upsert(BodyMeasurementEntity(date = date.toString(), type = type.name, valueCm = value))
            } else {
                dao.deleteByDateAndType(date.toString(), type.name)
            }
        }
    }

    private fun BodyMeasurementEntity.toDomain(): BodyMeasurementEntry? {
        val parsedType = MeasurementType.parse(type) ?: return null
        return BodyMeasurementEntry(id = id, date = LocalDate.parse(date), type = parsedType, valueCm = valueCm)
    }
}
