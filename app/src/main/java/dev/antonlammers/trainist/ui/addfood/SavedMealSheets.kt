package dev.antonlammers.trainist.ui.addfood

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.FoodPortions
import dev.antonlammers.trainist.domain.NutritionTotals
import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.MealCategory
import dev.antonlammers.trainist.domain.model.SavedMealItem
import dev.antonlammers.trainist.ui.components.BareNumericTextField
import dev.antonlammers.trainist.ui.components.MacroBar
import dev.antonlammers.trainist.ui.components.displayName
import dev.antonlammers.trainist.ui.theme.CarbsColor
import dev.antonlammers.trainist.ui.theme.FatColor
import dev.antonlammers.trainist.ui.theme.ProteinColor
import dev.antonlammers.trainist.util.normalizeDecimal

/** What the create sheet creates. */
internal enum class CreateKind { FOOD, MEAL }

/**
 * The create sheet: one sheet for creating a custom food **or** a saved meal, switched by the
 * segmented control at its top. The switch is only offered while creating something new
 * ([kindSwitchable]) — an existing meal being edited, or one started from the Overview, is a meal and
 * nothing else.
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
                KindSegment(
                    selected = kind,
                    onSelect = onKindChange,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                )
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

/** Food | Meal: a filled segment on a recessed track — the app's own take on a segmented button. */
@Composable
private fun KindSegment(
    selected: CreateKind,
    onSelect: (CreateKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CreateKind.entries.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(
                        when (option) {
                            CreateKind.FOOD -> R.string.addfood_create_kind_food
                            CreateKind.MEAL -> R.string.addfood_create_kind_meal
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    color = if (isSelected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The meal body of [CreateSheet]: name, the ingredient card with editable amounts and "add
 * ingredient" (which hands over to the screen's search), and the totals above the pinned save button.
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.addfood_field_name)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        SheetCaption(
            stringResource(R.string.addfood_meal_ingredients_label, draft.items.size),
            modifier = Modifier.padding(top = 4.dp),
        )
        IngredientCard {
            if (draft.items.isEmpty()) {
                Text(
                    stringResource(R.string.addfood_meal_no_ingredients),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
                IngredientDivider()
            }
            draft.items.forEach { item ->
                IngredientRow(
                    food = item.food,
                    amount = item.amount,
                    onAmountChange = { onAmountChange(item.key, it) },
                    onRemove = { onRemove(item.key) },
                )
                IngredientDivider()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(onClick = onAddIngredient),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    stringResource(R.string.addfood_meal_add_ingredient),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MealTotals(draft.totals)
        SheetConfirmButton(stringResource(R.string.common_save), enabled = draft.canSave, onClick = onSave)
    }
}

/** A meal's total kcal in large type, its macro bar and the gram legend beneath. */
@Composable
private fun MealTotals(totals: NutritionTotals) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                totals.kcal.toInt().toString(),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                stringResource(R.string.addfood_meal_kcal_total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.alignByBaseline(),
            )
        }
        MacroBar(totals.proteinG, totals.carbsG, totals.fatG, Modifier.fillMaxWidth(), height = 8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendItem(ProteinColor, stringResource(R.string.addfood_meal_legend_protein, totals.proteinG.toInt()))
            LegendItem(CarbsColor, stringResource(R.string.addfood_meal_legend_carbs, totals.carbsG.toInt()))
            LegendItem(FatColor, stringResource(R.string.addfood_meal_legend_fat, totals.fatG.toInt()))
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            ) {
                IngredientCard {
                    draft.meal.items.forEachIndexed { index, item ->
                        if (index > 0) IngredientDivider()
                        IngredientRow(
                            food = item.food,
                            amount = draft.amounts[index],
                            onAmountChange = { onAmountChange(index, it) },
                        )
                    }
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
                SheetConfirmButton(stringResource(R.string.addfood_add_button), enabled = portions.isNotEmpty(), onClick = onConfirm)
            }
        }
    }
}

/** The recessed card holding a meal's ingredient rows, divided by hairlines. */
@Composable
private fun IngredientCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        content = content,
    )
}

@Composable
private fun IngredientDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

/**
 * An ingredient: tag dot, name (+ brand) over what the current amount adds up to, the compact gram
 * box and, while editing a meal, a remove button.
 */
@Composable
private fun IngredientRow(
    food: Food,
    amount: String,
    onAmountChange: (String) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val grams = amount.normalizeDecimal().toDoubleOrNull()?.takeIf { it > 0 }
    val totals = FoodPortions.totals(listOfNotNull(grams?.let { SavedMealItem(food, it) }))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = if (onRemove != null) 8.dp else 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            FoodTitleLine(tag = food.tag, title = foodTitle(food.name, food.brand))
            Text(
                compactDetail(totals),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = TITLE_INDENT),
            )
        }
        GramBox(amount = amount, onAmountChange = onAmountChange)
        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.addfood_meal_remove_ingredient_content_description),
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** The compact, right-aligned gram amount of an ingredient. */
@Composable
private fun GramBox(amount: String, onAmountChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    BareNumericTextField(
        value = amount,
        onValueChange = onAmountChange,
        textStyle = MaterialTheme.typography.labelLarge.copy(
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        onFocusChange = { focused = it },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .height(34.dp)
                    .widthIn(min = 64.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape,
                    )
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.widthIn(min = 24.dp, max = 64.dp), contentAlignment = Alignment.CenterEnd) { innerTextField() }
                Text(
                    "g",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 3.dp),
                )
            }
        },
    )
}

/** The kcal/macro preview above a sheet's confirm button. */
@Composable
internal fun TotalsPill(totals: NutritionTotals) {
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

/** Mono-uppercase caption above a group of sheet fields. */
@Composable
internal fun SheetCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** The full-width accent button that ends every data-entry sheet. */
@Composable
internal fun SheetConfirmButton(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}
