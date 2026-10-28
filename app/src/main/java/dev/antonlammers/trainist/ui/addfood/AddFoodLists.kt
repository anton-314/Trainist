package dev.antonlammers.trainist.ui.addfood

import dev.antonlammers.trainist.domain.model.FoodEntry
import java.time.LocalDate

/** The AddFood screen's three lists, switched by its floating pill. */
enum class AddFoodTab { HISTORY, FOODS, MEALS }

/** The search's source filter chips. */
enum class SearchSource { ALL, MINE, HISTORY, OPEN_FOOD_FACTS }

/** One day of the history tab: its entries (newest first, as delivered) and their kcal total. */
data class HistoryDay(
    val date: LocalDate,
    val kcal: Double,
    val entries: List<FoodEntry>,
)

/** Groups the history by day, newest day first, each with the kcal it adds up to. */
fun historyDays(entries: List<FoodEntry>): List<HistoryDay> = entries
    .groupBy { it.date }
    .entries
    .sortedByDescending { it.key }
    .map { (date, dayEntries) -> HistoryDay(date, dayEntries.sumOf { it.kcal }, dayEntries) }

/**
 * The search results split into the groups the screen shows under one header each: the user's own
 * meals and foods ("MEINE"), what they have eaten before, and Open Food Facts.
 *
 * Meals are left out while an ingredient is being picked ([includeMeals] = false) — a meal can't be
 * an ingredient of another one — and the counts on the filter chips follow, so a chip never promises
 * a hit that isn't shown.
 */
data class SearchSections(
    val mine: List<LocalSearchResult>,
    val history: List<LocalSearchResult.HistoryResult>,
    val online: RemoteSearchState,
) {
    /**
     * Hits behind a filter chip. Open Food Facts has no count until its answer is in (null), so a
     * chip never shows a "0" that is really "not asked yet".
     */
    fun count(source: SearchSource): Int? = when (source) {
        SearchSource.ALL -> if (onlineSettled) mine.size + history.size + online.results.size else null
        SearchSource.MINE -> mine.size
        SearchSource.HISTORY -> history.size
        SearchSource.OPEN_FOOD_FACTS -> if (onlineSettled) online.results.size else null
    }

    /** Whether the group for [group] is visible under [filter]. */
    fun shows(group: SearchSource, filter: SearchSource): Boolean = filter == SearchSource.ALL || filter == group

    /**
     * Nothing at all to show under [filter] — only once Open Food Facts has had its say too, otherwise
     * a "no results" hint flashes up in the pause before the request even goes out.
     */
    fun isEmpty(filter: SearchSource): Boolean {
        val localEmpty = (!shows(SearchSource.MINE, filter) || mine.isEmpty()) &&
            (!shows(SearchSource.HISTORY, filter) || history.isEmpty())
        val onlineEmpty = !shows(SearchSource.OPEN_FOOD_FACTS, filter) ||
            (onlineSettled && !online.failed && online.results.isEmpty())
        return localEmpty && onlineEmpty
    }

    private val onlineSettled: Boolean get() = !online.isLoading

    companion object {
        fun of(local: List<LocalSearchResult>, online: RemoteSearchState, includeMeals: Boolean) = SearchSections(
            mine = local.filter {
                it is LocalSearchResult.CustomFoodResult || (includeMeals && it is LocalSearchResult.SavedMealResult)
            },
            history = local.filterIsInstance<LocalSearchResult.HistoryResult>(),
            online = online,
        )
    }
}

/** Where [query] occurs in [text] (case-insensitive), to set it in bold; null when it doesn't. */
fun matchRange(text: String, query: String): IntRange? {
    val needle = query.trim()
    if (needle.isEmpty()) return null
    val start = text.indexOf(needle, ignoreCase = true)
    return if (start < 0) null else start until start + needle.length
}
