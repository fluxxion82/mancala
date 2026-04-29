package ai.sterling.ui.board

import ai.sterling.ui.theme.BoardColors
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.max

/**
 * Soft warm glow drawn around the active player's mancala. Pulses gently to draw the eye
 * without being distracting.
 */
@Composable
fun MancalaGlow(
    slot: PitSlot,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "mancala-glow")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    // Cache the radial gradient brush — without this we'd allocate a Brush +
    // colorStops Array + 3 Pair objects on every animation frame (60fps × the
    // entire game session). The pulse alpha is applied via Modifier.alpha
    // outside so the brush itself stays stable.
    val radius = max(slot.width, slot.height) * 0.85f
    val center = remember(slot.center) { Offset(slot.center.x, slot.center.y) }
    val glowBrush = remember(center, radius) {
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to BoardColors.GlowAmber.copy(alpha = 0.0f),
                0.55f to BoardColors.GlowAmber.copy(alpha = 0.32f),
                1f to Color.Transparent,
            ),
            center = center,
            radius = radius,
        )
    }

    Canvas(modifier = modifier.alpha(pulse)) {
        drawCircle(
            brush = glowBrush,
            radius = radius,
            center = center,
        )
    }
}
