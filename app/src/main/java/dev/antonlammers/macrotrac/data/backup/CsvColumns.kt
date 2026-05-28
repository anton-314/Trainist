package dev.antonlammers.macrotrac.data.backup

/**
 * Central registry of CSV column names.
 * Add new nutrients/metrics here and update CsvExporter + CsvImporter accordingly.
 * Column order in HEADER determines export order; import always reads by name.
 */
object CsvColumns {
    const val DATE = "date"
    const val FOOD_NAME = "food_name"
    const val BRAND = "brand"
    const val AMOUNT_GRAMS = "amount_grams"
    const val KCAL = "kcal"
    const val PROTEIN_G = "protein_g"
    const val CARBS_G = "carbs_g"
    const val FAT_G = "fat_g"
    const val TIMESTAMP_MS = "timestamp_ms"

    val HEADER: String = listOf(
        DATE, FOOD_NAME, BRAND, AMOUNT_GRAMS,
        KCAL, PROTEIN_G, CARBS_G, FAT_G,
        TIMESTAMP_MS,
    ).joinToString(",")
}
