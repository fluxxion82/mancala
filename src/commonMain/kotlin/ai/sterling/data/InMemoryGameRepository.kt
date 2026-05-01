package ai.sterling.data

import ai.sterling.AiMode
import ai.sterling.aiDispatcher
import ai.sterling.engine.AiBackend
import ai.sterling.model.Game
import ai.sterling.model.Game.GameStatus
import ai.sterling.model.HumanSide
import ai.sterling.repository.GameRepository
import ai.sterling.ui.animation.MoveEvent
import ai.sterling.util.GameLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

class InMemoryGameRepository(
    private val aiBackend: AiBackend,
    /**
     * Selects which AI search algorithm runs and its budget. See [AiMode].
     */
    private val aiMode: AiMode = AiMode.AlphaBeta(),
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
        // Dispatch the search via [aiDispatcher]: on JVM that's a single-lane
        // background dispatcher so the UI thread stays free during a multi-second
        // budget; on Wasm it's an empty context (no background threads exist —
        // the worker actual handles off-main work).
        return try {
            val move = withContext(aiDispatcher) { aiBackend.selectMove(_game.value, aiMode) }
            // Pull telemetry from the most recent search and persist it. Backends that
            // don't surface telemetry return null and we skip the log line.
            aiBackend.lastSearchTelemetry()?.let { gameLogger.recordAiMove(it) }
            move
        } catch (t: CancellationException) {
            // Don't swallow cancellation: when the user restarts mid-think, the VM's
            // collectLatest cancels this coroutine and we MUST propagate so the
            // caller's `applyMove(ai)` line never runs. Otherwise a fallback move
            // gets stamped onto the freshly-restarted game.
            throw t
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
        // Drop any cached search tree from the previous game — the new initial
        // position is rarely reachable from the prior root anyway, but doing this
        // explicitly makes the contract obvious.
        aiBackend.resetSearchState()
        gameLogger.startGame(humanIsPlayerOne = humanSide == HumanSide.PLAYER_ONE)
        _events.tryEmit(MoveEvent.Reset)
    }
}
