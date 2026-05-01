package ai.sterling.loading

import ai.sterling.ui.theme.BoardColors
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun MancalaLoadingScreen(
    state: WeightLoadingState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().background(BoardColors.TableFelt),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp).widthIn(max = 320.dp),
        ) {
            MancalaSpinner(active = state !is WeightLoadingState.Error)

            Spacer(Modifier.height(20.dp))

            Text(
                text = state.headline(),
                color = BoardColors.Parchment,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )

            val sub = state.subline()
            if (sub != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = sub,
                    color = BoardColors.ParchmentDim,
                    fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(16.dp))

            ProgressBar(state)

            if (state is WeightLoadingState.Error) {
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = BoardColors.WoodLight,
                        contentColor = BoardColors.Parchment,
                    ),
                ) {
                    Text(
                        text = "Retry",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(state: WeightLoadingState) {
    val progress = (state as? WeightLoadingState.Downloading)?.let { d ->
        val total = d.total
        if (total != null && total > 0) {
            (d.received.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }
    }
    val barColor = if (state is WeightLoadingState.Error) BoardColors.WoodMid else BoardColors.GlowAmber
    val track = BoardColors.PitMid
    if (progress != null) {
        LinearProgressIndicator(
            progress = progress,
            color = barColor,
            backgroundColor = track,
            modifier = Modifier.fillMaxWidth().height(4.dp),
        )
    } else if (state !is WeightLoadingState.Error && state !is WeightLoadingState.Idle) {
        LinearProgressIndicator(
            color = barColor,
            backgroundColor = track,
            modifier = Modifier.fillMaxWidth().height(4.dp),
        )
    } else {
        // Hidden placeholder so layout doesn't jump when transitioning.
        Spacer(Modifier.fillMaxWidth().height(4.dp))
    }
}

@Composable
private fun MancalaSpinner(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "mancala-loader")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "stone-phase",
    )

    Canvas(modifier = Modifier.size(width = 168.dp, height = 64.dp)) {
        val pitCount = 6
        val pitRadius = size.height * 0.35f
        val gap = (size.width - pitRadius * 2 * pitCount) / (pitCount + 1)
        val cy = size.height / 2f
        val pitCenters = (0 until pitCount).map { i ->
            Offset(x = gap + pitRadius + i * (pitRadius * 2 + gap), y = cy)
        }

        // Pits.
        pitCenters.forEach { c ->
            drawCircle(
                color = BoardColors.PitDeep,
                radius = pitRadius,
                center = c,
            )
            drawCircle(
                color = BoardColors.PitRim,
                radius = pitRadius,
                center = c,
                style = Stroke(width = 2f),
            )
        }

        if (!active) return@Canvas

        // A single stone hops through the pits in a small arc.
        val phasing = phase.coerceIn(0f, pitCount.toFloat())
        val from = phasing.toInt().coerceAtMost(pitCount - 1)
        val to = (from + 1).coerceAtMost(pitCount - 1)
        val t = (phasing - from).coerceIn(0f, 1f)

        val a = pitCenters[from]
        val b = pitCenters[to]
        val x = a.x + (b.x - a.x) * t
        // Sin arc above the line so the stone visibly hops between pits.
        val arcHeight = pitRadius * 1.6f
        val y = cy - arcHeight * sin((t * PI).toFloat())

        drawCircle(
            color = BoardColors.MarblePalette[1],
            radius = pitRadius * 0.55f,
            center = Offset(x, y),
        )
        drawCircle(
            color = BoardColors.MarblePalette[1].copy(alpha = 0.6f),
            radius = pitRadius * 0.55f,
            center = Offset(x, y),
            style = Stroke(width = 1.5f),
        )
    }
}

private fun WeightLoadingState.headline(): String = when (this) {
    WeightLoadingState.Idle -> "Preparing model…"
    WeightLoadingState.Checking -> "Checking cache…"
    is WeightLoadingState.Downloading -> "Downloading model…"
    WeightLoadingState.Decompressing -> "Decompressing…"
    WeightLoadingState.Initializing -> "Initializing engine…"
    WeightLoadingState.Ready -> "Ready"
    is WeightLoadingState.Error -> "Couldn't load the model"
}

private fun WeightLoadingState.subline(): String? = when (this) {
    is WeightLoadingState.Downloading -> {
        val total = total
        if (total != null && total > 0) {
            "${formatMb(received)} / ${formatMb(total)}"
        } else {
            formatMb(received)
        }
    }
    is WeightLoadingState.Error -> cause.message?.takeIf { it.isNotBlank() }
    else -> null
}

private fun formatMb(bytes: Long): String {
    val mb = bytes / 1_048_576.0
    return if (mb >= 10.0) "${mb.toInt()} MB" else "${(mb * 10).toLong() / 10.0} MB"
}
