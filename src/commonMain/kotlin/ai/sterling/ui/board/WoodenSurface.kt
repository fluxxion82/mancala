package ai.sterling.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ai.sterling.ui.theme.BoardColors
import ai.sterling.ui.theme.Dimens
import kotlin.random.Random

@Composable
fun WoodenSurface(
    slots: List<PitSlot>,
    modifier: Modifier = Modifier,
) {
    val grainStrokes = remember { generateGrainStrokes(seed = 42, count = 220) }

    Canvas(modifier = modifier) {
        val cornerPx = Dimens.BoardCorner.toPx()

        drawRoundRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to BoardColors.WoodDark,
                    0.5f to BoardColors.WoodLight,
                    1f to BoardColors.WoodMid,
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            ),
            cornerRadius = CornerRadius(cornerPx),
        )

        // Subtle darker bands across the grain direction.
        for (band in grainStrokes.bands) {
            drawRect(
                color = BoardColors.WoodGrain.copy(alpha = band.alpha),
                topLeft = Offset(0f, band.y * size.height),
                size = Size(size.width, band.height),
            )
        }

        // Long horizontal grain striations.
        for (stroke in grainStrokes.strokes) {
            val y = stroke.y * size.height
            val xStart = stroke.startBias * size.width
            val xEnd = size.width - stroke.endBias * size.width
            val ctrlY = y + stroke.curve
            val path = Path().apply {
                moveTo(xStart, y)
                quadraticTo((xStart + xEnd) / 2f, ctrlY, xEnd, y + stroke.endY)
            }
            drawPath(
                path = path,
                color = BoardColors.WoodGrain.copy(alpha = stroke.alpha),
                style = Stroke(width = stroke.width),
            )
        }

        // Carved pit insets.
        for (slot in slots) {
            drawPitInset(slot)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPitInset(slot: PitSlot) {
    val rect = slot.rect
    val cx = rect.left + rect.width / 2f
    val cy = rect.top + rect.height / 2f
    val maxRadius = maxOf(rect.width, rect.height)

    // Outer dark shadow ring giving the carved depth illusion.
    drawOval(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to BoardColors.PitDeep.copy(alpha = 0f),
                0.85f to BoardColors.PitDeep.copy(alpha = 0f),
                1f to BoardColors.PitDeep.copy(alpha = 0.55f),
            ),
            center = Offset(cx, cy),
            radius = maxRadius * 0.65f,
        ),
        topLeft = Offset(rect.left - 6f, rect.top - 6f),
        size = Size(rect.width + 12f, rect.height + 12f),
    )

    // Pit body
    drawOval(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to BoardColors.PitDeep,
                0.7f to BoardColors.PitMid,
                1f to BoardColors.PitRim,
            ),
            center = Offset(cx, cy),
            radius = maxRadius * 0.55f,
        ),
        topLeft = rect.topLeft,
        size = rect.size,
    )

    // Inner shadow ring (darker oval slightly inside the rim)
    drawOval(
        color = Color.Black.copy(alpha = 0.18f),
        topLeft = Offset(rect.left + 3f, rect.top + 3f),
        size = Size(rect.width - 6f, rect.height - 6f),
        style = Stroke(width = 4f),
    )

    // Upper-left rim highlight arc.
    drawArc(
        color = Color.White.copy(alpha = 0.10f),
        startAngle = 200f,
        sweepAngle = 80f,
        useCenter = false,
        topLeft = Offset(rect.left + 4f, rect.top + 4f),
        size = Size(rect.width - 8f, rect.height - 8f),
        style = Stroke(width = 2f),
    )
}

private data class Grain(
    val strokes: List<GrainStroke>,
    val bands: List<GrainBand>,
)

private data class GrainStroke(
    val y: Float,
    val startBias: Float,
    val endBias: Float,
    val curve: Float,
    val endY: Float,
    val alpha: Float,
    val width: Float,
)

private data class GrainBand(
    val y: Float,
    val height: Float,
    val alpha: Float,
)

private fun generateGrainStrokes(seed: Int, count: Int): Grain {
    val rng = Random(seed)
    val strokes = List(count) {
        GrainStroke(
            y = rng.nextFloat(),
            startBias = rng.nextFloat() * 0.18f,
            endBias = rng.nextFloat() * 0.18f,
            curve = (rng.nextFloat() - 0.5f) * 14f,
            endY = (rng.nextFloat() - 0.5f) * 6f,
            alpha = 0.05f + rng.nextFloat() * 0.06f,
            width = 0.7f + rng.nextFloat() * 0.6f,
        )
    }
    val bands = List(8) {
        GrainBand(
            y = rng.nextFloat(),
            height = 6f + rng.nextFloat() * 14f,
            alpha = 0.03f + rng.nextFloat() * 0.04f,
        )
    }
    return Grain(strokes, bands)
}
