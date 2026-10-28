package dev.antonlammers.trainist.data.local.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface UserDataDao {
    @Query(
        "SELECT EXISTS(SELECT 1 FROM food_entries) OR EXISTS(SELECT 1 FROM weight_entries) " +
            "OR EXISTS(SELECT 1 FROM body_measurements) OR EXISTS(SELECT 1 FROM custom_foods) " +
            "OR EXISTS(SELECT 1 FROM saved_meals) OR EXISTS(SELECT 1 FROM exercises WHERE isCustom = 1) " +
            "OR EXISTS(SELECT 1 FROM workout_templates) OR EXISTS(SELECT 1 FROM workout_sessions)",
    )
    suspend fun hasUserData(): Boolean
}
