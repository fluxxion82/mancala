package ai.sterling

import ai.sterling.engine.monte.MonteCarlo
import ai.sterling.model.Game
import ai.sterling.model.Game.Companion.newGame
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainViewModel(
    private var game: Game
): BaseViewModel() {
    private var mcEngine = MonteCarlo(game)

    val stones = mutableStateListOf<Int>().apply { addAll(game.board.pockets) }
    val gameStatus = mutableStateOf(game.status)
    private var computerMoveJob: Job? = null

    fun onMoveInput(position: Int) {
        if (gameStatus.value is Game.Status.Finished) {
            return
        }

        launch {
            gameStatus.value = game.makeMove(position)
            mcEngine.apply(game)
            stones.clear()
            stones.addAll(game.board.pockets)

            game.board.printBoard()
            val status = gameStatus.value
            println("your move: $position, stats=$status")
            println()

            if (status is Game.Status.Finished) {
                when (status) {
                    is Game.Status.Finished.Draw -> println("draw")
                    is Game.Status.Finished.PlayerOneWin -> println("player one win")
                    is Game.Status.Finished.PlayerTwoWin -> println("player two win")
                }
            } else {
                makeComputerMove()
            }
        }
    }

    private fun makeComputerMove() {
        if (game.board.playerTwo.turn && gameStatus.value !is Game.Status.Finished) {
            println("makeComputerMove")
            computerMoveJob = launch {
                delay(2000)
                while (game.board.playerTwo.turn && gameStatus.value !is Game.Status.Finished) {
                    mcEngine.apply(game)
                    val nextMove = mcEngine.runBest(25000)
                    gameStatus.value = game.makeMove(nextMove)
                    stones.clear()
                    stones.addAll(game.board.pockets)

                    val status = gameStatus.value
                    if (status is Game.Status.Finished) {
                        when (status) {
                            is Game.Status.Finished.Draw -> println("draw")
                            is Game.Status.Finished.PlayerOneWin -> println("player one win")
                            is Game.Status.Finished.PlayerTwoWin -> println("player two win")
                        }
                    }

                    println("computer move: $nextMove")
                    game.board.printBoard()
                    println()
                }
            }
        }
    }

    fun onRestartClick() {
        computerMoveJob?.cancel()
        game = newGame()
        mcEngine = MonteCarlo(game)
        stones.clear()
        stones.addAll(game.board.pockets)
        gameStatus.value = game.status

        game.board.printBoard()
    }
}
