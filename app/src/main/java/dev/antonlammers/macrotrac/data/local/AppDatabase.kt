package dev.antonlammers.macrotrac.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.antonlammers.macrotrac.data.local.dao.CustomFoodDao
import dev.antonlammers.macrotrac.data.local.dao.DailyGoalDao
import dev.antonlammers.macrotrac.data.local.dao.ExerciseDao
import dev.antonlammers.macrotrac.data.local.dao.FoodEntryDao
import dev.antonlammers.macrotrac.data.local.dao.WeightEntryDao
import dev.antonlammers.macrotrac.data.local.dao.WorkoutSessionDao
import dev.antonlammers.macrotrac.data.local.dao.WorkoutTemplateDao
import dev.antonlammers.macrotrac.data.local.entity.CustomFoodEntity
import dev.antonlammers.macrotrac.data.local.entity.DailyGoalEntity
import dev.antonlammers.macrotrac.data.local.entity.ExerciseEntity
import dev.antonlammers.macrotrac.data.local.entity.FoodEntryEntity
import dev.antonlammers.macrotrac.data.local.entity.SessionExerciseEntity
import dev.antonlammers.macrotrac.data.local.entity.SetEntryEntity
import dev.antonlammers.macrotrac.data.local.entity.TemplateExerciseEntity
import dev.antonlammers.macrotrac.data.local.entity.WeightEntryEntity
import dev.antonlammers.macrotrac.data.local.entity.WorkoutSessionEntity
import dev.antonlammers.macrotrac.data.local.entity.WorkoutTemplateEntity

@Database(
    entities = [
        FoodEntryEntity::class, DailyGoalEntity::class, WeightEntryEntity::class, CustomFoodEntity::class,
        ExerciseEntity::class, WorkoutTemplateEntity::class, TemplateExerciseEntity::class,
        WorkoutSessionEntity::class, SessionExerciseEntity::class, SetEntryEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun dailyGoalDao(): DailyGoalDao
    abstract fun weightEntryDao(): WeightEntryDao
    abstract fun customFoodDao(): CustomFoodDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutTemplateDao(): WorkoutTemplateDao
    abstract fun workoutSessionDao(): WorkoutSessionDao

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

        /**
         * Adds the workout module tables. Purely additive — existing tables are untouched. The
         * CREATE statements are copied verbatim from Room's generated v8 schema (schemas/…/8.json)
         * so the migrated schema matches what Room expects for the entities exactly.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `stableId` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `isCustom` INTEGER NOT NULL, `primaryMuscles` TEXT NOT NULL, `secondaryMuscles` TEXT NOT NULL, `equipment` TEXT, `mechanic` TEXT, `category` TEXT, `instructions` TEXT NOT NULL, `restSeconds` INTEGER)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exercises_stableId` ON `exercises` (`stableId`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `workout_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `stableId` TEXT NOT NULL, `name` TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_templates_stableId` ON `workout_templates` (`stableId`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `template_exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `templateId` INTEGER NOT NULL, `exerciseStableId` TEXT NOT NULL, `position` INTEGER NOT NULL, `targetSets` INTEGER NOT NULL, FOREIGN KEY(`templateId`) REFERENCES `workout_templates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_template_exercises_templateId` ON `template_exercises` (`templateId`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `workout_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `stableId` TEXT NOT NULL, `date` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `startedAtMs` INTEGER NOT NULL, `endedAtMs` INTEGER, `note` TEXT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_sessions_stableId` ON `workout_sessions` (`stableId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sessions_isActive` ON `workout_sessions` (`isActive`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `session_exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `exerciseStableId` TEXT NOT NULL, `position` INTEGER NOT NULL, `supersetGroupId` INTEGER, FOREIGN KEY(`sessionId`) REFERENCES `workout_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_exercises_sessionId` ON `session_exercises` (`sessionId`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `set_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionExerciseId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `reps` INTEGER NOT NULL, `type` TEXT NOT NULL, `completed` INTEGER NOT NULL, FOREIGN KEY(`sessionExerciseId`) REFERENCES `session_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_set_entries_sessionExerciseId` ON `set_entries` (`sessionExerciseId`)")
            }
        }

        /**
         * Adds a persisted rest-timer anchor to `workout_sessions` so an in-progress rest survives
         * leaving and resuming the session (all four columns nullable — null means "no rest running").
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN restExerciseStableId TEXT")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN restTotalSeconds INTEGER")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN restEndAtMs INTEGER")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN restPausedRemainingMs INTEGER")
            }
        }

        /**
         * Adds manual drag-to-reorder ordering for templates (backfilled from `id` so existing
         * templates keep their creation order) and links a session back to the template it was
         * started from, so the templates list can show each one's "last used" date.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_templates ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE workout_templates SET position = id")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN templateStableId TEXT")
            }
        }
    }
}
