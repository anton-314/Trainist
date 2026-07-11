package dev.antonlammers.macrotrac.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.macrotrac.domain.model.ExerciseType
import dev.antonlammers.macrotrac.domain.model.SetEntry
import dev.antonlammers.macrotrac.ui.components.NumericTextField
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Training history (spec §3.6): a month calendar with a dot on training days; tapping a day shows its
 * session(s) with per-exercise volume / estimated 1RM and a PR trophy badge, and lets the user correct
 * a past unit in place (weight, reps, set-type, add/remove set) or delete it with an undo snackbar.
 * Ink & Paper style, reusing the shared set-entry building blocks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHistoryScreen(
    navController: NavController,
    viewModel: WorkoutHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verlauf") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "calendar") {
                MonthCalendar(
                    month = state.displayedMonth,
                    trainingDays = state.trainingDays,
                    selectedDate = state.selectedDate,
                    today = LocalDate.now(),
                    onPrevious = viewModel::showPreviousMonth,
                    onNext = viewModel::showNextMonth,
                    onSelect = viewModel::selectDate,
                )
            }

            state.selectedDate?.let { date ->
                item(key = "day-header") { DayHeader(date) }
            }

            if (state.selectedSessions.isEmpty()) {
                item(key = "empty") { EmptyDayHint() }
            } else {
                state.selectedSessions.forEach { session ->
                    item(key = "session-${session.id}") {
                        SessionCard(
                            session = session,
                            onSetWeight = { ei, si, w -> viewModel.setWeight(session.id, ei, si, w) },
                            onSetReps = { ei, si, r -> viewModel.setReps(session.id, ei, si, r) },
                            onSetType = { ei, si, t -> viewModel.setSetType(session.id, ei, si, t) },
                            onAddSet = { ei -> viewModel.addSet(session.id, ei) },
                            onRemoveSet = { ei, si -> viewModel.removeSet(session.id, ei, si) },
                            onDelete = {
                                viewModel.deletePending(session.id)
                                scope.launch {
                                    val result = snackbar.showSnackbar(
                                        message = "Einheit gelöscht",
                                        actionLabel = "Rückgängig",
                                        duration = SnackbarDuration.Short,
                                    )
                                    when (result) {
                                        SnackbarResult.ActionPerformed -> viewModel.undoDelete(session.id)
                                        SnackbarResult.Dismissed -> viewModel.confirmDelete(session.id)
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

// ─────────────────────────────────────────────────────────────────────────────
// Calendar
// ─────────────────────────────────────────────────────────────────────────────

private val WEEKDAY_LABELS = listOf("MO", "DI", "MI", "DO", "FR", "SA", "SO")
private val MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)
private val DAY_FORMATTER = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

@Composable
private fun MonthCalendar(
    month: YearMonth,
    trainingDays: Set<LocalDate>,
    selectedDate: LocalDate?,
    today: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Vorheriger Monat")
            }
            Text(
                month.atDay(1).format(MONTH_FORMATTER).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNext) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Nächster Monat")
            }
        }

        Row(Modifier.fillMaxWidth()) {
            WEEKDAY_LABELS.forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        weeksOf(month).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                selected = date == selectedDate,
                                isTraining = date in trainingDays,
                                isToday = date == today,
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    isTraining: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val numberColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = numberColor,
        )
        // Training-day marker: a discreet dot under the number (accent, or on-accent when selected).
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(
                    when {
                        !isTraining -> androidx.compose.ui.graphics.Color.Transparent
                        selected -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.primary
                    },
                ),
        )
    }
}

/** The days of [month] laid out in Monday-first weeks, with nulls padding the leading/trailing cells. */
private fun weeksOf(month: YearMonth): List<List<LocalDate?>> {
    val leadingBlanks = month.atDay(1).dayOfWeek.value - 1 // Mon = 1 → 0 blanks
    val cells = buildList<LocalDate?> {
        repeat(leadingBlanks) { add(null) }
        for (day in 1..month.lengthOfMonth()) add(month.atDay(day))
        while (size % 7 != 0) add(null)
    }
    return cells.chunked(7)
}

// ─────────────────────────────────────────────────────────────────────────────
// Day detail
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DayHeader(date: LocalDate) {
    Text(
        date.format(DAY_FORMATTER).replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun EmptyDayHint() {
    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        Text(
            "Kein Training an diesem Tag.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionCard(
    session: HistorySessionUi,
    onSetWeight: (Int, Int, Double) -> Unit,
    onSetReps: (Int, Int, Int) -> Unit,
    onSetType: (Int, Int, dev.antonlammers.macrotrac.domain.model.SetType) -> Unit,
    onAddSet: (Int) -> Unit,
    onRemoveSet: (Int, Int) -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Caption("EINHEIT")
                Text("Volumen ${formatKg(session.totalVolumeKg)} kg", style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Einheit löschen", tint = MaterialTheme.colorScheme.error)
            }
        }

        session.exercises.forEachIndexed { exerciseIndex, exercise ->
            ExerciseBlock(
                exercise = exercise,
                onSetWeight = { si, w -> onSetWeight(exerciseIndex, si, w) },
                onSetReps = { si, r -> onSetReps(exerciseIndex, si, r) },
                onSetType = { si, t -> onSetType(exerciseIndex, si, t) },
                onAddSet = { onAddSet(exerciseIndex) },
                onRemoveSet = { si -> onRemoveSet(exerciseIndex, si) },
            )
        }
    }
}

@Composable
private fun ExerciseBlock(
    exercise: HistoryExerciseUi,
    onSetWeight: (Int, Double) -> Unit,
    onSetReps: (Int, Int) -> Unit,
    onSetType: (Int, dev.antonlammers.macrotrac.domain.model.SetType) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
) {
    val weightCaption = if (exercise.type == ExerciseType.BODYWEIGHT) "ZUSATZ" else "KG"
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (exercise.isPersonalRecord) PrBadge()
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Caption("SATZ", Modifier.width(32.dp))
            Caption(weightCaption, Modifier.weight(1f))
            Caption("WDH", Modifier.weight(1f))
            Box(Modifier.width(40.dp))
        }

        exercise.sets.forEachIndexed { setIndex, set ->
            HistorySetRow(
                setNumber = setIndex + 1,
                set = set,
                onWeightChange = { onSetWeight(setIndex, it) },
                onRepsChange = { onSetReps(setIndex, it) },
                onSetTypeChange = { onSetType(setIndex, it) },
                onRemove = { onRemoveSet(setIndex) },
            )
        }

        TextButton(onClick = onAddSet) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Satz hinzufügen", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            MetricStat("VOLUMEN", "${formatKg(exercise.volumeKg)} kg")
            exercise.estimatedOneRepMaxKg?.let { MetricStat("E1RM", "${formatKg(it)} kg") }
        }
    }
}

