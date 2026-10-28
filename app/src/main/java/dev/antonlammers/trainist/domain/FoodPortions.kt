package dev.antonlammers.trainist.domain

import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.domain.model.FoodTag
import dev.antonlammers.trainist.domain.model.MealCategory
import dev.antonlammers.trainist.domain.model.SavedMealItem
import java.time.LocalDate

/**
 * The conversion between a food's per-100 g values ([Food]) and a logged portion ([FoodEntry]) —
 * shared by single-food logging, saved meals and the history, so all three scale identically.
 */
object FoodPortions {

    /** [food] scaled to [amountGrams], as an entry in [mealCategory] on [date]. */
    fun entry(
        food: Food,
        amountGrams: Double,
        mealCategory: MealCategory,
        tag: FoodTag,
        date: LocalDate,
        timestampMs: Long,
    ): FoodEntry {
        val factor = amountGrams / 100.0
        return FoodEntry(
            foodName = food.name,
            brand = food.brand,
            amountGrams = amountGrams,
            kcal = food.kcalPer100g * factor,
            proteinG = food.proteinPer100g * factor,
            carbsG = food.carbsPer100g * factor,
            fatG = food.fatPer100g * factor,
            sugarG = food.sugarPer100g * factor,
            fiberG = food.fiberPer100g * factor,
            saltG = food.saltPer100g * factor,
            mealCategory = mealCategory,
            tag = tag,
            date = date,
            timestampMs = timestampMs,
        )
    }

    /**
     * The per-100 g values behind a logged [entry] — how a history row becomes something to log
     * again or to put into a meal. An entry without a positive amount has no ratio to scale by, so
     * its totals are taken as the per-100 g values (the long-standing history behaviour).
     */
    fun per100g(entry: FoodEntry): Food {
        val factor = if (entry.amountGrams > 0) 100.0 / entry.amountGrams else 1.0
        return Food(
            id = entry.foodName,
            name = entry.foodName,
            brand = entry.brand,
            kcalPer100g = entry.kcal * factor,
            proteinPer100g = entry.proteinG * factor,
            carbsPer100g = entry.carbsG * factor,
            fatPer100g = entry.fatG * factor,
            sugarPer100g = entry.sugarG * factor,
            fiberPer100g = entry.fiberG * factor,
            saltPer100g = entry.saltG * factor,
            tag = entry.tag,
        )
    }

    /**
     * A saved meal's ingredients as entries, one per item, each with its own amount from [amounts]
     * (same order as [items]) and its own tag — so the clean-eating split stays per ingredient.
     *
     * Timestamps count down from [nowMs] by one millisecond per item: the day view lists entries
     * newest first, so this keeps the meal's own ingredient order there, and no two entries tie.
     */
    fun mealEntries(
        items: List<SavedMealItem>,
        amounts: List<Double>,
        mealCategory: MealCategory,
        date: LocalDate,
        nowMs: Long,
    ): List<FoodEntry> {
        require(items.size == amounts.size) { "one amount per ingredient" }
        return items.mapIndexed { index, item ->
            entry(item.food, amounts[index], mealCategory, item.food.tag, date, nowMs - index)
        }
    }

    /** Summed kcal and macros of [items] at their amounts — the preview under a meal. */
    fun totals(items: List<SavedMealItem>): NutritionTotals = items.fold(NutritionTotals()) { acc, item ->
        val factor = item.amountGrams / 100.0
        NutritionTotals(
            kcal = acc.kcal + item.food.kcalPer100g * factor,
            proteinG = acc.proteinG + item.food.proteinPer100g * factor,
            carbsG = acc.carbsG + item.food.carbsPer100g * factor,
            fatG = acc.fatG + item.food.fatPer100g * factor,
        )
    }
}

data class NutritionTotals(
    val kcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
)
