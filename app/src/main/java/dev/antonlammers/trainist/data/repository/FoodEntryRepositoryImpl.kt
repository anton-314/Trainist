package dev.antonlammers.trainist.data.repository

import dev.antonlammers.trainist.data.local.dao.FoodEntryDao
import dev.antonlammers.trainist.data.local.entity.FoodEntryEntity
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.domain.model.FoodTag
import dev.antonlammers.trainist.domain.model.MealCategory
import dev.antonlammers.trainist.domain.repository.FoodEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class FoodEntryRepositoryImpl @Inject constructor(
    private val dao: FoodEntryDao,
) : FoodEntryRepository {

    override fun entriesForDate(date: LocalDate): Flow<List<FoodEntry>> =
        dao.entriesForDate(date.toString()).map { entities -> entities.map { it.toDomain() } }

    override fun entriesInRange(from: LocalDate, to: LocalDate): Flow<List<FoodEntry>> =
        dao.entriesInRange(from.toString(), to.toString()).map { entities -> entities.map { it.toDomain() } }

    override fun recentEntries(limit: Int): Flow<List<FoodEntry>> =
        dao.recentEntries(limit).map { entities -> entities.map { it.toDomain() } }

    override suspend fun allEntries(): List<FoodEntry> = dao.allEntries().map { it.toDomain() }

    override suspend fun add(entry: FoodEntry) = dao.insert(entry.toEntity())

    override suspend fun addAll(entries: List<FoodEntry>) = dao.insertAll(entries.map { it.toEntity() })

    override suspend fun update(entry: FoodEntry) = dao.update(entry.toEntity())

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
        sugarG = sugarG,
        fiberG = fiberG,
        saltG = saltG,
        mealCategory = runCatching { MealCategory.valueOf(mealCategory) }.getOrDefault(MealCategory.SNACK),
        tag = FoodTag.parse(tag),
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
        sugarG = sugarG,
        fiberG = fiberG,
        saltG = saltG,
        mealCategory = mealCategory.name,
        tag = tag.name,
        date = date.toString(),
        timestampMs = timestampMs,
    )
}
