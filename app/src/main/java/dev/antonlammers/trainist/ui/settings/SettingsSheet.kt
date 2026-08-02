package dev.antonlammers.trainist.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The one bottom-sheet shell behind every settings surface — language picker, backup, support.
 *
 * A handful of buttons does not warrant a navigation entry: the sheet keeps the hub (and its scroll
 * position) visible behind it and costs one tap to dismiss, where a pushed screen costs a back
 * navigation. Only a real editing form still gets a screen of its own — see `GoalsScreen`.
 *
 * Content supplies its own horizontal inset ([SHEET_CONTENT_PADDING]) so rows can span the full
 * width while buttons stay inset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.statusBarsPadding(),
        sheetState = rememberModalBottomSheetState(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                // The country picker types into a search field; without this the keyboard covers it.
                .imePadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = SHEET_CONTENT_PADDING, vertical = 12.dp),
            )
            content()
        }
    }
}

/** Horizontal inset shared by every [SettingsSheet]'s content, so all sheets line up. */
val SHEET_CONTENT_PADDING = 20.dp
