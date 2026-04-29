package ai.sterling

import ai.sterling.engine.ml.NeuralNetEngine
import ai.sterling.model.Game
import ai.sterling.model.Game.GameStatus
import ai.sterling.ui.animation.MoveEvent
import ai.sterling.util.GameLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {

    private val neuralNetEngine = NeuralNetEngine(searchDepth = 3)
    private val gameLogger = GameLogger()

    private val _game = MutableStateFlow(Game.new())
    val game: StateFlow<Game> = _game.asStateFlow()

    val gameStatus: StateFlow<GameStatus> = _game
        .map { it.status }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = GameStatus.PlayerOneTurn,
        )

    private val _events = MutableSharedFlow<MoveEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<MoveEvent> = _events.asSharedFlow()

    init {
        gameLogger.startGame(humanIsPlayerOne = true)
    }

    fun applyMove(position: Int) {
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

        viewModelScope.launch {
            _events.emit(
                MoveEvent.MoveApplied(
                    boardBeforePockets = before.board.pockets,
                    position = position,
                    isPlayerOne = isP1,
                    boardAfter = newGame.board,
                    statusAfter = newGame.status,
                ),
            )
        }
    }

    suspend fun computeAiMove(): Int = withContext(Dispatchers.Default) {
        neuralNetEngine.selectMove(_game.value)
    }

    fun restart() {
        _game.value = Game.new()
        gameLogger.startGame(humanIsPlayerOne = true)
        viewModelScope.launch { _events.emit(MoveEvent.Reset) }
    }
}
