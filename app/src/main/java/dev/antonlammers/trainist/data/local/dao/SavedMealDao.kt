package dev.antonlammers.trainist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.antonlammers.trainist.data.local.entity.SavedMealEntity
import dev.antonlammers.trainist.data.local.entity.SavedMealItemEntity
import dev.antonlammers.trainist.data.local.relation.SavedMealWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedMealDao {
    @Transaction
    @Query("SELECT * FROM saved_meals ORDER BY name COLLATE NOCASE ASC")
    fun allMeals(): Flow<List<SavedMealWithItems>>

    @Query("SELECT id FROM saved_meals WHERE stableId = :stableId LIMIT 1")
    suspend fun idForStableId(stableId: String): Long?

    @Insert
    suspend fun insertMeal(meal: SavedMealEntity): Long

    @Update
    suspend fun updateMeal(meal: SavedMealEntity)

    @Insert
    suspend fun insertItems(items: List<SavedMealItemEntity>)

    @Query("DELETE FROM saved_meal_items WHERE mealId = :mealId")
    suspend fun deleteItems(mealId: Long)

    @Query("DELETE FROM saved_meals WHERE id = :id")
    suspend fun deleteMeal(id: Long)
}
