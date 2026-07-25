package dev.antonlammers.trainist.ui.stats

import dev.antonlammers.trainist.domain.model.ExerciseType
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.domain.model.SessionExercise
import dev.antonlammers.trainist.domain.model.SetEntry
import dev.antonlammers.trainist.domain.model.SetType
import dev.antonlammers.trainist.domain.model.WeightEntry
import dev.antonlammers.trainist.domain.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class OverloadSeriesTest {

    private val weightReps: (String) -> ExerciseType = { ExerciseType.WEIGHT_REPS }
    private val noBodyWeight: (LocalDate) -> Double? = { null }

    /** A Monday, so week arithmetic in the tests below reads plainly. */
    private val week0 = LocalDate.of(2026, 4, 6)

    private fun set(weightKg: Double, reps: Int, type: SetType = SetType.NORMAL) =
        SetEntry(position = 0, weightKg = weightKg, reps = reps, type = type)

    /** One session on [date] performing each `exercise to sets` pair. */
    private fun session(date: LocalDate, vararg exercises: Pair<String, List<SetEntry>>) = WorkoutSession(
        stableId = "s-$date-${exercises.joinToString { it.first }}",
        date = date,
        isActive = false,
        startedAtMs = 0L,
        endedAtMs = 1L,
        exercises = exercises.mapIndexed { i, (id, sets) ->
            SessionExercise(exerciseStableId = id, position = i, sets = sets)
        },
    )

    /** A single set at [weightKg] × 1 rep, whose Epley 1RM is exactly `weightKg * (1 + 1/30)`. */
    private fun single(weightKg: Double) = listOf(set(weightKg, 1))

    private fun index(sessions: List<WorkoutSession>) =
        OverloadSeries.index(sessions, weightReps, noBodyWeight)

    // --- week bucketing ---------------------------------------------------------------------

    @Test
    fun `weeks start on Monday`() {
        assertEquals(week0, OverloadSeries.weekStart(week0))
        assertEquals(week0, OverloadSeries.weekStart(week0.plusDays(6)))
        assertEquals(week0.plusWeeks(1), OverloadSeries.weekStart(week0.plusDays(7)))
        assertEquals(DayOfWeek.MONDAY, OverloadSeries.windowStart(LocalDate.of(2026, 7, 25)).dayOfWeek)
    }

    @Test
    fun `the window spans exactly WINDOW_WEEKS weeks including the current one`() {
        val today = LocalDate.of(2026, 7, 25)

        val from = OverloadSeries.windowStart(today)

        assertEquals(OverloadSeries.WINDOW_WEEKS.toLong() - 1, java.time.temporal.ChronoUnit.WEEKS.between(from, OverloadSeries.weekStart(today)))
    }

    // --- the index itself -------------------------------------------------------------------

    @Test
    fun `no sessions yields no points`() {
        val result = index(emptyList())

        assertTrue(result.points.isEmpty())
        assertEquals(0, result.trackedExercises)
    }

    @Test
    fun `the series starts at the base index`() {
        val result = index(listOf(session(week0, "bench" to single(100.0))))

        assertEquals(1, result.points.size)
        assertEquals(BASE_INDEX, result.points.single().index, 0.0001)
        // A first appearance carries no comparison, so nothing is tracked yet.
        assertEquals(0, result.trackedExercises)
    }

    @Test
    fun `a ten percent gain on the only exercise moves the index by ten percent`() {
        val result = index(
            listOf(
                session(week0, "bench" to single(100.0)),
                session(week0.plusWeeks(1), "bench" to single(110.0)),
            ),
        )

        assertEquals(listOf(100.0, 110.0), result.points.map { it.index }.map { round2(it) })
        assertEquals(1, result.trackedExercises)
        assertEquals(10.0, OverloadSeries.totalChangePercent(result.points)!!, 0.01)
    }

    @Test
    fun `equal and opposite moves on two exercises cancel out`() {
        val result = index(
            listOf(
                session(week0, "bench" to single(100.0), "squat" to single(100.0)),
                // +10 % and −10 % → the geometric mean nets back to the base index.
                session(week0.plusWeeks(1), "bench" to single(110.0), "squat" to single(100.0 / 1.1)),
            ),
        )

        assertEquals(BASE_INDEX, result.points.last().index, 0.0001)
    }

    @Test
    fun `a rotating split does not fake progress`() {
        // Week 0 trains a heavy lift, week 1 a light one, week 2 the heavy one again — unchanged.
        // A naive average of 1RMs would crash in week 1 and spike back in week 2.
        val result = index(
            listOf(
                session(week0, "squat" to single(150.0)),
                session(week0.plusWeeks(1), "curl" to single(20.0)),
                session(week0.plusWeeks(2), "squat" to single(150.0)),
            ),
        )

        assertTrue(result.points.all { round2(it.index) == 100.0 })
    }

    @Test
    fun `a newly added exercise never dilutes an existing progression`() {
        val result = index(
            listOf(
                session(week0, "bench" to single(100.0)),
                session(week0.plusWeeks(1), "bench" to single(105.0)),
                // The new exercise only shows up here; it must not pull the index back toward 100.
                session(week0.plusWeeks(2), "bench" to single(105.0), "rows" to single(60.0)),
            ),
        )

        assertEquals(105.0, round2(result.points.last().index), 0.0)
    }

    @Test
    fun `a comparison older than the look-back limit is dropped instead of compressed`() {
        val result = index(
            listOf(
                session(week0, "bench" to single(100.0)),
                // Six weeks later — too far back for a single weekly step, so it re-baselines.
                session(week0.plusWeeks(6), "bench" to single(150.0)),
            ),
        )

        assertEquals(listOf(100.0, 100.0), result.points.map { round2(it.index) })
        assertEquals(0, result.trackedExercises)
    }

    @Test
    fun `an implausible step is clamped so a typo cannot destroy the series`() {
        val result = index(
            listOf(
                session(week0, "bench" to single(100.0)),
                session(week0.plusWeeks(1), "bench" to single(1000.0)), // fat-fingered
            ),
        )

        assertEquals(120.0, round2(result.points.last().index), 0.0)
    }

    @Test
    fun `warm-up-only weeks contribute no data`() {
        val result = index(
            listOf(
                session(week0, "bench" to listOf(set(60.0, 10, SetType.WARMUP))),
                session(week0.plusWeeks(1), "bench" to single(100.0)),
            ),
        )

        assertEquals(1, result.points.size)
    }

    @Test
    fun `a week takes its best 1RM and is dated at its last training day`() {
        val result = index(
            listOf(
                session(week0, "bench" to single(100.0)),
                session(week0.plusDays(3), "bench" to single(120.0)),
                session(week0.plusWeeks(1), "bench" to single(120.0)),
            ),
        )

        assertEquals(week0.plusDays(3), result.points.first().date)
        // Week 0's best (120) carried into week 1 unchanged → no growth.
        assertEquals(BASE_INDEX, result.points.last().index, 0.0001)
    }

    // --- derived figures ---------------------------------------------------------------------

    @Test
    fun `a single point is a baseline, not a change`() {
        val points = index(listOf(session(week0, "bench" to single(100.0)))).points

        assertNull(OverloadSeries.totalChangePercent(points))
    }

    @Test
    fun `changeSince compares against the last point at or before the cutoff`() {
        val points = index(
            listOf(
                session(week0, "bench" to single(100.0)),
                session(week0.plusWeeks(1), "bench" to single(110.0)),
                session(week0.plusWeeks(2), "bench" to single(110.0 * 1.1)),
            ),
        ).points

        val recent = OverloadSeries.changeSince(points, week0.plusWeeks(1))

        assertEquals(10.0, recent!!, 0.01)
        assertNull(OverloadSeries.changeSince(points, week0.minusWeeks(1)))
    }

    @Test
    fun `spanWeeks counts the gap between the first and last point, not the trained weeks`() {
        val points = index(
            listOf(
                session(week0, "bench" to single(100.0)),
                session(week0.plusWeeks(5), "bench" to single(100.0)),
            ),
        ).points

        assertEquals(6, OverloadSeries.spanWeeks(points))
        assertEquals(0, OverloadSeries.spanWeeks(emptyList()))
    }

    @Test
    fun `bounds always keep the base index visible`() {
        val (min, max) = OverloadSeries.bounds(listOf(OverloadPoint(week0, 112.0), OverloadPoint(week0, 118.0)))

        assertTrue(min < BASE_INDEX)
        assertTrue(max > 118.0)
    }

    // --- nutrition / body-weight aggregates ----------------------------------------------------

    @Test
    fun `body-weight trend needs enough weigh-ins over enough days`() {
        val short = (0..2L).map { WeightEntry(date = week0.plusDays(it), weightKg = 80.0, timestampMs = it) }
        assertNull(OverloadSeries.bodyWeightTrendKgPerWeek(short))

        // 80 kg down to 76 kg over 28 days → −1 kg per week.
        val long = (0..4L).map {
            WeightEntry(date = week0.plusDays(it * 7), weightKg = 80.0 - it, timestampMs = it)
        }
        assertEquals(-1.0, OverloadSeries.bodyWeightTrendKgPerWeek(long)!!, 0.001)
    }

    @Test
    fun `unlogged days do not count as zero-kcal days`() {
        // 25 logged days at 2000 kcal inside a window where most days have no entry at all.
        val entries = (0 until 25).map { food(week0.plusDays(it.toLong()), kcal = 2000.0, proteinG = 160.0) }

        assertEquals(1.0, OverloadSeries.kcalVsGoal(entries, goalKcal = 2000.0)!!, 0.001)
        assertEquals(2.0, OverloadSeries.proteinPerKgBodyWeight(entries, bodyWeightKg = 80.0)!!, 0.001)
    }

    @Test
    fun `too few logged days yields no intake signal`() {
        val entries = (0 until OverloadSeries.MIN_LOGGED_DAYS - 1).map {
            food(week0.plusDays(it.toLong()), kcal = 500.0, proteinG = 10.0)
        }

        assertNull(OverloadSeries.kcalVsGoal(entries, goalKcal = 2000.0))
        assertNull(OverloadSeries.proteinPerKgBodyWeight(entries, bodyWeightKg = 80.0))
    }

    @Test
    fun `intake signals need a goal and a body weight`() {
        val entries = (0 until 25).map { food(week0.plusDays(it.toLong()), kcal = 2000.0, proteinG = 160.0) }

        assertNull(OverloadSeries.kcalVsGoal(entries, goalKcal = 0.0))
        assertNull(OverloadSeries.proteinPerKgBodyWeight(entries, bodyWeightKg = null))
        assertNotNull(OverloadSeries.proteinPerKgBodyWeight(entries, bodyWeightKg = 80.0))
    }

    private fun food(date: LocalDate, kcal: Double, proteinG: Double) = FoodEntry(
        foodName = "x",
        brand = null,
        amountGrams = 100.0,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = 0.0,
        fatG = 0.0,
        date = date,
        timestampMs = 0L,
    )

    private fun round2(v: Double) = Math.round(v * 100) / 100.0
}
