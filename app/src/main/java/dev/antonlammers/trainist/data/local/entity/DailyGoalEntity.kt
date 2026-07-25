package dev.antonlammers.trainist.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_goal")
data class DailyGoalEntity(
    @PrimaryKey val id: Int = 1,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val targetWeightKg: Double? = null,
    // BMR-calculator profile — all nullable, absence means "the calculator has never been run".
    // Body weight is deliberately not stored here; it prefills live from the latest WeightEntry.
    val bmrSex: String? = null,
    val bmrAgeYears: Int? = null,
    val bmrHeightCm: Double? = null,
    val bmrActivityLevel: String? = null,
)
