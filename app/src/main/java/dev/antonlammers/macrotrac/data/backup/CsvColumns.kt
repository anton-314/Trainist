package dev.antonlammers.macrotrac.data.backup

/**
 * Central registry of CSV column names for food entries.
 * Column order in HEADER determines export order; import always reads by name.
 * To add a column: add the constant, add it to HEADER, write it in CsvFormat.toRow,
 * and read it in CsvFormat.fromRow with a sensible default for missing values.
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
    const val SUGAR_G = "sugar_g"
    const val FIBER_G = "fiber_g"
    const val SALT_G = "salt_g"
    const val MEAL_CATEGORY = "meal_category"
    const val TIMESTAMP_MS = "timestamp_ms"

    val HEADER: String = listOf(
        DATE, FOOD_NAME, BRAND, AMOUNT_GRAMS,
        KCAL, PROTEIN_G, CARBS_G, FAT_G,
        SUGAR_G, FIBER_G, SALT_G, MEAL_CATEGORY,
        TIMESTAMP_MS,
    ).joinToString(",")
}
