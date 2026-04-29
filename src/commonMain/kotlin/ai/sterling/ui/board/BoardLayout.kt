package ai.sterling.ui.board

import ai.sterling.ui.animation.MancalaController
import ai.sterling.ui.animation.SowingAnimator
import ai.sterling.ui.theme.BoardColors
import ai.sterling.ui.theme.Dimens
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun BoardLayout(
    controller: MancalaController,
    onPitClick: (Int) -> Unit,
    activeMancala: Int?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.background(BoardColors.TableFelt),
    ) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        val pitPx = with(density) { Dimens.PitDiameter.toPx() }
        val mancalaWPx = with(density) { Dimens.MancalaWidth.toPx() }
        val mancalaHPx = with(density) { Dimens.MancalaHeight.toPx() }
        val paddingPx = with(density) { Dimens.BoardPadding.toPx() }
        val spacingPx = with(density) { Dimens.PitSpacing.toPx() }

        val slots = remember(widthPx, heightPx, pitPx, mancalaWPx, mancalaHPx, paddingPx, spacingPx) {
            computePitSlots(widthPx, heightPx, pitPx, mancalaWPx, mancalaHPx, paddingPx, spacingPx)
        }

        // Push computed centers/bounds into the controller. This is the analogue of
        // onGloballyPositioned, but cheaper because we know the layout arithmetic.
        LaunchedEffect(slots) {
            for (slot in slots) {
                controller.pitCenters[slot.index] = slot.center
                controller.pitBounds[slot.index] = slot.rect
            }
        }

        Box(modifier = Modifier.matchParentSize()) {
            WoodenSurface(slots = slots, modifier = Modifier.matchParentSize())

            // Layer 1.5: soft glow on the active player's mancala (above wood, behind stones).
            if (activeMancala != null) {
                slots.firstOrNull { it.index == activeMancala }?.let { slot ->
                    MancalaGlow(slot = slot, modifier = Modifier.matchParentSize())
                }
            }

            // Layer 2: stones at rest, one cluster per pit. Stones currently in flight are
            // hidden here and rendered by SowingAnimator on the top overlay.
            val hidden = controller.inFlight.keys
            for (slot in slots) {
                val pitRadiusDp = with(density) { (slot.width / 2f).toDp() }
                val stones = controller.visualStones.filter { it.pit == slot.index }
                val isHoveredAndClickable = !slot.isMancala &&
                    controller.hoveredPit.value == slot.index &&
                    controller.isClickablePit(slot.index)
                val liftTarget = if (isHoveredAndClickable) -with(density) { 8.dp.toPx() } else 0f
                val liftPx by animateFloatAsState(
                    targetValue = liftTarget,
                    animationSpec = tween(durationMillis = 140),
                    label = "pit-hover-lift",
                )
                StoneCluster(
                    stones = stones,
                    pitCenter = slot.center,
                    pitRadius = pitRadiusDp,
                    modifier = Modifier.matchParentSize(),
                    hiddenIds = hidden,
                    liftYPx = liftPx,
                )
            }

            // Layer 2.5: count labels (above marbles for legibility).
            for (slot in slots) {
                PitCountLabel(
                    count = controller.visibleCount(slot.index),
                    slot = slot,
                )
            }

            // Layer 2.7: clickable hit-targets layered over the pit areas.
            for (slot in slots) {
                if (slot.isMancala) continue
                ClickableHitArea(
                    rect = slot.rect,
                    isClickable = controller.isClickablePit(slot.index),
                    onHoverChange = { hovered ->
                        if (hovered) controller.hoveredPit.value = slot.index
                        else if (controller.hoveredPit.value == slot.index) controller.hoveredPit.value = null
                    },
                    onClick = { onPitClick(slot.index) },
                )
            }

            // Layer 3: in-flight stones overlay.
            SowingAnimator(controller = controller)
        }
    }
}

@Composable
private fun ClickableHitArea(
    rect: Rect,
    isClickable: Boolean,
    onHoverChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    LaunchedEffect(hovered, isClickable) {
        onHoverChange(hovered && isClickable)
    }

    Box(
        modifier = Modifier
            .layout { measurable, _ ->
                val placeable = measurable.measure(
                    Constraints.fixed(rect.width.toInt(), rect.height.toInt())
                )
                layout(rect.width.toInt(), rect.height.toInt()) {
                    placeable.place(IntOffset(rect.left.toInt(), rect.top.toInt()))
                }
            }
            .hoverable(interactionSource)
            .then(if (isClickable) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .clickable(enabled = isClickable, onClick = onClick),
    )
}

private fun computePitSlots(
    widthPx: Float,
    heightPx: Float,
    pitPx: Float,
    mancalaWPx: Float,
    mancalaHPx: Float,
    paddingPx: Float,
    spacingPx: Float,
): List<PitSlot> {
    val pitsRowWidth = pitPx * 6 + spacingPx * 5
    val naturalBoardWidth = paddingPx * 2 + mancalaWPx * 2 + spacingPx * 4 + pitsRowWidth
    val naturalBoardHeight = paddingPx * 2 + mancalaHPx

    val originX = ((widthPx - naturalBoardWidth) / 2f).coerceAtLeast(0f) + paddingPx
    val originY = ((heightPx - naturalBoardHeight) / 2f).coerceAtLeast(0f) + paddingPx

    val leftMancalaCenter = Offset(
        x = originX + mancalaWPx / 2f,
        y = originY + mancalaHPx / 2f,
    )
    val rightMancalaCenter = Offset(
        x = originX + mancalaWPx + spacingPx * 2 + pitsRowWidth + spacingPx * 2 + mancalaWPx / 2f,
        y = originY + mancalaHPx / 2f,
    )
    val pitsStartX = originX + mancalaWPx + spacingPx * 2 + pitPx / 2f
    val gap = (mancalaHPx - pitPx * 2 - spacingPx) / 2f
    val pitsTopY = originY + gap + pitPx / 2f
    val pitsBottomY = pitsTopY + pitPx + spacingPx

    return buildList {
        add(PitSlot(13, leftMancalaCenter, mancalaWPx, mancalaHPx))
        for (col in 0 until 6) {
            val cx = pitsStartX + col * (pitPx + spacingPx)
            add(PitSlot(12 - col, Offset(cx, pitsTopY), pitPx, pitPx))
            add(PitSlot(col, Offset(cx, pitsBottomY), pitPx, pitPx))
        }
        add(PitSlot(6, rightMancalaCenter, mancalaWPx, mancalaHPx))
    }
}

