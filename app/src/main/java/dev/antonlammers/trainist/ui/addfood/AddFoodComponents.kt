package dev.antonlammers.trainist.ui.addfood

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.NutritionTotals
import dev.antonlammers.trainist.domain.model.FoodTag
import dev.antonlammers.trainist.ui.components.MacroBar
import dev.antonlammers.trainist.ui.components.TagDot
import dev.antonlammers.trainist.ui.theme.LocalInkTheme

// ─────────────────────────────────────────────────────────────────────────────
// Header + search
// ─────────────────────────────────────────────────────────────────────────────

/** Serif title of the current list, what is being logged for beneath it, and the round close button. */
@Composable
internal fun AddFoodHeader(
    title: String,
    subtitle: String,
    closeContentDescription: String,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.displaySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = closeContentDescription, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * The one search over everything (own foods and meals, history, Open Food Facts) — identical on every
 * tab — and the square barcode tile beside it. Empty, its two-line hint names what it searches.
 */
@Composable
internal fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onScanClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            interactionSource = interaction,
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .height(52.dp)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape,
                        )
                        .padding(start = 14.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(22.dp),
                    )
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Column {
                                Text(
                                    stringResource(R.string.addfood_search_hint),
                                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 20.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                // Once focused, the hint has done its job; one line reads calmer next to the cursor.
                                if (!focused) {
                                    Text(
                                        stringResource(R.string.addfood_search_sources_hint),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            lineHeight = 13.sp,
                                            letterSpacing = 1.2.sp,
                                        ),
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        innerTextField()
                    }
                    if (query.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { onQueryChange("") },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Cancel,
                                contentDescription = stringResource(R.string.addfood_search_clear_content_description),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            },
        )
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .clickable(onClick = onScanClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.QrCodeScanner,
                contentDescription = stringResource(R.string.addfood_scan_barcode_content_description),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** The search's source filter: one chip per result group, each with its live hit count. */
@Composable
internal fun SourceChips(
    sections: SearchSections,
    selected: SearchSource,
    onSelect: (SearchSource) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SearchSource.entries.forEach { source ->
            val isSelected = source == selected
            val shape = RoundedCornerShape(8.dp)
            val content = if (isSelected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(shape)
                    .then(
                        if (isSelected) {
                            Modifier.background(MaterialTheme.colorScheme.onSurface)
                        } else {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                        },
                    )
                    .clickable { onSelect(source) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(source.labelRes()),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                    color = content,
                    maxLines = 1,
                )
                sections.count(source)?.let { count ->
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                        color = content.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

private fun SearchSource.labelRes(): Int = when (this) {
    SearchSource.ALL -> R.string.addfood_source_all
    SearchSource.MINE -> R.string.addfood_source_mine
    SearchSource.HISTORY -> R.string.addfood_source_history
    SearchSource.OPEN_FOOD_FACTS -> R.string.addfood_source_open_food_facts
}

// ─────────────────────────────────────────────────────────────────────────────
// List vocabulary
// ─────────────────────────────────────────────────────────────────────────────

/** Mono caption above a list: what it holds on the left, how it is sorted on the right. */
@Composable
internal fun ListCaption(start: String, end: String?) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
        Text(
            start,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (end != null) {
            Text(end, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** A day of the history: its name in accent serif, the kcal it added up to on the right. */
@Composable
internal fun DayHeader(day: String, kcal: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            day,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(kcal, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A search result group: outlined icon + mono title, the group's hit count on the right. */
@Composable
internal fun SearchGroupHeader(icon: ImageVector, title: String, count: Int?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(15.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** Indent of a card's detail line, so it starts under the title rather than under the tag dot. */
internal val TITLE_INDENT = 13.dp

/** Tag dot (an empty slot when untagged, so titles stay aligned) and a one-line title. */
@Composable
internal fun FoodTitleLine(tag: FoodTag, title: AnnotatedString) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp)) { TagDot(tag, size = 7.dp) }
        Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A food's title: [name] with the part matching the search in bold ([highlight]), followed by a quiet
 * " · [secondary]" (the brand, or "Mahlzeit" for a meal in the search).
 */
@Composable
internal fun foodTitle(name: String, secondary: String?, highlight: IntRange? = null, trailing: String? = null): AnnotatedString {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    return buildAnnotatedString {
        if (highlight == null) {
            append(name)
        } else {
            append(name.substring(0, highlight.first))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name.substring(highlight)) }
            append(name.substring(highlight.last + 1))
        }
        if (!secondary.isNullOrBlank()) withStyle(SpanStyle(color = quiet)) { append(" · $secondary") }
        if (trailing != null) append(trailing)
    }
}

/** "352 KCAL · 25P · 47K · 4F" for what an amount (or a whole meal) adds up to. */
@Composable
internal fun compactDetail(totals: NutritionTotals): String = stringResource(
    R.string.addfood_food_detail_compact,
    totals.kcal.toInt(),
    totals.proteinG.toInt(),
    totals.carbsG.toInt(),
    totals.fatG.toInt(),
)

internal val CardShape = RoundedCornerShape(18.dp)
internal val MealCardShape = RoundedCornerShape(24.dp)

/** One food as a card: title line over a mono detail line, an optional trailing icon or menu. */
@Composable
internal fun FoodCard(
    tag: FoodTag,
    title: AnnotatedString,
    detail: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = if (trailing != null) 8.dp else 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            FoodTitleLine(tag, title)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = TITLE_INDENT),
            )
        }
        trailing?.invoke()
    }
}

/** A quiet 18–20 dp glyph at a card's end telling where a result comes from. */
@Composable
internal fun TrailingGlyph(icon: ImageVector, size: Dp = 18.dp) {
    Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(size))
    }
}

/** A saved meal: serif name, its ingredients in one line, then kcal · macro bar · gram split. */
@Composable
internal fun MealCard(
    name: String,
    ingredients: String,
    totals: NutritionTotals,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MealCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp))
                Text(
                    ingredients,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ItemMenuButton(onEdit = onEdit, onDelete = onDelete)
        }
        Row(
            modifier = Modifier.padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.addfood_day_kcal, totals.kcal.toInt()),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
            )
            MacroBar(totals.proteinG, totals.carbsG, totals.fatG, Modifier.weight(1f))
            Text(
                stringResource(
                    R.string.addfood_macros_compact,
                    totals.proteinG.toInt(),
                    totals.carbsG.toInt(),
                    totals.fatG.toInt(),
                ),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** ⋮ with Bearbeiten / Löschen — the visible way to what the swipe gestures also do. */
@Composable
internal fun ItemMenuButton(onEdit: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.addfood_more_options_content_description),
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_edit)) },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = { open = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_delete)) },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

