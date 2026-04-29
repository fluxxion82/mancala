package ai.sterling.ui.board

import ai.sterling.ui.theme.BoardColors
import ai.sterling.ui.theme.Dimens
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit

/**
 * Renders the count of stones in a single pit, positioned at the slot's bottom-center
 * for regular pits or at the top of the well for mancalas. Uses parchment color with a
 * dark shadow so it stays legible against marbles or wood.
 */
@Composable
fun PitCountLabel(
    count: Int,
    slot: PitSlot,
    modifier: Modifier = Modifier,
) {
    if (count <= 0 && !slot.isMancala) return

    val fontSize: TextUnit = if (slot.isMancala) Dimens.MancalaCountFontSize else Dimens.PitCountFontSize
    val color = if (slot.isMancala) BoardColors.Parchment else BoardColors.ParchmentDim

    val anchor = if (slot.isMancala) {
        // Top of the mancala well.
        Offset(slot.center.x, slot.rect.top + slot.height * 0.10f)
    } else {
        // Bottom edge of the pit, slightly inside.
        Offset(slot.center.x, slot.rect.bottom - slot.height * 0.10f)
    }

    Box(
        modifier = modifier
            .layout { measurable, _ ->
                val placeable = measurable.measure(Constraints())
                layout(placeable.width, placeable.height) {
                    placeable.place(
                        IntOffset(
                            (anchor.x - placeable.width / 2f).toInt(),
                            (anchor.y - placeable.height / 2f).toInt(),
                        )
                    )
                }
            },
    ) {
        Text(
            text = count.toString(),
            color = color,
            fontWeight = if (slot.isMancala) FontWeight.Bold else FontWeight.SemiBold,
            style = TextStyle(
                fontSize = fontSize,
                shadow = Shadow(
                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                    offset = Offset(1f, 2f),
                    blurRadius = 3f,
                ),
            ),
        )
    }
}
