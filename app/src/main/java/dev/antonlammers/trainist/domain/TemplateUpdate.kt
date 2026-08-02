package dev.antonlammers.trainist.domain

import dev.antonlammers.trainist.domain.model.SetType
import dev.antonlammers.trainist.domain.model.TemplateExercise
import dev.antonlammers.trainist.domain.model.WorkoutSession
import dev.antonlammers.trainist.domain.model.WorkoutTemplate

/**
 * Pure, Android-free reconciliation of a finished [WorkoutSession] back into the [WorkoutTemplate] it
 * was started from (spec addendum — "update the template with what I changed"). Offered to the user
 * on finish; never applied automatically.
 *
 * Rules (chosen deliberately, see the workout module docs):
 * - An exercise is **performed** when it has at least one checked-off (completed) set. Its new plan is
 *   the [dev.antonlammers.trainist.domain.model.SetType]s of those **completed** sets, in order — so
 *   an extra set that was added but never checked off does not leak into the template, and a set whose
 *   type was changed carries the change over.
 * - A template exercise the session did **not** perform (skipped, or removed during the session) is
 *   kept **unchanged** — you often don't train everything, and that must not silently shrink the plan.
 * - An exercise performed but **not** in the template (added live) is **appended** to the template.
 * - A performed exercise also carries its **superset grouping** as trained back into the plan, for
 *   the same reason its set types are carried back: what was actually done is the better plan. A
 *   skipped slot keeps whatever grouping it had, and [Supersets.normalize] then re-derives the whole
 *   template's groups from adjacency, so a half-updated plan can never end up with a group of one.
 *
 * Returns the merged template, or `null` when there is nothing to offer (no linked template, or the
 * merge would reproduce the template exactly).
 */
object TemplateUpdate {

    /** One performed exercise reduced to what the template should learn from it. */
    private data class Performed(
        val exerciseStableId: String,
        val setTypes: List<SetType>,
        val supersetGroupId: Int?,
    )

    fun merge(template: WorkoutTemplate, session: WorkoutSession): WorkoutTemplate? {
        val original = Supersets.normalize(
            template.exercises
                .sortedBy { it.position }
                .mapIndexed { index, slot -> slot.copy(position = index) },
        )

        // Session group ids and template group ids are both small integers counting from 1, so they
        // would collide when a skipped slot's group meets a performed slot's group. Shifting the
        // session's ids past the template's keeps them distinct until normalize renumbers everything.
        val groupIdOffset = original.mapNotNull { it.supersetGroupId }.maxOrNull() ?: 0

        // Performed exercises (in session order), each reduced to the types of its completed sets.
        val performed = session.exercises
            .sortedBy { it.position }
            .mapNotNull { se ->
                val completedTypes = se.sets
                    .filter { it.completed }
                    .sortedBy { it.position }
                    .map { it.type }
                if (completedTypes.isEmpty()) {
                    null
                } else {
                    Performed(se.exerciseStableId, completedTypes, se.supersetGroupId?.plus(groupIdOffset))
                }
            }
            .toMutableList()

        // Update each template slot the session performed (greedy match by stableId, so a duplicated
        // exercise consumes performed entries in order); keep the rest as-is.
        val updated = original.map { slot ->
            val matchIndex = performed.indexOfFirst { it.exerciseStableId == slot.exerciseStableId }
            if (matchIndex < 0) {
                slot
            } else {
                val match = performed.removeAt(matchIndex)
                slot.copy(setTypes = match.setTypes, supersetGroupId = match.supersetGroupId)
            }
        }

        // Whatever performed exercises are left over were added live → append them.
        val appended = performed.map {
            TemplateExercise(it.exerciseStableId, 0, it.setTypes, it.supersetGroupId)
        }

        val merged = Supersets.normalize(updated + appended)
            .mapIndexed { index, slot -> slot.copy(position = index) }
        return if (merged == original) null else template.copy(exercises = merged)
    }
}
