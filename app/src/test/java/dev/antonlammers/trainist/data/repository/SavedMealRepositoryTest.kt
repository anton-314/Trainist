package dev.antonlammers.trainist.data.repository

import dev.antonlammers.trainist.data.local.dao.SavedMealDao
import dev.antonlammers.trainist.data.local.entity.SavedMealEntity
import dev.antonlammers.trainist.data.local.entity.SavedMealItemEntity
import dev.antonlammers.trainist.data.local.relation.SavedMealWithItems
import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.FoodTag
import dev.antonlammers.trainist.domain.model.SavedMeal
import dev.antonlammers.trainist.domain.model.SavedMealItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedMealRepositoryTest {

    private val dao = FakeSavedMealDao()
    private val repo = SavedMealRepositoryImpl(dao, PassThroughTransactionRunner())

    @Test
    fun `a saved meal reads back with its ingredients in order and all snapshot values`() = runTest {
        val meal = muesli()

        val id = repo.save(meal)

        assertEquals(meal.copy(id = id), repo.meals().first().single())
    }

    @Test
    fun `saving under an existing stable key replaces the meal instead of adding one`() = runTest {
        repo.save(muesli())

        // A backup re-import arrives with id 0 and the same stable key.
        repo.save(muesli().copy(name = "Müsli groß", items = muesli().items.take(1)))

        val meals = repo.meals().first()
        assertEquals(1, meals.size)
        assertEquals("Müsli groß", meals.single().name)
        assertEquals(1, meals.single().items.size)
        assertEquals(1, dao.items.value.size)
    }

    @Test
    fun `editing by id keeps the row and rewrites the ingredients`() = runTest {
        val id = repo.save(muesli())
        val edited = repo.meals().first().single().let { it.copy(items = it.items.reversed()) }

        assertEquals(id, repo.save(edited))

        assertEquals(edited, repo.meals().first().single())
    }

    @Test
    fun `meals are listed by name, ignoring case`() = runTest {
        repo.save(muesli().copy(stableId = "b", name = "porridge"))
        repo.save(muesli().copy(stableId = "a", name = "Abendbrot"))
        repo.save(muesli().copy(stableId = "c", name = "Müsli"))

        assertEquals(listOf("Abendbrot", "Müsli", "porridge"), repo.meals().first().map { it.name })
    }

    @Test
    fun `delete removes the meal and its ingredients`() = runTest {
        val id = repo.save(muesli())

        repo.delete(id)

        assertTrue(repo.meals().first().isEmpty())
        assertTrue(dao.items.value.isEmpty())
    }

    private fun muesli() = SavedMeal(
        stableId = "meal-1",
        name = "Müsli",
        items = listOf(
            SavedMealItem(food("Haferflocken", "Kölln", FoodTag.HEALTHY), 60.0),
            SavedMealItem(food("Magerquark", null, FoodTag.NEUTRAL), 150.0),
            SavedMealItem(food("Milch", null, FoodTag.NONE), 200.0),
        ),
    )

    private fun food(name: String, brand: String?, tag: FoodTag) = Food(
        id = "", name = name, brand = brand,
        kcalPer100g = 100.0, proteinPer100g = 10.0, carbsPer100g = 5.0, fatPer100g = 1.0,
        sugarPer100g = 2.0, fiberPer100g = 3.0, saltPer100g = 0.1, tag = tag,
    )
}

private class PassThroughTransactionRunner : TransactionRunner {
    override suspend fun <R> transaction(block: suspend () -> R): R = block()
}

/** In-memory [SavedMealDao]: unique stableId, name ordering (NOCASE) and the FK cascade on delete. */
private class FakeSavedMealDao : SavedMealDao {
    val meals = MutableStateFlow<List<SavedMealEntity>>(emptyList())
    val items = MutableStateFlow<List<SavedMealItemEntity>>(emptyList())
    private var nextMealId = 1L
    private var nextItemId = 1L

    override fun allMeals(): Flow<List<SavedMealWithItems>> = meals.map { list ->
        list.sortedBy { it.name.lowercase() }.map { meal ->
            // Deliberately unordered, like @Relation: the mapper has to sort by position.
            SavedMealWithItems(meal, items.value.filter { it.mealId == meal.id }.shuffled())
        }
    }

    override suspend fun idForStableId(stableId: String): Long? = meals.value.firstOrNull { it.stableId == stableId }?.id

    override suspend fun insertMeal(meal: SavedMealEntity): Long {
        check(meals.value.none { it.stableId == meal.stableId }) { "UNIQUE constraint failed: stableId" }
        val id = nextMealId++
        meals.value = meals.value + meal.copy(id = id)
        return id
    }

    override suspend fun updateMeal(meal: SavedMealEntity) {
        meals.value = meals.value.map { if (it.id == meal.id) meal else it }
    }

    override suspend fun insertItems(items: List<SavedMealItemEntity>) {
        this.items.value = this.items.value + items.map { it.copy(id = nextItemId++) }
    }

    override suspend fun deleteItems(mealId: Long) {
        items.value = items.value.filterNot { it.mealId == mealId }
    }

    override suspend fun deleteMeal(id: Long) {
        meals.value = meals.value.filterNot { it.id == id }
        items.value = items.value.filterNot { it.mealId == id }
    }
}
