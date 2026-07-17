package dev.antonlammers.trainist.domain

import dev.antonlammers.trainist.domain.model.ExerciseType
import dev.antonlammers.trainist.domain.model.SessionExercise
import dev.antonlammers.trainist.domain.model.SetType
import dev.antonlammers.trainist.domain.model.WorkoutSession
import java.time.LocalDate

/** One performed set of an exercise, flattened out of its session for the exercise-detail log. */
data class PerformedSet(
    /** 1-based position within its session (in logged order). */
    val setNumber: Int,
    /** Weight as entered — for bodyweight exercises this is the added weight. */
    val weightKg: Double,
    val reps: Int,
    val type: SetType,
    /** True for the single set that holds the current max-weight PR. */
    val isPersonalRecord: Boolean = false,
)

/** One session in which the exercise was performed, with its performed sets. */
data class ExerciseSessionLog(
    val sessionId: Long,
    val date: LocalDate,
    val startedAtMs: Long,
    val sets: List<PerformedSet>,
)

/**
 * Everything the exercise-detail screen needs from the training history: the chronological session
 * log (newest first) and the current max-weight PR value.
 */
data class ExerciseHistoryData(
    val sessions: List<ExerciseSessionLog> = emptyList(),
    /** Highest effective weight over all non-warm-up performed sets, or null if never performed. */
    val personalRecordKg: Double? = null,
) {
    val hasData: Boolean get() = sessions.isNotEmpty()
}

/**
 * Pure, Android-free aggregation of one exercise's history across all sessions — analogous
 * to [InlineHistory]/[WorkoutMetrics]. Produces the chronological set log and locates the current
 * PR (the earliest set that reached the highest effective weight). The active session is included if
 * present (a set logged just now counts); [bodyWeightForDate] resolves body weight per session date
 * for bodyweight exercises (see [WorkoutMetrics.effectiveWeightKg]).
 */
object ExerciseHistory {

    fun build(
        history: List<WorkoutSession>,
        exerciseStableId: String,
        type: ExerciseType,
        bodyWeightForDate: (LocalDate) -> Double?,
    ): ExerciseHistoryData {
        // Sessions that trained the exercise, keeping only the sets actually performed (reps ≥ 1).
        val occurrences = history.mapNotNull { session ->
            val exercise = session.exercises.firstOrNull { it.exerciseStableId == exerciseStableId }
                ?: return@mapNotNull null
            val performed = exercise.sets.sortedBy { it.position }.filter { it.reps >= 1 }
            if (performed.isEmpty()) null else Occurrence(session, exercise, bodyWeightForDate(session.date))
        }

        // Current PR = max effective weight over non-warm-up performed sets; located to a single set
        // (the earliest by date → session start → position) so exactly one row gets the trophy.
        val prSet = occurrences
            .flatMap { occ ->
                occ.exercise.sets
                    .filter { it.type != SetType.WARMUP && it.reps >= 1 }
                    .map { PrCandidate(occ, it, WorkoutMetrics.effectiveWeightKg(type, it.weightKg, occ.bodyWeightKg)) }
            }
            .let { candidates ->
                val best = candidates.maxByOrNull { it.effectiveKg } ?: return@let null
                candidates
                    .filter { it.effectiveKg == best.effectiveKg }
                    .minWith(compareBy({ it.occ.session.date }, { it.occ.session.startedAtMs }, { it.set.position }))
            }
        val personalRecordKg = prSet?.effectiveKg

        val sessions = occurrences
            .sortedWith(compareByDescending<Occurrence> { it.session.date }.thenByDescending { it.session.startedAtMs })
            .map { occ ->
                val performed = occ.exercise.sets.sortedBy { it.position }.filter { it.reps >= 1 }
                ExerciseSessionLog(
                    sessionId = occ.session.id,
                    date = occ.session.date,
                    startedAtMs = occ.session.startedAtMs,
                    sets = performed.mapIndexed { index, set ->
                        PerformedSet(
                            setNumber = index + 1,
                            weightKg = set.weightKg,
                            reps = set.reps,
                            type = set.type,
                            isPersonalRecord = prSet != null && set === prSet.set,
                        )
                    },
                )
            }

        return ExerciseHistoryData(sessions, personalRecordKg)
    }

    private data class Occurrence(
        val session: WorkoutSession,
        val exercise: SessionExercise,
        val bodyWeightKg: Double?,
    )

    private data class PrCandidate(
        val occ: Occurrence,
        val set: dev.antonlammers.trainist.domain.model.SetEntry,
        val effectiveKg: Double,
    )
}
