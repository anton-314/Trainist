package dev.antonlammers.macrotrac.ui.overview

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.EaseOutExpo
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.macrotrac.domain.model.FoodEntry
import dev.antonlammers.macrotrac.domain.model.FoodTag
import dev.antonlammers.macrotrac.domain.model.MealCategory
import dev.antonlammers.macrotrac.domain.model.WeightEntry
import dev.antonlammers.macrotrac.ui.components.NumericTextField
import dev.antonlammers.macrotrac.ui.components.TagSelector
import dev.antonlammers.macrotrac.ui.components.color
import dev.antonlammers.macrotrac.ui.components.displayName
import dev.antonlammers.macrotrac.ui.navigation.Screen
import dev.antonlammers.macrotrac.ui.theme.CarbsColor
import dev.antonlammers.macrotrac.ui.theme.FatColor
import dev.antonlammers.macrotrac.ui.theme.ProteinColor
import dev.antonlammers.macrotrac.util.normalizeDecimal
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Entrance/replay motion. A single master "clock" (seconds) is restarted from 0
// whenever the screen opens, the day changes, or the summary card is tapped
// (see `replayTrigger`). Every animated element derives its own eased progress
// from a fixed window on this clock, matching the timelines in the design handoff
// (Calorie Ring Motion.dc.html / Macro Bars Motion.dc.html).
// ─────────────────────────────────────────────────────────────────────────────
private const val AnimTotalSeconds = 3.2f

