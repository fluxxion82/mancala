package ai.sterling.ui.animation

import ai.sterling.model.Board
import ai.sterling.model.Game

enum class StoneMoveKind {
    SOW,
    CAPTURE_OPPOSITE,
    CAPTURE_LANDING,
    SWEEP,
}

data class StoneMove(
    val fromPit: Int,
    val toPit: Int,
    val kind: StoneMoveKind,
)

data class AnimationPlan(
    val moves: List<StoneMove>,
    val finalPockets: List<Int>,
)

sealed class MoveEvent {
    data class MoveApplied(
        val boardBeforePockets: List<Int>,
        val position: Int,
        val isPlayerOne: Boolean,
        val boardAfter: Board,
        val statusAfter: Game.GameStatus,
    ) : MoveEvent()

    data object Reset : MoveEvent()
}
