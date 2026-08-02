package dev.antonlammers.trainist.ui.workout

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import dev.antonlammers.trainist.domain.model.SetEntry
import dev.antonlammers.trainist.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Covers the live session's set row, whose text state is the one genuinely subtle piece of UI in the
 * workout module: each field's text is local (`remember(set.id, set.completed)`), so that a
 * half-typed value survives recomposition **and** the values adopted from the inline-history
 * placeholder on check-off appear as real text rather than as the grey hint.
 *
 * Both rules only exist inside a real composition — a ViewModel test cannot see either.
 */
class WorkoutSetRowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun emptySet(id: Long = 1L) =
        SetEntry(id = id, position = 0, weightKg = 0.0, reps = 0, type = SetType.NORMAL)

    @Test
    fun anEmptySetShowsLastSessionsValuesAsPlaceholders() {
        compose.setContent {
            SetRow(
                setNumber = 1,
                set = emptySet(),
                weightPlaceholder = "80",
                repsPlaceholder = "8",
                onWeightChange = {},
                onRepsChange = {},
                onToggleCompleted = {},
                onSetTypeChange = {},
                onDelete = {},
            )
        }

        compose.onNodeWithText("80").assertExists()
        compose.onNodeWithText("8").assertExists()
    }

    @Test
    fun checkingOffAdoptsThePlaceholderAsRealText() {
        // What the VM does on check-off: the empty set comes back completed and carrying the
        // history values. The row must re-seed its text from it, or the user still sees grey hints
        // over a set that is already logged.
        val set = mutableStateOf(emptySet())
        compose.setContent {
            SetRow(
                setNumber = 1,
                set = set.value,
                weightPlaceholder = "80",
                repsPlaceholder = "8",
                onWeightChange = {},
                onRepsChange = {},
                onToggleCompleted = {},
                onSetTypeChange = {},
                onDelete = {},
            )
        }

        set.value = set.value.copy(weightKg = 80.0, reps = 8, completed = true)

        compose.onNodeWithText("80").assertExists()
        compose.onNodeWithText("8").assertExists()
    }

    @Test
    fun aHalfTypedDecimalSurvivesAnUnrelatedRecomposition() {
        // "82." is not yet a number; re-seeding the field here would delete the trailing separator
        // out from under the user mid-typing.
        val setNumber = mutableStateOf(1)
        var lastWeight = 0.0
        compose.setContent {
            SetRow(
                setNumber = setNumber.value,
                set = emptySet(),
                weightPlaceholder = null,
                repsPlaceholder = null,
                onWeightChange = { lastWeight = it },
                onRepsChange = {},
                onToggleCompleted = {},
                onSetTypeChange = {},
                onDelete = {},
            )
        }

        compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("82.")
        setNumber.value = 2

        compose.onNodeWithText("82.").assertExists()
        assertEquals(82.0, lastWeight, 0.001)
    }

    @Test
    fun editingTheWeightReportsTheParsedValue() {
        var lastWeight = 0.0
        compose.setContent {
            SetRow(
                setNumber = 1,
                set = emptySet(),
                weightPlaceholder = null,
                repsPlaceholder = null,
                onWeightChange = { lastWeight = it },
                onRepsChange = {},
                onToggleCompleted = {},
                onSetTypeChange = {},
                onDelete = {},
            )
        }

        compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("77,5")

        // Comma is accepted as a decimal separator app-wide (normalizeDecimal).
        assertEquals(77.5, lastWeight, 0.001)
    }
}
