package dev.antonlammers.macrotrac.data.repository

import dev.antonlammers.macrotrac.data.local.dao.CustomFoodDao
import dev.antonlammers.macrotrac.data.local.entity.CustomFoodEntity
import dev.antonlammers.macrotrac.domain.model.Food
import dev.antonlammers.macrotrac.domain.repository.CustomFoodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CustomFoodRepositoryImpl @Inject constructor(
    private val dao: CustomFoodDao,
) : CustomFoodRepository {

    override fun allFoods(): Flow<List<Food>> =
        dao.allFoods().map { list -> list.map { it.toDomain() } }

    override suspend fun save(food: Food): Food {
        val id = dao.insert(food.toEntity())
        return food.copy(id = id.toString())
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    private fun CustomFoodEntity.toDomain() = Food(
        id = id.toString(),
        name = name,
        brand = brand,
        kcalPer100g = kcalPer100g,
        proteinPer100g = proteinPer100g,
        carbsPer100g = carbsPer100g,
        fatPer100g = fatPer100g,
        sugarPer100g = sugarPer100g,
        fiberPer100g = fiberPer100g,
        saltPer100g = saltPer100g,
    )

    private fun Food.toEntity() = CustomFoodEntity(
        name = name,
        brand = brand,
        kcalPer100g = kcalPer100g,
        proteinPer100g = proteinPer100g,
        carbsPer100g = carbsPer100g,
        fatPer100g = fatPer100g,
        sugarPer100g = sugarPer100g,
        fiberPer100g = fiberPer100g,
        saltPer100g = saltPer100g,
    )
}
