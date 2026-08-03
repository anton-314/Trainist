package dev.antonlammers.trainist.ui.stats

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.model.MeasurementType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Covers the one thing about the body-measurement sheet that only exists inside a real composition:
 * its save button has to be **on screen**. Seven fields plus a title outgrow the partially-expanded
 * sheet, and the shipped 1.0.188 sheet put the button at the end of non-scrollable content, where it
 * ended up below the fold with nothing to scroll — the entry flow simply had no way to finish.
 *
 * The sheet renders in its own window, so this is only reachable instrumented; a ViewModel test sees
 * the save callback fire either way.
 */
class MeasurementEntrySheetTest {

    @get:Rule
    val compose = createComposeRule()

    private fun string(id: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    /** The sheet's own label mapping, mirrored here so the test reads in whatever locale it runs. */
    private fun MeasurementType.label() = string(
        when (this) {
            MeasurementType.NECK -> R.string.measurement_type_neck
            MeasurementType.CHEST -> R.string.measurement_type_chest
            MeasurementType.WAIST -> R.string.measurement_type_waist
            MeasurementType.HIPS -> R.string.measurement_type_hips
            MeasurementType.BICEPS -> R.string.measurement_type_biceps
            MeasurementType.THIGH -> R.string.measurement_type_thigh
            MeasurementType.CALF -> R.string.measurement_type_calf
        },
    )

    private val saveButton get() = compose.onNodeWithText(string(R.string.common_save))

    /** Fields render one per type in enum order, so position identifies them without a test tag. */
    private fun field(type: MeasurementType) =
        compose.onAllNodes(hasSetTextAction())[MeasurementType.entries.indexOf(type)]

    /** Hosts the sheet the way the Körpermaße card does, capturing what save reports. */
    private fun setContent(today: Map<MeasurementType, Double> = emptyMap()): () -> Map<MeasurementType, Double?>? {
        var saved: Map<MeasurementType, Double?>? = null
        compose.setContent {
            MeasurementEntrySheet(today = today, onSave = { saved = it }, onDismiss = {})
        }
        return { saved }
    }

    @Test
    fun theSaveButtonIsOnScreenWithoutScrolling() {
        setContent()

        // The regression, stated as the user hits it: the sheet is open, the fields are there, and
        // the button must be visible where it stands — not somewhere past the sheet's bottom edge.
        saveButton.assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun everyMeasurementTypeIsReachableWithTheButtonStillOnScreen() {
        setContent()

        // The field list scrolls, the button does not: the last type has to be scrollable into view
        // on any screen — including the ones where all seven fields don't fit at once.
        MeasurementType.entries.forEach { type ->
            compose.onNodeWithText(type.label()).performScrollTo().assertIsDisplayed()
        }
        saveButton.assertIsDisplayed()
    }

    @Test
    fun savingReportsATypedValueAndNullsABlankedOne() {
        // Waist prefilled from today's entry and then cleared, calf typed fresh. A blank field means
        // "no measurement today" — it has to reach the caller as null rather than be dropped.
        val saved = setContent(today = mapOf(MeasurementType.WAIST to 82.5))

        field(MeasurementType.WAIST).performScrollTo().performTextReplacement("")
        field(MeasurementType.CALF).performScrollTo().performTextReplacement("38,5")
        saveButton.performClick()

        val result = requireNotNull(saved())
        assertEquals(null, result[MeasurementType.WAIST])
        assertEquals(38.5, requireNotNull(result[MeasurementType.CALF]), 0.001)
    }
}