/**
 * Swipe gestures on a card: end-to-start deletes, start-to-end (when [onEdit] is given) opens the
 * editor and snaps back. The colour behind is clipped to the card's [shape], so it only shows as the
 * card slides away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeActions(
    shape: Shape,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
                SwipeToDismissBoxValue.StartToEnd -> { onEdit?.invoke(); false }
                else -> false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = onEdit != null,
        backgroundContent = {
            val target = dismissState.targetValue
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(
                        when (target) {
                            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                            SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                            else -> Color.Transparent
                        },
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = if (target == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                when (target) {
                    SwipeToDismissBoxValue.EndToStart -> Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    SwipeToDismissBoxValue.StartToEnd -> Icon(
                        Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.common_edit),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    else -> Unit
                }
            }
        },
    ) { content() }
}

/** Grey placeholder cards standing in for Open Food Facts results while they load. */
@Composable
internal fun SkeletonCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth(0.55f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
        Box(Modifier.fillMaxWidth(0.8f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

/** One quiet line in place of the Open Food Facts results when it can't be reached, with a retry. */
@Composable
internal fun OnlineSearchFailed(onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.addfood_online_search_failed) + " · ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.addfood_online_search_retry),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onRetry),
        )
    }
}

/** A centred accent text button with a leading plus — "„skyr“ als eigenes Lebensmittel anlegen". */
@Composable
internal fun AccentTextAction(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** A plain centred hint for a list with nothing to show. */
@Composable
internal fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    )
}

/**
 * An empty list as an invitation: icon in a ring, serif title, one sentence, and — where the list can
 * be filled from here — the accent button plus an optional quieter second way in.
 */
@Composable
internal fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    action: Pair<String, () -> Unit>? = null,
    secondary: Triple<ImageVector, String, () -> Unit>? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 30.sp), textAlign = TextAlign.Center)
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 270.dp),
        )
        if (action != null) {
            Row(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = action.second)
                    .padding(start = 16.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                Text(action.first, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        if (secondary != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = secondary.third)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(secondary.first, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(secondary.second, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Overlays above the list
// ─────────────────────────────────────────────────────────────────────────────

/** Lets the list fade into the background behind the floating bar instead of being cut off by it. */
@Composable
internal fun BoxScope.BottomFade() {
    val background = MaterialTheme.colorScheme.background
    Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to background)),
        )
        // Below the fade, the navigation-bar inset itself is solid background too.
        Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).background(background))
    }
}

/** One entry of the plus button's menu. */
internal class AddMenuItem(val icon: ImageVector, val title: String, val subtitle: String, val onClick: () -> Unit)

/**
 * The plus button's menu on the history tab: a scrim over the screen (the floating bar stays above
 * it) and a card of create options rising from the button's corner. A tap on the scrim closes it.
 */
@Composable
internal fun BoxScope.AddMenu(visible: Boolean, items: List<AddMenuItem>, onDismiss: () -> Unit) {
    val scrim = if (LocalInkTheme.current) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(MENU_MS)),
        exit = fadeOut(tween(MENU_MS)),
        modifier = Modifier.matchParentSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(MENU_MS)) + scaleIn(tween(MENU_MS), initialScale = 0.85f, transformOrigin = TransformOrigin(1f, 1f)),
        exit = fadeOut(tween(MENU_MS)) + scaleOut(tween(MENU_MS), targetScale = 0.85f, transformOrigin = TransformOrigin(1f, 1f)),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(end = 16.dp, bottom = MENU_BOTTOM_OFFSET),
    ) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            modifier = Modifier
                .width(248.dp)
                .shadow(16.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = item.onClick)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Text(item.title, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, lineHeight = 20.sp))
                        Text(
                            item.subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Menu ↔ scrim fade and the menu's rise from the button. */
private const val MENU_MS = 150

/** The floating bar's bottom inset (20) + its height (60) + a 12 dp gap. */
private val MENU_BOTTOM_OFFSET = 92.dp
