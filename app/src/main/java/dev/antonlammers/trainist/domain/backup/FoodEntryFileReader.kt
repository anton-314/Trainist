package dev.antonlammers.trainist.domain.backup

import dev.antonlammers.trainist.domain.model.FoodEntry

/**
 * Reads the food entries out of a single CSV file the user opened with the app ("Öffnen mit
 * Trainist") — **without saving anything**, so the user can review them first.
 *
 * Deliberately narrower than [BackupImporter]: only a food-entries CSV is accepted. Any other
 * section (goal, weight, a full backup ZIP …) would *replace* existing data, and a file opened from
 * a file manager or a download must never be able to do that; full restores stay in Settings.
 *
 * Takes the string form of an Android `Uri`, for the same reason as [BackupImporter].
 */
interface FoodEntryFileReader {
    suspend fun read(uri: String): FoodEntryFile
}

/** What a file opened for a food import turned out to contain. */
sealed interface FoodEntryFile {
    /** At least one valid entry; [skipped] rows were malformed and are left out. */
    data class Entries(val entries: List<FoodEntry>, val skipped: Int) : FoodEntryFile

    /** A food-entries CSV whose rows are all malformed (or which has no rows at all). */
    data object NoValidRows : FoodEntryFile

    /** Readable, but not a food-entries CSV — another backup section, a ZIP, or unrelated data. */
    data object NotFoodEntries : FoodEntryFile

    /** The file could not be opened or read. */
    data object Unreadable : FoodEntryFile
}
