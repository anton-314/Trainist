package dev.antonlammers.macromind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.antonlammers.macromind.data.local.entity.DailyGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyGoalDao {
    @Query("SELECT * FROM daily_goal WHERE id = 1")
    fun goal(): Flow<DailyGoalEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(goal: DailyGoalEntity)
}
