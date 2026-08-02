package dev.antonlammers.trainist.domain

import dev.antonlammers.trainist.domain.model.SessionExercise
import dev.antonlammers.trainist.domain.model.SetEntry
import dev.antonlammers.trainist.domain.model.SetType
import dev.antonlammers.trainist.domain.model.TemplateExercise
import dev.antonlammers.trainist.domain.model.WorkoutSession
import dev.antonlammers.trainist.domain.model.WorkoutTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TemplateUpdateTest {

    private fun template(vararg slots: TemplateExercise) =
        WorkoutTemplate(id = 7, stableId = "tpl", name = "Push", exercises = slots.toList())

    private fun slot(stableId: String, position: Int, vararg types: SetType) =
        TemplateExercise(stableId, position, types.toList())

    private fun session(vararg exercises: SessionExercise) = WorkoutSession(
        stableId = "sess",
        date = LocalDate.of(2026, 7, 16),
        isActive = false,
        startedAtMs = 0L,
        templateStableId = "tpl",
        exercises = exercises.toList(),
    )

    /** A session exercise whose sets are (type, completed) pairs. */
    private fun sessionExercise(stableId: String, position: Int, vararg sets: Pair<SetType, Boolean>) =
        SessionExercise(
            exerciseStableId = stableId,
            position = position,
            sets = sets.mapIndexed { i, (type, completed) ->
                SetEntry(position = i, weightKg = 60.0, reps = 8, type = type, completed = completed)
            },
        )

    // --- superset grouping ---

    @Test
    fun `a superset formed during the session is folded back into the template`() {
        val tpl = template(slot("bench", 0, SetType.NORMAL), slot("row", 1, SetType.NORMAL))
        val sess = session(
            sessionExercise("bench", 0, SetType.NORMAL to true).copy(supersetGroupId = 1),
            sessionExercise("row", 1, SetType.NORMAL to true).copy(supersetGroupId = 1),
        )

        val merged = TemplateUpdate.merge(tpl, sess)

        assertEquals(listOf(1, 1), merged!!.exercises.map { it.supersetGroupId })
    }

    @Test
    fun `a superset dissolved during the session is dissolved in the template too`() {
        val tpl = template(
            slot("bench", 0, SetType.NORMAL).copy(supersetGroupId = 1),
            slot("row", 1, SetType.NORMAL).copy(supersetGroupId = 1),
        )
        val sess = session(
            sessionExercise("bench", 0, SetType.NORMAL to true),
            sessionExercise("row", 1, SetType.NORMAL to true),
        )

        val merged = TemplateUpdate.merge(tpl, sess)

        assertEquals(listOf(null, null), merged!!.exercises.map { it.supersetGroupId })
    }

    @Test
    fun `a session matching its template's supersets yields no update`() {
        val tpl = template(
            slot("bench", 0, SetType.NORMAL).copy(supersetGroupId = 1),
            slot("row", 1, SetType.NORMAL).copy(supersetGroupId = 1),
        )
        val sess = session(
            sessionExercise("bench", 0, SetType.NORMAL to true).copy(supersetGroupId = 1),
            sessionExercise("row", 1, SetType.NORMAL to true).copy(supersetGroupId = 1),
        )
        assertNull(TemplateUpdate.merge(tpl, sess))
    }

    @Test
    fun `a skipped slot between two grouped performed ones cannot leave a group of one`() {
        // "bench" is skipped and keeps its group; "row" was performed alone. The surviving members of
        // the old group are no longer adjacent-and-paired, so normalize must dissolve it rather than
        // leave "bench" as a lone superset member.
        val tpl = template(
            slot("bench", 0, SetType.NORMAL).copy(supersetGroupId = 1),
            slot("row", 1, SetType.NORMAL).copy(supersetGroupId = 1),
        )
        val sess = session(sessionExercise("row", 0, SetType.NORMAL to true))

        val merged = TemplateUpdate.merge(tpl, sess)

        assertEquals(listOf(null, null), merged!!.exercises.map { it.supersetGroupId })
    }

    @Test
    fun `a live-added superset is appended to the template as a group`() {
        val tpl = template(slot("bench", 0, SetType.NORMAL))
        val sess = session(
            sessionExercise("bench", 0, SetType.NORMAL to true),
            sessionExercise("curl", 1, SetType.NORMAL to true).copy(supersetGroupId = 1),
            sessionExercise("fly", 2, SetType.NORMAL to true).copy(supersetGroupId = 1),
        )

        val merged = TemplateUpdate.merge(tpl, sess)!!

        assertEquals(listOf("bench", "curl", "fly"), merged.exercises.map { it.exerciseStableId })
        assertEquals(listOf(null, 1, 1), merged.exercises.map { it.supersetGroupId })
    }

    @Test
    fun `an unchanged session yields no update`() {
        val tpl = template(
            slot("bench", 0, SetType.NORMAL, SetType.NORMAL),
            slot("squat", 1, SetType.NORMAL),
        )
        val sess = session(
            sessionExercise("bench", 0, SetType.NORMAL to true, SetType.NORMAL to true),
            sessionExercise("squat", 1, SetType.NORMAL to true),
        )
        assertNull(TemplateUpdate.merge(tpl, sess))
    }

    @Test
    fun `an added set on a performed exercise grows that slot`() {
        val tpl = template(slot("bench", 0, SetType.NORMAL, SetType.NORMAL))
        val sess = session(
            sessionExercise("bench", 0, SetType.NORMAL to true, SetType.NORMAL to true, SetType.NORMAL to true),
        )
        val merged = TemplateUpdate.merge(tpl, sess)!!
        assertEquals(7, merged.id) // same row → replaces, not duplicates
        assertEquals(
            listOf(SetType.NORMAL, SetType.NORMAL, SetType.NORMAL),
            merged.exercises.single().setTypes,
        )
    }

    @Test
    fun `a changed set type carries over`() {
        val tpl = template(slot("bench", 0, SetType.NORMAL, SetType.NORMAL))
        val sess = session(
            sessionExercise("bench", 0, SetType.NORMAL to true, SetType.DROP to true),
        )
        val merged = TemplateUpdate.merge(tpl, sess)!!
        assertEquals(listOf(SetType.NORMAL, SetType.DROP), merged.exercises.single().setTypes)
    }

    @Test
    fun `an added but unchecked set does not leak into the template`() {
        val tpl = template(slot("bench", 0, SetType.NORMAL, SetType.NORMAL))
        val sess = session(
            // Two completed as planned + a third that was added but never checked off.
            sessionExercise("bench", 0, SetType.NORMAL to true, SetType.NORMAL to true, SetType.NORMAL to false),
        )
        assertNull(TemplateUpdate.merge(tpl, sess))
    }

    @Test
    fun `a skipped exercise stays unchanged in the template`() {
        val tpl = template(
            slot("bench", 0, SetType.NORMAL, SetType.NORMAL),
            slot("squat", 1, SetType.NORMAL, SetType.NORMAL, SetType.NORMAL),
        )
        val sess = session(
            sessionExercise("bench", 0, SetType.NORMAL to true, SetType.NORMAL to true, SetType.NORMAL to true),
            // squat present but nothing checked off → skipped.
            sessionExercise("squat", 1, SetType.NORMAL to false, SetType.NORMAL to false, SetType.NORMAL to false),
        )
        val merged = TemplateUpdate.merge(tpl, sess)!!
        assertEquals(listOf("bench", "squat"), merged.exercises.map { it.exerciseStableId })
        // bench grew to 3, squat kept its planned 3.
        assertEquals(3, merged.exercises[0].setTypes.size)
        assertEquals(listOf(SetType.NORMAL, SetType.NORMAL, SetType.NORMAL), merged.exercises[1].setTypes)
    }

    @Test
    fun `a live-added exercise is appended`() {
        val tpl = template(slot("bench", 0, SetType.NORMAL))
        val sess = session(
            sessionExercise("bench", 0, SetType.NORMAL to true),
            sessionExercise("curl", 1, SetType.WARMUP to true, SetType.NORMAL to true),
        )
        val merged = TemplateUpdate.merge(tpl, sess)!!
        assertEquals(listOf("bench", "curl"), merged.exercises.map { it.exerciseStableId })
        assertEquals(listOf(0, 1), merged.exercises.map { it.position })
        assertEquals(listOf(SetType.WARMUP, SetType.NORMAL), merged.exercises[1].setTypes)
    }

    @Test
    fun `an exercise with only warmups checked off is still treated as performed`() {
        // "performed" == at least one completed set, regardless of type — the plan becomes those sets.
        val tpl = template(slot("bench", 0, SetType.NORMAL, SetType.NORMAL))
        val sess = session(
            sessionExercise("bench", 0, SetType.WARMUP to true),
        )
        val merged = TemplateUpdate.merge(tpl, sess)!!
        assertEquals(listOf(SetType.WARMUP), merged.exercises.single().setTypes)
    }
}
