package dev.antonlammers.trainist.data.backup

import dev.antonlammers.trainist.domain.model.BodyMeasurementEntry
import dev.antonlammers.trainist.domain.model.Exercise
import dev.antonlammers.trainist.domain.model.ExerciseType
import dev.antonlammers.trainist.domain.model.Mechanic
import dev.antonlammers.trainist.domain.model.MeasurementType
import dev.antonlammers.trainist.domain.model.SessionExercise
import dev.antonlammers.trainist.domain.model.SetEntry
import dev.antonlammers.trainist.domain.model.SetType
import dev.antonlammers.trainist.domain.model.TemplateExercise
import dev.antonlammers.trainist.domain.model.WorkoutSession
import dev.antonlammers.trainist.domain.model.WorkoutTemplate
import dev.antonlammers.trainist.fake.FakeBodyMeasurementRepository
import dev.antonlammers.trainist.fake.FakeCustomFoodRepository
import dev.antonlammers.trainist.fake.FakeExerciseCatalogRepository
import dev.antonlammers.trainist.fake.FakeFoodEntryRepository
import dev.antonlammers.trainist.fake.FakeGoalRepository
import dev.antonlammers.trainist.fake.FakeWeightRepository
import dev.antonlammers.trainist.fake.FakeWorkoutSessionRepository
import dev.antonlammers.trainist.fake.FakeWorkoutTemplateRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupImporterTest {

    @Test
    fun `detectCsvType returns FOOD_ENTRIES when food_name column is present`() {
        val headers = CsvFormat.parseHeaders(CsvColumns.HEADER)
        assertEquals(CsvType.FOOD_ENTRIES, detectCsvType(headers))
    }

    @Test
    fun `detectCsvType returns WEIGHT_ENTRIES when weight_kg column is present`() {
        val headers = CsvFormat.parseHeaders(WeightCsvFormat.HEADER)
        assertEquals(CsvType.WEIGHT_ENTRIES, detectCsvType(headers))
    }

    @Test
    fun `detectCsvType returns DAILY_GOAL when kcal is present but no food_name or weight_kg`() {
        val headers = CsvFormat.parseHeaders(GoalCsvFormat.HEADER)
        assertEquals(CsvType.DAILY_GOAL, detectCsvType(headers))
    }

    @Test
    fun `detectCsvType returns CUSTOM_FOODS when kcal_per_100g column is present`() {
        val headers = CsvFormat.parseHeaders(CustomFoodCsvFormat.HEADER)
        assertEquals(CsvType.CUSTOM_FOODS, detectCsvType(headers))
    }

    @Test
    fun `detectCsvType returns BODY_MEASUREMENTS when value_cm column is present`() {
        val headers = CsvFormat.parseHeaders(BodyMeasurementCsvFormat.HEADER)
        assertEquals(CsvType.BODY_MEASUREMENTS, detectCsvType(headers))
    }

    @Test
    fun `detectCsvType returns UNKNOWN for unrecognised headers`() {
        val headers = CsvFormat.parseHeaders("foo,bar,baz")
        assertEquals(CsvType.UNKNOWN, detectCsvType(headers))
    }

    @Test
    fun `detectCsvType returns FOOD_ENTRIES for old export without salt_g`() {
        val oldHeader = listOf(
            CsvColumns.DATE, CsvColumns.FOOD_NAME, CsvColumns.BRAND, CsvColumns.AMOUNT_GRAMS,
            CsvColumns.KCAL, CsvColumns.PROTEIN_G, CsvColumns.CARBS_G, CsvColumns.FAT_G,
            CsvColumns.SUGAR_G, CsvColumns.FIBER_G, CsvColumns.MEAL_CATEGORY, CsvColumns.TIMESTAMP_MS,
        ).joinToString(",")
        assertEquals(CsvType.FOOD_ENTRIES, detectCsvType(CsvFormat.parseHeaders(oldHeader)))
    }

    @Test
    fun `legacy ZIP backup without salt and target columns imports with defaults`() = runTest {
        val food = FakeFoodEntryRepository()
        val weight = FakeWeightRepository()
        val goal = FakeGoalRepository()
        val custom = FakeCustomFoodRepository()

        // Headers as an older app version (pre-salt food/custom, pre-target goal) would have written.
        val zip = zipOf(
            "food_entries.csv" to """
                date,food_name,brand,amount_grams,kcal,protein_g,carbs_g,fat_g,sugar_g,fiber_g,meal_category,timestamp_ms
                2026-06-01,Apfel,,150,80,0.5,21,0.3,18,2,SNACK,1717200000000
            """.trimIndent(),
            "weight_entries.csv" to """
                date,weight_kg,timestamp_ms
                2026-06-01,80.5,1717200000000
            """.trimIndent(),
            "daily_goal.csv" to """
                kcal,protein_g,carbs_g,fat_g
                2000,150,250,70
            """.trimIndent(),
            "custom_foods.csv" to """
                name,brand,kcal_per_100g,protein_per_100g,carbs_per_100g,fat_per_100g,sugar_per_100g,fiber_per_100g
                Magerquark,,67,12,4,0.3,4,0
            """.trimIndent(),
        )

        val result = importZipEntries(
            ByteArrayInputStream(zip),
            targetsOf(food = food, weight = weight, goal = goal, custom = custom),
        )

        assertEquals(1, result.foodImported)
        assertEquals(0, result.foodSkipped)
        assertEquals(1, result.weightImported)
        assertTrue(result.goalRestored)
        assertEquals(1, result.customFoodsImported)

        // Columns absent in the old backup default cleanly — no parse failure, no schema mismatch.
        assertEquals(0.0, food.allEntries().first().saltG, 0.001)
        assertNull(goal.goal().first().targetWeightKg)
        assertEquals(0.0, custom.allFoods().first().first().saltPer100g, 0.001)
        assertEquals(80.5, weight.allEntries().first().weightKg, 0.001)
    }

    @Test
    fun `legacy single daily_goal CSV without target column restores goal with null target`() = runTest {
        val goal = FakeGoalRepository()

        val result = importCsvLines(
            listOf("kcal,protein_g,carbs_g,fat_g", "1800,140,200,60"),
            targetsOf(goal = goal),
        )

        assertTrue(result.goalRestored)
        val saved = goal.goal().first()
        assertEquals(1800.0, saved.kcal, 0.001)
        assertNull(saved.targetWeightKg)
    }

    // --- Training data detection ---------------------------------------------------------------

    @Test
    fun `detectCsvType recognises each training section by its unique column`() {
        assertEquals(CsvType.EXERCISES, detectCsvType(CsvFormat.parseHeaders(ExerciseCsvFormat.HEADER)))
        assertEquals(CsvType.WORKOUT_TEMPLATES, detectCsvType(CsvFormat.parseHeaders(WorkoutTemplateCsvFormat.HEADER)))
        assertEquals(CsvType.TEMPLATE_EXERCISES, detectCsvType(CsvFormat.parseHeaders(TemplateExerciseCsvFormat.HEADER)))
        assertEquals(CsvType.WORKOUT_SESSIONS, detectCsvType(CsvFormat.parseHeaders(WorkoutSessionCsvFormat.HEADER)))
        assertEquals(CsvType.SESSION_EXERCISES, detectCsvType(CsvFormat.parseHeaders(SessionExerciseCsvFormat.HEADER)))
        assertEquals(CsvType.SET_ENTRIES, detectCsvType(CsvFormat.parseHeaders(SetEntryCsvFormat.HEADER)))
    }

    // --- Training round-trips -------------------------------------------------------------------

    @Test
    fun `full backup round-trips training data alongside nutrition`() = runTest {
        val zip = fullBackupZip()

        val catalog = FakeExerciseCatalogRepository()
        val templates = FakeWorkoutTemplateRepository()
        val sessions = FakeWorkoutSessionRepository()
        val food = FakeFoodEntryRepository()

        val measurements = FakeBodyMeasurementRepository()

        val result = importZipEntries(
            ByteArrayInputStream(zip),
            targetsOf(food = food, catalog = catalog, templates = templates, sessions = sessions, measurements = measurements),
        )

        // Nutrition still imports unchanged next to the new training sections.
        assertEquals(1, result.foodImported)
        assertEquals(1, result.exercisesImported)
        assertEquals(1, result.templatesImported)
        assertEquals(1, result.sessionsImported)
        assertEquals(1, result.measurementsImported)

        // Only the custom exercise travelled; its metadata survived the round-trip.
        val klimmzug = catalog.exercises().first().first { it.stableId == "custom-klimmzug" }
        assertEquals("Klimmzug", klimmzug.name)
        assertEquals(ExerciseType.BODYWEIGHT, klimmzug.type)
        assertTrue(klimmzug.isCustom)
        assertEquals(listOf("Lats", "Biceps"), klimmzug.primaryMuscles)
        assertEquals(listOf("Hochziehen", "Ablassen"), klimmzug.instructions)
        assertEquals(Mechanic.COMPOUND, klimmzug.mechanic)
        assertEquals(120, klimmzug.restSeconds)

        // Template graph rebuilt in order, referencing both catalog and custom by stable id.
        val template = templates.templates().first().single()
        assertEquals("Push Day", template.name)
        assertEquals(listOf("cat-squat", "custom-klimmzug"), template.exercises.map { it.exerciseStableId })
        assertEquals(listOf(0, 1), template.exercises.map { it.position })
        assertEquals(listOf(3, 4), template.exercises.map { it.setTypes.size })

        // Session graph rebuilt: exercises in order, each with its own sets reconnected by position.
        val session = sessions.sessions().first().single()
        assertEquals("custom-session", session.stableId)
        assertEquals(LocalDate.parse("2026-07-05"), session.date)
        assertFalse(session.isActive)
        assertEquals("Guter Tag", session.note)
        // The session's link back to the template it was started from must survive the round-trip
        // too — otherwise the templates list can't show "last used" after a fresh-device restore.
        assertEquals("custom-template", session.templateStableId)
        assertEquals(listOf("cat-squat", "custom-klimmzug"), session.exercises.map { it.exerciseStableId })

        val squat = session.exercises[0]
        assertEquals(2, squat.sets.size)
        assertEquals(listOf(100.0, 105.0), squat.sets.map { it.weightKg })
        assertEquals(listOf(SetType.WARMUP, SetType.NORMAL), squat.sets.map { it.type })

        val klimm = session.exercises[1]
        assertEquals(1, klimm.sets.size)
        assertEquals(8, klimm.sets.single().reps)
        assertTrue(klimm.sets.single().completed)
    }

    @Test
    fun `fresh device import relinks references against the seeded catalog and created customs`() = runTest {
        // Simulate a freshly installed device: the catalog is seeded, but has no custom exercises and
        // was never touched by templates/sessions. Row ids therefore differ from the source device.
        val catalog = FakeExerciseCatalogRepository()
        catalog.upsertAll(listOf(catalogSquat()))
        val templates = FakeWorkoutTemplateRepository()
        val sessions = FakeWorkoutSessionRepository()

        importZipEntries(
            ByteArrayInputStream(fullBackupZip()),
            targetsOf(catalog = catalog, templates = templates, sessions = sessions),
        )

        // The referenced custom exercise was created; the seeded catalog entry was reused, both by id.
        val ids = catalog.exercises().first().map { it.stableId }.toSet()
        assertTrue("cat-squat" in ids)
        assertTrue("custom-klimmzug" in ids)

        val template = templates.templates().first().single()
        // Every reference resolves to an exercise now present in the catalog.
        template.exercises.forEach { slot ->
            assertNotNull(catalog.exercise(slot.exerciseStableId).first())
        }
        val session = sessions.sessions().first().single()
        session.exercises.forEach { ex ->
            assertNotNull(catalog.exercise(ex.exerciseStableId).first())
        }
    }

    @Test
    fun `re-importing the same backup ZIP does not duplicate templates or sessions`() = runTest {
        val catalog = FakeExerciseCatalogRepository()
        val templates = FakeWorkoutTemplateRepository()
        val sessions = FakeWorkoutSessionRepository()
        val zip = fullBackupZip()
        val targets = targetsOf(catalog = catalog, templates = templates, sessions = sessions)

        importZipEntries(ByteArrayInputStream(zip), targets)
        val secondResult = importZipEntries(ByteArrayInputStream(zip), targets)

        // The second import still parses and reports every row — it's the target repositories that
        // recognise the stableId and replace in place rather than piling up duplicates.
        assertEquals(1, secondResult.exercisesImported)
        assertEquals(1, secondResult.templatesImported)
        assertEquals(1, secondResult.sessionsImported)

        assertEquals(1, catalog.exercises().first().count { it.stableId == "custom-klimmzug" })

        val allTemplates = templates.templates().first()
        assertEquals(1, allTemplates.size)
        assertEquals(2, allTemplates.single().exercises.size)

        val allSessions = sessions.sessions().first()
        assertEquals(1, allSessions.size)
        val session = allSessions.single()
        assertEquals(2, session.exercises.size)
        assertEquals(2, session.exercises[0].sets.size)
        assertEquals(1, session.exercises[1].sets.size)
    }

    @Test
    fun `legacy nutrition-only ZIP imports fine and reports zero training data`() = runTest {
        val zip = zipOf(
            "food_entries.csv" to """
                date,food_name,brand,amount_grams,kcal,protein_g,carbs_g,fat_g,sugar_g,fiber_g,salt_g,meal_category,tag,timestamp_ms
                2026-06-01,Apfel,,150,80,0.5,21,0.3,18,2,0,SNACK,NONE,1717200000000
            """.trimIndent(),
        )

        val result = importZipEntries(ByteArrayInputStream(zip), targetsOf())

        assertEquals(1, result.foodImported)
        assertEquals(0, result.exercisesImported)
        assertEquals(0, result.templatesImported)
        assertEquals(0, result.sessionsImported)
    }

    @Test
    fun `single exercises CSV imports custom exercises standalone`() = runTest {
        val catalog = FakeExerciseCatalogRepository()
        val lines = listOf(ExerciseCsvFormat.HEADER, ExerciseCsvFormat.toRow(customKlimmzug()))

        val result = importCsvLines(lines, targetsOf(catalog = catalog))

        assertEquals(1, result.exercisesImported)
        assertEquals("Klimmzug", catalog.exercises().first().single().name)
    }

    @Test
    fun `single body measurements CSV imports standalone`() = runTest {
        val measurements = FakeBodyMeasurementRepository()
        val entry = BodyMeasurementEntry(date = LocalDate.parse("2026-06-01"), type = MeasurementType.WAIST, valueCm = 81.5)
        val lines = listOf(BodyMeasurementCsvFormat.HEADER, BodyMeasurementCsvFormat.toRow(entry))

        val result = importCsvLines(lines, targetsOf(measurements = measurements))

        assertEquals(1, result.measurementsImported)
        val saved = measurements.entriesForDate(entry.date).first().single()
        assertEquals(MeasurementType.WAIST, saved.type)
        assertEquals(81.5, saved.valueCm, 0.001)
    }

    // --- Training fixtures & CSV builders (mirror what BackupExporter writes) --------------------

    private fun customKlimmzug() = Exercise(
        stableId = "custom-klimmzug",
        name = "Klimmzug",
        type = ExerciseType.BODYWEIGHT,
        isCustom = true,
        primaryMuscles = listOf("Lats", "Biceps"),
        secondaryMuscles = listOf("Forearms"),
        equipment = "body only",
        mechanic = Mechanic.COMPOUND,
        category = "strength",
        instructions = listOf("Hochziehen", "Ablassen"),
        restSeconds = 120,
    )

    private fun catalogSquat() = Exercise(
        stableId = "cat-squat",
        name = "Barbell Squat",
        type = ExerciseType.WEIGHT_REPS,
        isCustom = false,
        primaryMuscles = listOf("Quadriceps"),
    )

    private fun pushDayTemplate() = WorkoutTemplate(
        stableId = "custom-template",
        name = "Push Day",
        exercises = listOf(
            TemplateExercise(exerciseStableId = "cat-squat", position = 0, setTypes = List(3) { SetType.NORMAL }),
            TemplateExercise(exerciseStableId = "custom-klimmzug", position = 1, setTypes = List(4) { SetType.NORMAL }),
        ),
    )

    private fun loggedSession() = WorkoutSession(
        stableId = "custom-session",
        date = LocalDate.parse("2026-07-05"),
        isActive = false,
        startedAtMs = 1_720_000_000_000,
        endedAtMs = 1_720_000_360_000,
        note = "Guter Tag",
        templateStableId = "custom-template",
        exercises = listOf(
            SessionExercise(
                exerciseStableId = "cat-squat",
                position = 0,
                sets = listOf(
                    SetEntry(position = 0, weightKg = 100.0, reps = 5, type = SetType.WARMUP, completed = true),
                    SetEntry(position = 1, weightKg = 105.0, reps = 5, type = SetType.NORMAL, completed = true),
                ),
            ),
            SessionExercise(
                exerciseStableId = "custom-klimmzug",
                position = 1,
                sets = listOf(
                    SetEntry(position = 0, weightKg = 0.0, reps = 8, type = SetType.NORMAL, completed = true),
                ),
            ),
        ),
    )

    /** Builds a complete backup ZIP (nutrition + training) as BackupExporter would write it. */
    private fun fullBackupZip(): ByteArray {
        val exercises = listOf(customKlimmzug())
        val templates = listOf(pushDayTemplate())
        val sessions = listOf(loggedSession())
        val measurements = listOf(BodyMeasurementEntry(date = LocalDate.parse("2026-06-01"), type = MeasurementType.WAIST, valueCm = 81.5))
        return zipOf(
            "food_entries.csv" to """
                date,food_name,brand,amount_grams,kcal,protein_g,carbs_g,fat_g,sugar_g,fiber_g,salt_g,meal_category,tag,timestamp_ms
                2026-06-01,Apfel,,150,80,0.5,21,0.3,18,2,0,SNACK,NONE,1717200000000
            """.trimIndent(),
            "body_measurements.csv" to section(BodyMeasurementCsvFormat.HEADER, measurements.map { BodyMeasurementCsvFormat.toRow(it) }),
            WorkoutBackupEntries.EXERCISES to section(ExerciseCsvFormat.HEADER, exercises.map { ExerciseCsvFormat.toRow(it) }),
            WorkoutBackupEntries.WORKOUT_TEMPLATES to section(WorkoutTemplateCsvFormat.HEADER, templates.map { WorkoutTemplateCsvFormat.toRow(it) }),
            WorkoutBackupEntries.TEMPLATE_EXERCISES to section(
                TemplateExerciseCsvFormat.HEADER,
                templates.flatMap { t -> t.exercises.map { TemplateExerciseCsvFormat.toRow(t.stableId, it) } },
            ),
            WorkoutBackupEntries.WORKOUT_SESSIONS to section(WorkoutSessionCsvFormat.HEADER, sessions.map { WorkoutSessionCsvFormat.toRow(it) }),
            WorkoutBackupEntries.SESSION_EXERCISES to section(
                SessionExerciseCsvFormat.HEADER,
                sessions.flatMap { s -> s.exercises.map { SessionExerciseCsvFormat.toRow(s.stableId, it) } },
            ),
            WorkoutBackupEntries.SET_ENTRIES to section(
                SetEntryCsvFormat.HEADER,
                sessions.flatMap { s -> s.exercises.flatMap { e -> e.sets.map { SetEntryCsvFormat.toRow(s.stableId, e.position, it) } } },
            ),
        )
    }

    private fun section(header: String, rows: List<String>): String =
        (listOf(header) + rows).joinToString("\n")

    private fun targetsOf(
        food: FakeFoodEntryRepository = FakeFoodEntryRepository(),
        weight: FakeWeightRepository = FakeWeightRepository(),
        goal: FakeGoalRepository = FakeGoalRepository(),
        custom: FakeCustomFoodRepository = FakeCustomFoodRepository(),
        catalog: FakeExerciseCatalogRepository = FakeExerciseCatalogRepository(),
        templates: FakeWorkoutTemplateRepository = FakeWorkoutTemplateRepository(),
        sessions: FakeWorkoutSessionRepository = FakeWorkoutSessionRepository(),
        measurements: FakeBodyMeasurementRepository = FakeBodyMeasurementRepository(),
    ) = ImportTargets(food, weight, goal, custom, catalog, templates, sessions, measurements)

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
}
