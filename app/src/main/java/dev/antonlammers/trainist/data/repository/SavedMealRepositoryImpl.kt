package dev.antonlammers.trainist.data.repository

import dev.antonlammers.trainist.data.local.dao.SavedMealDao
import dev.antonlammers.trainist.data.repository.SavedMealMappers.itemEntities
import dev.antonlammers.trainist.data.repository.SavedMealMappers.toDomain
import dev.antonlammers.trainist.data.repository.SavedMealMappers.toEntity
import dev.antonlammers.trainist.domain.model.SavedMeal
import dev.antonlammers.trainist.domain.repository.SavedMealRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SavedMealRepositoryImpl @Inject constructor(
    private val dao: SavedMealDao,
    private val runner: TransactionRunner,
) : SavedMealRepository {

    override fun meals(): Flow<List<SavedMeal>> =
        dao.allMeals().map { list -> list.map { it.toDomain() } }

    override suspend fun save(meal: SavedMeal): Long = runner.transaction {
        // Resolve the row by stable key, so an edit and a backup re-import (which arrives with
        // id = 0) both overwrite the existing meal instead of adding a second one.
        val existingId = meal.id.takeIf { it != 0L } ?: dao.idForStableId(meal.stableId)
        val mealId = if (existingId != null) {
            dao.updateMeal(meal.toEntity(existingId))
            dao.deleteItems(existingId)
            existingId
        } else {
            dao.insertMeal(meal.toEntity(0))
        }
        dao.insertItems(meal.itemEntities(mealId))
        mealId
    }

    override suspend fun markUsed(id: Long, atMs: Long) = dao.markUsed(id, atMs)

    override suspend fun delete(id: Long) = dao.deleteMeal(id)
}
