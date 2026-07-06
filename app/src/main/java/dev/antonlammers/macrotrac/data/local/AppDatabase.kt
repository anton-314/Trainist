package dev.antonlammers.macrotrac.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.antonlammers.macrotrac.data.local.dao.CustomFoodDao
import dev.antonlammers.macrotrac.data.local.dao.DailyGoalDao
import dev.antonlammers.macrotrac.data.local.dao.FoodEntryDao
import dev.antonlammers.macrotrac.data.local.dao.WeightEntryDao
import dev.antonlammers.macrotrac.data.local.entity.CustomFoodEntity
import dev.antonlammers.macrotrac.data.local.entity.DailyGoalEntity
import dev.antonlammers.macrotrac.data.local.entity.FoodEntryEntity
import dev.antonlammers.macrotrac.data.local.entity.WeightEntryEntity

@Database(
    entities = [FoodEntryEntity::class, DailyGoalEntity::class, WeightEntryEntity::class, CustomFoodEntity::class],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun dailyGoalDao(): DailyGoalDao
    abstract fun weightEntryDao(): WeightEntryDao
    abstract fun customFoodDao(): CustomFoodDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_entries ADD COLUMN sugarG REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE food_entries ADD COLUMN fiberG REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE food_entries ADD COLUMN mealCategory TEXT NOT NULL DEFAULT 'SNACK'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS weight_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        weightKg REAL NOT NULL,
                        date TEXT NOT NULL,
                        timestampMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_weight_entries_date ON weight_entries (date)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS custom_foods (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        brand TEXT,
                        kcalPer100g REAL NOT NULL,
                        proteinPer100g REAL NOT NULL,
                        carbsPer100g REAL NOT NULL,
                        fatPer100g REAL NOT NULL,
                        sugarPer100g REAL NOT NULL DEFAULT 0,
                        fiberPer100g REAL NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_entries ADD COLUMN saltG REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE custom_foods ADD COLUMN saltPer100g REAL NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Nullable: no default — absence means "no target weight set".
                db.execSQL("ALTER TABLE daily_goal ADD COLUMN targetWeightKg REAL")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Clean-eating tag, stored as the FoodTag enum name; 'NONE' = untagged (default).
                db.execSQL("ALTER TABLE food_entries ADD COLUMN tag TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE custom_foods ADD COLUMN tag TEXT NOT NULL DEFAULT 'NONE'")
            }
        }
    }
}
