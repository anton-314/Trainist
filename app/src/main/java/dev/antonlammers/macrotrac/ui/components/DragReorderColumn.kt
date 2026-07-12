package dev.antonlammers.macrotrac.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

/**
 * A plain, non-lazy vertical list of [items] that can be reordered by long-pressing and dragging a
 * dedicated handle — the one drag-and-drop pattern used app-wide for reorderable lists (template
 * exercise slots, session set rows, stats cards, the templates list itself), replacing the previous
 * mix of up/down arrows and a reorder menu. Meant for short lists nested inside a single outer
 * `LazyColumn` item, not a scrolling list of its own.
 *
 * Long-pressing the handle picks the row up (a short haptic tick confirms the grab) and the row
 * follows the finger; crossing a neighbour's mid-height calls [onMove] immediately — so it can fire
 * repeatedly through one continuous drag, letting a row travel the whole list (e.g. first ↔ last) in
 * a single gesture rather than one swap per press. On release the row settles smoothly back into
 * place. [key] must be stable and unique per item (survives reordering): each row is wrapped in a
 * [key] block so its composition — including the in-progress drag gesture and the placement
 * animation — **moves with the logical item** when the list reorders underneath it, instead of being
 * torn down and cancelling the drag after the first swap.
 *
 * While one row is dragged, the others animate into their new slots ([animatePlacement]) so a swap
 * reads as a smooth shift rather than an instant jump.
 */
@Composable
fun <T> DragReorderColumn(
    items: List<T>,
    key: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    itemContent: @Composable (
        index: Int,
        item: T,
        rowModifier: Modifier,
        dragHandleModifier: Modifier,
        isDragging: Boolean,
    ) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentItems by rememberUpdatedState(items)
    val currentOnMove by rememberUpdatedState(onMove)
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val itemHeightsPx = remember { mutableStateMapOf<Any, Int>() }

    Column(modifier = modifier, verticalArrangement = verticalArrangement) {
        items.forEachIndexed { index, item ->
            val itemKey = key(item)
            key(itemKey) {
                val isDragging = itemKey == draggingKey

                val rowModifier = Modifier
                    .onGloballyPositioned { itemHeightsPx[itemKey] = it.size.height }
                    .zIndex(if (isDragging) 1f else 0f)
                    // The dragged row follows the finger via [graphicsLayer]; the rest animate the
                    // layout shift when a swap moves them. Applying both to the dragged row would make
                    // the placement animation fight the manual offset, so it is disabled while dragging.
                    .then(if (isDragging) Modifier else Modifier.animatePlacement())
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffsetPx else 0f
                        shadowElevation = if (isDragging) 6f else 0f
                    }

                val dragHandleModifier = Modifier.pointerInput(itemKey) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            draggingKey = itemKey
                            dragOffsetPx = 0f
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragEnd = {
                            // Ease the leftover offset (up to half a row) back to rest, then drop the
                            // dragging state so the row re-joins the placement-animated flow.
                            val settleFrom = dragOffsetPx
                            scope.launch {
                                Animatable(settleFrom).animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                ) { dragOffsetPx = value }
                                draggingKey = null
                            }
                        },
                        onDragCancel = { draggingKey = null; dragOffsetPx = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetPx += dragAmount.y
                            val heightPx = itemHeightsPx[itemKey]?.toFloat()
                                ?: return@detectDragGesturesAfterLongPress
                            var current = currentItems.indexOfFirst { key(it) == itemKey }
                            while (dragOffsetPx > heightPx / 2 && current < currentItems.lastIndex) {
                                currentOnMove(current, current + 1)
                                dragOffsetPx -= heightPx
                                current += 1
                            }
                            while (dragOffsetPx < -heightPx / 2 && current > 0) {
                                currentOnMove(current, current - 1)
                                dragOffsetPx += heightPx
                                current -= 1
                            }
                        },
                    )
                }

                itemContent(index, item, rowModifier, dragHandleModifier, isDragging)
            }
        }
    }
}

/**
 * Animates this element's position within its parent: whenever layout places it somewhere new (e.g. a
 * reorder shifts it by a row), it slides from its previous spot to the new one instead of jumping.
 * Standard `onPlaced` + `offset` placement-animation pattern.
 */
private fun Modifier.animatePlacement(): Modifier = composed {
    val scope = rememberCoroutineScope()
    var targetOffset by remember { mutableStateOf(IntOffset.Zero) }
    var animatable by remember { mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null) }
    this
        .onPlaced { targetOffset = it.positionInParent().round() }
        .offset {
            val anim = animatable
                ?: Animatable(targetOffset, IntOffset.VectorConverter).also { animatable = it }
            if (anim.targetValue != targetOffset) {
                scope.launch {
                    anim.animateTo(targetOffset, spring(stiffness = Spring.StiffnessMediumLow))
                }
            }
            anim.value - targetOffset
        }
}
