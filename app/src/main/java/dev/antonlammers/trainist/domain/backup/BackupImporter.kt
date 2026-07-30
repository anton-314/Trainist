package dev.antonlammers.trainist.domain.backup

/**
 * Restores a backup from a user-picked location.
 *
 * Takes the **string form of an Android `Uri`** — see [BackupExporter] for why the seam is stringly
 * typed rather than carrying `Uri` itself.
 */
interface BackupImporter {
    /** How much of each section a single import restored. */
    data class Result(
        val foodImported: Int = 0,
        val foodSkipped: Int = 0,
        val weightImported: Int = 0,
        val goalRestored: Boolean = false,
        val customFoodsImported: Int = 0,
        val exercisesImported: Int = 0,
        val templatesImported: Int = 0,
        val sessionsImported: Int = 0,
        val measurementsImported: Int = 0,
    )

    suspend fun import(uri: String): Result
}
