package dev.antonlammers.macrotrac.data.local

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
 * Verifies the additive workout-module migrations (v7 → v10) on a real SQLite engine: each one must
 * apply its schema change and leave existing data intact. Instrumented (needs Android's SQLite) — run
 * via `connectedDebugAndroidTest` against a device/emulator.
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
        val db = openV7WithOneFoodEntry()

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
        val db = openV7WithOneFoodEntry()
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
        val db = openV7WithOneFoodEntry()
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

    /** Creates a fresh database at schema version 7 with one representative food entry. */
    private fun openV7WithOneFoodEntry(): SupportSQLiteDatabase {
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
