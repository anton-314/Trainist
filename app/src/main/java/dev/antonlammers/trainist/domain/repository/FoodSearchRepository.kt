package dev.antonlammers.trainist.domain.repository

import dev.antonlammers.trainist.domain.model.Food

interface FoodSearchRepository {

    suspend fun getByBarcode(barcode: String): Result<Food?>

    /**
     * Products matching [query] by name, most relevant first. Empty when nothing matches; a failed
     * lookup fails the [Result] so the caller can distinguish "no hits" from "no network".
     *
     * Only products with usable nutrition data are returned — a hit the app cannot turn into a
     * logged entry is worse than no hit at all.
     */
    suspend fun searchByName(query: String): Result<List<Food>>
}
