package ai.sterling.ui.animation

import ai.sterling.ui.board.Marble
import ai.sterling.ui.theme.Dimens
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.PI
import kotlin.math.sin

/**
 * Top overlay layer rendering each in-flight stone at an absolute root-coordinate offset.
 * Pure renderer — all state lives in [MancalaBoardAnimationState.inFlight].
 */
@Composable
fun SowingAnimator(state: MancalaBoardAnimationState) {
    val density = LocalDensity.current
    val arcHeightPx = with(density) { Dimens.ArcHeight.toPx() }
    val marbleRadiusPx = with(density) { Dimens.MarbleRadius.toPx() }

    Box(modifier = Modifier) {
        state.inFlight.forEach { (id, fs) ->
            key(id) {
                val t = fs.tAnim.value
                val pos = lerp(fs.from, fs.to, t) + Offset(0f, -sin(PI * t).toFloat() * arcHeightPx)
                val alpha = if (fs.kind == StoneMoveKind.SWEEP) 0.85f else 1f
                Marble(
                    colorSeed = fs.colorSeed,
                    alpha = alpha,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (pos.x - marbleRadiusPx).toInt(),
                                (pos.y - marbleRadiusPx).toInt(),
                            )
                        }
                        .graphicsLayer {
                            scaleX = fs.scaleAnim.value
                            scaleY = fs.scaleAnim.value
                        },
                )
            }
        }
    }
}

private fun lerp(a: Offset, b: Offset, t: Float): Offset =
    Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
