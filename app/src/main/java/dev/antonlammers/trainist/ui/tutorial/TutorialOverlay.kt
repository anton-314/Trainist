package dev.antonlammers.trainist.ui.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.antonlammers.trainist.R
import kotlin.math.max

/** Breathing room between the highlighted element and the edge of the cut-out. */
private val SPOTLIGHT_PADDING = 10.dp

/** Gap between the cut-out and the explanation card. */
private val BUBBLE_GAP = 20.dp

private val BUBBLE_INSET = 20.dp

/** How far the pulse ring travels outwards before it fades out. */
private val PULSE_TRAVEL = 14.dp

private val SPOTLIGHT_CORNER = 22.dp

private const val SCRIM_ALPHA = 0.82f

/**
 * The guided tour's overlay: dims the whole app, cuts the current step's element back out of the
 * dim and rings it, and explains it in a card placed on whichever side of the cut-out has room.
 *
 * Drawn over the real screens rather than over screenshots of them — the app underneath is live, so
 * the tour can never show a layout the user doesn't actually have. The cut-out follows the element
 * through [TutorialAnchors]; if the target hasn't been laid out (yet), the card centres itself and
 * no hole is cut, which is also what happens on the frame between two tabs.
 *
 * A tap anywhere advances, the card carries an explicit "next", and every step can be left through
 * "skip" or the system back gesture.
 */
@Composable
fun TutorialOverlay(
    step: TutorialStep,
    anchors: TutorialAnchors,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val anchor = anchors[step.target]
    val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA)
    val ringColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val spotlight = anchor?.inflate(with(density) { SPOTLIGHT_PADDING.toPx() })

    val pulse by rememberInfiniteTransition(label = "tutorialPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "tutorialPulseProgress",
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Own child rather than a modifier on the root: it swallows taps meant for the app
        // underneath while the explanation card above it keeps its own buttons clickable.
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(step) { detectTapGestures { onNext() } },
        )

        Canvas(
            // The cut-out is a Clear-blended draw, which only erases within its own layer — without
            // an offscreen layer it would punch through to the window and show black.
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
        ) {
            drawRect(scrimColor)
            spotlight?.let { drawSpotlight(it, step.highlight, ringColor, pulse) }
        }

        val placeBelow = spotlight == null ||
            spotlight.center.y < with(density) { maxHeight.toPx() } / 2f
        val cardAlignment = when {
            spotlight == null -> Alignment.Center
            placeBelow -> Alignment.TopStart
            else -> Alignment.BottomStart
        }
        val cardPadding = with(density) {
            when {
                spotlight == null -> 0.dp
                placeBelow -> spotlight.bottom.toDp() + BUBBLE_GAP
                else -> (maxHeight - spotlight.top.toDp() + BUBBLE_GAP)
            }
        }

        Box(
            modifier = Modifier
                .align(cardAlignment)
                .padding(
                    top = if (placeBelow) cardPadding else 0.dp,
                    bottom = if (placeBelow) 0.dp else cardPadding,
                )
                .padding(horizontal = BUBBLE_INSET),
        ) {
            TutorialCard(step = step, onNext = onNext, onSkip = onSkip)
        }
    }
}

/** Erases the target from the scrim and rings it: a steady outline plus an outward pulse. */
private fun DrawScope.drawSpotlight(
    spotlight: Rect,
    highlight: TutorialHighlight,
    ringColor: Color,
    pulse: Float,
) {
    val stroke = Stroke(width = 2.dp.toPx())
    val travel = PULSE_TRAVEL.toPx() * pulse
    val pulseColor = ringColor.copy(alpha = (1f - pulse) * 0.6f)

    when (highlight) {
        TutorialHighlight.CIRCLE -> {
            val radius = max(spotlight.width, spotlight.height) / 2f
            drawCircle(Color.Black, radius, spotlight.center, blendMode = BlendMode.Clear)
            drawCircle(ringColor, radius, spotlight.center, style = stroke)
            drawCircle(pulseColor, radius + travel, spotlight.center, style = stroke)
        }

        TutorialHighlight.ROUNDED -> {
            val corner = CornerRadius(SPOTLIGHT_CORNER.toPx())
            drawRoundRect(Color.Black, spotlight.topLeft, spotlight.size, corner, blendMode = BlendMode.Clear)
            drawRoundRect(ringColor, spotlight.topLeft, spotlight.size, corner, style = stroke)
            drawRoundRect(
                color = pulseColor,
                topLeft = spotlight.topLeft - Offset(travel, travel),
                size = Size(spotlight.width + travel * 2, spotlight.height + travel * 2),
                cornerRadius = CornerRadius(corner.x + travel),
                style = stroke,
            )
        }
    }
}

/** The explanation: where you are in the tour, what the ringed element does, and the way on or out. */
@Composable
private fun TutorialCard(step: TutorialStep, onNext: () -> Unit, onSkip: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.tutorial_progress, step.number, TutorialStep.count),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSkip) {
                    Text(
                        stringResource(R.string.tutorial_skip_button),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Text(stringResource(step.title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(step.body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    stringResource(
                        if (step.isLast) R.string.tutorial_done_button else R.string.tutorial_next_button,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
