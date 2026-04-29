package ai.sterling.ui.board

import ai.sterling.model.Board
import ai.sterling.model.HumanSide
import ai.sterling.ui.animation.MancalaBoardAnimationState
import ai.sterling.ui.animation.SowingAnimator
import ai.sterling.ui.theme.BoardColors
import ai.sterling.ui.theme.Dimens
import ai.sterling.ui.theme.LocalBoardScale
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
import androidx.compose.runtime.CompositionLocalProvider
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
    animationState: MancalaBoardAnimationState,
    onPitClick: (Int) -> Unit,
    isLegalMove: (Int) -> Boolean,
    activeMancala: Int?,
    humanSide: HumanSide?,
    modifier: Modifier = Modifier,
) {
    fun isClickablePit(index: Int): Boolean =
        isLegalMove(index) && !animationState.isAnimating

    BoxWithConstraints(
        modifier = modifier.background(BoardColors.TableFelt),
    ) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        val basePitPx = with(density) { Dimens.PitDiameter.toPx() }
        val baseMancalaWPx = with(density) { Dimens.MancalaWidth.toPx() }
        val baseMancalaHPx = with(density) { Dimens.MancalaHeight.toPx() }
        val basePaddingPx = with(density) { Dimens.BoardPadding.toPx() }
        val baseSpacingPx = with(density) { Dimens.PitSpacing.toPx() }

        // Natural (unscaled) board footprint — used to compute a uniform scale that
        // shrinks the board to fit narrow hosts like an in-page embed.
        val naturalWidthPx = basePaddingPx * 2 + baseMancalaWPx * 2 +
            baseSpacingPx * 4 + basePitPx * 6 + baseSpacingPx * 5
        val naturalHeightPx = basePaddingPx * 2 + baseMancalaHPx
        val scale = minOf(
            if (naturalWidthPx > 0f) widthPx / naturalWidthPx else 1f,
            if (naturalHeightPx > 0f) heightPx / naturalHeightPx else 1f,
            1f,
        ).coerceAtLeast(0.1f)

        val pitPx = basePitPx * scale
        val mancalaWPx = baseMancalaWPx * scale
        val mancalaHPx = baseMancalaHPx * scale
        val paddingPx = basePaddingPx * scale
        val spacingPx = baseSpacingPx * scale

        val slots = remember(widthPx, heightPx, pitPx, mancalaWPx, mancalaHPx, paddingPx, spacingPx, humanSide) {
            computePitSlots(widthPx, heightPx, pitPx, mancalaWPx, mancalaHPx, paddingPx, spacingPx, humanSide)
        }

        // Push computed centers/bounds into the animation state. This is the analogue of
        // onGloballyPositioned, but cheaper because we know the layout arithmetic.
        LaunchedEffect(slots) {
            for (slot in slots) {
                animationState.pitCenters[slot.index] = slot.center
                animationState.pitBounds[slot.index] = slot.rect
            }
        }

        CompositionLocalProvider(LocalBoardScale provides scale) {
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
            val hidden = animationState.inFlight.keys
            for (slot in slots) {
                val pitRadiusDp = with(density) { (slot.width / 2f).toDp() }
                val stones = animationState.visualStones.filter { it.pit == slot.index }
                val isHoveredAndClickable = !slot.isMancala &&
                    animationState.hoveredPit.value == slot.index &&
                    isClickablePit(slot.index)
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
                    count = animationState.visibleCount(slot.index),
                    slot = slot,
                )
            }

            // Layer 2.7: clickable hit-targets layered over the pit areas.
            for (slot in slots) {
                if (slot.isMancala) continue
                ClickableHitArea(
                    rect = slot.rect,
                    isClickable = isClickablePit(slot.index),
                    onHoverChange = { hovered ->
                        if (hovered) animationState.hoveredPit.value = slot.index
                        else if (animationState.hoveredPit.value == slot.index) animationState.hoveredPit.value = null
                    },
                    onClick = { onPitClick(slot.index) },
                )
            }

            // Layer 3: in-flight stones overlay.
            SowingAnimator(state = animationState)
        }
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
    humanSide: HumanSide?,
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

    // When the human plays as Player 2, flip the board so their pits are on the
    // bottom (the side closest to the player). The animation system and click
    // gating are already keyed on absolute pocket index, so this is a pure
    // layout-time relabel — no other code needs to change.
    val flipped = humanSide == HumanSide.PLAYER_TWO
    val leftMancalaIndex = if (flipped) Board.PLAYER_ONE_MANCALA else Board.PLAYER_TWO_MANCALA
    val rightMancalaIndex = if (flipped) Board.PLAYER_TWO_MANCALA else Board.PLAYER_ONE_MANCALA

    return buildList {
        add(PitSlot(leftMancalaIndex, leftMancalaCenter, mancalaWPx, mancalaHPx))
        for (col in 0 until 6) {
            val cx = pitsStartX + col * (pitPx + spacingPx)
            val topIndex = if (flipped) (5 - col) else (12 - col)
            val bottomIndex = if (flipped) (7 + col) else col
            add(PitSlot(topIndex, Offset(cx, pitsTopY), pitPx, pitPx))
            add(PitSlot(bottomIndex, Offset(cx, pitsBottomY), pitPx, pitPx))
        }
        add(PitSlot(rightMancalaIndex, rightMancalaCenter, mancalaWPx, mancalaHPx))
    }
}

