package dev.antonlammers.macrotrac.data.seed

import dev.antonlammers.macrotrac.domain.model.ExerciseType
import dev.antonlammers.macrotrac.domain.model.Mechanic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExerciseSnapshotParserTest {

    @Test
    fun `maps all needed fields and ignores extras`() {
        val json = """
            [{
              "id": "Barbell_Bench_Press",
              "name": "Barbell Bench Press",
              "force": "push",
              "level": "intermediate",
              "primaryMuscles": ["chest"],
              "secondaryMuscles": ["shoulders", "triceps"],
              "equipment": "barbell",
              "mechanic": "compound",
              "category": "strength",
              "instructions": ["Lie on the bench.", "Press up."],
              "images": ["Barbell_Bench_Press/0.jpg"]
            }]
        """.trimIndent()

        val result = ExerciseSnapshotParser.parse(json)

        assertEquals(1, result.size)
        val ex = result.first()
        assertEquals("Barbell_Bench_Press", ex.stableId)
        assertEquals("Barbell Bench Press", ex.name)
        assertEquals(listOf("chest"), ex.primaryMuscles)
        assertEquals(listOf("shoulders", "triceps"), ex.secondaryMuscles)
        assertEquals("barbell", ex.equipment)
        assertEquals(Mechanic.COMPOUND, ex.mechanic)
        assertEquals("strength", ex.category)
        assertEquals(listOf("Lie on the bench.", "Press up."), ex.instructions)
        // Catalog entries are never custom and carry no rest override.
        assertFalse(ex.isCustom)
        assertNull(ex.restSeconds)
    }

    @Test
    fun `derives BODYWEIGHT type from body only equipment`() {
        val json = """
            [{"id":"pushup","name":"Push-Up","equipment":"body only","primaryMuscles":["chest"]}]
        """.trimIndent()
        assertEquals(ExerciseType.BODYWEIGHT, ExerciseSnapshotParser.parse(json).first().type)
    }

    @Test
    fun `derives WEIGHT_REPS type for equipment other than body only`() {
        val json = """[{"id":"curl","name":"Curl","equipment":"dumbbell"}]"""
        assertEquals(ExerciseType.WEIGHT_REPS, ExerciseSnapshotParser.parse(json).first().type)
    }

    @Test
    fun `tolerates missing optional fields`() {
        val json = """[{"id":"x","name":"Minimal"}]"""
        val ex = ExerciseSnapshotParser.parse(json).first()
        assertEquals("Minimal", ex.name)
        assertTrue(ex.primaryMuscles.isEmpty())
        assertTrue(ex.secondaryMuscles.isEmpty())
        assertTrue(ex.instructions.isEmpty())
        assertNull(ex.equipment)
        assertNull(ex.mechanic)
        assertNull(ex.category)
        // No equipment → not "body only" → default weighted type.
        assertEquals(ExerciseType.WEIGHT_REPS, ex.type)
    }

    @Test
    fun `unknown mechanic falls back to null`() {
        val json = """[{"id":"x","name":"X","mechanic":"weird"}]"""
        assertNull(ExerciseSnapshotParser.parse(json).first().mechanic)
    }

    @Test
    fun `empty array yields empty list`() {
        assertTrue(ExerciseSnapshotParser.parse("[]").isEmpty())
    }

    @Test
    fun `bundled asset parses into a non-trivial catalog with unique stable ids`() {
        // Unit tests run with the module dir as working dir → read the real shipped asset.
        val asset = File("src/main/assets/exercise_catalog.json")
        assertTrue("bundled snapshot asset should exist", asset.exists())

        val exercises = ExerciseSnapshotParser.parse(asset.readText())

        assertTrue("expected a sizeable catalog", exercises.size > 500)
        assertTrue("no custom entries in the bundled snapshot", exercises.none { it.isCustom })
        assertEquals(
            "stable ids must be unique",
            exercises.size,
            exercises.map { it.stableId }.toSet().size,
        )
    }
}
