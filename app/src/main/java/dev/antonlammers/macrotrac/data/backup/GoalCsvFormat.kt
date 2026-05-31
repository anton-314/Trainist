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
        val cols = row.split(",")
        val kcal = headers[KCAL]?.let { cols.getOrNull(it)?.trim()?.toDoubleOrNull() } ?: return null
        val proteinG = headers[PROTEIN_G]?.let { cols.getOrNull(it)?.trim()?.toDoubleOrNull() } ?: return null
        val carbsG = headers[CARBS_G]?.let { cols.getOrNull(it)?.trim()?.toDoubleOrNull() } ?: return null
        val fatG = headers[FAT_G]?.let { cols.getOrNull(it)?.trim()?.toDoubleOrNull() } ?: return null
        return DailyGoal(kcal = kcal, proteinG = proteinG, carbsG = carbsG, fatG = fatG)
    }

    fun parseHeaders(headerLine: String): Map<String, Int> =
        headerLine.split(",").mapIndexed { i, h -> h.trim() to i }.toMap()
}
