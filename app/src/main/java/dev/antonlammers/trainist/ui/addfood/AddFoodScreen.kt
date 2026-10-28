package dev.antonlammers.trainist.ui.addfood

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EggAlt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EggAlt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.FoodPortions
import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.domain.model.FoodTag
import dev.antonlammers.trainist.domain.model.SavedMeal
import dev.antonlammers.trainist.ui.components.displayName
import dev.antonlammers.trainist.ui.navigation.FloatingNavSnackbarOffset
import dev.antonlammers.trainist.ui.navigation.FloatingPillBar
import dev.antonlammers.trainist.ui.navigation.PillTab
import dev.antonlammers.trainist.ui.navigation.PrimaryAction
import dev.antonlammers.trainist.ui.navigation.Screen
import dev.antonlammers.trainist.ui.util.localizedDateFormatter
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Logging food: the user's history, own foods and saved meals as three lists switched by a floating
 * pill (the same bar as the app's tab navigation), one search over all of them plus Open Food Facts,
 * and a plus button that creates whatever fits the current list.
 *
 * While a saved meal's ingredient is being picked ([MealEditorUiState.picking]) the screen doubles as
 * the picker: the meals list steps aside (a meal can't be an ingredient) and nothing can be created.
 */
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
    val focusManager = LocalFocusManager.current

    var selectedTab by rememberSaveable { mutableStateOf(AddFoodTab.HISTORY) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    // The create sheet in food mode. Meal mode needs no flag of its own: it is open whenever the meal
    // editor holds a draft (unless the user switched the sheet over to food). The food form's values
    // live outside the sheet's content, so switching to "Mahlzeit" and back keeps what was typed.
    var showCreateFood by rememberSaveable { mutableStateOf(false) }
    var foodForm by remember { mutableStateOf<CustomFoodFormState?>(null) }
    var foodToEdit by remember { mutableStateOf<Food?>(null) }
    val picking = mealState.picking
    val searching = state.query.isNotBlank()

    // Resolved here (not inside the snackbar-launching lambdas below, which run in a coroutine
    // scope rather than a @Composable context, so stringResource() isn't callable there).
    val customFoodDeletedMessage = stringResource(R.string.addfood_custom_food_deleted)
    val entryDeletedMessage = stringResource(R.string.addfood_entry_deleted)
    val mealDeletedMessage = stringResource(R.string.addfood_saved_meal_deleted)
    val undoLabel = stringResource(R.string.common_undo)

    fun openCreateFood(prefillName: String = "") {
        menuOpen = false
        foodForm = CustomFoodFormState(prefillName = prefillName)
        showCreateFood = true
    }

    fun openCreateMeal() {
        menuOpen = false
        showCreateFood = false
        mealEditor.startNew()
    }

    fun <T> deleteWithUndo(item: T, message: String, pending: (T) -> Unit, undo: (T) -> Unit, confirm: (T) -> Unit) {
        pending(item)
        coroutineScope.launch {
            when (snackbar.showSnackbar(message = message, actionLabel = undoLabel, duration = SnackbarDuration.Short)) {
                SnackbarResult.ActionPerformed -> undo(item)
                SnackbarResult.Dismissed -> confirm(item)
            }
        }
    }

    val actions = ListActions(
        selectEntry = viewModel::selectRecentFood,
        deleteEntry = { deleteWithUndo(it, entryDeletedMessage, viewModel::deletePendingEntry, viewModel::undoDeleteEntry, viewModel::confirmDeleteEntry) },
        selectFood = viewModel::selectFood,
        editFood = { foodToEdit = it },
        deleteFood = {
            deleteWithUndo(it, customFoodDeletedMessage, viewModel::deletePendingCustomFood, viewModel::undoDeleteCustomFood, viewModel::confirmDeleteCustomFood)
        },
        selectMeal = viewModel::selectMeal,
        editMeal = mealEditor::edit,
        deleteMeal = { deleteWithUndo(it, mealDeletedMessage, viewModel::deletePendingMeal, viewModel::undoDeleteMeal, viewModel::confirmDeleteMeal) },
        createFood = if (picking) null else ::openCreateFood,
        createMeal = ::openCreateMeal,
        scanBarcode = { navController.navigate(Screen.BarcodeScanner.route) },
        retryOnline = viewModel::retryRemoteSearch,
    )

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
        foodForm = null
        when (saved.origin) {
            // Created via "+": most likely about to be eaten — offer to log it right away, like a
            // freshly created custom food.
            MealDraftOrigin.NEW -> {
                selectedTab = AddFoodTab.MEALS
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

    // Innermost first: an open menu, then a search, then the ingredient pick.
    BackHandler(enabled = menuOpen || state.query.isNotEmpty() || picking) {
        when {
            menuOpen -> menuOpen = false
            state.query.isNotEmpty() -> {
                viewModel.onQueryChange("")
                focusManager.clearFocus()
            }
            else -> mealEditor.cancelPicking()
        }
    }

    CreateSheets(
        showCreateFood = showCreateFood,
        foodForm = foodForm,
        mealState = mealState,
        hidden = picking || state.selectedFood != null || state.mealToLog != null,
        onKindChange = { kind ->
            showCreateFood = kind == CreateKind.FOOD
            if (kind == CreateKind.FOOD && foodForm == null) foodForm = CustomFoodFormState()
            if (kind == CreateKind.MEAL) mealEditor.startNew()
        },
        onDismiss = {
            showCreateFood = false
            foodForm = null
            val origin = mealState.draft?.origin
            mealEditor.discard()
            // Opened from the Overview's bookmark only to save that meal — cancelling goes back there.
            if (origin == MealDraftOrigin.FROM_DAY) navController.popBackStack()
        },
        onSaveFood = { food ->
            viewModel.saveCustomFood(food)
            showCreateFood = false
            foodForm = null
            mealEditor.discard()
        },
        mealEditor = mealEditor,
    )

    foodToEdit?.let { food ->
        val form = remember(food) { CustomFoodFormState(initial = food) }
        CreateSheet(
            kind = CreateKind.FOOD,
            kindSwitchable = false,
            title = stringResource(R.string.addfood_custom_food_dialog_title_edit),
            onKindChange = {},
            onDismiss = { foodToEdit = null },
        ) {
            CustomFoodForm(
                state = form,
                onSave = { updated ->
                    viewModel.updateCustomFood(updated)
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

    // While picking an ingredient, a meal can't be one — the meals list steps aside.
    val tabs = if (picking) listOf(AddFoodTab.HISTORY, AddFoodTab.FOODS) else AddFoodTab.entries
    val tab = if (selectedTab in tabs) selectedTab else AddFoodTab.HISTORY
    val sections = remember(localSearchResults, remoteSearch, picking) {
        SearchSections.of(localSearchResults, remoteSearch, includeMeals = !picking)
    }

    // A Surface rather than a plain background: without a Scaffold above it, it is what provides
    // `onBackground` as the content colour — otherwise every uncoloured Text falls back to black.
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                val mealName = mealState.draft?.name?.trim().orEmpty()
                AddFoodHeader(
                    title = when {
                        !picking -> stringResource(tab.titleRes())
                        mealName.isEmpty() -> stringResource(R.string.addfood_pick_ingredient_title_unnamed)
                        else -> stringResource(R.string.addfood_pick_ingredient_title, mealName)
                    },
                    subtitle = if (picking) {
                        stringResource(R.string.addfood_pick_ingredient_subtitle)
                    } else {
                        stringResource(R.string.addfood_subtitle, viewModel.targetDate.formatRelative(), state.mealCategory.displayName())
                    },
                    closeContentDescription = stringResource(
                        if (picking) R.string.addfood_pick_ingredient_cancel_content_description else R.string.addfood_close_content_description,
                    ),
                    onClose = { if (picking) mealEditor.cancelPicking() else navController.popBackStack() },
                )
                SearchRow(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    onScanClick = actions.scanBarcode,
                )
                if (searching && !state.isLoading) {
                    SourceChips(sections, state.searchSource, viewModel::onSearchSourceChange)
                }

                val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                // A fresh list per tab (and for the search), so each one starts at its top.
                key(searching, tab) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().imePadding(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LIST_BOTTOM_CLEARANCE + bottomInset),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when {
                            state.isLoading -> item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            searching -> searchItems(state.query, sections, state.searchSource, actions)
                            tab == AddFoodTab.HISTORY -> historyItems(historyDays(recentEntries), actions)
                            tab == AddFoodTab.FOODS -> foodItems(customFoods, actions)
                            else -> mealItems(savedMeals, actions)
                        }
                    }
                }
            }

            BottomFade()

            AddMenu(
                visible = menuOpen && !picking,
                items = listOf(
                    AddMenuItem(
                        Icons.Rounded.EggAlt,
                        stringResource(R.string.addfood_create_food),
                        stringResource(R.string.addfood_create_food_hint),
                    ) { openCreateFood() },
                    AddMenuItem(
                        Icons.Rounded.RestaurantMenu,
                        stringResource(R.string.addfood_create_meal),
                        stringResource(R.string.addfood_create_meal_hint),
                    ) { openCreateMeal() },
                ),
                onDismiss = { menuOpen = false },
            )

            val pillTabs = tabs.map { it.pillTab() }
            val actionDescription = stringResource(
                when {
                    menuOpen -> R.string.addfood_menu_close_content_description
                    tab == AddFoodTab.HISTORY -> R.string.addfood_create_content_description
                    tab == AddFoodTab.FOODS -> R.string.addfood_create_food
                    else -> R.string.addfood_create_meal
                },
            )
            val action = remember(actionDescription, tab, menuOpen) {
                PrimaryAction(actionDescription, tutorialTarget = null) {
                    when {
                        menuOpen -> menuOpen = false
                        tab == AddFoodTab.HISTORY -> menuOpen = true
                        tab == AddFoodTab.FOODS -> openCreateFood()
                        else -> openCreateMeal()
                    }
                }
            }
            FloatingPillBar(
                tabs = pillTabs,
                selectedIndex = tabs.indexOf(tab),
                onSelect = { index ->
                    menuOpen = false
                    selectedTab = tabs[index]
                },
                action = action,
                showsAction = !picking,
                actionIcon = if (menuOpen) Icons.Rounded.Close else Icons.Rounded.Add,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            SnackbarHost(
                snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = FloatingNavSnackbarOffset),
            )
        }
    }
}

/** The create sheet (food or meal) and the meal editor behind it. */
@Composable
private fun CreateSheets(
    showCreateFood: Boolean,
    foodForm: CustomFoodFormState?,
    mealState: MealEditorUiState,
    hidden: Boolean,
    onKindChange: (CreateKind) -> Unit,
    onDismiss: () -> Unit,
    onSaveFood: (Food) -> Unit,
    mealEditor: MealEditorViewModel,
) {
    val mealDraft = mealState.draft
    val kind = when {
        showCreateFood && foodForm != null -> CreateKind.FOOD
        mealDraft != null -> CreateKind.MEAL
        else -> return
    }
    if (hidden) return
    CreateSheet(
        kind = kind,
        kindSwitchable = mealDraft == null || mealDraft.origin == MealDraftOrigin.NEW,
        title = stringResource(
            when {
                kind == CreateKind.FOOD -> R.string.addfood_custom_food_dialog_title_new
                mealDraft?.origin == MealDraftOrigin.EDIT -> R.string.addfood_meal_dialog_title_edit
                else -> R.string.addfood_meal_dialog_title_new
            },
        ),
        onKindChange = onKindChange,
        onDismiss = onDismiss,
    ) {
        if (kind == CreateKind.FOOD && foodForm != null) {
            CustomFoodForm(state = foodForm, onSave = onSaveFood)
        } else if (mealDraft != null) {
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

/** Everything a list row can do, handed down to the list builders below. */
private class ListActions(
    val selectEntry: (FoodEntry) -> Unit,
    val deleteEntry: (FoodEntry) -> Unit,
    val selectFood: (Food) -> Unit,
    val editFood: (Food) -> Unit,
    val deleteFood: (Food) -> Unit,
    val selectMeal: (SavedMeal) -> Unit,
    val editMeal: (SavedMeal) -> Unit,
    val deleteMeal: (SavedMeal) -> Unit,
    /** Null while picking an ingredient — nothing can be created then. */
    val createFood: ((String) -> Unit)?,
    val createMeal: () -> Unit,
    val scanBarcode: () -> Unit,
    val retryOnline: () -> Unit,
)

private fun LazyListScope.historyItems(days: List<HistoryDay>, actions: ListActions) {
    if (days.isEmpty()) {
        item(key = "empty") {
            EmptyState(
                icon = Icons.Rounded.History,
                title = stringResource(R.string.addfood_empty_history_title),
            )
        }
        return
    }
    days.forEach { day ->
        item(key = "day_${day.date}") {
            DayHeader(day.date.formatRelative(), stringResource(R.string.addfood_day_kcal, day.kcal.toInt()))
        }
        items(day.entries, key = { "history_${it.id}" }) { entry ->
            SwipeActions(shape = CardShape, onDelete = { actions.deleteEntry(entry) }) {
                FoodCard(
                    tag = entry.tag,
                    title = foodTitle(
                        name = buildString {
                            append(entry.foodName)
                            entry.brand?.let { append(" ($it)") }
                            append(" · ${entry.amountGrams.formatAmount()} g")
                        },
                        secondary = null,
                    ),
                    detail = stringResource(
                        R.string.addfood_food_detail_total,
                        entry.kcal.toInt(),
                        entry.proteinG.toInt(),
                        entry.carbsG.toInt(),
                        entry.fatG.toInt(),
                    ),
                    onClick = { actions.selectEntry(entry) },
                )
            }
        }
    }
}

private fun LazyListScope.foodItems(foods: List<Food>, actions: ListActions) {
    if (foods.isEmpty()) {
        item(key = "empty") {
            val create = actions.createFood
            EmptyState(
                icon = Icons.Rounded.EggAlt,
                title = stringResource(R.string.addfood_empty_custom_foods_title),
                text = stringResource(R.string.addfood_empty_custom_foods),
                action = create?.let { c -> stringResource(R.string.addfood_create_food) to { c("") } },
                secondary = Triple(Icons.Rounded.QrCodeScanner, stringResource(R.string.addfood_empty_scan_barcode), actions.scanBarcode),
            )
        }
        return
    }
    item(key = "caption") {
        ListCaption(
            pluralStringResource(R.plurals.addfood_foods_header, foods.size, foods.size),
            stringResource(R.string.addfood_sort_alphabetical),
        )
    }
    items(foods, key = { "custom_${it.id}" }) { food ->
        CustomFoodCard(food, highlight = null, detailRes = R.string.addfood_food_detail_compact, actions = actions)
    }
}

private fun LazyListScope.mealItems(meals: List<SavedMeal>, actions: ListActions) {
    if (meals.isEmpty()) {
        item(key = "empty") {
            EmptyState(
                icon = Icons.Rounded.RestaurantMenu,
                title = stringResource(R.string.addfood_empty_saved_meals_title),
                text = stringResource(R.string.addfood_empty_saved_meals),
                action = stringResource(R.string.addfood_create_meal) to actions.createMeal,
            )
        }
        return
    }
    item(key = "caption") {
        ListCaption(
            pluralStringResource(R.plurals.addfood_meals_header, meals.size, meals.size),
            stringResource(R.string.addfood_sort_recent),
        )
    }
    items(meals, key = { "meal_${it.id}" }) { meal ->
        SwipeActions(shape = MealCardShape, onDelete = { actions.deleteMeal(meal) }, onEdit = { actions.editMeal(meal) }) {
            MealCard(
                name = meal.name,
                ingredients = meal.items.joinToString { it.food.name },
                totals = FoodPortions.totals(meal.items),
                onClick = { actions.selectMeal(meal) },
                onEdit = { actions.editMeal(meal) },
                onDelete = { actions.deleteMeal(meal) },
            )
        }
    }
}

private fun LazyListScope.searchItems(query: String, sections: SearchSections, filter: SearchSource, actions: ListActions) {
    if (sections.isEmpty(filter)) {
        item(key = "empty") { EmptyHint(stringResource(R.string.addfood_empty_search_results)) }
    }

    if (sections.shows(SearchSource.MINE, filter) && sections.mine.isNotEmpty()) {
        item(key = "group_mine") {
            SearchGroupHeader(Icons.Outlined.Person, stringResource(R.string.addfood_group_mine), sections.mine.size)
        }
        items(
            sections.mine,
            key = { result ->
                when (result) {
                    is LocalSearchResult.SavedMealResult -> "search_meal_${result.meal.id}"
                    is LocalSearchResult.CustomFoodResult -> "search_custom_${result.food.id}"
                    is LocalSearchResult.HistoryResult -> "search_history_${result.entry.id}"
                }
            },
        ) { result ->
            when (result) {
                is LocalSearchResult.SavedMealResult -> {
                    val meal = result.meal
                    SwipeActions(shape = CardShape, onDelete = { actions.deleteMeal(meal) }, onEdit = { actions.editMeal(meal) }) {
                        FoodCard(
                            tag = FoodTag.NONE,
                            title = foodTitle(meal.name, stringResource(R.string.addfood_search_meal_label), matchRange(meal.name, query)),
                            detail = compactDetail(FoodPortions.totals(meal.items)),
                            onClick = { actions.selectMeal(meal) },
                            trailing = { TrailingGlyph(Icons.Outlined.RestaurantMenu) },
                        )
                    }
                }
                is LocalSearchResult.CustomFoodResult -> CustomFoodCard(
                    food = result.food,
                    highlight = matchRange(result.food.name, query),
                    detailRes = R.string.addfood_food_detail_compact_per_100g,
                    actions = actions,
                )
                is LocalSearchResult.HistoryResult -> Unit
            }
        }
    }

    if (sections.shows(SearchSource.HISTORY, filter) && sections.history.isNotEmpty()) {
        item(key = "group_history") {
            SearchGroupHeader(Icons.Outlined.History, stringResource(R.string.addfood_group_history), sections.history.size)
        }
        items(sections.history, key = { "search_history_${it.entry.id}" }) { result ->
            val entry = result.entry
            SwipeActions(shape = CardShape, onDelete = { actions.deleteEntry(entry) }) {
                FoodCard(
                    tag = entry.tag,
                    title = foodTitle(entry.foodName, entry.brand, matchRange(entry.foodName, query)),
                    detail = per100Detail(FoodPortions.per100g(entry), R.string.addfood_food_detail_compact_per_100g),
                    onClick = { actions.selectEntry(entry) },
                    trailing = { TrailingGlyph(Icons.Outlined.History) },
                )
            }
        }
    }

    // Open Food Facts below whatever the user has logged before — their own foods and history are
    // always the better match, so they stay on top.
    val online = sections.online
    if (sections.shows(SearchSource.OPEN_FOOD_FACTS, filter) && (online.isLoading || online.failed || online.results.isNotEmpty())) {
        item(key = "group_online") {
            SearchGroupHeader(
                Icons.Outlined.Public,
                stringResource(R.string.addfood_group_open_food_facts),
                sections.count(SearchSource.OPEN_FOOD_FACTS).takeUnless { online.failed },
            )
        }
        when {
            online.isLoading -> items(SKELETON_COUNT, key = { "skeleton_$it" }) { SkeletonCard() }
            online.failed -> item(key = "online_failed") { OnlineSearchFailed(actions.retryOnline) }
            else -> items(online.results, key = { "off_${it.id}" }) { food ->
                FoodCard(
                    tag = food.tag,
                    title = foodTitle(food.name, food.brand, matchRange(food.name, query)),
                    detail = per100Detail(food, R.string.addfood_food_detail_compact_per_100g),
                    onClick = { actions.selectFood(food) },
                    trailing = { TrailingGlyph(Icons.Rounded.ChevronRight, size = 20.dp) },
                )
            }
        }
    }

    actions.createFood?.let { create ->
        item(key = "create_from_query") {
            val name = query.trim()
            AccentTextAction(stringResource(R.string.addfood_create_from_query, name)) { create(name) }
        }
    }
}

/** A custom food: swipe or ⋮ to edit/delete, tap to log. */
@Composable
private fun CustomFoodCard(food: Food, highlight: IntRange?, detailRes: Int, actions: ListActions) {
    SwipeActions(shape = CardShape, onDelete = { actions.deleteFood(food) }, onEdit = { actions.editFood(food) }) {
        FoodCard(
            tag = food.tag,
            title = foodTitle(food.name, food.brand, highlight),
            detail = per100Detail(food, detailRes),
            onClick = { actions.selectFood(food) },
            trailing = { ItemMenuButton(onEdit = { actions.editFood(food) }, onDelete = { actions.deleteFood(food) }) },
        )
    }
}

/** A food's per-100 g values in one of the compact detail formats. */
@Composable
private fun per100Detail(food: Food, detailRes: Int): String = stringResource(
    detailRes,
    food.kcalPer100g.toInt(),
    food.proteinPer100g.toInt(),
    food.carbsPer100g.toInt(),
    food.fatPer100g.toInt(),
)

private fun AddFoodTab.titleRes(): Int = when (this) {
    AddFoodTab.HISTORY -> R.string.addfood_tab_history
    AddFoodTab.FOODS -> R.string.addfood_tab_custom_foods
    AddFoodTab.MEALS -> R.string.addfood_tab_saved_meals
}

@Composable
private fun AddFoodTab.pillTab(): PillTab = when (this) {
    AddFoodTab.HISTORY -> PillTab(stringResource(R.string.addfood_tab_history), Icons.Rounded.History, Icons.Outlined.History)
    AddFoodTab.FOODS -> PillTab(stringResource(R.string.addfood_pill_foods), Icons.Rounded.EggAlt, Icons.Outlined.EggAlt)
    AddFoodTab.MEALS -> PillTab(stringResource(R.string.addfood_pill_meals), Icons.Rounded.RestaurantMenu, Icons.Outlined.RestaurantMenu)
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

/** Room below the last list item for the floating bar and the fade above it. */
private val LIST_BOTTOM_CLEARANCE = 120.dp

/** Placeholder cards while Open Food Facts answers. */
private const val SKELETON_COUNT = 3

/** Back-stack result key: the name of a meal saved from the Overview, for its confirmation snackbar. */
internal const val SAVED_MEAL_NAME_RESULT = "savedMealName"

private fun BarcodeError.messageRes(): Int = when (this) {
    BarcodeError.PRODUCT_NOT_FOUND -> R.string.addfood_barcode_error_not_found
    BarcodeError.SERVER_UNAVAILABLE -> R.string.addfood_barcode_error_server_unavailable
    BarcodeError.NETWORK_UNAVAILABLE -> R.string.addfood_barcode_error_network_unavailable
    BarcodeError.UNKNOWN -> R.string.addfood_barcode_error_unknown
}
