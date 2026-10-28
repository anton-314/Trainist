package dev.antonlammers.trainist.ui.addfood

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.FoodTag
import dev.antonlammers.trainist.ui.components.BareNumericTextField
import dev.antonlammers.trainist.ui.components.TagSelector
import dev.antonlammers.trainist.ui.theme.CalorieColor
import dev.antonlammers.trainist.ui.theme.CarbsColor
import dev.antonlammers.trainist.ui.theme.FatColor
import dev.antonlammers.trainist.ui.theme.ProteinColor
import dev.antonlammers.trainist.util.normalizeDecimal

/**
 * The custom-food form's values, as typed. Held outside the form's composition so the create sheet
 * can switch to "Mahlzeit" and back without losing what was entered; [initial] fills it for an edit,
 * [prefillName] for "„skyr“ als eigenes Lebensmittel anlegen" from the search.
 */
@Stable
internal class CustomFoodFormState(private val initial: Food? = null, prefillName: String = "") {
    var name by mutableStateOf(initial?.name ?: prefillName)
    var brand by mutableStateOf(initial?.brand.orEmpty())
    var kcal by mutableStateOf(initial?.kcalPer100g?.formatAmount().orEmpty())
    var protein by mutableStateOf(initial?.proteinPer100g?.formatAmount().orEmpty())
    var carbs by mutableStateOf(initial?.carbsPer100g?.formatAmount().orEmpty())
    var fat by mutableStateOf(initial?.fatPer100g?.formatAmount().orEmpty())
    var sugar by mutableStateOf(initial?.sugarPer100g.optionalText())
    var fiber by mutableStateOf(initial?.fiberPer100g.optionalText())
    var salt by mutableStateOf(initial?.saltPer100g.optionalText())
    var tag by mutableStateOf(initial?.tag ?: FoodTag.NONE)

    /** The optional nutrients start opened when there is something in them to see. */
    var optionalExpanded by mutableStateOf(listOf(sugar, fiber, salt).any { it.isNotEmpty() })

    /** Name, kcal and the three macros are required; the optional nutrients are not. */
    val isValid: Boolean
        get() = name.isNotBlank() && listOf(kcal, protein, carbs, fat).all { it.toAmount() != null }

    /** The food to store — keeping the id of the one being edited. Null unless [isValid]. */
    fun toFood(): Food? {
        if (!isValid) return null
        return Food(
            id = initial?.id ?: "",
            name = name.trim(),
            brand = brand.trim().takeIf { it.isNotBlank() },
            kcalPer100g = kcal.toAmount()!!,
            proteinPer100g = protein.toAmount()!!,
            carbsPer100g = carbs.toAmount()!!,
            fatPer100g = fat.toAmount()!!,
            sugarPer100g = sugar.toAmount() ?: 0.0,
            fiberPer100g = fiber.toAmount() ?: 0.0,
            saltPer100g = salt.toAmount() ?: 0.0,
            tag = tag,
        )
    }

    private fun String.toAmount(): Double? = normalizeDecimal().toDoubleOrNull()

    /** 0 is how the domain stores "not given", so it shows as an empty field. */
    private fun Double?.optionalText(): String = this?.takeIf { it > 0 }?.formatAmount().orEmpty()
}

/**
 * The custom-food body of [CreateSheet] — name and brand, the four required nutrients as tiles, the
 * optional ones folded away, the tag, and the save button pinned below the scrolling fields.
 */
@Composable
internal fun ColumnScope.CustomFoodForm(
    state: CustomFoodFormState,
    onSave: (Food) -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormTextField(state.name, { state.name = it }, stringResource(R.string.addfood_field_name), Modifier.weight(3f))
            FormTextField(state.brand, { state.brand = it }, stringResource(R.string.addfood_field_brand), Modifier.weight(2f))
        }

        SheetCaption(stringResource(R.string.addfood_nutrition_per_100g_label), Modifier.padding(top = 4.dp))
        val kcalUnit = stringResource(R.string.addfood_unit_kcal)
        val gramUnit = stringResource(R.string.addfood_unit_grams)
        TileGrid(
            listOf(
                TileSpec(R.string.addfood_tile_kcal, CalorieColor, kcalUnit, state.kcal) { state.kcal = it },
                TileSpec(R.string.addfood_tile_protein, ProteinColor, gramUnit, state.protein) { state.protein = it },
                TileSpec(R.string.addfood_tile_carbs, CarbsColor, gramUnit, state.carbs) { state.carbs = it },
                TileSpec(R.string.addfood_tile_fat, FatColor, gramUnit, state.fat) { state.fat = it },
            ),
        )

        OptionalToggle(expanded = state.optionalExpanded, onToggle = { state.optionalExpanded = !state.optionalExpanded })
        AnimatedVisibility(
            visible = state.optionalExpanded,
            enter = expandVertically(tween(EXPAND_MS)) + fadeIn(tween(EXPAND_MS)),
            exit = shrinkVertically(tween(EXPAND_MS)) + fadeOut(tween(EXPAND_MS)),
        ) {
            TileGrid(
                listOf(
                    TileSpec(R.string.addfood_tile_sugar, null, gramUnit, state.sugar) { state.sugar = it },
                    TileSpec(R.string.addfood_tile_fiber, null, gramUnit, state.fiber) { state.fiber = it },
                    TileSpec(R.string.addfood_tile_salt, null, gramUnit, state.salt) { state.salt = it },
                ),
            )
        }

        TagSelector(selected = state.tag, onSelected = { state.tag = it }, modifier = Modifier.padding(top = 4.dp))
    }
    SheetConfirmButton(
        label = stringResource(R.string.common_save),
        enabled = state.isValid,
        onClick = { state.toFood()?.let(onSave) },
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
    )
}

@Composable
private fun FormTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outline),
        modifier = modifier,
    )
}

private class TileSpec(
    val label: Int,
    val dot: Color?,
    val unit: String,
    val value: String,
    val onValueChange: (String) -> Unit,
)

/** Input tiles two to a row; an odd last tile keeps its half width. */
@Composable
private fun TileGrid(tiles: List<TileSpec>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile -> NutrientTile(tile, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * One nutrient per 100 g: a coloured dot and mono label over the value in large serif type. The whole
 * tile is the text field — a tap anywhere on it focuses the number (and selects it, like every numeric
 * field), and the accent border shows which tile is being typed into.
 */
@Composable
private fun NutrientTile(tile: TileSpec, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    BareNumericTextField(
        value = tile.value,
        onValueChange = tile.onValueChange,
        textStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp, lineHeight = 32.sp),
        onFocusChange = { focused = it },
        modifier = modifier,
        decorationBox = { innerTextField ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(if (focused) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (tile.dot != null) Box(Modifier.size(7.dp).clip(CircleShape).background(tile.dot))
                    Text(
                        stringResource(tile.label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Only as wide as the number, so the unit sits right behind it.
                    Box(Modifier.weight(1f, fill = false).width(IntrinsicSize.Max).alignByBaseline()) {
                        if (tile.value.isEmpty()) {
                            Text(
                                "0",
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp, lineHeight = 32.sp),
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        innerTextField()
                    }
                    Text(
                        tile.unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
        },
    )
}

/** The fold for sugar, fibre and salt — they rarely matter, so they stay out of the way until asked for. */
@Composable
private fun OptionalToggle(expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(EXPAND_MS), label = "optionalChevron")
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onToggle)
            .padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.addfood_optional_nutrients),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.addfood_optional_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Icon(
            Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(rotation),
        )
    }
}

private const val EXPAND_MS = 200
