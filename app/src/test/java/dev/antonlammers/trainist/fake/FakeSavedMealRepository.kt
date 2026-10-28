package dev.antonlammers.trainist.fake

import dev.antonlammers.trainist.domain.model.SavedMeal
import dev.antonlammers.trainist.domain.repository.SavedMealRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory saved meals; [save] upserts on the stable key like the real repository. */
class FakeSavedMealRepository : SavedMealRepository {

    private val _meals = MutableStateFlow<List<SavedMeal>>(emptyList())
    private var nextId = 1L

    override fun meals(): Flow<List<SavedMeal>> = _meals.map { it.sortedBy { m -> m.name.lowercase() } }

    override suspend fun save(meal: SavedMeal): Long {
        val existing = _meals.value.firstOrNull { it.id == meal.id && meal.id != 0L }
            ?: _meals.value.firstOrNull { it.stableId == meal.stableId }
        val id = existing?.id ?: nextId++
        _meals.update { list -> list.filterNot { it.id == id } + meal.copy(id = id) }
        return id
    }

    override suspend fun delete(id: Long) {
        _meals.update { list -> list.filterNot { it.id == id } }
    }
}
