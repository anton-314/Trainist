package dev.antonlammers.macrotrac.data.repository

import dev.antonlammers.macrotrac.data.local.dao.WeightEntryDao
import dev.antonlammers.macrotrac.data.local.entity.WeightEntryEntity
import dev.antonlammers.macrotrac.domain.model.WeightEntry
import dev.antonlammers.macrotrac.domain.repository.WeightRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class WeightRepositoryImpl @Inject constructor(
    private val dao: WeightEntryDao,
) : WeightRepository {

    override fun entryForDate(date: LocalDate): Flow<WeightEntry?> =
        dao.entryForDate(date.toString()).map { it?.toDomain() }

    override fun entriesInRange(from: LocalDate, to: LocalDate): Flow<List<WeightEntry>> =
        dao.entriesInRange(from.toString(), to.toString()).map { list -> list.map { it.toDomain() } }

    override suspend fun allEntries(): List<WeightEntry> = dao.allEntries().map { it.toDomain() }

    override suspend fun save(entry: WeightEntry) = dao.insert(entry.toEntity())

    private fun WeightEntryEntity.toDomain() = WeightEntry(
        id = id,
        weightKg = weightKg,
        date = LocalDate.parse(date),
        timestampMs = timestampMs,
    )

    private fun WeightEntry.toEntity() = WeightEntryEntity(
        weightKg = weightKg,
        date = date.toString(),
        timestampMs = timestampMs,
    )
}
