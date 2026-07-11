package dev.antonlammers.macrotrac.ui.stats

import dev.antonlammers.macrotrac.domain.model.ExerciseType
import dev.antonlammers.macrotrac.domain.model.SessionExercise
import dev.antonlammers.macrotrac.domain.model.SetEntry
import dev.antonlammers.macrotrac.domain.model.SetType
import dev.antonlammers.macrotrac.domain.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class WorkoutSeriesTest {

    private val weightReps: (String) -> ExerciseType = { ExerciseType.WEIGHT_REPS }
    private val noBodyWeight: (LocalDate) -> Double? = { null }

    private fun set(weightKg: Double, reps: Int, type: SetType = SetType.NORMAL) =
        SetEntry(position = 0, weightKg = weightKg, reps = reps, type = type)

    private fun session(exerciseStableId: String, date: LocalDate, sets: List<SetEntry>) = WorkoutSession(
        stableId = "s-$date-$exerciseStableId",
        date = date,
        isActive = false,
        startedAtMs = 0L,
        endedAtMs = 1L,
        exercises = listOf(SessionExercise(exerciseStableId = exerciseStableId, position = 0, sets = sets)),
    )

    // --- frequency ---

    @Test
    fun `frequency buckets completed sessions per day for WEEK with empty days as zero`() {
        val to = LocalDate.of(2026, 7, 15)
        val from = to.minusDays(6)
        val dates = listOf(to, to, from) // two on the last day, one on the first

        val points = WorkoutSeries.frequency(TimeRange.WEEK, from, to, dates)

        assertEquals(7, points.size)
        assertEquals(1.0, points.first().value, 0.0)
        assertEquals(2.0, points.last().value, 0.0)
        assertEquals(0.0, points[3].value, 0.0) // an untrained middle day
    }

    @Test
    fun `frequency buckets per month for YEAR`() {
        val to = LocalDate.of(2026, 7, 15)
        val from = to.minusMonths(11).withDayOfMonth(1)
        val dates = listOf(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 6, 5))

        val points = WorkoutSeries.frequency(TimeRange.YEAR, from, to, dates)

        assertEquals(12, points.size)
        assertEquals(2.0, points.last().value, 0.0)      // July: two
        assertEquals(1.0, points[10].value, 0.0)         // June: one
    }

    // --- strength progression ---

    @Test
    fun `strength samples take the day's best 1RM and skip warm-up-only sessions`() {
        val d = LocalDate.of(2026, 7, 10)
        val sessions = listOf(
            session("bench", d, listOf(set(100.0, 5))),                          // e1RM 116.67
            session("bench", d, listOf(set(90.0, 10))),                          // e1RM 120.0 → day best
            session("bench", d.minusDays(2), listOf(set(120.0, 5, SetType.WARMUP))), // warm-up only → no sample
            session("bench", d.plusDays(3), listOf(set(105.0, 3))),              // e1RM 115.5
        )

        val samples = WorkoutSeries.strengthSamples(TimeRange.WEEK, sessions, "bench", weightReps, noBodyWeight)

        assertEquals(listOf(d, d.plusDays(3)), samples.map { it.date })
        assertEquals(120.0, samples[0].estimatedOneRepMaxKg, 0.01)
        assertEquals(115.5, samples[1].estimatedOneRepMaxKg, 0.01)
    }

    @Test
    fun `strength samples aggregate to the month's best for YEAR`() {
        val sessions = listOf(
            session("bench", LocalDate.of(2026, 6, 3), listOf(set(100.0, 5))),   // 116.67
            session("bench", LocalDate.of(2026, 6, 20), listOf(set(110.0, 5))),  // 128.33 → June best
            session("bench", LocalDate.of(2026, 7, 1), listOf(set(90.0, 5))),    // 105.0
        )

        val samples = WorkoutSeries.strengthSamples(TimeRange.YEAR, sessions, "bench", weightReps, noBodyWeight)

        assertEquals(2, samples.size)
        assertEquals(YearMonth.of(2026, 6).atDay(15), samples[0].date)
        assertEquals(128.333, samples[0].estimatedOneRepMaxKg, 0.01)
        assertEquals(105.0, samples[1].estimatedOneRepMaxKg, 0.01)
    }

    @Test
    fun `strength samples ignore other exercises`() {
        val d = LocalDate.of(2026, 7, 10)
        val sessions = listOf(
            session("bench", d, listOf(set(100.0, 5))),
            session("squat", d, listOf(set(140.0, 5))),
        )

        val samples = WorkoutSeries.strengthSamples(TimeRange.WEEK, sessions, "squat", weightReps, noBodyWeight)

        assertEquals(1, samples.size)
        assertEquals(163.333, samples[0].estimatedOneRepMaxKg, 0.01) // 140 × (1 + 5/30)
    }

    @Test
    fun `strength samples use the resolved body weight for bodyweight exercises`() {
        val d = LocalDate.of(2026, 7, 10)
        val sessions = listOf(session("pullup", d, listOf(set(10.0, 8)))) // +10 added
        val bodyWeight: (LocalDate) -> Double? = { 80.0 }

        val samples = WorkoutSeries.strengthSamples(
            TimeRange.WEEK, sessions, "pullup", { ExerciseType.BODYWEIGHT }, bodyWeight,
        )

        // (80 + 10) × (1 + 8/30) = 90 × 1.2667 = 114.0
        assertEquals(114.0, samples.single().estimatedOneRepMaxKg, 0.01)
    }

    // --- bounds ---

    @Test
    fun `bounds pad outward and round to the half kg`() {
        val samples = listOf(StrengthSample(LocalDate.of(2026, 7, 1), 100.0), StrengthSample(LocalDate.of(2026, 7, 2), 120.0))

        val (lo, hi) = WorkoutSeries.bounds(samples)

        // pad = max(0.5, 20 × 0.15 = 3.0) = 3.0 → 97.0 .. 123.0 (already on the half kg)
        assertEquals(97.0, lo, 0.0)
        assertEquals(123.0, hi, 0.0)
        assertTrue(lo < 100.0 && hi > 120.0)
    }

    @Test
    fun `bounds are zero when there are no samples`() {
        assertEquals(0.0 to 0.0, WorkoutSeries.bounds(emptyList()))
    }
}