@Composable
private fun HistorySetRow(
    setNumber: Int,
    set: SetEntry,
    onWeightChange: (Double) -> Unit,
    onRepsChange: (Int) -> Unit,
    onSetTypeChange: (dev.antonlammers.macrotrac.domain.model.SetType) -> Unit,
    onRemove: () -> Unit,
) {
    // Local text is the field's source of truth so intermediate values ("82.") survive; re-seeded
    // only when the stable set id changes (never mid-edit).
    var weightText by remember(set.id) { mutableStateOf(weightToText(set.weightKg)) }
    var repsText by remember(set.id) { mutableStateOf(repsToText(set.reps)) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SetTypeBadge(setNumber = setNumber, type = set.type, onTypeChange = onSetTypeChange, modifier = Modifier.width(32.dp))
        NumericTextField(
            value = weightText,
            onValueChange = { weightText = it; onWeightChange(parseWeight(it)) },
            label = null,
            decimal = true,
            modifier = Modifier.weight(1f),
        )
        NumericTextField(
            value = repsText,
            onValueChange = { repsText = it; onRepsChange(parseReps(it)) },
            label = null,
            decimal = false,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove, modifier = Modifier.width(40.dp)) {
            Icon(Icons.Rounded.Delete, contentDescription = "Satz löschen", tint = MaterialTheme.colorScheme.outline)
        }
    }
}

/** The monochrome PR marker (spec §3.5/§6): an accent trophy glyph + mono "PR" label. */
@Composable
private fun PrBadge() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            Icons.Rounded.EmojiEvents,
            contentDescription = "Persönlicher Rekord",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text("PR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MetricStat(label: String, value: String) {
    Column {
        Caption(label)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
