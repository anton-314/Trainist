package dev.antonlammers.macrotrac.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.antonlammers.macrotrac.data.local.dao.DailyGoalDao
import dev.antonlammers.macrotrac.data.local.dao.FoodEntryDao
import dev.antonlammers.macrotrac.data.local.entity.DailyGoalEntity
import dev.antonlammers.macrotrac.data.local.entity.FoodEntryEntity

@Database(
    entities = [FoodEntryEntity::class, DailyGoalEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun dailyGoalDao(): DailyGoalDao
}
