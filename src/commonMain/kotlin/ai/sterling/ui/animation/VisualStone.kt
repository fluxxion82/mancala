package ai.sterling.ui.animation

import androidx.compose.ui.geometry.Offset

data class VisualStone(
    val id: Long,
    val pit: Int,
    val colorSeed: Int,
    val jitter: Offset,
)
