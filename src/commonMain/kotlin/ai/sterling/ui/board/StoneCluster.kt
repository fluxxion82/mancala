package ai.sterling.ui.board

import ai.sterling.ui.animation.VisualStone
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Renders the marbles at rest in a single pit, arranged in concentric rings using each
 * stone's deterministic jitter blended with a slot index.
 *
 * @param hiddenIds stones currently in flight; rendered by SowingAnimator instead.
 */
@Composable
fun StoneCluster(
    stones: List<VisualStone>,
    pitCenter: Offset,
    pitRadius: Dp,
    modifier: Modifier = Modifier,
    hiddenIds: Set<Long> = emptySet(),
    liftYPx: Float = 0f,
) {
    if (stones.isEmpty()) return
    val density = LocalDensity.current
    val pitRadiusPx = with(density) { pitRadius.toPx() }
    val visible = stones.filter { it.id !in hiddenIds }
    val n = stones.size
    val crowdScale = if (n > 18) sqrt(9f / n.toFloat()).coerceAtMost(1f) else 1f
    // Marble radius derives from the pit radius (1/4 the diameter) so it auto-scales
    // when BoardLayout shrinks pit dimensions to fit a narrow host.
    val marbleRadius = pitRadius * 0.25f * crowdScale
    val marbleRadiusPx = with(density) { marbleRadius.toPx() }

    Box(modifier = modifier) {
        visible.forEachIndexed { idx, stone ->
            key(stone.id) {
                val slot = clusterSlot(
                    indexInPit = stones.indexOf(stone),
                    countInPit = n,
                    pitRadiusPx = pitRadiusPx,
                    jitter = stone.jitter,
                )
                val px = pitCenter.x + slot.x - marbleRadiusPx
                val py = pitCenter.y + slot.y - marbleRadiusPx + liftYPx
                Marble(
                    colorSeed = stone.colorSeed,
                    radius = marbleRadius,
                    modifier = Modifier.offset { IntOffset(px.toInt(), py.toInt()) },
                )
            }
        }
    }
}

private fun clusterSlot(
    indexInPit: Int,
    countInPit: Int,
    pitRadiusPx: Float,
    jitter: Offset,
): Offset {
    val ringJitter = pitRadiusPx * 0.12f
    return when {
        countInPit <= 1 -> Offset(jitter.x * ringJitter, jitter.y * ringJitter)
        countInPit <= 5 -> {
            val ringR = pitRadiusPx * 0.35f
            val theta = (indexInPit.toFloat() / countInPit) * 2f * PI.toFloat()
            Offset(cos(theta) * ringR + jitter.x * ringJitter, sin(theta) * ringR + jitter.y * ringJitter)
        }
        countInPit <= 9 -> {
            if (indexInPit == 0) {
                Offset(jitter.x * ringJitter, jitter.y * ringJitter)
            } else {
                val ringR = pitRadiusPx * 0.45f
                val outerCount = countInPit - 1
                val theta = ((indexInPit - 1).toFloat() / outerCount) * 2f * PI.toFloat()
                Offset(cos(theta) * ringR + jitter.x * ringJitter, sin(theta) * ringR + jitter.y * ringJitter)
            }
        }
        else -> {
            // two rings
            val innerSlots = min(6, countInPit / 3)
            val outerSlots = countInPit - innerSlots
            if (indexInPit < innerSlots) {
                val ringR = pitRadiusPx * 0.25f
                val theta = (indexInPit.toFloat() / innerSlots) * 2f * PI.toFloat()
                Offset(cos(theta) * ringR + jitter.x * ringJitter, sin(theta) * ringR + jitter.y * ringJitter)
            } else {
                val outerIdx = indexInPit - innerSlots
                val ringR = pitRadiusPx * 0.55f
                val theta = (outerIdx.toFloat() / outerSlots) * 2f * PI.toFloat()
                Offset(cos(theta) * ringR + jitter.x * ringJitter, sin(theta) * ringR + jitter.y * ringJitter)
            }
        }
    }
}
