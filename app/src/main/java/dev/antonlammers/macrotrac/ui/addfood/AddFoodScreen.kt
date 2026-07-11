package dev.antonlammers.macrotrac.ui.addfood

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.macrotrac.domain.model.Food
import dev.antonlammers.macrotrac.domain.model.FoodEntry
import dev.antonlammers.macrotrac.domain.model.FoodTag
import dev.antonlammers.macrotrac.domain.model.MealCategory
import dev.antonlammers.macrotrac.ui.components.NumericTextField
import dev.antonlammers.macrotrac.ui.components.TagDot
import dev.antonlammers.macrotrac.ui.components.TagSelector
import dev.antonlammers.macrotrac.ui.navigation.Screen
import dev.antonlammers.macrotrac.util.normalizeDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(
    navController: NavController,
    viewModel: AddFoodViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recentEntries by viewModel.recentEntries.collectAsStateWithLifecycle()
    val customFoods by viewModel.customFoods.collectAsStateWithLifecycle()
    val localSearchResults by viewModel.localSearchResults.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showCreateDialog by remember { mutableStateOf(false) }
    var foodToEdit by remember { mutableStateOf<Food?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

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

    if (showCreateDialog) {
        CustomFoodDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { food ->
                viewModel.saveCustomFood(food)
                showCreateDialog = false
            },
        )
    }

    foodToEdit?.let { food ->
        CustomFoodDialog(
            initial = food,
            onDismiss = { foodToEdit = null },
            onSave = { updated ->
                viewModel.updateCustomFood(updated.copy(id = food.id))
                foodToEdit = null
            },
        )
    }

    state.error?.let { errorMsg ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Barcode-Suche") },
            text = { Text(errorMsg) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text("OK") }
            },
        )
    }

    state.selectedFood?.let { food ->
        AmountSheet(
            food = food,
            amount = state.amountGrams,
            mealCategory = state.mealCategory,
            tag = state.tag,
            onAmountChange = viewModel::onAmountChange,
            onMealCategoryChange = viewModel::onMealCategoryChange,
            onTagChange = viewModel::onTagChange,
            onConfirm = viewModel::confirmAdd,
            onDismiss = viewModel::dismissSelection,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val isToday = viewModel.targetDate == LocalDate.now()
                    if (isToday) {
                        Text("Mahlzeit hinzufügen")
                    } else {
                        Text("Eintrag für ${viewModel.targetDate.formatRelative()}")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Eigenes Lebensmittel")
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

            if (state.query.isEmpty() && !state.isLoading) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    MonoTab("Verlauf", selectedTab == 0) { selectedTab = 0 }
                    MonoTab("Meine Lebensmittel", selectedTab == 1) { selectedTab = 1 }
                }
            }

            LazyColumn {
                // Empty state: active tab content
                if (state.query.isEmpty() && !state.isLoading) {
                    // Tab 0 = Verlauf (default), Tab 1 = Meine Lebensmittel.
                    if (selectedTab == 1) {
                        if (customFoods.isEmpty()) {
                            item {
                                EmptyHint("Noch keine eigenen Lebensmittel.\nTippe auf + um eines anzulegen.")
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
                                                message = "Lebensmittel gelöscht",
                                                actionLabel = "Rückgängig",
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
                            item { EmptyHint("Noch keine Einträge im Verlauf.") }
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
                                                    message = "Eintrag gelöscht",
                                                    actionLabel = "Rückgängig",
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
                    if (localSearchResults.isEmpty()) {
                        item { EmptyHint("Keine Ergebnisse") }
                    } else {
                        items(
                            localSearchResults,
                            key = { result ->
                                when (result) {
                                    is LocalSearchResult.CustomFoodResult -> "search_custom_${result.food.id}"
                                    is LocalSearchResult.HistoryResult -> "search_history_${result.entry.id}"
                                }
                            },
                        ) { result ->
                            when (result) {
                                is LocalSearchResult.CustomFoodResult -> SwipeableCustomFoodRow(
                                    food = result.food,
                                    onClick = { viewModel.selectFood(result.food) },
                                    onEdit = { foodToEdit = result.food },
                                    onDelete = {
                                        viewModel.deletePendingCustomFood(result.food)
                                        coroutineScope.launch {
                                            val r = snackbar.showSnackbar(
                                                message = "Lebensmittel gelöscht",
                                                actionLabel = "Rückgängig",
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
                                                message = "Eintrag gelöscht",
                                                actionLabel = "Rückgängig",
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
            placeholder = { Text("Lebensmittel suchen") },
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
                contentDescription = "Barcode scannen",
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
            label.uppercase(Locale("de")),
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
        label.uppercase(Locale("de")),
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
            detail = "${food.kcalPer100g.toInt()} kcal · ${food.proteinPer100g.toInt()}g P · " +
                "${food.carbsPer100g.toInt()}g K · ${food.fatPer100g.toInt()}g F · pro 100 g",
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
            detail = "${entry.kcal.toInt()} kcal · ${entry.proteinG.toInt()}g P · " +
                "${entry.carbsG.toInt()}g K · ${entry.fatG.toInt()}g F",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomFoodDialog(
    initial: Food? = null,
    onDismiss: () -> Unit,
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
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (initial == null) "Neues Lebensmittel" else "Lebensmittel bearbeiten",
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(name, { name = it }, label = { Text("Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(brand, { brand = it }, label = { Text("Marke (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            NumericTextField(kcal, { kcal = it }, label = "kcal / 100 g *", modifier = Modifier.fillMaxWidth())
            NumericTextField(protein, { protein = it }, label = "Protein g / 100 g *", modifier = Modifier.fillMaxWidth())
            NumericTextField(carbs, { carbs = it }, label = "Kohlenhydrate g / 100 g *", modifier = Modifier.fillMaxWidth())
            NumericTextField(fat, { fat = it }, label = "Fett g / 100 g *", modifier = Modifier.fillMaxWidth())
            NumericTextField(sugar, { sugar = it }, label = "Zucker g / 100 g", modifier = Modifier.fillMaxWidth())
            NumericTextField(fiber, { fiber = it }, label = "Ballaststoffe g / 100 g", modifier = Modifier.fillMaxWidth())
            NumericTextField(salt, { salt = it }, label = "Salz g / 100 g", modifier = Modifier.fillMaxWidth())
            TagSelector(selected = tag, onSelected = { tag = it })
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
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Speichern", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Amount entry — a bottom sheet (28dp top radius, scrim) with the same fields and
// flow as before: amount, meal chips, tag selector, macro preview, confirm button.
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AmountSheet(
    food: Food,
    amount: String,
    mealCategory: MealCategory,
    tag: FoodTag,
    onAmountChange: (String) -> Unit,
    onMealCategoryChange: (MealCategory) -> Unit,
    onTagChange: (FoodTag) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val amt = amount.normalizeDecimal().toDoubleOrNull() ?: 0.0
    val factor = amt / 100.0

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
            Text(food.name, style = MaterialTheme.typography.titleLarge)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "MENGE",
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
                            selected = cat == mealCategory,
                            onClick = { onMealCategoryChange(cat) },
                            label = { Text(cat.displayName(), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }

            TagSelector(selected = tag, onSelected = onTagChange)

            if (amt > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${(food.kcalPer100g * factor).toInt()} kcal · " +
                            "${(food.proteinPer100g * factor).toInt()}g P · " +
                            "${(food.carbsPer100g * factor).toInt()}g K · " +
                            "${(food.fatPer100g * factor).toInt()}g F",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = onConfirm,
                enabled = amt > 0,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Hinzufügen", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun LocalDate.formatRelative(): String {
    val today = LocalDate.now()
    return when (this) {
        today -> "Heute"
        today.minusDays(1) -> "Gestern"
        else -> format(DateTimeFormatter.ofPattern("EEE, d. MMM", Locale("de")))
    }
}

private fun MealCategory.displayName() = when (this) {
    MealCategory.BREAKFAST -> "Frühstück"
    MealCategory.LUNCH -> "Mittagessen"
    MealCategory.DINNER -> "Abendessen"
    MealCategory.SNACK -> "Snack"
}
