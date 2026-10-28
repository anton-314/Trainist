package dev.antonlammers.trainist.data.repository

import dev.antonlammers.trainist.data.local.entity.SavedMealEntity
import dev.antonlammers.trainist.data.local.entity.SavedMealItemEntity
import dev.antonlammers.trainist.data.local.relation.SavedMealWithItems
import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.FoodTag
import dev.antonlammers.trainist.domain.model.SavedMeal
import dev.antonlammers.trainist.domain.model.SavedMealItem

/**
 * Pure entity↔domain mapping for saved meals. Ingredients are sorted by `position` on read (Room's
 * `@Relation` doesn't guarantee order) and renumbered from list order on write.
 */
object SavedMealMappers {

    fun SavedMealWithItems.toDomain(): SavedMeal = SavedMeal(
        id = meal.id,
        stableId = meal.stableId,
        name = meal.name,
        items = items.sortedBy { it.position }.map { it.toDomain() },
        lastUsedMs = meal.lastUsedMs,
    )

    fun SavedMeal.toEntity(id: Long): SavedMealEntity =
        SavedMealEntity(id = id, stableId = stableId, name = name, lastUsedMs = lastUsedMs)

    fun SavedMeal.itemEntities(mealId: Long): List<SavedMealItemEntity> =
        items.mapIndexed { index, item ->
            val food = item.food
            SavedMealItemEntity(
                mealId = mealId,
                position = index,
                foodName = food.name,
                brand = food.brand,
                amountGrams = item.amountGrams,
                kcalPer100g = food.kcalPer100g,
                proteinPer100g = food.proteinPer100g,
                carbsPer100g = food.carbsPer100g,
                fatPer100g = food.fatPer100g,
                sugarPer100g = food.sugarPer100g,
                fiberPer100g = food.fiberPer100g,
                saltPer100g = food.saltPer100g,
                tag = food.tag.name,
            )
        }

    private fun SavedMealItemEntity.toDomain() = SavedMealItem(
        food = Food(
            id = "",
            name = foodName,
            brand = brand,
            kcalPer100g = kcalPer100g,
            proteinPer100g = proteinPer100g,
            carbsPer100g = carbsPer100g,
            fatPer100g = fatPer100g,
            sugarPer100g = sugarPer100g,
            fiberPer100g = fiberPer100g,
            saltPer100g = saltPer100g,
            tag = FoodTag.parse(tag),
        ),
        amountGrams = amountGrams,
    )
}
