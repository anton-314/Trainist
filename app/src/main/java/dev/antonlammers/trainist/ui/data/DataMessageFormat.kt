package dev.antonlammers.trainist.ui.data

import android.content.Context
import dev.antonlammers.trainist.R

/**
 * Resolves a [DataMessage] into a localized display string. Kept as a plain `Context`-based
 * function (like the notification formatters) rather than a `@Composable` one, since callers
 * (the snackbar-showing `LaunchedEffect` in SettingsScreen/OnboardingScreen) run in a coroutine
 * scope, not a Compose context.
 */
fun DataMessage.toDisplayString(context: Context): String = when (this) {
    is DataMessage.ExportFailed -> context.getString(R.string.data_export_failed, error.orEmpty())
    is DataMessage.ImportFailed -> context.getString(R.string.data_import_failed, error.orEmpty())
    is DataMessage.ImportResult -> {
        val parts = buildList {
            val food = context.getString(R.string.data_import_food_entries, foodImported) +
                if (foodSkipped > 0) " " + context.getString(R.string.data_import_food_skipped, foodSkipped) else ""
            if (foodImported > 0 || foodSkipped > 0) add(food)
            if (weightImported > 0) add(context.getString(R.string.data_import_weight_entries, weightImported))
            if (goalRestored) add(context.getString(R.string.data_import_goal_restored))
            if (customFoodsImported > 0) add(context.getString(R.string.data_import_custom_foods, customFoodsImported))
            if (exercisesImported > 0) add(context.getString(R.string.data_import_exercises, exercisesImported))
            if (templatesImported > 0) add(context.getString(R.string.data_import_templates, templatesImported))
            if (sessionsImported > 0) add(context.getString(R.string.data_import_sessions, sessionsImported))
            if (measurementsImported > 0) add(context.getString(R.string.data_import_measurements, measurementsImported))
        }
        parts.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: context.getString(R.string.data_import_nothing)
    }
}
