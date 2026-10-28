package dev.antonlammers.trainist.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import dev.antonlammers.trainist.data.local.entity.SavedMealEntity
import dev.antonlammers.trainist.data.local.entity.SavedMealItemEntity

/** A saved meal together with its (unordered on read — sort by position) ingredients. */
data class SavedMealWithItems(
    @Embedded val meal: SavedMealEntity,
    @Relation(parentColumn = "id", entityColumn = "mealId")
    val items: List<SavedMealItemEntity>,
)
