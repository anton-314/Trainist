package dev.antonlammers.trainist.domain

import dev.antonlammers.trainist.domain.model.ExerciseType
import dev.antonlammers.trainist.domain.model.SetEntry
import dev.antonlammers.trainist.domain.model.SetType
import dev.antonlammers.trainist.domain.model.WeightEntry
import dev.antonlammers.trainist.domain.model.WorkoutSession
import java.time.LocalDate

/**
 * Pure, Android-free training calculations — analogous to [MacroCalculator] and
 * `WeightSeries`, reachable by JVM unit tests. Everything is derived from plain values so the ViewModel
 * (which owns the Room/Weight-repository wiring) can feed it and the later history/stats screens can
 * reuse the exact same logic.
 *
 * The **effective weight** of a set is the basis for volume, 1RM and PRs:
 * - [ExerciseType.WEIGHT_REPS]: the entered external weight.
 * - [ExerciseType.BODYWEIGHT]: the tracked body weight + the optional added weight (the real link
 *   between the nutrition and training sides). With no recorded body weight the caller
 *   passes the last known one (see [resolveBodyWeightKg]); if none exists at all it passes null and
 *   only the added weight is counted.
 */
object WorkoutMetrics {

    /** Effective weight of a single set. */
    fun effectiveWeightKg(type: ExerciseType, enteredWeightKg: Double, bodyWeightKg: Double?): Double =
        when (type) {
            ExerciseType.WEIGHT_REPS -> enteredWeightKg
            ExerciseType.BODYWEIGHT -> (bodyWeightKg ?: 0.0) + enteredWeightKg
        }

    /**
     * Estimated 1RM of a single set by the **Epley** formula: `weight × (1 + reps / 30)`.
     * Display-only — never a PR trigger. Zero for a set with no reps (nothing was lifted).
     */
    fun epleyOneRepMaxKg(effectiveWeightKg: Double, reps: Int): Double =
        if (reps <= 0) 0.0 else effectiveWeightKg * (1.0 + reps / 30.0)

    /** Volume contribution of one set: effective weight × reps — but **0 for warm-up sets**. */
    fun setVolumeKg(set: SetEntry, type: ExerciseType, bodyWeightKg: Double?): Double =
        if (set.type == SetType.WARMUP) 0.0
        else effectiveWeightKg(type, set.weightKg, bodyWeightKg) * set.reps

    /** Total volume over [sets] (Σ effective weight × reps; warm-ups excluded). */
    fun volumeKg(sets: List<SetEntry>, type: ExerciseType, bodyWeightKg: Double?): Double =
        sets.sumOf { setVolumeKg(it, type, bodyWeightKg) }

    /**
     * The best estimated 1RM over the performed, non-warm-up sets (highest Epley value), or null if
     * there is no such set. A per-exercise display summary of [epleyOneRepMaxKg].
     */
    fun bestEstimatedOneRepMaxKg(sets: List<SetEntry>, type: ExerciseType, bodyWeightKg: Double?): Double? =
        performedWorkSets(sets)
            .map { epleyOneRepMaxKg(effectiveWeightKg(type, it.weightKg, bodyWeightKg), it.reps) }
            .maxOrNull()

    /**
     * The highest effective weight over the performed, non-warm-up sets — the basis for PR detection
     *, or null if there is no such set. Warm-ups and un-performed sets (reps < 1) are
     * excluded, so e.g. an untouched placeholder set of a bodyweight exercise is never a "record".
     */
    fun bestEffectiveWeightKg(sets: List<SetEntry>, type: ExerciseType, bodyWeightKg: Double?): Double? =
        performedWorkSets(sets)
            .map { effectiveWeightKg(type, it.weightKg, bodyWeightKg) }
            .maxOrNull()

    /**
     * Whether [candidateBestKg] is a **new personal record** versus [previousBestKg] — a new highest
     * effective weight for the exercise. Strictly greater: a tie is **not** a new PR. A
     * null candidate (no performed set) is never a PR; a null previous best means any real candidate
     * is the first PR.
     */
    fun isNewPersonalRecord(candidateBestKg: Double?, previousBestKg: Double?): Boolean =
        candidateBestKg != null && (previousBestKg == null || candidateBestKg > previousBestKg)

    /**
     * Sweeps [completedSessions] chronologically and marks, per session, which exercises achieved a
     * new **max-effective-weight PR** at that point in time: the running best effective
     * weight per exercise is tracked, and a session's exercise is a record when its best strictly
     * exceeds everything before it (ties are not PRs). [typeOf] resolves an exercise's [ExerciseType],
     * [bodyWeightForDate] the body weight to apply on a session's date (for bodyweight exercises).
     * Returns the set of `(sessionStableId, exerciseStableId)` pairs that set a record — a session
     * keeps its badge even once a later session beats it.
     */
    fun personalRecords(
        completedSessions: List<WorkoutSession>,
        typeOf: (String) -> ExerciseType,
        bodyWeightForDate: (LocalDate) -> Double?,
    ): Set<Pair<String, String>> {
        val records = mutableSetOf<Pair<String, String>>()
        val runningBest = mutableMapOf<String, Double>()
        completedSessions
            .sortedWith(compareBy<WorkoutSession> { it.date }.thenBy { it.startedAtMs })
            .forEach { session ->
                session.exercises.forEach { se ->
                    val best = bestEffectiveWeightKg(
                        se.sets,
                        typeOf(se.exerciseStableId),
                        bodyWeightForDate(session.date),
                    ) ?: return@forEach
                    if (isNewPersonalRecord(best, runningBest[se.exerciseStableId])) {
                        records += session.stableId to se.exerciseStableId
                        runningBest[se.exerciseStableId] = best
                    }
                }
            }
        return records
    }

    /**
     * The body weight to use for a session on [date]: the
     * most recent entry on or before [date]; if there is none, the earliest known entry as a
     * last-resort estimate; null only when there are no weight entries at all ("gar keins vorhanden"
     * → the caller counts only the added weight).
     */
    fun resolveBodyWeightKg(entries: List<WeightEntry>, date: LocalDate): Double? {
        if (entries.isEmpty()) return null
        val onOrBefore = entries.filter { !it.date.isAfter(date) }.maxByOrNull { it.date }
        return (onOrBefore ?: entries.minByOrNull { it.date })?.weightKg
    }

    /** Non-warm-up sets that were actually performed (reps ≥ 1) — the sets that count for records. */
    private fun performedWorkSets(sets: List<SetEntry>): List<SetEntry> =
        sets.filter { it.type != SetType.WARMUP && it.reps >= 1 }
}
