package dev.antonlammers.trainist.domain

import dev.antonlammers.trainist.domain.model.SupersetMember

/**
 * Where one member sits in its superset, everything a badge needs: [ordinal] identifies the group
 * (0-based, rendered as A / B / C by the UI), [member] is the 1-based place within it and [size] the
 * group's size — "A · 2/3". Derived state, never stored.
 */
data class SupersetPlacement(val ordinal: Int, val member: Int, val size: Int)

/**
 * Superset grouping over an ordered list of [SupersetMember]s — the planned slots of a template or
 * the performed exercises of a session, which follow exactly the same rules.
 *
 * A superset is a **maximal run of adjacent members sharing the same non-null**
 * [SupersetMember.supersetGroupId] — adjacency is part of the definition, not an accident of the
 * order. That single rule is what keeps the feature honest: an exercise can only be supersetted with
 * the one it is actually performed next to, so a removal, an insertion or a reorder can never leave
 * behind a "group" whose members are pages apart in the list.
 *
 * Every operation therefore ends in [normalize], which is the only place group ids are ever written.
 * It re-derives the ids from adjacency alone: runs of two or more become groups numbered 1..n in list
 * order, everything else is cleared. Two consequences worth relying on — a group can never have fewer
 * than two members (a solo superset is a contradiction), and the ids are canonical, so
 * [groupOrdinals] is a plain `id - 1` and the display letter never jumps when an unrelated group is
 * dissolved.
 *
 * Ids are only meaningful *within one list*; nothing outside references them.
 */
object Supersets {

    /**
     * Re-derives all group ids from adjacency. Runs of ≥ 2 adjacent members that currently share an
     * id are renumbered 1..n in list order; every other id is cleared.
     *
     * Call this after any structural change to the list (add, remove, reorder) — it is what turns a
     * group whose partner has just been deleted back into a plain exercise.
     */
    fun <T : SupersetMember<T>> normalize(items: List<T>): List<T> {
        val result = items.toMutableList()
        var nextGroupId = 1
        var index = 0
        while (index < result.size) {
            val id = result[index].supersetGroupId
            val runEnd = if (id == null) index else runEndOf(result, index, id)
            val runLength = runEnd - index + 1
            if (id != null && runLength >= 2) {
                val groupId = nextGroupId++
                for (i in index..runEnd) result[i] = result[i].withSupersetGroupId(groupId)
                index = runEnd + 1
            } else {
                if (id != null) result[index] = result[index].withSupersetGroupId(null)
                index++
            }
        }
        return result
    }

    /**
     * Joins the member at [index] with the one after it into one superset, merging whatever groups
     * either of them already belongs to. Returns the list unchanged when there is no next member.
     */
    fun <T : SupersetMember<T>> groupWithNext(items: List<T>, index: Int): List<T> {
        if (!canGroupWithNext(items, index)) return items
        // Span both members' existing runs so joining two groups merges them whole, rather than
        // tearing one member out of each.
        val from = runStart(items, index)
        val to = runEnd(items, index + 1)
        val merged = items.mapIndexed { i, item ->
            if (i in from..to) item.withSupersetGroupId(MERGE_MARKER) else item
        }
        return normalize(merged)
    }

    /**
     * Removes the member at [index] from its superset. The remainder stays a superset if at least two
     * members are left; otherwise [normalize] dissolves it too.
     */
    fun <T : SupersetMember<T>> ungroup(items: List<T>, index: Int): List<T> {
        if (index !in items.indices || items[index].supersetGroupId == null) return items
        val cleared = items.mapIndexed { i, item ->
            if (i == index) item.withSupersetGroupId(null) else item
        }
        return normalize(cleared)
    }

    /** True when there is a following member to superset the one at [index] with. */
    fun <T : SupersetMember<T>> canGroupWithNext(items: List<T>, index: Int): Boolean =
        index in items.indices && index + 1 in items.indices

    /**
     * True when the member at [index] is the last of its superset — or is not supersetted at all.
     * This is the "may the rest timer start now?" question: inside a superset the point is to go
     * straight to the next exercise, so only the final member ends a round.
     */
    fun <T : SupersetMember<T>> isLastInGroup(items: List<T>, index: Int): Boolean {
        if (index !in items.indices) return true
        val id = items[index].supersetGroupId ?: return true
        return items.getOrNull(index + 1)?.supersetGroupId != id
    }

    /**
     * Position of the member at [index] within its superset as `member to size`, or null when it is
     * not supersetted — the "2/3" part of a group badge.
     */
    fun <T : SupersetMember<T>> memberPosition(items: List<T>, index: Int): Pair<Int, Int>? {
        if (index !in items.indices) return null
        items[index].supersetGroupId ?: return null
        val from = runStart(items, index)
        val to = runEnd(items, index)
        return (index - from + 1) to (to - from + 1)
    }

    /**
     * List index → [SupersetPlacement] for every supersetted member; indices that stand alone are
     * simply absent. This is the one call a screen needs to render superset badges, which is why the
     * three derived numbers travel together instead of as three parallel nullable fields.
     */
    fun <T : SupersetMember<T>> placements(items: List<T>): Map<Int, SupersetPlacement> {
        val ordinals = groupOrdinals(items)
        return items.indices.mapNotNull { index ->
            val groupId = items[index].supersetGroupId ?: return@mapNotNull null
            val ordinal = ordinals[groupId] ?: return@mapNotNull null
            val (member, size) = memberPosition(items, index) ?: return@mapNotNull null
            index to SupersetPlacement(ordinal, member, size)
        }.toMap()
    }

    /**
     * Group id → 0-based ordinal in list order, for labelling groups A, B, C … The UI turns the
     * ordinal into a letter; the domain deliberately produces no display text.
     */
    fun <T : SupersetMember<T>> groupOrdinals(items: List<T>): Map<Int, Int> =
        items.mapNotNull { it.supersetGroupId }
            .distinct()
            .sorted()
            .withIndex()
            .associate { (ordinal, id) -> id to ordinal }

    // --- run boundaries (a run = adjacent members sharing one non-null id) ---

    private fun <T : SupersetMember<T>> runStart(items: List<T>, index: Int): Int {
        val id = items[index].supersetGroupId ?: return index
        var start = index
        while (start - 1 >= 0 && items[start - 1].supersetGroupId == id) start--
        return start
    }

    private fun <T : SupersetMember<T>> runEnd(items: List<T>, index: Int): Int {
        val id = items[index].supersetGroupId ?: return index
        return runEndOf(items, index, id)
    }

    private fun <T : SupersetMember<T>> runEndOf(items: List<T>, index: Int, id: Int): Int {
        var end = index
        while (end + 1 < items.size && items[end + 1].supersetGroupId == id) end++
        return end
    }

    /**
     * Placeholder id stamped on the members being merged, before [normalize] hands out the real one.
     * Any value works — normalize only reads adjacency — but it must not collide with an id already in
     * the list, so it sits outside the 1..n range normalize produces.
     */
    private const val MERGE_MARKER = -1
}
