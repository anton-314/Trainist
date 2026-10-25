package dev.antonlammers.trainist.ui.foodimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.ui.components.TagDot
import dev.antonlammers.trainist.ui.components.displayName
import dev.antonlammers.trainist.ui.util.currentAppLocale
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The surface of a "Öffnen mit Trainist" food import: the preview sheet while one is showing, an
 * error dialog when the file can't be imported, nothing otherwise.
 */
@Composable
fun FoodImportHost(
    phase: FoodImportPhase,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (phase) {
        is FoodImportPhase.Preview -> FoodImportSheet(phase, onConfirm = onConfirm, onDismiss = onDismiss)
        is FoodImportPhase.Failed -> FoodImportErrorDialog(phase.error, onDismiss = onDismiss)
        FoodImportPhase.Idle, FoodImportPhase.Loading -> Unit
    }
}

/**
 * Lists every entry the file will add, so nothing reaches the diary unseen — the values may come
 * from an AI's estimate, and the same download opened twice would otherwise double a meal.
 *
 * A file can hold any number of rows, so this follows the tall-sheet shape: fully expanded, only
 * the entry list scrolls, the import button is pinned below it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FoodImportSheet(
    preview: FoodImportPhase.Preview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val totalKcal = preview.entries.sumOf { it.kcal }.toInt()

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
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.foodimport_sheet_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    pluralStringResource(R.plurals.foodimport_summary, preview.entries.size, preview.entries.size, totalKcal),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (preview.skipped > 0) {
                    Text(
                        pluralStringResource(R.plurals.foodimport_skipped, preview.skipped, preview.skipped),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Column(
                modifier = Modifier
                    // fill = false so a single entry keeps the sheet at its content height.
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
            ) {
                preview.entries.forEachIndexed { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    FoodImportRow(entry)
                }
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.foodimport_confirm), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun FoodImportRow(entry: FoodEntry) {
    val date = entry.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentAppLocale()))
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Fixed-width leading slot keeps titles aligned whether or not a tag dot is shown.
            Box(Modifier.size(7.dp)) { TagDot(entry.tag, size = 7.dp) }
            Text(
                buildString {
                    append(entry.foodName)
                    entry.brand?.let { append(" ($it)") }
                    append(" · ${entry.amountGrams.toInt()} g")
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            stringResource(
                R.string.addfood_food_detail_total,
                entry.kcal.toInt(),
                entry.proteinG.toInt(),
                entry.carbsG.toInt(),
                entry.fatG.toInt(),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 15.dp, top = 4.dp),
        )
        Text(
            "${entry.mealCategory.displayName()} · $date",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 15.dp, top = 2.dp),
        )
    }
}

@Composable
private fun FoodImportErrorDialog(error: FoodImportError, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.foodimport_error_title)) },
        text = { Text(stringResource(error.messageRes())) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) } },
    )
}

private fun FoodImportError.messageRes(): Int = when (this) {
    FoodImportError.UNREADABLE -> R.string.foodimport_error_unreadable
    FoodImportError.NOT_FOOD_ENTRIES -> R.string.foodimport_error_not_food_entries
    FoodImportError.NO_VALID_ROWS -> R.string.foodimport_error_no_valid_rows
    FoodImportError.SAVE_FAILED -> R.string.foodimport_error_save_failed
}
