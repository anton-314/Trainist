package dev.antonlammers.trainist.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.ui.components.DragReorderColumn
import dev.antonlammers.trainist.ui.navigation.Screen
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * "Training" tab home — a resume hint for any running session plus the list of saved workout
 * templates. The FAB starts an empty workout; tapping a template starts a session from it; swiping a
 * template edits (StartToEnd) or deletes it (EndToStart, with undo); long-pressing the drag handle
 * reorders the list manually (persisted immediately, like every other reorderable list in the app).
 * Each card also shows when the template was last trained from, so the longest-neglected ones are
 * easy to spot. The top bar holds a "new template" and an exercise-catalog action. Ink & Paper style,
 * reusing the app's building blocks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    navController: NavController,
    viewModel: TemplatesViewModel = hiltViewModel(),
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Resolved here (not inside the snackbar-launching lambda below, which runs in a coroutine
    // scope rather than a @Composable context, so stringResource() isn't callable there).
    val templateDeletedMessage = stringResource(R.string.templates_deleted_message)
    val undoLabel = stringResource(R.string.common_undo)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.templates_title)) },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.WorkoutHistory.route) }) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = stringResource(R.string.templates_history_content_description))
                    }
                    IconButton(onClick = { navController.navigate(Screen.TemplateEditor.forTemplate(0)) }) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.templates_new_template_content_description))
                    }
                    IconButton(onClick = { navController.navigate(Screen.ExerciseCatalog.route) }) {
                        Icon(Icons.Rounded.MenuBook, contentDescription = stringResource(R.string.templates_catalog_content_description))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Screen.WorkoutSession.start(0)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                shape = RoundedCornerShape(20.dp),
                icon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                text = { Text(stringResource(R.string.templates_start_workout_button), style = MaterialTheme.typography.labelLarge) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            activeSession?.let {
                item(key = "active-banner") {
                    ActiveSessionBanner(onClick = { navController.navigate(Screen.WorkoutSession.start(0)) })
                }
            }

            if (templates.isEmpty()) {
                item(key = "empty") { EmptyTemplates() }
            } else {
                item(key = "templates") {
                    DragReorderColumn(
                        items = templates,
                        key = { it.template.id },
                        onMove = viewModel::moveTemplate,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { _, item, rowModifier, dragHandleModifier, _ ->
                        val template = item.template
                        SwipeableTemplateCard(
                            item = item,
                            rowModifier = rowModifier,
                            dragHandleModifier = dragHandleModifier,
                            onStart = { navController.navigate(Screen.WorkoutSession.start(template.id)) },
                            onEdit = { navController.navigate(Screen.TemplateEditor.forTemplate(template.id)) },
                            onDelete = {
                                viewModel.deletePending(template)
                                coroutineScope.launch {
                                    val result = snackbar.showSnackbar(
                                        message = templateDeletedMessage,
                                        actionLabel = undoLabel,
                                        duration = SnackbarDuration.Short,
                                    )
                                    when (result) {
                                        SnackbarResult.ActionPerformed -> viewModel.undoDelete(template)
                                        SnackbarResult.Dismissed -> viewModel.confirmDelete(template)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionBanner(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.templates_active_session_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                stringResource(R.string.templates_active_session_subtitle),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTemplateCard(
    item: TemplateListItem,
    rowModifier: Modifier,
    dragHandleModifier: Modifier,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
                SwipeToDismissBoxValue.StartToEnd -> { onEdit(); false }
                else -> false
            }
        }
    )
    SwipeToDismissBox(
        modifier = rowModifier,
        state = dismissState,
        backgroundContent = { SwipeBackground(dismissState.targetValue) },
    ) {
        TemplateCard(item = item, dragHandleModifier = dragHandleModifier, onClick = onStart)
    }
}

@Composable
private fun SwipeBackground(target: SwipeToDismissBoxValue) {
    val bgColor = when (target) {
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .padding(horizontal = 20.dp),
        contentAlignment = when (target) {
            SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
            else -> Alignment.CenterEnd
        },
    ) {
        when (target) {
            SwipeToDismissBoxValue.EndToStart -> Icon(
                Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.common_delete),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            SwipeToDismissBoxValue.StartToEnd -> Icon(
                Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.common_edit),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            else -> {}
        }
    }
}

/** A flat template card: a drag handle, name (serif) over a mono "last trained" line, and
 *  long-press-to-drag reordering via [dragHandleModifier]. */
@Composable
private fun TemplateCard(item: TemplateListItem, dragHandleModifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Rounded.DragIndicator,
            contentDescription = stringResource(R.string.templates_drag_handle_content_description),
            tint = MaterialTheme.colorScheme.outline,
            modifier = dragHandleModifier,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.template.name, style = MaterialTheme.typography.titleMedium)
            Text(
                item.lastUsedDate.lastUsedText(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyTemplates() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Rounded.FitnessCenter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(28.dp),
        )
        Text(stringResource(R.string.templates_empty_title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.templates_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** "Heute" / "Gestern" / "Vor N Tagen" / "Noch nie trainiert" — orientation, not a precise date. */
private fun LocalDate?.lastUsedText(): String {
    if (this == null) return "Noch nie trainiert"
    val days = ChronoUnit.DAYS.between(this, LocalDate.now())
    return when {
        days <= 0L -> "Heute"
        days == 1L -> "Gestern"
        else -> "Vor $days Tagen"
    }
}
