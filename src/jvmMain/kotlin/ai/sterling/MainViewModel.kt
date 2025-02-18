package ai.sterling

import ai.sterling.engine.monte.MonteCarlo
import ai.sterling.model.Game
import ai.sterling.model.Game.GameStatus
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainViewModel: ViewModel() {
    private var game: Game = Game.new()
    private var mcEngine = MonteCarlo(game)

    val stones = mutableStateListOf<Int>().apply {
        addAll(game.board.pockets)
    }
    val gameStatus = mutableStateOf<GameStatus>(GameStatus.PlayerOneTurn)
    private var computerMoveJob: Job? = null

    fun onMoveInput(position: Int) {
        if (gameStatus.value is GameStatus.Finished) {
            return
        }

        viewModelScope.launch {
            try {
                game = game.makeMove(position)
                updateGameState()

                printGameState(position, "Player")

                if (gameStatus.value !is GameStatus.Finished) {
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
            computerMoveJob = viewModelScope.launch {
                delay(2000)
                while (gameStatus.value == GameStatus.PlayerTwoTurn) {
                    mcEngine.apply(game)
                    val nextMove = mcEngine.runBest(20000)

                    try {
                        game = game.makeMove(nextMove)
                        updateGameState()
                        printGameState(nextMove, "Computer")
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
        mcEngine.apply(game)
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
        mcEngine = MonteCarlo(game)
        updateGameState()
        printBoard()
    }
}
