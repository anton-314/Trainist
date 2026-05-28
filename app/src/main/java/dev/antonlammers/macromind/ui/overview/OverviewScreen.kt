package dev.antonlammers.macromind.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.macromind.domain.model.FoodEntry
import dev.antonlammers.macromind.ui.navigation.Screen
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    navController: NavController,
    viewModel: OverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale("de"))))
                },
                actions = {
                    TextButton(onClick = { navController.navigate(Screen.Goals.route) }) {
                        Text("Ziele")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.AddFood.route) }) {
                Icon(Icons.Default.Add, contentDescription = "Mahlzeit hinzufügen")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { MacroSummaryCard(state = state) }
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
                items(state.entries, key = { it.id }) { entry ->
                    FoodEntryRow(entry = entry, onDelete = { viewModel.delete(entry.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun MacroSummaryCard(state: OverviewUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MacroRow("Kalorien", state.totalKcal, state.goal.kcal, "kcal")
            MacroRow("Protein", state.totalProtein, state.goal.proteinG, "g")
            MacroRow("Kohlenhydrate", state.totalCarbs, state.goal.carbsG, "g")
            MacroRow("Fett", state.totalFat, state.goal.fatG, "g")
        }
    }
}

@Composable
private fun MacroRow(label: String, current: Double, goal: Double, unit: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            "${current.toInt()} / ${goal.toInt()} $unit",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun FoodEntryRow(entry: FoodEntry, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Löschen")
        }
    }
}
