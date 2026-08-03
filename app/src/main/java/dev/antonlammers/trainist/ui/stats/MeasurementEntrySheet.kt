package dev.antonlammers.trainist.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.model.MeasurementType
import dev.antonlammers.trainist.ui.components.NumericTextField
import dev.antonlammers.trainist.util.normalizeDecimal

/**
 * Quick-add sheet for today's body measurements: one optional cm field per [MeasurementType],
 * prefilled from [today]. A blank field on save means "no entry for that type today" (clears any
 * existing one) — the map [onSave] receives is the complete state for the day, not a diff.
 *
 * Seven fields plus a title are taller than the partially-expanded sheet and taller still than what
 * is left over once the keyboard is up, so the save button must not simply sit at the end of the
 * content: the sheet opens fully expanded ([skipPartiallyExpanded]), only the **field list**
 * scrolls, and the button is pinned below it — always on screen, whatever the field count or the
 * IME does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MeasurementEntrySheet(
    today: Map<MeasurementType, Double>,
    onSave: (Map<MeasurementType, Double?>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var fields by remember(today) {
        mutableStateOf(MeasurementType.entries.associateWith { type -> today[type]?.let { formatCm(it) } ?: "" })
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.statusBarsPadding(),
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Text(
                stringResource(R.string.measurement_entry_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Column(
                modifier = Modifier
                    // fill = false so a short list keeps the sheet at its content height; the weight
                    // caps the list at the space left next to the pinned button.
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MeasurementType.entries.forEach { type ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            type.displayName(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        NumericTextField(
                            value = fields[type].orEmpty(),
                            onValueChange = { text -> fields = fields + (type to text) },
                            label = null,
                            suffix = "cm",
                            textStyle = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Button(
                onClick = { onSave(fields.mapValues { (_, text) -> text.normalizeDecimal().toDoubleOrNull() }) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.common_save), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
