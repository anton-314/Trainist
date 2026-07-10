package dev.antonlammers.macrotrac.data.seed

import dev.antonlammers.macrotrac.domain.repository.ExerciseCatalogRepository
import javax.inject.Inject

/**
 * One-shot, idempotent seeding of the bundled exercise catalog into Room.
 *
 * Runs once per snapshot version: on first launch, and again only when [SNAPSHOT_VERSION] is bumped
 * (a newer bundled snapshot). Seeding upserts by [dev.antonlammers.macrotrac.domain.model.Exercise.stableId]
 * via the [ExerciseCatalogRepository], so catalog rows are replaced/refreshed while **custom**
 * exercises (their own UUIDs) are left untouched. Android-free — the asset read and version storage
 * are injected behind interfaces so this is unit-testable with fakes.
 */
class ExerciseCatalogSeeder @Inject constructor(
    private val repository: ExerciseCatalogRepository,
    private val versionStore: SeedVersionStore,
    private val source: ExerciseSnapshotSource,
) {
    suspend fun seedIfNeeded() {
        if (versionStore.seededVersion() >= SNAPSHOT_VERSION) return
        repository.upsertAll(source.load())
        versionStore.setSeededVersion(SNAPSHOT_VERSION)
    }

    companion object {
        /** Bump when the bundled `exercise_catalog.json` asset changes to re-seed on next launch. */
        const val SNAPSHOT_VERSION = 1
    }
}
