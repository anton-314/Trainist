package dev.antonlammers.macrotrac.domain

import dev.antonlammers.macrotrac.domain.model.SessionExercise
import dev.antonlammers.macrotrac.domain.model.SetEntry
import dev.antonlammers.macrotrac.domain.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class InlineHistoryTest {

    private fun session(
        id: Long,
        date: LocalDate,
        startedAtMs: Long,
        isActive: Boolean,
        exerciseStableId: String,
        sets: List<Pair<Double, Int>>,
    ) = WorkoutSession(
        id = id,
        stableId = "s$id",
        date = date,
        isActive = isActive,
        startedAtMs = startedAtMs,
        endedAtMs = if (isActive) null else startedAtMs + 1,
        exercises = listOf(
            SessionExercise(
                exerciseStableId = exerciseStableId,
                position = 0,
                sets = sets.mapIndexed { i, (w, r) -> SetEntry(position = i, weightKg = w, reps = r) },
            ),
        ),
    )

    @Test
    fun `returns empty when the exercise has never been trained`() {
        val history = listOf(
            session(1, LocalDate.of(2026, 7, 1), 1000, isActive = false, "squat", listOf(100.0 to 5)),
        )
        assertEquals(emptyList<SetPerformance>(), InlineHistory.lastPerformance(history, "bench"))
    }

    @Test
    fun `returns the sets of the most recent completed session in position order`() {
        val history = listOf(
            session(1, LocalDate.of(2026, 7, 1), 1000, isActive = false, "bench", listOf(80.0 to 8, 82.5 to 6)),
            session(2, LocalDate.of(2026, 7, 5), 2000, isActive = false, "bench", listOf(85.0 to 8, 85.0 to 7, 85.0 to 5)),
        )
        assertEquals(
            listOf(SetPerformance(85.0, 8), SetPerformance(85.0, 7), SetPerformance(85.0, 5)),
            InlineHistory.lastPerformance(history, "bench"),
        )
    }

    @Test
    fun `later start time on the same day wins`() {
        val day = LocalDate.of(2026, 7, 5)
        val history = listOf(
            session(1, day, 5000, isActive = false, "bench", listOf(90.0 to 3)),
            session(2, day, 1000, isActive = false, "bench", listOf(70.0 to 10)),
        )
        assertEquals(listOf(SetPerformance(90.0, 3)), InlineHistory.lastPerformance(history, "bench"))
    }

    @Test
    fun `the active session is ignored`() {
        val history = listOf(
            session(1, LocalDate.of(2026, 7, 1), 1000, isActive = false, "bench", listOf(80.0 to 8)),
            // A newer session, but still in progress — must not be used as "last time".
            session(2, LocalDate.of(2026, 7, 9), 9000, isActive = true, "bench", listOf(0.0 to 0)),
        )
        assertEquals(listOf(SetPerformance(80.0, 8)), InlineHistory.lastPerformance(history, "bench"))
    }

    @Test
    fun `placeholderForSet returns the value at the index or null past the end`() {
        val last = listOf(SetPerformance(80.0, 8), SetPerformance(82.5, 6))
        assertEquals(SetPerformance(80.0, 8), InlineHistory.placeholderForSet(last, 0))
        assertEquals(SetPerformance(82.5, 6), InlineHistory.placeholderForSet(last, 1))
        assertNull(InlineHistory.placeholderForSet(last, 2))
    }
}
