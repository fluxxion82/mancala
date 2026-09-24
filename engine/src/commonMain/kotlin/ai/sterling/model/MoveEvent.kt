package ai.sterling.model

/**
 * Discrete game-flow events emitted by [ai.sterling.repository.GameRepository.events].
 * UIs replay these to animate sowing (Compose) or to redraw/print a move (terminal).
 */
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
