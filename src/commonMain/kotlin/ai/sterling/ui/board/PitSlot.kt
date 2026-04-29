package ai.sterling.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

data class PitSlot(
    val index: Int,
    val center: Offset,
    val width: Float,
    val height: Float,
) {
    val isMancala: Boolean get() = index == 6 || index == 13

    val rect: Rect
        get() = Rect(
            offset = Offset(center.x - width / 2f, center.y - height / 2f),
            size = Size(width, height),
        )
}
