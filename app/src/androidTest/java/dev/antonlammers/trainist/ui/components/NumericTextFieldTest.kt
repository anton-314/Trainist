package dev.antonlammers.trainist.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Covers `NumericTextField`'s select-all-on-focus rule, which every numeric field in the app relies
 * on and which no JVM test can reach: the behaviour exists only as a selection range inside a real
 * composition. It is asserted the way a user experiences it — tap a pre-filled field, type one
 * character, and the old value is gone rather than appended to.
 */
class NumericTextFieldTest {

    @get:Rule
    val compose = createComposeRule()

    private val field get() = compose.onNode(hasSetTextAction())

    /** Hosts the field with the same string-in/string-out contract its callers use. */
    private fun setContent(initial: String): () -> String {
        var text = initial
        compose.setContent {
            var state by remember { mutableStateOf(initial) }
            NumericTextField(
                value = state,
                onValueChange = { state = it; text = it },
                label = null,
            )
        }
        return { text }
    }

    @Test
    fun typingIntoAPreFilledFieldReplacesTheWholeValue() {
        val current = setContent(initial = "82.5")

        field.performClick()
        field.performTextInput("9")

        // The point of the whole component: focus selected the pre-filled value, so typing
        // overwrites it instead of producing "82.59" or "982.5".
        assertEquals("9", current())
    }

    @Test
    fun anExternalValueChangeIsMirroredIntoTheField() {
        // The "calculate" buttons (BMR wizard, goals editor) push a value in from outside.
        var external by mutableStateOf("100")
        compose.setContent {
            NumericTextField(value = external, onValueChange = { external = it }, label = null)
        }
        compose.onNodeWithText("100").assertExists()

        external = "2350"

        compose.onNodeWithText("2350").assertExists()
    }

    @Test
    fun anIntermediateDecimalReachesTheCallerUnchanged() {
        // "82." is not a number yet; the field must not swallow or normalize it mid-typing.
        val current = setContent(initial = "")

        field.performTextReplacement("82.")

        assertEquals("82.", current())
    }

    @Test
    fun aCommaDecimalReachesTheCallerVerbatim() {
        // Parsing comma vs period is the caller's job (normalizeDecimal); the field stays neutral.
        val current = setContent(initial = "")

        field.performTextReplacement("1,5")

        assertEquals("1,5", current())
    }

    @Test
    fun anEmptyFieldIsTypedIntoNormally() {
        val current = setContent(initial = "")

        field.performClick()
        field.performTextInput("12")

        assertEquals("12", current())
    }
}
