package ai.sterling.session

import ai.sterling.AiMode
import ai.sterling.data.InMemoryGameRepository
import ai.sterling.engine.AiBackend
import ai.sterling.model.Board
import ai.sterling.model.Game
import ai.sterling.model.HumanSide
import ai.sterling.model.MoveEvent
import ai.sterling.model.isHumansTurn
import ai.sterling.repository.GameRepository
import ai.sterling.util.GameLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * UI-agnostic human-vs-AI game flow. Owns no UI state; any front end (the Compose
 * `MancalaBoardViewModel`, a terminal UI, a test) observes [game] / [humanSide] /
 * [events] and calls [playHumanMove] / [restart].
 *
 * On construction it launches, in [scope], a collector that automatically asks the
 * AI for a move and applies it whenever the game settles on the AI's turn. A new
 * state (e.g. [restart] mid-think) cancels the in-flight search. Cancel [scope] or
 * call [close] to stop it.
 *
 * No game is running until the first [restart] picks the human's side.
 */
class GameSession(
    private val repository: GameRepository,
    scope: CoroutineScope,
) {
    /** Convenience: in-memory repository around [aiBackend] (see [ai.sterling.createAiBackend]). */
    constructor(
        aiBackend: AiBackend,
        aiMode: AiMode,
        scope: CoroutineScope,
        gameLogger: GameLogger? = GameLogger(),
    ) : this(InMemoryGameRepository(aiBackend, aiMode, gameLogger), scope)

    val game: StateFlow<Game> = repository.game
    val humanSide: StateFlow<HumanSide?> = repository.humanSide
    val events: SharedFlow<MoveEvent> = repository.events

    private val _isAiThinking = MutableStateFlow(false)

    /** True while the AI search for the current position is running. */
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    private val aiPump: Job = scope.launch {
        // Auto-pump AI moves whenever (game, humanSide) settles on a state where
        // it is the AI's turn. The repository is purely data; the session is
        // responsible for advancing the game when the human is not the next mover.
        combine(game, humanSide) { g, h -> g to h }
            .distinctUntilChanged()
            .collectLatest { (g, side) ->
                // collectLatest cancels this block if a new state arrives while
                // computeAiMove is still suspended (e.g. user restarts mid-think).
                side ?: return@collectLatest
                val status = g.status
                if (status is Game.GameStatus.Finished) return@collectLatest
                if (status.isHumansTurn(side)) return@collectLatest

                _isAiThinking.value = true
                val ai = try {
                    repository.computeAiMove()
                } finally {
                    _isAiThinking.value = false
                }
                repository.applyMove(ai)
            }
    }

    /** Whether the human may play [position] (absolute pocket index 0-12) right now. */
    fun isLegalMove(position: Int): Boolean {
        if (position == Board.PLAYER_ONE_MANCALA || position == Board.PLAYER_TWO_MANCALA) return false
        val side = humanSide.value ?: return false
        val status = game.value.status
        if (!status.isHumansTurn(side)) return false
        val onHumansSide = when (side) {
            HumanSide.PLAYER_ONE -> position in Board.PLAYER_ONE_POCKETS
            HumanSide.PLAYER_TWO -> position in Board.PLAYER_TWO_POCKETS
        }
        if (!onHumansSide) return false
        return game.value.board.pockets[position] > 0
    }

    /**
     * Applies the human's move at absolute pocket [position] if legal. Returns false
     * (and changes nothing) otherwise. The AI reply, if any, follows automatically.
     */
    fun playHumanMove(position: Int): Boolean {
        if (!isLegalMove(position)) return false
        repository.applyMove(position)
        return true
    }

    /** Starts a fresh game with the human on [side]. P1 always moves first. */
    fun restart(side: HumanSide) {
        repository.restart(side)
    }

    /** Stops the AI pump. Not needed if the owning [CoroutineScope] is cancelled. */
    fun close() {
        aiPump.cancel()
    }
}
