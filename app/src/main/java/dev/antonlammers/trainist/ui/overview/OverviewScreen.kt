package dev.antonlammers.trainist.ui.overview

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.EaseOutExpo
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.domain.model.FoodTag
import dev.antonlammers.trainist.domain.model.MealCategory
import dev.antonlammers.trainist.domain.model.WeightEntry
import dev.antonlammers.trainist.ui.addfood.SAVED_MEAL_NAME_RESULT
import dev.antonlammers.trainist.ui.components.NumericTextField
import dev.antonlammers.trainist.ui.components.TagSelector
import dev.antonlammers.trainist.ui.components.color
import dev.antonlammers.trainist.ui.components.displayName
import dev.antonlammers.trainist.ui.navigation.FloatingNavClearance
import dev.antonlammers.trainist.ui.navigation.FloatingNavSnackbarOffset
import dev.antonlammers.trainist.ui.navigation.ProvidePrimaryAction
import dev.antonlammers.trainist.ui.navigation.Screen
import dev.antonlammers.trainist.ui.theme.CarbsColor
import dev.antonlammers.trainist.ui.theme.FatColor
import dev.antonlammers.trainist.ui.theme.ProteinColor
import dev.antonlammers.trainist.ui.tutorial.TutorialTarget
import dev.antonlammers.trainist.ui.tutorial.tutorialAnchor
import dev.antonlammers.trainist.ui.util.currentAppLocale
import dev.antonlammers.trainist.ui.util.localizedDateFormatter
import dev.antonlammers.trainist.util.normalizeDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import kotlinx.coroutines.launch

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

    // UI-local replay trigger — bumped on a tap of the calorie card. Day changes are already
    // captured via `state.date`. Both restart the animation clock shared by the two summary cards.
    var replayTrigger by remember { mutableIntStateOf(0) }
    val clock = remember { Animatable(0f) }
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
    // Read lazily inside the cards, so only they recompose per animation frame.
    val time = { clock.value }

    // Resolved here (not inside the snackbar-launching lambda below, which runs in a coroutine
    // scope rather than a @Composable context, so stringResource() isn't callable there).
    val entryDeletedMessage = stringResource(R.string.addfood_entry_deleted)
    val undoLabel = stringResource(R.string.common_undo)
    val context = LocalContext.current

    // A meal section saved as "Meine Mahlzeiten" via its bookmark comes back here from AddFood,
    // carrying the meal's name for the confirmation.
    val backStackHandle = navController.currentBackStackEntry?.savedStateHandle
    LaunchedEffect(backStackHandle) {
        backStackHandle?.getStateFlow<String?>(SAVED_MEAL_NAME_RESULT, null)?.collect { name ->
            if (name != null) {
                backStackHandle.remove<String>(SAVED_MEAL_NAME_RESULT)
                snackbar.showSnackbar(context.getString(R.string.overview_meal_saved, name))
            }
        }
    }

    // The shell's plus button logs food for the day being viewed, not always for today.
    ProvidePrimaryAction(
        contentDescription = stringResource(R.string.addfood_title),
        tutorialTarget = TutorialTarget.ADD_FOOD,
    ) { navController.navigate(Screen.AddFood.withDate(state.date)) }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbar, modifier = Modifier.padding(bottom = FloatingNavSnackbarOffset))
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = FloatingNavClearance),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OverviewHeader(
                    date = state.date,
                    weight = state.todayWeight,
                    onSaveWeight = viewModel::saveWeight,
                    onToday = viewModel::goToToday,
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                )
            }
            item {
                WeekStrip(
                    week = state.week,
                    selected = state.date,
                    goalKcal = state.goal.kcal,
                    onSelect = viewModel::selectDate,
                    onShiftWeek = viewModel::shiftWeek,
                )
            }
            item {
                CalorieCard(state = state, time = time, onReplay = { replayTrigger++ })
            }
            item {
                MacroCard(state = state, time = time)
            }
            if (state.entries.isEmpty() && state.copyableMeals.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.overview_empty_state),
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
                                    label = stringResource(R.string.overview_copy_meal_button, category.displayName()),
                                    onClick = { viewModel.copyMealFromPreviousDay(category) },
                                )
                            }
                        }
                        return@forEach
                    }
                    item(key = "header_${category.name}") {
                        val categoryName = category.displayName()
                        MealSectionHeader(
                            name = categoryName,
                            kcal = state.kcalForMeal(category).toInt(),
                            saveAsMealDescription = stringResource(R.string.overview_save_as_meal_content_description, categoryName),
                            onSaveAsMeal = { navController.navigate(Screen.AddFood.saveMeal(state.date, category)) },
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
                                                message = entryDeletedMessage,
                                                actionLabel = undoLabel,
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
                    stringResource(R.string.addfood_amount_label),
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
                    stringResource(R.string.addfood_meal_label),
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
                        stringResource(
                            R.string.addfood_food_detail_total,
                            (entry.kcal * factor).toInt(),
                            (entry.proteinG * factor).toInt(),
                            (entry.carbsG * factor).toInt(),
                            (entry.fatG * factor).toInt(),
                        ),
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
                Text(stringResource(R.string.common_save), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun OverviewHeader(
    date: LocalDate,
    weight: WeightEntry?,
    onSaveWeight: (Double) -> Unit,
    onToday: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                date.format(localizedDateFormatter("EEEE")),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            Text(
                date.format(localizedDateFormatter("d. MMMM")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (date != LocalDate.now()) {
                TextButton(onClick = onToday, modifier = Modifier.height(36.dp)) {
                    Text(stringResource(R.string.common_today), color = MaterialTheme.colorScheme.primary)
                }
            }
            WeightChip(
                weight = weight,
                onSave = onSaveWeight,
                modifier = Modifier.tutorialAnchor(TutorialTarget.WEIGHT),
            )
            HeaderIconButton(
                icon = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.settings_title),
                onClick = onOpenSettings,
                modifier = Modifier.tutorialAnchor(TutorialTarget.SETTINGS),
            )
        }
    }
}

private val HeaderControlHeight = 36.dp

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(HeaderControlHeight)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun WeightChip(weight: WeightEntry?, onSave: (Double) -> Unit, modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val dialogTitle = stringResource(R.string.overview_weight_dialog_title)

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(dialogTitle) },
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
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .height(HeaderControlHeight)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClickLabel = dialogTitle) {
                input = weight?.weightKg?.let {
                    if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
                } ?: ""
                showDialog = true
            }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.MonitorWeight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp),
        )
        Text(
            weight?.let { "${it.weightKg} kg" } ?: "– kg",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/**
 * Monday–Sunday of the selected day's ISO week, each day with a mini progress bar against the kcal
 * goal. Tapping a day selects it; a horizontal swipe moves a whole week, keeping the weekday.
 */
@Composable
private fun WeekStrip(
    week: List<DaySummary>,
    selected: LocalDate,
    goalKcal: Double,
    onSelect: (LocalDate) -> Unit,
    onShiftWeek: (Long) -> Unit,
) {
    val swipeThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    AnimatedContent(
        targetState = week,
        contentKey = { it.firstOrNull()?.date },
        transitionSpec = {
            val forward = (targetState.firstOrNull()?.date ?: LocalDate.MIN) >
                (initialState.firstOrNull()?.date ?: LocalDate.MIN)
            val sign = if (forward) 1 else -1
            (slideInHorizontally { width -> sign * width } + fadeIn()) togetherWith
                (slideOutHorizontally { width -> -sign * width } + fadeOut())
        },
        label = "weekStrip",
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .tutorialAnchor(TutorialTarget.DAY_SWITCH)
            .pointerInput(Unit) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        // Swiping right reveals the previous week, like turning back a page.
                        when {
                            drag > swipeThreshold -> onShiftWeek(-1)
                            drag < -swipeThreshold -> onShiftWeek(1)
                        }
                    },
                    onHorizontalDrag = { _, delta -> drag += delta },
                )
            },
    ) { days ->
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEach { day ->
                WeekDayCell(
                    day = day,
                    isSelected = day.date == selected,
                    goalKcal = goalKcal,
                    onClick = { onSelect(day.date) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WeekDayCell(
    day: DaySummary,
    isSelected: Boolean,
    goalKcal: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val isFuture = day.date.isAfter(LocalDate.now())
    val ratio = if (goalKcal > 0) (day.kcal / goalKcal).toFloat() else 0f
    val background by animateColorAsState(
        if (isSelected) scheme.onBackground else Color.Transparent,
        animationSpec = tween(150),
        label = "weekCell",
    )
    val abbreviationColor = if (isSelected) scheme.background else scheme.onSurfaceVariant
    val numberColor = when {
        isSelected -> scheme.background
        isFuture -> scheme.outline
        else -> scheme.onBackground
    }
    val trackColor = if (isSelected) scheme.onSurfaceVariant else scheme.surfaceVariant
    val fillColor = when {
        ratio > 1f -> scheme.error
        isSelected -> scheme.background
        else -> scheme.onSurfaceVariant
    }
    val locale = currentAppLocale()
    val fullDate = day.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = fullDate
                selected = isSelected
                role = Role.Tab
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).take(2).uppercase(locale),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
            color = abbreviationColor,
        )
        Text(
            "${day.date.dayOfMonth}",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
            color = numberColor,
        )
        Box(
            modifier = Modifier
                .size(width = 18.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(trackColor),
        ) {
            if (!isFuture && ratio > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(ratio.coerceAtMost(1f))
                        .fillMaxHeight()
                        .background(fillColor),
                )
            }
        }
    }
}

/** Shared shell of the two summary cards. */
@Composable
private fun SummaryCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { content() }
}

