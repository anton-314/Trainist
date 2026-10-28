package dev.antonlammers.trainist.ui.components

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Numeric input field used app-wide.
 *
 * Design language: every field that holds a number selects its whole content when it gains
 * focus, so a pre-filled value (e.g. the last used amount) can be overwritten immediately by
 * typing — without deleting the old value first. Always use this composable for numeric input
 * instead of a bare [OutlinedTextField] so this behaviour stays consistent everywhere.
 *
 * The public API is intentionally [String]-based: callers own the raw text and parse it
 * themselves (the app accepts both comma and period as decimal separators). The internal
 * [TextFieldValue] — which carries the selection needed for select-all-on-focus — never leaks out.
 *
 * @param decimal `true` for a decimal keyboard (grams, kg), `false` for a whole-number keyboard.
 * @param label floating Material label, or `null` when the caller supplies its own caption above.
 * @param textStyle style for the value text (e.g. a serif style for the Goals editor).
 * @param leadingIcon optional leading slot, e.g. a small colored accent tick.
 */
@Composable
fun NumericTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    decimal: Boolean = true,
    suffix: String? = null,
    supportingText: String? = null,
    placeholder: String? = null,
    textStyle: TextStyle = LocalTextStyle.current,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    var fieldValue by rememberNumericFieldValue(value)

    OutlinedTextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            if (it.text != value) onValueChange(it.text)
        },
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        textStyle = textStyle,
        leadingIcon = leadingIcon,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        singleLine = true,
        suffix = suffix?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        modifier = modifier.onFocusChanged { focusState ->
            if (focusState.isFocused) fieldValue = fieldValue.selectAll()
        },
    )
}

/**
 * [NumericTextField]'s behaviour without the Material outline: the same select-all-on-focus and
 * String API, for a number that sits inside a custom-drawn surface (the macro tiles of the custom-food
 * sheet, the compact amount box of a meal ingredient). The caller draws the frame around the value
 * via [decorationBox] and can follow the focus through [onFocusChange] (e.g. to tint a border).
 */
@Composable
fun BareNumericTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = true,
    textStyle: TextStyle = LocalTextStyle.current,
    onFocusChange: (Boolean) -> Unit = {},
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit = { it() },
) {
    var fieldValue by rememberNumericFieldValue(value)
    BasicTextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            if (it.text != value) onValueChange(it.text)
        },
        // Unlike a Material field, BasicTextField paints an unspecified colour black.
        textStyle = if (textStyle.color.isSpecified) textStyle else textStyle.copy(color = LocalContentColor.current),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = decorationBox,
        modifier = modifier.onFocusChanged { focusState ->
            if (focusState.isFocused) fieldValue = fieldValue.selectAll()
            onFocusChange(focusState.isFocused)
        },
    )
}

/**
 * The field's internal [TextFieldValue] — which carries the selection needed for select-all-on-focus
 * — kept in sync with the caller's [value]. External changes (e.g. a "calculate" button) are mirrored
 * in; while the text is unchanged that is skipped, so a selection set on focus is preserved.
 */
@Composable
private fun rememberNumericFieldValue(value: String): MutableState<TextFieldValue> {
    val state = remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (state.value.text != value) {
        state.value = state.value.copy(text = value, selection = TextRange(value.length))
    }
    return state
}

private fun TextFieldValue.selectAll(): TextFieldValue =
    if (text.isEmpty()) this else copy(selection = TextRange(0, text.length))
