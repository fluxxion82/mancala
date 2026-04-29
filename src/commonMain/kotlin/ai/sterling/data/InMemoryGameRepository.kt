package ai.sterling.data

import ai.sterling.engine.ml.NeuralNetEngine
import ai.sterling.model.Game
import ai.sterling.model.Game.GameStatus
import ai.sterling.model.HumanSide
import ai.sterling.repository.GameRepository
import ai.sterling.ui.animation.MoveEvent
import ai.sterling.util.GameLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.yield

class InMemoryGameRepository(
    private val neuralNetEngine: NeuralNetEngine,
    /**
     * If > 0, AI uses iterative-deepening search with this wall-clock budget,
     * adapting depth to the device's speed. If 0, falls back to the engine's
     * fixed [NeuralNetEngine.selectMove] path (depth determined at engine creation).
     */
    private val aiTimeBudgetMs: Long = 0,
    private val aiMaxDepth: Int = 6,
    private val gameLogger: GameLogger = GameLogger(),
) : GameRepository {

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

    override suspend fun computeAiMove(): Int {
        // Yield once so the human's move animation gets a frame before the AI
        // starts thinking — without this, on Wasm the AI compute begins in the
        // same event-loop tick as the state update and the human's animation
        // never gets to render.
        yield()
        // Don't bother with withContext(Dispatchers.Default) — on Kotlin/Wasm
        // there are no background threads, so it's the main dispatcher anyway.
        // The engine yields cooperatively inside its search loop.
        return try {
            val move = if (aiTimeBudgetMs > 0L) {
                neuralNetEngine.selectMoveAdaptive(_game.value, aiTimeBudgetMs, aiMaxDepth)
            } else {
                neuralNetEngine.selectMove(_game.value)
            }
            println("[mancala] AI selected move=$move status=${_game.value.status}")
            move
        } catch (t: Throwable) {
            println("[mancala] AI inference failed: ${t::class.simpleName}: ${t.message}")
            t.printStackTrace()
            // Fall back to any legal move so the game doesn't deadlock.
            val board = _game.value.board
            val legal = board.legalMoves(_game.value.status == GameStatus.PlayerOneTurn)
            legal.firstOrNull() ?: 0
        }
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
