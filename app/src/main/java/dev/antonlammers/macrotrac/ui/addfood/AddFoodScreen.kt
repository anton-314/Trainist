package dev.antonlammers.macrotrac.ui.addfood

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.macrotrac.domain.model.Food
import dev.antonlammers.macrotrac.domain.model.FoodEntry
import dev.antonlammers.macrotrac.domain.model.MealCategory
import dev.antonlammers.macrotrac.ui.navigation.Screen
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    var showCreateDialog by remember { mutableStateOf(false) }

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
        CreateCustomFoodDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { food ->
                viewModel.saveCustomFood(food)
                showCreateDialog = false
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

            LazyColumn {
                // Empty state: custom foods + history grouped by date
                if (state.query.isEmpty() && !state.isLoading) {
                    if (customFoods.isNotEmpty()) {
                        item { SectionLabel("Meine Lebensmittel") }
                        items(customFoods, key = { "custom_${it.id}" }) { food ->
                            CustomFoodRow(
                                food = food,
                                onClick = { viewModel.selectFood(food) },
                                onDelete = { viewModel.deleteCustomFood(food) },
                            )
                            HorizontalDivider()
                        }
                    }
                    if (recentEntries.isNotEmpty()) {
                        item { SectionLabel("Verlauf") }
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
                                RecentFoodRow(entry = entry, onClick = { viewModel.selectRecentFood(entry) })
                                HorizontalDivider()
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

                // Error (barcode)
                if (state.error != null) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                state.error!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
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
                                is LocalSearchResult.CustomFoodResult -> CustomFoodRow(
                                    food = result.food,
                                    onClick = { viewModel.selectFood(result.food) },
                                    onDelete = { viewModel.deleteCustomFood(result.food) },
                                )
                                is LocalSearchResult.HistoryResult -> RecentFoodRow(
                                    entry = result.entry,
                                    onClick = { viewModel.selectRecentFood(result.entry) },
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun CustomFoodRow(food: Food, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Löschen",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentFoodRow(entry: FoodEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
private fun CreateCustomFoodDialog(onDismiss: () -> Unit, onSave: (Food) -> Unit) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var sugar by remember { mutableStateOf("") }
    var fiber by remember { mutableStateOf("") }

    val isValid = name.isNotBlank()
        && kcal.toDoubleOrNull() != null
        && protein.toDoubleOrNull() != null
        && carbs.toDoubleOrNull() != null
        && fat.toDoubleOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neues Lebensmittel") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val decimalKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                OutlinedTextField(name, { name = it }, label = { Text("Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(brand, { brand = it }, label = { Text("Marke (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(kcal, { kcal = it }, label = { Text("kcal / 100 g *") }, singleLine = true, keyboardOptions = decimalKeyboard, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(protein, { protein = it }, label = { Text("Protein g / 100 g *") }, singleLine = true, keyboardOptions = decimalKeyboard, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(carbs, { carbs = it }, label = { Text("Kohlenhydrate g / 100 g *") }, singleLine = true, keyboardOptions = decimalKeyboard, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(fat, { fat = it }, label = { Text("Fett g / 100 g *") }, singleLine = true, keyboardOptions = decimalKeyboard, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sugar, { sugar = it }, label = { Text("Zucker g / 100 g") }, singleLine = true, keyboardOptions = decimalKeyboard, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(fiber, { fiber = it }, label = { Text("Ballaststoffe g / 100 g") }, singleLine = true, keyboardOptions = decimalKeyboard, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        Food(
                            id = "",
                            name = name.trim(),
                            brand = brand.trim().takeIf { it.isNotBlank() },
                            kcalPer100g = kcal.toDouble(),
                            proteinPer100g = protein.toDouble(),
                            carbsPer100g = carbs.toDouble(),
                            fatPer100g = fat.toDouble(),
                            sugarPer100g = sugar.toDoubleOrNull() ?: 0.0,
                            fiberPer100g = fiber.toDoubleOrNull() ?: 0.0,
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
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    label = { Text("Menge (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
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
