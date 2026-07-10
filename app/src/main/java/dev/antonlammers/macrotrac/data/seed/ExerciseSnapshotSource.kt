package dev.antonlammers.macrotrac.data.seed

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antonlammers.macrotrac.domain.model.Exercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Supplies the bundled exercise snapshot. Abstracted so the seeder can be unit-tested with a fake
 * source (no assets/Android needed) — the concrete source reads the versioned app asset.
 */
fun interface ExerciseSnapshotSource {
    suspend fun load(): List<Exercise>
}

/** Reads and parses the bundled `exercise_catalog.json` asset off the main thread. */
class AssetExerciseSnapshotSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : ExerciseSnapshotSource {

    override suspend fun load(): List<Exercise> = withContext(Dispatchers.IO) {
        val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        ExerciseSnapshotParser.parse(json)
    }

    private companion object {
        const val ASSET_NAME = "exercise_catalog.json"
    }
}
