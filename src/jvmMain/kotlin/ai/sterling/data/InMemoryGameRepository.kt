package ai.sterling.data

import ai.sterling.engine.ml.NeuralNetEngine
import ai.sterling.model.Game
import ai.sterling.model.Game.GameStatus
import ai.sterling.model.HumanSide
import ai.sterling.repository.GameRepository
import ai.sterling.ui.animation.MoveEvent
import ai.sterling.util.GameLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class InMemoryGameRepository : GameRepository {

    private val neuralNetEngine = NeuralNetEngine(searchDepth = 3)
    private val gameLogger = GameLogger()

    private val _game = MutableStateFlow(Game.new())
    override val game: StateFlow<Game> = _game.asStateFlow()

    private val _humanSide = MutableStateFlow<HumanSide?>(null)
    override val humanSide: StateFlow<HumanSide?> = _humanSide.asStateFlow()

    private val _events = MutableSharedFlow<MoveEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<MoveEvent> = _events.asSharedFlow()

    override fun applyMove(position: Int) {
        val before = _game.value
        if (before.status is GameStatus.Finished) return

        val isP1 = before.status == GameStatus.PlayerOneTurn
        val newGame = try {
            before.makeMove(position)
        } catch (e: IllegalArgumentException) {
            println("Invalid move at $position: ${e.message}")
            return
        }

        _game.value = newGame
        gameLogger.recordMove(position, isP1)
        if (newGame.status is GameStatus.Finished) {
            gameLogger.endGame(newGame.status)
        }

        _events.tryEmit(
            MoveEvent.MoveApplied(
                boardBeforePockets = before.board.pockets,
                position = position,
                isPlayerOne = isP1,
                boardAfter = newGame.board,
                statusAfter = newGame.status,
            ),
        )
    }

    override suspend fun computeAiMove(): Int = withContext(Dispatchers.Default) {
        neuralNetEngine.selectMove(_game.value)
    }

    override fun restart(humanSide: HumanSide) {
        // Update humanSide BEFORE the game so any snapshot read between these
        // operations sees a consistent (humanSide, game) pair.
        _humanSide.value = humanSide
        _game.value = Game.new()
        gameLogger.startGame(humanIsPlayerOne = humanSide == HumanSide.PLAYER_ONE)
        _events.tryEmit(MoveEvent.Reset)
    }
}
