package dev.antonlammers.trainist.ui.workout

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.model.ExerciseType
import dev.antonlammers.trainist.domain.model.Mechanic
import java.util.Locale

/**
 * Shared display formatting for exercise metadata, so the catalog and the exercise-detail screen
 * render types / mechanics / muscle names identically.
 */

// The bundled exercise catalog (free-exercise-db) is English-only data (muscle/equipment names),
// so titlecasing it is intentionally locale-fixed to English regardless of the app's language.
internal fun String.titleCase(): String = split(" ").joinToString(" ") { word ->
    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
}

@Composable
internal fun ExerciseType.displayName(): String = stringResource(
    when (this) {
        ExerciseType.WEIGHT_REPS -> R.string.workout_exercise_type_weight_reps
        ExerciseType.BODYWEIGHT -> R.string.workout_exercise_type_bodyweight
    },
)

@Composable
internal fun Mechanic.displayName(): String = stringResource(
    when (this) {
        Mechanic.COMPOUND -> R.string.workout_mechanic_compound
        Mechanic.ISOLATION -> R.string.workout_mechanic_isolation
    },
)
