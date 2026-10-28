package dev.antonlammers.trainist.domain.repository

import dev.antonlammers.trainist.domain.model.SavedMeal
import kotlinx.coroutines.flow.Flow

interface SavedMealRepository {
    /** All saved meals, most recently logged first; never-logged ones after them, by name. */
    fun meals(): Flow<List<SavedMeal>>

    /**
     * Inserts or replaces [meal] together with its ingredients, keyed on [SavedMeal.stableId] — an
     * existing meal with that key is overwritten in place, never duplicated. Returns the row id.
     */
    suspend fun save(meal: SavedMeal): Long

    /** Records that meal [id] was just logged, moving it to the top of [meals]. */
    suspend fun markUsed(id: Long, atMs: Long)

    suspend fun delete(id: Long)
}
