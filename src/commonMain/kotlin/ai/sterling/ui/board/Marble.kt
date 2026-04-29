package ai.sterling.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import ai.sterling.ui.theme.BoardColors
import ai.sterling.ui.theme.Dimens
import ai.sterling.ui.theme.darken
import ai.sterling.ui.theme.lighten
import androidx.compose.ui.unit.Dp
import kotlin.math.absoluteValue

@Composable
fun Marble(
    colorSeed: Int,
    modifier: Modifier = Modifier,
    radius: Dp = Dimens.MarbleRadius,
    alpha: Float = 1f,
) {
    val body = BoardColors.MarblePalette[colorSeed.absoluteValue % BoardColors.MarblePalette.size]

    Canvas(
        modifier = modifier
            .alpha(alpha)
            .let { it then Modifier },
    ) {
        val r = radius.toPx()
        val diameter = r * 2f
        val center = Offset(r, r)

        // Drop shadow ellipse below
        drawOval(
            color = Color.Black.copy(alpha = 0.32f),
            topLeft = Offset(-r * 0.05f, r * 0.55f),
            size = Size(diameter * 1.10f, diameter * 0.55f),
        )

        // Body
        val highlightCenter = Offset(center.x - r * 0.32f, center.y - r * 0.32f)
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to body.lighten(0.55f),
                    0.55f to body,
                    1f to body.darken(0.35f),
                ),
                center = highlightCenter,
                radius = r * 1.45f,
            ),
            radius = r,
            center = center,
        )

        // Specular highlight
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = r * 0.20f,
            center = Offset(center.x - r * 0.42f, center.y - r * 0.42f),
        )

        // Secondary subtle reflection
        drawCircle(
            color = Color.White.copy(alpha = 0.22f),
            radius = r * 0.09f,
            center = Offset(center.x + r * 0.28f, center.y + r * 0.36f),
        )
    }
}

fun marbleSlotSize(): Dp = Dimens.MarbleRadius * 2f
