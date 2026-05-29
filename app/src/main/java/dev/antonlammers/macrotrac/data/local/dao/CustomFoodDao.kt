package dev.antonlammers.macrotrac.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.antonlammers.macrotrac.data.local.entity.CustomFoodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomFoodDao {
    @Query("SELECT * FROM custom_foods ORDER BY name ASC")
    fun allFoods(): Flow<List<CustomFoodEntity>>

    @Insert
    suspend fun insert(food: CustomFoodEntity): Long

    @Query("DELETE FROM custom_foods WHERE id = :id")
    suspend fun delete(id: Long)
}
