package ai.sterling.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import ai.sterling.ui.theme.BoardColors
import ai.sterling.ui.theme.Dimens
import ai.sterling.ui.theme.LocalBoardScale
import ai.sterling.ui.theme.darken
import ai.sterling.ui.theme.lighten
import androidx.compose.ui.unit.Dp
import kotlin.math.absoluteValue

@Composable
fun Marble(
    colorSeed: Int,
    modifier: Modifier = Modifier,
    radius: Dp = Dimens.MarbleRadius * LocalBoardScale.current,
    alpha: Float = 1f,
) {
    val body = BoardColors.MarblePalette[colorSeed.absoluteValue % BoardColors.MarblePalette.size]
    val r = with(LocalDensity.current) { radius.toPx() }

    // Cache the gradient brush + colorStops array so we stop reallocating one
    // every frame (4-6 marbles in flight × 60fps × N moves chews up memory and
    // forces Skia to rebuild the shader each time, which Firefox in particular
    // doesn't reclaim aggressively).
    val bodyBrush = remember(body, r) {
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to body.lighten(0.55f),
                0.55f to body,
                1f to body.darken(0.35f),
            ),
            center = Offset(r - r * 0.32f, r - r * 0.32f),
            radius = r * 1.45f,
        )
    }
    val shadowColor = remember { Color.Black.copy(alpha = 0.32f) }
    val specularColor = remember { Color.White.copy(alpha = 0.85f) }
    val secondaryColor = remember { Color.White.copy(alpha = 0.22f) }

    Canvas(modifier = modifier.alpha(alpha)) {
        val diameter = r * 2f
        val center = Offset(r, r)

        // Drop shadow ellipse below
        drawOval(
            color = shadowColor,
            topLeft = Offset(-r * 0.05f, r * 0.55f),
            size = Size(diameter * 1.10f, diameter * 0.55f),
        )

        // Body
        drawCircle(
            brush = bodyBrush,
            radius = r,
            center = center,
        )

        // Specular highlight
        drawCircle(
            color = specularColor,
            radius = r * 0.20f,
            center = Offset(center.x - r * 0.42f, center.y - r * 0.42f),
        )

        // Secondary subtle reflection
        drawCircle(
            color = secondaryColor,
            radius = r * 0.09f,
            center = Offset(center.x + r * 0.28f, center.y + r * 0.36f),
        )
    }
}

fun marbleSlotSize(): Dp = Dimens.MarbleRadius * 2f
