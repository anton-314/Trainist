package dev.antonlammers.trainist.ui.addfood

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.FoodPortions
import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.domain.model.FoodTag
import dev.antonlammers.trainist.domain.model.MealCategory
import dev.antonlammers.trainist.domain.model.SavedMeal
import dev.antonlammers.trainist.domain.model.SavedMealItem
import dev.antonlammers.trainist.ui.components.NumericTextField
import dev.antonlammers.trainist.ui.components.TagDot
import dev.antonlammers.trainist.ui.components.TagSelector
import dev.antonlammers.trainist.ui.components.displayName
import dev.antonlammers.trainist.ui.navigation.Screen
import dev.antonlammers.trainist.ui.util.currentAppLocale
import dev.antonlammers.trainist.ui.util.localizedDateFormatter
import dev.antonlammers.trainist.util.normalizeDecimal
import java.time.LocalDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(
    navController: NavController,
    viewModel: AddFoodViewModel = hiltViewModel(),
    mealEditor: MealEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recentEntries by viewModel.recentEntries.collectAsStateWithLifecycle()
    val customFoods by viewModel.customFoods.collectAsStateWithLifecycle()
    val savedMeals by viewModel.savedMeals.collectAsStateWithLifecycle()
    val mealState by mealEditor.uiState.collectAsStateWithLifecycle()
    val localSearchResults by viewModel.localSearchResults.collectAsStateWithLifecycle()
    val remoteSearch by viewModel.remoteSearchResults.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // The "+" sheet in food mode. Meal mode needs no flag of its own: it is open whenever the meal
    // editor holds a draft (unless the user switched the sheet over to food).
    var showCreateFood by rememberSaveable { mutableStateOf(false) }
    var foodToEdit by remember { mutableStateOf<Food?>(null) }
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_HISTORY) }
    val picking = mealState.picking

    // Resolved here (not inside the snackbar-launching lambdas below, which run in a coroutine
    // scope rather than a @Composable context, so stringResource() isn't callable there).
    val customFoodDeletedMessage = stringResource(R.string.addfood_custom_food_deleted)
    val entryDeletedMessage = stringResource(R.string.addfood_entry_deleted)
    val mealDeletedMessage = stringResource(R.string.addfood_saved_meal_deleted)
    val undoLabel = stringResource(R.string.common_undo)

    LaunchedEffect(state.entryAdded) {
        if (state.entryAdded) {
            viewModel.entryAddedHandled()
            navController.popBackStack()
        }
    }

    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    LaunchedEffect(savedStateHandle) {
        savedStateHandle?.getStateFlow<String?>("barcode", null)?.collect { barcode ->
            if (barcode != null) {
                viewModel.handleBarcode(barcode)
                savedStateHandle.remove<String>("barcode")
            }
        }
    }

    LaunchedEffect(mealState.saved) {
        val saved = mealState.saved ?: return@LaunchedEffect
        mealEditor.savedHandled()
        showCreateFood = false
        when (saved.origin) {
            // Created via "+": most likely about to be eaten — offer to log it right away, like a
            // freshly created custom food.
            MealDraftOrigin.NEW -> {
                selectedTab = TAB_MEALS
                viewModel.selectMeal(saved.meal)
            }
            MealDraftOrigin.EDIT -> Unit
            // Its entries are already in the diary; go back to where the user came from.
            MealDraftOrigin.FROM_DAY -> {
                navController.previousBackStackEntry?.savedStateHandle?.set(SAVED_MEAL_NAME_RESULT, saved.meal.name)
                navController.popBackStack()
            }
        }
    }

    BackHandler(enabled = picking) { mealEditor.cancelPicking() }

    val mealDraft = mealState.draft
    val createKind = when {
        showCreateFood -> CreateKind.FOOD
        mealDraft != null -> CreateKind.MEAL
        else -> null
    }
    if (createKind != null && !picking && state.selectedFood == null && state.mealToLog == null) {
        val dismiss = {
            showCreateFood = false
            mealEditor.discard()
            // Opened from the Overview's bookmark only to save that meal — cancelling goes back there.
            if (mealDraft?.origin == MealDraftOrigin.FROM_DAY) navController.popBackStack()
        }
        CreateSheet(
            kind = createKind,
            kindSwitchable = mealDraft == null || mealDraft.origin == MealDraftOrigin.NEW,
            title = stringResource(
                when {
                    createKind == CreateKind.FOOD -> R.string.addfood_custom_food_dialog_title_new
                    mealDraft?.origin == MealDraftOrigin.EDIT -> R.string.addfood_meal_dialog_title_edit
                    else -> R.string.addfood_meal_dialog_title_new
                },
            ),
            onKindChange = { kind ->
                showCreateFood = kind == CreateKind.FOOD
                if (kind == CreateKind.MEAL) mealEditor.startNew()
            },
            onDismiss = dismiss,
        ) {
            if (createKind == CreateKind.FOOD || mealDraft == null) {
                CustomFoodForm(
                    onSave = { food ->
                        viewModel.saveCustomFood(food)
                        dismiss()
                    },
                )
            } else {
                MealEditorForm(
                    draft = mealDraft,
                    onNameChange = mealEditor::onNameChange,
                    onAmountChange = mealEditor::onAmountChange,
                    onRemove = mealEditor::removeItem,
                    onAddIngredient = mealEditor::startPicking,
                    onSave = mealEditor::save,
                )
            }
        }
    }

    foodToEdit?.let { food ->
        CreateSheet(
            kind = CreateKind.FOOD,
            kindSwitchable = false,
            title = stringResource(R.string.addfood_custom_food_dialog_title_edit),
            onKindChange = {},
            onDismiss = { foodToEdit = null },
        ) {
            CustomFoodForm(
                initial = food,
                onSave = { updated ->
                    viewModel.updateCustomFood(updated.copy(id = food.id))
                    foodToEdit = null
                },
            )
        }
    }

    state.mealToLog?.let { draft ->
        MealLogSheet(
            draft = draft,
            mealCategory = state.mealCategory,
            onAmountChange = viewModel::onMealLogAmountChange,
            onMealCategoryChange = viewModel::onMealCategoryChange,
            onConfirm = viewModel::confirmLogMeal,
            onDismiss = viewModel::dismissMealLog,
        )
    }

    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text(stringResource(R.string.addfood_barcode_error_title)) },
            text = { Text(stringResource(error.messageRes())) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.common_ok)) }
            },
        )
    }

    state.selectedFood?.let { food ->
        AmountSheet(
            food = food,
            amount = state.amountGrams,
            mealCategory = state.mealCategory,
            tag = state.tag,
            forIngredient = picking,
            onAmountChange = viewModel::onAmountChange,
            onMealCategoryChange = viewModel::onMealCategoryChange,
            onTagChange = viewModel::onTagChange,
            onConfirm = {
                if (picking) viewModel.takeIngredient()?.let(mealEditor::addItem) else viewModel.confirmAdd()
            },
            onDismiss = viewModel::dismissSelection,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val isToday = viewModel.targetDate == LocalDate.now()
                    val mealName = mealDraft?.name?.trim().orEmpty()
                    if (picking) {
                        Text(
                            if (mealName.isEmpty()) {
                                stringResource(R.string.addfood_pick_ingredient_title_unnamed)
                            } else {
                                stringResource(R.string.addfood_pick_ingredient_title, mealName)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else if (isToday) {
                        Text(stringResource(R.string.addfood_title))
                    } else {
                        Text(stringResource(R.string.addfood_title_for_date, viewModel.targetDate.formatRelative()))
                    }
                },
                navigationIcon = {
                    if (picking) {
                        IconButton(onClick = mealEditor::cancelPicking) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.addfood_pick_ingredient_cancel_content_description),
                            )
                        }
                    } else {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    }
                },
                actions = {
                    if (!picking) {
                        IconButton(onClick = { showCreateFood = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.addfood_create_content_description))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchBar(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onScanClick = { navController.navigate(Screen.BarcodeScanner.route) },
            )

            // While picking an ingredient, a meal can't be one — the meals tab steps aside.
            val tab = if (picking && selectedTab == TAB_MEALS) TAB_HISTORY else selectedTab
            val shownLocalResults =
                if (picking) localSearchResults.filterNot { it is LocalSearchResult.SavedMealResult } else localSearchResults
            if (state.query.isEmpty() && !state.isLoading) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    MonoTab(stringResource(R.string.addfood_tab_history), tab == TAB_HISTORY) { selectedTab = TAB_HISTORY }
                    MonoTab(stringResource(R.string.addfood_tab_custom_foods), tab == TAB_CUSTOM_FOODS) { selectedTab = TAB_CUSTOM_FOODS }
                    if (!picking) {
                        MonoTab(stringResource(R.string.addfood_tab_saved_meals), tab == TAB_MEALS) { selectedTab = TAB_MEALS }
                    }
                }
            }

            val onDeleteMeal: (SavedMeal) -> Unit = { meal ->
                viewModel.deletePendingMeal(meal)
                coroutineScope.launch {
                    val result = snackbar.showSnackbar(
                        message = mealDeletedMessage,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Short,
                    )
                    when (result) {
                        SnackbarResult.ActionPerformed -> viewModel.undoDeleteMeal(meal)
                        SnackbarResult.Dismissed -> viewModel.confirmDeleteMeal(meal)
                    }
                }
            }

            LazyColumn {
                // Empty state: active tab content
                if (state.query.isEmpty() && !state.isLoading) {
                    if (tab == TAB_MEALS) {
                        if (savedMeals.isEmpty()) {
                            item { EmptyHint(stringResource(R.string.addfood_empty_saved_meals)) }
                        } else {
                            items(savedMeals, key = { "meal_${it.id}" }) { meal ->
                                SwipeableMealRow(
                                    meal = meal,
                                    onClick = { viewModel.selectMeal(meal) },
                                    onEdit = { mealEditor.edit(meal) },
                                    onDelete = { onDeleteMeal(meal) },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    } else if (tab == TAB_CUSTOM_FOODS) {
                        if (customFoods.isEmpty()) {
                            item {
                                EmptyHint(stringResource(R.string.addfood_empty_custom_foods))
                            }
                        } else {
                            items(customFoods, key = { "custom_${it.id}" }) { food ->
                                SwipeableCustomFoodRow(
                                    food = food,
                                    onClick = { viewModel.selectFood(food) },
                                    onEdit = { foodToEdit = food },
                                    onDelete = {
                                        viewModel.deletePendingCustomFood(food)
                                        coroutineScope.launch {
                                            val result = snackbar.showSnackbar(
                                                message = customFoodDeletedMessage,
                                                actionLabel = undoLabel,
                                                duration = SnackbarDuration.Short,
                                            )
                                            when (result) {
                                                SnackbarResult.ActionPerformed -> viewModel.undoDeleteCustomFood(food)
                                                SnackbarResult.Dismissed -> viewModel.confirmDeleteCustomFood(food)
                                            }
                                        }
                                    },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    } else {
                        if (recentEntries.isEmpty()) {
                            item { EmptyHint(stringResource(R.string.addfood_empty_history)) }
                        } else {
                            val grouped = recentEntries
                                .groupBy { it.date }
                                .entries
                                .sortedByDescending { it.key }
                            grouped.forEach { (date, entries) ->
                                item(key = "header_$date") {
                                    DateGroupHeader(date.formatRelative())
                                }
                                items(entries, key = { "history_${it.id}" }) { entry ->
                                    SwipeableHistoryRow(
                                        entry = entry,
                                        onClick = { viewModel.selectRecentFood(entry) },
                                        onDelete = {
                                            viewModel.deletePendingEntry(entry)
                                            coroutineScope.launch {
                                                val result = snackbar.showSnackbar(
                                                    message = entryDeletedMessage,
                                                    actionLabel = undoLabel,
                                                    duration = SnackbarDuration.Short,
                                                )
                                                when (result) {
                                                    SnackbarResult.ActionPerformed -> viewModel.undoDeleteEntry(entry)
                                                    SnackbarResult.Dismissed -> viewModel.confirmDeleteEntry(entry)
                                                }
                                            }
                                        },
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }

                // Loading (barcode)
                if (state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                }

                // Search results: flat list of matching custom foods + deduplicated history
                if (state.query.isNotBlank() && !state.isLoading) {
                    // Only speak of "no results" once the online search has had its say too —
                    // otherwise the hint flashes up in the pause before the request even goes out.
                    if (shownLocalResults.isEmpty() && remoteSearch.results.isEmpty() &&
                        !remoteSearch.isLoading && !remoteSearch.failed
                    ) {
                        item { EmptyHint(stringResource(R.string.addfood_empty_search_results)) }
                    } else {
                        items(
                            shownLocalResults,
                            key = { result ->
                                when (result) {
                                    is LocalSearchResult.SavedMealResult -> "search_meal_${result.meal.id}"
                                    is LocalSearchResult.CustomFoodResult -> "search_custom_${result.food.id}"
                                    is LocalSearchResult.HistoryResult -> "search_history_${result.entry.id}"
                                }
                            },
                        ) { result ->
                            when (result) {
                                is LocalSearchResult.SavedMealResult -> SwipeableMealRow(
                                    meal = result.meal,
                                    onClick = { viewModel.selectMeal(result.meal) },
                                    onEdit = { mealEditor.edit(result.meal) },
                                    onDelete = { onDeleteMeal(result.meal) },
                                )
                                is LocalSearchResult.CustomFoodResult -> SwipeableCustomFoodRow(
                                    food = result.food,
                                    onClick = { viewModel.selectFood(result.food) },
                                    onEdit = { foodToEdit = result.food },
                                    onDelete = {
                                        viewModel.deletePendingCustomFood(result.food)
                                        coroutineScope.launch {
                                            val r = snackbar.showSnackbar(
                                                message = customFoodDeletedMessage,
                                                actionLabel = undoLabel,
                                                duration = SnackbarDuration.Short,
                                            )
                                            when (r) {
                                                SnackbarResult.ActionPerformed -> viewModel.undoDeleteCustomFood(result.food)
                                                SnackbarResult.Dismissed -> viewModel.confirmDeleteCustomFood(result.food)
                                            }
                                        }
                                    },
                                )
                                is LocalSearchResult.HistoryResult -> SwipeableHistoryRow(
                                    entry = result.entry,
                                    onClick = { viewModel.selectRecentFood(result.entry) },
                                    onDelete = {
                                        viewModel.deletePendingEntry(result.entry)
                                        coroutineScope.launch {
                                            val r = snackbar.showSnackbar(
                                                message = entryDeletedMessage,
                                                actionLabel = undoLabel,
                                                duration = SnackbarDuration.Short,
                                            )
                                            when (r) {
                                                SnackbarResult.ActionPerformed -> viewModel.undoDeleteEntry(result.entry)
                                                SnackbarResult.Dismissed -> viewModel.confirmDeleteEntry(result.entry)
                                            }
                                        }
                                    },
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }

                    // Open Food Facts results, below whatever the user has already logged before —
                    // their own history is always the better match, so it stays on top.
                    if (remoteSearch.isLoading || remoteSearch.failed || remoteSearch.results.isNotEmpty()) {
                        item { SectionLabel(stringResource(R.string.addfood_online_results_label)) }
                    }
                    when {
                        remoteSearch.isLoading -> item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                        }
                        remoteSearch.failed -> item {
                            EmptyHint(stringResource(R.string.addfood_online_search_failed))
                        }
                        else -> items(remoteSearch.results, key = { "off_${it.id}" }) { food ->
                            FoodRowContent(
                                tag = food.tag,
                                title = buildString {
                                    append(food.name)
                                    food.brand?.let { append(" ($it)") }
                                },
                                detail = stringResource(
                                    R.string.addfood_food_detail_per_100g,
                                    food.kcalPer100g.toInt(),
                                    food.proteinPer100g.toInt(),
                                    food.carbsPer100g.toInt(),
                                    food.fatPer100g.toInt(),
                                ),
                                onClick = { viewModel.selectFood(food) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top: rounded search field (surface fill, hairline border) + a separate square
// barcode-scan tile matching the field height.
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onScanClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.addfood_search_placeholder)) },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .clickable(onClick = onScanClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.QrCodeScanner,
                contentDescription = stringResource(R.string.addfood_scan_barcode_content_description),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Mono-uppercase tab label with a 2dp accent underline on the active tab. */
@Composable
private fun RowScope.MonoTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label.uppercase(currentAppLocale()),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp, end = 4.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

@Composable
private fun DateGroupHeader(label: String) {
    Text(
        label.uppercase(currentAppLocale()),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableCustomFoodRow(
    food: Food,
    onClick: () -> Unit,
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
        state = dismissState,
        enableDismissFromStartToEnd = true,
        backgroundContent = { SwipeBackground(dismissState.targetValue) },
    ) {
        FoodRowContent(
            tag = food.tag,
            title = buildString {
                append(food.name)
                food.brand?.let { append(" ($it)") }
            },
            detail = stringResource(
                R.string.addfood_food_detail_per_100g,
                food.kcalPer100g.toInt(),
                food.proteinPer100g.toInt(),
                food.carbsPer100g.toInt(),
                food.fatPer100g.toInt(),
            ),
            onClick = onClick,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableHistoryRow(
    entry: FoodEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = { SwipeBackground(dismissState.targetValue) },
    ) {
        FoodRowContent(
            tag = entry.tag,
            title = buildString {
                append(entry.foodName)
                entry.brand?.let { append(" ($it)") }
                append(" · ${entry.amountGrams.toInt()} g")
            },
            detail = stringResource(
                R.string.addfood_food_detail_total,
                entry.kcal.toInt(),
                entry.proteinG.toInt(),
                entry.carbsG.toInt(),
                entry.fatG.toInt(),
            ),
            onClick = onClick,
        )
    }
}

/** A saved meal: name over "n ingredients · kcal"; swipe to edit or delete, like a custom food. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableMealRow(
    meal: SavedMeal,
    onClick: () -> Unit,
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
        state = dismissState,
        enableDismissFromStartToEnd = true,
        backgroundContent = { SwipeBackground(dismissState.targetValue) },
    ) {
        FoodRowContent(
            tag = FoodTag.NONE,
            title = meal.name,
            detail = pluralStringResource(
                R.plurals.addfood_meal_detail,
                meal.items.size,
                meal.items.size,
                FoodPortions.totals(meal.items).kcal.toInt(),
            ),
            onClick = onClick,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
            .background(bgColor)
            .padding(horizontal = 16.dp),
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

/** Mono-uppercase caption separating a group of rows from the one above it. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** A food/history list row: tag dot + name (sans) with a mono detail line beneath. */
@Composable
private fun FoodRowContent(
    tag: FoodTag,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Fixed-width leading slot keeps titles aligned whether or not a tag dot is shown.
            Box(Modifier.size(7.dp)) { TagDot(tag, size = 7.dp) }
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 15.dp, top = 4.dp),
        )
    }
}

/**
 * The custom-food body of [CreateSheet] — new (via "+") or edited (via swipe, with [initial]). Only
 * the field list scrolls; the save button stays pinned below it.
 */
@Composable
private fun ColumnScope.CustomFoodForm(
    initial: Food? = null,
    onSave: (Food) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var brand by remember { mutableStateOf(initial?.brand ?: "") }
    var kcal by remember { mutableStateOf(initial?.kcalPer100g?.toString() ?: "") }
    var protein by remember { mutableStateOf(initial?.proteinPer100g?.toString() ?: "") }
    var carbs by remember { mutableStateOf(initial?.carbsPer100g?.toString() ?: "") }
    var fat by remember { mutableStateOf(initial?.fatPer100g?.toString() ?: "") }
    var sugar by remember { mutableStateOf(initial?.sugarPer100g?.takeIf { it > 0 }?.toString() ?: "") }
    var fiber by remember { mutableStateOf(initial?.fiberPer100g?.takeIf { it > 0 }?.toString() ?: "") }
    var salt by remember { mutableStateOf(initial?.saltPer100g?.takeIf { it > 0 }?.toString() ?: "") }
    var tag by remember { mutableStateOf(initial?.tag ?: FoodTag.NONE) }

    val isValid = name.isNotBlank()
        && kcal.normalizeDecimal().toDoubleOrNull() != null
        && protein.normalizeDecimal().toDoubleOrNull() != null
        && carbs.normalizeDecimal().toDoubleOrNull() != null
        && fat.normalizeDecimal().toDoubleOrNull() != null

    Column(
        modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.addfood_field_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(brand, { brand = it }, label = { Text(stringResource(R.string.addfood_field_brand)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        NumericTextField(kcal, { kcal = it }, label = stringResource(R.string.addfood_field_kcal), modifier = Modifier.fillMaxWidth())
        NumericTextField(protein, { protein = it }, label = stringResource(R.string.addfood_field_protein), modifier = Modifier.fillMaxWidth())
        NumericTextField(carbs, { carbs = it }, label = stringResource(R.string.addfood_field_carbs), modifier = Modifier.fillMaxWidth())
        NumericTextField(fat, { fat = it }, label = stringResource(R.string.addfood_field_fat), modifier = Modifier.fillMaxWidth())
        NumericTextField(sugar, { sugar = it }, label = stringResource(R.string.addfood_field_sugar), modifier = Modifier.fillMaxWidth())
        NumericTextField(fiber, { fiber = it }, label = stringResource(R.string.addfood_field_fiber), modifier = Modifier.fillMaxWidth())
        NumericTextField(salt, { salt = it }, label = stringResource(R.string.addfood_field_salt), modifier = Modifier.fillMaxWidth())
        TagSelector(selected = tag, onSelected = { tag = it })
    }
    Button(
        onClick = {
            onSave(
                Food(
                    id = initial?.id ?: "",
                    name = name.trim(),
                    brand = brand.trim().takeIf { it.isNotBlank() },
                    kcalPer100g = kcal.normalizeDecimal().toDouble(),
                    proteinPer100g = protein.normalizeDecimal().toDouble(),
                    carbsPer100g = carbs.normalizeDecimal().toDouble(),
                    fatPer100g = fat.normalizeDecimal().toDouble(),
                    sugarPer100g = sugar.normalizeDecimal().toDoubleOrNull() ?: 0.0,
                    fiberPer100g = fiber.normalizeDecimal().toDoubleOrNull() ?: 0.0,
                    saltPer100g = salt.normalizeDecimal().toDoubleOrNull() ?: 0.0,
                    tag = tag,
                )
            )
        },
        enabled = isValid,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
        shape = RoundedCornerShape(14.dp),
    ) { Text(stringResource(R.string.common_save), style = MaterialTheme.typography.labelLarge) }
}

/**
 * Amount entry for a single food: amount, meal-category chips, tag, then the macro preview and the
 * confirm button. Follows the tall-sheet shape — fully expanded, only the fields scroll, preview and
 * button pinned below them — so the button stays above the keyboard while the amount is typed.
 * As an ingredient pick ([forIngredient]) the category chips are left out: an ingredient is logged
 * with its meal, whose category is chosen then.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AmountSheet(
    food: Food,
    amount: String,
    mealCategory: MealCategory,
    tag: FoodTag,
    forIngredient: Boolean,
    onAmountChange: (String) -> Unit,
    onMealCategoryChange: (MealCategory) -> Unit,
    onTagChange: (FoodTag) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val amountGrams = amount.normalizeDecimal().toDoubleOrNull()?.takeIf { it > 0 }

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
                food.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.addfood_amount_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NumericTextField(
                        value = amount,
                        onValueChange = onAmountChange,
                        label = null,
                        suffix = "g",
                        textStyle = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (!forIngredient) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                selected = cat == mealCategory,
                                onClick = { onMealCategoryChange(cat) },
                                label = { Text(cat.displayName(), style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }

                TagSelector(selected = tag, onSelected = onTagChange)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (amountGrams != null) TotalsPill(FoodPortions.totals(listOf(SavedMealItem(food, amountGrams))))
                Button(
                    onClick = onConfirm,
                    enabled = amountGrams != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        stringResource(if (forIngredient) R.string.addfood_add_to_meal_button else R.string.addfood_add_button),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalDate.formatRelative(): String {
    val today = LocalDate.now()
    return when (this) {
        today -> stringResource(R.string.common_today)
        today.minusDays(1) -> stringResource(R.string.common_yesterday)
        else -> format(localizedDateFormatter("EEE, d. MMM"))
    }
}

private const val TAB_HISTORY = 0
private const val TAB_CUSTOM_FOODS = 1
private const val TAB_MEALS = 2

/** Back-stack result key: the name of a meal saved from the Overview, for its confirmation snackbar. */
internal const val SAVED_MEAL_NAME_RESULT = "savedMealName"

private fun BarcodeError.messageRes(): Int = when (this) {
    BarcodeError.PRODUCT_NOT_FOUND -> R.string.addfood_barcode_error_not_found
    BarcodeError.SERVER_UNAVAILABLE -> R.string.addfood_barcode_error_server_unavailable
    BarcodeError.NETWORK_UNAVAILABLE -> R.string.addfood_barcode_error_network_unavailable
    BarcodeError.UNKNOWN -> R.string.addfood_barcode_error_unknown
}
