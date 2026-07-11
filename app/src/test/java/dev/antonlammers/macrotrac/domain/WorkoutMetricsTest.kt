package dev.antonlammers.macrotrac.domain

import dev.antonlammers.macrotrac.domain.model.ExerciseType
import dev.antonlammers.macrotrac.domain.model.SessionExercise
import dev.antonlammers.macrotrac.domain.model.SetEntry
import dev.antonlammers.macrotrac.domain.model.SetType
import dev.antonlammers.macrotrac.domain.model.WeightEntry
import dev.antonlammers.macrotrac.domain.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorkoutMetricsTest {

    private fun set(
        weightKg: Double,
        reps: Int,
        type: SetType = SetType.NORMAL,
        position: Int = 0,
    ) = SetEntry(position = position, weightKg = weightKg, reps = reps, type = type)

    private fun weight(date: LocalDate, kg: Double) =
        WeightEntry(weightKg = kg, date = date, timestampMs = 0L)

    // --- effective weight ---

    @Test
    fun `weight-reps effective weight is the entered weight`() {
        assertEquals(80.0, WorkoutMetrics.effectiveWeightKg(ExerciseType.WEIGHT_REPS, 80.0, 75.0), 0.0)
    }

    @Test
    fun `bodyweight effective weight adds tracked body weight to the added weight`() {
        assertEquals(90.0, WorkoutMetrics.effectiveWeightKg(ExerciseType.BODYWEIGHT, 15.0, 75.0), 0.0)
    }

    @Test
    fun `bodyweight without added weight is the body weight`() {
        assertEquals(75.0, WorkoutMetrics.effectiveWeightKg(ExerciseType.BODYWEIGHT, 0.0, 75.0), 0.0)
    }

    @Test
    fun `bodyweight with no known body weight counts only the added weight`() {
        assertEquals(15.0, WorkoutMetrics.effectiveWeightKg(ExerciseType.BODYWEIGHT, 15.0, null), 0.0)
    }

    // --- Epley 1RM ---

    @Test
    fun `epley one rep max follows weight times one plus reps over thirty`() {
        // 100 × (1 + 10/30) = 133.33…
        assertEquals(133.333, WorkoutMetrics.epleyOneRepMaxKg(100.0, 10), 0.001)
    }

    @Test
    fun `epley one rep max is zero for a set with no reps`() {
        assertEquals(0.0, WorkoutMetrics.epleyOneRepMaxKg(100.0, 0), 0.0)
    }

    @Test
    fun `best estimated one rep max picks the highest Epley across performed sets`() {
        val sets = listOf(
            set(100.0, 5),  // 100 × (1 + 5/30)  = 116.67
            set(80.0, 12),  // 80  × (1 + 12/30) = 112.0
            set(60.0, 10, type = SetType.WARMUP), // excluded
        )
        assertEquals(116.667, WorkoutMetrics.bestEstimatedOneRepMaxKg(sets, ExerciseType.WEIGHT_REPS, null)!!, 0.001)
    }

    @Test
    fun `best estimated one rep max is null when there is no performed work set`() {
        val sets = listOf(set(60.0, 10, type = SetType.WARMUP), set(0.0, 0))
        assertNull(WorkoutMetrics.bestEstimatedOneRepMaxKg(sets, ExerciseType.WEIGHT_REPS, null))
    }

    // --- volume ---

    @Test
    fun `volume sums effective weight times reps and excludes warm-ups`() {
        val sets = listOf(
            set(60.0, 10, type = SetType.WARMUP), // excluded
            set(100.0, 5),                        // 500
            set(90.0, 8, type = SetType.DROP),    // 720
        )
        assertEquals(1220.0, WorkoutMetrics.volumeKg(sets, ExerciseType.WEIGHT_REPS, null), 0.0)
    }

    @Test
    fun `volume of only warm-ups is zero`() {
        val sets = listOf(
            set(60.0, 10, type = SetType.WARMUP),
            set(70.0, 8, type = SetType.WARMUP),
        )
        assertEquals(0.0, WorkoutMetrics.volumeKg(sets, ExerciseType.WEIGHT_REPS, null), 0.0)
    }

    @Test
    fun `bodyweight volume uses body weight plus added weight per set`() {
        val sets = listOf(set(0.0, 10), set(20.0, 5)) // (75×10) + ((75+20)×5) = 750 + 475
        assertEquals(1225.0, WorkoutMetrics.volumeKg(sets, ExerciseType.BODYWEIGHT, 75.0), 0.0)
    }

    // --- PR detection ---

    @Test
    fun `best effective weight ignores warm-ups and un-performed sets`() {
        val sets = listOf(
            set(120.0, 8, type = SetType.WARMUP), // heaviest but a warm-up → excluded
            set(0.0, 0),                          // placeholder, no reps → excluded
            set(100.0, 5),
            set(90.0, 3),
        )
        assertEquals(100.0, WorkoutMetrics.bestEffectiveWeightKg(sets, ExerciseType.WEIGHT_REPS, null)!!, 0.0)
    }

    @Test
    fun `best effective weight of a bodyweight-only exercise is body weight`() {
        val sets = listOf(set(0.0, 10)) // no added weight, reps performed
        assertEquals(75.0, WorkoutMetrics.bestEffectiveWeightKg(sets, ExerciseType.BODYWEIGHT, 75.0)!!, 0.0)
    }

    @Test
    fun `best effective weight is null when only warm-ups were done`() {
        val sets = listOf(set(120.0, 8, type = SetType.WARMUP))
        assertNull(WorkoutMetrics.bestEffectiveWeightKg(sets, ExerciseType.WEIGHT_REPS, null))
    }

    @Test
    fun `a strictly higher effective weight is a new personal record`() {
        assertTrue(WorkoutMetrics.isNewPersonalRecord(102.5, 100.0))
    }

    @Test
    fun `the first ever performance is a personal record`() {
        assertTrue(WorkoutMetrics.isNewPersonalRecord(60.0, null))
    }

    @Test
    fun `a tie is not a new personal record`() {
        assertFalse(WorkoutMetrics.isNewPersonalRecord(100.0, 100.0))
    }

    @Test
    fun `a lower weight is not a new personal record`() {
        assertFalse(WorkoutMetrics.isNewPersonalRecord(97.5, 100.0))
    }

    @Test
    fun `no performed set is never a personal record`() {
        assertFalse(WorkoutMetrics.isNewPersonalRecord(null, 100.0))
        assertFalse(WorkoutMetrics.isNewPersonalRecord(null, null))
    }

    // --- PR sweep across sessions ---

    private fun session(
        stableId: String,
        date: LocalDate,
        exerciseStableId: String,
        sets: List<SetEntry>,
        startedAtMs: Long = 0L,
    ) = WorkoutSession(
        stableId = stableId,
        date = date,
        isActive = false,
        startedAtMs = startedAtMs,
        endedAtMs = startedAtMs + 1,
        exercises = listOf(SessionExercise(exerciseStableId = exerciseStableId, position = 0, sets = sets)),
    )

    @Test
    fun `personal records marks each new max weight and ignores ties and regressions`() {
        val history = listOf(
            session("s1", LocalDate.of(2026, 7, 1), "bench", listOf(set(100.0, 5))), // first → PR
            session("s2", LocalDate.of(2026, 7, 3), "bench", listOf(set(100.0, 8))), // heavier reps, same weight → tie, no PR
            session("s3", LocalDate.of(2026, 7, 5), "bench", listOf(set(102.5, 3))), // heavier → PR
            session("s4", LocalDate.of(2026, 7, 8), "bench", listOf(set(97.5, 10))), // lighter → no PR
        )
        val prs = WorkoutMetrics.personalRecords(
            history,
            typeOf = { ExerciseType.WEIGHT_REPS },
            bodyWeightForDate = { null },
        )
        assertEquals(setOf("s1" to "bench", "s3" to "bench"), prs)
    }

    @Test
    fun `personal records are tracked independently per exercise`() {
        val history = listOf(
            session("s1", LocalDate.of(2026, 7, 1), "bench", listOf(set(100.0, 5))),
            session("s2", LocalDate.of(2026, 7, 2), "squat", listOf(set(140.0, 5))),
        )
        val prs = WorkoutMetrics.personalRecords(
            history,
            typeOf = { ExerciseType.WEIGHT_REPS },
            bodyWeightForDate = { null },
        )
        assertEquals(setOf("s1" to "bench", "s2" to "squat"), prs)
    }

    @Test
    fun `personal records use the body weight of each session for bodyweight exercises`() {
        val history = listOf(
            // 80 kg body weight + 0 added = 80 effective → first PR
            session("s1", LocalDate.of(2026, 7, 1), "pullup", listOf(set(0.0, 8))),
            // 82 kg body weight later + 0 added = 82 effective → new PR from body-weight gain alone
            session("s2", LocalDate.of(2026, 7, 8), "pullup", listOf(set(0.0, 6))),
        )
        val bodyWeights = mapOf(
            LocalDate.of(2026, 7, 1) to 80.0,
            LocalDate.of(2026, 7, 8) to 82.0,
        )
        val prs = WorkoutMetrics.personalRecords(
            history,
            typeOf = { ExerciseType.BODYWEIGHT },
            bodyWeightForDate = { bodyWeights[it] },
        )
        assertEquals(setOf("s1" to "pullup", "s2" to "pullup"), prs)
    }

    @Test
    fun `an exercise with only warm-ups never sets a record`() {
        val history = listOf(
            session("s1", LocalDate.of(2026, 7, 1), "bench", listOf(set(120.0, 5, type = SetType.WARMUP))),
        )
        val prs = WorkoutMetrics.personalRecords(
            history,
            typeOf = { ExerciseType.WEIGHT_REPS },
            bodyWeightForDate = { null },
        )
        assertTrue(prs.isEmpty())
    }

    // --- body weight resolution ---

    @Test
    fun `body weight resolves to the entry on the session date`() {
        val entries = listOf(
            weight(LocalDate.of(2026, 7, 1), 80.0),
            weight(LocalDate.of(2026, 7, 5), 79.0),
        )
        assertEquals(79.0, WorkoutMetrics.resolveBodyWeightKg(entries, LocalDate.of(2026, 7, 5))!!, 0.0)
    }

    @Test
    fun `body weight falls back to the most recent earlier entry`() {
        val entries = listOf(
            weight(LocalDate.of(2026, 7, 1), 80.0),
            weight(LocalDate.of(2026, 7, 3), 79.5),
        )
        // Nothing recorded on the 10th → use the last known (the 3rd).
        assertEquals(79.5, WorkoutMetrics.resolveBodyWeightKg(entries, LocalDate.of(2026, 7, 10))!!, 0.0)
    }

    @Test
    fun `body weight falls back to the earliest entry when all are later than the date`() {
        val entries = listOf(
            weight(LocalDate.of(2026, 7, 5), 79.0),
            weight(LocalDate.of(2026, 7, 8), 78.5),
        )
        assertEquals(79.0, WorkoutMetrics.resolveBodyWeightKg(entries, LocalDate.of(2026, 7, 1))!!, 0.0)
    }

    @Test
    fun `body weight is null when there are no entries at all`() {
        assertNull(WorkoutMetrics.resolveBodyWeightKg(emptyList(), LocalDate.of(2026, 7, 1)))
    }
}
