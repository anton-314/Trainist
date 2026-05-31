package dev.antonlammers.macrotrac.ui.stats

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.macrotrac.ui.data.DataViewModel
import dev.antonlammers.macrotrac.ui.theme.CalorieColor
import dev.antonlammers.macrotrac.ui.theme.ProteinColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    navController: NavController,
    statsViewModel: StatsViewModel = hiltViewModel(),
    dataViewModel: DataViewModel = hiltViewModel(),
) {
    val state by statsViewModel.uiState.collectAsStateWithLifecycle()
    val dataState by dataViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(dataState.message) {
        dataState.message?.let {
            snackbar.showSnackbar(it)
            dataViewModel.clearMessage()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { dataViewModel.import(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistik") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Time range selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeRange.entries.forEach { range ->
                    FilterChip(
                        selected = state.timeRange == range,
                        onClick = { statsViewModel.setTimeRange(range) },
                        label = { Text(range.label) },
                    )
                }
            }

            // Calorie chart
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Kalorien", style = MaterialTheme.typography.titleSmall)
                    if (state.caloriePoints.isEmpty() || state.caloriePoints.all { it.value == 0.0 }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Noch keine Einträge",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        CalorieBarChart(
                            points = state.caloriePoints,
                            goalKcal = state.goalKcal,
                            barColor = CalorieColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            goalColor = MaterialTheme.colorScheme.primary,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Weight chart
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gewicht", style = MaterialTheme.typography.titleSmall)
                    if (state.weightPoints.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Noch keine Gewichtsdaten",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        WeightLineChart(
                            points = state.weightPoints,
                            lineColor = ProteinColor,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // CSV section
            HorizontalDivider()
            Text("Daten", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (dataState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Button(
                onClick = {
                    dataViewModel.export { uri ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Exportieren via"))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !dataState.isLoading,
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Vollständiges Backup exportieren")
            }

            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/zip", "text/csv", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !dataState.isLoading,
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Backup importieren")
            }
        }
    }
}

@Composable
private fun CalorieBarChart(
    points: List<ChartPoint>,
    goalKcal: Double,
    barColor: Color,
    trackColor: Color,
    goalColor: Color,
    labelColor: Color,
) {
    val maxValue = maxOf(points.maxOf { it.value }, goalKcal, 1.0).toFloat()
    val labelStep = when {
        points.size <= 8 -> 1
        points.size <= 16 -> 2
        else -> (points.size / 6).coerceAtLeast(1)
    }

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            val barWidth = size.width / points.size
            val pad = (barWidth * 0.12f).coerceAtLeast(1.5.dp.toPx())

            points.forEachIndexed { i, point ->
                val fillFraction = (point.value.toFloat() / maxValue).coerceIn(0f, 1f)
                val fillHeight = fillFraction * size.height
                val x = i * barWidth

                drawRect(trackColor, Offset(x + pad, 0f), Size(barWidth - pad * 2, size.height))
                if (fillHeight > 0f) {
                    drawRect(barColor, Offset(x + pad, size.height - fillHeight), Size(barWidth - pad * 2, fillHeight))
                }
            }

            if (goalKcal > 0f) {
                val goalY = size.height - (goalKcal.toFloat() / maxValue * size.height).coerceIn(0f, size.height)
                drawLine(
                    goalColor,
                    Offset(0f, goalY),
                    Offset(size.width, goalY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEachIndexed { i, point ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (i % labelStep == 0) {
                        Text(
                            point.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightLineChart(
    points: List<ChartPoint>,
    lineColor: Color,
    labelColor: Color,
) {
    val minValue = (points.minOf { it.value } - 1.0).toFloat().coerceAtLeast(0f)
    val maxValue = (points.maxOf { it.value } + 1.0).toFloat()
    val range = (maxValue - minValue).takeIf { it > 0f } ?: 1f
    val labelStep = when {
        points.size <= 8 -> 1
        else -> (points.size / 5).coerceAtLeast(1)
    }

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            fun xFor(i: Int) = if (points.size > 1) i * size.width / (points.size - 1).toFloat() else size.width / 2f
            fun yFor(v: Double) = size.height - ((v.toFloat() - minValue) / range * size.height)

            if (points.size >= 2) {
                val path = Path()
                path.moveTo(xFor(0), yFor(points[0].value))
                for (i in 1 until points.size) {
                    path.lineTo(xFor(i), yFor(points[i].value))
                }
                drawPath(
                    path,
                    lineColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            points.forEachIndexed { i, point ->
                val cx = xFor(i)
                val cy = yFor(point.value)
                drawCircle(lineColor, 5.dp.toPx(), Offset(cx, cy))
                drawCircle(Color.White, 2.5.dp.toPx(), Offset(cx, cy))
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEachIndexed { i, point ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (i % labelStep == 0) {
                        Text(
                            point.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