/** Eased 0..1 progress of the timeline window [startS, endS] at the given clock time. */
private fun windowProgress(timeS: Float, startS: Float, endS: Float, easing: Easing): Float {
    if (endS <= startS) return if (timeS >= endS) 1f else 0f
    val raw = ((timeS - startS) / (endS - startS)).coerceIn(0f, 1f)
    return easing.transform(raw)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    navController: NavController,
    viewModel: OverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // UI-local replay trigger — bumped on a tap of the summary card. Day changes are
    // already captured via `state.date`. Both key the animation clock in MacroSummaryCard.
    var replayTrigger by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IconButton(onClick = viewModel::previousDay) {
                            Icon(Icons.Rounded.KeyboardArrowLeft, contentDescription = "Vorheriger Tag")
                        }
                        Text(
                            state.date.format(DateTimeFormatter.ofPattern("EEE, d. MMM", Locale("de"))),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        IconButton(onClick = viewModel::nextDay) {
                            Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = "Nächster Tag")
                        }
                    }
                },
                actions = {
                    if (state.date != LocalDate.now()) {
                        TextButton(onClick = viewModel::goToToday) { Text("Heute") }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddFood.withDate(state.date)) },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Mahlzeit hinzufügen")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                MacroSummaryCard(
                    state = state,
                    replayTrigger = replayTrigger,
                    onReplay = { replayTrigger++ },
                )
            }
            item { WeightCard(weight = state.todayWeight, onSave = viewModel::saveWeight) }
            if (state.entries.isEmpty() && state.copyableMeals.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Noch keine Einträge heute",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                MealCategory.entries.forEach { category ->
                    val categoryEntries = state.entriesForMeal(category)
                    if (categoryEntries.isEmpty()) {
                        if (category in state.copyableMeals) {
                            item(key = "copy_${category.name}") {
                                CopyMealButton(
                                    label = "${category.displayName()} von Gestern kopieren",
                                    onClick = { viewModel.copyMealFromPreviousDay(category) },
                                )
                            }
                        }
                        return@forEach
                    }
                    item(key = "header_${category.name}") {
                        MealSectionHeader(
                            name = category.displayName(),
                            kcal = state.kcalForMeal(category).toInt(),
                        )
                    }
                    items(categoryEntries, key = { it.id }) { entry ->
                        var showEditDialog by remember { mutableStateOf(false) }
                        var amountInput by remember { mutableStateOf("") }
                        var selectedCategory by remember { mutableStateOf(entry.mealCategory) }
                        var selectedTag by remember { mutableStateOf(entry.tag) }

                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                when (value) {
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        viewModel.deletePending(entry)
                                        coroutineScope.launch {
                                            val result = snackbar.showSnackbar(
                                                message = "Eintrag gelöscht",
                                                actionLabel = "Rückgängig",
                                                duration = SnackbarDuration.Short,
                                            )
                                            when (result) {
                                                SnackbarResult.ActionPerformed -> viewModel.undoDelete(entry)
                                                SnackbarResult.Dismissed -> viewModel.confirmDelete(entry)
                                            }
                                        }
                                        true
                                    }
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        amountInput = entry.amountGrams.toInt().toString()
                                        selectedCategory = entry.mealCategory
                                        selectedTag = entry.tag
                                        showEditDialog = true
                                        false
                                    }
                                    else -> false
                                }
                            }
                        )

                        if (showEditDialog) {
                            EditFoodDialog(
                                entry = entry,
                                amountInput = amountInput,
                                onAmountChange = { amountInput = it },
                                selectedCategory = selectedCategory,
                                onCategoryChange = { selectedCategory = it },
                                selectedTag = selectedTag,
                                onTagChange = { selectedTag = it },
                                onDismiss = { showEditDialog = false },
                                onConfirm = { updated ->
                                    viewModel.update(updated)
                                    showEditDialog = false
                                },
                            )
                        }

                        SwipeToDismissBox(
                            state = dismissState,
                            modifier = Modifier.clip(RoundedCornerShape(18.dp)),
                            enableDismissFromStartToEnd = true,
                            backgroundContent = {
                                val bgColor = when (dismissState.targetValue) {
                                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                                    else -> Color.Transparent
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(bgColor)
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = when (dismissState.targetValue) {
                                        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                        else -> Alignment.CenterEnd
                                    },
                                ) {
                                    when (dismissState.targetValue) {
                                        SwipeToDismissBoxValue.EndToStart -> Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Löschen",
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                        SwipeToDismissBoxValue.StartToEnd -> Icon(
                                            Icons.Rounded.Edit,
                                            contentDescription = "Bearbeiten",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                        else -> {}
                                    }
                                }
                            },
                        ) {
                            FoodEntryRow(entry = entry)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditFoodDialog(
    entry: FoodEntry,
    amountInput: String,
    onAmountChange: (String) -> Unit,
    selectedCategory: MealCategory,
    onCategoryChange: (MealCategory) -> Unit,
    selectedTag: FoodTag,
    onTagChange: (FoodTag) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (FoodEntry) -> Unit,
) {
    val newAmount = amountInput.normalizeDecimal().toDoubleOrNull() ?: 0.0
    val factor = if (entry.amountGrams > 0 && newAmount > 0) newAmount / entry.amountGrams else 1.0
    val sheetState = rememberModalBottomSheetState()

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
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                buildString {
                    append(entry.foodName)
                    entry.brand?.let { append(" ($it)") }
                },
                style = MaterialTheme.typography.titleLarge,
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "MENGE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NumericTextField(
                    value = amountInput,
                    onValueChange = onAmountChange,
                    label = null,
                    suffix = "g",
                    textStyle = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "MAHLZEIT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MealCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = cat == selectedCategory,
                            onClick = { onCategoryChange(cat) },
                            label = {
                                Text(cat.displayName(), style = MaterialTheme.typography.labelSmall)
                            },
                        )
                    }
                }
            }

            TagSelector(selected = selectedTag, onSelected = onTagChange)

            if (newAmount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${(entry.kcal * factor).toInt()} kcal · " +
                            "${(entry.proteinG * factor).toInt()}g P · " +
                            "${(entry.carbsG * factor).toInt()}g K · " +
                            "${(entry.fatG * factor).toInt()}g F",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                enabled = newAmount > 0,
                onClick = {
                    if (newAmount > 0) {
                        val f = newAmount / entry.amountGrams
                        onConfirm(
                            entry.copy(
                                amountGrams = newAmount,
                                kcal = entry.kcal * f,
                                proteinG = entry.proteinG * f,
                                carbsG = entry.carbsG * f,
                                fatG = entry.fatG * f,
                                sugarG = entry.sugarG * f,
                                fiberG = entry.fiberG * f,
                                saltG = entry.saltG * f,
                                mealCategory = selectedCategory,
                                tag = selectedTag,
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Speichern", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun MacroSummaryCard(
    state: OverviewUiState,
    replayTrigger: Int,
    onReplay: () -> Unit,
) {
    val clock = remember { Animatable(0f) }
    // Restart the entrance animation on screen open, day change, or a tap on the card.
    LaunchedEffect(state.date, replayTrigger) {
        clock.snapTo(0f)
        clock.animateTo(
            targetValue = AnimTotalSeconds,
            animationSpec = tween(
                durationMillis = (AnimTotalSeconds * 1000).toInt(),
                easing = LinearEasing,
            ),
        )
    }
    val time = clock.value
    val interaction = remember { MutableInteractionSource() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, onClick = onReplay),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            CalorieRing(state = state, time = time)

            if (FoodTag.selectable.any { state.kcalForTag(it) > 0 }) {
                CleanEatingSummary(cleanPercent = state.cleanPercent, time = time)
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MacroBar("Protein", state.totalProtein, state.goal.proteinG, "g", ProteinColor, time, 0.4f, 1.3f)
                MacroBar("Kohlenhydrate", state.totalCarbs, state.goal.carbsG, "g", CarbsColor, time, 0.55f, 1.45f)
                MacroBar("Fett", state.totalFat, state.goal.fatG, "g", FatColor, time, 0.7f, 1.6f)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SecondaryMacro("Zucker", state.totalSugar, "g")
                SecondaryMacro("Ballaststoffe", state.totalFiber, "g")
                SecondaryMacro("Salz", state.totalSalt, "g")
            }
        }
    }
}

@Composable
private fun CalorieRing(state: OverviewUiState, time: Float) {
    val current = state.totalKcal
    val goal = state.goal.kcal
    val progress = if (goal > 0) (current / goal).toFloat().coerceIn(0f, 1f) else 0f
    val totalSweep = 360f * progress

    // Open track uses surface-2; consumed-but-untagged kcal use the darker `outline` so
    // the eaten-untagged portion stays visually separate from the still-open remainder.
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val untaggedConsumedColor = MaterialTheme.colorScheme.outline

    // Consumed kcal split by tag, in ring order (green → orange → red → grey/untagged).
    val segments = listOf(FoodTag.HEALTHY, FoodTag.NEUTRAL, FoodTag.UNHEALTHY, FoodTag.NONE)
        .map { tag -> (if (tag == FoodTag.NONE) untaggedConsumedColor else tag.color()) to state.kcalForTag(tag) }
        .filter { it.second > 0 }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(176.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 15.5.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            if (current > 0) {
                // Segments draw sequentially in kcal order; each grows into its fixed final
                // position (butt caps so segments abut). The per-segment window is proportional
                // to its kcal share across the 0.5–2.05s draw phase, so the fill stays in sync
                // with the centre count-up regardless of which tags are present.
                var cumKcal = 0.0
                var cumSweep = 0f
                segments.forEach { (color, kcal) ->
                    val segStart = 0.5f + (cumKcal / current).toFloat() * 1.55f
                    val segEnd = 0.5f + ((cumKcal + kcal) / current).toFloat() * 1.55f
                    val segAnim = windowProgress(time, segStart, segEnd, EaseOutCubic)
                    val finalSweep = (kcal / current).toFloat() * totalSweep
                    if (segAnim > 0f) {
                        drawArc(
                            color = color,
                            startAngle = -90f + cumSweep,
                            sweepAngle = finalSweep * segAnim,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                        )
                    }
                    cumKcal += kcal
                    cumSweep += finalSweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val shownKcal = (current * windowProgress(time, 0.4f, 2.0f, EaseOutExpo)).toInt()
            Text(
                "$shownKcal",
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                "KCAL",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            val subAlpha = windowProgress(time, 2.1f, 2.6f, EaseOutCubic)
            val remaining = goal - current
            val (caption, captionColor) = when {
                remaining > 0 -> "noch ${remaining.toInt()}" to MaterialTheme.colorScheme.onSurfaceVariant
                remaining < 0 -> "+${(-remaining).toInt()} zuviel" to MaterialTheme.colorScheme.error
                else -> "Ziel erreicht" to MaterialTheme.colorScheme.primary
            }
            Text(
                caption,
                style = MaterialTheme.typography.bodyMedium,
                color = captionColor,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .alpha(subAlpha),
            )
        }
    }
}

@Composable
private fun CleanEatingSummary(cleanPercent: Int?, time: Float) {
    val reveal = windowProgress(time, 2.6f, 3.2f, EaseOutCubic)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = reveal
                translationY = (1f - reveal) * 16.dp.toPx()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (cleanPercent != null) {
            Text(
                "$cleanPercent % CLEAN",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FoodTag.selectable.forEach { tag ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(tag.color()))
                    Text(
                        tag.displayName(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroBar(
    label: String,
    current: Double,
    goal: Double,
    unit: String,
    color: Color,
    time: Float,
    startS: Float,
    endS: Float,
) {
    val eased = windowProgress(time, startS, endS, EaseOutCubic)
    val target = if (goal > 0) (current / goal).toFloat().coerceIn(0f, 1f) else 0f
    val animatedProgress = target * eased
    val shownValue = (current * eased).toInt()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "$shownValue / ${goal.toInt()} $unit",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.2f)),
        ) {
            if (animatedProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(color),
                )
            }
        }
    }
}

@Composable
private fun SecondaryMacro(label: String, value: Double, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${value.toInt()}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                " $unit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun WeightCard(weight: WeightEntry?, onSave: (Double) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Gewicht eintragen") },
            text = {
                NumericTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = "kg",
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    input.normalizeDecimal().toDoubleOrNull()?.let { onSave(it) }
                    showDialog = false
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Abbrechen") }
            },
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                input = weight?.weightKg?.let {
                    if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
                } ?: ""
                showDialog = true
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Gewicht heute", style = MaterialTheme.typography.titleSmall)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    weight?.let { "${it.weightKg} kg" } ?: "– kg",
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "Bearbeiten",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

private fun MealCategory.displayName() = when (this) {
    MealCategory.BREAKFAST -> "Frühstück"
    MealCategory.LUNCH -> "Mittagessen"
    MealCategory.DINNER -> "Abendessen"
    MealCategory.SNACK -> "Snack"
}

@Composable
private fun MealSectionHeader(name: String, kcal: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "$kcal kcal",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CopyMealButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
    ) {
        Icon(
            Icons.Rounded.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun FoodEntryRow(entry: FoodEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Fixed-width leading slot keeps the name (and the macro line below) aligned
            // whether or not the entry carries a tag dot.
            Box(Modifier.size(7.dp)) {
                if (entry.tag != FoodTag.NONE) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(entry.tag.color()))
                }
            }
            Text(
                buildString {
                    append(entry.foodName)
                    entry.brand?.let { append(" ($it)") }
                    append(" · ${entry.amountGrams.toInt()} g")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            "${entry.kcal.toInt()} kcal · ${entry.proteinG.toInt()}g P · ${entry.carbsG.toInt()}g K · ${entry.fatG.toInt()}g F",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 13.dp),
        )
    }
}
