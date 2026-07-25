package dev.antonlammers.trainist.ui.help

import androidx.annotation.StringRes
import dev.antonlammers.trainist.R

/**
 * One help entry — a getting-started step, an FAQ question, or a glossary term. All three are the
 * same shape (a heading plus a paragraph); only the rendering differs.
 */
data class HelpEntry(@StringRes val title: Int, @StringRes val body: Int)

/** A titled group of [HelpEntry]s, rendered as one card under a mono-uppercase section label. */
data class HelpSection(@StringRes val title: Int, val entries: List<HelpEntry>)

/**
 * The Help Center's content, as data rather than hand-written composables.
 *
 * Adding a question is one [HelpEntry] plus its two strings in each locale's `strings.xml` — no UI
 * change — and keeping it Android-free (string ids, resolved in the composable) means the whole
 * structure stays reachable by a plain JVM test (`HelpContentTest` guards against duplicated or
 * empty sections, which are otherwise invisible until someone scrolls the screen).
 */
object HelpContent {

    /** Shown expanded at the top: the four things a new user does first, in order. */
    val gettingStarted: List<HelpEntry> = listOf(
        HelpEntry(R.string.help_start_goals_title, R.string.help_start_goals_body),
        HelpEntry(R.string.help_start_nutrition_title, R.string.help_start_nutrition_body),
        HelpEntry(R.string.help_start_training_title, R.string.help_start_training_body),
        HelpEntry(R.string.help_start_progress_title, R.string.help_start_progress_body),
    )

    /** FAQ groups followed by the glossary — every entry collapsed until tapped. */
    val sections: List<HelpSection> = listOf(
        HelpSection(
            R.string.help_section_nutrition,
            listOf(
                HelpEntry(R.string.help_nutrition_q_add, R.string.help_nutrition_a_add),
                HelpEntry(R.string.help_nutrition_q_search, R.string.help_nutrition_a_search),
                HelpEntry(R.string.help_nutrition_q_edit, R.string.help_nutrition_a_edit),
                HelpEntry(R.string.help_nutrition_q_tags, R.string.help_nutrition_a_tags),
                HelpEntry(R.string.help_nutrition_q_copy, R.string.help_nutrition_a_copy),
            ),
        ),
        HelpSection(
            R.string.help_section_training,
            listOf(
                HelpEntry(R.string.help_training_q_start, R.string.help_training_a_start),
                HelpEntry(R.string.help_training_q_templates, R.string.help_training_a_templates),
                HelpEntry(R.string.help_training_q_hints, R.string.help_training_a_hints),
                HelpEntry(R.string.help_training_q_timer, R.string.help_training_a_timer),
                HelpEntry(R.string.help_training_q_timer_killed, R.string.help_training_a_timer_killed),
                HelpEntry(R.string.help_training_q_template_sync, R.string.help_training_a_template_sync),
                HelpEntry(R.string.help_training_q_history_edit, R.string.help_training_a_history_edit),
            ),
        ),
        HelpSection(
            R.string.help_section_stats,
            listOf(
                HelpEntry(R.string.help_stats_q_calories, R.string.help_stats_a_calories),
                HelpEntry(R.string.help_stats_q_macros, R.string.help_stats_a_macros),
                HelpEntry(R.string.help_stats_q_overload, R.string.help_stats_a_overload),
                HelpEntry(R.string.help_stats_q_too_little_data, R.string.help_stats_a_too_little_data),
                HelpEntry(R.string.help_stats_q_reorder, R.string.help_stats_a_reorder),
            ),
        ),
        HelpSection(
            R.string.help_section_data,
            listOf(
                HelpEntry(R.string.help_data_q_where, R.string.help_data_a_where),
                HelpEntry(R.string.help_data_q_backup, R.string.help_data_a_backup),
                HelpEntry(R.string.help_data_q_new_phone, R.string.help_data_a_new_phone),
                HelpEntry(R.string.help_data_q_import_merge, R.string.help_data_a_import_merge),
                HelpEntry(R.string.help_data_q_partial_import, R.string.help_data_a_partial_import),
            ),
        ),
        HelpSection(
            R.string.help_section_glossary,
            listOf(
                HelpEntry(R.string.help_glossary_bmr_term, R.string.help_glossary_bmr_def),
                HelpEntry(R.string.help_glossary_tdee_term, R.string.help_glossary_tdee_def),
                HelpEntry(R.string.help_glossary_macros_term, R.string.help_glossary_macros_def),
                HelpEntry(R.string.help_glossary_clean_term, R.string.help_glossary_clean_def),
                HelpEntry(R.string.help_glossary_volume_term, R.string.help_glossary_volume_def),
                HelpEntry(R.string.help_glossary_e1rm_term, R.string.help_glossary_e1rm_def),
                HelpEntry(R.string.help_glossary_pr_term, R.string.help_glossary_pr_def),
                HelpEntry(R.string.help_glossary_set_types_term, R.string.help_glossary_set_types_def),
                HelpEntry(R.string.help_glossary_overload_term, R.string.help_glossary_overload_def),
            ),
        ),
    )
}
