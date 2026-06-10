package dev.antonlammers.macrotrac.ui.addfood

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.macrotrac.domain.model.Food
import dev.antonlammers.macrotrac.domain.model.FoodEntry
import dev.antonlammers.macrotrac.domain.model.MealCategory
import dev.antonlammers.macrotrac.ui.components.NumericTextField
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
    var selectedTab by remember { mutableStateOf(0) }

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
        AmountDialog(
            food = food,
            amount = state.amountGrams,
            mealCategory = state.mealCategory,
            onAmountChange = viewModel::onAmountChange,
            onMealCategoryChange = viewModel::onMealCategoryChange,
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Eigenes Lebensmittel")
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
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Lebensmittel suchen…") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { navController.navigate(Screen.BarcodeScanner.route) }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Barcode scannen")
                    }
                },
                keyboardOptions = KeyboardOptions.Default,
            )

            if (state.query.isEmpty() && !state.isLoading) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Meine Lebensmittel") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Verlauf") },
                    )
                }
            }

            LazyColumn {
                // Empty state: active tab content
                if (state.query.isEmpty() && !state.isLoading) {
                    if (selectedTab == 0) {
                        if (customFoods.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "Noch keine eigenen Lebensmittel.\nTippe auf + um eines anzulegen.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                }
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
                                HorizontalDivider()
                            }
                        }
                    } else {
                        if (recentEntries.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "Noch keine Einträge im Verlauf.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            val grouped = recentEntries
                                .groupBy { it.date }
                                .entries
                                .sortedByDescending { it.key }
                            grouped.forEach { (date, entries) ->
                                item(key = "header_$date") {
                                    Text(
                                        date.formatRelative(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
                                    )
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
                                    HorizontalDivider()
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
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "Keine Ergebnisse",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
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
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
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
                        Icons.Default.Delete,
                        contentDescription = "Löschen",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    SwipeToDismissBoxValue.StartToEnd -> Icon(
                        Icons.Default.Edit,
                        contentDescription = "Bearbeiten",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    else -> {}
                }
            }
        },
    ) {
        CustomFoodRow(food = food, onClick = onClick)
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
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Löschen",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        RecentFoodRow(entry = entry, onClick = onClick)
    }
}

@Composable
private fun CustomFoodRow(food: Food, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            buildString {
                append(food.name)
                food.brand?.let { append(" ($it)") }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "${food.kcalPer100g.toInt()} kcal · ${food.proteinPer100g.toInt()}g P · ${food.carbsPer100g.toInt()}g K · ${food.fatPer100g.toInt()}g F (pro 100 g)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentFoodRow(entry: FoodEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            buildString {
                append(entry.foodName)
                entry.brand?.let { append(" ($it)") }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            buildString {
                append("${entry.kcal.toInt()} kcal")
                append(" · ${entry.amountGrams.toInt()} g")
                append(" · ${entry.proteinG.toInt()}g P")
                append(" · ${entry.carbsG.toInt()}g K")
                append(" · ${entry.fatG.toInt()}g F")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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

    val isValid = name.isNotBlank()
        && kcal.normalizeDecimal().toDoubleOrNull() != null
        && protein.normalizeDecimal().toDoubleOrNull() != null
        && carbs.normalizeDecimal().toDoubleOrNull() != null
        && fat.normalizeDecimal().toDoubleOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Neues Lebensmittel" else "Lebensmittel bearbeiten") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(brand, { brand = it }, label = { Text("Marke (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                NumericTextField(kcal, { kcal = it }, label = "kcal / 100 g *", modifier = Modifier.fillMaxWidth())
                NumericTextField(protein, { protein = it }, label = "Protein g / 100 g *", modifier = Modifier.fillMaxWidth())
                NumericTextField(carbs, { carbs = it }, label = "Kohlenhydrate g / 100 g *", modifier = Modifier.fillMaxWidth())
                NumericTextField(fat, { fat = it }, label = "Fett g / 100 g *", modifier = Modifier.fillMaxWidth())
                NumericTextField(sugar, { sugar = it }, label = "Zucker g / 100 g", modifier = Modifier.fillMaxWidth())
                NumericTextField(fiber, { fiber = it }, label = "Ballaststoffe g / 100 g", modifier = Modifier.fillMaxWidth())
                NumericTextField(salt, { salt = it }, label = "Salz g / 100 g", modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
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
                        )
                    )
                },
                enabled = isValid,
            ) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmountDialog(
    food: Food,
    amount: String,
    mealCategory: MealCategory,
    onAmountChange: (String) -> Unit,
    onMealCategoryChange: (MealCategory) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NumericTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    label = "Menge (g)",
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Mahlzeit", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = cat == mealCategory,
                            onClick = { onMealCategoryChange(cat) },
                            label = { Text(cat.displayName()) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Hinzufügen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
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
