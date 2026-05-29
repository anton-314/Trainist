package dev.antonlammers.macrotrac.domain.repository

import dev.antonlammers.macrotrac.domain.model.Food
import kotlinx.coroutines.flow.Flow

interface CustomFoodRepository {
    fun allFoods(): Flow<List<Food>>
    suspend fun save(food: Food): Food
    suspend fun delete(id: Long)
}
