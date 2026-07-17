package dev.antonlammers.trainist.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.android.EntryPointAccessors
import dev.antonlammers.trainist.MainActivity
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.di.WidgetEntryPoint
import dev.antonlammers.trainist.ui.theme.CalorieColor
import dev.antonlammers.trainist.ui.theme.CarbsColor
import dev.antonlammers.trainist.ui.theme.FatColor
import dev.antonlammers.trainist.ui.theme.ProteinColor
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class MacroWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val today = LocalDate.now()
        val entries = ep.foodEntryRepository().entriesForDate(today).first()
        val goal = ep.goalRepository().goal().first()

        val data = WidgetData(
            consumedKcal = entries.sumOf { it.kcal },
            goalKcal = goal.kcal,
            consumedProtein = entries.sumOf { it.proteinG },
            goalProtein = goal.proteinG,
            consumedCarbs = entries.sumOf { it.carbsG },
            goalCarbs = goal.carbsG,
            consumedFat = entries.sumOf { it.fatG },
            goalFat = goal.fatG,
        )

        provideContent {
            GlanceTheme {
                WidgetContent(data)
            }
        }
    }
}

private data class WidgetData(
    val consumedKcal: Double,
    val goalKcal: Double,
    val consumedProtein: Double,
    val goalProtein: Double,
    val consumedCarbs: Double,
    val goalCarbs: Double,
    val consumedFat: Double,
    val goalFat: Double,
) {
    val remainingKcal get() = goalKcal - consumedKcal
    val kcalProgress get() = if (goalKcal > 0) (consumedKcal / goalKcal).toFloat().coerceIn(0f, 1f) else 0f
}

@androidx.compose.runtime.Composable
private fun WidgetContent(data: WidgetData) {
    val context = LocalContext.current
    val openApp = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    )

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(openApp),
    ) {
        // Header row
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                context.getString(R.string.app_name),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                context.getString(R.string.widget_open_button),
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                ),
            )
        }

        Spacer(GlanceModifier.height(10.dp))

        // Remaining kcal — the focal point
        val remaining = data.remainingKcal
        Text(
            if (remaining >= 0)
                context.getString(R.string.widget_kcal_remaining, remaining.toInt())
            else
                context.getString(R.string.widget_kcal_over_goal, (-remaining).toInt()),
            style = TextStyle(
                color = if (remaining >= 0) GlanceTheme.colors.onBackground
                        else GlanceTheme.colors.error,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            ),
        )
        Text(
            "${data.consumedKcal.toInt()} / ${data.goalKcal.toInt()} kcal",
            style = TextStyle(
                color = GlanceTheme.colors.secondary,
                fontSize = 11.sp,
            ),
        )

        Spacer(GlanceModifier.height(6.dp))

        LinearProgressIndicator(
            progress = data.kcalProgress,
            modifier = GlanceModifier.fillMaxWidth().height(5.dp),
            color = ColorProvider(CalorieColor),
            backgroundColor = ColorProvider(CalorieColor.copy(alpha = 0.18f)),
        )

        Spacer(GlanceModifier.height(10.dp))

        // Macro rows
        MacroRow(context.getString(R.string.widget_macro_protein_letter), data.consumedProtein, data.goalProtein, ProteinColor)
        Spacer(GlanceModifier.height(5.dp))
        MacroRow(context.getString(R.string.widget_macro_carbs_letter), data.consumedCarbs, data.goalCarbs, CarbsColor)
        Spacer(GlanceModifier.height(5.dp))
        MacroRow(context.getString(R.string.widget_macro_fat_letter), data.consumedFat, data.goalFat, FatColor)
    }
}

@androidx.compose.runtime.Composable
private fun MacroRow(label: String, consumed: Double, goal: Double, color: Color) {
    val progress = if (goal > 0) (consumed / goal).toFloat().coerceIn(0f, 1f) else 0f
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            label,
            modifier = GlanceModifier.width(14.dp),
            style = TextStyle(
                color = ColorProvider(color),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            ),
        )
        Spacer(GlanceModifier.width(6.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = GlanceModifier.defaultWeight().height(5.dp),
            color = ColorProvider(color),
            backgroundColor = ColorProvider(color.copy(alpha = 0.18f)),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            "${consumed.toInt()}/${goal.toInt()}g",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 10.sp,
            ),
        )
    }
}
