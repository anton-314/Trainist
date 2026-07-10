package dev.antonlammers.macrotrac.data.seed

import dev.antonlammers.macrotrac.domain.model.Exercise
import dev.antonlammers.macrotrac.domain.model.ExerciseType
import dev.antonlammers.macrotrac.fake.FakeExerciseCatalogRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogSeederTest {

    private class InMemoryVersionStore(var version: Int = 0) : SeedVersionStore {
        override suspend fun seededVersion(): Int = version
        override suspend fun setSeededVersion(version: Int) { this.version = version }
    }

    private class CountingSource(private val exercises: List<Exercise>) : ExerciseSnapshotSource {
        var loadCount = 0
            private set
        override suspend fun load(): List<Exercise> {
            loadCount++
            return exercises
        }
    }

    private fun catalog(id: String, name: String = id) = Exercise(
        stableId = id, name = name, type = ExerciseType.WEIGHT_REPS, isCustom = false,
    )

    private fun custom(id: String) = Exercise(
        stableId = id, name = "My Exercise", type = ExerciseType.BODYWEIGHT, isCustom = true,
    )

    @Test
    fun `seeds catalog on first run and records the version`() = runTest {
        val repo = FakeExerciseCatalogRepository()
        val store = InMemoryVersionStore(version = 0)
        val source = CountingSource(listOf(catalog("a"), catalog("b")))
        val seeder = ExerciseCatalogSeeder(repo, store, source)

        seeder.seedIfNeeded()

        assertEquals(2, repo.exercises().first().size)
        assertEquals(ExerciseCatalogSeeder.SNAPSHOT_VERSION, store.version)
        assertEquals(1, source.loadCount)
    }

    @Test
    fun `does nothing when the current snapshot version is already seeded`() = runTest {
        val repo = FakeExerciseCatalogRepository()
        val store = InMemoryVersionStore(version = ExerciseCatalogSeeder.SNAPSHOT_VERSION)
        val source = CountingSource(listOf(catalog("a")))
        val seeder = ExerciseCatalogSeeder(repo, store, source)

        seeder.seedIfNeeded()

        assertTrue(repo.exercises().first().isEmpty())
        assertEquals("asset must not even be loaded when up to date", 0, source.loadCount)
    }

    @Test
    fun `re-seeding a newer snapshot preserves custom exercises and refreshes the catalog`() = runTest {
        val repo = FakeExerciseCatalogRepository()
        val store = InMemoryVersionStore(version = 0)

        // First seed with the original catalog.
        ExerciseCatalogSeeder(repo, store, CountingSource(listOf(catalog("a", "Old Name")))).seedIfNeeded()
        // User adds a custom exercise.
        repo.upsertAll(listOf(custom("custom-1")))

        // A newer snapshot ships: simulate the version bump so seeding runs again.
        store.version = 0
        val updated = listOf(catalog("a", "New Name"), catalog("b"))
        ExerciseCatalogSeeder(repo, store, CountingSource(updated)).seedIfNeeded()

        val all = repo.exercises().first()
        // Custom exercise survives.
        assertTrue(all.any { it.stableId == "custom-1" && it.isCustom })
        // Catalog entry is refreshed in place (no duplicate), and the new one is added.
        assertEquals("New Name", all.first { it.stableId == "a" }.name)
        assertEquals(1, all.count { it.stableId == "a" })
        assertTrue(all.any { it.stableId == "b" })
    }
}
