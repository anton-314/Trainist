package dev.antonlammers.macrotrac.domain

import dev.antonlammers.macrotrac.domain.model.WorkoutSession

/** The weight/reps a single set was performed with — the orientation value shown as a placeholder. */
data class SetPerformance(val weightKg: Double, val reps: Int)

/**
 * Pure, Android-free derivation of "what did I do last time" for the inline-history hints (spec
 * §3.3). Given the session history and an exercise, it finds the most recent **completed** session
 * that contains that exercise and returns its logged sets (in order) as placeholder values.
 *
 * The active (in-progress) session is ignored — hints reflect a *previous* training, not the one
 * currently being logged.
 */
object InlineHistory {

    /**
     * The ordered set values of [exerciseStableId] from the most recent completed session that
     * contains it, or an empty list if the exercise has never been trained before.
     */
    fun lastPerformance(history: List<WorkoutSession>, exerciseStableId: String): List<SetPerformance> =
        history.asSequence()
            .filter { !it.isActive }
            .filter { session -> session.exercises.any { it.exerciseStableId == exerciseStableId } }
            .sortedWith(compareByDescending<WorkoutSession> { it.date }.thenByDescending { it.startedAtMs })
            .firstOrNull()
            ?.exercises
            ?.firstOrNull { it.exerciseStableId == exerciseStableId }
            ?.sets
            ?.sortedBy { it.position }
            ?.map { SetPerformance(it.weightKg, it.reps) }
            .orEmpty()

    /**
     * The placeholder for the set at [setIndex], i.e. the value logged at the same position last
     * time. Null when last time had fewer sets (or the exercise is new), so no hint is shown.
     */
    fun placeholderForSet(lastPerformance: List<SetPerformance>, setIndex: Int): SetPerformance? =
        lastPerformance.getOrNull(setIndex)
}
