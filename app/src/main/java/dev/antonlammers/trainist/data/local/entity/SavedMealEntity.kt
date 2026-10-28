package dev.antonlammers.trainist.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A saved meal ("Meine Mahlzeiten"); its ingredients live in [SavedMealItemEntity]. */
@Entity(
    tableName = "saved_meals",
    indices = [Index(value = ["stableId"], unique = true)],
)
data class SavedMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stableId: String,
    val name: String,
    /** When the meal was last logged (epoch ms); null = never since it was saved. */
    val lastUsedMs: Long? = null,
)
