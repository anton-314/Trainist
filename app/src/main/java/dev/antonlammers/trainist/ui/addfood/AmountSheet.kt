package dev.antonlammers.trainist.ui.addfood

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.FoodPortions
import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.FoodTag
import dev.antonlammers.trainist.domain.model.MealCategory
import dev.antonlammers.trainist.domain.model.SavedMealItem
import dev.antonlammers.trainist.ui.components.NumericTextField
import dev.antonlammers.trainist.ui.components.TagSelector
import dev.antonlammers.trainist.ui.components.displayName
import dev.antonlammers.trainist.util.normalizeDecimal

/**
 * Amount entry for a single food: amount, meal-category chips, tag, then the macro preview and the
 * confirm button. Follows the tall-sheet shape — fully expanded, only the fields scroll, preview and
 * button pinned below them — so the button stays above the keyboard while the amount is typed.
 * As an ingredient pick ([forIngredient]) the category chips are left out: an ingredient is logged
 * with its meal, whose category is chosen then.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AmountSheet(
    food: Food,
    amount: String,
    mealCategory: MealCategory,
    tag: FoodTag,
    forIngredient: Boolean,
    onAmountChange: (String) -> Unit,
    onMealCategoryChange: (MealCategory) -> Unit,
    onTagChange: (FoodTag) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val amountGrams = amount.normalizeDecimal().toDoubleOrNull()?.takeIf { it > 0 }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.statusBarsPadding(),
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Text(
                food.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.addfood_amount_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NumericTextField(
                        value = amount,
                        onValueChange = onAmountChange,
                        label = null,
                        suffix = "g",
                        textStyle = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (!forIngredient) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.addfood_meal_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MealCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = cat == mealCategory,
                                onClick = { onMealCategoryChange(cat) },
                                label = { Text(cat.displayName(), style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }

                TagSelector(selected = tag, onSelected = onTagChange)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (amountGrams != null) TotalsPill(FoodPortions.totals(listOf(SavedMealItem(food, amountGrams))))
                SheetConfirmButton(
                    label = stringResource(if (forIngredient) R.string.addfood_add_to_meal_button else R.string.addfood_add_button),
                    enabled = amountGrams != null,
                    onClick = onConfirm,
                )
            }
        }
    }
}
