package dev.antonlammers.trainist.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One ingredient of a [SavedMealEntity], cascade-deleted with it. The food is stored as a snapshot of
 * its per-100 g values (same columns as [CustomFoodEntity]), not as a reference to another row — a
 * meal stays exactly as saved when the food it was built from changes or disappears.
 */
@Entity(
    tableName = "saved_meal_items",
    foreignKeys = [
        ForeignKey(
            entity = SavedMealEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mealId")],
)
data class SavedMealItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long,
    val position: Int,
    val foodName: String,
    val brand: String?,
    val amountGrams: Double,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val sugarPer100g: Double,
    val fiberPer100g: Double,
    val saltPer100g: Double,
    val tag: String,
)
