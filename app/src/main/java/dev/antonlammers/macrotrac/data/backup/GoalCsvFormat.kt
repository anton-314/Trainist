package dev.antonlammers.macrotrac.data.backup

import dev.antonlammers.macrotrac.domain.model.DailyGoal

object GoalCsvFormat {
    private const val KCAL = "kcal"
    private const val PROTEIN_G = "protein_g"
    private const val CARBS_G = "carbs_g"
    private const val FAT_G = "fat_g"

    val HEADER: String = listOf(KCAL, PROTEIN_G, CARBS_G, FAT_G).joinToString(",")

    fun toRow(goal: DailyGoal): String =
        listOf(goal.kcal, goal.proteinG, goal.carbsG, goal.fatG).joinToString(",")

    fun fromRow(row: String, headers: Map<String, Int>): DailyGoal? {
        val cols = CsvFormat.parseLine(row)
        val kcal = cols.csvDbl(headers, KCAL) ?: return null
        val proteinG = cols.csvDbl(headers, PROTEIN_G) ?: return null
        val carbsG = cols.csvDbl(headers, CARBS_G) ?: return null
        val fatG = cols.csvDbl(headers, FAT_G) ?: return null
        return DailyGoal(kcal = kcal, proteinG = proteinG, carbsG = carbsG, fatG = fatG)
    }
}
