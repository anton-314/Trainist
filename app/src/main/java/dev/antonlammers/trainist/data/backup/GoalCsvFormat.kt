package dev.antonlammers.trainist.data.backup

import dev.antonlammers.trainist.domain.model.ActivityLevel
import dev.antonlammers.trainist.domain.model.BiologicalSex
import dev.antonlammers.trainist.domain.model.BmrProfile
import dev.antonlammers.trainist.domain.model.DailyGoal

object GoalCsvFormat {
    private const val KCAL = "kcal"
    private const val PROTEIN_G = "protein_g"
    private const val CARBS_G = "carbs_g"
    private const val FAT_G = "fat_g"
    private const val TARGET_WEIGHT_KG = "target_weight_kg"
    private const val BMR_SEX = "bmr_sex"
    private const val BMR_AGE_YEARS = "bmr_age_years"
    private const val BMR_HEIGHT_CM = "bmr_height_cm"
    private const val BMR_ACTIVITY_LEVEL = "bmr_activity_level"

    val HEADER: String = listOf(
        KCAL, PROTEIN_G, CARBS_G, FAT_G, TARGET_WEIGHT_KG,
        BMR_SEX, BMR_AGE_YEARS, BMR_HEIGHT_CM, BMR_ACTIVITY_LEVEL,
    ).joinToString(",")

    fun toRow(goal: DailyGoal): String = listOf(
        goal.kcal, goal.proteinG, goal.carbsG, goal.fatG, goal.targetWeightKg ?: "",
        goal.bmrProfile?.sex?.name ?: "",
        goal.bmrProfile?.ageYears ?: "",
        goal.bmrProfile?.heightCm ?: "",
        goal.bmrProfile?.activityLevel?.name ?: "",
    ).joinToString(",")

    fun fromRow(row: String, headers: Map<String, Int>): DailyGoal? {
        val cols = CsvFormat.parseLine(row)
        val kcal = cols.csvDbl(headers, KCAL) ?: return null
        val proteinG = cols.csvDbl(headers, PROTEIN_G) ?: return null
        val carbsG = cols.csvDbl(headers, CARBS_G) ?: return null
        val fatG = cols.csvDbl(headers, FAT_G) ?: return null
        // Optional column — older exports without it parse to null (no target).
        val targetWeightKg = cols.csvDbl(headers, TARGET_WEIGHT_KG)
        // Optional columns — older exports without them parse to null (no saved profile).
        val bmrProfile = BmrProfile.fromParts(
            sex = BiologicalSex.parse(cols.csvStr(headers, BMR_SEX)),
            ageYears = cols.csvInt(headers, BMR_AGE_YEARS),
            heightCm = cols.csvDbl(headers, BMR_HEIGHT_CM),
            activityLevel = ActivityLevel.parse(cols.csvStr(headers, BMR_ACTIVITY_LEVEL)),
        )
        return DailyGoal(
            kcal = kcal,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            targetWeightKg = targetWeightKg,
            bmrProfile = bmrProfile,
        )
    }
}
