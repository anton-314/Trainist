package dev.antonlammers.trainist.domain.repository

import dev.antonlammers.trainist.domain.model.SavedMeal
import kotlinx.coroutines.flow.Flow

interface SavedMealRepository {
    /** All saved meals, by name. */
    fun meals(): Flow<List<SavedMeal>>

    /**
     * Inserts or replaces [meal] together with its ingredients, keyed on [SavedMeal.stableId] — an
     * existing meal with that key is overwritten in place, never duplicated. Returns the row id.
     */
    suspend fun save(meal: SavedMeal): Long

    suspend fun delete(id: Long)
}
