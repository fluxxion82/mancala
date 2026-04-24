package ai.sterling

import ai.sterling.engine.ml.NeuralNetEngine
import ai.sterling.model.Game
import ai.sterling.model.Game.GameStatus
import ai.sterling.util.GameLogger
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainViewModel: ViewModel() {
    private var game: Game = Game.new()
    private val neuralNetEngine = NeuralNetEngine(searchDepth = 3)
    private val gameLogger = GameLogger()

    val stones = mutableStateListOf<Int>().apply {
        addAll(game.board.pockets)
    }
    val gameStatus = mutableStateOf<GameStatus>(GameStatus.PlayerOneTurn)
    private var computerMoveJob: Job? = null

    init {
        gameLogger.startGame(humanIsPlayerOne = true)
    }

    fun onMoveInput(position: Int) {
        if (gameStatus.value is GameStatus.Finished) {
            return
        }

        viewModelScope.launch {
            try {
                val wasPlayerOne = gameStatus.value == GameStatus.PlayerOneTurn
                game = game.makeMove(position)
                gameLogger.recordMove(position, wasPlayerOne)
                updateGameState()

                printGameState(position, "Player")

                if (gameStatus.value is GameStatus.Finished) {
                    gameLogger.endGame(gameStatus.value)
                } else {
                    makeComputerMove()
                }
            } catch (e: IllegalArgumentException) {
                // Handle invalid move
                println("Invalid move: ${e.message}")
            }
        }
    }

    private fun makeComputerMove() {
        if (gameStatus.value == GameStatus.PlayerTwoTurn) {
            computerMoveJob = viewModelScope.launch(Dispatchers.Default) {
                delay(2000)
                while (gameStatus.value == GameStatus.PlayerTwoTurn) {
                    val nextMove = neuralNetEngine.selectMove(game)

                    try {
                        val wasPlayerOne = gameStatus.value == GameStatus.PlayerOneTurn
                        game = game.makeMove(nextMove)
                        gameLogger.recordMove(nextMove, wasPlayerOne)
                        updateGameState()
                        printGameState(nextMove, "Computer")

                        if (gameStatus.value is GameStatus.Finished) {
                            gameLogger.endGame(gameStatus.value)
                        }
                    } catch (e: IllegalArgumentException) {
                        println("Invalid computer move: ${e.message}")
                        break
                    }
                }
            }
        }
    }

    private fun updateGameState() {
        gameStatus.value = game.status
        stones.clear()
        stones.addAll(game.board.pockets)
    }

    private fun printGameState(move: Int, player: String) {
        println("$player move: $move")
        println("Game status: ${gameStatus.value}")
        printBoard()

        when (gameStatus.value) {
            is GameStatus.Finished.Draw -> println("Game ended in a draw")
            is GameStatus.Finished.PlayerOneWin -> println("Player One wins!")
            is GameStatus.Finished.PlayerTwoWin -> println("Player Two wins!")
            else -> println()
        }
    }

    private fun printBoard() {
        val board = stones
        println("""
            ${board[12]} | ${board[11]} | ${board[10]} | ${board[9]} | ${board[8]} | ${board[7]}
         ${board[13]}                       ${board[6]}
            ${board[0]} | ${board[1]} | ${board[2]} | ${board[3]} | ${board[4]} | ${board[5]}
        """.trimIndent())
    }

    fun onRestartClick() {
        computerMoveJob?.cancel()
        game = Game.new()
        updateGameState()
        printBoard()
        gameLogger.startGame(humanIsPlayerOne = true)
    }
}
