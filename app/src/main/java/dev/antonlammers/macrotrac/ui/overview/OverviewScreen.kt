package dev.antonlammers.macrotrac.ui.overview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.macrotrac.domain.model.FoodEntry
import dev.antonlammers.macrotrac.domain.model.MealCategory
import dev.antonlammers.macrotrac.domain.model.WeightEntry
import dev.antonlammers.macrotrac.ui.components.NumericTextField
import dev.antonlammers.macrotrac.ui.navigation.Screen
import dev.antonlammers.macrotrac.util.normalizeDecimal
import dev.antonlammers.macrotrac.ui.theme.CalorieColor
import dev.antonlammers.macrotrac.ui.theme.CarbsColor
import dev.antonlammers.macrotrac.ui.theme.FatColor
import dev.antonlammers.macrotrac.ui.theme.ProteinColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    navController: NavController,
    viewModel: OverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IconButton(onClick = viewModel::previousDay) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Vorheriger Tag")
                        }
                        Text(
                            state.date.format(DateTimeFormatter.ofPattern("EEE, d. MMM", Locale("de"))),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(onClick = viewModel::nextDay) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Nächster Tag")
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
            FloatingActionButton(onClick = { navController.navigate(Screen.AddFood.withDate(state.date)) }) {
                Icon(Icons.Default.Add, contentDescription = "Mahlzeit hinzufügen")
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
            item { MacroSummaryCard(state = state) }
            item { WeightCard(weight = state.todayWeight, onSave = viewModel::saveWeight) }
            if (state.entries.isEmpty()) {
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
                val grouped = state.entries.groupBy { it.mealCategory }
                MealCategory.entries.forEach { category ->
                    val categoryEntries = grouped[category] ?: return@forEach
                    item(key = category.name) {
                        Text(
                            category.displayName(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(categoryEntries, key = { it.id }) { entry ->
                        var showEditDialog by remember { mutableStateOf(false) }
                        var amountInput by remember { mutableStateOf("") }
                        var selectedCategory by remember { mutableStateOf(entry.mealCategory) }

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
                                onDismiss = { showEditDialog = false },
                                onConfirm = { updated ->
                                    viewModel.update(updated)
                                    showEditDialog = false
                                },
                            )
                        }

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
                            FoodEntryRow(entry = entry)
                        }
                        HorizontalDivider()
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
    onDismiss: () -> Unit,
    onConfirm: (FoodEntry) -> Unit,
) {
    val newAmount = amountInput.normalizeDecimal().toDoubleOrNull() ?: 0.0
    val factor = if (entry.amountGrams > 0 && newAmount > 0) newAmount / entry.amountGrams else 1.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eintrag bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    buildString {
                        append(entry.foodName)
                        entry.brand?.let { append(" ($it)") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                NumericTextField(
                    value = amountInput,
                    onValueChange = onAmountChange,
                    label = "Menge",
                    suffix = "g",
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Mahlzeit", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                if (newAmount > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "${(entry.kcal * factor).toInt()} kcal · " +
                                "${(entry.proteinG * factor).toInt()}g P · " +
                                "${(entry.carbsG * factor).toInt()}g K · " +
                                "${(entry.fatG * factor).toInt()}g F",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amountInput.normalizeDecimal().toDoubleOrNull()?.let { it > 0 } == true,
                onClick = {
                    val newAmt = amountInput.normalizeDecimal().toDoubleOrNull() ?: return@TextButton
                    if (newAmt > 0) {
                        val f = newAmt / entry.amountGrams
                        onConfirm(
                            entry.copy(
                                amountGrams = newAmt,
                                kcal = entry.kcal * f,
                                proteinG = entry.proteinG * f,
                                carbsG = entry.carbsG * f,
                                fatG = entry.fatG * f,
                                sugarG = entry.sugarG * f,
                                fiberG = entry.fiberG * f,
                                mealCategory = selectedCategory,
                            )
                        )
                    }
                },
            ) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

@Composable
private fun MacroSummaryCard(state: OverviewUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            CalorieRing(current = state.totalKcal, goal = state.goal.kcal)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MacroBar("Protein", state.totalProtein, state.goal.proteinG, "g", ProteinColor)
                MacroBar("Kohlenhydrate", state.totalCarbs, state.goal.carbsG, "g", CarbsColor)
                MacroBar("Fett", state.totalFat, state.goal.fatG, "g", FatColor)
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
private fun CalorieRing(current: Double, goal: Double) {
    val progress = if (goal > 0) (current / goal).toFloat().coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 900),
        label = "calorie_ring",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 18.dp.toPx()
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
            if (animatedProgress > 0f) {
                drawArc(
                    color = CalorieColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${current.toInt()}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text("kcal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val remaining = goal - current
            if (remaining > 0) {
                Text(
                    "noch ${remaining.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (remaining < 0) {
                Text(
                    "+${(-remaining).toInt()} zuviel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text(
                    "Ziel erreicht",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun MacroBar(label: String, current: Double, goal: Double, unit: String, color: Color) {
    val progress = if (goal > 0) (current / goal).toFloat().coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 900),
        label = "${label}_bar",
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(
                "${current.toInt()} / ${goal.toInt()} $unit",
                style = MaterialTheme.typography.bodySmall,
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
        Text(
            "${value.toInt()} $unit",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Bearbeiten",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun FoodEntryRow(entry: FoodEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            buildString {
                append(entry.foodName)
                entry.brand?.let { append(" ($it)") }
                append(" · ${entry.amountGrams.toInt()} g")
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "${entry.kcal.toInt()} kcal · ${entry.proteinG.toInt()}g P · ${entry.carbsG.toInt()}g K · ${entry.fatG.toInt()}g F",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
