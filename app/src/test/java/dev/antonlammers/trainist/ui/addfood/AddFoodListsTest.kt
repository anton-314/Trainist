package dev.antonlammers.trainist.ui.addfood

import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.domain.model.MealCategory
import dev.antonlammers.trainist.domain.model.SavedMeal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddFoodListsTest {

    // --- history days ---

    @Test
    fun `history is grouped by day, newest day first, each with its kcal total`() {
        val monday = LocalDate.parse("2026-10-26")
        val tuesday = monday.plusDays(1)
        val entries = listOf(
            entry(1, monday, 174.0),
            entry(2, tuesday, 220.0),
            entry(3, tuesday, 143.5),
        )

        val days = historyDays(entries)

        assertEquals(listOf(tuesday, monday), days.map { it.date })
        assertEquals(363.5, days[0].kcal, 0.0)
        assertEquals(listOf(2L, 3L), days[0].entries.map { it.id })
        assertEquals(174.0, days[1].kcal, 0.0)
    }

    @Test
    fun `an empty history has no days`() {
        assertTrue(historyDays(emptyList()).isEmpty())
    }

    // --- search sections ---

    @Test
    fun `meals and custom foods are mine, history is its own group`() {
        val sections = SearchSections.of(localResults(), RemoteSearchState(), includeMeals = true)

        assertEquals(2, sections.mine.size)
        assertTrue(sections.mine[0] is LocalSearchResult.SavedMealResult)
        assertTrue(sections.mine[1] is LocalSearchResult.CustomFoodResult)
        assertEquals(1, sections.history.size)
    }

    @Test
    fun `while picking an ingredient, meals are left out and the counts follow`() {
        val sections = SearchSections.of(localResults(), RemoteSearchState(), includeMeals = false)

        assertEquals(1, sections.mine.size)
        assertEquals(1, sections.count(SearchSource.MINE))
        assertEquals(2, sections.count(SearchSource.ALL))
    }

    @Test
    fun `counts add up over all sources once Open Food Facts has answered`() {
        val online = RemoteSearchState(results = listOf(food("Skyr Vanille"), food("Skyr Natur")))
        val sections = SearchSections.of(localResults(), online, includeMeals = true)

        assertEquals(5, sections.count(SearchSource.ALL))
        assertEquals(2, sections.count(SearchSource.MINE))
        assertEquals(1, sections.count(SearchSource.HISTORY))
        assertEquals(2, sections.count(SearchSource.OPEN_FOOD_FACTS))
    }

    @Test
    fun `while Open Food Facts loads, its count and the total are unknown rather than zero`() {
        val sections = SearchSections.of(localResults(), RemoteSearchState(isLoading = true), includeMeals = true)

        assertNull(sections.count(SearchSource.OPEN_FOOD_FACTS))
        assertNull(sections.count(SearchSource.ALL))
        assertEquals(2, sections.count(SearchSource.MINE))
    }

    @Test
    fun `a filter shows only its own group, all shows every group`() {
        val sections = SearchSections.of(emptyList(), RemoteSearchState(), includeMeals = true)

        assertTrue(sections.shows(SearchSource.MINE, SearchSource.ALL))
        assertTrue(sections.shows(SearchSource.OPEN_FOOD_FACTS, SearchSource.ALL))
        assertTrue(sections.shows(SearchSource.HISTORY, SearchSource.HISTORY))
        assertFalse(sections.shows(SearchSource.MINE, SearchSource.HISTORY))
    }

    @Test
    fun `no results is only declared once Open Food Facts has had its say`() {
        assertFalse(SearchSections.of(emptyList(), RemoteSearchState(isLoading = true), true).isEmpty(SearchSource.ALL))
        assertTrue(SearchSections.of(emptyList(), RemoteSearchState(), true).isEmpty(SearchSource.ALL))
    }

    @Test
    fun `a failed online search is something to show, not an empty result`() {
        assertFalse(SearchSections.of(emptyList(), RemoteSearchState(failed = true), true).isEmpty(SearchSource.ALL))
    }

    @Test
    fun `emptiness is judged under the filter - hits elsewhere don't count`() {
        val sections = SearchSections.of(localResults(), RemoteSearchState(), includeMeals = true)

        assertFalse(sections.isEmpty(SearchSource.MINE))
        assertTrue(sections.isEmpty(SearchSource.OPEN_FOOD_FACTS))
    }

    // --- match highlighting ---

    @Test
    fun `the match is found regardless of case`() {
        assertEquals(0..3, matchRange("Skyr Natur", "skyr"))
        assertEquals(5..9, matchRange("Skyr Natur", "NATUR"))
    }

    @Test
    fun `surrounding whitespace in the query is ignored`() {
        assertEquals(0..3, matchRange("Skyr Natur", " skyr "))
    }

    @Test
    fun `no match, or an empty query, highlights nothing`() {
        assertNull(matchRange("Skyr Natur", "quark"))
        assertNull(matchRange("Skyr Natur", "  "))
    }

    private fun localResults(): List<LocalSearchResult> = listOf(
        LocalSearchResult.SavedMealResult(SavedMeal(id = 1, stableId = "m", name = "Skyr-Bowl", items = emptyList())),
        LocalSearchResult.CustomFoodResult(food("Skyr selbstgemacht")),
        LocalSearchResult.HistoryResult(entry(9, LocalDate.parse("2026-10-27"), 95.0)),
    )

    private fun food(name: String) = Food(
        id = name, name = name, brand = null,
        kcalPer100g = 63.0, proteinPer100g = 11.0, carbsPer100g = 4.0, fatPer100g = 0.2,
    )

    private fun entry(id: Long, date: LocalDate, kcal: Double) = FoodEntry(
        id = id, date = date, foodName = "Skyr", brand = null, amountGrams = 150.0,
        kcal = kcal, proteinG = 17.0, carbsG = 6.0, fatG = 0.3,
        mealCategory = MealCategory.BREAKFAST, timestampMs = id,
    )
}