@Composable
private fun CalorieCard(state: OverviewUiState, time: () -> Float, onReplay: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val current = state.totalKcal
    val goal = state.goal.kcal
    val isOver = goal > 0 && current > goal
    val hasTags = FoodTag.selectable.any { state.kcalForTag(it) > 0 }

    SummaryCard(
        modifier = Modifier
            .tutorialAnchor(TutorialTarget.CALORIES)
            .clickable(interactionSource = interaction, indication = null, onClick = onReplay),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val t = time()
            val reveal = windowProgress(t, 2.1f, 2.6f, EaseOutCubic)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val shownKcal = (current * windowProgress(t, 0.4f, 2.0f, EaseOutExpo)).toInt()
                    Text(
                        "$shownKcal",
                        style = MaterialTheme.typography.displayMedium,
                        color = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        "/ ${goal.toInt()} " + stringResource(R.string.overview_kcal_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                state.cleanPercent?.takeIf { hasTags }?.let { percent ->
                    Text(
                        stringResource(R.string.overview_clean_percent, percent),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .revealUp(reveal),
                    )
                }
            }

            CalorieBar(state = state, time = t)

            if (hasTags) {
                Row(
                    modifier = Modifier.revealUp(reveal),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
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
    }
}

/** Fades in while sliding up 16dp, driven by an already-eased [progress]. */
private fun Modifier.revealUp(progress: Float) = graphicsLayer {
    alpha = progress
    translationY = (1f - progress) * 16.dp.toPx()
}

/**
 * Consumed kcal against the goal as one segmented bar, split by tag in the order
 * healthy → neutral → unhealthy → untagged. Past the goal the bar is full and the segments keep
 * their proportions.
 */
@Composable
private fun CalorieBar(state: OverviewUiState, time: Float) {
    val current = state.totalKcal
    val goal = state.goal.kcal
    // Consumed-but-untagged kcal use the darker `outline`, so they stay distinct from the open track.
    val untaggedColor = MaterialTheme.colorScheme.outline
    val segments = listOf(FoodTag.HEALTHY, FoodTag.NEUTRAL, FoodTag.UNHEALTHY, FoodTag.NONE)
        .map { tag -> (if (tag == FoodTag.NONE) untaggedColor else tag.color()) to state.kcalForTag(tag) }
        .filter { it.second > 0 }
    val scale = maxOf(current, goal)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (current <= 0 || scale <= 0) return@Canvas
        val gap = 2.dp.toPx()
        // Segments draw one after another; each window is proportional to its kcal share across
        // the 0.5–2.05s draw phase, so the fill stays in sync with the count-up.
        var cumKcal = 0.0
        segments.forEachIndexed { index, (color, kcal) ->
            val segStart = 0.5f + (cumKcal / current).toFloat() * 1.55f
            val segEnd = 0.5f + ((cumKcal + kcal) / current).toFloat() * 1.55f
            val progress = windowProgress(time, segStart, segEnd, EaseOutCubic)
            val startX = (cumKcal / scale).toFloat() * size.width
            val trailingGap = if (index < segments.lastIndex) gap else 0f
            val finalWidth = (kcal / scale).toFloat() * size.width - trailingGap
            if (progress > 0f && finalWidth > 0f) {
                drawRect(
                    color = color,
                    topLeft = Offset(startX, 0f),
                    size = Size(finalWidth * progress, size.height),
                )
            }
            cumKcal += kcal
        }
    }
}

@Composable
private fun MacroCard(state: OverviewUiState, time: () -> Float) {
    SummaryCard {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val t = time()
            Row(modifier = Modifier.fillMaxWidth()) {
                val ringModifier = Modifier.weight(1f)
                MacroRing(stringResource(R.string.overview_macro_protein), state.totalProtein, state.goal.proteinG, ProteinColor, t, 0.4f, 1.3f, ringModifier)
                MacroRing(stringResource(R.string.overview_macro_carbs), state.totalCarbs, state.goal.carbsG, CarbsColor, t, 0.55f, 1.45f, ringModifier)
                MacroRing(stringResource(R.string.overview_macro_fat), state.totalFat, state.goal.fatG, FatColor, t, 0.7f, 1.6f, ringModifier)
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            ) {
                SecondaryMacro(stringResource(R.string.overview_sugar_label), state.totalSugar)
                SecondaryMacro(stringResource(R.string.overview_fiber_label), state.totalFiber)
                SecondaryMacro(stringResource(R.string.overview_salt_label), state.totalSalt)
            }
        }
    }
}

@Composable
private fun MacroRing(
    label: String,
    current: Double,
    goal: Double,
    color: Color,
    time: Float,
    startS: Float,
    endS: Float,
    modifier: Modifier = Modifier,
) {
    val eased = windowProgress(time, startS, endS, EaseOutCubic)
    val target = if (goal > 0) (current / goal).toFloat().coerceIn(0f, 1f) else 0f
    val sweep = 360f * target * eased

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 6.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                drawArc(
                    color = color.copy(alpha = 0.2f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth),
                )
                if (sweep > 0f) {
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }
            Text(
                "${(current * eased).toInt()}",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "/ ${goal.toInt()} G",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun SecondaryMacro(label: String, valueG: Double) {
    Text(
        buildAnnotatedString {
            append("$label ")
            withStyle(
                SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium),
            ) { append("${valueG.toInt()} g") }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        maxLines = 1,
    )
}

@Composable
private fun MealSectionHeader(
    name: String,
    kcal: Int,
    saveAsMealDescription: String,
    onSaveAsMeal: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            "$kcal kcal",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Saves the section's entries as one of "Meine Mahlzeiten" — the quickest way to one.
        IconButton(onClick = onSaveAsMeal, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.BookmarkAdd,
                contentDescription = saveAsMealDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
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
            stringResource(
                R.string.addfood_food_detail_total,
                entry.kcal.toInt(),
                entry.proteinG.toInt(),
                entry.carbsG.toInt(),
                entry.fatG.toInt(),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 13.dp),
        )
    }
}
