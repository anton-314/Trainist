package dev.antonlammers.trainist.domain

import dev.antonlammers.trainist.domain.model.SessionExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupersetsTest {

    /** Builds an exercise list from group ids, e.g. `list(null, 1, 1, null)`. */
    private fun list(vararg groupIds: Int?) = groupIds.mapIndexed { index, groupId ->
        SessionExercise(
            id = index + 1L,
            exerciseStableId = "ex$index",
            position = index,
            supersetGroupId = groupId,
        )
    }

    private fun List<SessionExercise>.ids() = map { it.supersetGroupId }

    // --- normalize ---

    @Test
    fun `normalize keeps an adjacent pair and numbers it 1`() {
        assertEquals(listOf(null, 1, 1, null), Supersets.normalize(list(null, 4, 4, null)).ids())
    }

    @Test
    fun `normalize dissolves a group left with a single member`() {
        // What a deletion leaves behind: the partner is gone, so this is no longer a superset.
        assertEquals(listOf(null, null), Supersets.normalize(list(2, null)).ids())
    }

    @Test
    fun `normalize dissolves members that are no longer adjacent`() {
        // A reorder pushed an unrelated exercise between the two members.
        assertEquals(listOf(null, null, null), Supersets.normalize(list(1, null, 1)).ids())
    }

    @Test
    fun `normalize splits one id used by two separate runs into two groups`() {
        assertEquals(
            listOf(1, 1, null, 2, 2),
            Supersets.normalize(list(9, 9, null, 9, 9)).ids(),
        )
    }

    @Test
    fun `normalize numbers groups in list order`() {
        assertEquals(
            listOf(1, 1, 2, 2, 3, 3),
            Supersets.normalize(list(7, 7, 3, 3, 5, 5)).ids(),
        )
    }

    @Test
    fun `normalize keeps a run of three together`() {
        assertEquals(listOf(1, 1, 1), Supersets.normalize(list(2, 2, 2)).ids())
    }

    @Test
    fun `normalize leaves an ungrouped list untouched`() {
        assertEquals(listOf(null, null), Supersets.normalize(list(null, null)).ids())
    }

    @Test
    fun `normalize handles an empty list`() {
        assertEquals(emptyList<Int?>(), Supersets.normalize(emptyList<SessionExercise>()).ids())
    }

    // --- groupWithNext ---

    @Test
    fun `groupWithNext joins two ungrouped neighbours`() {
        assertEquals(listOf(null, 1, 1), Supersets.groupWithNext(list(null, null, null), 1).ids())
    }

    @Test
    fun `groupWithNext extends an existing group instead of splitting it`() {
        // Grouping the second member of [A B] with C must produce one group of three, not two pairs.
        assertEquals(listOf(1, 1, 1), Supersets.groupWithNext(list(1, 1, null), 1).ids())
    }

    @Test
    fun `groupWithNext merges two whole groups`() {
        assertEquals(
            listOf(1, 1, 1, 1),
            Supersets.groupWithNext(list(1, 1, 2, 2), 1).ids(),
        )
    }

    @Test
    fun `groupWithNext on the last exercise does nothing`() {
        val original = list(null, null)
        assertEquals(original.ids(), Supersets.groupWithNext(original, 1).ids())
    }

    @Test
    fun `groupWithNext on an out-of-range index does nothing`() {
        val original = list(null, null)
        assertEquals(original.ids(), Supersets.groupWithNext(original, 5).ids())
    }

    @Test
    fun `groupWithNext preserves the exercises themselves`() {
        val grouped = Supersets.groupWithNext(list(null, null), 0)
        assertEquals(listOf("ex0", "ex1"), grouped.map { it.exerciseStableId })
        assertEquals(listOf(1L, 2L), grouped.map { it.id })
    }

    // --- ungroup ---

    @Test
    fun `ungroup dissolves a pair entirely`() {
        // Removing one of two members leaves one behind, which is not a superset either.
        assertEquals(listOf(null, null), Supersets.ungroup(list(1, 1), 0).ids())
    }

    @Test
    fun `ungroup keeps the remaining two members of a triple together`() {
        assertEquals(listOf(null, 1, 1), Supersets.ungroup(list(1, 1, 1), 0).ids())
    }

    @Test
    fun `ungroup from the middle of a triple splits the run`() {
        // The two survivors are no longer adjacent, so nothing stays grouped.
        assertEquals(listOf(null, null, null), Supersets.ungroup(list(1, 1, 1), 1).ids())
    }

    @Test
    fun `ungroup on an exercise without a group does nothing`() {
        val original = list(null, 1, 1)
        assertEquals(original.ids(), Supersets.ungroup(original, 0).ids())
    }

    // --- isLastInGroup (the rest-timer question) ---

    @Test
    fun `an ungrouped exercise is always last`() {
        assertTrue(Supersets.isLastInGroup(list(null, null), 0))
    }

    @Test
    fun `a non-final member of a superset is not last`() {
        assertFalse(Supersets.isLastInGroup(list(1, 1), 0))
    }

    @Test
    fun `the final member of a superset is last`() {
        assertTrue(Supersets.isLastInGroup(list(1, 1), 1))
    }

    @Test
    fun `the final member of a superset is last even when another group follows`() {
        assertTrue(Supersets.isLastInGroup(list(1, 1, 2, 2), 1))
    }

    @Test
    fun `an out-of-range index counts as last`() {
        assertTrue(Supersets.isLastInGroup(list(1, 1), 9))
    }

    // --- labelling ---

    @Test
    fun `memberPosition numbers members within their own group`() {
        val exercises = list(null, 1, 1, 1)
        assertNull(Supersets.memberPosition(exercises, 0))
        assertEquals(1 to 3, Supersets.memberPosition(exercises, 1))
        assertEquals(2 to 3, Supersets.memberPosition(exercises, 2))
        assertEquals(3 to 3, Supersets.memberPosition(exercises, 3))
    }

    @Test
    fun `memberPosition restarts for the second group`() {
        assertEquals(1 to 2, Supersets.memberPosition(list(1, 1, 2, 2), 2))
    }

    @Test
    fun `groupOrdinals maps group ids to consecutive display ordinals`() {
        assertEquals(mapOf(1 to 0, 2 to 1), Supersets.groupOrdinals(list(1, 1, 2, 2)))
    }

    @Test
    fun `groupOrdinals is empty without supersets`() {
        assertEquals(emptyMap<Int, Int>(), Supersets.groupOrdinals(list(null, null)))
    }
}
