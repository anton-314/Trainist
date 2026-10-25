package dev.antonlammers.trainist.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.model.MealCategory

@Composable
fun MealCategory.displayName(): String = stringResource(
    when (this) {
        MealCategory.BREAKFAST -> R.string.meal_category_breakfast
        MealCategory.LUNCH -> R.string.meal_category_lunch
        MealCategory.DINNER -> R.string.meal_category_dinner
        MealCategory.SNACK -> R.string.meal_category_snack
    },
)
