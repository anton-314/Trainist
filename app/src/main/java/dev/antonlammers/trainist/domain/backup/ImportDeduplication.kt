package dev.antonlammers.trainist.domain.backup

import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.FoodEntry

/**
 * Keeps an import from ever doubling data that has no stable key — food entries and custom foods.
 * Everything else in a backup upserts on a stable key or a unique index; these two are plain
 * inserts, so restoring a backup over data that is already there used to store every row again
 * (and a backup exported from such a device carried the copies along, ×2, ×4, ×8 …).
 *
 * A row is dropped when it is **identical in every field but the row id** to one already stored or
 * to an earlier row of the same import. That is safe for food entries because two genuinely separate
 * entries never share their millisecond `timestampMs` (a saved meal's ingredients count down 1 ms
 * each). A row that differs in anything — an entry edited since the backup was made — is kept.
 */
object ImportDeduplication {

    fun newFoodEntries(existing: List<FoodEntry>, incoming: List<FoodEntry>): List<FoodEntry> =
        unseen(existing, incoming) { it.copy(id = 0) }

    fun newCustomFoods(existing: List<Food>, incoming: List<Food>): List<Food> =
        unseen(existing, incoming) { it.copy(id = "") }

    private inline fun <T, K> unseen(existing: List<T>, incoming: List<T>, identity: (T) -> K): List<T> {
        val seen = existing.mapTo(HashSet(), identity)
        return incoming.filter { seen.add(identity(it)) }
    }
}
