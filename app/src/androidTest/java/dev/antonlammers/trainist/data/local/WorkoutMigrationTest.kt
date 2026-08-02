package dev.antonlammers.trainist.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the additive migrations from v7 onwards on a real SQLite engine: each one must apply its
 * schema change and leave existing data intact. Instrumented (needs Android's SQLite) — run via
 * `connectedDebugAndroidTest` against a device/emulator.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutMigrationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "workout-migration-test.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate7to8_addsWorkoutTables_andPreservesExistingData() {
        val db = openV7WithSeedData()

        AppDatabase.MIGRATION_7_8.migrate(db)

        // All six workout tables now exist.
        val tables = db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }
        assertTrue(
            tables.containsAll(
                listOf(
                    "exercises", "workout_templates", "template_exercises",
                    "workout_sessions", "session_exercises", "set_entries",
                ),
            ),
        )

        // The pre-existing food entry survived untouched.
        db.query("SELECT COUNT(*) FROM food_entries").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }

        // New tables are usable and the FK cascade is wired: deleting a session removes its graph.
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL(
            "INSERT INTO workout_sessions (stableId, date, isActive, startedAtMs, endedAtMs, note) " +
                "VALUES ('s1', '2026-07-10', 1, 1000, NULL, NULL)",
        )
        val sessionId = db.query("SELECT id FROM workout_sessions LIMIT 1").use { c ->
            c.moveToFirst(); c.getLong(0)
        }
        db.execSQL(
            "INSERT INTO session_exercises (sessionId, exerciseStableId, position, supersetGroupId) " +
                "VALUES ($sessionId, 'squat', 0, NULL)",
        )
        val sessionExerciseId = db.query("SELECT id FROM session_exercises LIMIT 1").use { c ->
            c.moveToFirst(); c.getLong(0)
        }
        db.execSQL(
            "INSERT INTO set_entries (sessionExerciseId, position, weightKg, reps, type, completed) " +
                "VALUES ($sessionExerciseId, 0, 100.0, 5, 'NORMAL', 1)",
        )

        db.execSQL("DELETE FROM workout_sessions WHERE id = $sessionId")
        db.query("SELECT COUNT(*) FROM session_exercises").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM set_entries").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }

        db.close()
    }

    @Test
    fun migrate8to9_addsRestTimerColumns_andPreservesExistingData() {
        val db = openV7WithSeedData()
        AppDatabase.MIGRATION_7_8.migrate(db)
        db.execSQL(
            "INSERT INTO workout_sessions (stableId, date, isActive, startedAtMs, endedAtMs, note) " +
                "VALUES ('s1', '2026-07-10', 1, 1000, NULL, NULL)",
        )

        AppDatabase.MIGRATION_8_9.migrate(db)

        val columns = db.query("PRAGMA table_info(workout_sessions)").use { c ->
            val nameIdx = c.getColumnIndex("name")
            buildSet { while (c.moveToNext()) add(c.getString(nameIdx)) }
        }
        assertTrue(
            columns.containsAll(
                listOf("restExerciseStableId", "restTotalSeconds", "restEndAtMs", "restPausedRemainingMs"),
            ),
        )

        // The pre-existing session survived, with the new columns defaulting to null.
        db.query(
            "SELECT restExerciseStableId, restTotalSeconds, restEndAtMs, restPausedRemainingMs " +
                "FROM workout_sessions WHERE stableId = 's1'",
        ).use { c ->
            c.moveToFirst()
            assertTrue(c.isNull(0) && c.isNull(1) && c.isNull(2) && c.isNull(3))
        }

        // Usable: a running session can now persist a rest-timer anchor.
        db.execSQL(
            "UPDATE workout_sessions SET restExerciseStableId = 'squat', restTotalSeconds = 180, " +
                "restEndAtMs = 5000, restPausedRemainingMs = NULL WHERE stableId = 's1'",
        )
        db.query("SELECT restExerciseStableId, restTotalSeconds FROM workout_sessions WHERE stableId = 's1'").use { c ->
            c.moveToFirst()
            assertEquals("squat", c.getString(0))
            assertEquals(180, c.getInt(1))
        }

        db.close()
    }

    @Test
    fun migrate9to10_addsTemplatePositionAndSessionTemplateLink_andPreservesExistingData() {
        val db = openV7WithSeedData()
        AppDatabase.MIGRATION_7_8.migrate(db)
        AppDatabase.MIGRATION_8_9.migrate(db)
        db.execSQL("INSERT INTO workout_templates (id, stableId, name) VALUES (5, 'tpl', 'Push Day')")
        db.execSQL(
            "INSERT INTO workout_sessions (stableId, date, isActive, startedAtMs, endedAtMs, note) " +
                "VALUES ('s1', '2026-07-10', 1, 1000, NULL, NULL)",
        )

        AppDatabase.MIGRATION_9_10.migrate(db)

        val templateColumns = db.query("PRAGMA table_info(workout_templates)").use { c ->
            val nameIdx = c.getColumnIndex("name")
            buildSet { while (c.moveToNext()) add(c.getString(nameIdx)) }
        }
        assertTrue(templateColumns.contains("position"))
        val sessionColumns = db.query("PRAGMA table_info(workout_sessions)").use { c ->
            val nameIdx = c.getColumnIndex("name")
            buildSet { while (c.moveToNext()) add(c.getString(nameIdx)) }
        }
        assertTrue(sessionColumns.contains("templateStableId"))

        // The pre-existing template's position is backfilled from its row id, not left at 0.
        db.query("SELECT position FROM workout_templates WHERE stableId = 'tpl'").use { c ->
            c.moveToFirst()
            assertEquals(5, c.getInt(0))
        }

        // The pre-existing session's new templateStableId column defaults to null.
        db.query("SELECT templateStableId FROM workout_sessions WHERE stableId = 's1'").use { c ->
            c.moveToFirst()
            assertTrue(c.isNull(0))
        }

        // Usable: a new session can now record the template it was started from.
        db.execSQL("UPDATE workout_sessions SET templateStableId = 'tpl' WHERE stableId = 's1'")
        db.query("SELECT templateStableId FROM workout_sessions WHERE stableId = 's1'").use { c ->
            c.moveToFirst()
            assertEquals("tpl", c.getString(0))
        }

        db.close()
    }

    @Test
    fun migrate10to11_addsTemplateSetTypesColumn_andPreservesExistingData() {
        val db = openV7WithSeedData()
        AppDatabase.MIGRATION_7_8.migrate(db)
        AppDatabase.MIGRATION_8_9.migrate(db)
        AppDatabase.MIGRATION_9_10.migrate(db)
        db.execSQL("INSERT INTO workout_templates (id, stableId, name, position) VALUES (5, 'tpl', 'Push Day', 0)")
        db.execSQL(
            "INSERT INTO template_exercises (templateId, exerciseStableId, position, targetSets) " +
                "VALUES (5, 'bench', 0, 3)",
        )

        AppDatabase.MIGRATION_10_11.migrate(db)

        val columns = db.query("PRAGMA table_info(template_exercises)").use { c ->
            val nameIdx = c.getColumnIndex("name")
            buildSet { while (c.moveToNext()) add(c.getString(nameIdx)) }
        }
        assertTrue(columns.contains("setTypes"))

        // The pre-existing slot survived, with the new column defaulting to null and targetSets intact.
        db.query("SELECT targetSets, setTypes FROM template_exercises WHERE exerciseStableId = 'bench'").use { c ->
            c.moveToFirst()
            assertEquals(3, c.getInt(0))
            assertTrue(c.isNull(1))
        }

        // Usable: a saved slot can now record its planned per-set types.
        db.execSQL("UPDATE template_exercises SET setTypes = 'WARMUP\nNORMAL\nNORMAL' WHERE exerciseStableId = 'bench'")
        db.query("SELECT setTypes FROM template_exercises WHERE exerciseStableId = 'bench'").use { c ->
            c.moveToFirst()
            assertEquals("WARMUP\nNORMAL\nNORMAL", c.getString(0))
        }

        db.close()
    }

    @Test
    fun migrate11to12_addsBmrProfileColumns_andPreservesExistingGoal() {
        val db = openV7WithSeedData()
        AppDatabase.MIGRATION_7_8.migrate(db)
        AppDatabase.MIGRATION_8_9.migrate(db)
        AppDatabase.MIGRATION_9_10.migrate(db)
        AppDatabase.MIGRATION_10_11.migrate(db)

        AppDatabase.MIGRATION_11_12.migrate(db)

        val columns = db.query("PRAGMA table_info(daily_goal)").use { c ->
            val nameIdx = c.getColumnIndex("name")
            buildSet { while (c.moveToNext()) add(c.getString(nameIdx)) }
        }
        assertTrue(columns.containsAll(listOf("bmrSex", "bmrAgeYears", "bmrHeightCm", "bmrActivityLevel")))

        // The goal singleton kept its macros and target weight; the profile columns default to null,
        // which is what "the calculator has never been run" means to BmrProfile.fromParts.
        db.query(
            "SELECT kcal, proteinG, targetWeightKg, bmrSex, bmrAgeYears, bmrHeightCm, bmrActivityLevel " +
                "FROM daily_goal WHERE id = 1",
        ).use { c ->
            c.moveToFirst()
            assertEquals(2200.0, c.getDouble(0), 0.001)
            assertEquals(165.0, c.getDouble(1), 0.001)
            assertEquals(78.0, c.getDouble(2), 0.001)
            assertTrue(c.isNull(3) && c.isNull(4) && c.isNull(5) && c.isNull(6))
        }

        // Usable: the singleton row can now carry a complete BMR profile.
        db.execSQL(
            "UPDATE daily_goal SET bmrSex = 'MALE', bmrAgeYears = 31, bmrHeightCm = 183.0, " +
                "bmrActivityLevel = 'MODERATE' WHERE id = 1",
        )
        db.query("SELECT bmrSex, bmrAgeYears, bmrHeightCm, bmrActivityLevel FROM daily_goal WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals("MALE", c.getString(0))
            assertEquals(31, c.getInt(1))
            assertEquals(183.0, c.getDouble(2), 0.001)
            assertEquals("MODERATE", c.getString(3))
        }

        db.close()
    }

    @Test
    fun migrate12to13_addsBodyMeasurementsTable_andPreservesExistingData() {
        val db = openV7WithSeedData()
        AppDatabase.MIGRATION_7_8.migrate(db)
        AppDatabase.MIGRATION_8_9.migrate(db)
        AppDatabase.MIGRATION_9_10.migrate(db)
        AppDatabase.MIGRATION_10_11.migrate(db)
        AppDatabase.MIGRATION_11_12.migrate(db)

        AppDatabase.MIGRATION_12_13.migrate(db)

        val tables = db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }
        assertTrue(tables.contains("body_measurements"))

        // The pre-existing nutrition data survived untouched.
        db.query("SELECT COUNT(*) FROM food_entries").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM daily_goal").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }

        // Usable, and at most one row per (date, type): BodyMeasurementDao.upsert inserts with
        // REPLACE, which silently degrades into duplicate rows if the unique index is missing.
        db.execSQL("INSERT OR REPLACE INTO body_measurements (date, type, valueCm) VALUES ('2026-07-10', 'WAIST', 82.0)")
        db.execSQL("INSERT OR REPLACE INTO body_measurements (date, type, valueCm) VALUES ('2026-07-10', 'WAIST', 81.5)")
        db.execSQL("INSERT OR REPLACE INTO body_measurements (date, type, valueCm) VALUES ('2026-07-10', 'CHEST', 104.0)")
        db.execSQL("INSERT OR REPLACE INTO body_measurements (date, type, valueCm) VALUES ('2026-07-17', 'WAIST', 81.0)")

        db.query("SELECT COUNT(*) FROM body_measurements").use { c ->
            c.moveToFirst()
            assertEquals(3, c.getInt(0))
        }
        db.query("SELECT valueCm FROM body_measurements WHERE date = '2026-07-10' AND type = 'WAIST'").use { c ->
            c.moveToFirst()
            assertEquals(81.5, c.getDouble(0), 0.001)
        }

        db.close()
    }

    @Test
    fun migrate13to14_addsTemplateSupersetColumn_andPreservesExistingSlots() {
        val db = openV7WithSeedData()
        AppDatabase.MIGRATION_7_8.migrate(db)
        AppDatabase.MIGRATION_8_9.migrate(db)
        AppDatabase.MIGRATION_9_10.migrate(db)
        AppDatabase.MIGRATION_10_11.migrate(db)
        AppDatabase.MIGRATION_11_12.migrate(db)
        AppDatabase.MIGRATION_12_13.migrate(db)
        db.execSQL("INSERT INTO workout_templates (id, stableId, name, position) VALUES (5, 'tpl', 'Push Day', 0)")
        db.execSQL(
            "INSERT INTO template_exercises (templateId, exerciseStableId, position, targetSets, setTypes) " +
                "VALUES (5, 'bench', 0, 3, 'WARMUP\nNORMAL\nNORMAL')",
        )
        db.execSQL(
            "INSERT INTO template_exercises (templateId, exerciseStableId, position, targetSets, setTypes) " +
                "VALUES (5, 'row', 1, 3, 'NORMAL\nNORMAL\nNORMAL')",
        )

        AppDatabase.MIGRATION_13_14.migrate(db)

        val columns = db.query("PRAGMA table_info(template_exercises)").use { c ->
            val nameIdx = c.getColumnIndex("name")
            buildSet { while (c.moveToNext()) add(c.getString(nameIdx)) }
        }
        assertTrue(columns.contains("supersetGroupId"))

        // Both pre-existing slots survived with their plan intact, and the new column defaults to
        // null — which is exactly the "stands on its own" every pre-superset template had.
        db.query(
            "SELECT exerciseStableId, setTypes, supersetGroupId FROM template_exercises " +
                "WHERE templateId = 5 ORDER BY position",
        ).use { c ->
            c.moveToFirst()
            assertEquals("bench", c.getString(0))
            assertEquals("WARMUP\nNORMAL\nNORMAL", c.getString(1))
            assertTrue(c.isNull(2))
            c.moveToNext()
            assertEquals("row", c.getString(0))
            assertTrue(c.isNull(2))
        }

        // Usable: the two slots can now be planned as one superset.
        db.execSQL("UPDATE template_exercises SET supersetGroupId = 1 WHERE templateId = 5")
        db.query("SELECT COUNT(*) FROM template_exercises WHERE supersetGroupId = 1").use { c ->
            c.moveToFirst()
            assertEquals(2, c.getInt(0))
        }

        db.close()
    }

    /**
     * Creates a fresh database at schema version 7 with one representative row per pre-existing
     * table the later migrations touch or must leave alone: a food entry and the goal singleton.
     */
    private fun openV7WithSeedData(): SupportSQLiteDatabase {
        context.deleteDatabase(dbName)
        val callback = object : SupportSQLiteOpenHelper.Callback(7) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS food_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        foodName TEXT NOT NULL, brand TEXT, amountGrams REAL NOT NULL, kcal REAL NOT NULL,
                        proteinG REAL NOT NULL, carbsG REAL NOT NULL, fatG REAL NOT NULL,
                        sugarG REAL NOT NULL DEFAULT 0, fiberG REAL NOT NULL DEFAULT 0, saltG REAL NOT NULL DEFAULT 0,
                        mealCategory TEXT NOT NULL DEFAULT 'SNACK', tag TEXT NOT NULL DEFAULT 'NONE',
                        date TEXT NOT NULL, timestampMs INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "INSERT INTO food_entries (foodName, amountGrams, kcal, proteinG, carbsG, fatG, date, timestampMs) " +
                        "VALUES ('Apfel', 100, 52, 0.3, 14.0, 0.2, '2026-07-10', 1)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_goal (
                        id INTEGER NOT NULL,
                        kcal REAL NOT NULL, proteinG REAL NOT NULL, carbsG REAL NOT NULL, fatG REAL NOT NULL,
                        targetWeightKg REAL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "INSERT INTO daily_goal (id, kcal, proteinG, carbsG, fatG, targetWeightKg) " +
                        "VALUES (1, 2200, 165, 220, 70, 78.0)",
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(callback)
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }
}

