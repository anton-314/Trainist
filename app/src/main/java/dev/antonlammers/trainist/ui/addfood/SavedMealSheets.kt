package dev.antonlammers.trainist.ui.addfood

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.FoodPortions
import dev.antonlammers.trainist.domain.NutritionTotals
import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.MealCategory
import dev.antonlammers.trainist.ui.components.displayName
import dev.antonlammers.trainist.ui.components.NumericTextField
import dev.antonlammers.trainist.ui.components.TagDot

/** What the "+" sheet creates. */
internal enum class CreateKind { FOOD, MEAL }

/**
 * The "+" sheet: one sheet for creating a custom food **or** a saved meal, switched by the segmented
 * control at its top. The switch is only offered while creating something new ([kindSwitchable]) —
 * an existing meal being edited, or one started from the Overview, is a meal and nothing else.
 *
 * Both bodies follow the tall-sheet shape: opened fully expanded, only the field list scrolls, the
 * confirm button pinned below it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateSheet(
    kind: CreateKind,
    kindSwitchable: Boolean,
    title: String,
    onKindChange: (CreateKind) -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            if (kindSwitchable) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp),
                ) {
                    CreateKind.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option == kind,
                            onClick = { onKindChange(option) },
                            shape = SegmentedButtonDefaults.itemShape(index, CreateKind.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    when (option) {
                                        CreateKind.FOOD -> R.string.addfood_create_kind_food
                                        CreateKind.MEAL -> R.string.addfood_create_kind_meal
                                    },
                                ),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            content()
        }
    }
}

/**
 * The meal body of [CreateSheet]: name, the ingredient list with editable amounts, "add ingredient"
 * (which hands over to the screen's search) and the totals above the pinned save button.
 */
@Composable
internal fun ColumnScope.MealEditorForm(
    draft: MealDraft,
    onNameChange: (String) -> Unit,
    onAmountChange: (key: Long, amount: String) -> Unit,
    onRemove: (key: Long) -> Unit,
    onAddIngredient: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.addfood_field_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (draft.items.isEmpty()) {
            Text(
                stringResource(R.string.addfood_meal_no_ingredients),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        draft.items.forEach { item ->
            IngredientRow(
                food = item.food,
                amount = item.amount,
                onAmountChange = { onAmountChange(item.key, it) },
                trailing = {
                    IconButton(onClick = { onRemove(item.key) }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.addfood_meal_remove_ingredient_content_description),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
        OutlinedButton(
            onClick = onAddIngredient,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text(
                stringResource(R.string.addfood_meal_add_ingredient),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (draft.items.isNotEmpty()) TotalsPill(draft.totals)
        Button(
            onClick = onSave,
            enabled = draft.canSave,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) { Text(stringResource(R.string.common_save), style = MaterialTheme.typography.labelLarge) }
    }
}

/**
 * Logging a saved meal: every ingredient with its amount (pre-filled, editable for this entry only —
 * a cleared field leaves the ingredient out), the meal-category chips and the totals of what will
 * actually be logged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MealLogSheet(
    draft: MealLogDraft,
    mealCategory: MealCategory,
    onAmountChange: (index: Int, amount: String) -> Unit,
    onMealCategoryChange: (MealCategory) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val portions = draft.portions()
    val totals = FoodPortions.totals(portions.map { (item, amount) -> item.copy(amountGrams = amount) })

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
                draft.meal.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                stringResource(R.string.addfood_meal_log_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                draft.meal.items.forEachIndexed { index, item ->
                    IngredientRow(
                        food = item.food,
                        amount = draft.amounts[index],
                        onAmountChange = { onAmountChange(index, it) },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MealCategory.entries.forEach { category ->
                        FilterChip(
                            selected = category == mealCategory,
                            onClick = { onMealCategoryChange(category) },
                            label = { Text(category.displayName(), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                TotalsPill(totals)
                Button(
                    onClick = onConfirm,
                    enabled = portions.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) { Text(stringResource(R.string.addfood_add_button), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

/** An ingredient: tag dot + name/brand on the left, its gram amount field on the right. */
@Composable
private fun IngredientRow(
    food: Food,
    amount: String,
    onAmountChange: (String) -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(7.dp)) { TagDot(food.tag, size = 7.dp) }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                food.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            food.brand?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        NumericTextField(
            value = amount,
            onValueChange = onAmountChange,
            label = null,
            suffix = "g",
            modifier = Modifier.width(110.dp),
        )
        trailing()
    }
}

@Composable
private fun TotalsPill(totals: NutritionTotals) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(
                R.string.addfood_food_detail_total,
                totals.kcal.toInt(),
                totals.proteinG.toInt(),
                totals.carbsG.toInt(),
                totals.fatG.toInt(),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
