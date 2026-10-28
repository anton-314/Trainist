package dev.antonlammers.trainist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.antonlammers.trainist.data.local.entity.CustomFoodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomFoodDao {
    @Query("SELECT * FROM custom_foods ORDER BY name COLLATE NOCASE ASC")
    fun allFoods(): Flow<List<CustomFoodEntity>>

    @Insert
    suspend fun insert(food: CustomFoodEntity): Long

    @Update
    suspend fun update(food: CustomFoodEntity)

    @Query("DELETE FROM custom_foods WHERE id = :id")
    suspend fun delete(id: Long)
}
