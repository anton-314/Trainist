package dev.antonlammers.macrotrac.data.backup

import dev.antonlammers.macrotrac.domain.model.Exercise
import dev.antonlammers.macrotrac.domain.model.WorkoutSession
import dev.antonlammers.macrotrac.domain.model.WorkoutTemplate

/** ZIP entry names for the training sections, shared by the exporter and importer. */
object WorkoutBackupEntries {
    const val EXERCISES = "exercises.csv"
    const val WORKOUT_TEMPLATES = "workout_templates.csv"
    const val TEMPLATE_EXERCISES = "template_exercises.csv"
    const val WORKOUT_SESSIONS = "workout_sessions.csv"
    const val SESSION_EXERCISES = "session_exercises.csv"
    const val SET_ENTRIES = "set_entries.csv"
}

/**
 * Pure, Android-free reassembly of the relational training data from its flat CSV sections.
 *
 * Training data is a graph (session → exercise → sets; template → exercise slots) that the
 * repositories only persist whole. The backup therefore splits it into flat, name-based CSVs whose
 * foreign keys are the **stable string keys** (never row ids), and this object joins them back into
 * domain graphs so a backup rebuilds correctly on a freshly installed device with different row ids.
 *
 * Every parameter is the section's non-blank lines including the header (or null/empty when the
 * section is absent — older backups without training data simply yield empty lists, no error).
 */
object WorkoutBackup {

    fun parseExercises(lines: List<String>?): List<Exercise> =
        parseRows(lines) { row, headers -> ExerciseCsvFormat.fromRow(row, headers) }

    fun assembleTemplates(
        templateLines: List<String>?,
        templateExerciseLines: List<String>?,
    ): List<WorkoutTemplate> {
        val headers = parseRows(templateLines) { row, h -> WorkoutTemplateCsvFormat.fromRow(row, h) }
        if (headers.isEmpty()) return emptyList()

        val slotsByTemplate = parseRows(templateExerciseLines) { row, h ->
            TemplateExerciseCsvFormat.fromRow(row, h)
        }.groupBy { it.templateStableId }

        return headers.map { template ->
            val slots = slotsByTemplate[template.stableId].orEmpty()
                .map { it.exercise }
                .sortedBy { it.position }
            template.copy(exercises = slots)
        }
    }

    fun assembleSessions(
        sessionLines: List<String>?,
        sessionExerciseLines: List<String>?,
        setLines: List<String>?,
    ): List<WorkoutSession> {
        val headers = parseRows(sessionLines) { row, h -> WorkoutSessionCsvFormat.fromRow(row, h) }
        if (headers.isEmpty()) return emptyList()

        val exercisesBySession = parseRows(sessionExerciseLines) { row, h ->
            SessionExerciseCsvFormat.fromRow(row, h)
        }.groupBy { it.sessionStableId }

        // Sets keyed by (session, that exercise's position within the session).
        val setsByExercise = parseRows(setLines) { row, h -> SetEntryCsvFormat.fromRow(row, h) }
            .groupBy { it.sessionStableId to it.exercisePosition }

        return headers.map { session ->
            val exercises = exercisesBySession[session.stableId].orEmpty()
                .sortedBy { it.exercise.position }
                .map { row ->
                    val sets = setsByExercise[session.stableId to row.exercise.position].orEmpty()
                        .map { it.set }
                        .sortedBy { it.position }
                    row.exercise.copy(sets = sets)
                }
            session.copy(exercises = exercises)
        }
    }

    /** Parses every data row (skipping the header), dropping rows that fail to parse. */
    private inline fun <T> parseRows(lines: List<String>?, parse: (String, Map<String, Int>) -> T?): List<T> {
        if (lines == null || lines.size < 2) return emptyList()
        val headers = CsvFormat.parseHeaders(lines.first())
        return lines.drop(1).mapNotNull { runCatching { parse(it, headers) }.getOrNull() }
    }
}
