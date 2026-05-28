package dev.antonlammers.macromind.data.repository

import dev.antonlammers.macromind.data.local.dao.FoodEntryDao
import dev.antonlammers.macromind.data.local.entity.FoodEntryEntity
import dev.antonlammers.macromind.domain.model.FoodEntry
import dev.antonlammers.macromind.domain.repository.FoodEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class FoodEntryRepositoryImpl @Inject constructor(
    private val dao: FoodEntryDao,
) : FoodEntryRepository {

    override fun entriesForDate(date: LocalDate): Flow<List<FoodEntry>> =
        dao.entriesForDate(date.toString()).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun add(entry: FoodEntry) = dao.insert(entry.toEntity())

    override suspend fun delete(id: Long) = dao.delete(id)

    private fun FoodEntryEntity.toDomain() = FoodEntry(
        id = id,
        foodName = foodName,
        brand = brand,
        amountGrams = amountGrams,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        date = LocalDate.parse(date),
        timestampMs = timestampMs,
    )

    private fun FoodEntry.toEntity() = FoodEntryEntity(
        id = id,
        foodName = foodName,
        brand = brand,
        amountGrams = amountGrams,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        date = date.toString(),
        timestampMs = timestampMs,
    )
}
